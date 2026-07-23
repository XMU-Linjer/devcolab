import ElementPlus from 'element-plus';
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import type { Workspace } from '@/api/workspace';

const mocks = vi.hoisted(() => ({
  listWorkspaces: vi.fn(),
  createWorkspace: vi.fn(),
  deleteWorkspace: vi.fn(),
  registerGitRepository: vi.fn(),
  routerPush: vi.fn(),
  logout: vi.fn(),
}));

vi.mock('@/api/workspace', () => ({
  listWorkspaces: mocks.listWorkspaces,
  createWorkspace: mocks.createWorkspace,
  deleteWorkspace: mocks.deleteWorkspace,
}));

vi.mock('@/api/git', () => ({
  registerGitRepository: mocks.registerGitRepository,
}));

vi.mock('vue-router', () => ({
  useRouter: () => ({
    push: mocks.routerPush,
  }),
}));

vi.mock('@/stores/auth', () => ({
  useAuthStore: () => ({
    currentUser: {
      username: 'admin',
      displayName: '管理员',
    },
    logout: mocks.logout,
  }),
}));

import HomeView from './HomeView.vue';

const workspace: Workspace = {
  id: 'workspace-1',
  name: '订单协作空间',
  currentUserRole: 'ADMIN',
  createdAt: '2026-07-23T08:00:00Z',
  updatedAt: '2026-07-23T08:00:00Z',
};

async function mountHome(): Promise<VueWrapper> {
  mocks.listWorkspaces.mockResolvedValue([workspace]);
  const wrapper = mount(HomeView, {
    attachTo: document.body,
    global: {
      plugins: [ElementPlus],
      stubs: {
        AppSidebar: true,
        NotificationCenter: true,
        WorkspaceCreateDialog: true,
      },
    },
  });
  await flushPromises();
  return wrapper;
}

async function openDeleteDialog(wrapper: VueWrapper) {
  await wrapper.get('.workspace-card-menu').trigger('click');
  await flushPromises();
  const deleteItem = document.body.querySelector<HTMLElement>(
    '.el-dropdown-menu__item',
  );
  expect(deleteItem?.textContent).toContain('删除工作区');
  deleteItem?.click();
  await flushPromises();
}

function confirmationInput() {
  return document.body.querySelector<HTMLInputElement>(
    '#workspace-delete-confirmation',
  );
}

function confirmButton() {
  return Array.from(document.body.querySelectorAll<HTMLButtonElement>('button'))
    .find(button => button.textContent?.includes('确认删除'));
}

describe('HomeView workspace deletion', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mocks.deleteWorkspace.mockResolvedValue(undefined);
  });

  afterEach(() => {
    document.body.innerHTML = '';
  });

  it('keeps card navigation but stops the menu click from navigating', async () => {
    const wrapper = await mountHome();

    await wrapper.get('.workspace-card').trigger('click');
    expect(mocks.routerPush).toHaveBeenCalledWith({
      name: 'workspace-code',
      params: { workspaceId: workspace.id },
    });

    mocks.routerPush.mockClear();
    await wrapper.get('.workspace-card-menu').trigger('click');
    await flushPromises();
    expect(mocks.routerPush).not.toHaveBeenCalled();
  });

  it('deletes only after exact confirmation and updates the count', async () => {
    const wrapper = await mountHome();
    await openDeleteDialog(wrapper);

    expect(document.body.textContent).toContain(workspace.name);
    expect(confirmButton()?.disabled).toBe(true);

    confirmationInput()!.value = workspace.name;
    confirmationInput()!.dispatchEvent(new Event('input', { bubbles: true }));
    await flushPromises();
    expect(confirmButton()?.disabled).toBe(false);

    confirmButton()?.click();
    await flushPromises();

    expect(mocks.deleteWorkspace).toHaveBeenCalledTimes(1);
    expect(mocks.deleteWorkspace).toHaveBeenCalledWith(workspace.id);
    expect(wrapper.find('.workspace-card').exists()).toBe(false);
    expect(wrapper.text()).toContain('0 个工作区');
  });

  it('keeps the card and dialog when deletion fails', async () => {
    mocks.deleteWorkspace.mockRejectedValueOnce(
      new Error('删除服务暂时不可用'),
    );
    const wrapper = await mountHome();
    await openDeleteDialog(wrapper);

    confirmationInput()!.value = workspace.name;
    confirmationInput()!.dispatchEvent(new Event('input', { bubbles: true }));
    await flushPromises();
    confirmButton()?.click();
    await flushPromises();

    expect(wrapper.find('.workspace-card').exists()).toBe(true);
    expect(wrapper.text()).toContain('1 个工作区');
    expect(confirmationInput()).not.toBeNull();
  });
});
