#!/usr/bin/env node

import { randomUUID } from 'node:crypto';

const coreUrl = option('core-url', 'http://localhost:8080').replace(/\/$/, '');
const gatewayUrl = option('gateway-url', 'ws://localhost:8090').replace(/\/$/, '');
const password = 'Gateway@123456';

async function main() {
  if (typeof WebSocket === 'undefined') {
    throw new Error('This script requires a Node.js runtime with global WebSocket support.');
  }

  const username = `gateway_${Date.now()}`;
  const client = new ApiClient(coreUrl);
  const auth = await client.post('/api/v1/auth/register', {
    username,
    displayName: `Gateway E2E ${new Date().toISOString()}`,
    password,
  });
  client.accessToken = auth.accessToken;

  const workspace = await client.post('/api/v1/workspaces', {
    name: `Gateway E2E Workspace ${Date.now()}`,
  });
  const document = await client.post(`/api/v1/workspaces/${workspace.id}/documents`, {
    title: `Gateway E2E Document ${Date.now()}`,
    documentType: 'REQUIREMENT',
  });
  const block = await client.post(`/api/v1/documents/${document.id}/blocks`, {
    type: 'PARAGRAPH',
    content: {
      text: 'initial text',
    },
  });

  console.log(`[gateway-e2e] seed workspace=${workspace.id} document=${document.id} block=${block.id}`);

  const wsA = await connect('A', workspace.id, document.id, auth.accessToken);
  const wsB = await connect('B', workspace.id, document.id, auth.accessToken);

  const operationId = randomUUID();
  wsA.send(JSON.stringify({
    type: 'DOCUMENT_OPERATION',
    clientOperationId: operationId,
    operationType: 'UPDATE_TEXT',
    blockId: block.id,
    expectedVersion: block.version,
    content: {
      text: 'updated through gateway',
    },
  }));

  const applied = await waitForMessage(wsA, 'DOCUMENT_OPERATION_RESULT', message =>
    message.payload?.clientOperationId === operationId
    && message.payload?.status === 'APPLIED',
  );
  console.log(`[gateway-e2e] A result=${applied.payload.status} version=${applied.payload.block.version}`);

  const broadcast = await waitForMessage(wsB, 'DOCUMENT_OPERATION_BROADCAST', message =>
    message.payload?.clientOperationId === operationId
    && message.payload?.blockId === block.id,
  );
  console.log(`[gateway-e2e] B broadcast operation=${broadcast.payload.operationType} version=${broadcast.payload.block.version}`);

  wsA.send(JSON.stringify({
    type: 'DOCUMENT_OPERATION',
    clientOperationId: operationId,
    operationType: 'UPDATE_TEXT',
    blockId: block.id,
    expectedVersion: block.version,
    content: {
      text: 'duplicate update through gateway',
    },
  }));

  const duplicate = await waitForMessage(wsA, 'DOCUMENT_OPERATION_RESULT', message =>
    message.payload?.clientOperationId === operationId
    && message.payload?.status === 'DUPLICATE',
  );
  console.log(`[gateway-e2e] duplicate result=${duplicate.payload.status}`);

  const conflictOperationId = randomUUID();
  wsA.send(JSON.stringify({
    type: 'DOCUMENT_OPERATION',
    clientOperationId: conflictOperationId,
    operationType: 'UPDATE_TEXT',
    blockId: block.id,
    expectedVersion: block.version,
    content: {
      text: 'conflicting update through gateway',
    },
  }));

  const conflict = await waitForMessage(wsA, 'DOCUMENT_OPERATION_RESULT', message =>
    message.payload?.clientOperationId === conflictOperationId
    && message.payload?.status === 'CONFLICT',
  );
  console.log(`[gateway-e2e] conflict result=${conflict.payload.status}`);

  wsA.close();
  wsB.close();
  console.log('[gateway-e2e] PASS');
}

async function connect(label, workspaceId, documentId, accessToken) {
  const url = `${gatewayUrl}/ws/documents/${documentId}`
    + `?workspaceId=${workspaceId}&token=${encodeURIComponent(accessToken)}`;
  const socket = new WebSocket(url);
  socket.__messages = [];
  socket.addEventListener('message', event => {
    try {
      socket.__messages.push(JSON.parse(event.data));
    } catch {
      socket.__messages.push({ type: '<malformed>', raw: event.data });
    }
  });

  await new Promise((resolve, reject) => {
    const timeout = setTimeout(() => reject(
      new Error(`Timed out opening WebSocket ${label}`)
    ), 10000);
    socket.addEventListener('open', () => {
      clearTimeout(timeout);
      console.log(`[gateway-e2e] websocket ${label}=open`);
      resolve();
    }, { once: true });
    socket.addEventListener('error', () => {
      clearTimeout(timeout);
      reject(new Error(`WebSocket ${label} failed to connect`));
    }, { once: true });
  });

  return socket;
}

async function waitForMessage(socket, type, predicate, timeoutMs = 10000) {
  const startedAt = Date.now();
  while (Date.now() - startedAt < timeoutMs) {
    const index = socket.__messages.findIndex(message =>
      message.type === type && predicate(message)
    );
    if (index >= 0) {
      const [message] = socket.__messages.splice(index, 1);
      return message;
    }
    await delay(100);
  }
  throw new Error(`Timed out waiting for ${type}; seen=${JSON.stringify(socket.__messages)}`);
}

function option(name, fallback) {
  const index = process.argv.indexOf(`--${name}`);
  if (index === -1) {
    return fallback;
  }
  return process.argv[index + 1] ?? fallback;
}

function delay(ms) {
  return new Promise(resolve => setTimeout(resolve, ms));
}

class ApiClient {
  constructor(baseUrl) {
    this.baseUrl = baseUrl;
    this.accessToken = null;
  }

  async post(path, body) {
    return this.request('POST', path, body);
  }

  async request(method, path, body) {
    let response;
    try {
      response = await fetch(`${this.baseUrl}${path}`, {
        method,
        headers: {
          Accept: 'application/json',
          ...(body === undefined ? {} : { 'Content-Type': 'application/json' }),
          ...(this.accessToken ? { Authorization: `Bearer ${this.accessToken}` } : {}),
        },
        body: body === undefined ? undefined : JSON.stringify(body),
      });
    } catch (error) {
      const cause = error instanceof Error && error.cause instanceof Error
        ? `: ${error.cause.message}`
        : '';
      throw new Error(`${method} ${this.baseUrl}${path} failed to connect${cause}`);
    }

    const text = await response.text();
    if (!response.ok) {
      throw new Error(`${method} ${path} failed: ${response.status} ${text}`);
    }
    return text ? JSON.parse(text) : null;
  }
}

main().catch(error => {
  console.error(`[gateway-e2e] FAIL ${error.message}`);
  process.exitCode = 1;
});
