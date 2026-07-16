#!/usr/bin/env node

import { execFileSync } from 'node:child_process';

const coreBaseUrl = process.env.DEVCOLLAB_CORE_BASE_URL ?? 'http://localhost:8080';
const topic = process.env.DEVCOLLAB_OUTBOX_KAFKA_TOPIC ?? 'devcollab.domain-events';
const suffix = new Date().toISOString().replace(/\D/g, '').slice(0, 14);
const password = 'Password123!';
const waitTimeoutMs = Number(process.env.DEVCOLLAB_E2E_WAIT_TIMEOUT_MS ?? '60000');

async function main() {
  await assertCore();
  ensureKafkaTopic();

  const author = await register({
    username: `notify_author_${suffix}`,
    displayName: `Notify E2E Author ${suffix}`,
    password,
  });
  const admin = await register({
    username: `notify_admin_${suffix}`,
    displayName: `Notify E2E Admin ${suffix}`,
    password,
  });

  const workspace = await api(author.accessToken, '/api/v1/workspaces', {
    method: 'POST',
    body: {
      name: `Kafka Notification E2E Workspace ${suffix}`,
    },
  });

  await api(author.accessToken, `/api/v1/workspaces/${workspace.id}/members/invitations`, {
    method: 'POST',
    body: {
      username: admin.username,
      role: 'ADMIN',
    },
  });

  const document = await api(author.accessToken, `/api/v1/workspaces/${workspace.id}/documents`, {
    method: 'POST',
    body: {
      title: `Kafka Notification E2E Document ${suffix}`,
      documentType: 'REQUIREMENT',
    },
  });

  await api(author.accessToken, `/api/v1/documents/${document.id}/blocks`, {
    method: 'POST',
    body: {
      type: 'PARAGRAPH',
      content: {
        text: `Kafka notification E2E content ${suffix}`,
      },
    },
  });

  console.log(`[kafka-notification-e2e] created workspace=${workspace.id} document=${document.id}`);

  await api(author.accessToken, `/api/v1/documents/${document.id}/submit-review`, {
    method: 'POST',
  });

  const submittedOutbox = await waitForOutboxPublished(document.id, 'DOCUMENT_REVIEW_SUBMITTED');
  console.log(`[kafka-notification-e2e] submitted outbox status=${submittedOutbox.status}`);

  await waitForNotificationConsumerInbox(document.id, 'DOCUMENT_REVIEW_SUBMITTED');
  console.log('[kafka-notification-e2e] notification consumer consumed DOCUMENT_REVIEW_SUBMITTED');

  const adminNotification = await waitForNotification(
    admin.accessToken,
    document.id,
    'DOCUMENT_REVIEW_SUBMITTED',
    '文档待评审：'
  );
  console.log(`[kafka-notification-e2e] admin notification=${adminNotification.id} title=${adminNotification.title}`);

  const readNotification = await api(
    admin.accessToken,
    `/api/v1/notifications/${adminNotification.id}/read`,
    { method: 'PATCH' }
  );
  if (readNotification.unread !== false) {
    throw new Error(`Expected admin notification to become read, got unread=${readNotification.unread}`);
  }

  await api(admin.accessToken, `/api/v1/documents/${document.id}/approve-review`, {
    method: 'POST',
    body: {
      comment: 'Kafka notification E2E approve',
    },
  });

  const approvedOutbox = await waitForOutboxPublished(document.id, 'DOCUMENT_REVIEW_APPROVED');
  console.log(`[kafka-notification-e2e] approved outbox status=${approvedOutbox.status}`);

  await waitForNotificationConsumerInbox(document.id, 'DOCUMENT_REVIEW_APPROVED');
  console.log('[kafka-notification-e2e] notification consumer consumed DOCUMENT_REVIEW_APPROVED');

  const authorNotification = await waitForNotification(
    author.accessToken,
    document.id,
    'DOCUMENT_REVIEW_APPROVED',
    '文档已发布：'
  );
  console.log(`[kafka-notification-e2e] author notification=${authorNotification.id} title=${authorNotification.title}`);
  console.log(`[kafka-notification-e2e] PASS workspace=${workspace.id} document=${document.id}`);
}

async function assertCore() {
  const response = await fetch(`${coreBaseUrl}/actuator/health`);
  if (!response.ok && response.status !== 401) {
    throw new Error(`Core health check failed: ${response.status} ${await response.text()}`);
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

async function waitForOutboxPublished(documentId, eventType) {
  return waitFor(`${eventType} outbox published`, () => {
    const rows = queryOutbox(documentId, eventType);
    const published = rows.find(row => row.status === 'PUBLISHED');
    return published ?? null;
  }, waitTimeoutMs, () => {
    printDiagnostics(documentId);
    printOutboxHint(documentId, eventType);
  });
}

function queryOutbox(documentId, eventType) {
  const sql = `
    select event_type, status, retry_count, coalesce(left(last_error, 160), '') as last_error
      from outbox_events
     where aggregate_id = '${documentId}'::uuid
       and event_type = '${eventType}'
     order by occurred_at desc;
  `;

  return psql(sql)
    .filter(Boolean)
    .map(line => {
      const [rowEventType, status, retryCount, lastError] = line.split('|');
      return { eventType: rowEventType, status, retryCount: Number(retryCount), lastError };
    });
}

async function waitForNotificationConsumerInbox(documentId, eventType) {
  return waitFor(`${eventType} notification consumer inbox`, () => {
    const rows = queryNotificationConsumerInbox(documentId, eventType);
    return rows.length > 0 ? rows : null;
  }, waitTimeoutMs, () => printDiagnostics(documentId));
}

function queryNotificationConsumerInbox(documentId, eventType) {
  const sql = `
    select oe.event_type, ci.consumer_name, ci.consumed_at
      from consumer_inbox ci
      join outbox_events oe on oe.id = ci.event_id
     where oe.aggregate_id = '${documentId}'::uuid
       and oe.event_type = '${eventType}'
       and ci.consumer_name = 'notification-projection'
     order by ci.consumed_at desc;
  `;

  return psql(sql)
    .filter(Boolean)
    .map(line => {
      const [rowEventType, consumerName, consumedAt] = line.split('|');
      return { eventType: rowEventType, consumerName, consumedAt };
    });
}

async function waitForNotification(accessToken, documentId, type, titlePrefix) {
  return waitFor(`${type} notification API result`, async () => {
    const notifications = await api(
      accessToken,
      '/api/v1/notifications?unreadOnly=true&limit=20'
    );
    const match = notifications.find(notification =>
      notification.documentId === documentId
      && notification.type === type
      && notification.title?.startsWith(titlePrefix)
      && notification.unread === true
    );
    return match ?? null;
  }, waitTimeoutMs, () => printDiagnostics(documentId));
}

function printDiagnostics(documentId) {
  console.error(`[kafka-notification-e2e] diagnostics document=${documentId}`);
  printRows('outbox_events', `
    select id, event_type, status, retry_count,
           coalesce(left(last_error, 240), '') as last_error,
           occurred_at, published_at
      from outbox_events
     where aggregate_id = '${documentId}'::uuid
     order by occurred_at desc
     limit 20;
  `);
  printRows('consumer_inbox', `
    select oe.event_type, ci.consumer_name, ci.consumed_at
      from consumer_inbox ci
      join outbox_events oe on oe.id = ci.event_id
     where oe.aggregate_id = '${documentId}'::uuid
     order by ci.consumed_at desc
     limit 20;
  `);
  printRows('notifications', `
    select type, title, recipient_user_id, read_at, created_at
      from notifications
     where document_id = '${documentId}'::uuid
     order by created_at desc
     limit 20;
  `);
}

function printOutboxHint(documentId, eventType) {
  const rows = queryOutbox(documentId, eventType);
  const latest = rows[0];
  if (!latest) {
    console.error(`[kafka-notification-e2e] hint no ${eventType} outbox row found; check whether the business API created the event.`);
    return;
  }
  if (latest.status === 'PENDING' && latest.retryCount === 0 && !latest.lastError) {
    console.error('[kafka-notification-e2e] hint outbox event is still PENDING with retry_count=0 and empty last_error.');
    console.error('[kafka-notification-e2e] hint Core probably started without DEVCOLLAB_OUTBOX_WORKER_ENABLED=true, or the scheduler has not run yet.');
    console.error('[kafka-notification-e2e] hint restart Knowledge Core with DEVCOLLAB_OUTBOX_WORKER_ENABLED=true before running this script.');
  }
  if (latest.status === 'FAILED') {
    console.error(`[kafka-notification-e2e] hint outbox publish failed; inspect last_error=${latest.lastError || '<empty>'}`);
  }
}

function printRows(label, sql) {
  try {
    const rows = psql(sql);
    console.error(`[kafka-notification-e2e] ${label} rows=${rows.length}`);
    for (const row of rows) {
      console.error(`[kafka-notification-e2e] ${label} ${row}`);
    }
  } catch (error) {
    console.error(`[kafka-notification-e2e] ${label} diagnostics failed: ${error.message}`);
  }
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

async function waitFor(description, action, timeoutMs, onTimeout) {
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

  if (onTimeout) {
    onTimeout();
  }
  throw new Error(`Timed out waiting for ${description}${lastError ? `: ${lastError.message}` : ''}`);
}

function delay(ms) {
  return new Promise(resolve => setTimeout(resolve, ms));
}

main().catch(error => {
  console.error(`[kafka-notification-e2e] FAIL ${error.message}`);
  process.exitCode = 1;
});
