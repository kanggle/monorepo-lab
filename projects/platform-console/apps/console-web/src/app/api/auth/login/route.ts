import { NextResponse } from 'next/server';
import { cookies } from 'next/headers';
import { getServerEnv } from '@/shared/config/env';
import {
  generateCodeVerifier,
  deriveCodeChallenge,
  generateState,
} from '@/shared/lib/pkce';
import {
  PKCE_VERIFIER_COOKIE,
  OAUTH_STATE_COOKIE,
  transientCookieOpts,
} from '@/shared/lib/session';
import { sanitizeReturnPath } from '@/shared/lib/return-path';
import { logger, newRequestId } from '@/shared/lib/logger';

export const runtime = 'nodejs';

/**
 * IAM OIDC Authorization Code + PKCE — step 1 (login initiation).
 *
 * Public client `platform-console-web` (no client secret — auth-service
 * V0015 seed, ADR-003 public-client lineage). Generates a PKCE
 * verifier/challenge + anti-CSRF state server-side, stores the verifier+state
 * in short-lived HttpOnly cookies (never client JS — frontend-app.md
 * § Authentication), and 302-redirects the browser to GAP
 * `${OIDC_ISSUER_URL}/oauth2/authorize` (auth-api.md § GET /oauth2/authorize).
 *
 * `redirect` query param (post-login target) is sanitised to a same-site
 * relative path and round-tripped through the OAuth `state` cookie.
 */
export async function GET(req: Request) {
  const requestId = newRequestId();
  const env = getServerEnv();
  const { searchParams } = new URL(req.url);

  // Same-site sanitise via the shared predicate the login page also uses —
  // the single source of "is this redirect safe?" (open-redirect guard,
  // PC-FE-253).
  const postLoginPath = sanitizeReturnPath(searchParams.get('redirect'));

  const verifier = generateCodeVerifier();
  const challenge = await deriveCodeChallenge(verifier);
  const state = generateState();

  const authorizeUrl = new URL(`${env.OIDC_ISSUER_URL}/oauth2/authorize`);
  authorizeUrl.searchParams.set('response_type', 'code');
  authorizeUrl.searchParams.set('client_id', env.OIDC_CLIENT_ID);
  authorizeUrl.searchParams.set('redirect_uri', env.OIDC_REDIRECT_URI);
  authorizeUrl.searchParams.set('scope', env.OIDC_SCOPE);
  authorizeUrl.searchParams.set('code_challenge', challenge);
  authorizeUrl.searchParams.set('code_challenge_method', 'S256');
  authorizeUrl.searchParams.set('state', state);

  const jar = await cookies();
  jar.set(PKCE_VERIFIER_COOKIE, verifier, transientCookieOpts);
  // state cookie carries both the CSRF token and the post-login target.
  jar.set(
    OAUTH_STATE_COOKIE,
    `${state}|${postLoginPath}`,
    transientCookieOpts,
  );

  logger.info('oidc_login_initiated', { requestId });
  return NextResponse.redirect(authorizeUrl.toString());
}
