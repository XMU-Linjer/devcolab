import { flushPromises, shallowMount } from '@vue/test-utils';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import CodeWorkbenchView from './CodeWorkbenchView.vue';

const mocks = vi.hoisted(() => ({
  getSource: vi.fn(),
  queryBindings: vi.fn(),
  replace: vi.fn(),
}));

vi.mock('vue-router', () => ({
  useRoute: () => ({
    params: { workspaceId: 'workspace-1' },
    query: { repositoryId: 'repository-1' },
  }),
  useRouter: () => ({ replace: mocks.replace, push: vi.fn() }),
}));

vi.mock('@/api/agent', () => ({
  createAgentJob: vi.fn(),
  getAgentJob: vi.fn(),
  listAgentJobUnits: vi.fn(),
  readableAgentError: (_error: unknown, fallback: string) => fallback,
}));

vi.mock('@/api/workspace', () => ({
  getWorkspace: vi.fn().mockResolvedValue({
    id: 'workspace-1',
    name: 'Workspace',
    currentUserRole: 'ADMIN',
  }),
}));

vi.mock('@/api/git', () => ({
  listGitRepositories: vi.fn().mockResolvedValue([{
    id: 'repository-1',
    name: 'devcollab',
    defaultBranch: 'main',
    lastSyncedCommit: 'revision-1',
    syncStatus: 'READY',
  }]),
  listGitRepositoryFiles: vi.fn().mockResolvedValue([
    file('src/A.java'),
    file('src/B.java'),
    file('src/C.java'),
  ]),
  listGitChanges: vi.fn().mockResolvedValue([]),
  getGitRepositorySource: mocks.getSource,
  queryCodeBindings: mocks.queryBindings,
  syncGitRepository: vi.fn(),
}));

vi.mock('@/api/document', () => ({
  listDocumentTree: vi.fn().mockResolvedValue([
    documentNode('document-a', 'Document A'),
    documentNode('document-c', 'Document C'),
  ]),
  getDocument: vi.fn().mockImplementation((id: string) => Promise.resolve({
    id,
    workspaceId: 'workspace-1',
    title: id === 'document-a' ? 'Document A' : 'Document C',
    documentType: 'BACKEND',
    reviewStatus: 'DRAFT',
  })),
  listDocumentVersions: vi.fn().mockResolvedValue([]),
}));

vi.mock('@/api/documentChange', () => ({
  getPendingDocumentChangeCount: vi.fn().mockResolvedValue(0),
}));

vi.mock('@/composables/useDocumentCollaboration', () => ({
  useDocumentCollaboration: () => ({
    connected: { value: false },
    members: { value: [] },
    editingStates: { value: [] },
    latestRemoteBlock: { value: null },
    startEditing: vi.fn(),
    stopEditing: vi.fn(),
    updateContent: vi.fn(),
  }),
}));

describe('CodeWorkbenchView formal bindings', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mocks.getSource.mockImplementation(
      (_workspaceId: string, _repositoryId: string, path: string) =>
        Promise.resolve(source(path)),
    );
    mocks.queryBindings.mockImplementation(
      (_workspaceId: string, _repositoryId: string, _revision: string, path: string) =>
        Promise.resolve(bindingResult(path)),
    );
  });

  it('loads A, empty B, C and A again without reusing another file bindings', async () => {
    const wrapper = mountView();
    await flushPromises();
    expect(snapshot(wrapper)).toEqual({
      sourcePath: 'src/A.java',
      documentIds: 'document-a',
    });

    await selectFile(wrapper, 'src/B.java');
    expect(snapshot(wrapper)).toEqual({
      sourcePath: 'src/B.java',
      documentIds: '',
    });

    await selectFile(wrapper, 'src/C.java');
    expect(snapshot(wrapper)).toEqual({
      sourcePath: 'src/C.java',
      documentIds: 'document-c',
    });

    await selectFile(wrapper, 'src/A.java');
    expect(snapshot(wrapper)).toEqual({
      sourcePath: 'src/A.java',
      documentIds: 'document-a',
    });
    expect(mocks.queryBindings).toHaveBeenLastCalledWith(
      'workspace-1',
      'repository-1',
      'revision-1',
      'src/A.java',
    );
    wrapper.unmount();
  });

  it('ignores a late A response after B becomes the selected file', async () => {
    const lateA = deferred<ReturnType<typeof source>>();
    mocks.getSource.mockImplementation(
      (_workspaceId: string, _repositoryId: string, path: string) =>
        path === 'src/A.java' ? lateA.promise : Promise.resolve(source(path)),
    );
    const wrapper = mountView();
    await flushPromises();

    await selectFile(wrapper, 'src/B.java');
    lateA.resolve(source('src/A.java'));
    await flushPromises();

    expect(snapshot(wrapper)).toEqual({
      sourcePath: 'src/B.java',
      documentIds: '',
    });
    expect(mocks.queryBindings).not.toHaveBeenCalledWith(
      'workspace-1',
      'repository-1',
      'revision-1',
      'src/A.java',
    );
    wrapper.unmount();
  });
});

function mountView() {
  return shallowMount(CodeWorkbenchView, {
    global: {
      stubs: {
        AppSidebar: { template: '<aside><slot name="workspace-panel" /></aside>' },
        LinkedRepositoryContext: {
          name: 'LinkedRepositoryContext',
          props: ['documents'],
          emits: ['select-file'],
          template: `
            <div>
              <span data-test="document-ids">{{ documents.map(item => item.id).join(',') }}</span>
              <button data-test="select-a" @click="$emit('select-file', 'src/A.java')" />
              <button data-test="select-b" @click="$emit('select-file', 'src/B.java')" />
              <button data-test="select-c" @click="$emit('select-file', 'src/C.java')" />
            </div>
          `,
        },
        LinkedWorkbenchShell: {
          props: ['sourcePath'],
          methods: {
            focusAnchor: vi.fn(),
            focusBlock: vi.fn(),
          },
          template: '<section data-test="source-path">{{ sourcePath }}<slot name="header-actions" /></section>',
        },
        NotificationCenter: true,
        ElAlert: true,
        ElSkeleton: true,
        ElDrawer: true,
        ElButton: true,
      },
    },
  });
}

async function selectFile(
  wrapper: ReturnType<typeof mountView>,
  path: 'src/A.java' | 'src/B.java' | 'src/C.java',
) {
  const key = path.slice(4, 5).toLowerCase();
  await wrapper.find(`[data-test="select-${key}"]`).trigger('click');
  await flushPromises();
}

function snapshot(wrapper: ReturnType<typeof mountView>) {
  return {
    sourcePath: wrapper.find('[data-test="source-path"]').text(),
    documentIds: wrapper.find('[data-test="document-ids"]').text(),
  };
}

function bindingResult(path: string) {
  const documentId = path === 'src/A.java'
    ? 'document-a'
    : path === 'src/C.java' ? 'document-c' : null;
  return {
    workspaceId: 'workspace-1',
    repositoryId: 'repository-1',
    filePath: path,
    fileHasBindings: documentId !== null,
    bindings: documentId ? [{
      bindingId: `binding-${path}`,
      documentId,
      blockId: null,
      pathPattern: path,
      documentTitle: documentId === 'document-a' ? 'Document A' : 'Document C',
    }] : [],
    truncated: false,
    omittedBindingCount: 0,
  };
}

function file(path: string) {
  return {
    id: `file-${path}`,
    path,
    blobSha: `blob-${path}`,
    sizeBytes: 10,
    language: 'Java',
    readable: true,
  };
}

function source(path: string) {
  return {
    repositoryId: 'repository-1',
    commitSha: 'revision-1',
    path,
    blobSha: `blob-${path}`,
    sizeBytes: 10,
    language: 'Java',
    readable: true,
    content: `class ${path.slice(4, 5)} {}`,
    symbols: [],
  };
}

function documentNode(id: string, title: string) {
  return {
    id,
    workspaceId: 'workspace-1',
    parentId: null,
    title,
    depth: 0,
    reviewStatus: 'DRAFT',
    documentType: 'BACKEND',
    children: [],
  };
}

function deferred<T>() {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>((resolver) => { resolve = resolver; });
  return { promise, resolve };
}
