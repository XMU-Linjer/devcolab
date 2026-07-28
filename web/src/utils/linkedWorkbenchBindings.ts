import type { CodeBindingQueryItem, GitRepositorySource } from '@/api/git';
import type { LinkedDocumentChoice, LinkedFixture } from '@/types/linkedWorkbench';

interface BuildBindingFixtureInput {
  repositoryId: string;
  branch: string;
  commitSha: string;
  source: GitRepositorySource;
  bindings: CodeBindingQueryItem[];
  selectedDocumentId: string;
}

export function buildBindingFixture(input: BuildBindingFixtureInput): LinkedFixture {
  const selectedBindings = input.bindings.filter(
    binding => binding.documentId === input.selectedDocumentId,
  );
  const codeAnchors = selectedBindings.map(binding => ({
    id: `binding-anchor-${binding.bindingId}`,
    repositoryId: input.repositoryId,
    branch: input.branch,
    commitSha: input.commitSha,
    filePath: binding.pathPattern,
    language: input.source.language || 'text',
    symbolName: binding.blockId ? 'Block 关联文件' : '文档关联文件',
    anchorKind: 'FILE' as const,
    startLine: null,
    endLine: null,
    status: 'VALID' as const,
  }));
  const links = selectedBindings.map((binding, index) => ({
    id: `binding-link-${binding.bindingId}`,
    codeAnchorId: codeAnchors[index].id,
    documentId: binding.documentId,
    blockId: binding.blockId,
    relationType: 'DESCRIBES' as const,
  }));
  return { codeAnchors, links, issues: [], evidence: [] };
}

export function bindingDocumentChoices(
  bindings: CodeBindingQueryItem[],
  documentChoices: LinkedDocumentChoice[],
): LinkedDocumentChoice[] {
  const choiceById = new Map(documentChoices.map(item => [item.id, item]));
  const seen = new Set<string>();
  return bindings.flatMap((binding) => {
    if (seen.has(binding.documentId)) return [];
    seen.add(binding.documentId);
    const existing = choiceById.get(binding.documentId);
    return [{
      id: binding.documentId,
      title: existing?.title || binding.documentTitle || binding.documentId,
      depth: existing?.depth ?? 0,
      version: existing?.version,
      reviewStatus: existing?.reviewStatus,
    }];
  });
}
