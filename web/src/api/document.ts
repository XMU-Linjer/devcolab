import { http } from './http';

export type DocumentReviewStatus =
  | 'DRAFT'
  | 'IN_REVIEW'
  | 'PUBLISHED'
  | 'REJECTED'
  | 'SUPERSEDED'
  | 'DEPRECATED';
export type DocumentReviewAction = 'SUBMITTED' | 'APPROVED' | 'REJECTED';
export type DocumentType =
  | 'REQUIREMENT'
  | 'API'
  | 'ARCHITECTURE'
  | 'DATABASE'
  | 'FRONTEND'
  | 'BACKEND'
  | 'TEST'
  | 'DEPLOYMENT'
  | 'ADR';
export type DocumentVersionStatus = 'CURRENT' | 'SUPERSEDED';

export interface DocumentSummary {
  id: string;
  workspaceId: string;
  parentDocumentId: string | null;
  title: string;
  documentType: DocumentType;
  reviewStatus: DocumentReviewStatus;
  createdBy: string;
  createdAt: string;
  updatedAt: string;
}

export interface DocumentTreeNode {
  id: string;
  title: string;
  children: DocumentTreeNode[];
}

export interface DocumentVersion {
  id: string;
  documentId: string;
  versionNo: number;
  title: string;
  status: DocumentVersionStatus;
  snapshotPayload: string;
  publishedBy: string;
  publishedAt: string;
}

export interface DocumentReviewRecord {
  id: string;
  documentId: string;
  action: DocumentReviewAction;
  comment: string | null;
  operatorUserId: string;
  createdAt: string;
}

export interface DocumentOperationLog {
  id: string;
  workspaceId: string;
  documentId: string;
  action: string;
  message: string;
  operatorUserId: string;
  targetType: string;
  targetId: string;
  createdAt: string;
}

export interface CreateDocumentPayload {
  title: string;
  parentDocumentId?: string | null;
  documentType?: DocumentType | null;
}

export async function createDocument(
  workspaceId: string,
  payload: CreateDocumentPayload,
): Promise<DocumentSummary> {
  const { data } = await http.post<DocumentSummary>(
    `/workspaces/${workspaceId}/documents`,
    payload,
  );
  return data;
}

export async function listDocumentTree(
  workspaceId: string,
): Promise<DocumentTreeNode[]> {
  const { data } = await http.get<DocumentTreeNode[]>(
    `/workspaces/${workspaceId}/documents/tree`,
  );
  return data;
}

export async function getDocument(
  documentId: string,
): Promise<DocumentSummary> {
  const { data } = await http.get<DocumentSummary>(`/documents/${documentId}`);
  return data;
}

export async function updateDocument(
  documentId: string,
  payload: { title: string },
): Promise<DocumentSummary> {
  const { data } = await http.patch<DocumentSummary>(
    `/documents/${documentId}`,
    payload,
  );
  return data;
}

export async function moveDocument(
  documentId: string,
  payload: { parentDocumentId: string | null },
): Promise<DocumentSummary> {
  const { data } = await http.patch<DocumentSummary>(
    `/documents/${documentId}/parent`,
    payload,
  );
  return data;
}

export async function deleteDocument(documentId: string): Promise<void> {
  await http.delete(`/documents/${documentId}`);
}

export async function submitDocumentReview(
  documentId: string,
): Promise<DocumentSummary> {
  const { data } = await http.post<DocumentSummary>(
    `/documents/${documentId}/submit-review`,
  );
  return data;
}

export async function approveDocumentReview(
  documentId: string,
  payload: { comment?: string | null } = {},
): Promise<DocumentSummary> {
  const { data } = await http.post<DocumentSummary>(
    `/documents/${documentId}/approve-review`,
    payload,
  );
  return data;
}

export async function rejectDocumentReview(
  documentId: string,
  payload: { comment?: string | null } = {},
): Promise<DocumentSummary> {
  const { data } = await http.post<DocumentSummary>(
    `/documents/${documentId}/reject-review`,
    payload,
  );
  return data;
}

export async function deprecateDocument(
  documentId: string,
): Promise<DocumentSummary> {
  const { data } = await http.post<DocumentSummary>(
    `/documents/${documentId}/deprecate`,
  );
  return data;
}

export async function listDocumentVersions(
  documentId: string,
): Promise<DocumentVersion[]> {
  const { data } = await http.get<DocumentVersion[]>(
    `/documents/${documentId}/versions`,
  );
  return data;
}

export async function getDocumentVersion(
  documentId: string,
  versionId: string,
): Promise<DocumentVersion> {
  const { data } = await http.get<DocumentVersion>(
    `/documents/${documentId}/versions/${versionId}`,
  );
  return data;
}

export async function listDocumentReviewRecords(
  documentId: string,
): Promise<DocumentReviewRecord[]> {
  const { data } = await http.get<DocumentReviewRecord[]>(
    `/documents/${documentId}/review-records`,
  );
  return data;
}

export async function listDocumentTimeline(
  documentId: string,
): Promise<DocumentOperationLog[]> {
  const { data } = await http.get<DocumentOperationLog[]>(
    `/documents/${documentId}/timeline`,
  );
  return data;
}
