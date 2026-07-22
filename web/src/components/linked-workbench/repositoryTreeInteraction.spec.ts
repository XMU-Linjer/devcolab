import { flushPromises, mount } from '@vue/test-utils';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { nextTick } from 'vue';

import type { GitRepositoryFile } from '@/api/git';
import { buildRepositoryTree } from '@/utils/repositoryTree';
import LinkedRepositoryContext from './LinkedRepositoryContext.vue';

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
});
