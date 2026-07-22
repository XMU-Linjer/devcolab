import { mount } from '@vue/test-utils';
import { createMemoryHistory, createRouter } from 'vue-router';
import { describe, expect, it } from 'vitest';

import AppSidebar from './AppSidebar.vue';
import LinkedWorkspaceNavigation from './LinkedWorkspaceNavigation.vue';
import LinkedRepositoryContext from '@/components/linked-workbench/LinkedRepositoryContext.vue';

function testRouter() {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/workspaces', name: 'workspaces', component: { template: '<div />' } },
      { path: '/workspaces/:workspaceId', name: 'workspace-detail', component: { template: '<div />' } },
      { path: '/workspaces/:workspaceId/code', name: 'code-workbench', component: { template: '<div />' } },
    ],
  });
}

describe('linked workbench sidebar', () => {
  it('uses the linked variant only on the linked workbench route', async () => {
    const router = testRouter();
    await router.push('/workspaces/w1/code');
    await router.isReady();
    const wrapper = mount(AppSidebar, {
      props: { modelValue: false, active: 'code', workspaceId: 'w1' },
      global: { plugins: [router] },
    });
    expect(wrapper.get('aside').attributes('data-variant')).toBe('LINKED_WORKBENCH');
    expect(wrapper.text()).toContain('联动对照');
    expect(wrapper.text()).not.toContain('首页');

    await router.push('/workspaces/w1');
    await wrapper.vm.$nextTick();
    expect(wrapper.get('aside').attributes('data-variant')).toBe('DEFAULT');
    expect(wrapper.text()).toContain('首页');
  });

  it('emits real workbench actions from linked navigation', async () => {
    const wrapper = mount(LinkedWorkspaceNavigation, {
      props: { activeItem: 'linked', linkedCount: 3, reviewCount: 2, driftCount: 1 },
    });
    const buttons = wrapper.findAll('button');
    await buttons[1].trigger('click');
    await buttons[2].trigger('click');
    await buttons[3].trigger('click');
    expect(wrapper.emitted('open-linked')).toHaveLength(1);
    expect(wrapper.emitted('open-review')).toHaveLength(1);
    expect(wrapper.emitted('open-drift')).toHaveLength(1);
  });

  it('keeps selection outside the sidebar when collapsed', async () => {
    const router = testRouter();
    await router.push('/workspaces/w1/code');
    await router.isReady();
    const wrapper = mount(AppSidebar, {
      props: { modelValue: false, active: 'code', workspaceId: 'w1' },
      global: { plugins: [router] },
    });
    await wrapper.get('.sidebar-toggle').trigger('click');
    expect(wrapper.emitted('update:modelValue')?.[0]).toEqual([true]);
    expect(wrapper.emitted('select-document')).toBeUndefined();
  });

  it('updates the symbol summary and leaves scroll ownership to its parent', async () => {
    const anchor = {
      id: 'a1', repositoryId: 'r1', branch: 'main', commitSha: 'abc', filePath: 'A.java',
      language: 'Java', symbolName: 'createOrder()', startLine: 3, endLine: 8, status: 'VALID' as const,
    };
    const wrapper = mount(LinkedRepositoryContext, {
      props: {
        repositories: [], repositoryId: '', fileTree: [], filesCount: 0, selectedFilePath: '',
        documents: [], selectedDocumentId: '', activeAnchor: anchor,
        linkedBlockCount: 3, unresolvedIssueCount: 1, recentCommitCount: 4,
      },
      global: { stubs: { ElSelect: true, ElOption: true, ElSkeleton: true, ElTree: true } },
    });
    expect(wrapper.text()).toContain('createOrder()');
    expect(wrapper.text()).toContain('关联 Block3');
    expect(wrapper.find('.linked-repository-tree').exists()).toBe(true);
    expect(wrapper.find('.linked-context').attributes('style')).toBeUndefined();

    await wrapper.setProps({ activeAnchor: null });
    expect(wrapper.text()).toContain('未选择代码符号');
  });
});
