import { describe, it, expect, vi, beforeEach } from 'vitest';

/**
 * Same-origin ecommerce-ops orders proxy route handlers (TASK-PC-FE-083 —
 * § 2.4.10):
 *   - GET detail proxy: domain-facing IAM OIDC token attached server-side
 *     (NOT the operator token); NO X-Tenant-Id.
 *   - POST status change: same auth model + Zod body parse + 400/422/409
 *     passthrough as inline actionable (no crash).
 *   - bad body (Zod fail) → 422 (no upstream call).
 *   - 401 → no upstream call when the IAM session is absent.
 *   - 503 → 503 (section degrades only).
 */

const cookieJar = new Map<string, string>();
vi.mock('next/headers', () => ({
  cookies: async () => ({
    get: (n: string) =>
      cookieJar.has(n) ? { value: cookieJar.get(n)! } : undefined,
  }),
}));

const { ENV } = vi.hoisted(() => ({
  ENV: {
    OIDC_ISSUER_URL: 'http://iam.local',
    OIDC_CLIENT_ID: 'platform-console-web',
    OIDC_REDIRECT_URI: 'http://console.local/api/auth/callback',
    OIDC_SCOPE: 'openid profile email tenant.read',
    CONSOLE_REGISTRY_URL: 'http://iam.local/api/admin/console/registry',
    REGISTRY_TIMEOUT_MS: 50,
    CONSOLE_TOKEN_EXCHANGE_URL: 'http://iam.local/api/admin/auth/token-exchange',
    TOKEN_EXCHANGE_TIMEOUT_MS: 50,
    IAM_ADMIN_API_BASE: 'http://iam.local',
    ECOMMERCE_ADMIN_BASE_URL: 'http://ecommerce.local/api/admin',
    ECOMMERCE_PUBLIC_BASE_URL: 'http://ecommerce.local/api',
    ECOMMERCE_TIMEOUT_MS: 50,
    LOG_LEVEL: 'info' as const,
    NEXT_PUBLIC_APP_URL: 'http://console.local',
  },
}));
vi.mock('@/shared/config/env', () => ({
  clientEnv: { NEXT_PUBLIC_APP_URL: ENV.NEXT_PUBLIC_APP_URL },
  getServerEnv: () => ENV,
}));

import { GET as orderDetailGET } from '@/app/api/ecommerce/orders/[id]/route';
import { POST as statusPOST } from '@/app/api/ecommerce/orders/[id]/status/route';
import { ACCESS_COOKIE, OPERATOR_COOKIE } from '@/shared/lib/session';

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}
function ecomError(code: string, status: number) {
  return new Response(JSON.stringify({ code, message: 'e', timestamp: 't' }), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

const ORDER_DETAIL = {
  orderId: 'ord-1',
  userId: 'u-1',
  status: 'PENDING',
  totalPrice: 15000,
  items: [],
  shippingAddress: {
    recipient: '홍길동',
    phone: '010-0000-0000',
    zipCode: '12345',
    address1: '서울시',
    address2: '',
  },
  createdAt: '2026-06-14T00:00:00Z',
};

beforeEach(() => {
  cookieJar.clear();
  vi.unstubAllGlobals();
});

describe('GET /api/ecommerce/orders/{id} (detail proxy)', () => {
  it('attaches the domain-facing token (NOT the operator token), NO X-Tenant-Id', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    cookieJar.set(OPERATOR_COOKIE, 'OP-MUST-NOT-USE');
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(ORDER_DETAIL));
    vi.stubGlobal('fetch', fetchMock);

    const res = await orderDetailGET(
      new Request('http://console.local/api/ecommerce/orders/ord-1'),
      { params: Promise.resolve({ id: 'ord-1' }) },
    );
    expect(res.status).toBe(200);

    const init = fetchMock.mock.calls[0][1] as RequestInit;
    const h = init.headers as Record<string, string>;
    expect(h.Authorization).toBe('Bearer GAP-ACCESS');
    expect(h.Authorization).not.toContain('OP-MUST-NOT-USE');
    expect(h['X-Tenant-Id']).toBeUndefined();
  });

  it('no IAM session → 401 (no upstream call)', async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    const res = await orderDetailGET(
      new Request('http://console.local/api/ecommerce/orders/ord-1'),
      { params: Promise.resolve({ id: 'ord-1' }) },
    );
    expect(res.status).toBe(401);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('404 ORDER_NOT_FOUND → 404 passthrough', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(ecomError('ORDER_NOT_FOUND', 404)));
    const res = await orderDetailGET(
      new Request('http://console.local/api/ecommerce/orders/nope'),
      { params: Promise.resolve({ id: 'nope' }) },
    );
    expect(res.status).toBe(404);
    const body = await res.json();
    expect(body.code).toBe('ORDER_NOT_FOUND');
  });

  it('503 → 503 (section degrades only)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(ecomError('SERVICE_UNAVAILABLE', 503)),
    );
    const res = await orderDetailGET(
      new Request('http://console.local/api/ecommerce/orders/ord-1'),
      { params: Promise.resolve({ id: 'ord-1' }) },
    );
    expect(res.status).toBe(503);
  });
});

describe('POST /api/ecommerce/orders/{id}/status (status change proxy)', () => {
  it('attaches the domain-facing token, NO X-Tenant-Id, NO Idempotency-Key', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    cookieJar.set(OPERATOR_COOKIE, 'OP-MUST-NOT-USE');
    const fetchMock = vi
      .fn()
      .mockResolvedValue(jsonResponse({ orderId: 'ord-1', status: 'CONFIRMED' }));
    vi.stubGlobal('fetch', fetchMock);

    const res = await statusPOST(
      new Request('http://console.local/api/ecommerce/orders/ord-1/status', {
        method: 'POST',
        body: JSON.stringify({ status: 'CONFIRMED' }),
        headers: { 'Content-Type': 'application/json' },
      }),
      { params: Promise.resolve({ id: 'ord-1' }) },
    );
    expect(res.status).toBe(200);
    const data = await res.json();
    expect(data.orderId).toBe('ord-1');
    expect(data.status).toBe('CONFIRMED');

    const init = fetchMock.mock.calls[0][1] as RequestInit;
    const h = init.headers as Record<string, string>;
    expect(h.Authorization).toBe('Bearer GAP-ACCESS');
    expect(h.Authorization).not.toContain('OP-MUST-NOT-USE');
    expect(h['X-Tenant-Id']).toBeUndefined();
    expect(h['Idempotency-Key']).toBeUndefined();
  });

  it('an invalid body (Zod fail — missing status) → 422 (no upstream call)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    const res = await statusPOST(
      new Request('http://console.local/api/ecommerce/orders/ord-1/status', {
        method: 'POST',
        body: JSON.stringify({}), // missing status
        headers: { 'Content-Type': 'application/json' },
      }),
      { params: Promise.resolve({ id: 'ord-1' }) },
    );
    expect(res.status).toBe(422);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('no IAM session → 401 (no upstream call)', async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    const res = await statusPOST(
      new Request('http://console.local/api/ecommerce/orders/ord-1/status', {
        method: 'POST',
        body: JSON.stringify({ status: 'CONFIRMED' }),
        headers: { 'Content-Type': 'application/json' },
      }),
      { params: Promise.resolve({ id: 'ord-1' }) },
    );
    expect(res.status).toBe(401);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('400 InvalidOrder (invalid forward transition) → 400 passthrough (inline)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(ecomError('INVALID_ORDER', 400)),
    );
    const res = await statusPOST(
      new Request('http://console.local/api/ecommerce/orders/ord-1/status', {
        method: 'POST',
        body: JSON.stringify({ status: 'SHIPPED' }), // PENDING → SHIPPED invalid
        headers: { 'Content-Type': 'application/json' },
      }),
      { params: Promise.resolve({ id: 'ord-1' }) },
    );
    expect(res.status).toBe(400);
    const body = await res.json();
    expect(body.code).toBe('INVALID_ORDER');
  });

  it('422 OrderCannotBeCancelled → 422 passthrough (inline)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(ecomError('ORDER_CANNOT_BE_CANCELLED', 422)),
    );
    const res = await statusPOST(
      new Request('http://console.local/api/ecommerce/orders/ord-1/status', {
        method: 'POST',
        body: JSON.stringify({ status: 'CANCELLED' }),
        headers: { 'Content-Type': 'application/json' },
      }),
      { params: Promise.resolve({ id: 'ord-1' }) },
    );
    expect(res.status).toBe(422);
    const body = await res.json();
    expect(body.code).toBe('ORDER_CANNOT_BE_CANCELLED');
  });

  it('409 CONFLICT (optimistic lock) → 409 passthrough (refetch + retry)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(ecomError('CONFLICT', 409)),
    );
    const res = await statusPOST(
      new Request('http://console.local/api/ecommerce/orders/ord-1/status', {
        method: 'POST',
        body: JSON.stringify({ status: 'CONFIRMED' }),
        headers: { 'Content-Type': 'application/json' },
      }),
      { params: Promise.resolve({ id: 'ord-1' }) },
    );
    expect(res.status).toBe(409);
    const body = await res.json();
    expect(body.code).toBe('CONFLICT');
  });

  it('503 → 503 (section degrades only)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(ecomError('SERVICE_UNAVAILABLE', 503)),
    );
    const res = await statusPOST(
      new Request('http://console.local/api/ecommerce/orders/ord-1/status', {
        method: 'POST',
        body: JSON.stringify({ status: 'CONFIRMED' }),
        headers: { 'Content-Type': 'application/json' },
      }),
      { params: Promise.resolve({ id: 'ord-1' }) },
    );
    expect(res.status).toBe(503);
  });
});
