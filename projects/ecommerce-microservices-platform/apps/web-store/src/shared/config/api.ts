import { ApiClient } from '@repo/api-client';

/**
 * web-store axios client.
 *
 * Phase 4.5 F2 (token confidentiality) — the client NEVER holds the OIDC
 * access token:
 *
 *   - Client context (`window` defined): `baseURL` is the SAME-ORIGIN BFF proxy
 *     (`/api/bff`). The proxy route handler reads the access token from the
 *     encrypted server-side NextAuth JWT and attaches `Authorization: Bearer`
 *     SERVER-SIDE before forwarding to the backend gateway. `getAccessToken`
 *     returns null on the client — no token ever reaches browser JS.
 *
 *   - Server context (RSC / Server Actions): `baseURL` is the backend gateway
 *     (internal URL). Those callers attach the bearer via the server-only
 *     `getWebStoreSession()` helper for direct fetches; the shared axios client
 *     carries no token on the server either (the bridge is gone).
 *
 * Phase 4.5 F3 (silent refresh) — refresh is performed in the NextAuth `jwt`
 * callback (server-side, rotation-aware). The BFF proxy reads the freshest
 * token per request, so a still-valid refresh transparently re-issues. When the
 * backend returns 401 (token rejected / refresh already failed), the BFF
 * responds `401 X-Reauth: 1`; the interceptor below has no client refresh token
 * (`getRefreshToken: () => null`) so it short-circuits to `onAuthError`, which
 * redirects to a full re-auth (F1) preserving the return-to.
 */

const isServer = typeof window === 'undefined';

const SAME_ORIGIN_BFF = '/api/bff';

const baseURL = isServer
  ? process.env.API_URL_INTERNAL ?? process.env.NEXT_PUBLIC_API_URL ?? 'http://localhost:8080'
  : SAME_ORIGIN_BFF;

export const apiClient = new ApiClient({
  baseURL,
  // Client: tokens are server-only (F2) — the BFF proxy attaches the bearer.
  // Server: direct fetches use `getWebStoreSession()`; the shared client adds
  // no token here.
  getAccessToken: () => null,
  // No client-side refresh token (F2/F3): refresh happens server-side in the
  // NextAuth jwt callback. A 401 short-circuits straight to onAuthError.
  getRefreshToken: () => null,
  onAuthError: () => {
    if (typeof window === 'undefined') return;
    try {
      localStorage.removeItem('cart');
    } catch {
      // localStorage 접근 실패(프라이빗 모드 등) 시 무시
    }
    // Full re-auth via the IAM provider (F1), preserving the intended
    // destination as `?from=` so login bounces back (F6).
    const from = encodeURIComponent(
      window.location.pathname + window.location.search,
    );
    window.location.href = `/login?from=${from}`;
  },
});
