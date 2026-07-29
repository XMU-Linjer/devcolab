import { flushPromises, mount } from '@vue/test-utils';
import { ElMessageBox } from 'element-plus';
import { createMemoryHistory, createRouter } from 'vue-router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import type { DocumentBlock } from '@/api/block';
import BlockEditor from './BlockEditor.vue';
import TiptapBlock from './TiptapBlock.vue';

vi.mock('@/api/block', async (importOriginal) => {
  const original = await importOriginal<typeof import('@/api/block')>();
  return {
    ...original,
    listBlocks: vi.fn(),
    updateBlock: vi.fn(),
  };
});

import { listBlocks } from '@/api/block';

describe('BlockEditor compact editing state', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    HTMLElement.prototype.scrollIntoView = vi.fn();
    vi.mocked(listBlocks).mockResolvedValue([
      documentBlock('block-a', 0),
      documentBlock('block-b', 1),
    ]);
  });

  afterEach(() => {
    vi.restoreAllMocks();
    vi.clearAllTimers();
    vi.useRealTimers();
  });

  it('edits only the double-clicked Block and keeps editing during other Block selection', async () => {
    const wrapper = await mountEditor();
    const blocks = wrapper.findAllComponents(TiptapBlock);

    await blocks[0].trigger('dblclick');
    await flushPromises();
    expect(wrapper.findAll('.is-editing')).toHaveLength(1);
    expect(blocks[0].classes()).toContain('is-editing');

    await blocks[1].trigger('click');
    await vi.advanceTimersByTimeAsync(200);
    expect(blocks[0].classes()).toContain('is-editing');
    expect(blocks[1].classes()).not.toContain('is-editing');
    expect(wrapper.emitted('select-block')?.at(-1)).toEqual(['block-b']);
  });

  it('protects a dirty draft before switching the editing Block', async () => {
    const wrapper = await mountEditor();
    const blocks = wrapper.findAllComponents(TiptapBlock);
    const confirm = vi.spyOn(ElMessageBox, 'confirm');

    await blocks[0].trigger('dblclick');
    blocks[0].vm.$emit('dirty-change', 'block-a', true);
    confirm.mockRejectedValueOnce('close');
    await blocks[1].trigger('dblclick');
    await flushPromises();
    expect(blocks[0].classes()).toContain('is-editing');

    confirm.mockRejectedValueOnce('cancel');
    await blocks[1].trigger('dblclick');
    await flushPromises();
    expect(blocks[1].classes()).toContain('is-editing');
  });

  it('programmatic focus never enters or exits editing', async () => {
    const wrapper = await mountEditor();
    const blocks = wrapper.findAllComponents(TiptapBlock);

    expect(await (wrapper.vm as unknown as { focusBlock: (id: string) => Promise<boolean> })
      .focusBlock('block-a')).toBe(true);
    expect(wrapper.findAll('.is-editing')).toHaveLength(0);

    await blocks[0].trigger('dblclick');
    await (wrapper.vm as unknown as { focusBlock: (id: string) => Promise<boolean> })
      .focusBlock('block-b');
    expect(blocks[0].classes()).toContain('is-editing');
  });
});

async function mountEditor() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [{ path: '/', component: { template: '<div />' } }],
  });
  await router.push('/');
  await router.isReady();
  const wrapper = mount(BlockEditor, {
    props: {
      documentId: 'document-a',
      compactReading: true,
    },
    global: {
      plugins: [router],
    },
  });
  await flushPromises();
  return wrapper;
}

function documentBlock(id: string, sortOrder: number): DocumentBlock {
  return {
    id,
    documentId: 'document-a',
    type: 'PARAGRAPH',
    content: {
      text: id,
      schemaVersion: 1,
      document: {
        type: 'doc',
        content: [{
          type: 'paragraph',
          content: [{ type: 'text', text: id }],
        }],
      },
    },
    sortOrder,
    version: 1,
    createdBy: 'user-a',
    createdAt: '2026-07-29T00:00:00Z',
    updatedAt: '2026-07-29T00:00:00Z',
  };
}
