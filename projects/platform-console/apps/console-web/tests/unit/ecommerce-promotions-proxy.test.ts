import { describe, it, expect, vi, beforeEach } from 'vitest';

/**
 * Same-origin ecommerce-ops promotions proxy route handlers
 * (TASK-PC-FE-086 — ADR-031 Phase 3b; updated by TASK-BE-536):
 *   - create POST / update PUT / delete DELETE / coupon-issue POST: domain-facing
 *     IAM OIDC token attached server-side (NOT the operator token); NO X-Tenant-Id.
 *     coupon-issue POST now attaches `Idempotency-Key` (the producer requires it
 *     there); every other route still sends none.
 *   - bad body (Zod fail) → 422 (no upstream call).
 *   - 401 → 401 when the IAM session is absent.
 *   - 503 → 503 (section degrades only); 422 → 422 passthrough.
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

import {
  GET as listGET,
  POST as createPOST,
} from '@/app/api/ecommerce/promotions/route';
import {
  GET as detailGET,
  PUT as updatePUT,
  DELETE as deleteDELETE,
} from '@/app/api/ecommerce/promotions/[id]/route';
import { POST as issuePOST } from '@/app/api/ecommerce/promotions/[id]/coupons/issue/route';
import { ACCESS_COOKIE, OPERATOR_COOKIE } from '@/shared/lib/session';

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}
function noContent() {
  return new Response(null, { status: 204 });
}
function ecomError(code: string, status: number) {
  return new Response(JSON.stringify({ code, message: 'e', timestamp: 't' }), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

const VALID_BODY = {
  name: 'Summer Sale',
  discountType: 'PERCENTAGE',
  discountValue: 20,
  maxDiscountAmount: 5000,
  maxIssuanceCount: 100,
  startDate: '2026-07-01',
  endDate: '2026-07-31',
};

beforeEach(() => {
  cookieJar.clear();
  vi.unstubAllGlobals();
});

describe('GET/POST /api/ecommerce/promotions (list + create)', () => {
  it('GET list passes through the promotion list', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    const list = {
      content: [],
      page: 0,
      size: 20,
      totalElements: 0,
    };
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse(list)));
    const res = await listGET(
      new Request('http://console.local/api/ecommerce/promotions?page=0&size=20'),
    );
    expect(res.status).toBe(200);
    const body = await res.json();
    expect(body.content).toEqual([]);
  });

  it('POST create — attaches the domain-facing token (NOT the operator token), NO X-Tenant-Id / Idempotency-Key', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    cookieJar.set(OPERATOR_COOKIE, 'OP-MUST-NOT-USE');
    const fetchMock = vi
      .fn()
      .mockResolvedValue(jsonResponse({ promotionId: 'promo-9' }, 201));
    vi.stubGlobal('fetch', fetchMock);

    const res = await createPOST(
      new Request('http://console.local/api/ecommerce/promotions', {
        method: 'POST',
        body: JSON.stringify(VALID_BODY),
        headers: { 'Content-Type': 'application/json' },
      }),
    );
    expect(res.status).toBe(201);

    const init = fetchMock.mock.calls[0][1] as RequestInit;
    const h = init.headers as Record<string, string>;
    expect(h.Authorization).toBe('Bearer GAP-ACCESS');
    expect(h.Authorization).not.toContain('OP-MUST-NOT-USE');
    expect(h['X-Tenant-Id']).toBeUndefined();
    expect(h['Idempotency-Key']).toBeUndefined();
  });

  it('POST create — invalid body (Zod fail: missing name) → 422 (no upstream call)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    const res = await createPOST(
      new Request('http://console.local/api/ecommerce/promotions', {
        method: 'POST',
        body: JSON.stringify({ discountType: 'FIXED', discountValue: 1000 }), // missing name, etc.
        headers: { 'Content-Type': 'application/json' },
      }),
    );
    expect(res.status).toBe(422);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('POST create — no IAM session → 401 (no upstream call)', async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    const res = await createPOST(
      new Request('http://console.local/api/ecommerce/promotions', {
        method: 'POST',
        body: JSON.stringify(VALID_BODY),
        headers: { 'Content-Type': 'application/json' },
      }),
    );
    expect(res.status).toBe(401);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('POST create — 503 → 503 (section degrades only)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(ecomError('SERVICE_UNAVAILABLE', 503)),
    );
    const res = await createPOST(
      new Request('http://console.local/api/ecommerce/promotions', {
        method: 'POST',
        body: JSON.stringify(VALID_BODY),
        headers: { 'Content-Type': 'application/json' },
      }),
    );
    expect(res.status).toBe(503);
  });
});

describe('GET/PUT/DELETE /api/ecommerce/promotions/{id}', () => {
  beforeEach(() => cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS'));

  it('GET detail passes through the promotion detail', async () => {
    const detail = {
      promotionId: 'promo-1',
      name: 'Summer',
      discountType: 'FIXED',
      discountValue: 1000,
      issuedCount: 0,
      maxIssuanceCount: 50,
      startDate: '2026-07-01',
      endDate: '2026-07-31',
      status: 'SCHEDULED',
    };
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse(detail)));
    const res = await detailGET(
      new Request('http://console.local/api/ecommerce/promotions/promo-1'),
      { params: Promise.resolve({ id: 'promo-1' }) },
    );
    expect(res.status).toBe(200);
  });

  it('PUT update forwards a full-replace body (PUT, NOT PATCH)', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValue(jsonResponse({ promotionId: 'promo-1' }));
    vi.stubGlobal('fetch', fetchMock);
    const res = await updatePUT(
      new Request('http://console.local/api/ecommerce/promotions/promo-1', {
        method: 'PUT',
        body: JSON.stringify(VALID_BODY),
        headers: { 'Content-Type': 'application/json' },
      }),
      { params: Promise.resolve({ id: 'promo-1' }) },
    );
    expect(res.status).toBe(200);
    const [url, init] = fetchMock.mock.calls[0];
    expect(String(url)).toBe('http://ecommerce.local/api/promotions/promo-1');
    expect((init as RequestInit).method).toBe('PUT');
  });

  it('PUT update — invalid body (Zod fail: discountValue 0) → 422 (no upstream call)', async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    const res = await updatePUT(
      new Request('http://console.local/api/ecommerce/promotions/promo-1', {
        method: 'PUT',
        body: JSON.stringify({ ...VALID_BODY, discountValue: 0 }), // 0 is not positive
        headers: { 'Content-Type': 'application/json' },
      }),
      { params: Promise.resolve({ id: 'promo-1' }) },
    );
    expect(res.status).toBe(422);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('DELETE → 204', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(noContent()));
    const res = await deleteDELETE(
      new Request('http://console.local/api/ecommerce/promotions/promo-1', {
        method: 'DELETE',
      }),
      { params: Promise.resolve({ id: 'promo-1' }) },
    );
    expect(res.status).toBe(204);
  });

  it('422 PROMOTION_ALREADY_ENDED on update → 422 passthrough', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(ecomError('PROMOTION_ALREADY_ENDED', 422)),
    );
    const res = await updatePUT(
      new Request('http://console.local/api/ecommerce/promotions/promo-1', {
        method: 'PUT',
        body: JSON.stringify(VALID_BODY),
        headers: { 'Content-Type': 'application/json' },
      }),
      { params: Promise.resolve({ id: 'promo-1' }) },
    );
    expect(res.status).toBe(422);
    const body = await res.json();
    expect(body.code).toBe('PROMOTION_ALREADY_ENDED');
  });
});

describe('POST /api/ecommerce/promotions/{id}/coupons/issue', () => {
  beforeEach(() => cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS'));

  it('forwards { userIds } and returns 201 { issuedCount }', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValue(jsonResponse({ issuedCount: 3 }, 201));
    vi.stubGlobal('fetch', fetchMock);
    const res = await issuePOST(
      new Request(
        'http://console.local/api/ecommerce/promotions/promo-1/coupons/issue',
        {
          method: 'POST',
          body: JSON.stringify({
            userIds: ['u-1', 'u-2', 'u-3'],
            idempotencyKey: 'idem-proxy-coupon',
          }),
          headers: { 'Content-Type': 'application/json' },
        },
      ),
      { params: Promise.resolve({ id: 'promo-1' }) },
    );
    expect(res.status).toBe(201);
    const body = await res.json();
    expect(body.issuedCount).toBe(3);
    const [url, init] = fetchMock.mock.calls[0];
    expect(String(url)).toBe(
      'http://ecommerce.local/api/promotions/promo-1/coupons/issue',
    );
    const sentBody = JSON.parse((init as RequestInit).body as string);
    expect(sentBody.userIds).toEqual(['u-1', 'u-2', 'u-3']);
    // TASK-PC-FE-252: body key → header verbatim, and stripped from the body.
    expect(sentBody.idempotencyKey).toBeUndefined();
    const h = (init as RequestInit).headers as Record<string, string>;
    expect(h['Idempotency-Key']).toBe('idem-proxy-coupon');
  });

  it('invalid body (empty userIds) → 422 (no upstream call)', async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    const res = await issuePOST(
      new Request(
        'http://console.local/api/ecommerce/promotions/promo-1/coupons/issue',
        {
          method: 'POST',
          body: JSON.stringify({ userIds: [] }),
          headers: { 'Content-Type': 'application/json' },
        },
      ),
      { params: Promise.resolve({ id: 'promo-1' }) },
    );
    expect(res.status).toBe(422);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('422 COUPON_LIMIT_EXCEEDED → passthrough', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(ecomError('COUPON_LIMIT_EXCEEDED', 422)),
    );
    const res = await issuePOST(
      new Request(
        'http://console.local/api/ecommerce/promotions/promo-1/coupons/issue',
        {
          method: 'POST',
          body: JSON.stringify({ userIds: ['u-1'], idempotencyKey: 'idem-x' }),
          headers: { 'Content-Type': 'application/json' },
        },
      ),
      { params: Promise.resolve({ id: 'promo-1' }) },
    );
    expect(res.status).toBe(422);
    const body = await res.json();
    expect(body.code).toBe('COUPON_LIMIT_EXCEEDED');
  });

  it('422 PROMOTION_NOT_ACTIVE → passthrough', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(ecomError('PROMOTION_NOT_ACTIVE', 422)),
    );
    const res = await issuePOST(
      new Request(
        'http://console.local/api/ecommerce/promotions/promo-1/coupons/issue',
        {
          method: 'POST',
          body: JSON.stringify({ userIds: ['u-1'], idempotencyKey: 'idem-x' }),
          headers: { 'Content-Type': 'application/json' },
        },
      ),
      { params: Promise.resolve({ id: 'promo-1' }) },
    );
    expect(res.status).toBe(422);
    const body = await res.json();
    expect(body.code).toBe('PROMOTION_NOT_ACTIVE');
  });
});
