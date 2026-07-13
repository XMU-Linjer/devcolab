import { http } from './http';

export interface DocumentSummary {
  id: string;
  workspaceId: string;
  parentDocumentId: string | null;
  title: string;
  createdBy: string;
  createdAt: string;
  updatedAt: string;
}

export interface DocumentTreeNode {
  id: string;
  title: string;
  children: DocumentTreeNode[];
}

export interface CreateDocumentPayload {
  title: string;
  parentDocumentId?: string | null;
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

