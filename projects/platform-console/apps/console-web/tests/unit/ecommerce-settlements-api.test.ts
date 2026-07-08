import { describe, it, expect, vi, beforeEach } from 'vitest';

/**
 * `features/ecommerce-ops/api/settlements-api.ts` — the read core of
 * TASK-PC-FE-221 Phase A (ecommerce settlement operator surface — the 8th
 * ecommerce-ops facet).
 *
 * THE CENTRAL ASSERTION (console-integration-contract § 2.4.10):
 *   - every settlement call's bearer is the **domain-facing IAM OIDC token**
 *     (`getDomainFacingToken()` → the `console_access_token` cookie), NEVER
 *     the exchanged operator token;
 *   - the operator-token path is ABSENT (`getOperatorToken` NOT called);
 *   - the console sends NO `X-Tenant-Id` and NO `Idempotency-Key` (reads);
 *   - all requests go to ECOMMERCE_ADMIN_BASE_URL + `/settlements/**`
 *     (NOT ECOMMERCE_PUBLIC_BASE_URL — admin subtree, like sellers);
 *   - the ecommerce FLAT error envelope `{ code, message, timestamp }` is parsed;
 *   - 403 TENANT_FORBIDDEN → ApiError(403); 404 SETTLEMENT_NOT_FOUND →
 *     ApiError(404); 503/timeout → EcommerceUnavailableError (degrade).
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
  listAccruals,
  getSellerBalance,
  getCommissionRate,
  listPeriods,
  listPayouts,
} from '@/features/ecommerce-ops/api/settlements-api';
import { ApiError, EcommerceUnavailableError } from '@/shared/api/errors';
import { ACCESS_COOKIE, OPERATOR_COOKIE } from '@/shared/lib/session';

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}
/** ecommerce FLAT error envelope. */
function ecomError(code: string, status: number, message = 'err') {
  return new Response(
    JSON.stringify({ code, message, timestamp: '2026-06-14T00:00:00.000Z' }),
    { status, headers: { 'Content-Type': 'application/json' } },
  );
}

const ACCRUALS = {
  items: [
    {
      accrualId: 'ac-1',
      orderId: 'ord-1',
      paymentId: 'pay-1',
      sellerId: 'acme-corp',
      type: 'ACCRUAL',
      grossMinor: 100000,
      rateBps: 1000,
      commissionMinor: 10000,
      sellerNetMinor: 90000,
      occurredAt: '2026-06-14T00:00:00Z',
    },
  ],
  page: 0,
  size: 20,
  totalElements: 1,
};

const BALANCE = {
  sellerId: 'acme-corp',
  accruedNetMinor: 90000,
  platformCommissionMinor: 10000,
  grossMinor: 100000,
  accrualCount: 1,
  asOf: '2026-06-14T00:00:00Z',
};

const RATE = { sellerId: 'acme-corp', rateBps: 1000, source: 'PLATFORM_DEFAULT' };

const PERIODS = {
  items: [
    {
      periodId: '2026-06',
      from: '2026-06-01T00:00:00Z',
      to: '2026-06-30T23:59:59Z',
      status: 'OPEN',
      closedAt: null,
      sellerCount: 3,
    },
  ],
  page: 0,
  size: 20,
  totalElements: 1,
};

const PAYOUTS = {
  items: [
    {
      payoutId: 'po-1',
      sellerId: 'acme-corp',
      payableNetMinor: 90000,
      commissionMinor: 10000,
      accrualCount: 1,
      status: 'PENDING',
      payoutReference: null,
      paidAt: null,
    },
  ],
  page: 0,
  size: 20,
  totalElements: 1,
};

beforeEach(() => {
  cookieJar.clear();
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

describe('settlements-api — per-domain credential selection (§ 2.4.10)', () => {
  it('sends the domain-facing IAM OIDC ACCESS cookie as the bearer (NOT the operator token)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-OIDC-ACCESS-required-by-ecommerce');
    cookieJar.set(OPERATOR_COOKIE, 'OPERATOR-TOKEN-must-not-be-used');
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(ACCRUALS));
    vi.stubGlobal('fetch', fetchMock);

    await listAccruals({ page: 0, size: 20 });

    const [, init] = fetchMock.mock.calls[0];
    const headers = (init as RequestInit).headers as Record<string, string>;
    expect(headers.Authorization).toBe(
      'Bearer GAP-OIDC-ACCESS-required-by-ecommerce',
    );
    expect(headers.Authorization).not.toContain('OPERATOR-TOKEN-must-not-be-used');
  });

  it('uses getDomainFacingToken() and NEVER getOperatorToken()', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-OIDC-ACCESS');
    cookieJar.set(OPERATOR_COOKIE, 'OPERATOR-TOKEN');
    const getDomainFacingSpy = vi.spyOn(sessionModule, 'getDomainFacingToken');
    const getOperatorSpy = vi.spyOn(sessionModule, 'getOperatorToken');
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse(ACCRUALS)));

    await listAccruals();

    expect(getDomainFacingSpy).toHaveBeenCalled();
    expect(getOperatorSpy).not.toHaveBeenCalled();
  });

  it('throws 401 with NO fetch when the IAM session is absent', async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    const err = await listAccruals().catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(401);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('sends NO X-Tenant-Id, NO Idempotency-Key, NO body on a read', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-OIDC-ACCESS');
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(BALANCE));
    vi.stubGlobal('fetch', fetchMock);

    await getSellerBalance('acme-corp');

    const init = fetchMock.mock.calls[0][1] as RequestInit;
    const headers = init.headers as Record<string, string>;
    expect(headers['X-Tenant-Id']).toBeUndefined();
    expect(headers['Idempotency-Key']).toBeUndefined();
    expect(init.method).toBe('GET');
    expect(init.body).toBeUndefined();
  });
});

describe('settlements-api — endpoint wiring + base URL (ADMIN subtree)', () => {
  beforeEach(() => cookieJar.set(ACCESS_COOKIE, 'GAP-OIDC-ACCESS'));

  it('accruals — GET ADMIN /settlements/accruals?sellerId=&orderId=&page=&size=', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(ACCRUALS));
    vi.stubGlobal('fetch', fetchMock);
    await listAccruals({ sellerId: 'acme-corp', orderId: 'ord-1', page: 2, size: 999 });
    const [url, init] = fetchMock.mock.calls[0];
    expect((init as RequestInit).method).toBe('GET');
    const u = new URL(String(url));
    expect(u.pathname).toBe('/api/admin/settlements/accruals');
    expect(u.searchParams.get('sellerId')).toBe('acme-corp');
    expect(u.searchParams.get('orderId')).toBe('ord-1');
    expect(u.searchParams.get('page')).toBe('2');
    expect(u.searchParams.get('size')).toBe('100'); // capped at 100
    // NOT the public base
    expect(String(url)).toContain('/api/admin/settlements');
  });

  it('accruals — omits sellerId/orderId when unset', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(ACCRUALS));
    vi.stubGlobal('fetch', fetchMock);
    await listAccruals();
    const u = new URL(String(fetchMock.mock.calls[0][0]));
    expect(u.searchParams.has('sellerId')).toBe(false);
    expect(u.searchParams.has('orderId')).toBe(false);
  });

  it('seller balance — GET ADMIN /settlements/sellers/{id}/balance', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(BALANCE));
    vi.stubGlobal('fetch', fetchMock);
    const b = await getSellerBalance('acme-corp');
    expect(b.accruedNetMinor).toBe(90000);
    expect(String(fetchMock.mock.calls[0][0])).toBe(
      'http://ecommerce.local/api/admin/settlements/sellers/acme-corp/balance',
    );
  });

  it('seller balance — URL-encodes the sellerId', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(BALANCE));
    vi.stubGlobal('fetch', fetchMock);
    await getSellerBalance('acme corp');
    expect(String(fetchMock.mock.calls[0][0])).toBe(
      'http://ecommerce.local/api/admin/settlements/sellers/acme%20corp/balance',
    );
  });

  it('commission rate — GET ADMIN /settlements/commission-rates/{id}', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(RATE));
    vi.stubGlobal('fetch', fetchMock);
    const r = await getCommissionRate('acme-corp');
    expect(r.rateBps).toBe(1000);
    expect(String(fetchMock.mock.calls[0][0])).toBe(
      'http://ecommerce.local/api/admin/settlements/commission-rates/acme-corp',
    );
  });

  it('periods — GET ADMIN /settlements/periods?page=&size=', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(PERIODS));
    vi.stubGlobal('fetch', fetchMock);
    await listPeriods({ page: 1, size: 20 });
    const u = new URL(String(fetchMock.mock.calls[0][0]));
    expect(u.pathname).toBe('/api/admin/settlements/periods');
    expect(u.searchParams.get('page')).toBe('1');
    expect(u.searchParams.get('size')).toBe('20');
  });

  it('payouts — GET ADMIN /settlements/periods/{id}/payouts?page=&size=', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(PAYOUTS));
    vi.stubGlobal('fetch', fetchMock);
    await listPayouts('2026-06', { page: 0, size: 20 });
    const u = new URL(String(fetchMock.mock.calls[0][0]));
    expect(u.pathname).toBe('/api/admin/settlements/periods/2026-06/payouts');
    expect(u.searchParams.get('page')).toBe('0');
    expect(u.searchParams.get('size')).toBe('20');
  });
});

describe('settlements-api — FLAT envelope + § 2.5 resilience taxonomy', () => {
  beforeEach(() => cookieJar.set(ACCESS_COOKIE, 'GAP-OIDC-ACCESS'));

  it('403 TENANT_FORBIDDEN → ApiError(403) inline (not a degrade)', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(ecomError('TENANT_FORBIDDEN', 403)));
    const err = await listAccruals().catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(403);
    expect(err.code).toBe('TENANT_FORBIDDEN');
  });

  it('404 SETTLEMENT_NOT_FOUND → ApiError(404) (cross-tenant / cross-seller)', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(ecomError('SETTLEMENT_NOT_FOUND', 404, 'gone')),
    );
    const err = await getSellerBalance('nope').catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(404);
    expect(err.code).toBe('SETTLEMENT_NOT_FOUND');
  });

  it('503 → EcommerceUnavailableError (section degrades only)', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(ecomError('SERVICE_UNAVAILABLE', 503)));
    const err = await listPeriods().catch((e) => e);
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
    const err = await listAccruals().catch((e) => e);
    expect(err).toBeInstanceOf(EcommerceUnavailableError);
    expect(err.reason).toBe('timeout');
  });

  it('a REVERSAL (negative) accrual + unknown/future field parse tolerantly', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        jsonResponse({
          ...ACCRUALS,
          items: [
            {
              ...ACCRUALS.items[0],
              type: 'REVERSAL',
              commissionMinor: -10000,
              sellerNetMinor: -90000,
              futureField: 'v2',
            },
          ],
        }),
      ),
    );
    const res = await listAccruals();
    expect(res.items[0].type).toBe('REVERSAL');
    expect(res.items[0].commissionMinor).toBe(-10000);
    expect((res.items[0] as Record<string, unknown>).futureField).toBe('v2');
  });
});
