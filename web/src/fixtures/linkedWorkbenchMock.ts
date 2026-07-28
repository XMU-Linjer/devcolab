import type { DocumentBlock } from '@/api/block';
import type { LinkedFixture } from '@/types/linkedWorkbench';

interface FixtureInput {
  repositoryId: string;
  branch: string;
  commitSha: string;
  filePath: string;
  language: string;
  source: string;
  blocks: DocumentBlock[];
}

export function buildLinkedWorkbenchFixture(input: FixtureInput): LinkedFixture {
  const lines = input.source.split(/\r?\n/);
  const candidates = candidateLines(lines);
  const count = Math.min(3, input.blocks.length, Math.max(candidates.length, 1));
  const driftIndex = count > 1 ? 1 : 0;
  const codeAnchors = Array.from({ length: count }, (_, index) => {
    const startLine = candidates[index] ?? Math.min(index + 1, Math.max(lines.length, 1));
    const endLine = Math.min(startLine + (index === 1 ? 2 : 1), Math.max(lines.length, 1));
    return {
      id: `mock-anchor-${input.repositoryId}-${index + 1}`,
      repositoryId: input.repositoryId,
      branch: input.branch,
      commitSha: input.commitSha,
      filePath: input.filePath,
      language: input.language,
      symbolName: symbolAt(lines[startLine - 1] ?? '', index),
      startLine,
      endLine,
      status: index === driftIndex ? 'DRIFTED' as const : 'VALID' as const,
    };
  });

  const links = codeAnchors.map((anchor, index) => ({
    id: `mock-link-${input.repositoryId}-${index + 1}`,
    codeAnchorId: anchor.id,
    documentId: input.blocks[index].documentId,
    blockId: input.blocks[index].id,
    relationType: index === driftIndex ? 'CONFLICTS_WITH' as const : 'IMPLEMENTS' as const,
  }));

  const issues = links
    .filter((_, index) => index === driftIndex)
    .map((link) => ({
      id: `mock-issue-${link.id}`,
      linkId: link.id,
      title: '实现与文档描述可能发生漂移',
      description: '当前提交中的代码范围与已关联 Block 需要人工复核。',
      severity: 'HIGH' as const,
      status: 'OPEN' as const,
      blockId: link.blockId,
      codeAnchorId: link.codeAnchorId,
      commitSha: input.commitSha,
      sourceType: 'RULE' as const,
      sourceKey: 'mock-drift-rule',
    }));

  const evidence = links.map((link, index) => ({
    id: `mock-evidence-${link.id}`,
    linkId: link.id,
    title: index === driftIndex ? '漂移规则命中证据' : '当前提交代码证据',
    summary: `关联 ${input.filePath} 与文档 Block #${index + 1}`,
    kind: index === driftIndex ? 'RULE' as const : 'COMMIT' as const,
    commitSha: input.commitSha,
  }));

  return { codeAnchors, links, issues, evidence };
}

function candidateLines(lines: string[]) {
  const strong = lines
    .map((line, index) => ({ line: line.trim(), number: index + 1 }))
    .filter(({ line }) => /\b(class|interface|record|enum|function|public|private|protected|export|const)\b/.test(line))
    .map(({ number }) => number);
  if (strong.length >= 3) return strong;
  const fallback = lines
    .map((line, index) => ({ line: line.trim(), number: index + 1 }))
    .filter(({ line }) => line.length > 0)
    .map(({ number }) => number);
  return [...new Set([...strong, ...fallback])];
}

function symbolAt(line: string, index: number) {
  const match = line.match(/(?:class|interface|record|enum|function)\s+([\w$]+)/)
    ?? line.match(/([\w$]+)\s*\(/);
  return match?.[1] ?? `关联范围 ${index + 1}`;
}
