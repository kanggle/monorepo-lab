import type { cookies } from 'next/headers';
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
  clearOperatorSession,
  clearTenantSelection,
} from '@/shared/lib/session';
import { exchangeForOperatorToken } from '@/shared/lib/operator-token-exchange';
import { exchangeForAssumedToken } from '@/shared/lib/assume-tenant-exchange';
import { homeTenantFromAccessToken } from '@/shared/lib/jwt';
import { OperatorExchangeError } from '@/shared/api/errors';
import { logger } from '@/shared/lib/logger';

/**
 * The single IAM refresh + re-exchange sequence, shared by BOTH refresh entry
 * points (TASK-MONO-674):
 *
 *   - `POST /api/auth/refresh` — the browser API client after a `401`
 *     (JSON response; unchanged contract, console-integration-contract § 2.6);
 *   - `GET  /api/auth/refresh` — the `(console)` guard's idle-expiry hop
 *     (navigation response; § 2.6.1).
 *
 * 🔴 Why one function: the two entry points differ only in **what they do with
 * a failure** (JSON 401 vs. a redirect with a reason, and whether a rejected
 * grant may be a two-tab rotation race). The token handling itself — rotate,
 * re-exchange the operator token, re-assume the tenant — must never diverge;
 * two copies of it is how one side silently keeps a stale operator token.
 *
 * Cookie effects by outcome (callers own everything else):
 *   - `ok`                       → access/refresh/id_token + operator (+ assumed / home tenant) set.
 *   - `no_refresh_token`         → nothing touched.
 *   - `grant_rejected`           → nothing touched (the caller decides whether to clear —
 *                                  a rotation-race loser must NOT delete the winner's cookies).
 *   - `operator_not_provisioned` / `operator_unavailable`
 *                                → rotated IAM cookies set, operator session (+ tenant pair)
 *                                  dropped. The caller decides whether to drop IAM too.
 *   - `error`                    → nothing touched (thrown before any cookie was set).
 *
 * Public-client refresh_token grant against `${OIDC_ISSUER_URL}/oauth2/token`
 * (`grant_type=refresh_token` + `client_id`, no secret). V0015 seeds
 * `settings.token.reuse-refresh-tokens=false` → IAM rotates the refresh token on
 * every call, so the rotated token is re-stored.
 */

type CookieStore = Awaited<ReturnType<typeof cookies>>;

/**
 * How long the idle-expiry `GET` waits before its single `retry=1` hop after an
 * IAM `400 invalid_grant` (TASK-MONO-674 § 2.6.1 rotation race).
 *
 * The retry request carries the browser's cookies **as of when it is sent**, so
 * the wait is what gives a concurrently-refreshing tab time to land its
 * `Set-Cookie` (its IAM call already rotated; it is still doing the operator +
 * assume re-exchanges). Too short → the loser tab reads no fresh session and
 * shows `session_expired` although the other tab just refreshed. Too long → a
 * genuinely revoked refresh token costs this much extra before the reason is
 * shown. That second cost is only ever paid on a failure path, so the value
 * errs long. It lives here, not in `route.ts`, because a Next route file may
 * only export route fields.
 */
export const REFRESH_RACE_GRACE_MS = 2000;

export type RefreshOutcome =
  | { kind: 'ok' }
  | { kind: 'no_refresh_token' }
  | {
      kind: 'grant_rejected';
      status: number;
      oauthError: string | undefined;
      /** IAM `400 invalid_grant` — the token may have been rotated by a concurrent refresh. */
      rotationSuspect: boolean;
    }
  | { kind: 'operator_not_provisioned' }
  | { kind: 'operator_unavailable' }
  | { kind: 'error' };

const RefreshResponseSchema = z.object({
  access_token: z.string().min(1),
  token_type: z.string(),
  expires_in: z.number().int().positive(),
  refresh_token: z.string().min(1).optional(),
  // Rotated id_token (openid scope) — kept fresh for the logout id_token_hint
  // (TASK-PC-FE-033). Optional: not every refresh response re-issues it.
  id_token: z.string().min(1).optional(),
});

export async function refreshSessionCookies(
  jar: CookieStore,
  ctx: { requestId: string; via: 'api' | 'guard' },
): Promise<RefreshOutcome> {
  const { requestId, via } = ctx;
  const env = getServerEnv();
  const refresh = jar.get(REFRESH_COOKIE)?.value;
  if (!refresh) return { kind: 'no_refresh_token' };

  try {
    const form = new URLSearchParams();
    form.set('grant_type', 'refresh_token');
    form.set('refresh_token', refresh);
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
      const body = (await upstream.json().catch(() => ({}))) as { error?: unknown };
      const oauthError = typeof body.error === 'string' ? body.error : undefined;
      const rotationSuspect = upstream.status === 400 && oauthError === 'invalid_grant';
      logger.warn('refresh_failed', {
        requestId,
        via,
        status: upstream.status,
        error: oauthError,
      });
      return { kind: 'grant_rejected', status: upstream.status, oauthError, rotationSuspect };
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

    // --- Re-exchange operator + re-assume tenant CONCURRENTLY ------------
    // (TASK-PC-FE-120) The operator re-exchange (§ 2.6, admin-service) and the
    // assume-tenant re-exchange (§ 2.7, SAS) are independent RFC 8693 grants —
    // both consume only the rotated base `access_token`, neither feeds the
    // other. They are still awaited in dependency order below: operator is the
    // FATAL gate, assume is non-fatal. exchangeForAssumedToken throws, so on the
    // operator-fail early-return path (where assumePromise is never awaited)
    // attach a no-op handler up-front to avoid an unhandled rejection.
    const activeTenant = jar.get(TENANT_COOKIE)?.value;
    const operatorPromise = exchangeForOperatorToken(data.access_token);
    const assumePromise = activeTenant
      ? exchangeForAssumedToken(data.access_token, activeTenant)
      : null;
    if (assumePromise) void assumePromise.catch(() => {});

    // --- Re-exchange the operator token (§ 2.6 / ADR-MONO-014 D2) ---------
    // No operator-refresh state: the rotated IAM access token is re-exchanged
    // for a fresh operator token. On failure the operator session is dropped
    // here (never a stale operator token, never a GAP-token fallback on the
    // operator boundary); whether the IAM cookies go too is the caller's call.
    try {
      const op = await operatorPromise;
      jar.set(OPERATOR_COOKIE, op.accessToken, {
        ...tokenCookieOpts,
        maxAge: op.expiresIn,
      });
    } catch (err) {
      clearOperatorSession(jar);
      const failClosed =
        err instanceof OperatorExchangeError && err.reason === 'fail_closed';
      logger.warn('refresh_reexchange_failed', {
        requestId,
        via,
        reason: failClosed ? 'fail_closed' : 'unavailable',
      });
      return { kind: failClosed ? 'operator_not_provisioned' : 'operator_unavailable' };
    }

    // --- Re-assume the active tenant (ADR-MONO-020 D4 / § 2.7) ------------
    // The assumed token has NO refresh token (D2) — re-minted from the rotated
    // base token. On failure DROP BOTH the assumed token AND the active tenant
    // (never a stale assumed token). The base IAM + operator session stays valid.
    if (assumePromise) {
      try {
        const assumed = await assumePromise;
        jar.set(ASSUMED_TOKEN_COOKIE, assumed.accessToken, {
          ...tokenCookieOpts,
          maxAge: assumed.expiresIn,
        });
      } catch {
        clearTenantSelection(jar);
        logger.warn('refresh_reassume_failed', { requestId, via });
      }
    } else {
      // --- Re-default the home tenant (TASK-MONO-674) --------------------
      // The callback's home-tenant default carries `maxAge = expires_in`, so it
      // expires TOGETHER with the access token. Without this, an idle-expired
      // session would come back authenticated but tenant-less, and the
      // tenant-scoped overviews would gate with "select a tenant" again (the
      // TASK-PC-FE-036 defect). Same rule as `/api/auth/callback`: the rotated
      // base token is already scoped to the home tenant, so no assume is
      // needed; the platform sentinel '*' has no home tenant → left unset.
      // 🔴 What this CANNOT restore: nothing — a switched (non-home) tenant is
      // set by `/api/tenant` as a session cookie with no maxAge, so it survives
      // idling and takes the re-assume branch above instead.
      const homeTenant = homeTenantFromAccessToken(data.access_token);
      if (homeTenant) {
        jar.set(TENANT_COOKIE, homeTenant, {
          ...tokenCookieOpts,
          maxAge: data.expires_in,
        });
      }
    }

    logger.info('refresh_ok', { requestId, via });
    return { kind: 'ok' };
  } catch (err) {
    logger.error('refresh_error', { requestId, via, err: String(err) });
    return { kind: 'error' };
  }
}
