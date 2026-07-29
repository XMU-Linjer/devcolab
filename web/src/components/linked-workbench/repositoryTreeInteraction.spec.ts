import { flushPromises, mount } from '@vue/test-utils';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { nextTick } from 'vue';

import type { GitRepositoryFile } from '@/api/git';
import { buildRepositoryTree } from '@/utils/repositoryTree';
import LinkedRepositoryContext from './LinkedRepositoryContext.vue';
import repositoryContextSource from './LinkedRepositoryContext.vue?raw';

function file(path: string): GitRepositoryFile {
  return {
    id: path,
    path,
    blobSha: `sha-${path}`,
    sizeBytes: 1,
    language: null,
    readable: true,
  };
}

const sourceFiles = [
  file('README.md'),
  file('src/main.py'),
  file('src/agents/rule2.py'),
  file('src/agents/rule10.py'),
];
const restorationFiles = [
  file('.mvn/wrapper/maven-wrapper.properties'),
  file('agent-service/app/context/budget.py'),
  file('web/src/main.ts'),
];

const baseProps = {
  repositories: [],
  repositoryId: 'repository-1',
  filesCount: sourceFiles.length,
  documents: [],
  selectedDocumentId: 'document-1',
  activeAnchor: null,
};

describe('repository tree interaction', () => {
  const scrollIntoView = vi.fn();

  beforeEach(() => {
    Object.defineProperty(HTMLElement.prototype, 'scrollIntoView', {
      configurable: true,
      value: scrollIntoView,
    });
  });

  afterEach(() => {
    scrollIntoView.mockReset();
  });

  it('expands selected parents and locates the selected file without smooth scrolling', async () => {
    const wrapper = mount(LinkedRepositoryContext, {
      props: {
        ...baseProps,
        fileTree: buildRepositoryTree(sourceFiles),
        selectedFilePath: 'src/agents/rule2.py',
      },
      global: { stubs: { ElSelect: true, ElOption: true, ElSkeleton: true } },
    });
    await flushPromises();
    await nextTick();

    expect(wrapper.findAll('.el-tree-node.is-expanded').length).toBeGreaterThanOrEqual(2);
    expect(wrapper.get('.el-tree-node.is-current').text()).toContain('rule2.py');
    expect(scrollIntoView).toHaveBeenCalledWith({
      block: 'nearest',
      inline: 'nearest',
      behavior: 'auto',
    });

    await wrapper.setProps({ fileTree: buildRepositoryTree([...sourceFiles]) });
    await flushPromises();
    expect(wrapper.findAll('.el-tree-node.is-expanded').length).toBeGreaterThanOrEqual(2);
    expect(wrapper.get('.el-tree-node.is-current').text()).toContain('rule2.py');
  });

  it('expands a directory without emitting a file selection', async () => {
    const wrapper = mount(LinkedRepositoryContext, {
      props: {
        ...baseProps,
        fileTree: buildRepositoryTree(sourceFiles),
        selectedFilePath: '',
      },
      global: { stubs: { ElSelect: true, ElOption: true, ElSkeleton: true } },
    });
    await wrapper.get('.el-tree-node__content').trigger('click');
    await nextTick();

    expect(wrapper.emitted('select-file')).toBeUndefined();
    expect(wrapper.get('.el-tree-node').classes()).toContain('is-expanded');
  });

  it('emits the stable file path immediately when a file is selected', async () => {
    const wrapper = mount(LinkedRepositoryContext, {
      props: {
        ...baseProps,
        fileTree: buildRepositoryTree(sourceFiles),
        selectedFilePath: '',
      },
      global: { stubs: { ElSelect: true, ElOption: true, ElSkeleton: true } },
    });
    const nodes = wrapper.findAll('.el-tree-node__content');
    const readmeNode = nodes.find(item => item.text().includes('README.md'));
    expect(readmeNode).toBeDefined();
    await readmeNode!.trigger('click');

    expect(wrapper.emitted('select-file')?.[0]).toEqual(['README.md']);
    expect(wrapper.getComponent({ name: 'ElTree' }).props('nodeKey')).toBe('key');
  });

  it('keeps icons and labels left aligned while reserving the right edge for metadata', async () => {
    const wrapper = mount(LinkedRepositoryContext, {
      props: {
        ...baseProps,
        fileTree: buildRepositoryTree(sourceFiles),
        selectedFilePath: 'README.md',
        fileLinkCounts: { 'README.md': 3 },
      },
      global: { stubs: { ElSelect: true, ElOption: true, ElSkeleton: true } },
    });
    await flushPromises();

    const selectedRow = wrapper.get('.el-tree-node.is-current .linked-tree-node');
    const children = Array.from(selectedRow.element.children);
    expect(children[0]?.classList.contains('repository-node-icon')).toBe(true);
    expect(children[1]?.classList.contains('repository-node-label')).toBe(true);
    expect(children[2]?.classList.contains('repository-node-meta')).toBe(true);
    expect(selectedRow.get('.repository-node-label').text()).toBe('README.md');
    expect(selectedRow.get('.repository-node-meta').text()).toBe('3');

    expect(repositoryContextSource).toMatch(/\.linked-tree-node \{[^}]*justify-content: flex-start/);
    expect(repositoryContextSource).toMatch(/\.repository-node-label \{[^}]*flex: 1 1 auto[^}]*margin-left: 0[^}]*text-align: left[^}]*text-overflow: ellipsis/);
    expect(repositoryContextSource).toMatch(/\.repository-node-meta \{[^}]*flex: 0 0 auto[^}]*margin-left: auto/);
  });

  it('uses the same icon-label structure for directories and files without horizontal scrolling', () => {
    const wrapper = mount(LinkedRepositoryContext, {
      props: {
        ...baseProps,
        fileTree: buildRepositoryTree(sourceFiles),
        selectedFilePath: '',
      },
      global: { stubs: { ElSelect: true, ElOption: true, ElSkeleton: true } },
    });

    const rows = wrapper.findAll('.linked-tree-node');
    expect(rows.length).toBeGreaterThan(1);
    for (const row of rows) {
      expect(row.element.children[0]?.classList.contains('repository-node-icon')).toBe(true);
      expect(row.element.children[1]?.classList.contains('repository-node-label')).toBe(true);
      expect(row.attributes('title')).toBeTruthy();
    }
    expect(repositoryContextSource).toMatch(/\.linked-repository-tree \{[^}]*overflow: hidden/);
    expect(repositoryContextSource).toMatch(/\.linked-tree-node \{[^}]*width: auto[^}]*flex: 1 1 auto[^}]*overflow: hidden/);
  });

  it('restores budget.py ancestors without expanding the first .mvn directory', async () => {
    const wrapper = mount(LinkedRepositoryContext, {
      props: {
        ...baseProps,
        filesCount: restorationFiles.length,
        fileTree: buildRepositoryTree(restorationFiles),
        selectedFilePath: 'agent-service/app/context/budget.py',
      },
      global: { stubs: { ElSelect: true, ElOption: true, ElSkeleton: true } },
    });
    await flushPromises();
    await nextTick();

    expect(wrapper.find('.linked-tree-node[title=".mvn"]').exists()).toBe(true);
    expect(wrapper.find(
      '.el-tree-node.is-expanded > .el-tree-node__content .linked-tree-node[title=".mvn"]',
    ).exists()).toBe(false);
    for (const key of [
      'agent-service',
      'agent-service/app',
      'agent-service/app/context',
    ]) {
      expect(wrapper.find(
        `.el-tree-node.is-expanded > .el-tree-node__content .linked-tree-node[title="${key}"]`,
      ).exists()).toBe(true);
    }
    expect(wrapper.get('.el-tree-node.is-current').text()).toContain('budget.py');
    expect(scrollIntoView).toHaveBeenCalled();
  });

  it('does not reopen an unrelated .mvn directory when the user clicks another directory', async () => {
    const wrapper = mount(LinkedRepositoryContext, {
      props: {
        ...baseProps,
        filesCount: restorationFiles.length,
        fileTree: buildRepositoryTree(restorationFiles),
        selectedFilePath: 'agent-service/app/context/budget.py',
      },
      global: { stubs: { ElSelect: true, ElOption: true, ElSkeleton: true } },
    });
    await flushPromises();

    const webRow = wrapper.get('.linked-tree-node[title="web"]');
    await webRow.element.parentElement?.dispatchEvent(new MouseEvent('click', { bubbles: true }));
    await nextTick();

    expect(wrapper.find(
      '.el-tree-node.is-expanded > .el-tree-node__content .linked-tree-node[title=".mvn"]',
    ).exists()).toBe(false);
    expect(wrapper.emitted('select-file')).toBeUndefined();
  });

  it('removes obsolete automatic ancestors when the selected file changes', async () => {
    const wrapper = mount(LinkedRepositoryContext, {
      props: {
        ...baseProps,
        filesCount: restorationFiles.length,
        fileTree: buildRepositoryTree(restorationFiles),
        selectedFilePath: '.mvn/wrapper/maven-wrapper.properties',
      },
      global: { stubs: { ElSelect: true, ElOption: true, ElSkeleton: true } },
    });
    await flushPromises();
    expect(wrapper.find(
      '.el-tree-node.is-expanded > .el-tree-node__content .linked-tree-node[title=".mvn"]',
    ).exists()).toBe(true);

    await wrapper.setProps({ selectedFilePath: 'agent-service/app/context/budget.py' });
    await flushPromises();
    await nextTick();

    expect(wrapper.find(
      '.el-tree-node.is-expanded > .el-tree-node__content .linked-tree-node[title=".mvn"]',
    ).exists()).toBe(false);
    expect(wrapper.get('.el-tree-node.is-current').text()).toContain('budget.py');
  });

  it('restores and locates the selected file after the loading skeleton is removed', async () => {
    const wrapper = mount(LinkedRepositoryContext, {
      props: {
        ...baseProps,
        filesCount: restorationFiles.length,
        fileTree: buildRepositoryTree(restorationFiles),
        selectedFilePath: 'agent-service/app/context/budget.py',
        loading: true,
      },
      global: { stubs: { ElSelect: true, ElOption: true, ElSkeleton: true } },
    });
    expect(wrapper.find('.el-tree-node').exists()).toBe(false);

    await wrapper.setProps({ loading: false });
    await flushPromises();
    await nextTick();

    expect(wrapper.get('.el-tree-node.is-current').text()).toContain('budget.py');
    expect(wrapper.find(
      '.el-tree-node.is-expanded > .el-tree-node__content .linked-tree-node[title=".mvn"]',
    ).exists()).toBe(false);
    expect(scrollIntoView).toHaveBeenCalled();
  });
});
