import { describe, it, expect, vi, beforeEach } from 'vitest';

/**
 * Same-origin ecommerce-ops product proxy route handlers (TASK-PC-FE-081 —
 * § 2.4.10, updated by TASK-BE-536):
 *   - register POST / update PATCH / delete DELETE / variant POST·PATCH·DELETE
 *     / stock PATCH: domain-facing IAM OIDC token attached server-side (NOT the
 *     operator token); NO X-Tenant-Id. `Idempotency-Key` is now attached on
 *     register POST and stock PATCH (the producer requires it there); every
 *     other route still sends none.
 *   - bad body (Zod fail) → 422 (no upstream call).
 *   - 401 → no upstream call when the IAM session is absent.
 *   - 503 → 503 (section degrades only); 409 → 409 passthrough.
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
  POST as registerPOST,
} from '@/app/api/ecommerce/products/route';
import {
  GET as detailGET,
  PATCH as updatePATCH,
  DELETE as deleteDELETE,
} from '@/app/api/ecommerce/products/[id]/route';
import { POST as addVariantPOST } from '@/app/api/ecommerce/products/[id]/variants/route';
import {
  PATCH as updateVariantPATCH,
  DELETE as deleteVariantDELETE,
} from '@/app/api/ecommerce/products/[id]/variants/[variantId]/route';
import { PATCH as stockPATCH } from '@/app/api/ecommerce/products/[id]/stock/route';
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
function postReq(body: unknown) {
  return new Request('http://console.local/api/ecommerce/products', {
    method: 'POST',
    body: JSON.stringify(body),
    headers: { 'Content-Type': 'application/json' },
  });
}

const VALID_REGISTER = {
  name: 'Tee',
  price: 12000,
  variants: [{ optionName: 'M', stock: 5, additionalPrice: 0 }],
};

beforeEach(() => {
  cookieJar.clear();
  vi.unstubAllGlobals();
});

const EMPTY_LIST = { content: [], page: 0, size: 20, totalElements: 0 };

describe('GET /api/ecommerce/products (list) proxy', () => {
  it('forwards the status filter + pagination to GET /admin/products with the domain-facing token', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    cookieJar.set(OPERATOR_COOKIE, 'OP-MUST-NOT-USE');
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(EMPTY_LIST));
    vi.stubGlobal('fetch', fetchMock);

    const res = await listGET(
      new Request(
        'http://console.local/api/ecommerce/products?status=HIDDEN&page=0&size=20',
      ),
    );
    expect(res.status).toBe(200);

    const [url, init] = fetchMock.mock.calls[0];
    const parsed = new URL(String(url));
    expect(parsed.origin + parsed.pathname).toBe(
      'http://ecommerce.local/api/admin/products',
    );
    expect(parsed.searchParams.get('status')).toBe('HIDDEN');
    expect(parsed.searchParams.get('page')).toBe('0');
    expect(parsed.searchParams.get('size')).toBe('20');
    const h = (init as RequestInit).headers as Record<string, string>;
    expect(h.Authorization).toBe('Bearer GAP-ACCESS');
    expect(h.Authorization).not.toContain('OP-MUST-NOT-USE');
    expect(h['X-Tenant-Id']).toBeUndefined();
  });

  it('503 → 503 (section degrades only)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(ecomError('SERVICE_UNAVAILABLE', 503)),
    );
    const res = await listGET(
      new Request('http://console.local/api/ecommerce/products?status=HIDDEN'),
    );
    expect(res.status).toBe(503);
  });
});

describe('POST /api/ecommerce/products (register) proxy', () => {
  it('attaches the domain-facing token (NOT the operator token), NO X-Tenant-Id, and an Idempotency-Key (TASK-BE-536)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    cookieJar.set(OPERATOR_COOKIE, 'OP-MUST-NOT-USE');
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ id: 'p-9' }, 201));
    vi.stubGlobal('fetch', fetchMock);

    const res = await registerPOST(postReq(VALID_REGISTER));
    expect(res.status).toBe(201);

    const init = fetchMock.mock.calls[0][1] as RequestInit;
    const h = init.headers as Record<string, string>;
    expect(h.Authorization).toBe('Bearer GAP-ACCESS');
    expect(h.Authorization).not.toContain('OP-MUST-NOT-USE');
    expect(h['X-Tenant-Id']).toBeUndefined();
    // TASK-BE-536: the producer now requires Idempotency-Key on this endpoint.
    expect(h['Idempotency-Key']).toBeTruthy();
  });

  it('an invalid body (Zod fail) → 422 (no upstream call)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    // price 0 (not positive) + no variants → invalid RegisterProductRequest.
    const res = await registerPOST(postReq({ name: 'x', price: 0, variants: [] }));
    expect(res.status).toBe(422);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('no IAM session → 401 (no upstream call)', async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    const res = await registerPOST(postReq(VALID_REGISTER));
    expect(res.status).toBe(401);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('503 → 503 (section degrades only)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(ecomError('SERVICE_UNAVAILABLE', 503)));
    const res = await registerPOST(postReq(VALID_REGISTER));
    expect(res.status).toBe(503);
  });
});

// TASK-PC-FE-132 — the `useProduct` client hook refetches `GET /products/{id}`
// (mount refetch + post-mutation invalidate). The detail route exported only
// PATCH/DELETE, so the GET 405'd — surfacing as a stale/failed detail after a
// stock adjust. The GET proxy reuses the server-side `getProduct` (public read
// path), so a stock increase's post-success refetch now renders the new stock.
const DETAIL = {
  id: 'p-1',
  name: 'Tee',
  status: 'ON_SALE',
  price: 12000,
  variants: [{ id: 'v-1', optionName: 'M', stock: 7, additionalPrice: 0 }],
  images: [],
};

describe('GET /api/ecommerce/products/{id} (detail) proxy', () => {
  function detailReq(id: string) {
    return new Request(`http://console.local/api/ecommerce/products/${id}`);
  }

  it('returns 200 + ProductDetail, fetching the public read path with the domain-facing token', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    cookieJar.set(OPERATOR_COOKIE, 'OP-MUST-NOT-USE');
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(DETAIL));
    vi.stubGlobal('fetch', fetchMock);

    const res = await detailGET(detailReq('p-1'), {
      params: Promise.resolve({ id: 'p-1' }),
    });
    expect(res.status).toBe(200);
    const body = await res.json();
    expect(body.id).toBe('p-1');
    expect(body.variants[0].stock).toBe(7);

    const [url, init] = fetchMock.mock.calls[0];
    // public base (admin controller has no GET /{id}), NOT the admin base.
    expect(String(url)).toBe('http://ecommerce.local/api/products/p-1');
    const h = (init as RequestInit).headers as Record<string, string>;
    expect(h.Authorization).toBe('Bearer GAP-ACCESS');
    expect(h.Authorization).not.toContain('OP-MUST-NOT-USE');
    expect(h['X-Tenant-Id']).toBeUndefined();
  });

  it('404 PRODUCT_NOT_FOUND → 404 (mapping preserved)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(ecomError('PRODUCT_NOT_FOUND', 404)),
    );
    const res = await detailGET(detailReq('p-gone'), {
      params: Promise.resolve({ id: 'p-gone' }),
    });
    expect(res.status).toBe(404);
  });

  it('503 → 503 (section degrades only)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(ecomError('SERVICE_UNAVAILABLE', 503)),
    );
    const res = await detailGET(detailReq('p-1'), {
      params: Promise.resolve({ id: 'p-1' }),
    });
    expect(res.status).toBe(503);
  });
});

describe('PATCH/DELETE /api/ecommerce/products/{id}', () => {
  it('update PATCH forwards a partial body', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ id: 'p-1' }));
    vi.stubGlobal('fetch', fetchMock);
    const res = await updatePATCH(
      new Request('http://console.local/api/ecommerce/products/p-1', {
        method: 'PATCH',
        body: JSON.stringify({ status: 'HIDDEN' }),
        headers: { 'Content-Type': 'application/json' },
      }),
      { params: Promise.resolve({ id: 'p-1' }) },
    );
    expect(res.status).toBe(200);
    const [url, init] = fetchMock.mock.calls[0];
    expect(String(url)).toBe('http://ecommerce.local/api/admin/products/p-1');
    expect((init as RequestInit).method).toBe('PATCH');
  });

  it('delete DELETE → 204', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(noContent()));
    const res = await deleteDELETE(
      new Request('http://console.local/api/ecommerce/products/p-1', { method: 'DELETE' }),
      { params: Promise.resolve({ id: 'p-1' }) },
    );
    expect(res.status).toBe(204);
  });

  // TASK-PC-FE-131 — idempotent delete: re-deleting an already soft-deleted
  // product yields the producer's 404 PRODUCT_NOT_FOUND; the delete goal is
  // already met, so the proxy renders 204 (not a hard failure).
  it('delete DELETE on an already-deleted product (404 PRODUCT_NOT_FOUND) → 204 (idempotent)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(ecomError('PRODUCT_NOT_FOUND', 404)),
    );
    const res = await deleteDELETE(
      new Request('http://console.local/api/ecommerce/products/p-gone', { method: 'DELETE' }),
      { params: Promise.resolve({ id: 'p-gone' }) },
    );
    expect(res.status).toBe(204);
  });

  it('delete DELETE — a non-404 error (503) still degrades (mapping preserved)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(ecomError('SERVICE_UNAVAILABLE', 503)),
    );
    const res = await deleteDELETE(
      new Request('http://console.local/api/ecommerce/products/p-1', { method: 'DELETE' }),
      { params: Promise.resolve({ id: 'p-1' }) },
    );
    expect(res.status).toBe(503);
  });

  it('409 CONFLICT on update → 409 passthrough', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(ecomError('CONFLICT', 409)));
    const res = await updatePATCH(
      new Request('http://console.local/api/ecommerce/products/p-1', {
        method: 'PATCH',
        body: JSON.stringify({ price: 1 }),
        headers: { 'Content-Type': 'application/json' },
      }),
      { params: Promise.resolve({ id: 'p-1' }) },
    );
    expect(res.status).toBe(409);
    const body = await res.json();
    expect(body.code).toBe('CONFLICT');
  });
});

describe('variant + stock proxies', () => {
  beforeEach(() => cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS'));

  it('add variant POST forwards AddVariantRequest', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValue(jsonResponse({ id: 'v-2', optionName: 'L', stock: 3, additionalPrice: 0 }, 201));
    vi.stubGlobal('fetch', fetchMock);
    const res = await addVariantPOST(
      new Request('http://console.local/api/ecommerce/products/p-1/variants', {
        method: 'POST',
        body: JSON.stringify({ optionName: 'L', stock: 3, additionalPrice: 0 }),
        headers: { 'Content-Type': 'application/json' },
      }),
      { params: Promise.resolve({ id: 'p-1' }) },
    );
    expect(res.status).toBe(201);
    expect(String(fetchMock.mock.calls[0][0])).toBe(
      'http://ecommerce.local/api/admin/products/p-1/variants',
    );
  });

  it('add variant — invalid body → 422 (no upstream call)', async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    const res = await addVariantPOST(
      new Request('http://console.local/api/ecommerce/products/p-1/variants', {
        method: 'POST',
        body: JSON.stringify({ optionName: '', stock: -1, additionalPrice: 0 }),
        headers: { 'Content-Type': 'application/json' },
      }),
      { params: Promise.resolve({ id: 'p-1' }) },
    );
    expect(res.status).toBe(422);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('update variant PATCH (optionName + additionalPrice, NO stock)', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValue(jsonResponse({ id: 'v-1', optionName: 'M2', stock: 10, additionalPrice: 200 }));
    vi.stubGlobal('fetch', fetchMock);
    const res = await updateVariantPATCH(
      new Request('http://console.local/api/ecommerce/products/p-1/variants/v-1', {
        method: 'PATCH',
        body: JSON.stringify({ optionName: 'M2', additionalPrice: 200 }),
        headers: { 'Content-Type': 'application/json' },
      }),
      { params: Promise.resolve({ id: 'p-1', variantId: 'v-1' }) },
    );
    expect(res.status).toBe(200);
    expect(String(fetchMock.mock.calls[0][0])).toBe(
      'http://ecommerce.local/api/admin/products/p-1/variants/v-1',
    );
  });

  it('delete variant DELETE → 204', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(noContent()));
    const res = await deleteVariantDELETE(
      new Request('http://console.local/api/ecommerce/products/p-1/variants/v-1', {
        method: 'DELETE',
      }),
      { params: Promise.resolve({ id: 'p-1', variantId: 'v-1' }) },
    );
    expect(res.status).toBe(204);
  });

  it('stock PATCH forwards AdjustStockRequest (reason in body, no X-Operator-Reason header) and attaches Idempotency-Key (TASK-BE-536)', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValue(jsonResponse({ variantId: 'v-1', currentStock: 7 }));
    vi.stubGlobal('fetch', fetchMock);
    const res = await stockPATCH(
      new Request('http://console.local/api/ecommerce/products/p-1/stock', {
        method: 'PATCH',
        body: JSON.stringify({ variantId: 'v-1', quantity: -3, reason: 'damage' }),
        headers: { 'Content-Type': 'application/json' },
      }),
      { params: Promise.resolve({ id: 'p-1' }) },
    );
    expect(res.status).toBe(200);
    const init = fetchMock.mock.calls[0][1] as RequestInit;
    const h = init.headers as Record<string, string>;
    expect(h['X-Operator-Reason']).toBeUndefined();
    expect(h['Idempotency-Key']).toBeTruthy();
    const body = JSON.parse(init.body as string);
    expect(body).toEqual({ variantId: 'v-1', quantity: -3, reason: 'damage' });
  });

  it('stock PATCH — invalid body (quantity 0) → 422 (no upstream call)', async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    const res = await stockPATCH(
      new Request('http://console.local/api/ecommerce/products/p-1/stock', {
        method: 'PATCH',
        body: JSON.stringify({ variantId: 'v-1', reason: 'r' }),
        headers: { 'Content-Type': 'application/json' },
      }),
      { params: Promise.resolve({ id: 'p-1' }) },
    );
    expect(res.status).toBe(422);
    expect(fetchMock).not.toHaveBeenCalled();
  });
});
