import { describe, expect, it } from 'vitest';

import type { CodeAnchor, CodeDocumentLink } from '@/types/linkedWorkbench';
import { buildCodeRangePresentation } from './codeRangePresentation';

describe('code range presentation', () => {
  it('marks a single-line range as single', () => {
    const result = presentation([anchor('a', 3, 3)], [link('l', 'a')], 'l');

    expect(result.lineStates.get(3)).toMatchObject({ active: true, position: 'single' });
    expect(result.overlays).toHaveLength(1);
  });

  it('marks a continuous range with start, middle and end positions', () => {
    const result = presentation([anchor('a', 2, 5)], [link('l', 'a')], 'l');

    expect(result.lineStates.get(2)?.position).toBe('start');
    expect(result.lineStates.get(3)?.position).toBe('middle');
    expect(result.lineStates.get(4)?.position).toBe('middle');
    expect(result.lineStates.get(5)?.position).toBe('end');
  });

  it('keeps adjacent bindings as separate visual ranges', () => {
    const result = presentation(
      [anchor('a', 2, 3), anchor('b', 4, 5)],
      [link('l-a', 'a'), link('l-b', 'b')],
      null,
    );

    expect(result.lineStates.get(2)?.position).toBe('start');
    expect(result.lineStates.get(3)?.position).toBe('end');
    expect(result.lineStates.get(4)?.position).toBe('start');
    expect(result.lineStates.get(5)?.position).toBe('end');
    expect(result.overlays.map(item => [item.startLine, item.endLine])).toEqual([[2, 3], [4, 5]]);
  });

  it('gives an active overlapping range visual priority', () => {
    const result = presentation(
      [anchor('wide', 2, 6), anchor('active', 4, 5)],
      [link('l-wide', 'wide'), link('l-active', 'active')],
      'l-active',
    );

    expect(result.lineStates.get(4)).toMatchObject({
      active: true,
      anchorId: 'active',
      position: 'start',
    });
  });

  it('ignores invalid, out-of-file and FILE-level ranges safely', () => {
    const invalid = [
      anchor('before', 0, 2),
      anchor('reverse', 5, 4),
      anchor('after', 9, 12),
      { ...anchor('file', 1, 2), anchorKind: 'FILE' as const },
      { ...anchor('other', 1, 2), filePath: 'src/Other.java' },
    ];
    const result = presentation(
      invalid,
      invalid.map(item => link(`link-${item.id}`, item.id)),
      null,
    );

    expect(result.overlays).toEqual([]);
    expect(result.lineStates.size).toBe(0);
  });
});

function presentation(
  anchors: CodeAnchor[],
  links: CodeDocumentLink[],
  activeLinkId: string | null,
) {
  return buildCodeRangePresentation({
    lineCount: 10,
    filePath: 'src/A.java',
    anchors,
    links,
    activeLinkId,
  });
}

function anchor(id: string, startLine: number, endLine: number): CodeAnchor {
  return {
    id,
    bindingId: id,
    repositoryId: 'repo',
    revision: 'revision',
    branch: 'main',
    commitSha: 'revision',
    filePath: 'src/A.java',
    language: 'Java',
    anchorKind: 'RANGE',
    startLine,
    endLine,
    status: 'VALID',
  };
}

function link(id: string, codeAnchorId: string): CodeDocumentLink {
  return {
    id,
    codeAnchorId,
    documentId: 'document',
    blockId: 'block',
    relationType: 'DESCRIBES',
  };
}
