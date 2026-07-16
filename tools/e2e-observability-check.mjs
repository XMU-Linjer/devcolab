#!/usr/bin/env node

const endpoints = {
  core: process.env.DEVCOLLAB_CORE_ACTUATOR_URL ?? 'http://localhost:8080/actuator',
  worker: process.env.DEVCOLLAB_WORKER_ACTUATOR_URL ?? 'http://localhost:8082/actuator',
  gateway: process.env.DEVCOLLAB_GATEWAY_ACTUATOR_URL ?? 'http://localhost:8090/actuator',
  prometheus: process.env.DEVCOLLAB_PROMETHEUS_URL ?? 'http://localhost:9091',
  loki: process.env.DEVCOLLAB_LOKI_URL ?? 'http://localhost:3100',
  tempo: process.env.DEVCOLLAB_TEMPO_URL ?? 'http://localhost:3200',
  grafana: process.env.DEVCOLLAB_GRAFANA_URL ?? 'http://localhost:3000',
};

async function main() {
  for (const [name, baseUrl] of Object.entries({
    core: endpoints.core,
    worker: endpoints.worker,
    gateway: endpoints.gateway,
  })) {
    await assertActuator(name, baseUrl);
  }

  await fetch('http://localhost:8080/api/v1/auth/me');
  await fetch('http://localhost:8090/actuator/health');

  await waitFor('Prometheus scrapes all Java services', async () => {
    const data = await prometheusQuery('up{job=~"devcollab-.*"}');
    return data.length === 3 && data.every(item => Number(item.value[1]) === 1);
  });
  console.log('[observability-e2e] Prometheus targets PASS');

  await waitFor('OpenTelemetry Collector receives spans', async () => {
    const data = await prometheusQuery('sum(otelcol_receiver_accepted_spans_total)');
    return data.length > 0 && Number(data[0].value[1]) > 0;
  });
  console.log('[observability-e2e] OTLP collector PASS');

  await waitFor('Alloy sends local demo logs to Loki', async () => {
    const start = BigInt(Date.now() - 10 * 60 * 1000) * 1_000_000n;
    const url = new URL('/loki/api/v1/query_range', endpoints.loki);
    url.searchParams.set('query', '{job="devcollab-local"}');
    url.searchParams.set('start', start.toString());
    url.searchParams.set('limit', '5');
    const body = await getJson(url);
    return body.status === 'success' && body.data.result.length > 0;
  });
  console.log('[observability-e2e] Loki logs PASS');

  await waitFor('Tempo stores a Knowledge Core trace', async () => {
    const url = new URL('/api/search', endpoints.tempo);
    url.searchParams.set('q', '{ resource.service.name = "devcollab-knowledge-core" }');
    url.searchParams.set('limit', '5');
    const body = await getJson(url);
    return Array.isArray(body.traces) && body.traces.length > 0;
  });
  console.log('[observability-e2e] Tempo traces PASS');

  const grafana = await getJson(new URL('/api/health', endpoints.grafana));
  if (grafana.database !== 'ok') {
    throw new Error(`Grafana database is not healthy: ${JSON.stringify(grafana)}`);
  }
  console.log('[observability-e2e] Grafana PASS');
  console.log('[observability-e2e] PASS');
}

async function assertActuator(name, baseUrl) {
  const health = await getJson(new URL(`${baseUrl}/health`));
  if (health.status !== 'UP') {
    throw new Error(`${name} health expected UP, got ${JSON.stringify(health)}`);
  }
  const metrics = await getText(new URL(`${baseUrl}/prometheus`));
  if (!metrics.includes('jvm_memory_used_bytes')) {
    throw new Error(`${name} Prometheus endpoint is missing JVM metrics`);
  }
  console.log(`[observability-e2e] ${name} actuator PASS`);
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

async function waitFor(description, assertion, timeoutMs = 60_000) {
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

async function getText(url) {
  const response = await fetch(url);
  if (!response.ok) {
    throw new Error(`GET ${url} failed: ${response.status} ${await response.text()}`);
  }
  return response.text();
}

main().catch(error => {
  console.error(`[observability-e2e] FAIL ${error.message}`);
  process.exitCode = 1;
});
