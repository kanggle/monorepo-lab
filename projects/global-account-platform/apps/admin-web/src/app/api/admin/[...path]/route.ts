import { NextRequest, NextResponse } from 'next/server';
import { cookies } from 'next/headers';
import { getServerEnv } from '@/shared/config/env';
import { logger, newRequestId } from '@/shared/lib/logger';

type RouteContext = { params: Promise<{ path: string[] }> };

async function proxy(req: NextRequest, ctx: RouteContext): Promise<NextResponse> {
  const requestId = newRequestId();
  const env = getServerEnv();
  const { path } = await ctx.params;
  const upstreamPath = '/api/admin/' + path.join('/');
  const search = req.nextUrl.search;
  const url = `${env.NEXT_PUBLIC_API_BASE_URL}${upstreamPath}${search}`;

  const jar = await cookies();
  const accessToken = jar.get('accessToken')?.value;

  if (!accessToken) {
    return NextResponse.json(
      { code: 'TOKEN_INVALID', message: 'Access token is missing, expired, or has an invalid signature' },
      { status: 401 },
    );
  }

  const headers = new Headers();
  headers.set('Authorization', `Bearer ${accessToken}`);
  headers.set('X-Request-Id', requestId);

  const ct = req.headers.get('Content-Type');
  if (ct) headers.set('Content-Type', ct);

  const idempotencyKey = req.headers.get('Idempotency-Key');
  if (idempotencyKey) headers.set('Idempotency-Key', idempotencyKey);

  const operatorReason = req.headers.get('X-Operator-Reason');
  if (operatorReason) headers.set('X-Operator-Reason', operatorReason);

  let body: string | undefined;
  if (req.method !== 'GET' && req.method !== 'HEAD') {
    body = await req.text();
    if (!body) body = undefined;
  }

  try {
    const upstream = await fetch(url, { method: req.method, headers, body });
    const responseBody = await upstream.text();
    const responseCt = upstream.headers.get('Content-Type') ?? 'application/json';
    logger.info('admin_proxy', { requestId, method: req.method, path: upstreamPath, status: upstream.status });
    return new NextResponse(responseBody, {
      status: upstream.status,
      headers: { 'Content-Type': responseCt },
    });
  } catch (err) {
    logger.error('admin_proxy_error', { requestId, path: upstreamPath, err: String(err) });
    return NextResponse.json(
      { code: 'DOWNSTREAM_ERROR', message: '서비스 일시 장애' },
      { status: 502 },
    );
  }
}

export const GET = proxy;
export const POST = proxy;
export const PUT = proxy;
export const PATCH = proxy;
export const DELETE = proxy;
