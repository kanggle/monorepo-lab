import { NextResponse } from 'next/server';
import { cookies } from 'next/headers';
import { getServerEnv } from '@/shared/config/env';
import { logger, newRequestId } from '@/shared/lib/logger';

export async function POST() {
  const requestId = newRequestId();
  const env = getServerEnv();
  const jar = await cookies();
  const access = jar.get('accessToken')?.value;
  const refresh = jar.get('refreshToken')?.value;

  if (access && refresh) {
    try {
      await fetch(`${env.NEXT_PUBLIC_API_BASE_URL}/api/admin/auth/logout`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${access}`,
          'X-Request-Id': requestId,
        },
        body: JSON.stringify({ refreshToken: refresh }),
      });
    } catch (err) {
      logger.warn('logout_upstream_failed', { requestId, err: String(err) });
      // Still clear cookies below.
    }
  }

  jar.delete('accessToken');
  jar.delete('refreshToken');
  logger.info('logout_complete', { requestId });
  return new NextResponse(null, { status: 204 });
}
