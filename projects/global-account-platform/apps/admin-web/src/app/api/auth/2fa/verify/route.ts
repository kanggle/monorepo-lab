import { NextResponse } from 'next/server';
import { z } from 'zod';
import { getServerEnv } from '@/shared/config/env';
import { logger, newRequestId } from '@/shared/lib/logger';

const BodySchema = z.object({
  totpCode: z.string().length(6),
});

export async function POST(req: Request) {
  const requestId = newRequestId();
  const env = getServerEnv();
  const authHeader = req.headers.get('Authorization') ?? '';

  let body: z.infer<typeof BodySchema>;
  try {
    body = BodySchema.parse(await req.json());
  } catch {
    return NextResponse.json({ code: 'VALIDATION_ERROR', message: 'totpCode는 6자리 숫자입니다.' }, { status: 422 });
  }

  try {
    const upstream = await fetch(`${env.NEXT_PUBLIC_API_BASE_URL}/api/admin/auth/2fa/verify`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: authHeader,
        'X-Request-Id': requestId,
      },
      body: JSON.stringify(body),
    });

    const respBody = await upstream.json().catch(() => ({}));
    if (!upstream.ok) {
      logger.warn('2fa_verify_failed', { requestId, status: upstream.status });
      return NextResponse.json(respBody, { status: upstream.status });
    }

    logger.info('2fa_verify_success', { requestId });
    return NextResponse.json(respBody);
  } catch (err) {
    logger.error('2fa_verify_proxy_error', { requestId, err: String(err) });
    return NextResponse.json(
      { code: 'DOWNSTREAM_ERROR', message: '2FA 검증 서비스 일시 장애' },
      { status: 502 },
    );
  }
}
