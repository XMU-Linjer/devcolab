import { describe, expect, it } from 'vitest';
import { useLinkedWorkbenchState } from './useLinkedWorkbenchState';

const fixture = {
  codeAnchors: [
    { id: 'a1', repositoryId: 'r', branch: 'main', commitSha: 'abc', filePath: 'A.java', language: 'Java', startLine: 2, endLine: 3, status: 'VALID' as const },
    { id: 'a2', repositoryId: 'r', branch: 'main', commitSha: 'abc', filePath: 'A.java', language: 'Java', startLine: 6, endLine: 8, status: 'DRIFTED' as const },
  ],
  links: [
    { id: 'l1', codeAnchorId: 'a1', documentId: 'd', blockId: 'b1', relationType: 'IMPLEMENTS' as const },
    { id: 'l2', codeAnchorId: 'a2', documentId: 'd', blockId: 'b2', relationType: 'CONFLICTS_WITH' as const },
  ],
  issues: [{ id: 'i2', linkId: 'l2', title: '漂移', description: '待复核', severity: 'HIGH' as const, status: 'OPEN' as const, sourceType: 'RULE' as const }],
  evidence: [{ id: 'e1', linkId: 'l1', title: '提交', summary: '证据', kind: 'COMMIT' as const }],
};

describe('useLinkedWorkbenchState', () => {
  it('uses activeLinkId as the only source for derived context', () => {
    const state = useLinkedWorkbenchState();
    state.replaceFixture(fixture);
    state.replaceDocumentBlocks([{ id: 'b2', documentId: 'd', type: 'PARAGRAPH', content: { text: 'x', schemaVersion: 1, document: { type: 'doc' } }, sortOrder: 0, version: 1, createdBy: 'u', createdAt: '', updatedAt: '' }]);
    state.activateLink('l2', 'rail');
    expect(state.activeLink.value?.id).toBe('l2');
    expect(state.activeCodeAnchor.value?.id).toBe('a2');
    expect(state.activeDocumentBlock.value?.id).toBe('b2');
    expect(state.activeIssue.value?.id).toBe('i2');
  });

  it('retains selection across modes and opens inspector in drift review', () => {
    const state = useLinkedWorkbenchState();
    state.replaceFixture(fixture);
    state.activateLink('l1', 'code');
    state.toggleInspector(false);
    state.setMode('CODE_FOCUS');
    expect(state.activeLinkId.value).toBe('l1');
    state.setMode('DRIFT_REVIEW');
    expect(state.inspectorOpen.value).toBe(true);
    expect(state.activeLinkId.value).toBe('l2');
  });

  it('switches all bindings without creating a second relation state', () => {
    const state = useLinkedWorkbenchState();
    state.replaceFixture(fixture);

    expect(state.activeLinkIndex.value).toBe(0);
    expect(state.linkCount.value).toBe(2);
    expect(state.canSelectPreviousLink.value).toBe(false);
    expect(state.selectNextLink()?.id).toBe('l2');
    expect(state.activeLinkId.value).toBe('l2');
    expect(state.canSelectNextLink.value).toBe(false);
    expect(state.selectPreviousLink()?.id).toBe('l1');
  });
});
