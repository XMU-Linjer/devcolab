#!/usr/bin/env node

const coreBaseUrl = process.env.DEVCOLLAB_CORE_BASE_URL ?? 'http://localhost:8080';
const gatewayBaseUrl = process.env.DEVCOLLAB_GATEWAY_WS_URL ?? 'ws://localhost:8090';
const suffix = new Date()
  .toISOString()
  .replace(/\D/g, '')
  .slice(0, 14);
const password = 'Password123!';

const author = {
  username: `gw_author_${suffix}`,
  displayName: `Gateway Author ${suffix}`,
  password,
};
const collaborator = {
  username: `gw_member_${suffix}`,
  displayName: `Gateway Member ${suffix}`,
  password,
};

async function main() {
  assertWebSocketAvailable();

  const authorAuth = await register(author);
  const collaboratorAuth = await register(collaborator);
  const workspace = await api(authorAuth.accessToken, '/api/v1/workspaces', {
    method: 'POST',
    body: {
      name: `Gateway 联调空间 ${suffix}`,
    },
  });
  await api(authorAuth.accessToken, `/api/v1/workspaces/${workspace.id}/members/invitations`, {
    method: 'POST',
    body: {
      username: collaborator.username,
      role: 'MEMBER',
    },
  });
  const document = await api(authorAuth.accessToken, `/api/v1/workspaces/${workspace.id}/documents`, {
    method: 'POST',
    body: {
      title: `Gateway 联调文档 ${suffix}`,
      documentType: 'REQUIREMENT',
    },
  });
  const block = await api(authorAuth.accessToken, `/api/v1/documents/${document.id}/blocks`, {
    method: 'POST',
    body: {
      type: 'PARAGRAPH',
      content: {
        text: 'Gateway 联调段落',
      },
    },
  });

  const authorSocket = await openCollaborationSocket(
    workspace.id,
    document.id,
    authorAuth.accessToken,
    'author'
  );
  const collaboratorSocket = await openCollaborationSocket(
    workspace.id,
    document.id,
    collaboratorAuth.accessToken,
    'collaborator'
  );

  try {
    const presence = await waitForMessage(
      collaboratorSocket,
      message => message.type === 'PRESENCE_UPDATED'
        && Array.isArray(message.payload)
        && message.payload.length >= 2,
      'collaborator sees two online members'
    );
    console.log(`[gateway-e2e] presence members=${presence.payload.length}`);

    authorSocket.send(JSON.stringify({
      type: 'BLOCK_EDITING_STARTED',
      blockId: block.id,
    }));

    const editingStarted = await waitForMessage(
      collaboratorSocket,
      message => message.type === 'EDITING_UPDATED'
        && Array.isArray(message.payload)
        && message.payload.some(state =>
          state.blockId === block.id
          && state.username === author.username
        ),
      'collaborator sees author editing block'
    );
    console.log(`[gateway-e2e] editing started states=${editingStarted.payload.length}`);

    authorSocket.send(JSON.stringify({
      type: 'BLOCK_EDITING_STOPPED',
      blockId: block.id,
    }));

    const editingStopped = await waitForMessage(
      collaboratorSocket,
      message => message.type === 'EDITING_UPDATED'
        && Array.isArray(message.payload)
        && !message.payload.some(state => state.blockId === block.id),
      'collaborator sees editing state cleared'
    );
    console.log(`[gateway-e2e] editing stopped states=${editingStopped.payload.length}`);
  } finally {
    authorSocket.close();
    collaboratorSocket.close();
  }

  console.log(`[gateway-e2e] PASS workspace=${workspace.id} document=${document.id} block=${block.id}`);
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

async function openCollaborationSocket(workspaceId, documentId, accessToken, label) {
  const url = new URL(`${gatewayBaseUrl}/ws/documents/${documentId}`);
  url.searchParams.set('workspaceId', workspaceId);
  url.searchParams.set('token', accessToken);

  const socket = new WebSocket(url);
  socket.receivedMessages = [];

  socket.addEventListener('message', event => {
    socket.receivedMessages.push(JSON.parse(event.data));
  });

  await waitForOpen(socket, label);
  console.log(`[gateway-e2e] socket open label=${label}`);
  return socket;
}

function waitForOpen(socket, label) {
  return new Promise((resolve, reject) => {
    const timeout = setTimeout(() => {
      reject(new Error(`WebSocket open timeout: ${label}`));
    }, 5000);

    socket.addEventListener('open', () => {
      clearTimeout(timeout);
      resolve();
    }, { once: true });

    socket.addEventListener('error', () => {
      clearTimeout(timeout);
      reject(new Error(`WebSocket error before open: ${label}`));
    }, { once: true });

    socket.addEventListener('close', event => {
      clearTimeout(timeout);
      reject(new Error(`WebSocket closed before open: ${label} code=${event.code}`));
    }, { once: true });
  });
}

function waitForMessage(socket, predicate, description) {
  return new Promise((resolve, reject) => {
    const existing = socket.receivedMessages.find(predicate);
    if (existing) {
      resolve(existing);
      return;
    }

    const timeout = setTimeout(() => {
      socket.removeEventListener('message', onMessage);
      reject(new Error(`Timeout waiting for ${description}`));
    }, 5000);

    function onMessage(event) {
      const message = JSON.parse(event.data);
      if (!predicate(message)) {
        return;
      }
      clearTimeout(timeout);
      socket.removeEventListener('message', onMessage);
      resolve(message);
    }

    socket.addEventListener('message', onMessage);
  });
}

function assertWebSocketAvailable() {
  if (typeof WebSocket === 'undefined') {
    throw new Error('This script requires Node.js with global WebSocket support.');
  }
}

main().catch(error => {
  console.error(`[gateway-e2e] FAIL ${error.message}`);
  process.exitCode = 1;
});
