import { NextResponse } from 'next/server';
import { getServerEnv } from '@/shared/config/env';
import { logger, newRequestId } from '@/shared/lib/logger';

export async function POST(req: Request) {
  const requestId = newRequestId();
  const env = getServerEnv();
  const authHeader = req.headers.get('Authorization') ?? '';
  logger.info('2fa_enroll_attempt', { requestId, hasAuth: !!authHeader, authPrefix: authHeader.substring(0, 20) });

  if (!authHeader) {
    return NextResponse.json(
      { code: 'INVALID_BOOTSTRAP_TOKEN', message: 'Authorization header missing' },
      { status: 401 },
    );
  }

  try {
    const upstream = await fetch(`${env.NEXT_PUBLIC_API_BASE_URL}/api/admin/auth/2fa/enroll`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: authHeader,
        'X-Request-Id': requestId,
      },
    });

    const body = await upstream.json().catch(() => ({}));
    if (!upstream.ok) {
      logger.warn('2fa_enroll_failed', { requestId, status: upstream.status });
      return NextResponse.json(body, { status: upstream.status });
    }

    logger.info('2fa_enroll_success', { requestId });
    return NextResponse.json(body);
  } catch (err) {
    logger.error('2fa_enroll_proxy_error', { requestId, err: String(err) });
    return NextResponse.json(
      { code: 'DOWNSTREAM_ERROR', message: '2FA 등록 서비스 일시 장애' },
      { status: 502 },
    );
  }
}
