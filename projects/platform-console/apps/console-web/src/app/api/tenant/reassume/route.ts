import { NextResponse } from 'next/server';
import { cookies } from 'next/headers';
import { getServerEnv, publicOrigin } from '@/shared/config/env';
import {
  ACCESS_COOKIE,
  ASSUMED_TOKEN_COOKIE,
  TENANT_COOKIE,
  tokenCookieOpts,
  assumedTokenCookieMaxAge,
  clearTenantSelection,
} from '@/shared/lib/session';
import { exchangeForAssumedToken } from '@/shared/lib/assume-tenant-exchange';
import {
  buildSessionRefreshRedirectFor,
  resolveRefreshReturnPath,
} from '@/shared/lib/login-redirect';
import { logger, newRequestId } from '@/shared/lib/logger';

export const runtime = 'nodejs';

/**
 * TASK-PC-FE-338 slice c-1 fix — re-assume the SELECTED tenant from the current base IAM token
 * (console contract § 2.7 «Assumed-token TTL becomes 300 s»: «re-assume the active tenant before
 * the assumed token's expiry … from the current base token … drop to «테넌트를 먼저 선택하세요» only
 * if the re-assume itself is refused»).
 *
 * Reached from the `(console)` layout and the domain section gate when {@link needsReassume}: a
 * tenant is selected, its assumed cookie has lapsed, the base token is alive. From P3 the assumed
 * token lives 300 s against the base token's 1800 s, so this is the normal path every few minutes.
 *
 * 🔴 Why not the refresh hop (the first c-1 cut did that): a full IAM refresh rotates the refresh
 * token for no reason, and its rotation-race retry decides «complete session» — a selected tenant
 * without its assumed token is neither, so the retry ended in `session_expired`. The base token is
 * still valid here; only the assumed token needs minting.
 *
 * Outcomes — every one returns to the page, none can loop (each either sets the assumed cookie or
 * removes the tenant, and {@link needsReassume} is then false):
 *   - minted → the assumed cookie (lapsing 60 s before the token) → the page;
 *   - refused (denied · invalid · second factor required) or unavailable → the tenant selection is
 *     dropped → the page shows «테넌트를 먼저 선택하세요» (fail-closed: never the base token for a
 *     selected tenant, never a retry loop on an outage);
 *   - no base token / no tenant → the refresh hop / the page (nothing to re-assume from / for).
 */
export async function GET(req: Request): Promise<NextResponse> {
  const requestId = newRequestId();
  const jar = await cookies();
  const target = resolveRefreshReturnPath(new URL(req.url).searchParams.get('redirect'));
  const origin = publicOrigin(getServerEnv());
  const to = (path: string) => NextResponse.redirect(new URL(path, origin).toString());

  const tenant = jar.get(TENANT_COOKIE)?.value;
  if (!tenant || jar.get(ASSUMED_TOKEN_COOKIE)?.value) return to(target);

  const baseToken = jar.get(ACCESS_COOKIE)?.value ?? null;
  if (!baseToken) return to(buildSessionRefreshRedirectFor(target));

  try {
    const assumed = await exchangeForAssumedToken(baseToken, tenant);
    jar.set(ASSUMED_TOKEN_COOKIE, assumed.accessToken, {
      ...tokenCookieOpts,
      maxAge: assumedTokenCookieMaxAge(assumed.expiresIn),
    });
    logger.info('tenant_reassumed', { requestId, tenant });
  } catch (err) {
    clearTenantSelection(jar);
    logger.warn('tenant_reassume_refused', {
      requestId,
      tenant,
      reason: err instanceof Error ? err.name : 'unknown',
    });
  }
  return to(target);
}
