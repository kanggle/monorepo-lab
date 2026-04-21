import { createApiClient } from '@repo/api-client';

const isServer = typeof window === 'undefined';
const baseURL = isServer
  ? process.env.API_URL_INTERNAL ?? process.env.NEXT_PUBLIC_API_URL ?? 'http://localhost:8080'
  : process.env.NEXT_PUBLIC_API_URL ?? 'http://localhost:8080';

export const apiClient = createApiClient({
  baseURL,
  loginPath: '/login',
});
