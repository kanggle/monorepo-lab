import { describe, it, expect, vi, beforeEach } from 'vitest';

/**
 * `features/ecommerce-ops/api/products-api.ts` — the security-critical core of
 * TASK-PC-FE-081 (the FIRST ecommerce write surface — ADR-MONO-031 Phase 1b).
 *
 * THE CENTRAL ASSERTION (console-integration-contract § 2.4.10, inheriting the
 * non-IAM § 2.4.5 rules — the EXACT INVERSE of the FE-002..006 IAM assertion):
 *   - every ecommerce call's bearer is the **domain-facing IAM OIDC token**
 *     (`getDomainFacingToken()` → the `console_access_token` cookie when not
 *     tenant-switched), NEVER the exchanged operator token;
 *   - the operator-token path is ABSENT (`getOperatorToken` NOT called — pinned
 *     so a future blanket-apply refactor cannot break it);
 *   - the console sends NO `X-Tenant-Id` (ecommerce resolves tenant from the
 *     JWT `tenant_id` claim producer-side);
 *   - NO `Idempotency-Key` on any mutation (the producer defines none —
 *     § 2.4.10; confirm-gate + state guards are the double-submit defence);
 *   - the ecommerce FLAT error envelope `{ code, message, timestamp }` is
 *     parsed (NOT wms's nested `{ error: { code } }`);
 *   - 401 → ApiError(401); 403 → ApiError(403); 404/422/409 → ApiError inline;
 *     503/timeout → EcommerceUnavailableError (section degrades only);
 *   - detail (#2) uses the PUBLIC base; admin CRUD uses the ADMIN base.
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

import * as sessionModule from '@/shared/lib/session';

import {
  listProducts,
  getProduct,
  registerProduct,
  updateProduct,
  deleteProduct,
  addVariant,
  updateVariant,
  deleteVariant,
  adjustStock,
} from '@/features/ecommerce-ops/api/products-api';
import { ApiError, EcommerceUnavailableError } from '@/shared/api/errors';
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
/** ecommerce FLAT error envelope (distinct from wms's nested shape). */
function ecomError(code: string, status: number, message = 'err') {
  return new Response(
    JSON.stringify({ code, message, timestamp: '2026-06-13T00:00:00.000Z' }),
    { status, headers: { 'Content-Type': 'application/json' } },
  );
}

const PRODUCT_LIST = {
  content: [
    { id: 'p-1', name: 'Tee', status: 'ON_SALE', price: 12000, sellerId: 's-1' },
  ],
  page: 0,
  size: 20,
  totalElements: 1,
};
const PRODUCT_DETAIL = {
  id: 'p-1',
  name: 'Tee',
  description: 'a tee',
  status: 'ON_SALE',
  price: 12000,
  sellerId: 's-1',
  images: [],
  variants: [{ id: 'v-1', optionName: 'M', stock: 10, additionalPrice: 0 }],
};

beforeEach(() => {
  cookieJar.clear();
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

describe('products-api — per-domain credential selection (§ 2.4.10)', () => {
  it('sends the domain-facing IAM OIDC ACCESS cookie as the bearer (NOT the operator token)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-OIDC-ACCESS-required-by-ecommerce');
    cookieJar.set(OPERATOR_COOKIE, 'OPERATOR-TOKEN-must-not-be-used');
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(PRODUCT_LIST));
    vi.stubGlobal('fetch', fetchMock);

    await listProducts({ page: 0, size: 20 });

    const [url, init] = fetchMock.mock.calls[0];
    const headers = (init as RequestInit).headers as Record<string, string>;
    expect(headers.Authorization).toBe(
      'Bearer GAP-OIDC-ACCESS-required-by-ecommerce',
    );
    expect(headers.Authorization).not.toContain('OPERATOR-TOKEN-must-not-be-used');
    expect(String(url)).toContain('http://ecommerce.local/api/admin/products');
  });

  it('uses getDomainFacingToken() and NEVER getOperatorToken() (pins the per-domain rule)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-OIDC-ACCESS');
    cookieJar.set(OPERATOR_COOKIE, 'OPERATOR-TOKEN');
    const getDomainFacingSpy = vi.spyOn(sessionModule, 'getDomainFacingToken');
    const getOperatorSpy = vi.spyOn(sessionModule, 'getOperatorToken');
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse(PRODUCT_LIST)));

    await listProducts();

    expect(getDomainFacingSpy).toHaveBeenCalled();
    expect(getOperatorSpy).not.toHaveBeenCalled();
  });

  it('throws 401 with NO fetch when the IAM session is absent', async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    const err = await listProducts().catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(401);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('sends NO X-Tenant-Id on a mutation, and Idempotency-Key on register (TASK-BE-536 — producer now requires it there)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-OIDC-ACCESS');
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ id: 'p-9' }, 201));
    vi.stubGlobal('fetch', fetchMock);

    await registerProduct(
      {
        name: 'New',
        price: 5000,
        variants: [{ optionName: 'M', stock: 3, additionalPrice: 0 }],
      },
      'idem-reg-1',
    );

    const init = fetchMock.mock.calls[0][1] as RequestInit;
    const headers = init.headers as Record<string, string>;
    expect(headers['X-Tenant-Id']).toBeUndefined();
    // TASK-PC-FE-252: the api fn forwards the caller-supplied key verbatim (it no
    // longer mints its own) — the client mints one per confirmed intent.
    expect(headers['Idempotency-Key']).toBe('idem-reg-1');
    expect(headers['X-Operator-Reason']).toBeUndefined();
    expect(headers['X-Request-Id']).toBeTruthy();
  });
});

describe('products-api — endpoint wiring + bodies (§ 2.4.10 #1-9)', () => {
  beforeEach(() => cookieJar.set(ACCESS_COOKIE, 'GAP-OIDC-ACCESS'));

  it('1 list — GET admin base with filters + capped size', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(PRODUCT_LIST));
    vi.stubGlobal('fetch', fetchMock);
    await listProducts({ status: 'ON_SALE', size: 999, page: 2 });
    const [url, init] = fetchMock.mock.calls[0];
    expect((init as RequestInit).method).toBe('GET');
    const u = new URL(String(url));
    expect(u.pathname).toBe('/api/admin/products');
    expect(u.searchParams.get('status')).toBe('ON_SALE');
    expect(u.searchParams.get('size')).toBe('100'); // capped
    expect(u.searchParams.get('page')).toBe('2');
  });

  it('2 detail — GET the PUBLIC base /products/{id} (admin controller has no GET /{id})', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(PRODUCT_DETAIL));
    vi.stubGlobal('fetch', fetchMock);
    const d = await getProduct('p-1');
    expect(d.variants[0].optionName).toBe('M');
    const [url] = fetchMock.mock.calls[0];
    expect(String(url)).toBe('http://ecommerce.local/api/products/p-1');
  });

  it('3 register — POST admin /products with the producer RegisterProductRequest body', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ id: 'p-9' }, 201));
    vi.stubGlobal('fetch', fetchMock);
    const res = await registerProduct(
      {
        name: 'New',
        description: 'd',
        price: 5000,
        categoryId: 'c-1',
        variants: [{ optionName: 'M', stock: 3, additionalPrice: 100 }],
      },
      'idem-reg-2',
    );
    expect(res.id).toBe('p-9');
    const [url, init] = fetchMock.mock.calls[0];
    expect(String(url)).toBe('http://ecommerce.local/api/admin/products');
    expect((init as RequestInit).method).toBe('POST');
    const body = JSON.parse((init as RequestInit).body as string);
    expect(body.name).toBe('New');
    expect(body.variants[0]).toEqual({ optionName: 'M', stock: 3, additionalPrice: 100 });
    // the producer body must NOT carry the key (it rides the header only).
    expect(body.idempotencyKey).toBeUndefined();
    // TASK-BE-536 requires the key; TASK-PC-FE-252 supplies it from the caller.
    const headers = (init as RequestInit).headers as Record<string, string>;
    expect(headers['Idempotency-Key']).toBe('idem-reg-2');
  });

  it('4 update — PATCH admin /products/{id} (partial)', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ id: 'p-1' }));
    vi.stubGlobal('fetch', fetchMock);
    await updateProduct('p-1', { price: 7000, status: 'HIDDEN' });
    const [url, init] = fetchMock.mock.calls[0];
    expect(String(url)).toBe('http://ecommerce.local/api/admin/products/p-1');
    expect((init as RequestInit).method).toBe('PATCH');
    const body = JSON.parse((init as RequestInit).body as string);
    expect(body).toEqual({ price: 7000, status: 'HIDDEN' });
  });

  it('5 delete — DELETE admin /products/{id} (204, no body parse)', async () => {
    const fetchMock = vi.fn().mockResolvedValue(noContent());
    vi.stubGlobal('fetch', fetchMock);
    await expect(deleteProduct('p-1')).resolves.toBeUndefined();
    const [url, init] = fetchMock.mock.calls[0];
    expect(String(url)).toBe('http://ecommerce.local/api/admin/products/p-1');
    expect((init as RequestInit).method).toBe('DELETE');
  });

  it('6 add variant — POST admin /products/{id}/variants (AddVariantRequest)', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValue(jsonResponse({ id: 'v-2', optionName: 'L', stock: 5, additionalPrice: 0 }, 201));
    vi.stubGlobal('fetch', fetchMock);
    const v = await addVariant('p-1', { optionName: 'L', stock: 5, additionalPrice: 0 });
    expect(v.optionName).toBe('L');
    const [url, init] = fetchMock.mock.calls[0];
    expect(String(url)).toBe('http://ecommerce.local/api/admin/products/p-1/variants');
    expect((init as RequestInit).method).toBe('POST');
  });

  it('7 update variant — PATCH /variants/{vid} (optionName + additionalPrice, NO stock)', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValue(jsonResponse({ id: 'v-1', optionName: 'M2', stock: 10, additionalPrice: 200 }));
    vi.stubGlobal('fetch', fetchMock);
    await updateVariant('p-1', 'v-1', { optionName: 'M2', additionalPrice: 200 });
    const [url, init] = fetchMock.mock.calls[0];
    expect(String(url)).toBe('http://ecommerce.local/api/admin/products/p-1/variants/v-1');
    const body = JSON.parse((init as RequestInit).body as string);
    expect(body).toEqual({ optionName: 'M2', additionalPrice: 200 });
    expect(body.stock).toBeUndefined();
  });

  it('8 delete variant — DELETE /variants/{vid} (204)', async () => {
    const fetchMock = vi.fn().mockResolvedValue(noContent());
    vi.stubGlobal('fetch', fetchMock);
    await expect(deleteVariant('p-1', 'v-1')).resolves.toBeUndefined();
    const [url, init] = fetchMock.mock.calls[0];
    expect(String(url)).toBe('http://ecommerce.local/api/admin/products/p-1/variants/v-1');
    expect((init as RequestInit).method).toBe('DELETE');
  });

  it('9 adjust stock — PATCH /stock with AdjustStockRequest (signed quantity + reason in BODY)', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValue(jsonResponse({ variantId: 'v-1', currentStock: 7 }));
    vi.stubGlobal('fetch', fetchMock);
    const res = await adjustStock(
      'p-1',
      { variantId: 'v-1', quantity: -3, reason: 'damage' },
      'idem-stock-1',
    );
    expect(res.currentStock).toBe(7);
    const [url, init] = fetchMock.mock.calls[0];
    expect(String(url)).toBe('http://ecommerce.local/api/admin/products/p-1/stock');
    expect((init as RequestInit).method).toBe('PATCH');
    const body = JSON.parse((init as RequestInit).body as string);
    expect(body).toEqual({ variantId: 'v-1', quantity: -3, reason: 'damage' });
    const headers = (init as RequestInit).headers as Record<string, string>;
    expect(headers['X-Operator-Reason']).toBeUndefined();
    // TASK-BE-536 requires the key; TASK-PC-FE-252 forwards the caller's verbatim.
    expect(headers['Idempotency-Key']).toBe('idem-stock-1');
  });
});

describe('products-api — ecommerce FLAT envelope + § 2.5 resilience', () => {
  beforeEach(() => cookieJar.set(ACCESS_COOKIE, 'GAP-OIDC-ACCESS'));

  it('401 → ApiError(401) whole-session re-login', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(ecomError('UNAUTHORIZED', 401)));
    const err = await listProducts().catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(401);
  });

  it('403 → ApiError(403) inline', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(ecomError('ACCESS_DENIED', 403)));
    const err = await registerProduct(
      {
        name: 'x',
        price: 1,
        variants: [{ optionName: 'a', stock: 0, additionalPrice: 0 }],
      },
      'idem-x',
    ).catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(403);
  });

  it('parses the FLAT { code, message } shape (404 PRODUCT_NOT_FOUND)', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(ecomError('PRODUCT_NOT_FOUND', 404, 'gone')));
    const err = await getProduct('nope').catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(404);
    expect(err.code).toBe('PRODUCT_NOT_FOUND');
    expect(err.message).toBe('gone');
  });

  it('409 CONFLICT (optimistic lock) → ApiError(409) inline (no silent retry)', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(ecomError('CONFLICT', 409)));
    const err = await updateProduct('p-1', { price: 1 }).catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(409);
    expect(err.code).toBe('CONFLICT');
  });

  it('400 INSUFFICIENT_STOCK → ApiError(400) inline', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(ecomError('INSUFFICIENT_STOCK', 400)));
    const err = await adjustStock('p-1', { variantId: 'v-1', quantity: -99, reason: 'r' }, 'idem-x').catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(400);
    expect(err.code).toBe('INSUFFICIENT_STOCK');
  });

  it('a malformed / non-JSON error body does NOT crash (defensive parse)', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        new Response('not json', { status: 404, headers: { 'Content-Type': 'text/plain' } }),
      ),
    );
    const err = await getProduct('p-1').catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(404);
    expect(err.code).toBe('HTTP_404');
  });

  it('503 → EcommerceUnavailableError (section degrades only)', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(ecomError('SERVICE_UNAVAILABLE', 503)));
    const err = await listProducts().catch((e) => e);
    expect(err).toBeInstanceOf(EcommerceUnavailableError);
    expect(err.reason).toBe('downstream');
  });

  it('timeout → EcommerceUnavailableError(timeout)', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn((_u: string, init?: RequestInit) => {
        return new Promise((_res, rej) => {
          init?.signal?.addEventListener('abort', () => {
            const e = new Error('aborted');
            e.name = 'AbortError';
            rej(e);
          });
        });
      }),
    );
    const err = await listProducts().catch((e) => e);
    expect(err).toBeInstanceOf(EcommerceUnavailableError);
    expect(err.reason).toBe('timeout');
  });

  it('an unknown/future status enum parses without throwing (tolerant read)', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(jsonResponse({ ...PRODUCT_DETAIL, status: 'FUTURE_V2', extra: 'x' })),
    );
    const d = await getProduct('p-1');
    expect(d.status).toBe('FUTURE_V2');
  });
});
