import { NextResponse } from 'next/server';
import type { NextRequest } from 'next/server';
import { sessionCallback } from '@/shared/auth/auth-callbacks';
import { decodeSessionCookieHeader } from '@/shared/auth/session-token';

/**
 * Route guard. Public storefront paths (`/`, `/products/...`, `/login`,
 * `/api/auth/...`) are never gated — these match the existing customer
 * browsing experience pre-cutover. Protected paths (cart, checkout, my, etc.)
 * require an authenticated CONSUMER session; an unauthenticated visit
 * lands on `/login?from=<original>`.
 *
 * Cross-app guard: if a non-CONSUMER somehow holds a session (e.g. an
 * OPERATOR after `/api/auth/callback/iam`), the session callback in
 * `auth.ts` returns a session with `accountId=null`, and the `auth` check
 * here treats them as anonymous → redirected to `/login?error=...`.
 */
export async function middleware(request: NextRequest) {
  const { pathname, search } = request.nextUrl;

  // Public paths — never gate.
  if (
    pathname === '/' ||
    pathname.startsWith('/login') ||
    pathname.startsWith('/signup') ||
    pathname.startsWith('/products') ||
    // TASK-FE-102 — the cart is usable logged out (guest cart). Exactly `/cart`:
    // `/checkout*` stays gated, so ordering still requires login.
    pathname === '/cart' ||
    pathname.startsWith('/api/auth') ||
    // The same-origin BFF proxy (`/api/bff/[...path]`) enforces auth itself: it
    // reads the server-side session token and attaches the bearer, returning
    // 401 `X-Reauth: 1` (→ client-side full re-auth, F1) when the session is
    // absent/expired. It must NOT be middleware-bounced to `/login` — a 307 to
    // the login HTML breaks the client axios XHR (it follows the redirect, gets
    // HTML, fails to parse as JSON → react-query `isError` → "불러오는데 실패").
    // This also keeps public reads (product reviews/summary) loadable for
    // anonymous visitors, since the backend allows them. Mirrors the documented
    // intent of `authConfig.callbacks.authorized` in `shared/auth/auth.ts`.
    pathname.startsWith('/api/bff') ||
    // 🔴🔴 TASK-MONO-654 — 데모 백엔드 판정 탐침. **익명이 부를 수 있어야 한다.**
    // 이 배너의 청중이 정확히 «로그인하지 않은 방문자»(면접관)이므로, 여기 없으면
    // 그 방문자의 탐침이 `/login` 으로 307 되고 배너는 **영영 안 뜬다** — 그러면 이
    // 티켓이 고친 결함이 «배너가 낡았다» 에서 «배너가 아예 없다» 로 바뀔 뿐이다.
    // 🔵 새는 것이 없다: 이 라우트가 돌려주는 것은 `{state}` 하나이고, 그 사실은
    //    론처 페이지가 이미 방문자에게 **대놓고 보여 준다**(`/status` 는 공개다).
    pathname === '/api/demo/backend-state' ||
    pathname.startsWith('/_next') ||
    // The Web Push service worker script (TASK-FE-083) must be publicly fetchable:
    // `navigator.serviceWorker.register('/sw.js')` and the browser's periodic SW
    // update fetches run without app auth context, so a 307 to /login would break
    // registration. Also excluded from the matcher below so middleware never runs on it.
    pathname === '/sw.js' ||
    pathname === '/favicon.ico'
  ) {
    return NextResponse.next();
  }

  // 🔴 TASK-FE-106 — decode-only, NEVER `auth()`. `auth()` called without a
  // request (the RSC form) runs the `jwt` callback — i.e. performs the silent
  // refresh, rotating the refresh token at IAM — and then DISCARDS the
  // resulting Set-Cookie (next-auth `lib/index.js`: `getSession(...).then(r =>
  // r.json())`). Every protected navigation / prefetch after the access token
  // expired therefore burned the browser's refresh token: the rotated pair was
  // thrown away, the browser kept the old one, and the next session read sent
  // it again → IAM grace-window refusal (or, after 30s, reuse → family revoke).
  // A refresh may only run where its result is written back: `/api/auth/session`
  // (`session-route.ts`). Here we only judge the session we were handed, with
  // the same rules the session callback applies (role, refresh-failure flag).
  // An expired-but-refreshable session passes; the client's session read
  // refreshes it, and the BFF rejects a stale bearer on its own.
  const token = await decodeSessionCookieHeader<Record<string, unknown>>(
    request.headers.get('cookie') ?? '',
  );
  const session = token ? sessionCallback({ session: {}, token }) : null;
  if (!session || !session.accountId) {
    const loginUrl = request.nextUrl.clone();
    loginUrl.pathname = '/login';
    loginUrl.search = `?from=${encodeURIComponent(pathname + search)}`;
    return NextResponse.redirect(loginUrl);
  }

  return NextResponse.next();
}

export const config = {
  matcher: [
    /*
     * Match every path except:
     *  - /api/auth (next-auth handler)
     *  - /_next/static, /_next/image
     *  - /favicon.ico, /robots.txt, /sitemap.xml
     *  - /sw.js (Web Push service worker — must be public, TASK-FE-083-fix-001)
     *  - All public asset extensions handled by the negative lookahead
     */
    '/((?!api/auth|_next/static|_next/image|favicon.ico|robots.txt|sitemap.xml|sw.js).*)',
  ],
};
