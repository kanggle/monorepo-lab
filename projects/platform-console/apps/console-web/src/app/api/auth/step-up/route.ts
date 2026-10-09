import { NextResponse } from 'next/server';
import { cookies } from 'next/headers';
import { getServerEnv } from '@/shared/config/env';
import { startAuthorization } from '@/shared/lib/oidc-authorize';
import { resolveRefreshReturnPath } from '@/shared/lib/login-redirect';
import { logger, newRequestId } from '@/shared/lib/logger';

export const runtime = 'nodejs';

/**
 * `GET /api/auth/step-up?redirect=<path>` — second-factor step-up
 * (TASK-MONO-771, console-integration-contract § 2.6).
 *
 * Reached from:
 *   - the callback, when the operator token exchange answers `403 MFA_REQUIRED`
 *     for an authorization request this route did NOT start;
 *   - `GET /api/auth/refresh`, when the re-exchange answers `403 MFA_REQUIRED`
 *     (§ 2.6.1), and the browser API client after `POST /api/auth/refresh`
 *     answered `403 MFA_REQUIRED`;
 *   - the tenant switcher's «2단계 인증 후 들어가기» offer (§ 2.7).
 *
 * It starts a fresh IAM authorization **exactly as login does** (PKCE,
 * `state` — {@link startAuthorization}, the same function) with
 * `acr_values=mfa`. IAM then shows its challenge (or its enrolment page), and
 * the callback that follows performs the operator exchange again.
 *
 * Differences from `/api/auth/login`, both from the contract:
 *   - 🔴 the session is NOT cleared. § 2.6: on `403 MFA_REQUIRED` «the IAM
 *     cookies are kept». The callback that follows overwrites them on success.
 *   - the request is MARKED (`STEP_UP_MARKER_COOKIE` = its `state`): the loop
 *     bound. A callback for a marked request that still gets `403 MFA_REQUIRED`
 *     lands on `/login?error=mfa_required` instead of coming back here — one
 *     automatic step-up per login.
 *
 * `redirect` is attacker-controllable → the same consume-side resolver the
 * refresh route uses (`sanitizeReturnPath` + the guard predicate: `/login…`,
 * `/api/…` rejected → `/`). A cross-site GET can do no more than send the
 * visitor to IAM's own second-factor page.
 */
export async function GET(req: Request) {
  const requestId = newRequestId();
  const env = getServerEnv();
  const { searchParams } = new URL(req.url);
  const target = resolveRefreshReturnPath(searchParams.get('redirect'));

  const jar = await cookies();
  const authorizeUrl = await startAuthorization(jar, env, target, {
    stepUp: true,
  });

  logger.info('oidc_step_up_initiated', { requestId });
  const res = NextResponse.redirect(authorizeUrl);
  res.headers.set('Cache-Control', 'no-store');
  return res;
}
