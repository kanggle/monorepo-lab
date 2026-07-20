import { describe, it, expect, vi, beforeEach } from 'vitest';

/**
 * `features/ecommerce-ops/api/promotions-api.ts` — security-critical core of
 * TASK-PC-FE-086 (ecommerce promotions operator surface — ADR-031 Phase 3b).
 *
 * THE CENTRAL ASSERTION (console-integration-contract § 2.4.10, inheriting the
 * non-IAM § 2.4.5 rules — EXACT MIRROR of the products-api test):
 *   - every promotion call's bearer is the **domain-facing IAM OIDC token**
 *     (`getDomainFacingToken()` → the `console_access_token` cookie), NEVER
 *     the exchanged operator token;
 *   - the operator-token path is ABSENT (`getOperatorToken` NOT called — pinned
 *     so a future blanket-apply refactor cannot break it);
 *   - the console sends NO `X-Tenant-Id` (ecommerce resolves tenant from the
 *     JWT `tenant_id` claim producer-side);
 *   - `issueCoupons` sends an `Idempotency-Key` (TASK-BE-536, the producer now
 *     requires it there); every other mutation still sends none (producer
 *     defines none — § 2.4.10);
 *   - the ecommerce FLAT error envelope `{ code, message, timestamp }` is
 *     parsed (NOT wms's nested `{ error: { code } }`);
 *   - 401 → ApiError(401); 403 → ApiError(403); 404/422 → ApiError inline;
 *     503/timeout → EcommerceUnavailableError (section degrades only);
 *   - update uses PUT (full replace), NOT PATCH;
 *   - all requests go to ECOMMERCE_PUBLIC_BASE_URL + `/promotions/**`
 *     (NOT ECOMMERCE_ADMIN_BASE_URL — promotion-service sits at /api/promotions,
 *     not /api/admin/promotions).
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
  listPromotions,
  getPromotion,
  createPromotion,
  updatePromotion,
  deletePromotion,
  issueCoupons,
} from '@/features/ecommerce-ops/api/promotions-api';
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
    JSON.stringify({ code, message, timestamp: '2026-06-14T00:00:00.000Z' }),
    { status, headers: { 'Content-Type': 'application/json' } },
  );
}

const PROMOTION_LIST = {
  content: [
    {
      promotionId: 'promo-1',
      name: 'Summer Sale',
      discountType: 'PERCENTAGE',
      discountValue: 20,
      issuedCount: 5,
      maxIssuanceCount: 100,
      startDate: '2026-07-01',
      endDate: '2026-07-31',
      status: 'SCHEDULED',
    },
  ],
  page: 0,
  size: 20,
  totalElements: 1,
};

const PROMOTION_DETAIL = {
  promotionId: 'promo-1',
  name: 'Summer Sale',
  description: 'A summer sale',
  discountType: 'PERCENTAGE',
  discountValue: 20,
  maxDiscountAmount: 5000,
  issuedCount: 5,
  maxIssuanceCount: 100,
  startDate: '2026-07-01',
  endDate: '2026-07-31',
  status: 'SCHEDULED',
  createdAt: '2026-06-01T00:00:00Z',
  updatedAt: '2026-06-14T00:00:00Z',
};

const VALID_CREATE_BODY = {
  name: 'Summer Sale',
  description: 'Summer',
  discountType: 'PERCENTAGE' as const,
  discountValue: 20,
  maxDiscountAmount: 5000,
  maxIssuanceCount: 100,
  startDate: '2026-07-01',
  endDate: '2026-07-31',
};

beforeEach(() => {
  cookieJar.clear();
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

describe('promotions-api — per-domain credential selection (§ 2.4.10)', () => {
  it('sends the domain-facing IAM OIDC ACCESS cookie as the bearer (NOT the operator token)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-OIDC-ACCESS-required-by-ecommerce');
    cookieJar.set(OPERATOR_COOKIE, 'OPERATOR-TOKEN-must-not-be-used');
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(PROMOTION_LIST));
    vi.stubGlobal('fetch', fetchMock);

    await listPromotions({ page: 0, size: 20 });

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
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse(PROMOTION_LIST)));

    await listPromotions();

    expect(getDomainFacingSpy).toHaveBeenCalled();
    expect(getOperatorSpy).not.toHaveBeenCalled();
  });

  it('throws 401 with NO fetch when the IAM session is absent', async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    const err = await listPromotions().catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(401);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('sends NO X-Tenant-Id and NO Idempotency-Key on a mutation (tenant via JWT claim; producer has no idem key)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-OIDC-ACCESS');
    const fetchMock = vi
      .fn()
      .mockResolvedValue(jsonResponse({ promotionId: 'promo-9' }, 201));
    vi.stubGlobal('fetch', fetchMock);

    await createPromotion(VALID_CREATE_BODY);

    const init = fetchMock.mock.calls[0][1] as RequestInit;
    const headers = init.headers as Record<string, string>;
    expect(headers['X-Tenant-Id']).toBeUndefined();
    expect(headers['Idempotency-Key']).toBeUndefined();
    expect(headers['X-Operator-Reason']).toBeUndefined();
    expect(headers['X-Request-Id']).toBeTruthy();
  });
});

describe('promotions-api — endpoint wiring + base URL (ECOMMERCE_PUBLIC_BASE_URL + /promotions/**)', () => {
  beforeEach(() => cookieJar.set(ACCESS_COOKIE, 'GAP-OIDC-ACCESS'));

  it('list — GET PUBLIC base /promotions?... (NOT /api/admin/promotions)', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(PROMOTION_LIST));
    vi.stubGlobal('fetch', fetchMock);
    await listPromotions({ status: 'ACTIVE', size: 999, page: 2 });
    const [url, init] = fetchMock.mock.calls[0];
    expect((init as RequestInit).method).toBe('GET');
    const u = new URL(String(url));
    // Must use /api/promotions (public base), NOT /api/admin/promotions
    expect(u.pathname).toBe('/api/promotions');
    expect(u.searchParams.get('status')).toBe('ACTIVE');
    expect(u.searchParams.get('size')).toBe('100'); // capped at 100
    expect(u.searchParams.get('page')).toBe('2');
    // Confirm NOT using admin base
    expect(String(url)).not.toContain('/api/admin/promotions');
  });

  it('detail — GET PUBLIC base /promotions/{id}', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValue(jsonResponse(PROMOTION_DETAIL));
    vi.stubGlobal('fetch', fetchMock);
    const d = await getPromotion('promo-1');
    expect(d.name).toBe('Summer Sale');
    const [url] = fetchMock.mock.calls[0];
    expect(String(url)).toBe('http://ecommerce.local/api/promotions/promo-1');
  });

  it('create — POST PUBLIC base /promotions with CreatePromotionBody', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValue(jsonResponse({ promotionId: 'promo-9' }, 201));
    vi.stubGlobal('fetch', fetchMock);
    const res = await createPromotion(VALID_CREATE_BODY);
    expect(res.promotionId).toBe('promo-9');
    const [url, init] = fetchMock.mock.calls[0];
    expect(String(url)).toBe('http://ecommerce.local/api/promotions');
    expect((init as RequestInit).method).toBe('POST');
    const body = JSON.parse((init as RequestInit).body as string);
    expect(body.name).toBe('Summer Sale');
    expect(body.discountType).toBe('PERCENTAGE');
  });

  it('update — PUT (full replace) PUBLIC base /promotions/{id} (NOT PATCH)', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValue(jsonResponse({ promotionId: 'promo-1' }));
    vi.stubGlobal('fetch', fetchMock);
    await updatePromotion('promo-1', VALID_CREATE_BODY);
    const [url, init] = fetchMock.mock.calls[0];
    expect(String(url)).toBe('http://ecommerce.local/api/promotions/promo-1');
    // CRITICAL: must be PUT, NOT PATCH
    expect((init as RequestInit).method).toBe('PUT');
    const body = JSON.parse((init as RequestInit).body as string);
    expect(body.discountValue).toBe(20);
  });

  it('delete — DELETE PUBLIC base /promotions/{id} (204, no body parse)', async () => {
    const fetchMock = vi.fn().mockResolvedValue(noContent());
    vi.stubGlobal('fetch', fetchMock);
    await expect(deletePromotion('promo-1')).resolves.toBeUndefined();
    const [url, init] = fetchMock.mock.calls[0];
    expect(String(url)).toBe('http://ecommerce.local/api/promotions/promo-1');
    expect((init as RequestInit).method).toBe('DELETE');
  });

  it('issueCoupons — POST /promotions/{id}/coupons/issue with { userIds }', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValue(jsonResponse({ issuedCount: 3 }, 201));
    vi.stubGlobal('fetch', fetchMock);
    const res = await issueCoupons('promo-1', {
      userIds: ['u-1', 'u-2', 'u-3'],
    });
    expect(res.issuedCount).toBe(3);
    const [url, init] = fetchMock.mock.calls[0];
    expect(String(url)).toBe(
      'http://ecommerce.local/api/promotions/promo-1/coupons/issue',
    );
    expect((init as RequestInit).method).toBe('POST');
    const body = JSON.parse((init as RequestInit).body as string);
    expect(body.userIds).toEqual(['u-1', 'u-2', 'u-3']);
    // TASK-BE-536: the producer now requires Idempotency-Key on this endpoint.
    const headers = (init as RequestInit).headers as Record<string, string>;
    expect(headers['Idempotency-Key']).toBeTruthy();
  });
});

describe('promotions-api — ecommerce FLAT envelope + § 2.5 resilience', () => {
  beforeEach(() => cookieJar.set(ACCESS_COOKIE, 'GAP-OIDC-ACCESS'));

  it('401 → ApiError(401) whole-session re-login', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(ecomError('UNAUTHORIZED', 401)));
    const err = await listPromotions().catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(401);
  });

  it('403 ACCESS_DENIED → ApiError(403) inline', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(ecomError('ACCESS_DENIED', 403)));
    const err = await createPromotion(VALID_CREATE_BODY).catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(403);
  });

  it('parses the FLAT { code, message } shape (404 PROMOTION_NOT_FOUND)', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(ecomError('PROMOTION_NOT_FOUND', 404, 'gone')));
    const err = await getPromotion('nope').catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(404);
    expect(err.code).toBe('PROMOTION_NOT_FOUND');
    expect(err.message).toBe('gone');
  });

  it('400 VALIDATION_ERROR → ApiError(400) inline', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(ecomError('VALIDATION_ERROR', 400)),
    );
    const err = await createPromotion(VALID_CREATE_BODY).catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(400);
    expect(err.code).toBe('VALIDATION_ERROR');
  });

  it('422 PROMOTION_ALREADY_ENDED → ApiError(422) inline', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(ecomError('PROMOTION_ALREADY_ENDED', 422)),
    );
    const err = await updatePromotion('promo-1', VALID_CREATE_BODY).catch(
      (e) => e,
    );
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(422);
    expect(err.code).toBe('PROMOTION_ALREADY_ENDED');
  });

  it('422 COUPON_LIMIT_EXCEEDED → ApiError(422) inline', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(ecomError('COUPON_LIMIT_EXCEEDED', 422)),
    );
    const err = await issueCoupons('promo-1', { userIds: ['u-1'] }).catch(
      (e) => e,
    );
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(422);
    expect(err.code).toBe('COUPON_LIMIT_EXCEEDED');
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
    const err = await getPromotion('promo-1').catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(404);
    expect(err.code).toBe('HTTP_404');
  });

  it('503 → EcommerceUnavailableError (section degrades only)', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(ecomError('SERVICE_UNAVAILABLE', 503)),
    );
    const err = await listPromotions().catch((e) => e);
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
    const err = await listPromotions().catch((e) => e);
    expect(err).toBeInstanceOf(EcommerceUnavailableError);
    expect(err.reason).toBe('timeout');
  });

  it('an unknown/future status enum parses without throwing (tolerant read)', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        jsonResponse({ ...PROMOTION_DETAIL, status: 'FUTURE_V2', extra: 'x' }),
      ),
    );
    const d = await getPromotion('promo-1');
    expect(d.status).toBe('FUTURE_V2');
  });
});
