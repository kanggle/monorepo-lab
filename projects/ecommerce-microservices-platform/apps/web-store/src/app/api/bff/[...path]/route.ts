import { NextRequest, NextResponse } from 'next/server';
import { getWebStoreSession } from '@/shared/auth/session';
import { resolveUpstreamBaseUrl } from '@/shared/config/demo-backend';

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

// DEMO-RESOLVER-CONSUMER: web-store   (ADR-MONO-068 § D6 = B2 — 구현은 @demo/backend-resolver 하나뿐이다)
//
// 🔴 업스트림 주소를 여기서 **다시 조립하지 않는다.** 예전에는 이 함수가 env 사슬을
//    자기 자리에 적어 뒤고 `shared/config/api.ts` 가 **같은 사슬을 또** 적고 있었다 —
//    한 사실이 두 곳에 있으면 한쪽만 고쳐진다. 이제 둘 다 `demo-backend.ts` 를 쓴다.
//
// 🔵 `dynamic = 'force-dynamic'` 이라 이 핸들러는 요청마다 돈다 ⇒ 해석 결과가 빌드
//    산출물에 박히지 않는다(ADR-MONO-067 D2 가 요구하는 것이 정확히 그것이다).
const backendBaseUrl = resolveUpstreamBaseUrl;

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

async function buildTargetUrl(
  req: NextRequest,
  segments: string[],
): Promise<string> {
  const path = segments.map((s) => encodeURIComponent(s)).join('/');
  const search = req.nextUrl.search ?? '';
  // Backend gateway paths are rooted at `/` (e.g. `/api/orders`). The
  // `[...path]` segments already exclude the `/api/bff` prefix.
  return `${await backendBaseUrl()}/${path}${search}`;
}

async function forward(
  req: NextRequest,
  ctx: { params: Promise<{ path: string[] }> },
): Promise<NextResponse> {
  const { path } = await ctx.params;
  const segments = Array.isArray(path) ? path : [path];

  const session = await getWebStoreSession();
  const targetUrl = await buildTargetUrl(req, segments);

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

  // Null-body statuses (204 No Content, 205 Reset Content, 304 Not Modified) MUST
  // NOT carry a body: per the Fetch spec the `Response` constructor throws a
  // TypeError when given one, and even an empty ArrayBuffer counts as a (non-null)
  // body. Passing it through verbatim therefore turned every backend 204 into a
  // 500 at this proxy (e.g. the push-subscription DELETE). Forward such responses
  // with a null body; carry the arrayBuffer body only for statuses that permit one.
  const isNullBodyStatus =
    backendRes.status === 204 || backendRes.status === 205 || backendRes.status === 304;
  const resBody = isNullBodyStatus ? null : await backendRes.arrayBuffer();
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
