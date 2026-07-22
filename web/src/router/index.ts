import { createRouter, createWebHistory } from 'vue-router';
import axios from 'axios';

import { csrfHeader, getAccessToken, setAccessToken } from '@/api/http';
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
      path: '/workspaces/:workspaceId/code',
      name: 'code-workbench',
      meta: {
        requiresAuth: true,
      },
      component: () => import('@/views/CodeWorkbenchView.vue'),
    },
    {
      path: '/w/:workspaceId/docs/:documentId',
      name: 'document-workbench',
      meta: {
        requiresAuth: true,
      },
      component: () => import('@/views/DocumentWorkbenchView.vue'),
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

  if (!getAccessToken() && !authStore.initialized) {
    try {
      const { data } = await axios.post<{ accessToken: string }>(
        '/api/v1/auth/refresh',
        undefined,
        {
          withCredentials: true,
          headers: csrfHeader(),
        },
      );
      setAccessToken(data.accessToken);
    } catch {
      setAccessToken(null);
    }
  }

  if (getAccessToken() && !authStore.initialized) {
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
