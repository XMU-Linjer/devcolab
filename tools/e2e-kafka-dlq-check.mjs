#!/usr/bin/env node

import { execFileSync } from 'node:child_process';
import { randomUUID } from 'node:crypto';

const topic = process.env.DEVCOLLAB_KAFKA_DOCUMENT_TOPIC ?? 'devcollab.document.events';
const dlqTopic = process.env.DEVCOLLAB_KAFKA_DEAD_LETTER_TOPIC ?? 'devcollab.dead-letter';
const eventId = randomUUID();
const aggregateId = randomUUID();

async function main() {
  ensureTopic(topic);
  ensureTopic(dlqTopic);
  const beforeDlqOffset = latestOffset(dlqTopic);

  const message = JSON.stringify({
    eventId,
    aggregateType: 'DOCUMENT',
    aggregateId,
    eventType: 'DOCUMENT_CREATED',
    payload: JSON.stringify({
      // Deliberately missing workspaceId/title/updatedAt so search projection fails.
      documentId: aggregateId,
    }),
    occurredAt: new Date().toISOString(),
  });

  produce(topic, message);
  console.log(`[kafka-dlq-e2e] produced poison event eventId=${eventId} topic=${topic}`);

  const afterDlqOffset = await waitFor('DLQ offset increment', () => {
    const currentOffset = latestOffset(dlqTopic);
    return currentOffset > beforeDlqOffset ? currentOffset : null;
  }, 30000);

  const inboxCount = Number(psql(`
    select count(*)
      from consumer_inbox
     where consumer_name = 'search-projection'
       and event_id = '${eventId}'::uuid;
  `)[0] ?? '0');

  if (inboxCount !== 0) {
    throw new Error(`Expected no consumer_inbox row for failed event, got ${inboxCount}`);
  }

  console.log(`[kafka-dlq-e2e] dlq offset ${beforeDlqOffset}->${afterDlqOffset}`);
  console.log('[kafka-dlq-e2e] consumer_inbox rows=0');
  console.log(`[kafka-dlq-e2e] PASS eventId=${eventId}`);
}

function ensureTopic(topicName) {
  execFileSync('docker', [
    'exec',
    'devcollab-kafka',
    '/opt/kafka/bin/kafka-topics.sh',
    '--bootstrap-server',
    'localhost:9092',
    '--create',
    '--if-not-exists',
    '--topic',
    topicName,
    '--partitions',
    '1',
    '--replication-factor',
    '1',
  ], { stdio: 'pipe' });
}

function produce(topicName, message) {
  execFileSync('docker', [
    'exec',
    '-i',
    'devcollab-kafka',
    '/opt/kafka/bin/kafka-console-producer.sh',
    '--bootstrap-server',
    'localhost:9092',
    '--topic',
    topicName,
  ], {
    input: `${message}\n`,
    encoding: 'utf8',
    stdio: ['pipe', 'pipe', 'pipe'],
  });
}

function latestOffset(topicName) {
  const output = execFileSync('docker', [
    'exec',
    'devcollab-kafka',
    '/opt/kafka/bin/kafka-get-offsets.sh',
    '--bootstrap-server',
    'localhost:9092',
    '--topic',
    topicName,
  ], { encoding: 'utf8' });

  return output
    .split(/\r?\n/)
    .map(line => line.trim())
    .filter(Boolean)
    .map(line => Number(line.split(':').at(-1)))
    .reduce((sum, value) => sum + value, 0);
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
    '-c',
    sql,
  ], { encoding: 'utf8' });

  return output
    .split(/\r?\n/)
    .map(line => line.trim())
    .filter(line => line.length > 0);
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
  console.error(`[kafka-dlq-e2e] FAIL ${error.message}`);
  process.exitCode = 1;
});
