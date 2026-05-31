import { NextResponse } from 'next/server';
import { cookies } from 'next/headers';
import { getServerEnv } from '@/shared/config/env';
import {
  ACCESS_COOKIE,
  REFRESH_COOKIE,
  OPERATOR_COOKIE,
  TENANT_COOKIE,
  ASSUMED_TOKEN_COOKIE,
} from '@/shared/lib/session';
import { logger, newRequestId } from '@/shared/lib/logger';

export const runtime = 'nodejs';

/**
 * Logout: best-effort GAP token revocation (RFC 7009,
 * auth-api.md § POST /oauth2/revoke — public client passes `client_id`,
 * no secret) then unconditionally clears all session cookies.
 *
 * Cookie clearing is the source of truth for "logged out" — even if GAP
 * revoke fails the operator's console session ends.
 */
export async function POST() {
  const requestId = newRequestId();
  const env = getServerEnv();
  const jar = await cookies();
  const access = jar.get(ACCESS_COOKIE)?.value;
  const refresh = jar.get(REFRESH_COOKIE)?.value;

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
  logger.info('logout_complete', { requestId });
  return new NextResponse(null, { status: 204 });
}
