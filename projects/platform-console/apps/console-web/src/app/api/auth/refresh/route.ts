import { NextResponse } from 'next/server';
import { cookies } from 'next/headers';
import { z } from 'zod';
import { getServerEnv } from '@/shared/config/env';
import {
  ACCESS_COOKIE,
  REFRESH_COOKIE,
  tokenCookieOpts,
} from '@/shared/lib/session';
import { logger, newRequestId } from '@/shared/lib/logger';

export const runtime = 'nodejs';

/**
 * Server-route token refresh (frontend-app.md § Authentication: refresh handled
 * by a server route, not client JS).
 *
 * Public-client refresh_token grant against
 * `${OIDC_ISSUER_URL}/oauth2/token` (auth-api.md § POST /oauth2/token;
 * `grant_type=refresh_token` + `client_id`, no secret). V0015 seeds
 * `settings.token.reuse-refresh-tokens=false` → GAP rotates the refresh token
 * on every call (ADR-003), so the rotated token is re-stored.
 *
 * On failure both token cookies are cleared so the API client falls back to
 * a clean re-login (no client-side token juggling — task Failure Scenario).
 */

const RefreshResponseSchema = z.object({
  access_token: z.string().min(1),
  token_type: z.string(),
  expires_in: z.number().int().positive(),
  refresh_token: z.string().min(1).optional(),
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
