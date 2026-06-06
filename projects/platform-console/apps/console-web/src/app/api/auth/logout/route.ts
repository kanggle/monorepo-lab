import { NextResponse } from 'next/server';
import { cookies } from 'next/headers';
import { getServerEnv } from '@/shared/config/env';
import {
  ACCESS_COOKIE,
  REFRESH_COOKIE,
  OPERATOR_COOKIE,
  TENANT_COOKIE,
  ASSUMED_TOKEN_COOKIE,
  ID_TOKEN_COOKIE,
} from '@/shared/lib/session';
import { logger, newRequestId } from '@/shared/lib/logger';

export const runtime = 'nodejs';

/**
 * Logout (TASK-PC-FE-033 — RP-initiated OIDC logout).
 *
 * Three layers, in order, so the operator is fully signed out:
 *   1. Best-effort IAM token revocation (RFC 7009, auth-api.md
 *      § POST /oauth2/revoke — public client passes `client_id`, no secret).
 *      Invalidates the tokens but NOT the IdP login session.
 *   2. Clear ALL console session cookies (access / refresh / operator / tenant
 *      / assumed / id_token). Cookie clearing is the source of truth for
 *      "logged out" — even if revoke or the IdP end-session below fails, the
 *      console session ends here.
 *   3. Return the OIDC **end_session** URL (`/connect/logout` with
 *      `id_token_hint` + a registered `post_logout_redirect_uri`). The client
 *      navigates the browser there so the IdP (SAS) terminates ITS OWN session
 *      — otherwise `/oauth2/authorize` silently re-authenticates the next login
 *      with no credential form (the defect this closes).
 *
 * Fail-safe: with no `id_token` cookie (legacy session) there is no
 * `id_token_hint`, so we skip end-session and fall back to a local-only logout
 * (`logoutUrl = <app>/login`). Logout never errors or hangs.
 */
export async function POST() {
  const requestId = newRequestId();
  const env = getServerEnv();
  const jar = await cookies();
  const access = jar.get(ACCESS_COOKIE)?.value;
  const refresh = jar.get(REFRESH_COOKIE)?.value;
  const idToken = jar.get(ID_TOKEN_COOKIE)?.value;

  const revoke = async (token: string, hint: 'access_token' | 'refresh_token') => {
    const form = new URLSearchParams();
    form.set('token', token);
    form.set('token_type_hint', hint);
    form.set('client_id', env.OIDC_CLIENT_ID);
    try {
      await fetch(`${env.OIDC_ISSUER_URL}/oauth2/revoke`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/x-www-form-urlencoded',
          'X-Request-Id': requestId,
        },
        body: form.toString(),
        cache: 'no-store',
      });
    } catch (err) {
      logger.warn('logout_revoke_failed', { requestId, hint, err: String(err) });
    }
  };

  if (refresh) await revoke(refresh, 'refresh_token');
  if (access) await revoke(access, 'access_token');

  jar.delete(ACCESS_COOKIE);
  jar.delete(REFRESH_COOKIE);
  jar.delete(OPERATOR_COOKIE);
  jar.delete(TENANT_COOKIE);
  jar.delete(ASSUMED_TOKEN_COOKIE);
  jar.delete(ID_TOKEN_COOKIE);

  // Post-logout landing — must EXACTLY match a registered
  // `post-logout-redirect-uris` entry on the IAM `platform-console-web` client
  // (auth-service V0021). No query string (SAS matches the URI exactly).
  const postLogout = new URL('/login', env.NEXT_PUBLIC_APP_URL).toString();

  let logoutUrl: string;
  if (idToken) {
    const endSession = new URL(`${env.OIDC_ISSUER_URL}/connect/logout`);
    endSession.searchParams.set('id_token_hint', idToken);
    endSession.searchParams.set('post_logout_redirect_uri', postLogout);
    endSession.searchParams.set('client_id', env.OIDC_CLIENT_ID);
    logoutUrl = endSession.toString();
    logger.info('logout_complete', { requestId, rpInitiated: true });
  } else {
    // No id_token_hint → cannot drive a silent IdP end-session; fall back to a
    // local-only logout (cookies already cleared). AC-5.
    logoutUrl = postLogout;
    logger.info('logout_complete', { requestId, rpInitiated: false });
  }

  return NextResponse.json({ logoutUrl });
}
