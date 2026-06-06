import { NextResponse } from 'next/server';
import { cookies } from 'next/headers';
import { z } from 'zod';
import { getServerEnv } from '@/shared/config/env';
import {
  ACCESS_COOKIE,
  REFRESH_COOKIE,
  OPERATOR_COOKIE,
  TENANT_COOKIE,
  ASSUMED_TOKEN_COOKIE,
  ID_TOKEN_COOKIE,
  tokenCookieOpts,
} from '@/shared/lib/session';
import { exchangeForOperatorToken } from '@/shared/lib/operator-token-exchange';
import { exchangeForAssumedToken } from '@/shared/lib/assume-tenant-exchange';
import { OperatorExchangeError } from '@/shared/api/errors';
import { logger, newRequestId } from '@/shared/lib/logger';

export const runtime = 'nodejs';

/**
 * Server-route token refresh (frontend-app.md § Authentication: refresh handled
 * by a server route, not client JS).
 *
 * Public-client refresh_token grant against
 * `${OIDC_ISSUER_URL}/oauth2/token` (auth-api.md § POST /oauth2/token;
 * `grant_type=refresh_token` + `client_id`, no secret). V0015 seeds
 * `settings.token.reuse-refresh-tokens=false` → IAM rotates the refresh token
 * on every call (ADR-003), so the rotated token is re-stored.
 *
 * Re-exchange model (ADR-MONO-014 D2 / console-integration-contract § 2.6):
 * there is NO operator-refresh token/state — after the IAM access token
 * rotates, the operator token is re-obtained via a fresh exchange and the
 * operator cookie is updated. Consistent with callback fail-closed handling:
 * exchange `401` (operator deactivated/locked since login) or unavailable
 * → drop the whole session (IAM + operator cookies) and 401; never keep a
 * stale operator token, never fall back to the IAM token on /api/admin/**.
 *
 * On failure all session cookies are cleared so the API client falls back to
 * a clean re-login (no client-side token juggling — task Failure Scenario).
 */

const RefreshResponseSchema = z.object({
  access_token: z.string().min(1),
  token_type: z.string(),
  expires_in: z.number().int().positive(),
  refresh_token: z.string().min(1).optional(),
  // Rotated id_token (openid scope) — kept fresh for the logout id_token_hint
  // (TASK-PC-FE-033). Optional: not every refresh response re-issues it.
  id_token: z.string().min(1).optional(),
});

export async function POST() {
  const requestId = newRequestId();
  const env = getServerEnv();
  const jar = await cookies();
  const refresh = jar.get(REFRESH_COOKIE)?.value;

  if (!refresh) {
    return NextResponse.json(
      { code: 'TOKEN_INVALID', message: 'refresh token missing' },
      { status: 401 },
    );
  }

  try {
    const form = new URLSearchParams();
    form.set('grant_type', 'refresh_token');
    form.set('refresh_token', refresh);
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
      jar.delete(ACCESS_COOKIE);
      jar.delete(REFRESH_COOKIE);
      jar.delete(OPERATOR_COOKIE);
      jar.delete(ID_TOKEN_COOKIE);
      // Whole session dropped → also clear the coupled tenant + assumed token.
      jar.delete(ASSUMED_TOKEN_COOKIE);
      jar.delete(TENANT_COOKIE);
      logger.warn('refresh_failed', { requestId, status: upstream.status });
      return NextResponse.json(
        { code: 'TOKEN_INVALID', message: 'refresh failed' },
        { status: 401 },
      );
    }

    const data = RefreshResponseSchema.parse(await upstream.json());
    jar.set(ACCESS_COOKIE, data.access_token, {
      ...tokenCookieOpts,
      maxAge: data.expires_in,
    });
    if (data.refresh_token) {
      jar.set(REFRESH_COOKIE, data.refresh_token, {
        ...tokenCookieOpts,
        maxAge: 2_592_000,
      });
    }
    // Keep the logout id_token_hint fresh (TASK-PC-FE-033).
    if (data.id_token) {
      jar.set(ID_TOKEN_COOKIE, data.id_token, {
        ...tokenCookieOpts,
        maxAge: data.expires_in,
      });
    }

    // --- Re-exchange the operator token (§ 2.6 / ADR-MONO-014 D2) ---------
    // No operator-refresh state: the rotated IAM access token is re-exchanged
    // for a fresh operator token. Any exchange failure (fail-closed 401 or
    // unavailable) drops the WHOLE session — no stale operator token, no
    // GAP-token fallback on the operator boundary.
    try {
      const op = await exchangeForOperatorToken(data.access_token);
      jar.set(OPERATOR_COOKIE, op.accessToken, {
        ...tokenCookieOpts,
        maxAge: op.expiresIn,
      });
    } catch (err) {
      jar.delete(ACCESS_COOKIE);
      jar.delete(REFRESH_COOKIE);
      jar.delete(OPERATOR_COOKIE);
      jar.delete(ID_TOKEN_COOKIE);
      // Whole session dropped → also clear the coupled tenant + assumed token.
      jar.delete(ASSUMED_TOKEN_COOKIE);
      jar.delete(TENANT_COOKIE);
      const failClosed =
        err instanceof OperatorExchangeError && err.reason === 'fail_closed';
      logger.warn('refresh_reexchange_failed', {
        requestId,
        reason: failClosed ? 'fail_closed' : 'unavailable',
      });
      return NextResponse.json(
        { code: 'TOKEN_INVALID', message: 'operator session ended' },
        { status: 401 },
      );
    }

    // --- Re-assume the active tenant (ADR-MONO-020 D4 / § 2.7) ------------
    // The assumed (domain-facing) token has NO refresh token (D2) — it is
    // re-minted from the rotated base IAM access token. If an active tenant is
    // set, re-assume it now so the domain-facing credential stays scoped to
    // the selected customer after a refresh. On any re-assume failure DROP
    // BOTH the assumed token AND the active tenant (the operator falls back to
    // the base/no-tenant state — NEVER a stale assumed token). The base IAM +
    // operator session stays valid; only the tenant selection is reset.
    const activeTenant = jar.get(TENANT_COOKIE)?.value;
    if (activeTenant) {
      try {
        const assumed = await exchangeForAssumedToken(
          data.access_token,
          activeTenant,
        );
        jar.set(ASSUMED_TOKEN_COOKIE, assumed.accessToken, {
          ...tokenCookieOpts,
          maxAge: assumed.expiresIn,
        });
      } catch {
        // Drop the coupled pair — no stale assumed token, no orphan tenant.
        jar.delete(ASSUMED_TOKEN_COOKIE);
        jar.delete(TENANT_COOKIE);
        logger.warn('refresh_reassume_failed', { requestId });
      }
    }

    logger.info('refresh_ok', { requestId });
    return NextResponse.json({ ok: true });
  } catch (err) {
    logger.error('refresh_error', { requestId, err: String(err) });
    return NextResponse.json(
      { code: 'DOWNSTREAM_ERROR', message: 'refresh proxy error' },
      { status: 502 },
    );
  }
}
