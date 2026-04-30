import { NextResponse } from 'next/server';
import { cookies } from 'next/headers';
import { z } from 'zod';
import { getServerEnv } from '@/shared/config/env';
import { logger, newRequestId } from '@/shared/lib/logger';

const RefreshResponseSchema = z.object({
  accessToken: z.string(),
  refreshToken: z.string(),
  expiresIn: z.number().int().positive(),
});

const cookieOpts = {
  httpOnly: true,
  secure: true,
  sameSite: 'strict' as const,
  path: '/',
};

export async function POST() {
  const requestId = newRequestId();
  const env = getServerEnv();
  const jar = await cookies();
  const refresh = jar.get('refreshToken')?.value;
  if (!refresh) {
    return NextResponse.json({ code: 'TOKEN_INVALID', message: 'refresh token missing' }, { status: 401 });
  }

  try {
    const upstream = await fetch(`${env.NEXT_PUBLIC_API_BASE_URL}/api/admin/auth/refresh`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'X-Request-Id': requestId },
      body: JSON.stringify({ refreshToken: refresh }),
    });
    if (!upstream.ok) {
      jar.delete('accessToken');
      jar.delete('refreshToken');
      logger.warn('refresh_failed', { requestId, status: upstream.status });
      return NextResponse.json({ code: 'TOKEN_INVALID', message: 'refresh failed' }, { status: 401 });
    }
    const data = RefreshResponseSchema.parse(await upstream.json());
    jar.set('accessToken', data.accessToken, { ...cookieOpts, maxAge: data.expiresIn });
    jar.set('refreshToken', data.refreshToken, { ...cookieOpts, maxAge: 60 * 60 * 24 * 14 });
    return NextResponse.json({ ok: true });
  } catch (err) {
    logger.error('refresh_proxy_error', { requestId, err: String(err) });
    return NextResponse.json({ code: 'DOWNSTREAM_ERROR', message: 'refresh proxy error' }, { status: 502 });
  }
}
