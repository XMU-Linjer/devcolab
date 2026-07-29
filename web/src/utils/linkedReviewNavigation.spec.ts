import { describe, expect, it, vi } from 'vitest';

import type { DocumentChangeDetail } from '@/api/documentChange';
import type { GitRepository } from '@/api/git';
import { resolveAppliedReviewNavigationTargets } from './linkedReviewNavigation';

const repositories = [{
  id: 'repository-1',
  name: 'repository',
  lastSyncedCommit: 'revision-1',
}] as GitRepository[];

describe('resolveAppliedReviewNavigationTargets', () => {
  it('creates one result entry per applied document and uses the first formal binding', async () => {
    const query = vi.fn().mockImplementation(
      async (_workspace: string, _repository: string, _revision: string, filePath: string) => ({
        bindings: filePath === 'src/A.java'
          ? [{ documentId: 'document-a' }]
          : filePath === 'src/C.java' ? [{ documentId: 'document-c' }] : [],
      }),
    );
    const targets = await resolveAppliedReviewNavigationTargets(
      'workspace-1',
      detail([
        proposal(1, 'document-a', 'Document A', 'src/A.java'),
        proposal(2, 'document-a', 'Document A', 'src/B.java'),
        proposal(3, 'document-c', 'Document C', 'src/C.java'),
      ]),
      repositories,
      query,
    );

    expect(targets).toEqual([
      {
        documentId: 'document-a',
        documentTitle: 'Document A',
        repositoryId: 'repository-1',
        revision: 'revision-1',
        filePath: 'src/A.java',
      },
      {
        documentId: 'document-c',
        documentTitle: 'Document C',
        repositoryId: 'repository-1',
        revision: 'revision-1',
        filePath: 'src/C.java',
      },
    ]);
  });

  it('keeps a real document target with a null file when no formal binding is found', async () => {
    const targets = await resolveAppliedReviewNavigationTargets(
      'workspace-1',
      detail([proposal(1, 'document-a', 'Document A', 'src/A.java')]),
      repositories,
      vi.fn().mockResolvedValue({ bindings: [] }),
    );
    expect(targets[0]).toMatchObject({
      documentId: 'document-a',
      filePath: null,
    });
  });

  it('does not invent a document id for unresolved create-document proposals', async () => {
    const targets = await resolveAppliedReviewNavigationTargets(
      'workspace-1',
      detail([proposal(1, null, null, 'src/A.java')]),
      repositories,
      vi.fn(),
    );
    expect(targets).toEqual([]);
  });
});

function detail(bindingProposals: DocumentChangeDetail['bindingProposals']): DocumentChangeDetail {
  return {
    request: {
      id: 'review-1',
      workspaceId: 'workspace-1',
      status: 'APPLIED',
      summary: 'Applied',
      rationale: '',
      sourceType: 'MCP',
      submittedBy: { id: 'user-1', displayName: 'User' },
      createdAt: '',
      reviewedBy: null,
      reviewedAt: null,
      rejectionReason: null,
    },
    operations: [],
    bindingProposals,
    requestEvidence: [],
    replayed: false,
  };
}

function proposal(
  sequenceNumber: number,
  documentId: string | null,
  documentTitle: string | null,
  filePath: string,
): DocumentChangeDetail['bindingProposals'][number] {
  return {
    bindingProposalId: `proposal-${sequenceNumber}`,
    clientBindingProposalId: `client-${sequenceNumber}`,
    sequenceNumber,
    action: 'UPSERT_BINDING',
    repository: { id: 'repository-1', name: 'repository' },
    filePath,
    documentTarget: {
      documentId,
      documentTitle,
      blockId: null,
      blockType: null,
    },
    bindingId: null,
    reason: '',
  };
}
