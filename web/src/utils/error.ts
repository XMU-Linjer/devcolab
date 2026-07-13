import { AxiosError } from 'axios';

import type { ApiErrorResponse } from '@/api/auth';

export function readableError(error: unknown, fallback: string): string {
  if (error instanceof AxiosError) {
    const response = error.response?.data as ApiErrorResponse | undefined;
    return response?.message || fallback;
  }

  if (error instanceof Error) {
    return error.message;
  }

  return fallback;
}
