import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';

/**
 * TASK-FE-075 / Phase 4.5 F2 — same-origin BFF proxy.
 *
 * The proxy must attach the OIDC bearer SERVER-SIDE (read from the server-only
 * session helper), strip client cookies / inbound Authorization, and translate
 * a backend 401 into a re-auth signal (F1/F3 fallback).
 */

const getWebStoreSession = vi.fn();
vi.mock('@/shared/auth/session', () => ({
  getWebStoreSession: () => getWebStoreSession(),
}));

// next/server's NextRequest/NextResponse work under the node test env. Import
// the route AFTER mocks are registered.
import { NextRequest } from 'next/server';
import { GET, POST, DELETE } from '@/app/api/bff/[...path]/route';

function makeCtx(segments: string[]) {
  return { params: Promise.resolve({ path: segments }) };
}

describe('BFF proxy — F2 server-side bearer attach', () => {
  const fetchMock = vi.fn();

  beforeEach(() => {
    vi.stubGlobal('fetch', fetchMock);
    fetchMock.mockReset();
    getWebStoreSession.mockReset();
    process.env.API_URL_INTERNAL = 'http://gateway-service:8080';
  });
  afterEach(() => {
    vi.unstubAllGlobals();
    delete process.env.API_URL_INTERNAL;
  });

  it('세션 토큰을 Authorization: Bearer 로 서버측에서 첨부하고 백엔드 게이트웨이로 포워딩', async () => {
    getWebStoreSession.mockResolvedValue({
      accessToken: 'server-only-bearer',
      accountId: 'acc-1',
      tenantId: 'ecommerce',
      roles: ['CUSTOMER'],
    });
    fetchMock.mockResolvedValue(
      new Response(JSON.stringify({ ok: true }), {
        status: 200,
        headers: { 'content-type': 'application/json' },
      }),
    );

    const req = new NextRequest('http://localhost:3000/api/bff/api/orders?page=0', {
      method: 'GET',
      // A client cookie that must NOT be forwarded to the backend.
      headers: { cookie: 'authjs.session-token=abc', authorization: 'Bearer client-forged' },
    });

    const res = await GET(req, makeCtx(['api', 'orders']));
    expect(res.status).toBe(200);

    const [url, init] = fetchMock.mock.calls[0];
    expect(String(url)).toBe('http://gateway-service:8080/api/orders?page=0');
    // Bearer is the server session token, not the client-forged one.
    expect(init.headers.get('Authorization')).toBe('Bearer server-only-bearer');
    // Client cookie is stripped.
    expect(init.headers.get('cookie')).toBeNull();
  });

  it('세션 토큰이 없으면 Authorization 헤더를 첨부하지 않는다', async () => {
    getWebStoreSession.mockResolvedValue({
      accessToken: null,
      accountId: null,
      tenantId: null,
      roles: [],
    });
    fetchMock.mockResolvedValue(new Response('[]', { status: 200 }));

    const req = new NextRequest('http://localhost:3000/api/bff/api/products', { method: 'GET' });
    await GET(req, makeCtx(['api', 'products']));

    const [, init] = fetchMock.mock.calls[0];
    expect(init.headers.get('Authorization')).toBeNull();
  });

  it('POST 본문을 백엔드로 전달한다', async () => {
    getWebStoreSession.mockResolvedValue({
      accessToken: 'tok',
      accountId: 'a',
      tenantId: 't',
      roles: ['CUSTOMER'],
    });
    fetchMock.mockResolvedValue(new Response('{}', { status: 201 }));

    const req = new NextRequest('http://localhost:3000/api/bff/api/orders', {
      method: 'POST',
      headers: { 'content-type': 'application/json' },
      body: JSON.stringify({ productId: 'p1' }),
    });
    const res = await POST(req, makeCtx(['api', 'orders']));
    expect(res.status).toBe(201);

    const [, init] = fetchMock.mock.calls[0];
    expect(init.method).toBe('POST');
    expect(init.body).toBeDefined();
  });

  it('백엔드 401 → 401 + X-Reauth 시그널 (F1/F3 fallback)', async () => {
    getWebStoreSession.mockResolvedValue({
      accessToken: 'expired',
      accountId: 'a',
      tenantId: 't',
      roles: ['CUSTOMER'],
    });
    fetchMock.mockResolvedValue(new Response('unauthorized', { status: 401 }));

    const req = new NextRequest('http://localhost:3000/api/bff/api/orders', { method: 'GET' });
    const res = await GET(req, makeCtx(['api', 'orders']));

    expect(res.status).toBe(401);
    expect(res.headers.get('X-Reauth')).toBe('1');
    const body = (await res.json()) as { code: string };
    expect(body.code).toBe('REAUTH_REQUIRED');
  });

  it('백엔드 204 No Content → 예외 없이 204 로 통과(본문 없음) [FE-083-fix-002]', async () => {
    getWebStoreSession.mockResolvedValue({
      accessToken: 'tok',
      accountId: 'a',
      tenantId: 't',
      roles: ['CUSTOMER'],
    });
    // A null-body status: a body here would make the Response constructor throw.
    fetchMock.mockResolvedValue(new Response(null, { status: 204 }));

    const req = new NextRequest('http://localhost:3000/api/bff/api/notifications/me/push-subscriptions', {
      method: 'DELETE',
      headers: { 'content-type': 'application/json' },
      body: JSON.stringify({ endpoint: 'https://fcm.example/abc' }),
    });

    // Before the fix this rejected with `TypeError: Response constructor: Invalid
    // response status code 204` and Next translated it to a 500.
    const res = await DELETE(req, makeCtx(['api', 'notifications', 'me', 'push-subscriptions']));
    expect(res.status).toBe(204);
    expect(await res.text()).toBe('');
  });

  it('백엔드 304 Not Modified → 예외 없이 304 로 통과 [FE-083-fix-002]', async () => {
    getWebStoreSession.mockResolvedValue({
      accessToken: 'tok',
      accountId: 'a',
      tenantId: 't',
      roles: ['CUSTOMER'],
    });
    fetchMock.mockResolvedValue(new Response(null, { status: 304 }));

    const req = new NextRequest('http://localhost:3000/api/bff/api/products', { method: 'GET' });
    const res = await GET(req, makeCtx(['api', 'products']));
    expect(res.status).toBe(304);
  });

  it('본문 있는 응답(200)은 본문·status 를 그대로 통과 [FE-083-fix-002 회귀]', async () => {
    getWebStoreSession.mockResolvedValue({
      accessToken: 'tok',
      accountId: 'a',
      tenantId: 't',
      roles: ['CUSTOMER'],
    });
    fetchMock.mockResolvedValue(
      new Response(JSON.stringify({ ok: true }), {
        status: 200,
        headers: { 'content-type': 'application/json' },
      }),
    );

    const req = new NextRequest('http://localhost:3000/api/bff/api/orders', { method: 'GET' });
    const res = await GET(req, makeCtx(['api', 'orders']));
    expect(res.status).toBe(200);
    expect(await res.json()).toEqual({ ok: true });
  });

  it('업스트림 네트워크 실패 → 502', async () => {
    getWebStoreSession.mockResolvedValue({
      accessToken: 'tok',
      accountId: 'a',
      tenantId: 't',
      roles: ['CUSTOMER'],
    });
    fetchMock.mockRejectedValue(new Error('ECONNREFUSED'));

    const req = new NextRequest('http://localhost:3000/api/bff/api/orders', { method: 'GET' });
    const res = await GET(req, makeCtx(['api', 'orders']));
    expect(res.status).toBe(502);
  });
});
