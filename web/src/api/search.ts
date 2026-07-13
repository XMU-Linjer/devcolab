import { http } from './http';

export type SearchHitType = 'DOCUMENT_TITLE' | 'BLOCK_CONTENT';

export interface SearchHit {
  type: SearchHitType;
  documentId: string;
  documentTitle: string;
  blockId: string | null;
  snippet: string;
  updatedAt: string;
}

export async function searchWorkspace(
  workspaceId: string,
  keyword: string,
): Promise<SearchHit[]> {
  const { data } = await http.get<SearchHit[]>(
    `/workspaces/${workspaceId}/search`,
    {
      params: {
        keyword,
      },
    },
  );
  return data;
}
