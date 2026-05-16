import { NextResponse } from 'next/server';
import { cookies } from 'next/headers';
import { z } from 'zod';
import { getServerEnv } from '@/shared/config/env';
import {
  ACCESS_COOKIE,
  REFRESH_COOKIE,
  PKCE_VERIFIER_COOKIE,
  OAUTH_STATE_COOKIE,
  tokenCookieOpts,
} from '@/shared/lib/session';
import { logger, newRequestId } from '@/shared/lib/logger';

export const runtime = 'nodejs';

/**
 * GAP OIDC Authorization Code + PKCE — step 2 (callback / token exchange).
 *
 * GAP redirects the browser here (exact pre-registered redirect URI
 * `http://console.local/api/auth/callback` — V0015 seed). This handler:
 *   1. Validates the `state` against the HttpOnly state cookie (CSRF; on
 *      mismatch → safe re-login, no token leak — task Edge Case).
 *   2. Exchanges `code` + `code_verifier` at
 *      `${OIDC_ISSUER_URL}/oauth2/token` as a PUBLIC client
 *      (`grant_type=authorization_code`, `client_id`, no secret —
 *      auth-api.md § POST /oauth2/token).
 *   3. Stores access/refresh tokens in HttpOnly Secure SameSite=Strict
 *      cookies ONLY (frontend-app.md § Authentication; never localStorage).
 *   4. Clears the transient PKCE/state cookies and 302s to the post-login
 *      path carried by the state cookie.
 */

const TokenResponseSchema = z.object({
  access_token: z.string().min(1),
  token_type: z.string(),
  expires_in: z.number().int().positive(),
  refresh_token: z.string().min(1).optional(),
  scope: z.string().optional(),
  id_token: z.string().optional(),
});

function loginRedirect(appUrl: string, reason: string) {
  const url = new URL('/login', appUrl);
  url.searchParams.set('error', reason);
  return NextResponse.redirect(url.toString());
}

export async function GET(req: Request) {
  const requestId = newRequestId();
  const env = getServerEnv();
  const { searchParams } = new URL(req.url);
  const jar = await cookies();

  const code = searchParams.get('code');
  const state = searchParams.get('state');
  const oauthError = searchParams.get('error');

  const verifier = jar.get(PKCE_VERIFIER_COOKIE)?.value;
  const stateCookie = jar.get(OAUTH_STATE_COOKIE)?.value;

  // Always clear transient cookies — single-use.
  jar.delete(PKCE_VERIFIER_COOKIE);
  jar.delete(OAUTH_STATE_COOKIE);

  if (oauthError) {
    logger.warn('oidc_provider_error', { requestId, oauthError });
    return loginRedirect(env.NEXT_PUBLIC_APP_URL, 'provider_error');
  }

  if (!code || !state || !verifier || !stateCookie) {
    logger.warn('oidc_callback_missing_params', {
      requestId,
      hasCode: !!code,
      hasState: !!state,
      hasVerifier: !!verifier,
    });
    return loginRedirect(env.NEXT_PUBLIC_APP_URL, 'invalid_state');
  }

  const [expectedState, postLoginPath = '/'] = stateCookie.split('|');
  if (state !== expectedState) {
    logger.warn('oidc_state_mismatch', { requestId });
    return loginRedirect(env.NEXT_PUBLIC_APP_URL, 'state_mismatch');
  }

  try {
    const form = new URLSearchParams();
    form.set('grant_type', 'authorization_code');
    form.set('code', code);
    form.set('redirect_uri', env.OIDC_REDIRECT_URI);
    form.set('code_verifier', verifier);
    form.set('client_id', env.OIDC_CLIENT_ID);

    const upstream = await fetch(`${env.OIDC_ISSUER_URL}/oauth2/token`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/x-www-form-urlencoded',
        Accept: 'application/json',
        'X-Request-Id': requestId,
      },
      body: form.toString(),
      cache: 'no-store',
    });

    if (!upstream.ok) {
      const body = await upstream.json().catch(() => ({}));
      logger.warn('oidc_token_exchange_failed', {
        requestId,
        status: upstream.status,
        error: (body as { error?: string }).error,
      });
      return loginRedirect(env.NEXT_PUBLIC_APP_URL, 'token_exchange_failed');
    }

    const data = TokenResponseSchema.parse(await upstream.json());

    jar.set(ACCESS_COOKIE, data.access_token, {
      ...tokenCookieOpts,
      maxAge: data.expires_in,
    });
    if (data.refresh_token) {
      jar.set(REFRESH_COOKIE, data.refresh_token, {
        ...tokenCookieOpts,
        // refresh-token TTL from V0015 (PT720H = 2,592,000s).
        maxAge: 2_592_000,
      });
    }

    logger.info('oidc_login_success', { requestId });
    return NextResponse.redirect(
      new URL(postLoginPath, env.NEXT_PUBLIC_APP_URL).toString(),
    );
  } catch (err) {
    logger.error('oidc_callback_error', { requestId, err: String(err) });
    return loginRedirect(env.NEXT_PUBLIC_APP_URL, 'token_exchange_failed');
  }
}
