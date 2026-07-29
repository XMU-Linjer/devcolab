import { describe, expect, it } from 'vitest';

import type { CodeBindingQueryItem, GitRepositorySource } from '@/api/git';
import { bindingDocumentChoices, buildBindingFixture } from './linkedWorkbenchBindings';

describe('linkedWorkbenchBindings', () => {
  it('keeps file-specific bindings isolated while switching A to B to A', () => {
    const aBinding = binding('binding-a', 'document-a', 'src/A.java');
    const bBinding = binding('binding-b', 'document-b', 'src/B.java');

    const firstA = buildBindingFixture(input(source('src/A.java'), [aBinding], 'document-a'));
    const b = buildBindingFixture(input(source('src/B.java'), [bBinding], 'document-b'));
    const secondA = buildBindingFixture(input(source('src/A.java'), [aBinding], 'document-a'));

    expect(firstA.codeAnchors.map(item => item.filePath)).toEqual(['src/A.java']);
    expect(firstA.codeAnchors[0]).toMatchObject({
      anchorKind: 'FILE',
      startLine: null,
      endLine: null,
    });
    expect(b.codeAnchors.map(item => item.filePath)).toEqual(['src/B.java']);
    expect(secondA).toEqual(firstA);
    expect(b.links.map(item => item.documentId)).toEqual(['document-b']);
  });

  it('keeps multiple file-level proposals for one document as separate fixtures', () => {
    const a = binding('binding-a', 'document-shared', 'src/A.java');
    const b = binding('binding-b', 'document-shared', 'src/B.java');

    const aFixture = buildBindingFixture(
      input(source('src/A.java'), [a], 'document-shared'),
    );
    const bFixture = buildBindingFixture(
      input(source('src/B.java'), [b], 'document-shared'),
    );

    expect(aFixture.links[0].id).toBe('binding-link-binding-a');
    expect(bFixture.links[0].id).toBe('binding-link-binding-b');
    expect(aFixture.links[0].blockId).toBeNull();
    expect(bFixture.links[0].blockId).toBeNull();
  });

  it('deduplicates related documents without merging different document ids', () => {
    const choices = bindingDocumentChoices([
      binding('binding-a1', 'document-a', 'src/A.java'),
      binding('binding-a2', 'document-a', 'src/A.java'),
      binding('binding-b', 'document-b', 'src/A.java'),
    ], [
      { id: 'document-a', title: '文档 A', depth: 0 },
      { id: 'document-b', title: '文档 B', depth: 1 },
    ]);

    expect(choices.map(item => item.id)).toEqual(['document-a', 'document-b']);
    expect(choices.map(item => item.title)).toEqual(['文档 A', '文档 B']);
  });
});

function binding(
  bindingId: string,
  documentId: string,
  pathPattern: string,
): CodeBindingQueryItem {
  return {
    bindingId,
    workspaceId: 'workspace',
    repositoryId: 'repository',
    revision: 'revision',
    anchorKind: 'FILE',
    symbolKey: null,
    startLine: null,
    endLine: null,
    documentId,
    blockId: null,
    targetKey: 'DOCUMENT',
    pathPattern,
    documentTitle: documentId,
  };
}

function source(path: string): GitRepositorySource {
  return {
    repositoryId: 'repository',
    commitSha: 'revision',
    path,
    blobSha: `blob-${path}`,
    sizeBytes: 10,
    language: 'Java',
    readable: true,
    content: 'class Example {}',
    symbols: [],
  };
}

function input(
  sourceValue: GitRepositorySource,
  bindings: CodeBindingQueryItem[],
  selectedDocumentId: string,
) {
  return {
    repositoryId: 'repository',
    branch: 'main',
    commitSha: 'revision',
    source: sourceValue,
    bindings,
    selectedDocumentId,
  };
}
