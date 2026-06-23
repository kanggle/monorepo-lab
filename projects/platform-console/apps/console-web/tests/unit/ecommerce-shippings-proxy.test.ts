import { describe, it, expect, vi, beforeEach } from 'vitest';

/**
 * Same-origin ecommerce-ops shippings proxy route handlers
 * (TASK-PC-FE-088 — § 2.4.10.3):
 *   - GET list: domain-facing IAM OIDC token attached server-side
 *     (NOT the operator token); NO X-Tenant-Id.
 *   - PUT status: same auth model + Zod body parse + 400/409/422 passthrough.
 *   - POST refresh-tracking: same auth model, empty body, best-effort.
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

import { GET as listGET } from '@/app/api/ecommerce/shippings/route';
import { PUT as statusPUT } from '@/app/api/ecommerce/shippings/[id]/status/route';
import { POST as refreshPOST } from '@/app/api/ecommerce/shippings/[id]/refresh-tracking/route';
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

const SHIPPING_LIST = {
  content: [],
  page: 0,
  size: 20,
  totalElements: 0,
};

// Mutation upstream response = the producer's 3-field UpdateShippingStatusResponse
// projection (NO orderId/createdAt) — the real wire shape (TASK-PC-FE-129).
const SHIPPING = {
  shippingId: 'ship-1',
  status: 'SHIPPED',
  updatedAt: '2026-06-14T01:00:00Z',
};

beforeEach(() => {
  cookieJar.clear();
  vi.unstubAllGlobals();
});

describe('GET /api/ecommerce/shippings (list proxy)', () => {
  it('attaches the domain-facing token (NOT the operator token), NO X-Tenant-Id', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    cookieJar.set(OPERATOR_COOKIE, 'OP-MUST-NOT-USE');
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(SHIPPING_LIST));
    vi.stubGlobal('fetch', fetchMock);

    const res = await listGET(
      new Request('http://console.local/api/ecommerce/shippings?page=0&size=20'),
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
    const res = await listGET(
      new Request('http://console.local/api/ecommerce/shippings'),
    );
    expect(res.status).toBe(401);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('503 → 503 (section degrades only)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(ecomError('SERVICE_UNAVAILABLE', 503)),
    );
    const res = await listGET(
      new Request('http://console.local/api/ecommerce/shippings'),
    );
    expect(res.status).toBe(503);
  });

  it('passes through status filter to the upstream', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(SHIPPING_LIST));
    vi.stubGlobal('fetch', fetchMock);
    await listGET(
      new Request('http://console.local/api/ecommerce/shippings?status=PREPARING&page=0&size=20'),
    );
    const [url] = fetchMock.mock.calls[0];
    const u = new URL(String(url));
    expect(u.searchParams.get('status')).toBe('PREPARING');
  });
});

describe('PUT /api/ecommerce/shippings/{id}/status (status update proxy)', () => {
  it('attaches the domain-facing token, NO X-Tenant-Id, NO Idempotency-Key', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    cookieJar.set(OPERATOR_COOKIE, 'OP-MUST-NOT-USE');
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(SHIPPING));
    vi.stubGlobal('fetch', fetchMock);

    const res = await statusPUT(
      new Request('http://console.local/api/ecommerce/shippings/ship-1/status', {
        method: 'PUT',
        body: JSON.stringify({
          status: 'SHIPPED',
          carrier: 'CJ대한통운',
          trackingNumber: 'TRK-001',
        }),
        headers: { 'Content-Type': 'application/json' },
      }),
      { params: Promise.resolve({ id: 'ship-1' }) },
    );
    expect(res.status).toBe(200);
    const data = await res.json();
    expect(data.shippingId).toBe('ship-1');

    const init = fetchMock.mock.calls[0][1] as RequestInit;
    const h = init.headers as Record<string, string>;
    expect(h.Authorization).toBe('Bearer GAP-ACCESS');
    expect(h.Authorization).not.toContain('OP-MUST-NOT-USE');
    expect(h['X-Tenant-Id']).toBeUndefined();
    expect(h['Idempotency-Key']).toBeUndefined();
  });

  it('uses PUT method (not POST) to the upstream', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(SHIPPING));
    vi.stubGlobal('fetch', fetchMock);
    await statusPUT(
      new Request('http://console.local/api/ecommerce/shippings/ship-1/status', {
        method: 'PUT',
        body: JSON.stringify({ status: 'IN_TRANSIT' }),
        headers: { 'Content-Type': 'application/json' },
      }),
      { params: Promise.resolve({ id: 'ship-1' }) },
    );
    const [url, init] = fetchMock.mock.calls[0];
    expect(String(url)).toBe('http://ecommerce.local/api/shippings/ship-1/status');
    expect((init as RequestInit).method).toBe('PUT');
  });

  it('an invalid body (Zod fail — missing status) → 422 (no upstream call)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    const res = await statusPUT(
      new Request('http://console.local/api/ecommerce/shippings/ship-1/status', {
        method: 'PUT',
        body: JSON.stringify({}), // missing status
        headers: { 'Content-Type': 'application/json' },
      }),
      { params: Promise.resolve({ id: 'ship-1' }) },
    );
    expect(res.status).toBe(422);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('no IAM session → 401 (no upstream call)', async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    const res = await statusPUT(
      new Request('http://console.local/api/ecommerce/shippings/ship-1/status', {
        method: 'PUT',
        body: JSON.stringify({ status: 'SHIPPED' }),
        headers: { 'Content-Type': 'application/json' },
      }),
      { params: Promise.resolve({ id: 'ship-1' }) },
    );
    expect(res.status).toBe(401);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('400 InvalidShipping (SHIPPED without carrier/tracking) → 400 passthrough', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(ecomError('InvalidShipping', 400)),
    );
    const res = await statusPUT(
      new Request('http://console.local/api/ecommerce/shippings/ship-1/status', {
        method: 'PUT',
        body: JSON.stringify({ status: 'SHIPPED' }),
        headers: { 'Content-Type': 'application/json' },
      }),
      { params: Promise.resolve({ id: 'ship-1' }) },
    );
    expect(res.status).toBe(400);
    const body = await res.json();
    expect(body.code).toBe('InvalidShipping');
  });

  it('409 INVALID_TRANSITION → 409 passthrough (non-linear jump)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(ecomError('INVALID_TRANSITION', 409)),
    );
    const res = await statusPUT(
      new Request('http://console.local/api/ecommerce/shippings/ship-1/status', {
        method: 'PUT',
        body: JSON.stringify({ status: 'DELIVERED' }),
        headers: { 'Content-Type': 'application/json' },
      }),
      { params: Promise.resolve({ id: 'ship-1' }) },
    );
    expect(res.status).toBe(409);
    const body = await res.json();
    expect(body.code).toBe('INVALID_TRANSITION');
  });

  it('503 → 503 (section degrades only)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(ecomError('SERVICE_UNAVAILABLE', 503)),
    );
    const res = await statusPUT(
      new Request('http://console.local/api/ecommerce/shippings/ship-1/status', {
        method: 'PUT',
        body: JSON.stringify({ status: 'IN_TRANSIT' }),
        headers: { 'Content-Type': 'application/json' },
      }),
      { params: Promise.resolve({ id: 'ship-1' }) },
    );
    expect(res.status).toBe(503);
  });
});

describe('POST /api/ecommerce/shippings/{id}/refresh-tracking (refresh proxy)', () => {
  it('attaches the domain-facing token; returns 200 with the updated shipment', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    cookieJar.set(OPERATOR_COOKIE, 'OP-MUST-NOT-USE');
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(SHIPPING));
    vi.stubGlobal('fetch', fetchMock);

    const res = await refreshPOST(
      new Request(
        'http://console.local/api/ecommerce/shippings/ship-1/refresh-tracking',
        { method: 'POST' },
      ),
      { params: Promise.resolve({ id: 'ship-1' }) },
    );
    expect(res.status).toBe(200);
    const data = await res.json();
    expect(data.shippingId).toBe('ship-1');

    const [url, init] = fetchMock.mock.calls[0];
    expect(String(url)).toBe(
      'http://ecommerce.local/api/shippings/ship-1/refresh-tracking',
    );
    expect((init as RequestInit).method).toBe('POST');

    const h = (init as RequestInit).headers as Record<string, string>;
    expect(h.Authorization).toBe('Bearer GAP-ACCESS');
    expect(h.Authorization).not.toContain('OP-MUST-NOT-USE');
    expect(h['X-Tenant-Id']).toBeUndefined();
  });

  it('no IAM session → 401 (no upstream call)', async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    const res = await refreshPOST(
      new Request(
        'http://console.local/api/ecommerce/shippings/ship-1/refresh-tracking',
        { method: 'POST' },
      ),
      { params: Promise.resolve({ id: 'ship-1' }) },
    );
    expect(res.status).toBe(401);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('503 → 503 (section degrades only; best-effort does not alter carrier-mock 200 behavior)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(ecomError('SERVICE_UNAVAILABLE', 503)),
    );
    const res = await refreshPOST(
      new Request(
        'http://console.local/api/ecommerce/shippings/ship-1/refresh-tracking',
        { method: 'POST' },
      ),
      { params: Promise.resolve({ id: 'ship-1' }) },
    );
    expect(res.status).toBe(503);
  });
});
