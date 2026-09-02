import { ApiClient } from '@repo/api-client';
import { resolveDemoBackend } from '@/shared/config/demo-backend';

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

// DEMO-RESOLVER-CONSUMER: web-store   (ADR-MONO-068 § D6 = B2 — 구현은 @demo/backend-resolver 하나뿐이다)
//
// 🔴 서버 쪽 baseURL 은 **생성 시점에 굳으면 안 된다** (TASK-MONO-580 / ADR-MONO-067 D2).
//    데모 백엔드 주소는 부팅마다 바뀌는데 이 모듈은 프로세스당 한 번만 평가된다 ⇒ 굳은
//    값은 다음 부팅에 곧바로 썩는다. 그래서 **요청마다** `resolveBaseURL` 이 다시 정한다.
//
// 🔵 아래 `baseURL` 은 이제 **폴백**이다. 해석기가 `null` 을 내면(로컬·CI·데모 꺼짐·조회
//    실패) 이 값이 그대로 쓰이므로 로컬 개발과 CI 의 동작은 예전과 같다.
//
// 🔵 **클라이언트 분기는 손대지 않았다** — 브라우저는 상대경로 `/api/bff` 만 알아야 하고
//    (ADR-MONO-067 D1), 그 프록시 라우트가 서버에서 같은 해석기를 쓴다.
const baseURL = isServer
  ? process.env.API_URL_INTERNAL ?? process.env.NEXT_PUBLIC_API_URL ?? 'http://localhost:8080'
  : SAME_ORIGIN_BFF;

export const apiClient = new ApiClient({
  baseURL,
  // 🔴 서버에서만 건다. 브라우저에서 이 훅이 돌면 백엔드 오리진이 클라이언트 코드의
  //    관심사가 되어 D1 이 무너진다.
  resolveBaseURL: isServer
    ? async () => {
        const demo = await resolveDemoBackend();
        return demo?.baseUrl ?? null;
      }
    : undefined,
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
