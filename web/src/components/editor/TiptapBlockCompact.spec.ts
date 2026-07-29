import { flushPromises, mount } from '@vue/test-utils';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import type { DocumentBlock } from '@/api/block';
import TiptapBlock from './TiptapBlock.vue';

describe('TiptapBlock compact linked reading', () => {
  beforeEach(() => vi.useFakeTimers());
  afterEach(() => {
    vi.clearAllTimers();
    vi.useRealTimers();
  });

  it('keeps reading mode compact and distinguishes single click from double click', async () => {
    const wrapper = mountBlock();
    await flushPromises();

    expect(wrapper.classes()).toContain('is-compact-reading');
    expect(wrapper.find('.block-toolbar').exists()).toBe(false);
    expect(wrapper.find('.block-footer').exists()).toBe(false);
    expect(wrapper.get('.tiptap-content').attributes('contenteditable')).toBe('false');

    await wrapper.trigger('click');
    await wrapper.trigger('dblclick');
    await vi.advanceTimersByTimeAsync(200);

    expect(wrapper.emitted('edit-request')).toHaveLength(1);
    expect(wrapper.emitted('select')).toBeUndefined();
  });

  it('single click selects the binding but does not enter editing', async () => {
    const wrapper = mountBlock();
    await wrapper.trigger('click');
    await vi.advanceTimersByTimeAsync(200);

    expect(wrapper.emitted('select')).toHaveLength(1);
    expect(wrapper.emitted('edit-request')).toBeUndefined();
  });

  it('does not save or exit on blur and maps explicit keyboard commands', async () => {
    const wrapper = mountBlock({ editing: true });
    await flushPromises();
    const content = wrapper.get('.tiptap-content');

    expect(content.attributes('contenteditable')).toBe('true');
    await content.trigger('blur');
    expect(wrapper.emitted('save')).toBeUndefined();
    expect(wrapper.emitted('editing-stop')).toBeUndefined();

    await content.trigger('keydown', { ctrlKey: true, key: 's' });
    await content.trigger('keydown', { ctrlKey: true, key: 'Enter' });
    await content.trigger('keydown', { key: 'Escape' });

    expect(wrapper.emitted('save-intent')).toEqual([['stay'], ['finish']]);
    expect(wrapper.emitted('cancel-request')).toHaveLength(1);
  });

  it('renders structured Markdown semantics without raw markers', async () => {
    const wrapper = mountBlock({ block: structuredMarkdownBlock() });
    await flushPromises();

    expect(wrapper.get('h2').text()).toBe('DocumentType');
    expect(wrapper.findAll('li').map(item => item.text())).toEqual(['API 文档', 'ADR 决策']);
    expect(wrapper.get('.tiptap-content p code').text()).toBe('DocumentType');
    expect(wrapper.get('pre').text()).toContain('enum DocumentType');
    expect(wrapper.text()).not.toContain('##');
    expect(wrapper.text()).not.toContain('`DocumentType`');
  });
});

function mountBlock(overrides: Record<string, unknown> = {}) {
  return mount(TiptapBlock, {
    props: {
      block: documentBlock(),
      isFirst: true,
      isLast: true,
      compactReading: true,
      editing: false,
      ...overrides,
    },
    global: {
      stubs: {
        ElButton: {
          props: ['disabled', 'loading'],
          emits: ['click'],
          template: '<button :disabled="disabled" @click="$emit(\'click\')"><slot /></button>',
        },
        ElDropdown: { template: '<div><slot /><slot name="dropdown" /></div>' },
        ElDropdownMenu: { template: '<div><slot /></div>' },
        ElDropdownItem: { template: '<button><slot /></button>' },
        ElTag: { template: '<span><slot /></span>' },
        ElTooltip: { template: '<span><slot /></span>' },
      },
    },
  });
}

function documentBlock(): DocumentBlock {
  return {
    id: 'block-a',
    documentId: 'document-a',
    type: 'PARAGRAPH',
    content: {
      text: '订单创建流程',
      schemaVersion: 1,
      document: {
        type: 'doc',
        content: [{
          type: 'paragraph',
          content: [{ type: 'text', text: '订单创建流程' }],
        }],
      },
    },
    sortOrder: 0,
    version: 1,
    createdBy: 'user-a',
    createdAt: '2026-07-29T00:00:00Z',
    updatedAt: '2026-07-29T00:00:00Z',
  };
}

function structuredMarkdownBlock(): DocumentBlock {
  return {
    ...documentBlock(),
    content: {
      text: 'DocumentType\nAPI 文档\nADR 决策\nenum DocumentType { API, ADR }',
      schemaVersion: 1,
      document: {
        type: 'doc',
        content: [
          {
            type: 'heading',
            attrs: { level: 2 },
            content: [{ type: 'text', text: 'DocumentType' }],
          },
          {
            type: 'paragraph',
            content: [{
              type: 'text',
              text: 'DocumentType',
              marks: [{ type: 'code' }],
            }],
          },
          {
            type: 'bulletList',
            content: [
              {
                type: 'listItem',
                content: [{
                  type: 'paragraph',
                  content: [{ type: 'text', text: 'API 文档' }],
                }],
              },
              {
                type: 'listItem',
                content: [{
                  type: 'paragraph',
                  content: [{ type: 'text', text: 'ADR 决策' }],
                }],
              },
            ],
          },
          {
            type: 'codeBlock',
            content: [{ type: 'text', text: 'enum DocumentType { API, ADR }' }],
          },
        ],
      },
    },
  };
}
