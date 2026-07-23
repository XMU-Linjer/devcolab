import { mount } from '@vue/test-utils';
import { describe, expect, it } from 'vitest';

import WorkspaceDeleteDialog from './WorkspaceDeleteDialog.vue';

function mountDialog(loading = false) {
  return mount(WorkspaceDeleteDialog, {
    props: {
      modelValue: true,
      workspaceName: '订单协作空间',
      loading,
      'onUpdate:modelValue': () => undefined,
    },
    global: {
      stubs: {
        ElDialog: {
          props: ['modelValue'],
          template: '<div><slot /><slot name="footer" /></div>',
        },
        ElAlert: {
          props: ['title'],
          template: '<div>{{ title }}</div>',
        },
        ElInput: {
          props: ['modelValue', 'disabled'],
          emits: ['update:modelValue'],
          template: `
            <input
              :value="modelValue"
              :disabled="disabled"
              @input="$emit('update:modelValue', $event.target.value)"
            />
          `,
        },
        ElButton: {
          props: ['disabled', 'loading'],
          emits: ['click'],
          template: `
            <button
              :disabled="disabled"
              :data-loading="loading"
              @click="$emit('click')"
            >
              <slot />
            </button>
          `,
        },
      },
    },
  });
}

describe('WorkspaceDeleteDialog', () => {
  it('shows workspace name and requires an exact confirmation', async () => {
    const wrapper = mountDialog();
    const input = wrapper.get('input');
    const confirmButton = wrapper.findAll('button')
      .find(button => button.text().includes('确认删除'));

    expect(wrapper.text()).toContain('订单协作空间');
    expect(wrapper.text()).toContain('不可逆操作');
    expect(confirmButton?.attributes('disabled')).toBeDefined();

    await input.setValue('订单协作');
    expect(confirmButton?.attributes('disabled')).toBeDefined();

    await input.setValue('订单协作空间');
    expect(confirmButton?.attributes('disabled')).toBeUndefined();

    await confirmButton?.trigger('click');
    expect(wrapper.emitted('confirm')).toHaveLength(1);
  });

  it('disables duplicate submission while deletion is running', async () => {
    const wrapper = mountDialog(true);
    const input = wrapper.get('input');
    const confirmButton = wrapper.findAll('button')
      .find(button => button.text().includes('确认删除'));

    expect(input.attributes('disabled')).toBeDefined();
    expect(confirmButton?.attributes('disabled')).toBeDefined();

    await confirmButton?.trigger('click');
    expect(wrapper.emitted('confirm')).toBeUndefined();
  });

  it('clears the confirmation after closing', async () => {
    const wrapper = mountDialog();
    const input = wrapper.get('input');
    await input.setValue('订单协作空间');

    await wrapper.setProps({ modelValue: false });
    await wrapper.setProps({ modelValue: true });

    expect((wrapper.get('input').element as HTMLInputElement).value).toBe('');
  });
});
