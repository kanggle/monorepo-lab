import { ApiClient } from '@repo/api-client';
import { getAccessToken, clearAccessToken } from '@/shared/auth/token-bridge';

/**
 * web-store axios client. Token injection is wired through the NextAuth
 * session bridge (`shared/auth/token-bridge.ts`) which is updated by
 * `AuthProvider` whenever `useSession()` resolves.
 *
 * Refresh path:
 *   - GAP issues short-lived access tokens with refresh tokens. NextAuth's
 *     JWT cookie holds the refresh token but does NOT auto-refresh — when
 *     the access token expires, the next API call returns 401, the
 *     interceptor calls `onAuthError`, and we redirect to `/login` so
 *     NextAuth re-runs the OIDC flow (which re-issues both tokens).
 *   - We deliberately do NOT call the legacy `/api/auth/refresh` endpoint;
 *     `getRefreshToken: () => null` short-circuits the interceptor's refresh
 *     attempt, so 401 → onAuthError → /login.
 */

const isServer = typeof window === 'undefined';
const baseURL = isServer
  ? process.env.API_URL_INTERNAL ?? process.env.NEXT_PUBLIC_API_URL ?? 'http://localhost:8080'
  : process.env.NEXT_PUBLIC_API_URL ?? 'http://localhost:8080';

export const apiClient = new ApiClient({
  baseURL,
  // Read the GAP-issued access token from the NextAuth session bridge.
  // Returns null on the server (the bridge is client-only), which is correct:
  // server-side fetches should use `getWebStoreSession()` directly.
  getAccessToken: () => getAccessToken(),
  // No legacy refresh path with GAP — see file header.
  getRefreshToken: () => null,
  onAuthError: () => {
    if (typeof window === 'undefined') return;
    clearAccessToken();
    try {
      localStorage.removeItem('cart');
    } catch {
      // localStorage 접근 실패(프라이빗 모드 등) 시 무시
    }
    // Trigger NextAuth re-auth via the GAP provider. We don't await — this
    // is fire-and-forget; the page will navigate.
    window.location.href = '/api/auth/signin/gap';
  },
});
