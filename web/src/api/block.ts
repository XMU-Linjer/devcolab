import { http } from './http';

export type DocumentBlockType = 'PARAGRAPH' | 'HEADING' | 'CODE' | 'TODO';

export interface TiptapNode {
  type: string;
  text?: string;
  attrs?: Record<string, unknown>;
  marks?: Array<{ type: string; attrs?: Record<string, unknown> }>;
  content?: TiptapNode[];
}

export interface DocumentBlockContent {
  text: string;
  schemaVersion: number;
  document: TiptapNode;
}

export interface DocumentBlock {
  id: string;
  documentId: string;
  type: DocumentBlockType;
  content: DocumentBlockContent;
  sortOrder: number;
  version: number;
  createdBy: string;
  createdAt: string;
  updatedAt: string;
}

export interface CreateBlockPayload {
  type: DocumentBlockType;
  content: Pick<DocumentBlockContent, 'schemaVersion' | 'document'> & {
    text?: string;
  };
}

export interface UpdateBlockPayload {
  content: Pick<DocumentBlockContent, 'schemaVersion' | 'document'> & {
    text?: string;
  };
  expectedVersion: number;
}

export async function listBlocks(
  documentId: string,
): Promise<DocumentBlock[]> {
  const { data } = await http.get<DocumentBlock[]>(
    `/documents/${documentId}/blocks`,
  );
  return data;
}

export async function createBlock(
  documentId: string,
  payload: CreateBlockPayload,
): Promise<DocumentBlock> {
  const { data } = await http.post<DocumentBlock>(
    `/documents/${documentId}/blocks`,
    payload,
  );
  return data;
}

export async function updateBlock(
  documentId: string,
  blockId: string,
  payload: UpdateBlockPayload,
): Promise<DocumentBlock> {
  const { data } = await http.patch<DocumentBlock>(
    `/documents/${documentId}/blocks/${blockId}`,
    payload,
  );
  return data;
}

export async function deleteBlock(
  documentId: string,
  blockId: string,
): Promise<void> {
  await http.delete(`/documents/${documentId}/blocks/${blockId}`);
}

export async function moveBlock(
  documentId: string,
  blockId: string,
  targetIndex: number,
): Promise<DocumentBlock[]> {
  const { data } = await http.patch<DocumentBlock[]>(
    `/documents/${documentId}/blocks/${blockId}/position`,
    { targetIndex },
  );
  return data;
}
