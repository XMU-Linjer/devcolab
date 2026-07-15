#!/usr/bin/env node

import { execFileSync } from 'node:child_process';

const coreBaseUrl = process.env.DEVCOLLAB_CORE_BASE_URL ?? 'http://localhost:8080';
const elasticsearchUrl = process.env.DEVCOLLAB_ELASTICSEARCH_URL ?? 'http://localhost:9200';
const elasticsearchIndex = process.env.DEVCOLLAB_ELASTICSEARCH_INDEX ?? 'devcollab-search';
const topic = process.env.DEVCOLLAB_OUTBOX_KAFKA_TOPIC ?? 'devcollab.domain-events';
const suffix = new Date().toISOString().replace(/\D/g, '').slice(0, 14);
const keyword = `e2ekafka${suffix}`;
const password = 'Password123!';

async function main() {
  await assertCore();
  await assertElasticsearch();
  ensureKafkaTopic();

  const auth = await register({
    username: `kafka_author_${suffix}`,
    displayName: `Kafka E2E Author ${suffix}`,
    password,
  });

  const workspace = await api(auth.accessToken, '/api/v1/workspaces', {
    method: 'POST',
    body: {
      name: `Kafka ES E2E Workspace ${suffix}`,
    },
  });

  const document = await api(auth.accessToken, `/api/v1/workspaces/${workspace.id}/documents`, {
    method: 'POST',
    body: {
      title: `Kafka ES E2E Document ${keyword}`,
      documentType: 'REQUIREMENT',
    },
  });

  const block = await api(auth.accessToken, `/api/v1/documents/${document.id}/blocks`, {
    method: 'POST',
    body: {
      type: 'PARAGRAPH',
      content: {
        text: `Kafka ES E2E block content ${keyword}`,
      },
    },
  });

  console.log(`[kafka-es-e2e] created workspace=${workspace.id} document=${document.id} block=${block.id} keyword=${keyword}`);

  const outboxRows = await waitFor('outbox events published', () => {
    const rows = queryOutbox(document.id, block.id);
    const relevantRows = rows.filter(row =>
      ['DOCUMENT_CREATED', 'DOCUMENT_BLOCK_CREATED'].includes(row.eventType)
    );
    const allPublished = relevantRows.length >= 2
      && relevantRows.every(row => row.status === 'PUBLISHED');
    return allPublished ? rows : null;
  }, 30000);

  console.log(`[kafka-es-e2e] outbox published rows=${outboxRows.length}`);

  const inboxRows = await waitFor('search consumer inbox rows', () => {
    const rows = querySearchConsumerInbox(document.id, block.id);
    const consumedEventTypes = new Set(rows.map(row => row.eventType));
    return consumedEventTypes.has('DOCUMENT_CREATED')
      && consumedEventTypes.has('DOCUMENT_BLOCK_CREATED')
      ? rows
      : null;
  }, 30000);

  console.log(`[kafka-es-e2e] consumer_inbox search rows=${inboxRows.length}`);

  const esHits = await waitFor('Elasticsearch indexed hits', async () => {
    const hits = await searchElasticsearch(workspace.id, keyword);
    return hits.length >= 2 ? hits : null;
  }, 30000);

  console.log(`[kafka-es-e2e] elasticsearch hits=${esHits.length}`);

  const apiHits = await waitFor('Core search API hits from Elasticsearch', async () => {
    const hits = await api(
      auth.accessToken,
      `/api/v1/workspaces/${workspace.id}/search?keyword=${encodeURIComponent(keyword)}`
    );
    return Array.isArray(hits) && hits.length >= 2 ? hits : null;
  }, 30000);

  console.log(`[kafka-es-e2e] core search hits=${apiHits.length}`);
  console.log(`[kafka-es-e2e] PASS workspace=${workspace.id} document=${document.id} block=${block.id} keyword=${keyword}`);
}

async function assertCore() {
  const response = await fetch(`${coreBaseUrl}/actuator/health`);
  if (!response.ok && response.status !== 401) {
    throw new Error(`Core health check failed: ${response.status} ${await response.text()}`);
  }
}

async function assertElasticsearch() {
  const response = await fetch(`${elasticsearchUrl}/_cluster/health`);
  if (!response.ok) {
    throw new Error(`Elasticsearch health check failed: ${response.status} ${await response.text()}`);
  }
}

function ensureKafkaTopic() {
  execFileSync('docker', [
    'exec',
    'devcollab-kafka',
    '/opt/kafka/bin/kafka-topics.sh',
    '--bootstrap-server',
    'localhost:9092',
    '--create',
    '--if-not-exists',
    '--topic',
    topic,
    '--partitions',
    '1',
    '--replication-factor',
    '1',
  ], { stdio: 'pipe' });
}

async function register(payload) {
  return api(null, '/api/v1/auth/register', {
    method: 'POST',
    body: payload,
  });
}

async function api(accessToken, path, options = {}) {
  const response = await fetch(`${coreBaseUrl}${path}`, {
    method: options.method ?? 'GET',
    headers: {
      ...(accessToken ? { Authorization: `Bearer ${accessToken}` } : {}),
      ...(options.body ? { 'Content-Type': 'application/json' } : {}),
    },
    body: options.body ? JSON.stringify(options.body) : undefined,
  });

  if (!response.ok) {
    const text = await response.text();
    throw new Error(`${options.method ?? 'GET'} ${path} failed: ${response.status} ${text}`);
  }

  if (response.status === 204) {
    return null;
  }

  return response.json();
}

function queryOutbox(documentId, blockId) {
  const sql = `
    select event_type, status, retry_count, coalesce(left(last_error, 160), '') as last_error
      from outbox_events
     where aggregate_id in ('${documentId}'::uuid, '${blockId}'::uuid)
     order by occurred_at asc;
  `;

  return psql(sql)
    .filter(Boolean)
    .map(line => {
      const [eventType, status, retryCount, lastError] = line.split('|');
      return { eventType, status, retryCount: Number(retryCount), lastError };
    });
}

function querySearchConsumerInbox(documentId, blockId) {
  const sql = `
    select oe.event_type, ci.consumer_name, ci.consumed_at
      from consumer_inbox ci
      join outbox_events oe on oe.id = ci.event_id
     where oe.aggregate_id in ('${documentId}'::uuid, '${blockId}'::uuid)
       and ci.consumer_name = 'search-projection'
     order by ci.consumed_at asc;
  `;

  return psql(sql)
    .filter(Boolean)
    .map(line => {
      const [eventType, consumerName, consumedAt] = line.split('|');
      return { eventType, consumerName, consumedAt };
    });
}

function psql(sql) {
  const output = execFileSync('docker', [
    'exec',
    'devcollab-postgres',
    'psql',
    '-U',
    'devcollab',
    '-d',
    'devcollab',
    '-t',
    '-A',
    '-F',
    '|',
    '-c',
    sql,
  ], { encoding: 'utf8' });

  return output
    .split(/\r?\n/)
    .map(line => line.trim())
    .filter(line => line.length > 0);
}

async function searchElasticsearch(workspaceId, term) {
  const response = await fetch(`${elasticsearchUrl}/${elasticsearchIndex}/_search`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      size: 10,
      query: {
        bool: {
          filter: [
            { term: { workspaceId } },
          ],
          must: [
            { match: { text: { query: term } } },
          ],
        },
      },
    }),
  });

  if (response.status === 404) {
    return [];
  }

  if (!response.ok) {
    throw new Error(`Elasticsearch search failed: ${response.status} ${await response.text()}`);
  }

  const body = await response.json();
  return body?.hits?.hits ?? [];
}

async function waitFor(description, action, timeoutMs) {
  const startedAt = Date.now();
  let lastError;

  while (Date.now() - startedAt < timeoutMs) {
    try {
      const result = await action();
      if (result) {
        return result;
      }
    } catch (error) {
      lastError = error;
    }
    await delay(1000);
  }

  throw new Error(`Timed out waiting for ${description}${lastError ? `: ${lastError.message}` : ''}`);
}

function delay(ms) {
  return new Promise(resolve => setTimeout(resolve, ms));
}

main().catch(error => {
  console.error(`[kafka-es-e2e] FAIL ${error.message}`);
  process.exitCode = 1;
});
