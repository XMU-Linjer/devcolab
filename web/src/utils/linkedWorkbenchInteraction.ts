import type { CodeDocumentLink, LinkActivationSource } from '@/types/linkedWorkbench';

export function focusPlan(source: LinkActivationSource) {
  return {
    code: source !== 'code',
    document: source !== 'document',
  };
}

export function linkIdForBlock(links: CodeDocumentLink[], blockId: string) {
  return links.find(link => link.blockId === blockId)?.id ?? null;
}
