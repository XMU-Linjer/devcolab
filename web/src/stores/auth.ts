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

const ACCESS_TOKEN_KEY = 'devcollab.accessToken';

interface AuthState {
  accessToken: string | null;
  currentUser: AuthUser | null;
  initialized: boolean;
}

export const useAuthStore = defineStore('auth', {
  state: (): AuthState => ({
    accessToken: localStorage.getItem(ACCESS_TOKEN_KEY),
    currentUser: null,
    initialized: false,
  }),
  getters: {
    isAuthenticated: (state) => Boolean(state.accessToken),
  },
  actions: {
    setAccessToken(token: string) {
      this.accessToken = token;
      localStorage.setItem(ACCESS_TOKEN_KEY, token);
    },
    async login(payload: LoginPayload) {
      const response = await login(payload);
      this.setAccessToken(response.accessToken);
      this.currentUser = {
        userId: response.userId,
        username: response.username,
        displayName: response.displayName,
      };
      return response;
    },
    async register(payload: RegisterPayload) {
      const response = await register(payload);
      this.setAccessToken(response.accessToken);
      this.currentUser = {
        userId: response.userId,
        username: response.username,
        displayName: response.displayName,
      };
      return response;
    },
    async loadCurrentUser() {
      if (!this.accessToken) {
        this.initialized = true;
        return null;
      }

      try {
        this.currentUser = await getCurrentUser();
        this.accessToken = localStorage.getItem(ACCESS_TOKEN_KEY);
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
      this.accessToken = null;
      this.currentUser = null;
      localStorage.removeItem(ACCESS_TOKEN_KEY);
    },
  },
});
