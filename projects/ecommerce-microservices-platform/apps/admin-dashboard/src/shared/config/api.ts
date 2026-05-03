import { ApiClient } from '@repo/api-client';
import { getAccessToken, clearAccessToken } from '@/shared/auth/token-bridge';

/**
 * admin-dashboard axios client. Token injection is wired through the NextAuth
 * session bridge (`shared/auth/token-bridge.ts`) which is updated by
 * `AuthProvider` whenever `useSession()` resolves.
 *
 * On 401 (access token expired or revoked), we redirect to the GAP signin
 * URL so NextAuth re-runs the OIDC flow. We deliberately do not call the
 * legacy `/api/auth/refresh` endpoint with NextAuth.
 */

export const apiClient = new ApiClient({
  baseURL: process.env.NEXT_PUBLIC_API_URL ?? 'http://localhost:8080',
  getAccessToken: () => getAccessToken(),
  getRefreshToken: () => null,
  onAuthError: () => {
    if (typeof window === 'undefined') return;
    clearAccessToken();
    window.location.href = '/api/auth/signin/gap';
  },
});
