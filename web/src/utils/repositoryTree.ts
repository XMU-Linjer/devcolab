import type { GitRepositoryFile } from '@/api/git';
import type { LinkedFileTreeNode } from '@/types/linkedWorkbench';

const repositoryNodeCollator = new Intl.Collator(undefined, {
  numeric: true,
  sensitivity: 'base',
});

export function normalizeRepositoryPath(path: string): string {
  return path
    .replaceAll('\\', '/')
    .split('/')
    .filter(Boolean)
    .join('/');
}

export function repositoryFileAncestorKeys(path: string): string[] {
  const parts = normalizeRepositoryPath(path).split('/').filter(Boolean);
  return parts.slice(0, -1).map((_, index) => parts.slice(0, index + 1).join('/'));
}

export function isDirectoryNode(node: LinkedFileTreeNode): boolean {
  return node.kind === 'directory';
}

export function isFileNode(node: LinkedFileTreeNode): boolean {
  return node.kind === 'file';
}

export function getRepositoryNodeName(node: LinkedFileTreeNode): string {
  if (node.label) return node.label;
  const segments = node.key.split('/').filter(Boolean);
  return segments.at(-1) ?? '';
}

function getRepositoryNodeRank(node: LinkedFileTreeNode): number {
  if (isDirectoryNode(node)) return 0;
  if (isFileNode(node)) return 1;
  return 2;
}

export function compareRepositoryNodes(
  left: LinkedFileTreeNode,
  right: LinkedFileTreeNode,
): number {
  const rankDifference = getRepositoryNodeRank(left) - getRepositoryNodeRank(right);
  if (rankDifference !== 0) return rankDifference;
  return repositoryNodeCollator.compare(
    getRepositoryNodeName(left),
    getRepositoryNodeName(right),
  );
}

export function sortRepositoryTree(
  nodes: readonly LinkedFileTreeNode[],
): LinkedFileTreeNode[] {
  return [...nodes]
    .sort(compareRepositoryNodes)
    .map(node => ({
      ...node,
      children: node.children ? sortRepositoryTree(node.children) : node.children,
    }));
}

export function buildRepositoryTree(
  files: readonly GitRepositoryFile[],
): LinkedFileTreeNode[] {
  const root: LinkedFileTreeNode[] = [];

  for (const file of files) {
    let level = root;
    const parts = normalizeRepositoryPath(file.path).split('/').filter(Boolean);

    parts.forEach((part, index) => {
      const key = parts.slice(0, index + 1).join('/');
      const isLeaf = index === parts.length - 1;
      let node = level.find(item => item.key === key);

      if (!node) {
        node = isLeaf
          ? { key, label: part, kind: 'file', file }
          : { key, label: part, kind: 'directory', children: [] };
        level.push(node);
      }

      if (node.kind === 'directory' && node.children) level = node.children;
    });
  }

  return sortRepositoryTree(root);
}
