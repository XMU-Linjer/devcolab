import { describe, expect, it } from 'vitest';

import { highlightCode, resolveCodeLanguage } from './codeSyntaxHighlight';

describe('code syntax highlighting', () => {
  it('recognizes declared and extension-based languages', () => {
    expect(resolveCodeLanguage('Python', 'script.unknown')).toBe('python');
    expect(resolveCodeLanguage(null, 'src/Example.java')).toBe('java');
    expect(resolveCodeLanguage(null, 'src/view.ts')).toBe('typescript');
    expect(resolveCodeLanguage(null, 'src/View.vue')).toBe('markup');
    expect(resolveCodeLanguage(null, 'notes.unknown')).toBe('plain');
  });

  it('tokenizes Python and preserves the original lines', () => {
    const source = 'def review(name: str):\n    return "hello " + name';
    const highlighted = highlightCode(source, 'Python', 'app/main.py');

    expect(highlighted.language).toBe('python');
    expect(highlighted.lines).toHaveLength(2);
    expect(text(highlighted)).toBe(source);
    expect(classes(highlighted)).toContain('keyword');
    expect(classes(highlighted)).toContain('function');
    expect(classes(highlighted)).toContain('string');
  });

  it('tokenizes Java annotations, keywords, types and strings', () => {
    const highlighted = highlightCode(
      '@Override\npublic String name() { return "DevCollab"; }',
      'Java',
      'Example.java',
    );

    expect(highlighted.lines).toHaveLength(2);
    expect(classes(highlighted)).toContain('annotation');
    expect(classes(highlighted)).toContain('keyword');
    expect(classes(highlighted)).toContain('string');
  });

  it('handles TypeScript and Vue markup without changing source text', () => {
    const typescript = 'const count: number = 1;';
    const vue = '<script setup lang="ts">\nconst value = 1\n</script>';

    expect(text(highlightCode(typescript, 'TypeScript', 'state.ts'))).toBe(typescript);
    expect(text(highlightCode(vue, 'Vue', 'View.vue'))).toBe(vue);
  });

  it('returns plain tokens for unknown languages and caches unchanged files', () => {
    const source = '<script>alert("safe")</script>';
    const first = highlightCode(source, null, 'file.unknown');
    const second = highlightCode(source, null, 'file.unknown');

    expect(first).toBe(second);
    expect(first.language).toBe('plain');
    expect(text(first)).toBe(source);
    expect(classes(first)).toEqual([]);
  });
});

function text(result: ReturnType<typeof highlightCode>) {
  return result.lines.map(line => line.map(token => token.content).join('')).join('\n');
}

function classes(result: ReturnType<typeof highlightCode>) {
  return [...new Set(result.lines.flatMap(line => line.flatMap(token => token.classes)))];
}
