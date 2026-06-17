import { NextRequest, NextResponse } from 'next/server';
import { getWebStoreSession } from '@/shared/auth/session';

export const runtime = 'nodejs';
export const dynamic = 'force-dynamic';

/**
 * Same-origin BFF proxy (Phase 4.5 F2 — token confidentiality).
 *
 * Client components call `/api/bff/<gateway-path>` (e.g. `/api/bff/api/orders`)
 * via the shared axios client. This handler reads the OIDC access token from
 * the encrypted server-side NextAuth JWT (`getWebStoreSession()` → `getToken`,
 * never exposed to client JS), attaches it as `Authorization: Bearer ...`
 * SERVER-SIDE, and forwards the request to the backend gateway. The bearer
 * therefore never reaches the browser.
 *
 * Silent-refresh interplay (F3): the access token is refreshed in the NextAuth
 * `jwt` callback (proactively near expiry; the BFF reads the freshest token on
 * every call). If the backend still returns 401 (token rejected / refresh
 * already failed), the proxy responds with `401 { error: 'reauth_required' }`
 * and an `X-Reauth: 1` header so the client axios `onAuthError` redirects to a
 * full re-auth (F1), preserving the return-to.
 *
 * Server Components / Server Actions do NOT use this proxy — they read
 * `getWebStoreSession()` directly and call the gateway server-side.
 */

function backendBaseUrl(): string {
  return (
    process.env.API_URL_INTERNAL ??
    process.env.NEXT_PUBLIC_API_URL ??
    'http://localhost:8080'
  );
}

// Hop-by-hop / host-specific headers that must not be forwarded verbatim.
const STRIPPED_REQUEST_HEADERS = new Set([
  'host',
  'connection',
  'content-length',
  'authorization', // re-attached server-side from the session
  'cookie', // never forward the NextAuth session cookie to the backend
]);

const STRIPPED_RESPONSE_HEADERS = new Set([
  'content-encoding',
  'content-length',
  'transfer-encoding',
  'connection',
]);

function buildTargetUrl(req: NextRequest, segments: string[]): string {
  const path = segments.map((s) => encodeURIComponent(s)).join('/');
  const search = req.nextUrl.search ?? '';
  // Backend gateway paths are rooted at `/` (e.g. `/api/orders`). The
  // `[...path]` segments already exclude the `/api/bff` prefix.
  return `${backendBaseUrl()}/${path}${search}`;
}

async function forward(
  req: NextRequest,
  ctx: { params: Promise<{ path: string[] }> },
): Promise<NextResponse> {
  const { path } = await ctx.params;
  const segments = Array.isArray(path) ? path : [path];

  const session = await getWebStoreSession();
  const targetUrl = buildTargetUrl(req, segments);

  const headers = new Headers();
  req.headers.forEach((value, key) => {
    if (!STRIPPED_REQUEST_HEADERS.has(key.toLowerCase())) {
      headers.set(key, value);
    }
  });
  if (session.accessToken) {
    headers.set('Authorization', `Bearer ${session.accessToken}`);
  }

  const method = req.method.toUpperCase();
  const hasBody = method !== 'GET' && method !== 'HEAD';
  const body = hasBody ? await req.arrayBuffer() : undefined;

  let backendRes: Response;
  try {
    backendRes = await fetch(targetUrl, {
      method,
      headers,
      body: body && body.byteLength > 0 ? body : undefined,
      redirect: 'manual',
      cache: 'no-store',
    });
  } catch {
    return NextResponse.json(
      { code: 'BFF_UPSTREAM_ERROR', message: 'Upstream request failed' },
      { status: 502 },
    );
  }

  // 401 from the backend → token rejected. Signal the client to re-auth (F1).
  if (backendRes.status === 401) {
    return NextResponse.json(
      { code: 'REAUTH_REQUIRED', message: 'Re-authentication required' },
      { status: 401, headers: { 'X-Reauth': '1' } },
    );
  }

  const resHeaders = new Headers();
  backendRes.headers.forEach((value, key) => {
    if (!STRIPPED_RESPONSE_HEADERS.has(key.toLowerCase())) {
      resHeaders.set(key, value);
    }
  });

  const resBody = await backendRes.arrayBuffer();
  return new NextResponse(resBody, {
    status: backendRes.status,
    statusText: backendRes.statusText,
    headers: resHeaders,
  });
}

export const GET = forward;
export const POST = forward;
export const PUT = forward;
export const PATCH = forward;
export const DELETE = forward;
