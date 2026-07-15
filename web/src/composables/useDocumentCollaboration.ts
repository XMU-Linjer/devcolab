import { onUnmounted, ref, watch, type Ref } from 'vue';

import { getAccessToken } from '@/api/http';

export interface PresenceMember {
  sessionId: string;
  userId: string;
  username: string;
  joinedAt: string;
}

export interface EditingState {
  blockId: string;
  userId: string;
  username: string;
  startedAt: string;
}

interface ServerMessage<T = unknown> {
  type: string;
  payload: T;
}

export function useDocumentCollaboration(
  workspaceId: Ref<string>,
  documentId: Ref<string>,
) {
  const connected = ref(false);
  const members = ref<PresenceMember[]>([]);
  const editingStates = ref<EditingState[]>([]);
  const errorMessage = ref('');

  let socket: WebSocket | null = null;
  let heartbeatTimer: number | null = null;

  watch(
    [workspaceId, documentId],
    () => {
      connect();
    },
    { immediate: true },
  );

  onUnmounted(() => {
    disconnect();
  });

  function connect() {
    disconnect();

    const token = getAccessToken();
    if (!token || !workspaceId.value || !documentId.value) {
      return;
    }

    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    const params = new URLSearchParams({
      workspaceId: workspaceId.value,
      token,
    });

    socket = new WebSocket(
      `${protocol}//${window.location.host}/ws/documents/${documentId.value}?${params}`,
    );

    socket.addEventListener('open', () => {
      connected.value = true;
      errorMessage.value = '';
      heartbeatTimer = window.setInterval(() => {
        send({ type: 'HEARTBEAT' });
      }, 25_000);
    });

    socket.addEventListener('message', (event) => {
      handleMessage(event.data);
    });

    socket.addEventListener('close', () => {
      connected.value = false;
      clearHeartbeat();
    });

    socket.addEventListener('error', () => {
      errorMessage.value = '协作网关连接异常';
      connected.value = false;
    });
  }

  function disconnect() {
    clearHeartbeat();
    if (socket && socket.readyState !== WebSocket.CLOSED) {
      socket.close();
    }
    socket = null;
    connected.value = false;
    members.value = [];
    editingStates.value = [];
  }

  function startEditing(blockId: string) {
    send({
      type: 'BLOCK_EDITING_STARTED',
      blockId,
    });
  }

  function stopEditing(blockId: string) {
    send({
      type: 'BLOCK_EDITING_STOPPED',
      blockId,
    });
  }

  function send(message: Record<string, unknown>) {
    if (!socket || socket.readyState !== WebSocket.OPEN) {
      return;
    }
    socket.send(JSON.stringify(message));
  }

  function handleMessage(raw: string) {
    try {
      const message = JSON.parse(raw) as ServerMessage;
      if (message.type === 'PRESENCE_UPDATED') {
        members.value = message.payload as PresenceMember[];
        return;
      }
      if (message.type === 'EDITING_UPDATED') {
        editingStates.value = message.payload as EditingState[];
        return;
      }
      if (message.type === 'ERROR') {
        errorMessage.value = String(
          (message.payload as { message?: string })?.message ?? '协作消息处理失败',
        );
      }
    } catch {
      errorMessage.value = '协作消息格式异常';
    }
  }

  function clearHeartbeat() {
    if (heartbeatTimer !== null) {
      window.clearInterval(heartbeatTimer);
      heartbeatTimer = null;
    }
  }

  return {
    connected,
    members,
    editingStates,
    errorMessage,
    startEditing,
    stopEditing,
    reconnect: connect,
    disconnect,
  };
}
