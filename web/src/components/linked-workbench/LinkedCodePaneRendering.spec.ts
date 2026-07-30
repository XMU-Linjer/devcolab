import { mount } from '@vue/test-utils';
import { createPinia } from 'pinia';
import { describe, expect, it, vi } from 'vitest';

import type { CodeAnchor, CodeDocumentLink } from '@/types/linkedWorkbench';
import LinkedCodePane from './LinkedCodePane.vue';

describe('LinkedCodePane rendering', () => {
  it('renders one overlay and semantic line positions for a multi-line binding', () => {
    const wrapper = mountPane({
      content: 'one\ntwo\nthree\nfour\nfive',
      anchors: [anchor('anchor-a', 2, 4)],
      links: [link('link-a', 'anchor-a')],
      activeLinkId: 'link-a',
    });

    expect(wrapper.findAll('.code-range-overlay')).toHaveLength(1);
    expect(wrapper.get('.code-range-overlay').classes()).toContain('is-active');
    expect(wrapper.get('.code-range-overlay').attributes('style')).toContain('top: 33px');
    expect(wrapper.get('.code-range-overlay').attributes('style')).toContain('height: 75px');
    expect(wrapper.findAll('.code-line')[1]!.classes()).toContain('range-start');
    expect(wrapper.findAll('.code-line')[2]!.classes()).toContain('range-middle');
    expect(wrapper.findAll('.code-line')[3]!.classes()).toContain('range-end');
  });

  it('keeps adjacent bindings visually separate and prioritizes the active range', () => {
    const wrapper = mountPane({
      content: 'one\ntwo\nthree\nfour',
      anchors: [anchor('anchor-a', 1, 2), anchor('anchor-b', 3, 4)],
      links: [link('link-a', 'anchor-a'), link('link-b', 'anchor-b')],
      activeLinkId: 'link-b',
    });

    const overlays = wrapper.findAll('.code-range-overlay');
    expect(overlays).toHaveLength(2);
    expect(overlays[0]!.classes()).toContain('is-linked');
    expect(overlays[1]!.classes()).toContain('is-active');
    expect(wrapper.findAll('.code-line')[1]!.classes()).toContain('range-end');
    expect(wrapper.findAll('.code-line')[2]!.classes()).toContain('range-start');
  });

  it('uses exactly one line height for a single-line range', () => {
    const wrapper = mountPane({
      content: 'one\ntwo\nthree',
      anchors: [anchor('anchor-a', 2, 2)],
      links: [link('link-a', 'anchor-a')],
      activeLinkId: 'link-a',
    });

    expect(wrapper.get('.code-range-overlay').attributes('style')).toContain('top: 33px');
    expect(wrapper.get('.code-range-overlay').attributes('style')).toContain('height: 25px');
    expect(wrapper.findAll('.code-line')[1]!.classes()).toContain('range-single');
  });

  it('updates the whole active range without leaving the previous range active', async () => {
    const anchors = [anchor('anchor-a', 1, 2), anchor('anchor-b', 4, 5)];
    const links = [link('link-a', 'anchor-a'), link('link-b', 'anchor-b')];
    const wrapper = mountPane({
      content: 'one\ntwo\nthree\nfour\nfive',
      anchors,
      links,
      activeLinkId: 'link-a',
    });

    expect(wrapper.findAll('.code-range-overlay.is-active')).toHaveLength(1);
    expect(wrapper.findAll('.code-line.is-active')).toHaveLength(2);

    await wrapper.setProps({ activeLinkId: 'link-b' });

    expect(wrapper.findAll('.code-range-overlay.is-active')).toHaveLength(1);
    expect(wrapper.findAll('.code-line.is-active')).toHaveLength(2);
    expect(wrapper.findAll('.code-line')[0]!.classes()).not.toContain('is-active');
    expect(wrapper.findAll('.code-line')[3]!.classes()).toContain('is-active');
  });

  it('renders syntax tokens without injecting repository source as HTML', () => {
    const source = '@Override\npublic String name() { return "<script>alert(1)</script>"; }';
    const wrapper = mountPane({
      content: source,
      path: 'src/Example.java',
      language: 'Java',
    });

    expect(wrapper.findAll('.code-line')).toHaveLength(2);
    expect(wrapper.findAll('script')).toHaveLength(0);
    expect(wrapper.get('.code-lines').text()).toContain('<script>alert(1)</script>');
    expect(wrapper.findAll('.token.keyword').length).toBeGreaterThan(0);
    expect(wrapper.findAll('.token.string').length).toBeGreaterThan(0);
  });

  it('ignores invalid and FILE-level ranges without hiding source lines', () => {
    const wrapper = mountPane({
      content: 'one\ntwo\nthree',
      anchors: [
        anchor('invalid', 0, 2),
        { ...anchor('file', 1, 3), anchorKind: 'FILE' },
      ],
      links: [link('invalid-link', 'invalid'), link('file-link', 'file')],
      activeLinkId: 'invalid-link',
    });

    expect(wrapper.findAll('.code-range-overlay')).toHaveLength(0);
    expect(wrapper.findAll('.code-line')).toHaveLength(3);
    expect(wrapper.findAll('.code-line.is-linked')).toHaveLength(0);
  });

  it('keeps the active overlay pinned to the visible viewport while scrolling horizontally', async () => {
    const wrapper = mountPane({
      content: `short\n${'long-content '.repeat(80)}\nend`,
      anchors: [anchor('anchor-a', 1, 3)],
      links: [link('link-a', 'anchor-a')],
      activeLinkId: 'link-a',
    });
    const viewport = setViewportMetrics(wrapper, 640, 0);

    await wrapper.get('.code-lines').trigger('scroll');
    expect(wrapper.get('.code-range-overlay').attributes('style')).toContain('left: 0px');
    expect(wrapper.get('.code-range-overlay').attributes('style')).toContain('width: 640px');

    viewport.scrollLeft = 420;
    await wrapper.get('.code-lines').trigger('scroll');
    expect(wrapper.get('.code-range-overlay').attributes('style')).toContain('left: 420px');
    expect(wrapper.get('.code-range-overlay').attributes('style')).toContain('width: 640px');
  });

  it('updates active and linked overlay widths when the viewport is resized', async () => {
    const wrapper = mountPane({
      content: 'one\ntwo\nthree\nfour',
      anchors: [anchor('anchor-a', 1, 2), anchor('anchor-b', 3, 4)],
      links: [link('link-a', 'anchor-a'), link('link-b', 'anchor-b')],
      activeLinkId: 'link-a',
    });
    const viewport = setViewportMetrics(wrapper, 720, 180);
    await wrapper.get('.code-lines').trigger('scroll');

    for (const overlay of wrapper.findAll('.code-range-overlay')) {
      expect(overlay.attributes('style')).toContain('left: 180px');
      expect(overlay.attributes('style')).toContain('width: 720px');
    }

    viewport.clientWidth = 480;
    window.dispatchEvent(new Event('resize'));
    await wrapper.vm.$nextTick();
    for (const overlay of wrapper.findAll('.code-range-overlay')) {
      expect(overlay.attributes('style')).toContain('width: 480px');
    }
  });

  it('resets horizontal scroll when a different file is opened', async () => {
    const wrapper = mountPane({
      content: 'one\ntwo\nthree',
      anchors: [anchor('anchor-a', 1, 2)],
      links: [link('link-a', 'anchor-a')],
      activeLinkId: 'link-a',
    });
    const viewport = setViewportMetrics(wrapper, 600, 260);
    await wrapper.get('.code-lines').trigger('scroll');

    await wrapper.setProps({
      path: 'src/Next.java',
      content: 'next\nfile',
      anchors: [],
      links: [],
      activeLinkId: null,
    });
    await wrapper.vm.$nextTick();

    expect(viewport.scrollLeft).toBe(0);
  });

  it('resets horizontal scroll for programmatic Binding focus', async () => {
    const scrollIntoView = vi.fn();
    Object.defineProperty(HTMLElement.prototype, 'scrollIntoView', {
      configurable: true,
      value: scrollIntoView,
    });
    const wrapper = mountPane({
      content: 'one\ntwo\nthree\nfour',
      anchors: [anchor('anchor-a', 2, 3)],
      links: [link('link-a', 'anchor-a')],
      activeLinkId: 'link-a',
    });
    const viewport = setViewportMetrics(wrapper, 600, 310);
    await wrapper.get('.code-lines').trigger('scroll');

    (wrapper.vm as unknown as { focusAnchor: (anchorId: string) => void }).focusAnchor('anchor-a');
    await wrapper.vm.$nextTick();

    expect(viewport.scrollLeft).toBe(0);
    expect(scrollIntoView).toHaveBeenCalledOnce();
  });

  it('preserves manual horizontal scroll during ordinary vertical scrolling in the same Binding', async () => {
    const wrapper = mountPane({
      content: 'one\ntwo\nthree',
      anchors: [anchor('anchor-a', 1, 3)],
      links: [link('link-a', 'anchor-a')],
      activeLinkId: 'link-a',
    });
    const viewport = setViewportMetrics(wrapper, 600, 275);
    await wrapper.get('.code-lines').trigger('scroll');

    viewport.scrollTop = 80;
    await wrapper.get('.code-lines').trigger('scroll');
    await wrapper.setProps({ issues: [] });

    expect(viewport.scrollLeft).toBe(275);
    expect(wrapper.get('.code-range-overlay').attributes('style')).toContain('left: 275px');
  });
});

function mountPane(overrides: Record<string, unknown> = {}) {
  return mount(LinkedCodePane, {
    props: {
      workspaceId: 'workspace',
      repositoryId: 'repository',
      content: 'class Example {}',
      path: 'src/Example.java',
      language: 'Java',
      anchors: [],
      links: [],
      issues: [],
      activeLinkId: null,
      sourceLoaded: true,
      ...overrides,
    },
    global: {
      plugins: [createPinia()],
      stubs: {
        ElButton: { template: '<button><slot /></button>' },
        ElDialog: true,
        ElInput: true,
        ElTag: true,
        ElSkeleton: true,
        ElEmpty: true,
      },
    },
  });
}

function anchor(id: string, startLine: number, endLine: number): CodeAnchor {
  return {
    id,
    bindingId: id,
    repositoryId: 'repository',
    revision: 'revision',
    branch: 'main',
    commitSha: 'revision',
    filePath: 'src/Example.java',
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

function setViewportMetrics(
  wrapper: ReturnType<typeof mountPane>,
  initialClientWidth: number,
  initialScrollLeft: number,
) {
  const element = wrapper.get('.code-lines').element as HTMLElement;
  const metrics = {
    clientWidth: initialClientWidth,
    scrollLeft: initialScrollLeft,
    scrollTop: 0,
  };
  Object.defineProperties(element, {
    clientWidth: {
      configurable: true,
      get: () => metrics.clientWidth,
    },
    scrollLeft: {
      configurable: true,
      get: () => metrics.scrollLeft,
      set: value => { metrics.scrollLeft = Number(value); },
    },
    scrollTop: {
      configurable: true,
      get: () => metrics.scrollTop,
      set: value => { metrics.scrollTop = Number(value); },
    },
  });
  return metrics;
}
