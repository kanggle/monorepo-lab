import { describe, it, expect, vi, beforeEach } from 'vitest';

/**
 * `features/ecommerce-ops/api/shippings-api.ts` — the security-critical core of
 * TASK-PC-FE-088 (the ecommerce shippings operator surface — ADR-MONO-031 Phase 4b).
 *
 * THE CENTRAL ASSERTION (console-integration-contract § 2.4.10.3, inheriting the
 * non-IAM § 2.4.10 rules — EXACT MIRROR of the promotions-api test):
 *   - every ecommerce shipping call's bearer is the **domain-facing IAM OIDC token**
 *     (`getDomainFacingToken()` → the `console_access_token` cookie), NEVER the
 *     exchanged operator token;
 *   - the operator-token path is ABSENT (`getOperatorToken` NOT called — pinned
 *     so a future blanket-apply refactor cannot break it);
 *   - the console sends NO `X-Tenant-Id` (ecommerce resolves tenant from the
 *     JWT `tenant_id` claim producer-side);
 *   - NO `Idempotency-Key` on any mutation (the producer defines none — § 2.4.10);
 *   - the ecommerce FLAT error envelope `{ code, message, timestamp }` is parsed;
 *   - 401 → ApiError(401); 403 → ApiError(403); 400/404/409/422 → ApiError
 *     inline; 503/timeout → EcommerceUnavailableError (section degrades only);
 *   - all requests go to ECOMMERCE_PUBLIC_BASE_URL + `/shippings/**`
 *     (NOT ECOMMERCE_ADMIN_BASE_URL — shipping-service sits at /api/shippings,
 *     not /api/admin/shippings);
 *   - SHIPPED transition status body sends carrier + trackingNumber;
 *   - an unknown/future status enum parses without throwing (tolerant read).
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
  listShippings,
  updateShippingStatus,
  refreshTracking,
} from '@/features/ecommerce-ops/api/shippings-api';
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

const SHIPPING_LIST = {
  content: [
    {
      shippingId: 'ship-1',
      orderId: 'ord-1',
      userId: 'u-1',
      status: 'PREPARING',
      createdAt: '2026-06-14T00:00:00Z',
    },
  ],
  page: 0,
  size: 20,
  totalElements: 1,
};

// The producer's mutation response (PUT /status, POST /refresh-tracking) is the
// 3-field `UpdateShippingStatusResponse` PROJECTION — NOT a full Shipping. It
// has NO `orderId` / `createdAt`. The earlier fixture mocked a full Shipping,
// which green-washed TASK-PC-FE-129: the real wire shape failed `ShippingSchema`
// parse and turned a committed 200 into a false failure. (TASK-PC-FE-129)
const SHIPPING = {
  shippingId: 'ship-1',
  status: 'SHIPPED',
  updatedAt: '2026-06-14T01:00:00Z',
};

beforeEach(() => {
  cookieJar.clear();
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

describe('shippings-api — per-domain credential selection (§ 2.4.10)', () => {
  it('sends the domain-facing IAM OIDC ACCESS cookie as the bearer (NOT the operator token)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-OIDC-ACCESS-required-by-ecommerce');
    cookieJar.set(OPERATOR_COOKIE, 'OPERATOR-TOKEN-must-not-be-used');
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(SHIPPING_LIST));
    vi.stubGlobal('fetch', fetchMock);

    await listShippings({ page: 0, size: 20 });

    const [, init] = fetchMock.mock.calls[0];
    const headers = (init as RequestInit).headers as Record<string, string>;
    expect(headers.Authorization).toBe(
      'Bearer GAP-OIDC-ACCESS-required-by-ecommerce',
    );
    expect(headers.Authorization).not.toContain('OPERATOR-TOKEN-must-not-be-used');
  });

  it('uses getDomainFacingToken() and NEVER getOperatorToken() (pins the per-domain rule)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-OIDC-ACCESS');
    cookieJar.set(OPERATOR_COOKIE, 'OPERATOR-TOKEN');
    const getDomainFacingSpy = vi.spyOn(sessionModule, 'getDomainFacingToken');
    const getOperatorSpy = vi.spyOn(sessionModule, 'getOperatorToken');
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse(SHIPPING_LIST)));

    await listShippings();

    expect(getDomainFacingSpy).toHaveBeenCalled();
    expect(getOperatorSpy).not.toHaveBeenCalled();
  });

  it('throws 401 with NO fetch when the IAM session is absent', async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    const err = await listShippings().catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(401);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('sends NO X-Tenant-Id and NO Idempotency-Key on the status-change mutation', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-OIDC-ACCESS');
    const fetchMock = vi
      .fn()
      .mockResolvedValue(jsonResponse(SHIPPING));
    vi.stubGlobal('fetch', fetchMock);

    await updateShippingStatus('ship-1', {
      status: 'SHIPPED',
      carrier: 'CJ대한통운',
      trackingNumber: 'TRK-001',
    });

    const init = fetchMock.mock.calls[0][1] as RequestInit;
    const headers = init.headers as Record<string, string>;
    expect(headers['X-Tenant-Id']).toBeUndefined();
    expect(headers['Idempotency-Key']).toBeUndefined();
    expect(headers['X-Request-Id']).toBeTruthy();
  });
});

describe('shippings-api — endpoint wiring + base URL (ECOMMERCE_PUBLIC_BASE_URL + /shippings/**)', () => {
  beforeEach(() => cookieJar.set(ACCESS_COOKIE, 'GAP-OIDC-ACCESS'));

  it('list — GET PUBLIC base /shippings?... (NOT /api/admin/shippings)', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(SHIPPING_LIST));
    vi.stubGlobal('fetch', fetchMock);
    await listShippings({ status: 'PREPARING', size: 999, page: 2 });
    const [url, init] = fetchMock.mock.calls[0];
    expect((init as RequestInit).method).toBe('GET');
    const u = new URL(String(url));
    // Must use /api/shippings (public base), NOT /api/admin/shippings
    expect(u.pathname).toBe('/api/shippings');
    expect(u.searchParams.get('status')).toBe('PREPARING');
    expect(u.searchParams.get('size')).toBe('100'); // capped at 100
    expect(u.searchParams.get('page')).toBe('2');
    expect(String(url)).not.toContain('/api/admin/shippings');
  });

  it('updateShippingStatus — PUT PUBLIC base /shippings/{id}/status with status body', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(SHIPPING));
    vi.stubGlobal('fetch', fetchMock);
    const res = await updateShippingStatus('ship-1', {
      status: 'SHIPPED',
      carrier: 'CJ대한통운',
      trackingNumber: 'TRK-001',
    });
    expect(res.shippingId).toBe('ship-1');
    expect(res.status).toBe('SHIPPED');
    const [url, init] = fetchMock.mock.calls[0];
    expect(String(url)).toBe('http://ecommerce.local/api/shippings/ship-1/status');
    expect((init as RequestInit).method).toBe('PUT');
    const body = JSON.parse((init as RequestInit).body as string);
    expect(body.status).toBe('SHIPPED');
    expect(body.carrier).toBe('CJ대한통운');
    expect(body.trackingNumber).toBe('TRK-001');
  });

  it('SHIPPED transition sends both carrier and trackingNumber in body', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(SHIPPING));
    vi.stubGlobal('fetch', fetchMock);
    await updateShippingStatus('ship-1', {
      status: 'SHIPPED',
      carrier: 'CJ대한통운',
      trackingNumber: 'TRK-001',
    });
    const body = JSON.parse(
      (fetchMock.mock.calls[0][1] as RequestInit).body as string,
    );
    expect(body.carrier).toBe('CJ대한통운');
    expect(body.trackingNumber).toBe('TRK-001');
  });

  it('refreshTracking — POST PUBLIC base /shippings/{id}/refresh-tracking (empty body)', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(SHIPPING));
    vi.stubGlobal('fetch', fetchMock);
    const res = await refreshTracking('ship-1');
    expect(res.shippingId).toBe('ship-1');
    const [url, init] = fetchMock.mock.calls[0];
    expect(String(url)).toBe(
      'http://ecommerce.local/api/shippings/ship-1/refresh-tracking',
    );
    expect((init as RequestInit).method).toBe('POST');
  });
});

describe('shippings-api — ecommerce FLAT envelope + § 2.5 resilience', () => {
  beforeEach(() => cookieJar.set(ACCESS_COOKIE, 'GAP-OIDC-ACCESS'));

  it('401 → ApiError(401) whole-session re-login', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(ecomError('UNAUTHORIZED', 401)));
    const err = await listShippings().catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(401);
  });

  it('403 ACCESS_DENIED → ApiError(403) inline', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(ecomError('ACCESS_DENIED', 403)),
    );
    const err = await updateShippingStatus('ship-1', { status: 'SHIPPED' }).catch(
      (e) => e,
    );
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(403);
  });

  it('parses the FLAT { code, message } shape (404 SHIPPING_NOT_FOUND)', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(ecomError('SHIPPING_NOT_FOUND', 404, 'gone')),
    );
    const err = await updateShippingStatus('nope', { status: 'SHIPPED' }).catch(
      (e) => e,
    );
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(404);
    expect(err.code).toBe('SHIPPING_NOT_FOUND');
    expect(err.message).toBe('gone');
  });

  it('400 InvalidShipping (SHIPPED without carrier/tracking) → ApiError(400) inline', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(ecomError('InvalidShipping', 400)),
    );
    const err = await updateShippingStatus('ship-1', { status: 'SHIPPED' }).catch(
      (e) => e,
    );
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(400);
    expect(err.code).toBe('InvalidShipping');
  });

  it('409/422 INVALID_TRANSITION (non-linear jump) → ApiError inline', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(ecomError('INVALID_TRANSITION', 409)),
    );
    const err = await updateShippingStatus('ship-1', {
      status: 'DELIVERED',
    }).catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(409);
    expect(err.code).toBe('INVALID_TRANSITION');
  });

  it('a malformed / non-JSON error body does NOT crash (defensive parse)', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        new Response('not json', {
          status: 404,
          headers: { 'Content-Type': 'text/plain' },
        }),
      ),
    );
    const err = await updateShippingStatus('ship-1', { status: 'SHIPPED' }).catch(
      (e) => e,
    );
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(404);
    expect(err.code).toBe('HTTP_404');
  });

  it('503 → EcommerceUnavailableError (section degrades only)', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(ecomError('SERVICE_UNAVAILABLE', 503)),
    );
    const err = await listShippings().catch((e) => e);
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
    const err = await listShippings().catch((e) => e);
    expect(err).toBeInstanceOf(EcommerceUnavailableError);
    expect(err.reason).toBe('timeout');
  });

  it('an unknown/future status enum parses without throwing (tolerant read)', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        jsonResponse({ ...SHIPPING, status: 'FUTURE_V3', extra: 'x' }),
      ),
    );
    const res = await updateShippingStatus('ship-1', { status: 'IN_TRANSIT' });
    expect(res.status).toBe('FUTURE_V3');
  });

  // TASK-PC-FE-129 regression: the producer returns the 3-field projection
  // `{ shippingId, status, updatedAt }` — NO `orderId` / `createdAt`. Parsing
  // this with the full `ShippingSchema` (the previous bug) threw a ZodError that
  // `callEcommerce` swallowed into a NETWORK_ERROR, surfacing a FALSE failure to
  // the operator even though the backend committed the transition (200).
  it('a SHIPPED→IN_TRANSIT 200 with the 3-field projection (no orderId/createdAt) resolves — not a false failure', async () => {
    const projection = {
      shippingId: 'demo-ship-0002',
      status: 'IN_TRANSIT',
      updatedAt: '2026-06-23T11:16:18Z',
    };
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse(projection)));

    const res = await updateShippingStatus('demo-ship-0002', {
      status: 'IN_TRANSIT',
    });

    expect(res.shippingId).toBe('demo-ship-0002');
    expect(res.status).toBe('IN_TRANSIT');
    expect('orderId' in res).toBe(false);
    expect('createdAt' in res).toBe(false);
  });

  it('refresh-tracking also tolerates the 3-field projection response', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        jsonResponse({ shippingId: 'ship-1', status: 'IN_TRANSIT' }),
      ),
    );
    const res = await refreshTracking('ship-1');
    expect(res.shippingId).toBe('ship-1');
    expect(res.status).toBe('IN_TRANSIT');
  });
});
