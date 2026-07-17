#!/usr/bin/env node

import { execFileSync } from 'node:child_process';

const coreBaseUrl = process.env.DEVCOLLAB_CORE_BASE_URL ?? 'http://localhost:8080';
const topic = process.env.DEVCOLLAB_KAFKA_DOCUMENT_TOPIC ?? 'devcollab.document.events';
const timeoutMs = Number(process.env.DEVCOLLAB_E2E_WAIT_TIMEOUT_MS ?? '60000');
const suffix = new Date().toISOString().replace(/\D/g, '').slice(0, 14);
const password = 'Password123!';

async function main() {
  await assertCore();
  ensureKafkaTopic();

  const author = await register(`snapshot_author_${suffix}`, `Snapshot Author ${suffix}`);
  const reviewer = await register(`snapshot_reviewer_${suffix}`, `Snapshot Reviewer ${suffix}`);
  const workspace = await api(author.accessToken, '/api/v1/workspaces', {
    method: 'POST',
    body: { name: `MinIO Snapshot E2E ${suffix}` },
  });

  await api(author.accessToken, `/api/v1/workspaces/${workspace.id}/members/invitations`, {
    method: 'POST',
    body: { username: reviewer.username, role: 'ADMIN' },
  });

  const document = await api(author.accessToken, `/api/v1/workspaces/${workspace.id}/documents`, {
    method: 'POST',
    body: { title: `MinIO Snapshot Document ${suffix}`, documentType: 'REQUIREMENT' },
  });
  await api(author.accessToken, `/api/v1/documents/${document.id}/blocks`, {
    method: 'POST',
    body: {
      type: 'PARAGRAPH',
      content: { text: `MinIO snapshot content ${suffix}` },
    },
  });
  await api(author.accessToken, `/api/v1/documents/${document.id}/submit-review`, {
    method: 'POST',
  });
  await api(reviewer.accessToken, `/api/v1/documents/${document.id}/approve-review`, {
    method: 'POST',
    body: { comment: 'MinIO snapshot E2E approve' },
  });

  console.log(`[kafka-minio-e2e] approved workspace=${workspace.id} document=${document.id}`);

  const outbox = await waitFor('SNAPSHOT_REQUESTED outbox PUBLISHED', () => {
    const rows = psql(`
      select id, status
        from outbox_events
       where event_type = 'SNAPSHOT_REQUESTED'
         and payload::jsonb ->> 'documentId' = '${document.id}'
       order by occurred_at desc;
    `);
    const row = rows.map(splitRow).find(columns => columns[1] === 'PUBLISHED');
    return row ? { eventId: row[0], status: row[1] } : null;
  });
  console.log(`[kafka-minio-e2e] outbox event=${outbox.eventId} status=${outbox.status}`);

  await waitFor('snapshot consumer_inbox record', () => {
    const rows = psql(`
      select consumer_name
        from consumer_inbox
       where event_id = '${outbox.eventId}'::uuid
         and consumer_name = 'snapshot-object-storage';
    `);
    return rows.length > 0 ? rows[0] : null;
  });
  console.log('[kafka-minio-e2e] snapshot consumer recorded success');

  const stored = await waitFor('stored_objects metadata', () => {
    const rows = psql(`
      select bucket, object_key, size_bytes, checksum_sha256, status
        from stored_objects
       where source_event_id = '${outbox.eventId}'::uuid;
    `);
    if (rows.length === 0) return null;
    const columns = splitRow(rows[0]);
    return {
      bucket: columns[0],
      objectKey: columns[1],
      sizeBytes: Number(columns[2]),
      checksum: columns[3],
      status: columns[4],
    };
  });

  const stat = minioStat(stored.bucket, stored.objectKey);
  if (stored.status !== 'AVAILABLE') {
    throw new Error(`Expected stored object AVAILABLE, got ${stored.status}`);
  }
  if (stored.sizeBytes <= 0 || Number(stat.size) !== stored.sizeBytes) {
    throw new Error(`Object size mismatch: database=${stored.sizeBytes} minio=${stat.size}`);
  }

  console.log(`[kafka-minio-e2e] object=${stored.bucket}/${stored.objectKey} size=${stored.sizeBytes}`);
  console.log(`[kafka-minio-e2e] checksum=${stored.checksum}`);
  console.log(`[kafka-minio-e2e] PASS workspace=${workspace.id} document=${document.id}`);
}

async function assertCore() {
  const response = await fetch(`${coreBaseUrl}/actuator/health`);
  if (!response.ok) {
    throw new Error(`Core health check failed: ${response.status}`);
  }
}

function ensureKafkaTopic() {
  execFileSync('docker', [
    'exec', 'devcollab-kafka',
    '/opt/kafka/bin/kafka-topics.sh',
    '--bootstrap-server', 'localhost:9092',
    '--create', '--if-not-exists',
    '--topic', topic,
    '--partitions', '1',
    '--replication-factor', '1',
  ], { stdio: 'pipe' });
}

async function register(username, displayName) {
  const result = await api(null, '/api/v1/auth/register', {
    method: 'POST',
    body: { username, displayName, password },
  });
  return { ...result, username };
}

async function api(token, path, options = {}) {
  const response = await fetch(`${coreBaseUrl}${path}`, {
    method: options.method ?? 'GET',
    headers: {
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...(options.body ? { 'Content-Type': 'application/json' } : {}),
    },
    body: options.body ? JSON.stringify(options.body) : undefined,
  });
  if (!response.ok) {
    throw new Error(`${options.method ?? 'GET'} ${path} failed: ${response.status} ${await response.text()}`);
  }
  return response.status === 204 ? null : response.json();
}

function psql(sql) {
  const output = execFileSync('docker', [
    'exec', 'devcollab-postgres', 'psql',
    '-U', 'devcollab', '-d', 'devcollab',
    '-t', '-A', '-F', '|', '-c', sql,
  ], { encoding: 'utf8' });
  return output.split(/\r?\n/).map(line => line.trim()).filter(Boolean);
}

function minioStat(bucket, objectKey) {
  execFileSync('docker', [
    'exec', 'devcollab-minio', 'mc', 'alias', 'set',
    'local', 'http://localhost:9000',
    process.env.DEVCOLLAB_MINIO_ACCESS_KEY ?? 'devcollab',
    process.env.DEVCOLLAB_MINIO_SECRET_KEY ?? 'devcollab-minio-password',
  ], { stdio: 'pipe' });
  const output = execFileSync('docker', [
    'exec', 'devcollab-minio', 'mc', 'stat', '--json',
    `local/${bucket}/${objectKey}`,
  ], { encoding: 'utf8' });
  return JSON.parse(output.trim());
}

async function waitFor(description, action) {
  const startedAt = Date.now();
  let lastError;
  while (Date.now() - startedAt < timeoutMs) {
    try {
      const result = await action();
      if (result) return result;
    } catch (error) {
      lastError = error;
    }
    await new Promise(resolve => setTimeout(resolve, 1000));
  }
  printDiagnostics();
  throw new Error(`Timed out waiting for ${description}${lastError ? `: ${lastError.message}` : ''}`);
}

function printDiagnostics() {
  for (const [label, sql] of [
    ['outbox', `select event_type, status, retry_count, coalesce(last_error, '') from outbox_events order by occurred_at desc limit 10;`],
    ['inbox', `select consumer_name, event_id, consumed_at from consumer_inbox order by consumed_at desc limit 10;`],
    ['objects', `select bucket, object_key, status, created_at from stored_objects order by created_at desc limit 10;`],
  ]) {
    try {
      console.error(`[kafka-minio-e2e] diagnostics ${label}: ${psql(sql).join(' ; ')}`);
    } catch (error) {
      console.error(`[kafka-minio-e2e] diagnostics ${label} failed: ${error.message}`);
    }
  }
}

function splitRow(row) {
  return row.split('|');
}

main().catch(error => {
  console.error(`[kafka-minio-e2e] FAIL ${error.message}`);
  process.exitCode = 1;
});
