import type {
  DocumentChangeEvidence,
  DocumentChangeOperation,
  DocumentChangeStatus,
} from '@/api/documentChange';

export const reviewStatusLabels: Record<DocumentChangeStatus, string> = {
  PENDING: '待处理',
  APPLIED: '已应用',
  REJECTED: '已拒绝',
  STALE: '已失效',
};

export const operationLabels: Record<DocumentChangeOperation['operationType'], string> = {
  CREATE_DOCUMENT: '创建文档',
  ADD_BLOCK: '新增 Block',
  UPDATE_BLOCK: '修改 Block',
  DELETE_BLOCK: '删除 Block',
};

export function evidenceForOperation(operation: DocumentChangeOperation | null) {
  return operation?.evidence ?? [];
}

export function operationDocumentTitle(operation: DocumentChangeOperation) {
  return operation.target.documentTitle
    || operation.proposal.documentTitle
    || '待创建文档';
}

export function evidenceRangeLabel(evidence: DocumentChangeEvidence) {
  if (evidence.startLine == null || evidence.endLine == null) return '完整文件';
  return `L${evidence.startLine}–${evidence.endLine}`;
}

export function proposalText(operation: DocumentChangeOperation) {
  return operation.proposal.plainText ?? '';
}

export function currentText(operation: DocumentChangeOperation) {
  return operation.baseSnapshot?.plainText ?? '';
}

export function selectedEvidence(
  operation: DocumentChangeOperation | null,
  requestEvidence: DocumentChangeEvidence[],
  evidenceId?: string | null,
) {
  const all = [...evidenceForOperation(operation), ...requestEvidence];
  return all.find(item => item.id === evidenceId) ?? all[0] ?? null;
}
