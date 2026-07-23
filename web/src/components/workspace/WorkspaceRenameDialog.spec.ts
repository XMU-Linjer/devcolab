import ElementPlus from 'element-plus';
import { flushPromises, mount } from '@vue/test-utils';
import { afterEach, describe, expect, it } from 'vitest';

import WorkspaceRenameDialog from './WorkspaceRenameDialog.vue';

async function mountDialog(options?: {
  name?: string;
  loading?: boolean;
}) {
  const wrapper = mount(WorkspaceRenameDialog, {
    attachTo: document.body,
    props: {
      modelValue: true,
      workspaceName: options?.name ?? '原工作区',
      loading: options?.loading ?? false,
      'onUpdate:modelValue': (value: boolean) => {
        void wrapper.setProps({ modelValue: value });
      },
    },
    global: {
      plugins: [ElementPlus],
    },
  });
  await flushPromises();
  return wrapper;
}

function nameInput() {
  return document.body.querySelector<HTMLInputElement>(
    '#workspace-rename-name',
  );
}

function saveButton() {
  return Array.from(document.body.querySelectorAll<HTMLButtonElement>('button'))
    .find(button => button.textContent?.includes('保存'));
}

describe('WorkspaceRenameDialog', () => {
  afterEach(() => {
    document.body.innerHTML = '';
  });

  it('prefills and focuses the current name while unchanged input stays disabled', async () => {
    await mountDialog();

    expect(nameInput()?.value).toBe('原工作区');
    expect(nameInput()?.autofocus).toBe(true);
    expect(saveButton()?.disabled).toBe(true);
  });

  it('trims the name and submits once with Enter', async () => {
    const wrapper = await mountDialog();
    const input = nameInput()!;

    input.value = '  新工作区  ';
    input.dispatchEvent(new Event('input', { bubbles: true }));
    await flushPromises();
    expect(saveButton()?.disabled).toBe(false);

    input.dispatchEvent(new KeyboardEvent('keyup', {
      key: 'Enter',
      bubbles: true,
    }));
    await flushPromises();

    expect(wrapper.emitted('confirm')).toEqual([['新工作区']]);
  });

  it('disables editing and duplicate submission while loading', async () => {
    await mountDialog({ loading: true });

    expect(nameInput()?.disabled).toBe(true);
    expect(saveButton()?.disabled).toBe(true);
    saveButton()?.click();
  });

  it('clears the draft after closing and restores the latest name when reopened', async () => {
    const wrapper = await mountDialog();
    const input = nameInput()!;
    input.value = '临时名称';
    input.dispatchEvent(new Event('input', { bubbles: true }));

    await wrapper.setProps({ modelValue: false });
    await flushPromises();
    await wrapper.setProps({
      modelValue: true,
      workspaceName: '服务端最新名称',
    });
    await flushPromises();

    expect(nameInput()?.value).toBe('服务端最新名称');
  });
});
