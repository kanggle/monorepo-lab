import { NextResponse } from 'next/server';
import { cookies } from 'next/headers';
import { z } from 'zod';
import { getServerEnv } from '@/shared/config/env';
import { logger, newRequestId } from '@/shared/lib/logger';

const BodySchema = z.object({
  operatorId: z.string().min(1),
  password: z.string().min(8),
  totpCode: z.preprocess(
    (v) => (typeof v === 'string' && v.length === 0 ? undefined : v),
    z.string().length(6).optional(),
  ),
});

const TokenResponseSchema = z.object({
  accessToken: z.string(),
  refreshToken: z.string(),
  expiresIn: z.number().int().positive(),
  refreshExpiresIn: z.number().int().positive().optional(),
});

const accessCookieOpts = {
  httpOnly: true,
  secure: true,
  sameSite: 'strict' as const,
  path: '/',
};

export async function POST(req: Request) {
  const requestId = newRequestId();
  const env = getServerEnv();

  let body: z.infer<typeof BodySchema>;
  try {
    body = BodySchema.parse(await req.json());
  } catch {
    return NextResponse.json({ code: 'VALIDATION_ERROR', message: '요청이 올바르지 않습니다.' }, { status: 422 });
  }

  try {
    const upstream = await fetch(`${env.NEXT_PUBLIC_API_BASE_URL}/api/admin/auth/login`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'X-Request-Id': requestId },
      body: JSON.stringify(Object.fromEntries(Object.entries(body).filter(([, v]) => v !== undefined))),
    });

    if (!upstream.ok) {
      const errBody = await upstream.json().catch(() => ({ code: 'UNKNOWN', message: '로그인 실패' }));

      // 2FA enrollment required — pass bootstrapToken through to the client
      if (upstream.status === 401 && errBody.code === 'ENROLLMENT_REQUIRED' && errBody.bootstrapToken) {
        logger.info('login_enrollment_required', { requestId });
        return NextResponse.json(
          { code: 'ENROLLMENT_REQUIRED', message: errBody.message, bootstrapToken: errBody.bootstrapToken },
          { status: 401 },
        );
      }

      logger.warn('login_failed', { requestId, status: upstream.status, code: errBody.code });
      return NextResponse.json(errBody, { status: upstream.status });
    }

    const data = TokenResponseSchema.parse(await upstream.json());

    const jar = await cookies();
    jar.set('accessToken', data.accessToken, { ...accessCookieOpts, maxAge: data.expiresIn });
    jar.set('refreshToken', data.refreshToken, { ...accessCookieOpts, maxAge: data.refreshExpiresIn ?? 60 * 60 * 24 * 14 });

    logger.info('login_success', { requestId });
    return NextResponse.json({ ok: true });
  } catch (err) {
    logger.error('login_proxy_error', { requestId, err: String(err) });
    return NextResponse.json(
      { code: 'DOWNSTREAM_ERROR', message: '로그인 서비스 일시 장애' },
      { status: 502 },
    );
  }
}
