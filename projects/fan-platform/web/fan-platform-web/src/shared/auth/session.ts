import 'server-only';
import { cookies } from 'next/headers';
import {
  hasFreshAccessToken,
  publicSessionFromToken,
  selectAccessToken,
} from '@/shared/auth/auth-callbacks';
import { hasAuthenticatedUser } from '@/shared/auth/session-shape';
import { decodeSessionCookieHeader } from '@/shared/auth/session-token';

/**
 * Server-only access to the authenticated session + bearer token. NEVER import
 * this module from a client component — `server-only` will throw at build.
 *
 * The bearer token is read from the JWT session cookie and forwarded only to
 * `gatewayFetch()` calls inside Server Components / Server Actions / route
 * handlers. The browser bundle never sees it.
 *
 * <p><b>Why the token is read from the JWT:</b> the `session` callback
 * deliberately strips the access/refresh/id tokens off the public session
 * object (F2 — enforced by a unit test asserting `session.accessToken` is
 * undefined), so the decoded cookie is the only place the bearer lives
 * (TASK-FAN-FE-008).
 *
 * ─────────────────────────────────────────────────────────────────────────
 * 🔴 TASK-FAN-FE-027 — decode-only. Neither function here calls `auth()`.
 * ─────────────────────────────────────────────────────────────────────────
 * Both used to start with the request-less `auth()`. That form runs the `jwt`
 * callback — the silent refresh, which ROTATES the refresh token at IAM — and
 * then throws the rotated Set-Cookie away (a Server Component cannot set
 * cookies, and `next-auth/lib/index.js` does not try). `isAuthenticated()` is
 * called by the header on EVERY page (public ones included) and by the demo
 * heartbeat every 60s, `getFanSession()` by most pages: once the access token
 * expired, each of them spent the browser's refresh token and dropped the
 * result, so the next one replayed it — IAM grace refusal within 30s (logged
 * out), reuse → family revoke + security event after. And `getFanSession()`
 * read the bearer from the cookie anyway, i.e. the OLD access token, so even
 * the "successful" refresh never reached the gateway call.
 *
 * Now: one decode, judged with the `session` callback's rules
 * (`publicSessionFromToken`) and the one predicate (`hasAuthenticatedUser`).
 * The refresh runs only in `GET /api/auth/session` (driven by `SessionKeeper`),
 * where its cookie is written back.
 */

export interface FanSession {
  accessToken: string | null;
  accountId: string | null;
  tenantId: string | null;
  roles: string[];
  // Signed-in fan's email / display name (from the OIDC `email`/`profile` scopes).
  // Non-sensitive identity, safe to forward to a client component that opens the
  // PG window (KG이니시스 V2 requires the buyer email — see portone-checkout.ts).
  email: string | null;
  displayName: string | null;
  /**
   * TASK-FAN-FE-027 — the cookie's access token is expired / inside the refresh
   * margin, so this render's gateway calls used a stale bearer. The header hands
   * it to `SessionKeeper`, which refreshes and re-renders. A boolean only.
   */
  accessTokenStale: boolean;
}

const EMPTY: FanSession = {
  accessToken: null,
  accountId: null,
  tenantId: null,
  roles: [],
  email: null,
  displayName: null,
  accessTokenStale: false,
};

type DecodedToken = Record<string, unknown>;

/**
 * Decode the Auth.js session cookie of the current request. Returns null when
 * there is no cookie or it does not decode; a throw (e.g. `NEXTAUTH_SECRET`
 * absent → MissingSecret) is also null — "cannot judge" is anonymous, never
 * "signed in".
 */
async function readSessionToken(): Promise<DecodedToken | null> {
  try {
    const jar = await cookies();
    const cookieHeader = jar
      .getAll()
      .map((c) => `${c.name}=${c.value}`)
      .join('; ');
    return await decodeSessionCookieHeader<DecodedToken>(cookieHeader);
  } catch {
    return null;
  }
}

export async function getFanSession(): Promise<FanSession> {
  const token = await readSessionToken();
  if (!token) return EMPTY;
  const session = publicSessionFromToken(token) as {
    user?: { email?: string | null; name?: string | null };
    accountId?: string | null;
    tenantId?: string | null;
    roles?: string[];
  };
  // 🔵 `hasAuthenticatedUser` 로 판정한다 — 아래 `isAuthenticated` · `middleware.ts` 와
  //    **같은 술어**여야 한다. 두 함수가 갈리면 「헤더는 익명이라는데 페이지는 세션이
  //    있다고 한다」 같은 반쪽 상태가 생긴다.
  if (!hasAuthenticatedUser(session)) return EMPTY;
  return {
    accessToken: selectAccessToken(token),
    accountId: session.accountId ?? null,
    tenantId: session.tenantId ?? null,
    roles: session.roles ?? [],
    email: session.user?.email ?? null,
    displayName: session.user?.name ?? null,
    accessTokenStale: !hasFreshAccessToken(token),
  };
}

/**
 * 로그인한 방문자인가.
 *
 * 🔴🔴 예전 구현은 `Boolean(await auth())` 였고, 그것은 **틀린 술어다**. auth.js 가
 * 설정 오류로 500 을 내면 `auth()` 는 그 JSON 본문(`{ message: "There was a problem…" }`)
 * 을 **그대로** 돌려주고, `Boolean` 은 그것을 true 로 읽는다. `middleware.ts` 는 2026-08-28
 * 에 이미 이 함정을 고쳤지만(§ TASK-FAN-FE-019) 같은 질문의 **두 번째 사본**인 이 함수는
 * 안 고쳐졌다 — 한 사실이 두 곳에 있으면 한쪽만 고쳐진다는 그 모양 그대로다.
 *
 * 🔴 공개 브라우징이 생기면서 그 오답의 대가가 커졌다: `Header` 가 이 값으로 알림 조회
 * 여부를 정하므로, true 를 잘못 받으면 **익명 방문자가 게이트웨이로 요청을 보낸다.**
 *
 * 🔵 TASK-FAN-FE-027 — 이제 `auth()` 를 아예 부르지 않는다(위 모듈 주석). 판정은 복호한
 *    쿠키 + `session` 콜백 규칙 + `hasAuthenticatedUser` 하나. 복호 실패·throw 는 익명이다
 *    — 판정 불가는 «로그인함» 이 아니다.
 */
export async function isAuthenticated(): Promise<boolean> {
  const token = await readSessionToken();
  return token !== null && hasAuthenticatedUser(publicSessionFromToken(token));
}
