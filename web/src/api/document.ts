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
