import { defineStore } from 'pinia';

import {
  getCurrentUser,
  login,
  logout,
  register,
  type AuthUser,
  type LoginPayload,
  type RegisterPayload,
} from '@/api/auth';
import { getAccessToken, setAccessToken } from '@/api/http';

interface AuthState {
  currentUser: AuthUser | null;
  initialized: boolean;
}

export const useAuthStore = defineStore('auth', {
  state: (): AuthState => ({
    currentUser: null,
    initialized: false,
  }),
  getters: {
    isAuthenticated: () => Boolean(getAccessToken()),
  },
  actions: {
    async login(payload: LoginPayload) {
      const response = await login(payload);
      setAccessToken(response.accessToken);
      this.currentUser = {
        userId: response.userId,
        username: response.username,
        displayName: response.displayName,
      };
      return response;
    },
    async register(payload: RegisterPayload) {
      const response = await register(payload);
      setAccessToken(response.accessToken);
      this.currentUser = {
        userId: response.userId,
        username: response.username,
        displayName: response.displayName,
      };
      return response;
    },
    async loadCurrentUser() {
      if (!getAccessToken()) {
        this.initialized = true;
        return null;
      }

      try {
        this.currentUser = await getCurrentUser();
        return this.currentUser;
      } catch {
        this.clear();
        return null;
      } finally {
        this.initialized = true;
      }
    },
    async logout() {
      try {
        await logout();
      } finally {
        this.clear();
      }
    },
    clear() {
      setAccessToken(null);
      this.currentUser = null;
    },
  },
});