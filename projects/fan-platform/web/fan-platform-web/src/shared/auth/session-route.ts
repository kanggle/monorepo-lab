import { REFRESH_RACE_LOST_CLAIM, hasFreshAccessToken } from './auth-callbacks';
import {
  SECURE_SESSION_COOKIE,
  decodeSessionCookieHeader,
  sessionCookieNamesIn,
} from './session-token';

/**
 * TASK-FAN-FE-027 — `GET /api/auth/session` that survives a concurrent-refresh
 * race. 🔵 Per-project copy of ecommerce web-store's `session-route.ts`
 * (TASK-FE-106): same mechanism, same constants, same tests. Not promoted to
 * repo-root `libs/` (must stay project-agnostic) — if one copy changes, grep
 * the sibling.
 *
 * 🔴 The race: several requests carry the SAME expired session cookie (several
 * tabs' `SessionKeeper` reads, several serverless instances). Each runs the
 * `jwt` callback, each sends the SAME refresh token; IAM rotates for the first
 * and answers the rest `400 invalid_grant` within its 30s grace window,
 * revoking nothing (iam TASK-BE-606/608). Unwrapped, the loser writes its
 * `RefreshAccessTokenError` session cookie back to the browser — and when it
 * lands after the winner's, it overwrites the winner's freshly rotated cookie:
 * logged out, although the winner had succeeded.
 *
 *   1. The loser's `jwt` callback marks the token `refreshRaceLost` (and
 *      `error`, so the cookie is terminal if it ever escapes — never a replay).
 *   2. This wrapper sees the marker on the session body and DISCARDS that whole
 *      response — Set-Cookie included, so the winner's cookie is never
 *      overwritten. It waits {@link REFRESH_RACE_GRACE_MS}, then answers `307`
 *      to `?refresh_retry=1`. `fetch()` (`SessionKeeper`) follows it
 *      transparently, and the follow-up is a genuinely NEW request carrying the
 *      browser's cookies as of then — the winner's, if they have landed.
 *   3. The retry hop NEVER refreshes. If the cookie now holds a fresh access
 *      token ({@link hasFreshAccessToken} — the same judge the `jwt` callback
 *      uses) the normal handler answers with no refresh → logged in. If it
 *      still holds the expired one, the race is unresolved (or it was never a
 *      race — a dead refresh token gets the same `400`): the session cookie is
 *      cleared and an anonymous session returned → logged out, without sending
 *      the old refresh token a second time (after 30s that would be reuse →
 *      family revoke + security event).
 */

/**
 * How long the loser waits before its single retry hop. Same value as
 * web-store / console-web: long enough for the winner's response (one IAM
 * round trip) to land its Set-Cookie; paid only on a failure path; comfortably
 * inside IAM's 30s grace window.
 */
export const REFRESH_RACE_GRACE_MS = 2000;

/** Query flag of the one retry hop (loop bound: the hop never refreshes). */
export const SESSION_RETRY_PARAM = 'refresh_retry';

type DecodedToken = Record<string, unknown>;

export interface SessionRouteDeps<R extends Request> {
  /** The Auth.js route handler (`handlers.GET`). */
  handler: (req: R) => Promise<Response>;
  /** Decode-only session read (never refreshes). Injected for tests. */
  decode?: (cookieHeader: string) => Promise<DecodedToken | null>;
  /** Injected for tests. */
  sleep?: (ms: number) => Promise<void>;
}

export function isSessionAction(pathname: string): boolean {
  return pathname.replace(/\/+$/, '').endsWith('/api/auth/session');
}

const defaultSleep = (ms: number) => new Promise<void>((resolve) => setTimeout(resolve, ms));

async function lostRefreshRace(res: Response): Promise<boolean> {
  if (!res.ok) return false;
  if (!(res.headers.get('content-type') ?? '').includes('application/json')) return false;
  const body = (await res
    .clone()
    .json()
    .catch(() => null)) as Record<string, unknown> | null;
  return body !== null && typeof body === 'object' && body[REFRESH_RACE_LOST_CLAIM] === true;
}

/** `true` when the retry hop would have to refresh again — i.e. no winner's cookie arrived. */
function raceUnresolved(token: DecodedToken | null): boolean {
  return (
    token !== null &&
    !token.error &&
    typeof token.refreshToken === 'string' &&
    token.refreshToken.length > 0 &&
    !hasFreshAccessToken(token)
  );
}

function retryHop(url: URL): Response {
  // Relative Location: resolved by the browser against the public origin, so
  // it is correct behind Vercel / Traefik without rebuilding the host.
  return new Response(null, {
    status: 307,
    headers: {
      Location: `${url.pathname}?${SESSION_RETRY_PARAM}=1`,
      'Cache-Control': 'no-store',
    },
  });
}

function endSession(cookieHeader: string): Response {
  const headers = new Headers({
    'Content-Type': 'application/json',
    'Cache-Control': 'no-store',
  });
  for (const name of sessionCookieNamesIn(cookieHeader)) {
    const secure = name.startsWith(SECURE_SESSION_COOKIE) ? '; Secure' : '';
    headers.append('Set-Cookie', `${name}=; Path=/; Max-Age=0; HttpOnly; SameSite=Lax${secure}`);
  }
  // Auth.js answers a missing session with JSON `null`.
  return new Response('null', { status: 200, headers });
}

export function withRefreshRaceRetry<R extends Request>(
  deps: SessionRouteDeps<R>,
): (req: R) => Promise<Response> {
  const { handler } = deps;
  const decode = deps.decode ?? ((h: string) => decodeSessionCookieHeader<DecodedToken>(h));
  const sleep = deps.sleep ?? defaultSleep;

  return async (req: R): Promise<Response> => {
    const url = new URL(req.url);
    if (!isSessionAction(url.pathname)) return handler(req);

    if (url.searchParams.get(SESSION_RETRY_PARAM) === '1') {
      const cookieHeader = req.headers.get('cookie') ?? '';
      if (raceUnresolved(await decode(cookieHeader))) return endSession(cookieHeader);
      // Winner's cookie landed (fresh → the jwt callback will not refresh), or
      // there is nothing to refresh: the normal answer, cookie and all.
      return handler(req);
    }

    const res = await handler(req);
    if (!(await lostRefreshRace(res))) return res;
    await sleep(REFRESH_RACE_GRACE_MS);
    return retryHop(url);
  };
}
