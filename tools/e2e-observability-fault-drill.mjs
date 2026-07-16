#!/usr/bin/env node

import { spawn, spawnSync } from 'node:child_process';
import { existsSync, openSync, readFileSync, writeFileSync } from 'node:fs';
import path from 'node:path';
import process from 'node:process';
import { fileURLToPath } from 'node:url';

const repoRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const stateFile = path.join(repoRoot, 'logs', 'local-demo', 'processes.json');
const endpoints = {
  workerActuator: process.env.DEVCOLLAB_WORKER_ACTUATOR_URL ?? 'http://localhost:8082/actuator',
  prometheus: process.env.DEVCOLLAB_PROMETHEUS_URL ?? 'http://localhost:9091',
};

async function main() {
  const state = readState();
  let worker = findManagedService(state, 'devcollab-worker');

  console.log(`[fault-drill] baseline worker pid=${worker.processId} port=${worker.port}`);
  if (!(await isWorkerUp())) {
    console.log('[fault-drill] worker is not currently healthy; recovering before fault injection');
    worker = startWorker(worker);
    replaceManagedService(state, worker);
    writeState(state);
    await waitForPort(worker.port, true, 'worker port up before fault injection', 120_000, worker);
  }
  await assertWorkerUp();
  await waitForPrometheusTarget('devcollab-worker', 1, 'baseline worker target up', 45_000);

  console.log(`[fault-drill] injecting fault: stop managed worker process tree pid=${worker.processId}`);
  taskkill(worker.processId);
  await waitForPort(worker.port, false, 'worker port down', 30_000);
  await waitForPrometheusTarget('devcollab-worker', 0, 'Prometheus detects worker down', 90_000);
  console.log('[fault-drill] Prometheus detected worker DOWN');

  console.log('[fault-drill] recovering worker');
  const restarted = startWorker(worker);
  replaceManagedService(state, restarted);
  writeState(state);

  await waitForPort(restarted.port, true, 'worker port up after recovery', 120_000, restarted);
  await assertWorkerUp();
  await waitForPrometheusTarget('devcollab-worker', 1, 'Prometheus detects worker recovered', 90_000);
  console.log(`[fault-drill] worker recovered pid=${restarted.processId}`);

  await runObservabilityCheck();
  console.log('[fault-drill] PASS');
}

function readState() {
  if (!existsSync(stateFile)) {
    throw new Error(`Managed local demo state not found: ${stateFile}. Start local demo first.`);
  }
  const raw = readFileSync(stateFile, 'utf8').replace(/^\uFEFF/, '');
  const parsed = JSON.parse(raw);
  return Array.isArray(parsed) ? parsed : [parsed];
}

function writeState(state) {
  writeFileSync(stateFile, `${JSON.stringify(state, null, 2)}\n`, 'utf8');
}

function findManagedService(state, name) {
  const service = state.find(item => item.name === name);
  if (!service) {
    throw new Error(`Managed service not found in state: ${name}`);
  }
  return service;
}

function replaceManagedService(state, replacement) {
  const index = state.findIndex(item => item.name === replacement.name);
  if (index < 0) {
    state.push(replacement);
    return;
  }
  state[index] = replacement;
}

function taskkill(pid) {
  const result = spawnSync('taskkill.exe', ['/PID', String(pid), '/T', '/F'], {
    cwd: repoRoot,
    encoding: 'utf8',
  });
  if (result.status !== 0) {
    throw new Error(`taskkill failed for pid=${pid}: ${result.stderr || result.stdout}`);
  }
}

function startWorker(previous) {
  const stdout = openSync(previous.stdout, 'a');
  const stderr = openSync(previous.stderr, 'a');
  const mvnw = path.join(repoRoot, 'mvnw.cmd');
  const child = spawn('cmd.exe', ['/d', '/c', mvnw, '-pl', 'devcollab-worker', 'spring-boot:run'], {
    cwd: repoRoot,
    detached: true,
    windowsHide: true,
    stdio: ['ignore', stdout, stderr],
    env: buildWorkerEnv(),
  });
  child.unref();
  return {
    name: previous.name,
    module: previous.module,
    port: previous.port,
    processId: child.pid,
    stdout: previous.stdout,
    stderr: previous.stderr,
  };
}

function buildWorkerEnv() {
  const dotEnv = readDotEnv();
  const env = { ...process.env, ...dotEnv };
  env.DEVCOLLAB_DB_URL = env.DEVCOLLAB_DB_URL ?? 'jdbc:postgresql://localhost:5432/devcollab';
  env.DEVCOLLAB_DB_USERNAME = env.DEVCOLLAB_DB_USERNAME ?? 'devcollab';
  env.DEVCOLLAB_DB_PASSWORD = env.DEVCOLLAB_DB_PASSWORD ?? 'devcollab';
  env.DEVCOLLAB_REDIS_HOST = 'localhost';
  env.DEVCOLLAB_REDIS_PORT = resolveRedisHostPort(env);
  env.DEVCOLLAB_KAFKA_BOOTSTRAP_SERVERS = env.DEVCOLLAB_KAFKA_BOOTSTRAP_SERVERS ?? 'localhost:9092';
  env.DEVCOLLAB_ELASTICSEARCH_URL = env.DEVCOLLAB_ELASTICSEARCH_URL ?? 'http://localhost:9200';
  env.DEVCOLLAB_ELASTICSEARCH_ENABLED = 'true';
  env.DEVCOLLAB_WORKER_SERVER_PORT = '8082';
  env.DEVCOLLAB_WORKER_NOTIFICATION_ENABLED = 'true';
  env.DEVCOLLAB_TRACING_ENABLED = 'true';
  env.DEVCOLLAB_OTLP_TRACES_ENDPOINT = env.DEVCOLLAB_OTLP_TRACES_ENDPOINT ?? 'http://localhost:4318/v1/traces';
  return env;
}

function readDotEnv() {
  const envFile = path.join(repoRoot, '.env');
  if (!existsSync(envFile)) {
    return {};
  }
  const values = {};
  for (const line of readFileSync(envFile, 'utf8').split(/\r?\n/)) {
    const trimmed = line.trim();
    if (!trimmed || trimmed.startsWith('#') || !trimmed.includes('=')) {
      continue;
    }
    const [name, ...rest] = trimmed.split('=');
    values[name.trim()] = rest.join('=').trim();
  }
  return values;
}

function resolveRedisHostPort(env) {
  const result = spawnSync('docker', ['port', 'devcollab-redis', '6379/tcp'], {
    cwd: repoRoot,
    encoding: 'utf8',
  });
  const match = result.status === 0 ? result.stdout.match(/:(\d+)\s*$/m) : null;
  return match?.[1] ?? env.REDIS_HOST_PORT ?? '6379';
}

async function assertWorkerUp() {
  if (!(await isWorkerUp())) {
    throw new Error('worker health expected UP');
  }
  console.log('[fault-drill] worker actuator UP');
}

async function isWorkerUp() {
  try {
    const health = await getJson(new URL(`${endpoints.workerActuator}/health`));
    return health.status === 'UP';
  } catch {
    return false;
  }
}

async function waitForPrometheusTarget(job, expected, description, timeoutMs) {
  await waitFor(description, async () => {
    const data = await prometheusQuery(`up{job="${job}"}`);
    return data.length === 1 && Number(data[0].value[1]) === expected;
  }, timeoutMs);
}

async function prometheusQuery(query) {
  const url = new URL('/api/v1/query', endpoints.prometheus);
  url.searchParams.set('query', query);
  const body = await getJson(url);
  if (body.status !== 'success') {
    throw new Error(`Prometheus query failed: ${JSON.stringify(body)}`);
  }
  return body.data.result;
}

async function waitForPort(port, shouldBeOpen, description, timeoutMs, processEntry) {
  await waitFor(description, async () => {
    if (processEntry && !isProcessAlive(processEntry.processId)) {
      const tail = readTail(processEntry.stdout);
      throw new Error(`${processEntry.name} exited during recovery. stdout tail:\n${tail}`);
    }
    return await isPortOpen(port) === shouldBeOpen;
  }, timeoutMs);
}

function isProcessAlive(pid) {
  const result = spawnSync('powershell', ['-NoProfile', '-Command', `if (Get-Process -Id ${Number(pid)} -ErrorAction SilentlyContinue) { exit 0 } else { exit 1 }`], {
    cwd: repoRoot,
    encoding: 'utf8',
  });
  return result.status === 0;
}

function readTail(file) {
  if (!existsSync(file)) {
    return '<missing log file>';
  }
  return readFileSync(file, 'utf8').split(/\r?\n/).slice(-30).join('\n');
}

async function isPortOpen(port) {
  try {
    const response = await fetch(`http://localhost:${port}/actuator/health`, {
      signal: AbortSignal.timeout(800),
    });
    return response.ok;
  } catch {
    return false;
  }
}

async function runObservabilityCheck() {
  const script = path.join(repoRoot, 'tools', 'e2e-observability-check.mjs');
  await new Promise((resolve, reject) => {
    const child = spawn(process.execPath, [script], {
      cwd: repoRoot,
      stdio: 'inherit',
      env: process.env,
    });
    child.on('exit', code => {
      if (code === 0) {
        resolve();
        return;
      }
      reject(new Error(`observability check failed with exit code ${code}`));
    });
  });
}

async function waitFor(description, assertion, timeoutMs) {
  const deadline = Date.now() + timeoutMs;
  let lastError;
  while (Date.now() < deadline) {
    try {
      if (await assertion()) {
        return;
      }
    } catch (error) {
      lastError = error;
    }
    await new Promise(resolve => setTimeout(resolve, 2_000));
  }
  throw new Error(`Timed out waiting for ${description}${lastError ? `: ${lastError.message}` : ''}`);
}

async function getJson(url) {
  const response = await fetch(url);
  if (!response.ok) {
    throw new Error(`GET ${url} failed: ${response.status} ${await response.text()}`);
  }
  return response.json();
}

main().catch(error => {
  console.error(`[fault-drill] FAIL ${error.message}`);
  process.exitCode = 1;
});
