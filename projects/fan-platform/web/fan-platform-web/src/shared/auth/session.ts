import 'server-only';
import { headers } from 'next/headers';
import { getToken } from 'next-auth/jwt';
import { auth } from '@/shared/auth/auth';
import { selectAccessToken } from '@/shared/auth/auth-callbacks';
import { hasAuthenticatedUser } from '@/shared/auth/session-shape';

/**
 * Server-only access to the authenticated session + bearer token. NEVER import
 * this module from a client component — `server-only` will throw at build.
 *
 * The bearer token is read from the JWT session cookie and forwarded only to
 * `gatewayFetch()` calls inside Server Components / Server Actions / route
 * handlers. The browser bundle never sees it.
 *
 * <p><b>Why the token is read from the JWT and not from `auth()`:</b> the
 * `session` callback deliberately strips the access/refresh/id tokens off the
 * public session object (F2 — enforced by a unit test asserting
 * `session.accessToken` is undefined). `auth()` returns that already-stripped
 * session, so re-reading `session.accessToken` always yielded `null` and every
 * Server-Component data fetch went out unauthenticated → gateway 401 → the
 * "failed to load" error states (TASK-FAN-FE-008). We therefore decode the
 * encrypted session cookie directly with `getToken()`, which keeps the token
 * server-side and leaves the F2 invariant intact.
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
}

const EMPTY: FanSession = {
  accessToken: null,
  accountId: null,
  tenantId: null,
  roles: [],
  email: null,
  displayName: null,
};

/**
 * Decode the Auth.js session cookie server-side and return the stored bearer.
 * `cookieName`/`salt` default to `authjs.session-token` (or the `__Secure-`
 * prefixed name when the deployment URL is https), matching what Auth.js wrote.
 * Returns null when there is no cookie, no secret, or a failed silent refresh
 * flagged the session (`RefreshAccessTokenError`) — the caller then behaves as
 * unauthenticated rather than sending a known-stale token.
 */
async function readAccessTokenFromJwt(): Promise<string | null> {
  const secret = process.env.NEXTAUTH_SECRET;
  if (!secret) return null;
  try {
    const jwt = await getToken({
      req: { headers: await headers() },
      secret,
      secureCookie: (process.env.NEXTAUTH_URL ?? '').startsWith('https://'),
    });
    return selectAccessToken(jwt);
  } catch {
    return null;
  }
}

export async function getFanSession(): Promise<FanSession> {
  const session = await auth();
  // 🔵 `!session` 이 아니라 `hasAuthenticatedUser` 로 판정한다 — 위 `isAuthenticated` 와
  //    **같은 술어**여야 한다. 두 함수가 갈리면 「헤더는 익명이라는데 페이지는 세션이
  //    있다고 한다」 같은 반쪽 상태가 생긴다. (설정 오류 본문이 왔을 때 예전 코드도
  //    결국 전부 null 인 FanSession 을 만들었으므로 동작은 같고, 이유가 명시적으로 바뀐다.)
  // (`!session` 을 앞에 두는 것은 TS 의 널 좁히기를 위해서다 — 술어는 `unknown` 을 받으므로
  //  타입 가드가 아니다. 두 조건의 논리적 합집합은 `hasAuthenticatedUser` 하나와 같다.)
  if (!session || !hasAuthenticatedUser(session)) return EMPTY;
  const accessToken = await readAccessTokenFromJwt();
  return {
    accessToken,
    accountId: session.accountId ?? null,
    tenantId: session.tenantId ?? null,
    roles: session.roles ?? [],
    email: session.user?.email ?? null,
    displayName: session.user?.name ?? null,
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
 * 그 요청은 401 로 조용히 실패하므로 화면상 아무 일도 안 일어난 것처럼 보이고, 그 사이
 * «익명 방문은 백엔드를 안 부른다» 는 성질만 사라진다.
 *
 * 🔵 `throw` 도 익명으로 떨어뜨린다. 판정 불가는 «로그인함» 이 아니다.
 */
export async function isAuthenticated(): Promise<boolean> {
  try {
    return hasAuthenticatedUser(await auth());
  } catch {
    return false;
  }
}
