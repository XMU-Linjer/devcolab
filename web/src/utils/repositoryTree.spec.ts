import { describe, expect, it } from 'vitest';

import type { GitRepositoryFile } from '@/api/git';
import type { LinkedFileTreeNode } from '@/types/linkedWorkbench';
import {
  buildRepositoryTree,
  compareRepositoryNodes,
  getRepositoryNodeName,
  isDirectoryNode,
  isFileNode,
  sortRepositoryTree,
} from './repositoryTree';

function node(
  key: string,
  kind: LinkedFileTreeNode['kind'],
  children?: LinkedFileTreeNode[],
): LinkedFileTreeNode {
  return { key, label: key.split('/').at(-1) ?? '', kind, children };
}

function file(path: string): GitRepositoryFile {
  return {
    id: path,
    path,
    blobSha: `sha-${path}`,
    sizeBytes: 1,
    language: null,
    readable: true,
  };
}

describe('repository tree sorting', () => {
  it('places directories before files and unknown nodes', () => {
    const result = sortRepositoryTree([
      node('README.md', 'file'),
      node('mystery', 'unknown'),
      node('src', 'directory', []),
    ]);
    expect(result.map(item => item.key)).toEqual(['src', 'README.md', 'mystery']);
  });

  it('naturally sorts directories inside their group', () => {
    const result = sortRepositoryTree([
      node('folder10', 'directory', []),
      node('Folder2', 'directory', []),
      node('alpha', 'directory', []),
    ]);
    expect(result.map(item => item.key)).toEqual(['alpha', 'Folder2', 'folder10']);
  });

  it('naturally sorts files inside their group', () => {
    const result = sortRepositoryTree([
      node('file10.ts', 'file'),
      node('File2.ts', 'file'),
      node('alpha.ts', 'file'),
    ]);
    expect(result.map(item => item.key)).toEqual(['alpha.ts', 'File2.ts', 'file10.ts']);
  });

  it('sorts without case sensitivity', () => {
    expect(compareRepositoryNodes(node('alpha', 'file'), node('Beta', 'file'))).toBeLessThan(0);
  });

  it('recursively sorts every directory level', () => {
    const result = sortRepositoryTree([
      node('src', 'directory', [
        node('src/main.py', 'file'),
        node('src/agents', 'directory', [
          node('src/agents/rule10.py', 'file'),
          node('src/agents/rule2.py', 'file'),
        ]),
      ]),
    ]);
    expect(result[0].children?.map(item => item.key)).toEqual(['src/agents', 'src/main.py']);
    expect(result[0].children?.[0].children?.map(item => item.key)).toEqual([
      'src/agents/rule2.py',
      'src/agents/rule10.py',
    ]);
  });

  it('uses the path segment when the display label is absent', () => {
    const unnamed = { key: 'src/fallback.ts', kind: 'file' } as LinkedFileTreeNode;
    expect(getRepositoryNodeName(unnamed)).toBe('fallback.ts');
    expect(() => sortRepositoryTree([unnamed])).not.toThrow();
  });

  it('does not mutate the input array or nested children', () => {
    const children = [node('src/z.ts', 'file'), node('src/a', 'directory', [])];
    const input = [node('z.md', 'file'), node('src', 'directory', children)];
    sortRepositoryTree(input);
    expect(input.map(item => item.key)).toEqual(['z.md', 'src']);
    expect(children.map(item => item.key)).toEqual(['src/z.ts', 'src/a']);
  });

  it('keeps equal names stable', () => {
    const first = { ...node('first', 'file'), label: 'same' };
    const second = { ...node('second', 'file'), label: 'SAME' };
    expect(sortRepositoryTree([first, second]).map(item => item.key)).toEqual(['first', 'second']);
  });

  it('exposes explicit type guards', () => {
    expect(isDirectoryNode(node('src', 'directory', []))).toBe(true);
    expect(isFileNode(node('a.ts', 'file'))).toBe(true);
    expect(isDirectoryNode(node('unknown', 'unknown'))).toBe(false);
  });

  it('builds a complete folders-first tree from flat API files', () => {
    const result = buildRepositoryTree([
      file('README.md'),
      file('src/main.py'),
      file('tests/test_main.py'),
      file('src/agents/rule10.py'),
      file('src/agents/rule2.py'),
    ]);
    expect(result.map(item => item.key)).toEqual(['src', 'tests', 'README.md']);
    expect(result[0].children?.map(item => item.key)).toEqual(['src/agents', 'src/main.py']);
    expect(result[0].children?.[0].children?.map(item => item.key)).toEqual([
      'src/agents/rule2.py',
      'src/agents/rule10.py',
    ]);
  });
});
