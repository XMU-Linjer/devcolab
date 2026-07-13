import { createRouter, createWebHistory } from 'vue-router';

import { useAuthStore } from '@/stores/auth';

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      redirect: '/workspaces',
    },
    {
      path: '/workspaces',
      name: 'workspaces',
      meta: {
        requiresAuth: true,
      },
      component: () => import('@/views/HomeView.vue'),
    },
    {
      path: '/workspaces/:workspaceId',
      name: 'workspace-detail',
      meta: {
        requiresAuth: true,
      },
      component: () => import('@/views/WorkspaceDetailView.vue'),
    },
    {
      path: '/login',
      name: 'login',
      meta: {
        guestOnly: true,
      },
      component: () => import('@/views/LoginView.vue'),
    },
    {
      path: '/register',
      name: 'register',
      meta: {
        guestOnly: true,
      },
      component: () => import('@/views/RegisterView.vue'),
    },
  ],
});

router.beforeEach(async (to) => {
  const authStore = useAuthStore();

  if (authStore.accessToken && !authStore.initialized) {
    await authStore.loadCurrentUser();
  }

  if (to.meta.requiresAuth && !authStore.isAuthenticated) {
    return {
      name: 'login',
      query: {
        redirect: to.fullPath,
      },
    };
  }

  if (to.meta.guestOnly && authStore.isAuthenticated) {
    return { name: 'workspaces' };
  }

  return true;
});

export default router;
