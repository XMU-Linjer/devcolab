import { mount, flushPromises } from '@vue/test-utils';
import { describe, expect, it, vi, beforeEach, afterEach } from 'vitest';
import { createRouter, createMemoryHistory, type Router } from 'vue-router';
import ElementPlus from 'element-plus';

import ReviewWorkbenchView from './ReviewWorkbenchView.vue';
import * as documentChangeApi from '@/api/documentChange';
import * as blockApi from '@/api/block';
import * as gitApi from '@/api/git';
import * as workspaceApi from '@/api/workspace';

// Mock dependencies
vi.mock('@/api/documentChange');
vi.mock('@/api/block');
vi.mock('@/api/git');
vi.mock('@/api/workspace');
vi.mock('@/components/layout/AppSidebar.vue', () => ({ default: { template: '<div>Sidebar</div>' } }));
vi.mock('@/components/linked-workbench/LinkedRepositoryContext.vue', () => ({ default: { template: '<div>Context</div>' } }));
vi.mock('@/components/review/ReviewCodeEvidencePane.vue', () => ({ default: { template: '<div>CodePane</div>', props: ['evidence'] } }));
vi.mock('@/components/review/ReviewDocumentPane.vue', () => ({ default: { template: '<div>DocPane</div>', props: ['blocks'] } }));
vi.mock('@/components/review/ReviewInspector.vue', () => ({ default: { template: '<div>Inspector</div>' } }));

function createDeferred<T = any>() {
  let resolve!: (value: T | PromiseLike<T>) => void;
  let reject!: (reason?: any) => void;
  const promise = new Promise<T>((res, rej) => {
    resolve = res;
    reject = rej;
  });
  return { promise, resolve, reject };
}

describe('ReviewWorkbenchView asynchronous state and race conditions', () => {
  let router: Router;

  beforeEach(async () => {
    vi.resetAllMocks();
    
    // Default successful mocks
    vi.mocked(workspaceApi.getWorkspace).mockResolvedValue({ id: 'w1', name: 'Workspace 1' } as any);
    vi.mocked(gitApi.listGitRepositories).mockResolvedValue([]);
    vi.mocked(gitApi.listGitRepositoryFiles).mockResolvedValue([]);
    vi.mocked(documentChangeApi.listDocumentChanges).mockResolvedValue({ items: [], totalElements: 0, page: 0, size: 20, totalPages: 0 });

    router = createRouter({
      history: createMemoryHistory(),
      routes: [
        {
          path: '/workspaces/:workspaceId/reviews/:status/:requestId?',
          name: 'workspace-review-detail',
          component: ReviewWorkbenchView,
        }
      ]
    });
    
    // Mock localStorage
    vi.spyOn(Storage.prototype, 'getItem').mockReturnValue('false');
  });

  afterEach(() => {
    vi.clearAllMocks();
  });

  const mountView = async (initialPath: string) => {
    router.push(initialPath);
    await router.isReady();
    const wrapper = mount(ReviewWorkbenchView, {
      global: {
        plugins: [router, ElementPlus],
        stubs: {
          AppSidebar: true,
          LinkedRepositoryContext: true,
          ReviewCodeEvidencePane: true,
          ReviewDocumentPane: true,
          ReviewInspector: true,
        },
      },
    });
    await flushPromises();
    return wrapper;
  };

  const mockRequestDetail = (id: string, ops: any[] = []) => ({
    request: { id, status: 'PENDING', summary: 'Test Request' },
    operations: ops,
    requestEvidence: []
  });

  it('1. 详情请求乱序: B arrives before A, page should show B', async () => {
    const deferredA = createDeferred();
    const deferredB = createDeferred();
    
    vi.mocked(documentChangeApi.getDocumentChange).mockImplementation(async (_w, id) => {
      if (id === 'rA') return deferredA.promise;
      if (id === 'rB') return deferredB.promise;
      return null;
    });

    const wrapper = await mountView('/workspaces/w1/reviews/pending/rA');
    
    // Switch to rB before rA resolves
    router.push('/workspaces/w1/reviews/pending/rB');
    await flushPromises();

    // rB resolves first
    deferredB.resolve(mockRequestDetail('rB'));
    await flushPromises();

    // rA resolves later (stale)
    deferredA.resolve(mockRequestDetail('rA'));
    await flushPromises();

    // The current request should be rB
    const text = wrapper.text();
    expect(text).toContain('Test Request'); // From B
    expect((wrapper.vm as any).detail?.request.id).toBe('rB');
  });

  it('2. 详情旧错误响应: A fails after B succeeds, error should not overwrite B', async () => {
    const deferredA = createDeferred();
    const deferredB = createDeferred();
    
    vi.mocked(documentChangeApi.getDocumentChange).mockImplementation(async (_w, id) => {
      if (id === 'rA') return deferredA.promise;
      if (id === 'rB') return deferredB.promise;
      return null;
    });

    const wrapper = await mountView('/workspaces/w1/reviews/pending/rA');
    
    router.push('/workspaces/w1/reviews/pending/rB');
    await flushPromises();

    deferredB.resolve(mockRequestDetail('rB'));
    await flushPromises();

    // rA fails
    deferredA.reject(new Error('Network error'));
    await flushPromises();

    expect((wrapper.vm as any).errorMessage).toBe(''); // Error should be ignored
    expect((wrapper.vm as any).detail?.request.id).toBe('rB');
  });

  it('3. Operation 文档请求乱序: Op2 resolves before Op1, Op2 should win', async () => {
    const ops = [
      { operationId: 'op1', target: { documentId: 'doc1' }, operationType: 'UPDATE_BLOCK' },
      { operationId: 'op2', target: { documentId: 'doc2' }, operationType: 'UPDATE_BLOCK' }
    ];
    vi.mocked(documentChangeApi.getDocumentChange).mockResolvedValue(mockRequestDetail('r1', ops) as any);
    
    const deferredDoc1 = createDeferred();
    const deferredDoc2 = createDeferred();
    
    vi.mocked(blockApi.listBlocks).mockImplementation(async (id) => {
      if (id === 'doc1') return deferredDoc1.promise;
      if (id === 'doc2') return deferredDoc2.promise;
      return [];
    });

    const wrapper = await mountView('/workspaces/w1/reviews/pending/r1?operationId=op1');
    
    // Switch to op2
    router.push({ query: { operationId: 'op2' } });
    await flushPromises();

    // Doc2 resolves first
    deferredDoc2.resolve([{ id: 'block2' }]);
    await flushPromises();

    // Doc1 resolves later
    deferredDoc1.resolve([{ id: 'block1' }]);
    await flushPromises();

    expect((wrapper.vm as any).documentBlocks).toHaveLength(1);
    expect((wrapper.vm as any).documentBlocks[0].id).toBe('block2');
  });

  it('4. workspaceId 切换: w1 stale request resolves after switching to w2', async () => {
    const deferredW1 = createDeferred();
    vi.mocked(documentChangeApi.getDocumentChange).mockImplementation(async (w, _id) => {
      if (w === 'w1') return deferredW1.promise;
      if (w === 'w2') return mockRequestDetail('r2') as any;
      return null;
    });

    const wrapper = await mountView('/workspaces/w1/reviews/pending/r1');
    
    router.push('/workspaces/w2/reviews/pending/r2');
    await flushPromises();

    deferredW1.resolve(mockRequestDetail('r1'));
    await flushPromises();

    // w2 should be active, w1 result ignored
    expect((wrapper.vm as any).workspaceId).toBe('w2');
    expect((wrapper.vm as any).detail?.request.id).toBe('r2');
  });

  it('5. 无 Evidence: Should not call file API and should clear path', async () => {
    const ops = [{ operationId: 'op1', target: { documentId: 'doc1' }, operationType: 'UPDATE_BLOCK' }];
    vi.mocked(documentChangeApi.getDocumentChange).mockResolvedValue(mockRequestDetail('r1', ops) as any);
    
    const wrapper = await mountView('/workspaces/w1/reviews/pending/r1');
    
    expect((wrapper.vm as any).selectedFilePath).toBe('');
    expect((wrapper.vm as any).selectedRepositoryId).toBe('');
    expect(gitApi.getGitRepositorySource).not.toHaveBeenCalled();
    expect(gitApi.listGitRepositoryFiles).toHaveBeenCalledTimes(0);
  });

  it('6. 快速连续切换: Select Op1, Op2, Op3, Op3 should win', async () => {
    const ops = [
      { operationId: 'op1', target: { documentId: 'doc1' }, operationType: 'UPDATE_BLOCK' },
      { operationId: 'op2', target: { documentId: 'doc2' }, operationType: 'UPDATE_BLOCK' },
      { operationId: 'op3', target: { documentId: 'doc3' }, operationType: 'UPDATE_BLOCK' }
    ];
    vi.mocked(documentChangeApi.getDocumentChange).mockResolvedValue(mockRequestDetail('r1', ops) as any);
    
    const d1 = createDeferred();
    const d2 = createDeferred();
    const d3 = createDeferred();
    
    vi.mocked(blockApi.listBlocks).mockImplementation(async (id) => {
      if (id === 'doc1') return d1.promise;
      if (id === 'doc2') return d2.promise;
      if (id === 'doc3') return d3.promise;
      return [];
    });

    const wrapper = await mountView('/workspaces/w1/reviews/pending/r1?operationId=op1');
    
    router.push({ query: { operationId: 'op2' } });
    await flushPromises();
    
    router.push({ query: { operationId: 'op3' } });
    await flushPromises();

    // Resolve out of order
    d2.resolve([{ id: 'block2' }]);
    d1.resolve([{ id: 'block1' }]);
    d3.resolve([{ id: 'block3' }]);
    
    await flushPromises();

    expect((wrapper.vm as any).documentBlocks[0].id).toBe('block3');
  });
});
