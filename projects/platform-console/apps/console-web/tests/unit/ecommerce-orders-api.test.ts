import { describe, it, expect, vi, beforeEach } from 'vitest';

/**
 * `features/ecommerce-ops/api/orders-api.ts` — the security-critical core of
 * TASK-PC-FE-083 (the ecommerce orders operator surface — ADR-MONO-031 Phase
 * 1b).
 *
 * THE CENTRAL ASSERTION (console-integration-contract § 2.4.10, inheriting the
 * non-IAM § 2.4.5 rules — EXACT MIRROR of the products-api test):
 *   - every ecommerce order call's bearer is the **domain-facing IAM OIDC token**
 *     (`getDomainFacingToken()` → the `console_access_token` cookie), NEVER the
 *     exchanged operator token;
 *   - the operator-token path is ABSENT (`getOperatorToken` NOT called — pinned
 *     so a future blanket-apply refactor cannot break it);
 *   - the console sends NO `X-Tenant-Id` (ecommerce resolves tenant from the
 *     JWT `tenant_id` claim producer-side);
 *   - NO `Idempotency-Key` on the status-change mutation (the producer defines
 *     none — § 2.4.10);
 *   - the ecommerce FLAT error envelope `{ code, message, timestamp }` is
 *     parsed (NOT wms's nested `{ error: { code } }`);
 *   - 401 → ApiError(401); 403 → ApiError(403); 400/404/422/409 → ApiError
 *     inline; 503/timeout → EcommerceUnavailableError (section degrades only);
 *   - an unknown/future status in the response parses without throwing.
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
  listOrders,
  getOrder,
  changeOrderStatus,
} from '@/features/ecommerce-ops/api/orders-api';
import { ApiError, EcommerceUnavailableError } from '@/shared/api/errors';
import { ACCESS_COOKIE, OPERATOR_COOKIE } from '@/shared/lib/session';

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}
/** ecommerce FLAT error envelope (distinct from wms's nested shape). */
function ecomError(code: string, status: number, message = 'err') {
  return new Response(
    JSON.stringify({ code, message, timestamp: '2026-06-14T00:00:00.000Z' }),
    { status, headers: { 'Content-Type': 'application/json' } },
  );
}

const ORDER_LIST = {
  content: [
    {
      orderId: 'ord-1',
      userId: 'u-1',
      status: 'PENDING',
      totalPrice: 15000,
      itemCount: 1,
      firstItemName: 'Tee',
      createdAt: '2026-06-14T00:00:00Z',
    },
  ],
  page: 0,
  size: 20,
  totalElements: 1,
};

const ORDER_DETAIL = {
  orderId: 'ord-1',
  userId: 'u-1',
  status: 'PENDING',
  totalPrice: 15000,
  items: [
    {
      productId: 'p-1',
      variantId: 'v-1',
      productName: 'Tee',
      optionName: 'M',
      quantity: 1,
      unitPrice: 15000,
      sellerId: 's-1',
    },
  ],
  shippingAddress: {
    recipient: '홍길동',
    phone: '010-1234-5678',
    zipCode: '12345',
    address1: '서울시 강남구',
    address2: '',
  },
  createdAt: '2026-06-14T00:00:00Z',
  updatedAt: '2026-06-14T00:00:00Z',
};

beforeEach(() => {
  cookieJar.clear();
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

describe('orders-api — per-domain credential selection (§ 2.4.10)', () => {
  it('sends the domain-facing IAM OIDC ACCESS cookie as the bearer (NOT the operator token)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-OIDC-ACCESS-required-by-ecommerce');
    cookieJar.set(OPERATOR_COOKIE, 'OPERATOR-TOKEN-must-not-be-used');
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(ORDER_LIST));
    vi.stubGlobal('fetch', fetchMock);

    await listOrders({ page: 0, size: 20 });

    const [url, init] = fetchMock.mock.calls[0];
    const headers = (init as RequestInit).headers as Record<string, string>;
    expect(headers.Authorization).toBe(
      'Bearer GAP-OIDC-ACCESS-required-by-ecommerce',
    );
    expect(headers.Authorization).not.toContain('OPERATOR-TOKEN-must-not-be-used');
    expect(String(url)).toContain('http://ecommerce.local/api/admin/orders');
  });

  it('uses getDomainFacingToken() and NEVER getOperatorToken() (pins the per-domain rule)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-OIDC-ACCESS');
    cookieJar.set(OPERATOR_COOKIE, 'OPERATOR-TOKEN');
    const getDomainFacingSpy = vi.spyOn(sessionModule, 'getDomainFacingToken');
    const getOperatorSpy = vi.spyOn(sessionModule, 'getOperatorToken');
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse(ORDER_LIST)));

    await listOrders();

    expect(getDomainFacingSpy).toHaveBeenCalled();
    expect(getOperatorSpy).not.toHaveBeenCalled();
  });

  it('throws 401 with NO fetch when the IAM session is absent', async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    const err = await listOrders().catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(401);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('sends NO X-Tenant-Id and NO Idempotency-Key on the status-change mutation', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-OIDC-ACCESS');
    const fetchMock = vi
      .fn()
      .mockResolvedValue(jsonResponse({ orderId: 'ord-1', status: 'CONFIRMED' }));
    vi.stubGlobal('fetch', fetchMock);

    await changeOrderStatus('ord-1', { status: 'CONFIRMED' });

    const init = fetchMock.mock.calls[0][1] as RequestInit;
    const headers = init.headers as Record<string, string>;
    expect(headers['X-Tenant-Id']).toBeUndefined();
    expect(headers['Idempotency-Key']).toBeUndefined();
    expect(headers['X-Request-Id']).toBeTruthy();
  });
});

describe('orders-api — endpoint wiring + bodies (§ 2.4.10 #15-17)', () => {
  beforeEach(() => cookieJar.set(ACCESS_COOKIE, 'GAP-OIDC-ACCESS'));

  it('#15 list — GET admin /orders with status filter + capped size', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(ORDER_LIST));
    vi.stubGlobal('fetch', fetchMock);
    await listOrders({ status: 'PENDING', size: 999, page: 2 });
    const [url, init] = fetchMock.mock.calls[0];
    expect((init as RequestInit).method).toBe('GET');
    const u = new URL(String(url));
    expect(u.pathname).toBe('/api/admin/orders');
    expect(u.searchParams.get('status')).toBe('PENDING');
    expect(u.searchParams.get('size')).toBe('100'); // capped
    expect(u.searchParams.get('page')).toBe('2');
  });

  it('#16 detail — GET admin /orders/{id}', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(ORDER_DETAIL));
    vi.stubGlobal('fetch', fetchMock);
    const d = await getOrder('ord-1');
    expect(d.items[0].productName).toBe('Tee');
    const [url] = fetchMock.mock.calls[0];
    expect(String(url)).toBe('http://ecommerce.local/api/admin/orders/ord-1');
  });

  it('#17 change status — POST admin /orders/{id}/status with { status }', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValue(jsonResponse({ orderId: 'ord-1', status: 'CONFIRMED' }));
    vi.stubGlobal('fetch', fetchMock);
    const res = await changeOrderStatus('ord-1', { status: 'CONFIRMED' });
    expect(res.orderId).toBe('ord-1');
    expect(res.status).toBe('CONFIRMED');
    const [url, init] = fetchMock.mock.calls[0];
    expect(String(url)).toBe(
      'http://ecommerce.local/api/admin/orders/ord-1/status',
    );
    expect((init as RequestInit).method).toBe('POST');
    const body = JSON.parse((init as RequestInit).body as string);
    expect(body).toEqual({ status: 'CONFIRMED' });
  });
});

describe('orders-api — ecommerce FLAT envelope + § 2.5 resilience', () => {
  beforeEach(() => cookieJar.set(ACCESS_COOKIE, 'GAP-OIDC-ACCESS'));

  it('401 → ApiError(401) whole-session re-login', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(ecomError('UNAUTHORIZED', 401)));
    const err = await listOrders().catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(401);
  });

  it('403 → ApiError(403) inline', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(ecomError('ACCESS_DENIED', 403)),
    );
    const err = await changeOrderStatus('ord-1', { status: 'CONFIRMED' }).catch(
      (e) => e,
    );
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(403);
  });

  it('parses the FLAT { code, message } shape (404 ORDER_NOT_FOUND)', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(ecomError('ORDER_NOT_FOUND', 404, 'gone')),
    );
    const err = await getOrder('nope').catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(404);
    expect(err.code).toBe('ORDER_NOT_FOUND');
    expect(err.message).toBe('gone');
  });

  it('400 InvalidOrder (invalid forward transition) → ApiError(400) inline', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(ecomError('INVALID_ORDER', 400)),
    );
    const err = await changeOrderStatus('ord-1', { status: 'SHIPPED' }).catch(
      (e) => e,
    );
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(400);
    expect(err.code).toBe('INVALID_ORDER');
  });

  it('422 OrderCannotBeCancelled → ApiError(422) inline', async () => {
    vi.stubGlobal(
      'fetch',
      vi
        .fn()
        .mockResolvedValue(ecomError('ORDER_CANNOT_BE_CANCELLED', 422)),
    );
    const err = await changeOrderStatus('ord-1', { status: 'CANCELLED' }).catch(
      (e) => e,
    );
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(422);
    expect(err.code).toBe('ORDER_CANNOT_BE_CANCELLED');
  });

  it('409 CONFLICT (optimistic lock) → ApiError(409) inline (no silent retry)', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(ecomError('CONFLICT', 409)),
    );
    const err = await changeOrderStatus('ord-1', { status: 'CONFIRMED' }).catch(
      (e) => e,
    );
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(409);
    expect(err.code).toBe('CONFLICT');
  });

  it('a malformed / non-JSON error body does NOT crash (defensive parse)', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        new Response('not json', { status: 404, headers: { 'Content-Type': 'text/plain' } }),
      ),
    );
    const err = await getOrder('ord-1').catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(404);
    expect(err.code).toBe('HTTP_404');
  });

  it('503 → EcommerceUnavailableError (section degrades only)', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(ecomError('SERVICE_UNAVAILABLE', 503)),
    );
    const err = await listOrders().catch((e) => e);
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
    const err = await listOrders().catch((e) => e);
    expect(err).toBeInstanceOf(EcommerceUnavailableError);
    expect(err.reason).toBe('timeout');
  });

  it('an unknown/future status enum parses without throwing (tolerant read)', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        jsonResponse({ ...ORDER_DETAIL, status: 'FUTURE_V3', extra: 'x' }),
      ),
    );
    const d = await getOrder('ord-1');
    expect(d.status).toBe('FUTURE_V3');
  });
});
