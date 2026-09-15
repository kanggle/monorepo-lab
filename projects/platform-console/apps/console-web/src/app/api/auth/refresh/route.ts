import { NextResponse } from 'next/server';
import { cookies } from 'next/headers';
import { getServerEnv, publicOrigin } from '@/shared/config/env';
import {
  ACCESS_COOKIE,
  REFRESH_COOKIE,
  OPERATOR_COOKIE,
  clearFullSession,
} from '@/shared/lib/session';
import {
  refreshSessionCookies,
  REFRESH_RACE_GRACE_MS,
} from '@/shared/lib/session-refresh';
import { RE_LOGIN_PATH } from '@/shared/lib/re-login';
import {
  buildLoginRedirectFor,
  resolveRefreshReturnPath,
  SESSION_REFRESH_PATH,
  REFRESH_RETRY_PARAM,
} from '@/shared/lib/login-redirect';
import { logger, newRequestId } from '@/shared/lib/logger';

export const runtime = 'nodejs';

/**
 * Server-route token refresh (frontend-app.md § Authentication: refresh handled
 * by a server route, not client JS). The token sequence itself — IAM
 * refresh_token grant (rotating), operator re-exchange (§ 2.6 / ADR-MONO-014
 * D2), tenant re-assume (§ 2.7) — lives in ONE place,
 * {@link refreshSessionCookies}; the two methods here differ only in how they
 * answer.
 *
 * ---------------------------------------------------------------------------
 * POST — the browser API client after a `401` (`shared/api/client.ts`)
 * ---------------------------------------------------------------------------
 * JSON. Unchanged contract: any failure drops the whole session (IAM +
 * operator cookies) and answers `401`; an unexpected error answers `502`.
 *
 * ---------------------------------------------------------------------------
 * GET — the `(console)` guard's idle-expiry hop (TASK-MONO-674, § 2.6.1)
 * ---------------------------------------------------------------------------
 * 🔴 Why this exists: the access cookie's `maxAge` is the token's `expires_in`
 * (1800s), so after 30 idle minutes the BROWSER drops it and the layout guard —
 * which reads cookies only — bounced to `/login?redirect=…` with no reason,
 * although a 30-day refresh cookie was sitting right there. A layout cannot set
 * cookies, so the guard redirects here; this handler refreshes, sets the cookies
 * and redirects back to the page that was asked for.
 *
 * Owner decision (AC-1, verbatim label): «ⓐ+ⓑ 갱신+실패시 사유» — refresh
 * silently; when refresh fails, land on `/login?error=session_expired`.
 *
 * Loop bound: at most two hits per guard bounce (first attempt + one
 * `retry=1`), and `retry=1` never refreshes again. Every failure destination is
 * outside the guard.
 */

export async function POST() {
  const requestId = newRequestId();
  const jar = await cookies();
  const outcome = await refreshSessionCookies(jar, { requestId, via: 'api' });

  switch (outcome.kind) {
    case 'ok':
      return NextResponse.json({ ok: true });
    case 'no_refresh_token':
      return NextResponse.json(
        { code: 'TOKEN_INVALID', message: 'refresh token missing' },
        { status: 401 },
      );
    case 'grant_rejected':
      clearFullSession(jar);
      return NextResponse.json(
        { code: 'TOKEN_INVALID', message: 'refresh failed' },
        { status: 401 },
      );
    case 'operator_not_provisioned':
    case 'operator_unavailable':
      // Whole session — no stale operator token, no GAP-token fallback.
      clearFullSession(jar);
      return NextResponse.json(
        { code: 'TOKEN_INVALID', message: 'operator session ended' },
        { status: 401 },
      );
    case 'error':
      return NextResponse.json(
        { code: 'DOWNSTREAM_ERROR', message: 'refresh proxy error' },
        { status: 502 },
      );
  }
}

export async function GET(req: Request): Promise<NextResponse> {
  const requestId = newRequestId();
  const origin = publicOrigin(getServerEnv());
  const { searchParams } = new URL(req.url);
  // Attacker-controllable → consume-side sanitiser + the guard predicate.
  const target = resolveRefreshReturnPath(searchParams.get('redirect'));
  const isRetry = searchParams.get(REFRESH_RETRY_PARAM) === '1';
  const jar = await cookies();

  const to = (path: string): NextResponse => {
    const res = NextResponse.redirect(new URL(path, origin).toString());
    res.headers.set('Cache-Control', 'no-store');
    return res;
  };
  // ⓑ — the reason travels with the bounce; the destination is kept so the
  // re-login returns the operator to the page they asked for.
  const sessionExpired = (): NextResponse => {
    const url = new URL(RE_LOGIN_PATH, origin);
    if (target !== '/') url.searchParams.set('redirect', target);
    const res = NextResponse.redirect(url.toString());
    res.headers.set('Cache-Control', 'no-store');
    return res;
  };

  // A complete session needs no refresh: another tab already refreshed (this
  // is also how `retry=1` wins a rotation race), or a stale bookmark. It also
  // means a cross-site-triggered GET can only ever rotate a session that is
  // already expired.
  if (jar.get(ACCESS_COOKIE)?.value && jar.get(OPERATOR_COOKIE)?.value) {
    logger.info('idle_refresh_session_complete', { requestId, retry: isRetry });
    return to(target);
  }

  // Never logged in / logged out → the same bounce the guard gives them.
  if (!jar.get(REFRESH_COOKIE)?.value) {
    logger.info('idle_refresh_no_refresh_cookie', { requestId });
    return to(buildLoginRedirectFor(target));
  }

  // Loop bound: the retry hop never refreshes again. If the other tab's fresh
  // cookies had landed we would have returned above.
  if (isRetry) {
    logger.warn('idle_refresh_race_unresolved', { requestId });
    return sessionExpired();
  }

  const outcome = await refreshSessionCookies(jar, { requestId, via: 'guard' });
  switch (outcome.kind) {
    case 'ok':
      return to(target);

    case 'no_refresh_token':
      return to(buildLoginRedirectFor(target));

    case 'grant_rejected':
      if (outcome.rotationSuspect) {
        // 🔴 Rotation race (V0015 `reuse-refresh-tokens=false`): another tab may
        // have rotated this refresh token a moment ago. Deleting cookies here
        // could land AFTER — and erase — that tab's fresh Set-Cookie, so
        // nothing is deleted. Wait, then look once more with the browser's
        // current cookies.
        await new Promise((resolve) => setTimeout(resolve, REFRESH_RACE_GRACE_MS));
        return to(
          `${SESSION_REFRESH_PATH}?redirect=${encodeURIComponent(target)}&${REFRESH_RETRY_PARAM}=1`,
        );
      }
      // IAM 5xx is transient: keep the cookies so the next guard visit may
      // succeed. A 4xx rejection is final → no partial state (§ 2.6).
      if (outcome.status < 500) clearFullSession(jar);
      return sessionExpired();

    case 'operator_not_provisioned':
      // Callback parity (§ 2.6 / ADR-MONO-044): a valid IAM login that is not
      // an operator of any tenant goes to self-service onboarding. The operator
      // session was dropped; the rotated IAM cookies stay (onboarding needs
      // them as its subject_token).
      return to('/onboarding');

    case 'operator_unavailable':
      clearFullSession(jar);
      return sessionExpired();

    case 'error':
      return sessionExpired();
  }
}
