import { NextResponse } from 'next/server';
import { cookies } from 'next/headers';
import { z } from 'zod';
import { getServerEnv, publicOrigin } from '@/shared/config/env';
import {
  ACCESS_COOKIE,
  REFRESH_COOKIE,
  OPERATOR_COOKIE,
  ID_TOKEN_COOKIE,
  TENANT_COOKIE,
  PKCE_VERIFIER_COOKIE,
  OAUTH_STATE_COOKIE,
  tokenCookieOpts,
  clearOperatorSession,
} from '@/shared/lib/session';
import { exchangeForOperatorToken } from '@/shared/lib/operator-token-exchange';
import { homeTenantFromAccessToken } from '@/shared/lib/jwt';
import { OperatorExchangeError } from '@/shared/api/errors';
import { logger, newRequestId } from '@/shared/lib/logger';

export const runtime = 'nodejs';

/**
 * IAM OIDC Authorization Code + PKCE — step 2 (callback / token exchange).
 *
 * IAM redirects the browser here (exact pre-registered redirect URI
 * `http://console.local/api/auth/callback` — V0015 seed). This handler:
 *   1. Validates the `state` against the HttpOnly state cookie (CSRF; on
 *      mismatch → safe re-login, no token leak — task Edge Case).
 *   2. Exchanges `code` + `code_verifier` at
 *      `${OIDC_ISSUER_URL}/oauth2/token` as a PUBLIC client
 *      (`grant_type=authorization_code`, `client_id`, no secret —
 *      auth-api.md § POST /oauth2/token).
 *   3. Stores access/refresh tokens in HttpOnly Secure SameSite=Strict
 *      cookies ONLY (frontend-app.md § Authentication; never localStorage).
 *   4. Server-side exchanges the IAM access token for an admin-service
 *      operator token (RFC 8693 — console-integration-contract § 2.6 /
 *      ADR-MONO-014) and stores it in its own HttpOnly operator cookie
 *      (`maxAge = expiresIn`). Fail-closed: exchange `401`
 *      → `not_provisioned` re-login; unavailable → `operator_exchange_
 *      unavailable` re-login. On either failure NO operator cookie is set
 *      and the IAM token cookies are cleared — there is no partial authed
 *      state and the IAM token can never be used as an `/api/admin/**`
 *      credential (the #569 defect this closes).
 *   5. Clears the transient PKCE/state cookies and 302s to the post-login
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
    return loginRedirect(publicOrigin(env), 'provider_error');
  }

  if (!code || !state || !verifier || !stateCookie) {
    logger.warn('oidc_callback_missing_params', {
      requestId,
      hasCode: !!code,
      hasState: !!state,
      hasVerifier: !!verifier,
    });
    return loginRedirect(publicOrigin(env), 'invalid_state');
  }

  const [expectedState, postLoginPath = '/'] = stateCookie.split('|');
  if (state !== expectedState) {
    logger.warn('oidc_state_mismatch', { requestId });
    return loginRedirect(publicOrigin(env), 'state_mismatch');
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
      return loginRedirect(publicOrigin(env), 'token_exchange_failed');
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
    // id_token: stored ONLY as the RP-initiated-logout `id_token_hint`
    // (TASK-PC-FE-033 — never a credential). Optional in the OIDC response.
    if (data.id_token) {
      jar.set(ID_TOKEN_COOKIE, data.id_token, {
        ...tokenCookieOpts,
        maxAge: data.expires_in,
      });
    }

    // --- Server-side operator-token exchange (§ 2.6 / ADR-MONO-014) -------
    // The IAM access token is NOT an /api/admin/** credential — exchange it
    // for the operator token.
    try {
      const op = await exchangeForOperatorToken(data.access_token);
      jar.set(OPERATOR_COOKIE, op.accessToken, {
        ...tokenCookieOpts,
        maxAge: op.expiresIn,
      });
    } catch (err) {
      const notProvisioned =
        err instanceof OperatorExchangeError && err.reason === 'fail_closed';

      // Whatever the failure: NO operator cookie is set, and any prior
      // active-tenant selection (+ its coupled assumed token) is dropped so a
      // failed exchange never leaves a stale tenant pointing at a session with
      // no operator credential (TASK-PC-FE-036). `isAuthenticated()` requires
      // BOTH cookies, so neither branch below can reach the `(console)` shell.
      clearOperatorSession(jar);

      if (notProvisioned) {
        // fail_closed (exchange 401) = a VALID IAM login that is simply not
        // an operator of any tenant yet. Instead of bouncing to re-login, send
        // them to self-service onboarding (ADR-MONO-044 §3.4 — AWS "create
        // account → root" / GCP "create project → owner" parity). The IAM
        // access + refresh cookies are KEPT — not as an admin credential (they
        // never are, § 2.1), but as the onboarding endpoint's `subjectToken`
        // input + the operator-token re-exchange input once a tenant is
        // created. The pre-operator state (access present, operator absent) is
        // the only state the `(onboarding)` route group admits.
        logger.info('operator_exchange_not_provisioned_to_onboarding', {
          requestId,
        });
        return NextResponse.redirect(
          new URL('/onboarding', publicOrigin(env)).toString(),
        );
      }

      // unavailable (400/5xx/timeout/network) = a real exchange failure, NOT
      // "no workspace". Fail closed exactly as before: drop the IAM cookies too
      // (no partial authed state; the IAM token is never left usable) and force
      // a clean re-login. Onboarding must NOT be offered on a transient outage
      // (it would mis-read a downed admin-service as "you have no tenant").
      jar.delete(ACCESS_COOKIE);
      jar.delete(REFRESH_COOKIE);
      jar.delete(ID_TOKEN_COOKIE);
      logger.warn('operator_exchange_unavailable_on_callback', { requestId });
      return loginRedirect(
        publicOrigin(env),
        'operator_exchange_unavailable',
      );
    }

    // --- Default the active tenant to the operator's home tenant ----------
    // TASK-PC-FE-036: without this the active-tenant cookie is unset on first
    // load, so the tenant-scoped overviews (운영자 통합 개요 / 도메인 상태)
    // gate with "select a tenant" even though the switcher shows a tenant —
    // a confusing UI/server mismatch. A real-customer operator's IAM OIDC
    // access token already carries `tenant_id=<home>` (+ entitled_domains), so
    // defaulting the active tenant to it makes the overviews work immediately
    // via the base token (no assume-tenant needed — getDomainFacingToken falls
    // back to the base access token, which is already scoped to the home
    // tenant; switching to a NON-home assigned tenant still drives the
    // assume-tenant exchange via /api/tenant). The platform sentinel '*' has
    // no single home tenant → left unset (the operator explicitly selects a
    // customer; the switcher renders an unselected placeholder for them).
    const homeTenant = homeTenantFromAccessToken(data.access_token);
    if (homeTenant) {
      jar.set(TENANT_COOKIE, homeTenant, {
        ...tokenCookieOpts,
        maxAge: data.expires_in,
      });
    }

    logger.info('oidc_login_success', { requestId });
    return NextResponse.redirect(
      new URL(postLoginPath, publicOrigin(env)).toString(),
    );
  } catch (err) {
    logger.error('oidc_callback_error', { requestId, err: String(err) });
    return loginRedirect(publicOrigin(env), 'token_exchange_failed');
  }
}
