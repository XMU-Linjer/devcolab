#!/usr/bin/env node

import { randomUUID } from 'node:crypto';

const coreUrl = option('core-url', 'http://localhost:8080').replace(/\/$/, '');
const gatewayUrl = option('gateway-url', 'ws://localhost:8090').replace(/\/$/, '');
const password = 'Gateway@123456';

async function main() {
  if (typeof WebSocket === 'undefined') {
    throw new Error('This script requires a Node.js runtime with global WebSocket support.');
  }

  const suffix = Date.now();
  const usernameA = `gateway_a_${suffix}`;
  const usernameB = `gateway_b_${suffix}`;
  const clientA = new ApiClient(coreUrl);
  const clientB = new ApiClient(coreUrl);
  const authA = await clientA.post('/api/v1/auth/register', {
    username: usernameA,
    displayName: `Gateway E2E A ${new Date().toISOString()}`,
    password,
  });
  clientA.accessToken = authA.accessToken;
  const authB = await clientB.post('/api/v1/auth/register', {
    username: usernameB,
    displayName: `Gateway E2E B ${new Date().toISOString()}`,
    password,
  });
  clientB.accessToken = authB.accessToken;

  const workspace = await clientA.post('/api/v1/workspaces', {
    name: `Gateway E2E Workspace ${Date.now()}`,
  });
  await clientA.post(`/api/v1/workspaces/${workspace.id}/members/invitations`, {
    username: usernameB,
    role: 'MEMBER',
  });
  const document = await clientA.post(`/api/v1/workspaces/${workspace.id}/documents`, {
    title: `Gateway E2E Document ${Date.now()}`,
    documentType: 'REQUIREMENT',
  });
  const block = await clientA.post(`/api/v1/documents/${document.id}/blocks`, {
    type: 'PARAGRAPH',
    content: {
      text: 'initial text',
    },
  });

  console.log(`[gateway-e2e] seed workspace=${workspace.id} document=${document.id} block=${block.id}`);

  const wsA = await connect('A', workspace.id, document.id, authA.accessToken);
  const wsB = await connect('B', workspace.id, document.id, authB.accessToken);

  const initialSnapshot = await waitForMessage(wsB, 'ROOM_STATE_SNAPSHOT', message =>
    Array.isArray(message.payload?.members)
    && message.payload.members.some(member => member.username === usernameA)
    && message.payload.members.some(member => member.username === usernameB),
  );
  console.log(`[gateway-e2e] B snapshot members=${initialSnapshot.payload.members.length} editing=${initialSnapshot.payload.editingStates.length}`);

  wsA.send(JSON.stringify({
    type: 'BLOCK_EDITING_STARTED',
    blockId: block.id,
  }));

  const editingUpdate = await waitForMessage(wsB, 'EDITING_UPDATED', message =>
    Array.isArray(message.payload)
    && message.payload.some(state => state.blockId === block.id),
  );
  console.log(`[gateway-e2e] B editing states=${editingUpdate.payload.length}`);

  wsB.close();
  await waitForClose(wsB);

  const wsB2 = await connect('B-reconnect', workspace.id, document.id, authB.accessToken);
  const reconnectSnapshot = await waitForMessage(wsB2, 'ROOM_STATE_SNAPSHOT', message =>
    Array.isArray(message.payload?.editingStates)
    && message.payload.editingStates.some(state => state.blockId === block.id),
  );
  console.log(`[gateway-e2e] B reconnect snapshot members=${reconnectSnapshot.payload.members.length} editing=${reconnectSnapshot.payload.editingStates.length}`);

  const operationId = randomUUID();
  const operationRequest = {
    type: 'DOCUMENT_OPERATION',
    clientOperationId: operationId,
    operationType: 'UPDATE_TEXT',
    blockId: block.id,
    expectedVersion: block.version,
    content: {
      text: 'updated through gateway',
    },
  };
  wsA.send(JSON.stringify(operationRequest));

  const applied = await waitForMessage(wsA, 'DOCUMENT_OPERATION_RESULT', message =>
    message.payload?.clientOperationId === operationId
    && message.payload?.status === 'APPLIED',
  );
  console.log(`[gateway-e2e] A result=${applied.payload.status} sequence=${applied.payload.documentSequence} version=${applied.payload.block.version}`);

  const broadcast = await waitForMessage(wsB2, 'DOCUMENT_OPERATION_BROADCAST', message =>
    message.payload?.clientOperationId === operationId
    && message.payload?.blockId === block.id,
  );
  console.log(`[gateway-e2e] B reconnect broadcast operation=${broadcast.payload.operationType} version=${broadcast.payload.block.version}`);

  wsA.send(JSON.stringify(operationRequest));

  const duplicate = await waitForMessage(wsA, 'DOCUMENT_OPERATION_RESULT', message =>
    message.payload?.clientOperationId === operationId
    && message.payload?.status === 'DUPLICATE',
  );
  if (duplicate.payload.documentSequence !== applied.payload.documentSequence
    || duplicate.payload.block?.version !== applied.payload.block.version) {
    throw new Error('Duplicate operation did not return the original result');
  }
  console.log(`[gateway-e2e] duplicate result=${duplicate.payload.status} sequence=${duplicate.payload.documentSequence}`);

  wsA.send(JSON.stringify({
    ...operationRequest,
    content: {
      text: 'same id with changed content',
    },
  }));

  const reused = await waitForMessage(wsA, 'DOCUMENT_OPERATION_RESULT', message =>
    message.payload?.clientOperationId === operationId
    && message.payload?.status === 'CONFLICT',
  );
  console.log(`[gateway-e2e] reused operation id result=${reused.payload.status}`);

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

  const createOperationId = randomUUID();
  const createRequest = {
    type: 'DOCUMENT_OPERATION',
    clientOperationId: createOperationId,
    operationType: 'CREATE_BLOCK',
    blockType: 'PARAGRAPH',
    content: {
      text: 'created through gateway',
    },
  };
  wsA.send(JSON.stringify(createRequest));
  const created = await waitForMessage(wsA, 'DOCUMENT_OPERATION_RESULT', message =>
    message.payload?.clientOperationId === createOperationId
    && message.payload?.status === 'APPLIED',
  );
  const createdBlockId = created.payload.block.id;
  console.log(`[gateway-e2e] create result=${created.payload.status} sequence=${created.payload.documentSequence} block=${createdBlockId}`);
  await waitForMessage(wsB2, 'DOCUMENT_OPERATION_BROADCAST', message =>
    message.payload?.clientOperationId === createOperationId
    && message.payload?.blockId === createdBlockId,
  );

  wsA.send(JSON.stringify(createRequest));
  const duplicateCreate = await waitForMessage(wsA, 'DOCUMENT_OPERATION_RESULT', message =>
    message.payload?.clientOperationId === createOperationId
    && message.payload?.status === 'DUPLICATE',
  );
  if (duplicateCreate.payload.block?.id !== createdBlockId
    || duplicateCreate.payload.documentSequence !== created.payload.documentSequence) {
    throw new Error('Duplicate create did not return the original block');
  }

  const moveOperationId = randomUUID();
  wsA.send(JSON.stringify({
    type: 'DOCUMENT_OPERATION',
    clientOperationId: moveOperationId,
    operationType: 'MOVE_BLOCK',
    blockId: createdBlockId,
    targetIndex: 0,
  }));
  const moved = await waitForMessage(wsA, 'DOCUMENT_OPERATION_RESULT', message =>
    message.payload?.clientOperationId === moveOperationId
    && message.payload?.status === 'APPLIED',
  );
  if (moved.payload.blocks?.[0]?.id !== createdBlockId) {
    throw new Error('Move result did not return the authoritative block order');
  }
  console.log(`[gateway-e2e] move result=${moved.payload.status} sequence=${moved.payload.documentSequence} blocks=${moved.payload.blocks.length}`);
  await waitForMessage(wsB2, 'DOCUMENT_OPERATION_BROADCAST', message =>
    message.payload?.clientOperationId === moveOperationId
    && message.payload?.blocks?.[0]?.id === createdBlockId,
  );

  const deleteOperationId = randomUUID();
  const deleteRequest = {
    type: 'DOCUMENT_OPERATION',
    clientOperationId: deleteOperationId,
    operationType: 'DELETE_BLOCK',
    blockId: createdBlockId,
    expectedVersion: moved.payload.block.version,
  };
  wsA.send(JSON.stringify(deleteRequest));
  const deleted = await waitForMessage(wsA, 'DOCUMENT_OPERATION_RESULT', message =>
    message.payload?.clientOperationId === deleteOperationId
    && message.payload?.status === 'APPLIED',
  );
  if (deleted.payload.blocks?.some(item => item.id === createdBlockId)) {
    throw new Error('Delete result still contains the deleted block');
  }
  console.log(`[gateway-e2e] delete result=${deleted.payload.status} sequence=${deleted.payload.documentSequence} version=${deleted.payload.block.version}`);
  await waitForMessage(wsB2, 'DOCUMENT_OPERATION_BROADCAST', message =>
    message.payload?.clientOperationId === deleteOperationId
    && message.payload?.blockId === createdBlockId,
  );

  wsA.send(JSON.stringify(deleteRequest));
  const duplicateDelete = await waitForMessage(wsA, 'DOCUMENT_OPERATION_RESULT', message =>
    message.payload?.clientOperationId === deleteOperationId
    && message.payload?.status === 'DUPLICATE',
  );
  if (duplicateDelete.payload.block?.id !== createdBlockId
    || duplicateDelete.payload.documentSequence !== deleted.payload.documentSequence) {
    throw new Error('Duplicate delete did not return the original tombstone');
  }

  const finalBlocks = await clientA.get(`/api/v1/documents/${document.id}/blocks`);
  if (finalBlocks.some(item => item.id === createdBlockId)) {
    throw new Error('Deleted block is still present in Core');
  }
  const sequences = [
    applied.payload.documentSequence,
    created.payload.documentSequence,
    moved.payload.documentSequence,
    deleted.payload.documentSequence,
  ];
  if (sequences.join(',') !== '1,2,3,4') {
    throw new Error(`Unexpected document sequence chain: ${sequences.join(',')}`);
  }

  wsA.close();
  wsB2.close();
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

async function waitForClose(socket, timeoutMs = 5000) {
  if (socket.readyState === WebSocket.CLOSED) {
    return;
  }
  await new Promise((resolve, reject) => {
    const timeout = setTimeout(() => reject(
      new Error('Timed out waiting for WebSocket close')
    ), timeoutMs);
    socket.addEventListener('close', () => {
      clearTimeout(timeout);
      resolve();
    }, { once: true });
  });
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

  async get(path) {
    return this.request('GET', path);
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
