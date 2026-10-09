import { NextResponse } from 'next/server';
import { cookies } from 'next/headers';
import { z } from 'zod';
import { getServerEnv, publicOrigin } from '@/shared/config/env';
import {
  ACCESS_COOKIE,
  REFRESH_COOKIE,
  OPERATOR_COOKIE,
  ID_TOKEN_COOKIE,
  PKCE_VERIFIER_COOKIE,
  OAUTH_STATE_COOKIE,
  STEP_UP_MARKER_COOKIE,
  tokenCookieOpts,
  clearOperatorSession,
} from '@/shared/lib/session';
import { buildStepUpRedirectFor } from '@/shared/lib/login-redirect';
import { exchangeForOperatorToken } from '@/shared/lib/operator-token-exchange';
import { establishDefaultTenant } from '@/shared/lib/active-tenant-default';
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
 *      TASK-MONO-771: exchange `403 MFA_REQUIRED` → `/api/auth/step-up`
 *      (IAM cookies kept), or — when this callback answers the step-up's own
 *      request — `/login?error=mfa_required` (the loop bound). Only the `401`
 *      goes to `/onboarding`.
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

/**
 * TASK-PC-FE-324 AC-0 — measured 2026-10-09 (UTC). The refusal this ticket is
 * about is minted at the IAM token endpoint, not `/oauth2/authorize`: the
 * browser's existing `auth.hubwang.com` session (a consumer-pool account,
 * e.g. from a same-browser store login) lets `/oauth2/authorize` complete
 * silently (SSO reuse, a code IS issued), then
 * `TenantClaimTokenCustomizer.refuseConsumerPoolTenant` (iam-platform
 * auth-service `infrastructure/oauth2/TenantClaimTokenCustomizer.java:261-275`)
 * refuses to mint a token for it:
 *
 * ```java
 * throw new OAuth2AuthenticationException(new OAuth2Error(OAuth2ErrorCodes.INVALID_GRANT,
 *         "tenant_id '" + TenantContext.CONSUMER_POOL_TENANT_ID
 *                 + "' is a reserved storage value and is never issued", null));
 * ```
 *
 * Spring Security OAuth2 Authorization Server's default token-endpoint error
 * writer serialises that as HTTP 400
 * `{"error":"invalid_grant","error_description":"tenant_id 'consumer-pool' is
 * a reserved storage value and is never issued"}` — confirmed against the
 * SAME customizer's refusal shape asserted by iam-platform's own
 * `AssumeTenantExchangeIntegrationTest.consumerPool_refusedEvenWhenAssigned`
 * (`.contains("invalid_grant")`) and the sibling `error_description`-based
 * discriminator already in production for `TOKEN_TENANT_MISMATCH`
 * (`SasRefreshTokenAuthenticationProvider`, multi-tenancy.md § 202).
 *
 * Discriminator decision (AC-0 "문자열 일치 vs IdP 전용 코드"): **string match**,
 * not a new IdP-side error code — adding a dedicated code would be an
 * iam-platform change, out of this ticket's (platform-console-owned) scope,
 * and the existing `error_description` already carries a value anchored to a
 * source CONSTANT (`TenantContext.CONSUMER_POOL_TENANT_ID = "consumer-pool"`),
 * not free English prose. The match is therefore narrowed to the quoted
 * constant value, `'consumer-pool'`, rather than the full sentence — a future
 * wording edit around it does not silently break this, only a rename of the
 * reserved tenant id itself would (Edge Case, accepted — that rename is a
 * breaking, deliberate iam-platform change that would need its own
 * migration anyway).
 *
 * Control (must NOT match): every other `invalid_grant` on this endpoint —
 * expired/reused code, `TOKEN_TENANT_MISMATCH`, assume-tenant denials — has a
 * DIFFERENT `error_description` that never contains this literal, so those
 * keep the generic `token_exchange_failed` message (Scope § In Scope).
 */
const CONSUMER_POOL_REFUSAL_MARKER = "'consumer-pool'";

function isConsumerPoolSsoRefusal(body: unknown): boolean {
  if (typeof body !== 'object' || body === null) return false;
  const { error, error_description: description } = body as {
    error?: unknown;
    error_description?: unknown;
  };
  return (
    error === 'invalid_grant' &&
    typeof description === 'string' &&
    description.includes(CONSUMER_POOL_REFUSAL_MARKER)
  );
}

/**
 * TASK-MONO-771 — IAM's «취소» on its second-factor screens redirects here with
 * `error=access_denied` + `error_description=mfa_cancelled` (auth-api.md
 * § /mfa/challenge «취소», OIDC Core 3.1.2.6). Whole-value equality on both —
 * every other provider error keeps `provider_error`.
 */
function isSecondFactorCancel(error: string, description: string | null): boolean {
  return error === 'access_denied' && description === 'mfa_cancelled';
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
  const stepUpMarker = jar.get(STEP_UP_MARKER_COOKIE)?.value;

  // Always clear transient cookies — single-use. (The step-up marker too: it
  // bounds ONE authorization request, never the next one.)
  jar.delete(PKCE_VERIFIER_COOKIE);
  jar.delete(OAUTH_STATE_COOKIE);
  jar.delete(STEP_UP_MARKER_COOKIE);

  if (oauthError) {
    // TASK-MONO-771 (§ 2.6) — IAM's own «취소» on its second-factor page
    // answers `access_denied` + `mfa_cancelled` (auth-api.md § /mfa/challenge).
    // It lands where the loop bound lands, with the same reason.
    if (isSecondFactorCancel(oauthError, searchParams.get('error_description'))) {
      logger.info('oidc_second_factor_cancelled', { requestId });
      return loginRedirect(publicOrigin(env), 'mfa_required');
    }
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

    // DEMO-URL-EXEMPT: oidc-issuer — 발급자는 데모 IP 에서 파생되지 않는다.
    //   `ADR-MONO-069` 가 `C2` 로 **고정된 이름**(`https://auth.hubwang.com`)을 지정했고,
    //   그 문자열은 토큰의 `iss` 와 **문자 비교**된다 ⇒ 조용히 고쳐 쓰면 로그인이 깨진다.
    //   데모 컨테이너에서는 `demo.env` 가 이미 `DEMO_DOMAIN` 형태로 주입한다.
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
      // TASK-PC-FE-324 — distinguish "logged in as the WRONG (consumer-pool)
      // account" from every other token-exchange failure (expired/reused
      // code, network/5xx, …). See `isConsumerPoolSsoRefusal` above for the
      // measured discriminator. Only this one case gets the account-conflict
      // message + logout affordance — everything else keeps the generic
      // `token_exchange_failed` copy (Scope § In/Out of Scope).
      if (isConsumerPoolSsoRefusal(body)) {
        logger.warn('oidc_token_exchange_consumer_pool_refused', { requestId });
        return loginRedirect(publicOrigin(env), 'sso_wrong_account');
      }
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
    let operatorToken: string;
    try {
      const op = await exchangeForOperatorToken(data.access_token);
      jar.set(OPERATOR_COOKIE, op.accessToken, {
        ...tokenCookieOpts,
        maxAge: op.expiresIn,
      });
      operatorToken = op.accessToken;
    } catch (err) {
      const notProvisioned =
        err instanceof OperatorExchangeError && err.reason === 'fail_closed';
      const mfaRequired =
        err instanceof OperatorExchangeError && err.reason === 'mfa_required';

      // Whatever the failure: NO operator cookie is set, and any prior
      // active-tenant selection (+ its coupled assumed token) is dropped so a
      // failed exchange never leaves a stale tenant pointing at a session with
      // no operator credential (TASK-PC-FE-036). `isAuthenticated()` requires
      // BOTH cookies, so no branch below can reach the `(console)` shell.
      clearOperatorSession(jar);

      if (mfaRequired) {
        // TASK-MONO-771 (§ 2.6) — exchange `403 MFA_REQUIRED`: the caller IS a
        // resolved operator; a second factor is missing. 🔴 Step-up, NEVER
        // onboarding — routing this to `/onboarding` would send a SUPER_ADMIN
        // to the tenant-creation shell. The IAM cookies are KEPT (§ 2.6).
        //
        // Loop bound: one automatic step-up per login. A callback for the
        // request the step-up route itself started (marker === state) that is
        // STILL refused does not step up again.
        if (stepUpMarker !== undefined && stepUpMarker === state) {
          logger.warn('operator_exchange_mfa_required_after_step_up', { requestId });
          return loginRedirect(publicOrigin(env), 'mfa_required');
        }
        logger.info('operator_exchange_mfa_required_to_step_up', { requestId });
        return NextResponse.redirect(
          new URL(buildStepUpRedirectFor(postLoginPath), publicOrigin(env)).toString(),
        );
      }

      // 🔴 Only a `401` (`fail_closed`) means «not an operator» (§ 2.6,
      // TASK-MONO-771) — the predicate below must stay on that reason alone.
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

    // --- Default the active tenant (TASK-PC-FE-292) -----------------------
    // TASK-PC-FE-036 defaulted it to the token's `tenant_id`, on the premise
    // that the base token is already scoped to the operator's home tenant.
    // 🔴 That premise is false for this client: its tokens carry the client's
    // operational slug (`iam`), so the default was `iam` with no assumed token
    // and every domain screen answered 401 → «세션 만료» (live, 2026-09-16).
    // Now: the registry's selectable tenants decide, and the chosen tenant is
    // ASSUMED. Never fatal — without a default the operator is still logged in
    // and the domain sections ask them to pick a tenant.
    await establishDefaultTenant(jar, {
      accessToken: data.access_token,
      operatorToken,
      requestId,
      via: 'callback',
    });

    logger.info('oidc_login_success', { requestId });
    return NextResponse.redirect(
      new URL(postLoginPath, publicOrigin(env)).toString(),
    );
  } catch (err) {
    logger.error('oidc_callback_error', { requestId, err: String(err) });
    return loginRedirect(publicOrigin(env), 'token_exchange_failed');
  }
}
