import { csrfHeader, http } from './http';

export interface AuthUser {
  userId: string;
  username: string;
  displayName?: string;
}

export interface AuthResponse {
  userId: string;
  username: string;
  displayName: string;
  accessToken: string;
  tokenType: string;
  expiresInSeconds: number;
}

export interface LoginPayload {
  username: string;
  password: string;
}

export interface RegisterPayload {
  username: string;
  displayName: string;
  password: string;
}

export interface ApiErrorResponse {
  timestamp: string;
  status: number;
  code: string;
  message: string;
  path: string;
}

export async function login(payload: LoginPayload): Promise<AuthResponse> {
  const { data } = await http.post<AuthResponse>('/auth/login', payload);
  return data;
}

export async function register(
  payload: RegisterPayload,
): Promise<AuthResponse> {
  const { data } = await http.post<AuthResponse>('/auth/register', payload);
  return data;
}

export async function getCurrentUser(): Promise<AuthUser> {
  const { data } = await http.get<AuthUser>('/auth/me');
  return data;
}

export async function logout(): Promise<void> {
  await http.post('/auth/logout', undefined, {
    headers: csrfHeader(),
  });
}
