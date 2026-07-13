import axios, { AxiosError, type InternalAxiosRequestConfig } from 'axios';

const ACCESS_TOKEN_KEY = 'devcollab.accessToken';
const CSRF_COOKIE_NAME = 'dc_csrf';
const CSRF_HEADER_NAME = 'X-CSRF-Token';

interface RetriableRequestConfig extends InternalAxiosRequestConfig {
  _retry?: boolean;
}

export const http = axios.create({
  baseURL: '/api/v1',
  timeout: 10_000,
  withCredentials: true,
});

http.interceptors.request.use((config) => {
  const token = localStorage.getItem(ACCESS_TOKEN_KEY);

  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }

  return config;
});

http.interceptors.response.use(
  (response) => response,
  async (error: AxiosError) => {
    const originalRequest = error.config as RetriableRequestConfig | undefined;

    if (
      error.response?.status !== 401 ||
      !originalRequest ||
      originalRequest._retry ||
      originalRequest.url?.includes('/auth/refresh') ||
      originalRequest.url?.includes('/auth/login') ||
      originalRequest.url?.includes('/auth/register')
    ) {
      return Promise.reject(error);
    }

    const csrfToken = readCookie(CSRF_COOKIE_NAME);
    if (!csrfToken) {
      localStorage.removeItem(ACCESS_TOKEN_KEY);
      return Promise.reject(error);
    }

    originalRequest._retry = true;

    try {
      const { data } = await axios.post<{ accessToken: string }>(
        '/api/v1/auth/refresh',
        undefined,
        {
          withCredentials: true,
          headers: {
            [CSRF_HEADER_NAME]: csrfToken,
          },
        },
      );

      localStorage.setItem(ACCESS_TOKEN_KEY, data.accessToken);
      originalRequest.headers.Authorization = `Bearer ${data.accessToken}`;
      return http(originalRequest);
    } catch (refreshError) {
      localStorage.removeItem(ACCESS_TOKEN_KEY);
      return Promise.reject(refreshError);
    }
  },
);

export function csrfHeader() {
  const token = readCookie(CSRF_COOKIE_NAME);
  return token ? { [CSRF_HEADER_NAME]: token } : {};
}

function readCookie(name: string) {
  return document.cookie
    .split('; ')
    .find((cookie) => cookie.startsWith(`${name}=`))
    ?.split('=')
    .slice(1)
    .join('=');
}
