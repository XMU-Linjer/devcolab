import { onUnmounted, ref, watch, type Ref } from 'vue';

import { getAccessToken } from '@/api/http';
import type { DocumentBlock, DocumentBlockContent } from '@/api/block';

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

interface RoomStateSnapshot {
  members: PresenceMember[];
  editingStates: EditingState[];
}

interface DocumentOperationResult {
  clientOperationId: string;
  blockId: string;
  operationType: string;
  status: 'APPLIED' | 'CONFLICT' | 'REJECTED' | 'DUPLICATE';
  documentSequence?: number;
  block?: DocumentBlock;
  message?: string;
}

interface DocumentOperationBroadcast {
  clientOperationId: string;
  blockId: string;
  operationType: string;
  documentSequence: number;
  userId: string;
  username: string;
  block: DocumentBlock;
}

interface PendingOperation {
  resolve: (block: DocumentBlock) => void;
  reject: (error: CollaborationOperationError) => void;
  timer: number;
  message: Record<string, unknown>;
  retryCount: number;
}

export class CollaborationOperationError extends Error {
  constructor(
    public readonly status: DocumentOperationResult['status'] | 'TIMEOUT' | 'DISCONNECTED',
    message: string,
  ) {
    super(message);
    this.name = 'CollaborationOperationError';
  }
}

export function useDocumentCollaboration(
  workspaceId: Ref<string>,
  documentId: Ref<string>,
) {
  const connected = ref(false);
  const members = ref<PresenceMember[]>([]);
  const editingStates = ref<EditingState[]>([]);
  const latestRemoteBlock = ref<DocumentBlock | null>(null);
  const latestDocumentSequence = ref(0);
  const errorMessage = ref('');

  let socket: WebSocket | null = null;
  let heartbeatTimer: number | null = null;
  const pendingOperations = new Map<string, PendingOperation>();

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
    rejectPendingOperations('DISCONNECTED', '协作网关已断开，请稍后重试');
    if (socket && socket.readyState !== WebSocket.CLOSED) {
      socket.close();
    }
    socket = null;
    connected.value = false;
    members.value = [];
    editingStates.value = [];
    latestRemoteBlock.value = null;
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

  function updateContent(
    blockId: string,
    content: DocumentBlockContent,
    expectedVersion: number,
  ): Promise<DocumentBlock> {
    if (!socket || socket.readyState !== WebSocket.OPEN) {
      return Promise.reject(new CollaborationOperationError(
        'DISCONNECTED',
        '协作网关未连接，暂时无法通过实时链路保存',
      ));
    }

    const clientOperationId = operationId();
    return new Promise<DocumentBlock>((resolve, reject) => {
      const operationMessage = {
        type: 'DOCUMENT_OPERATION',
        clientOperationId,
        operationType: 'UPDATE_TEXT',
        blockId,
        expectedVersion,
        content,
      };
      const timer = scheduleOperationTimeout(clientOperationId);

      pendingOperations.set(clientOperationId, {
        resolve,
        reject,
        timer,
        message: operationMessage,
        retryCount: 0,
      });

      send(operationMessage);
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
      if (message.type === 'ROOM_STATE_SNAPSHOT') {
        const snapshot = message.payload as RoomStateSnapshot;
        members.value = snapshot.members;
        editingStates.value = snapshot.editingStates;
        return;
      }
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
        return;
      }
      if (message.type === 'DOCUMENT_OPERATION_RESULT') {
        handleOperationResult(message.payload as DocumentOperationResult);
        return;
      }
      if (message.type === 'DOCUMENT_OPERATION_BROADCAST') {
        const payload = message.payload as DocumentOperationBroadcast;
        latestDocumentSequence.value = Math.max(
          latestDocumentSequence.value,
          payload.documentSequence,
        );
        latestRemoteBlock.value = payload.block;
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

  function handleOperationResult(result: DocumentOperationResult) {
    const pending = pendingOperations.get(result.clientOperationId);
    if (!pending) {
      return;
    }

    window.clearTimeout(pending.timer);
    pendingOperations.delete(result.clientOperationId);

    if (result.documentSequence !== undefined) {
      latestDocumentSequence.value = Math.max(
        latestDocumentSequence.value,
        result.documentSequence,
      );
    }

    if ((result.status === 'APPLIED' || result.status === 'DUPLICATE')
      && result.block) {
      pending.resolve(result.block);
      return;
    }

    pending.reject(new CollaborationOperationError(
      result.status,
      result.message ?? operationStatusText(result.status),
    ));
  }

  function scheduleOperationTimeout(clientOperationId: string) {
    return window.setTimeout(() => {
      const pending = pendingOperations.get(clientOperationId);
      if (!pending) {
        return;
      }
      if (pending.retryCount === 0
        && socket?.readyState === WebSocket.OPEN) {
        pending.retryCount += 1;
        send(pending.message);
        pending.timer = scheduleOperationTimeout(clientOperationId);
        return;
      }
      pendingOperations.delete(clientOperationId);
      pending.reject(new CollaborationOperationError(
        'TIMEOUT',
        '协作保存重试后仍超时，请稍后再试',
      ));
    }, 5_000);
  }

  function rejectPendingOperations(
    status: CollaborationOperationError['status'],
    message: string,
  ) {
    pendingOperations.forEach((pending) => {
      window.clearTimeout(pending.timer);
      pending.reject(new CollaborationOperationError(status, message));
    });
    pendingOperations.clear();
  }

  function operationStatusText(status: DocumentOperationResult['status']) {
    const statusMap: Record<DocumentOperationResult['status'], string> = {
      APPLIED: '协作操作已应用',
      CONFLICT: '当前段落已被其他操作修改，请刷新内容后再继续编辑。',
      REJECTED: '协作操作被拒绝，请检查权限或文档状态。',
      DUPLICATE: '该协作操作已经处理过。',
    };
    return statusMap[status];
  }

  function operationId() {
    if (window.crypto?.randomUUID) {
      return window.crypto.randomUUID();
    }
    return `${Date.now()}-${Math.random().toString(16).slice(2)}`;
  }

  return {
    connected,
    members,
    editingStates,
    latestRemoteBlock,
    latestDocumentSequence,
    errorMessage,
    startEditing,
    stopEditing,
    updateContent,
    reconnect: connect,
    disconnect,
  };
}
