import { NextResponse } from 'next/server';
import { z } from 'zod';
import { getServerEnv } from '@/shared/config/env';
import { logger, newRequestId } from '@/shared/lib/logger';

/**
 * OAuth authorize BFF endpoint.
 *
 * Forwards `GET /api/auth/oauth/authorize?provider=&redirectUri=` to auth-service
 * and returns `{ authorizationUrl, state }` to the client. The client uses
 * `authorizationUrl` to redirect the browser to the provider's consent screen.
 *
 * `state` is generated and stored in Redis by auth-service (BFF pattern). The
 * client never persists state to localStorage.
 *
 * Spec: specs/features/oauth-social-login.md, specs/contracts/http/auth-api.md.
 */

const SUPPORTED_PROVIDERS = ['google', 'kakao', 'microsoft'] as const;
const ProviderSchema = z.enum(SUPPORTED_PROVIDERS);

const AuthorizeResponseSchema = z.object({
  authorizationUrl: z.string().url(),
  state: z.string().min(1),
});

export async function GET(req: Request) {
  const requestId = newRequestId();
  const env = getServerEnv();
  const { searchParams } = new URL(req.url);

  const providerParam = searchParams.get('provider');
  const redirectUri = searchParams.get('redirectUri');

  const providerResult = ProviderSchema.safeParse(providerParam);
  if (!providerResult.success) {
    return NextResponse.json(
      { code: 'UNSUPPORTED_PROVIDER', message: '지원하지 않는 provider입니다.' },
      { status: 400 },
    );
  }
  if (!redirectUri) {
    return NextResponse.json(
      { code: 'VALIDATION_ERROR', message: 'redirectUri는 필수입니다.' },
      { status: 422 },
    );
  }

  const upstreamUrl = new URL(`${env.NEXT_PUBLIC_API_BASE_URL}/api/auth/oauth/authorize`);
  upstreamUrl.searchParams.set('provider', providerResult.data);
  upstreamUrl.searchParams.set('redirectUri', redirectUri);

  try {
    const upstream = await fetch(upstreamUrl.toString(), {
      method: 'GET',
      headers: { Accept: 'application/json', 'X-Request-Id': requestId },
    });

    const respBody = await upstream.json().catch(() => ({}));
    if (!upstream.ok) {
      logger.warn('oauth_authorize_failed', {
        requestId,
        provider: providerResult.data,
        status: upstream.status,
        code: (respBody as { code?: string }).code,
      });
      return NextResponse.json(respBody, { status: upstream.status });
    }

    const data = AuthorizeResponseSchema.parse(respBody);
    logger.info('oauth_authorize_success', { requestId, provider: providerResult.data });
    return NextResponse.json(data);
  } catch (err) {
    logger.error('oauth_authorize_proxy_error', { requestId, err: String(err) });
    return NextResponse.json(
      { code: 'PROVIDER_ERROR', message: '소셜 로그인 서비스 일시 장애' },
      { status: 502 },
    );
  }
}
