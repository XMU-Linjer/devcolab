import Prism from 'prismjs';
import type { Token } from 'prismjs';
import 'prismjs/components/prism-bash';
import 'prismjs/components/prism-java';
import 'prismjs/components/prism-json';
import 'prismjs/components/prism-markdown';
import 'prismjs/components/prism-python';
import 'prismjs/components/prism-sql';
import 'prismjs/components/prism-typescript';
import 'prismjs/components/prism-yaml';

export interface CodeSyntaxToken {
  content: string;
  classes: string[];
}

export interface HighlightedCode {
  language: string;
  lines: CodeSyntaxToken[][];
}

const CACHE_LIMIT = 8;
const highlightCache = new Map<string, HighlightedCode>();

export function highlightCode(
  source: string,
  language: string | null | undefined,
  filePath: string,
): HighlightedCode {
  const resolvedLanguage = resolveCodeLanguage(language, filePath);
  const cacheKey = `${resolvedLanguage}\u0000${source}`;
  const cached = highlightCache.get(cacheKey);
  if (cached) {
    highlightCache.delete(cacheKey);
    highlightCache.set(cacheKey, cached);
    return cached;
  }

  const grammar = resolvedLanguage === 'plain' ? null : Prism.languages[resolvedLanguage];
  const lines: CodeSyntaxToken[][] = [[]];
  if (grammar) {
    appendTokenStream(lines, Prism.tokenize(source, grammar), []);
  } else {
    appendText(lines, source, []);
  }

  const highlighted = { language: resolvedLanguage, lines };
  highlightCache.set(cacheKey, highlighted);
  if (highlightCache.size > CACHE_LIMIT) {
    const oldest = highlightCache.keys().next().value;
    if (oldest) highlightCache.delete(oldest);
  }
  return highlighted;
}

export function resolveCodeLanguage(
  language: string | null | undefined,
  filePath: string,
) {
  const declared = normalizeLanguage(language ?? '');
  if (declared) return declared;

  const fileName = filePath.split('/').pop()?.toLowerCase() ?? '';
  if (fileName === 'dockerfile') return 'bash';
  const extension = fileName.includes('.') ? fileName.split('.').pop() ?? '' : '';
  return normalizeLanguage(extension) ?? 'plain';
}

function normalizeLanguage(value: string): string | null {
  const normalized = value.trim().toLowerCase();
  const aliases: Record<string, string> = {
    py: 'python',
    python: 'python',
    java: 'java',
    ts: 'typescript',
    tsx: 'typescript',
    typescript: 'typescript',
    js: 'javascript',
    jsx: 'javascript',
    javascript: 'javascript',
    vue: 'markup',
    html: 'markup',
    xml: 'markup',
    markup: 'markup',
    json: 'json',
    yaml: 'yaml',
    yml: 'yaml',
    md: 'markdown',
    markdown: 'markdown',
    sql: 'sql',
    sh: 'bash',
    bash: 'bash',
    shell: 'bash',
    zsh: 'bash',
  };
  return aliases[normalized] ?? null;
}

function appendTokenStream(
  lines: CodeSyntaxToken[][],
  stream: Array<string | Token>,
  inheritedClasses: string[],
) {
  stream.forEach((item) => {
    if (typeof item === 'string') {
      appendText(lines, item, inheritedClasses);
      return;
    }
    const classes = [
      ...inheritedClasses,
      'token',
      tokenClass(item.type),
      ...tokenAliases(item.alias).map(tokenClass),
    ];
    if (typeof item.content === 'string') {
      appendText(lines, item.content, classes);
    } else if (Array.isArray(item.content)) {
      appendTokenStream(lines, item.content, classes);
    } else {
      appendTokenStream(lines, [item.content], classes);
    }
  });
}

function appendText(lines: CodeSyntaxToken[][], text: string, classes: string[]) {
  const parts = text.split(/\r\n|\r|\n/);
  parts.forEach((part, index) => {
    if (part) lines[lines.length - 1]!.push({ content: part, classes });
    if (index < parts.length - 1) lines.push([]);
  });
}

function tokenAliases(alias: string | string[] | undefined) {
  if (!alias) return [];
  return Array.isArray(alias) ? alias : [alias];
}

function tokenClass(value: string) {
  return value.replace(/[^a-zA-Z0-9_-]/g, '');
}
