import { http } from './http';

export type SearchHitType = 'DOCUMENT_TITLE' | 'BLOCK_CONTENT';
export type SearchScope = 'ALL' | 'TITLE' | 'CONTENT';

export interface SearchHighlightRange {
  start: number;
  end: number;
}

export interface SearchHit {
  type: SearchHitType;
  documentId: string;
  documentTitle: string;
  blockId: string | null;
  snippet: string;
  highlights: SearchHighlightRange[];
  updatedAt: string;
}

export async function searchWorkspace(
  workspaceId: string,
  keyword: string,
  scope: SearchScope = 'ALL',
): Promise<SearchHit[]> {
  const { data } = await http.get<SearchHit[]>(
    `/workspaces/${workspaceId}/search`,
    {
      params: {
        keyword,
        scope,
      },
    },
  );
  return data;
}
