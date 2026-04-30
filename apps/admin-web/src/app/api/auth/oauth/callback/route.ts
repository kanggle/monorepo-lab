import { NextResponse } from 'next/server';
import { cookies } from 'next/headers';
import { z } from 'zod';
import { getServerEnv } from '@/shared/config/env';
import { logger, newRequestId } from '@/shared/lib/logger';

/**
 * OAuth callback BFF endpoint.
 *
 * Forwards `POST /api/auth/oauth/callback` to auth-service and on success sets
 * `accessToken` / `refreshToken` HttpOnly cookies. Mirrors the cookie strategy
 * of `/api/auth/login` so the client never sees JWTs.
 *
 * Spec: specs/features/oauth-social-login.md, specs/contracts/http/auth-api.md.
 */

const SUPPORTED_PROVIDERS = ['google', 'kakao', 'microsoft'] as const;

const BodySchema = z.object({
  provider: z.enum(SUPPORTED_PROVIDERS),
  code: z.string().min(1),
  state: z.string().min(1),
  redirectUri: z.string().url(),
});

const CallbackResponseSchema = z.object({
  accessToken: z.string(),
  refreshToken: z.string(),
  expiresIn: z.number().int().positive(),
  refreshExpiresIn: z.number().int().positive().optional(),
  isNewAccount: z.boolean(),
});

const cookieOpts = {
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
    return NextResponse.json(
      { code: 'VALIDATION_ERROR', message: '요청이 올바르지 않습니다.' },
      { status: 422 },
    );
  }

  try {
    const upstream = await fetch(`${env.NEXT_PUBLIC_API_BASE_URL}/api/auth/oauth/callback`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'X-Request-Id': requestId },
      body: JSON.stringify(body),
    });

    const respBody = await upstream.json().catch(() => ({}));
    if (!upstream.ok) {
      logger.warn('oauth_callback_failed', {
        requestId,
        provider: body.provider,
        status: upstream.status,
        code: (respBody as { code?: string }).code,
      });
      return NextResponse.json(respBody, { status: upstream.status });
    }

    const data = CallbackResponseSchema.parse(respBody);

    const jar = await cookies();
    jar.set('accessToken', data.accessToken, { ...cookieOpts, maxAge: data.expiresIn });
    jar.set('refreshToken', data.refreshToken, {
      ...cookieOpts,
      maxAge: data.refreshExpiresIn ?? 604800,
    });

    logger.info('oauth_callback_success', {
      requestId,
      provider: body.provider,
      isNewAccount: data.isNewAccount,
    });
    return NextResponse.json({ ok: true, isNewAccount: data.isNewAccount });
  } catch (err) {
    logger.error('oauth_callback_proxy_error', { requestId, err: String(err) });
    return NextResponse.json(
      { code: 'PROVIDER_ERROR', message: '소셜 로그인 서비스 일시 장애' },
      { status: 502 },
    );
  }
}
