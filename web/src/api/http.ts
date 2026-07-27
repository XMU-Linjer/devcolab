import axios, {
  AxiosError,
  type AxiosInstance,
  type InternalAxiosRequestConfig,
} from 'axios';

const CSRF_COOKIE_NAME = 'dc_csrf';
const CSRF_HEADER_NAME = 'X-CSRF-Token';

let memoryToken: string | null = null;

export function setAccessToken(token: string | null) {
  memoryToken = token;
}

export function getAccessToken() {
  return memoryToken;
}

interface RetriableRequestConfig extends InternalAxiosRequestConfig {
  _retry?: boolean;
}

export function createAuthenticatedHttp(baseURL: string): AxiosInstance {
  const client = axios.create({
    baseURL,
    timeout: 10_000,
    withCredentials: true,
  });

  client.interceptors.request.use((config) => {
    const token = getAccessToken();

    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }

    return config;
  });

  client.interceptors.response.use(
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
        setAccessToken(null);
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

        setAccessToken(data.accessToken);
        originalRequest.headers.Authorization = `Bearer ${data.accessToken}`;
        return client(originalRequest);
      } catch (refreshError) {
        setAccessToken(null);
        return Promise.reject(refreshError);
      }
    },
  );

  return client;
}

export const http = createAuthenticatedHttp('/api/v1');

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
