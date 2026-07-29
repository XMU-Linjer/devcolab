import { describe, expect, it } from 'vitest';

import type { CodeBindingQueryItem, GitRepositorySource } from '@/api/git';
import {
  bindingDocumentChoices,
  buildBindingFixture,
  selectDefaultBinding,
} from './linkedWorkbenchBindings';

describe('linkedWorkbenchBindings', () => {
  it('keeps file-specific bindings isolated while switching A to B to A', () => {
    const aBinding = binding('binding-a', 'document-a', 'src/A.java');
    const bBinding = binding('binding-b', 'document-b', 'src/B.java');

    const firstA = buildBindingFixture(input(source('src/A.java'), [aBinding]));
    const b = buildBindingFixture(input(source('src/B.java'), [bBinding]));
    const secondA = buildBindingFixture(input(source('src/A.java'), [aBinding]));

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
      input(source('src/A.java'), [a]),
    );
    const bFixture = buildBindingFixture(
      input(source('src/B.java'), [b]),
    );

    expect(aFixture.links[0].id).toBe('binding-link-binding-a');
    expect(bFixture.links[0].id).toBe('binding-link-binding-b');
    expect(aFixture.links[0].blockId).toBeNull();
    expect(bFixture.links[0].blockId).toBeNull();
  });

  it('preserves FILE, RANGE and SYMBOL anchors without merging bindings', () => {
    const bindings = [
      binding('file', 'document-a', 'src/A.java'),
      { ...binding('range', 'document-a', 'src/A.java'), anchorKind: 'RANGE' as const, startLine: 2, endLine: 4, blockId: 'block-a' },
      { ...binding('symbol', 'document-a', 'src/A.java'), anchorKind: 'SYMBOL' as const, symbolKey: 'JAVA:A.run', startLine: 6, endLine: 8, blockId: 'block-b' },
    ];

    const fixture = buildBindingFixture(input(source('src/A.java'), bindings));

    expect(fixture.links).toHaveLength(3);
    expect(fixture.codeAnchors.map(item => item.anchorKind)).toEqual(['SYMBOL', 'RANGE', 'FILE']);
    expect(fixture.codeAnchors[0]).toMatchObject({
      symbolName: 'JAVA:A.run',
      startLine: 6,
      endLine: 8,
    });
  });

  it('prefers exact revision block bindings and keeps legacy FILE selectable', () => {
    const legacy = { ...binding('legacy', 'document-a', 'src/A.java'), revision: null };
    const precise = {
      ...binding('precise', 'document-a', 'src/A.java'),
      anchorKind: 'SYMBOL' as const,
      blockId: 'block-a',
      symbolKey: 'JAVA:A.run',
      startLine: 2,
      endLine: 4,
    };

    expect(selectDefaultBinding([legacy, precise], 'revision')).toEqual(precise);
    expect(buildBindingFixture(input(source('src/A.java'), [legacy, precise])).links)
      .toHaveLength(2);
  });

  it('restores a valid binding id, then block id, before using stable defaults', () => {
    const first = { ...binding('a', 'document-a', 'src/A.java'), blockId: 'block-a' };
    const second = { ...binding('b', 'document-a', 'src/A.java'), blockId: 'block-b' };

    expect(selectDefaultBinding([first, second], 'revision', 'b')?.bindingId).toBe('b');
    expect(selectDefaultBinding([first, second], 'revision', 'missing', 'block-a')?.bindingId)
      .toBe('a');
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
) {
  return {
    repositoryId: 'repository',
    branch: 'main',
    commitSha: 'revision',
    source: sourceValue,
    bindings,
  };
}
