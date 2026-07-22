import { createMemoryHistory, createRouter } from 'vue-router';
import { describe, expect, it } from 'vitest';

import { appRoutes } from './index';

function createTestRouter() {
  return createRouter({
    history: createMemoryHistory(),
    routes: appRoutes,
  });
}

function waitForNavigation(router: ReturnType<typeof createTestRouter>) {
  return new Promise<void>((resolve) => {
    const remove = router.afterEach(() => {
      remove();
      resolve();
    });
  });
}

describe('workspace routes', () => {
  it('redirects the workspace root to the linked workbench and preserves query context', async () => {
    const router = createTestRouter();
    await router.push('/workspaces/w1?repositoryId=r1&documentId=d1');
    expect(router.currentRoute.value.name).toBe('workspace-code');
    expect(router.currentRoute.value.fullPath).toBe('/workspaces/w1/code?repositoryId=r1&documentId=d1');
    expect(router.currentRoute.value.matched.some(
      record => record.meta.sidebarVariant === 'LINKED_WORKBENCH',
    )).toBe(true);
  });

  it('uses one history entry for the redirect and does not loop through the old workspace view', async () => {
    const router = createTestRouter();
    await router.push('/workspaces');
    await router.push('/workspaces/w1');
    expect(router.currentRoute.value.fullPath).toBe('/workspaces/w1/code');

    const navigated = waitForNavigation(router);
    router.back();
    await navigated;
    expect(router.currentRoute.value.fullPath).toBe('/workspaces');
  });

  it('keeps non-workspace routes on the default shell contract', async () => {
    const router = createTestRouter();
    await router.push('/workspaces');
    expect(router.currentRoute.value.matched.some(
      record => record.meta.sidebarVariant === 'LINKED_WORKBENCH',
    )).toBe(false);
  });

  it('resolves the direct code URL without another redirect', async () => {
    const router = createTestRouter();
    let completedNavigations = 0;
    router.afterEach(() => { completedNavigations += 1; });
    await router.push('/workspaces/w1/code');
    expect(router.currentRoute.value.name).toBe('workspace-code');
    expect(completedNavigations).toBe(1);
  });
});
