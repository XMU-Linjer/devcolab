import type { DocumentChangeDetail } from '@/api/documentChange';
import type {
  CodeBindingQueryResult,
  GitRepository,
} from '@/api/git';

export interface AppliedReviewNavigationTarget {
  documentId: string;
  documentTitle: string | null;
  repositoryId: string;
  revision: string;
  filePath: string | null;
}

type BindingQuery = (
  workspaceId: string,
  repositoryId: string,
  revision: string,
  filePath: string,
) => Promise<CodeBindingQueryResult>;

export async function resolveAppliedReviewNavigationTargets(
  workspaceId: string,
  detail: DocumentChangeDetail,
  repositories: GitRepository[],
  queryBindings: BindingQuery,
): Promise<AppliedReviewNavigationTarget[]> {
  if (detail.request.status !== 'APPLIED') return [];

  const candidates = detail.bindingProposals
    .filter(proposal => proposal.action === 'UPSERT_BINDING')
    .sort((left, right) => left.sequenceNumber - right.sequenceNumber);
  const targets = new Map<string, AppliedReviewNavigationTarget>();

  for (const proposal of candidates) {
    const documentId = proposal.documentTarget.documentId;
    if (!documentId) continue;
    const repository = repositories.find(item => item.id === proposal.repository.id);
    const revision = repository?.lastSyncedCommit;
    if (!revision) continue;

    const key = `${proposal.repository.id}:${documentId}`;
    const existing = targets.get(key);
    if (!existing) {
      targets.set(key, {
        documentId,
        documentTitle: proposal.documentTarget.documentTitle,
        repositoryId: proposal.repository.id,
        revision,
        filePath: null,
      });
    }
    if (targets.get(key)?.filePath) continue;

    try {
      const result = await queryBindings(
        workspaceId,
        proposal.repository.id,
        revision,
        proposal.filePath,
      );
      if (result.bindings.some(binding => binding.documentId === documentId)) {
        targets.set(key, {
          ...(targets.get(key) as AppliedReviewNavigationTarget),
          filePath: proposal.filePath,
        });
      }
    } catch {
      // The apply result remains useful even when the formal binding lookup is unavailable.
    }
  }

  return [...targets.values()];
}
