import { describe, it, expect, vi, beforeEach } from 'vitest';

/**
 * `features/ecommerce-ops/api/sellers-api.ts` — security-critical core of
 * TASK-PC-FE-090 (ecommerce sellers operator surface — ADR-MONO-031 § 2.4.10 7th area).
 *
 * THE CENTRAL ASSERTION (console-integration-contract § 2.4.10.5):
 *   - every seller call's bearer is the **domain-facing IAM OIDC token**
 *     (`getDomainFacingToken()` → the `console_access_token` cookie), NEVER
 *     the exchanged operator token;
 *   - the operator-token path is ABSENT (`getOperatorToken` NOT called — pinned
 *     so a future blanket-apply refactor cannot break it);
 *   - the console sends NO `X-Tenant-Id` (ecommerce resolves tenant from the
 *     JWT `tenant_id` claim producer-side);
 *   - NO `Idempotency-Key` on any mutation (the producer defines none — § 2.4.10);
 *   - all requests go to ECOMMERCE_ADMIN_BASE_URL + `/sellers/**`
 *     (NOT ECOMMERCE_PUBLIC_BASE_URL — sellers live under the admin subtree
 *     `/api/admin/sellers`, unlike promotions/notifications/shippings);
 *   - the ecommerce FLAT error envelope `{ code, message, timestamp }` is
 *     parsed correctly;
 *   - 401 → ApiError(401); 403 → ApiError(403); 404 → ApiError(404) inline;
 *     409 → ApiError(409) inline; 503/timeout → EcommerceUnavailableError.
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
  listSellers,
  getSeller,
  registerSeller,
} from '@/features/ecommerce-ops/api/sellers-api';
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

const SELLER_LIST = {
  content: [
    {
      sellerId: 'acme-corp',
      displayName: 'Acme Corporation',
      status: 'ACTIVE',
      createdAt: '2026-06-14T00:00:00Z',
    },
  ],
  page: 0,
  size: 20,
  totalElements: 1,
};

const SELLER_DETAIL = {
  sellerId: 'acme-corp',
  displayName: 'Acme Corporation',
  status: 'ACTIVE',
  createdAt: '2026-06-14T00:00:00Z',
  updatedAt: '2026-06-14T00:00:00Z',
};

const VALID_REGISTER_BODY = {
  sellerId: 'acme-corp',
  displayName: 'Acme Corporation',
};

beforeEach(() => {
  cookieJar.clear();
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

describe('sellers-api — per-domain credential selection (§ 2.4.10.5)', () => {
  it('sends the domain-facing IAM OIDC ACCESS cookie as the bearer (NOT the operator token)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-OIDC-ACCESS-required-by-ecommerce');
    cookieJar.set(OPERATOR_COOKIE, 'OPERATOR-TOKEN-must-not-be-used');
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(SELLER_LIST));
    vi.stubGlobal('fetch', fetchMock);

    await listSellers({ page: 0, size: 20 });

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
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse(SELLER_LIST)));

    await listSellers();

    expect(getDomainFacingSpy).toHaveBeenCalled();
    expect(getOperatorSpy).not.toHaveBeenCalled();
  });

  it('throws 401 with NO fetch when the IAM session is absent', async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    const err = await listSellers().catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(401);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('sends NO X-Tenant-Id and NO Idempotency-Key on a mutation', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-OIDC-ACCESS');
    const fetchMock = vi
      .fn()
      .mockResolvedValue(jsonResponse({ sellerId: 'acme-corp' }, 201));
    vi.stubGlobal('fetch', fetchMock);

    await registerSeller(VALID_REGISTER_BODY);

    const init = fetchMock.mock.calls[0][1] as RequestInit;
    const headers = init.headers as Record<string, string>;
    expect(headers['X-Tenant-Id']).toBeUndefined();
    expect(headers['Idempotency-Key']).toBeUndefined();
    expect(headers['X-Request-Id']).toBeTruthy();
  });
});

describe('sellers-api — endpoint wiring + base URL (ECOMMERCE_ADMIN_BASE_URL + /sellers/**)', () => {
  beforeEach(() => cookieJar.set(ACCESS_COOKIE, 'GAP-OIDC-ACCESS'));

  it('list — GET ADMIN base /sellers?page=&size= (NOT ECOMMERCE_PUBLIC_BASE_URL)', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(SELLER_LIST));
    vi.stubGlobal('fetch', fetchMock);
    await listSellers({ size: 999, page: 2 });
    const [url, init] = fetchMock.mock.calls[0];
    expect((init as RequestInit).method).toBe('GET');
    const u = new URL(String(url));
    // CRITICAL: must use /api/admin/sellers (admin base), NOT /api/sellers
    expect(u.pathname).toBe('/api/admin/sellers');
    expect(u.searchParams.get('size')).toBe('100'); // capped at 100
    expect(u.searchParams.get('page')).toBe('2');
    // Confirm NOT using public base
    expect(String(url)).not.toMatch(/\/api\/sellers(?!\s)/);
    expect(String(url)).toContain('/api/admin/sellers');
  });

  it('detail — GET ADMIN base /sellers/{sellerId}', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValue(jsonResponse(SELLER_DETAIL));
    vi.stubGlobal('fetch', fetchMock);
    const d = await getSeller('acme-corp');
    expect(d.displayName).toBe('Acme Corporation');
    const [url] = fetchMock.mock.calls[0];
    expect(String(url)).toBe(
      'http://ecommerce.local/api/admin/sellers/acme-corp',
    );
  });

  it('register — POST ADMIN base /sellers with { sellerId, displayName } → 201 { sellerId }', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValue(jsonResponse({ sellerId: 'acme-corp' }, 201));
    vi.stubGlobal('fetch', fetchMock);
    const res = await registerSeller(VALID_REGISTER_BODY);
    expect(res.sellerId).toBe('acme-corp');
    const [url, init] = fetchMock.mock.calls[0];
    expect(String(url)).toBe('http://ecommerce.local/api/admin/sellers');
    expect((init as RequestInit).method).toBe('POST');
    const body = JSON.parse((init as RequestInit).body as string);
    expect(body.sellerId).toBe('acme-corp');
    expect(body.displayName).toBe('Acme Corporation');
  });

  it('register — URL-encodes the sellerId in detail path (special chars)', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValue(jsonResponse({ ...SELLER_DETAIL, sellerId: 'acme corp' }));
    vi.stubGlobal('fetch', fetchMock);
    await getSeller('acme corp');
    const [url] = fetchMock.mock.calls[0];
    expect(String(url)).toBe('http://ecommerce.local/api/admin/sellers/acme%20corp');
  });
});

describe('sellers-api — ecommerce FLAT envelope + § 2.5 resilience', () => {
  beforeEach(() => cookieJar.set(ACCESS_COOKIE, 'GAP-OIDC-ACCESS'));

  it('401 → ApiError(401) whole-session re-login', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(ecomError('UNAUTHORIZED', 401)));
    const err = await listSellers().catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(401);
  });

  it('403 ACCESS_DENIED → ApiError(403) inline', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(ecomError('ACCESS_DENIED', 403)),
    );
    const err = await registerSeller(VALID_REGISTER_BODY).catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(403);
  });

  it('parses the FLAT { code, message } shape (404 SELLER_NOT_FOUND)', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(ecomError('SELLER_NOT_FOUND', 404, 'gone')),
    );
    const err = await getSeller('nope').catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(404);
    expect(err.code).toBe('SELLER_NOT_FOUND');
    expect(err.message).toBe('gone');
  });

  it('409 CONFLICT (duplicate sellerId) → ApiError(409) inline', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(ecomError('CONFLICT', 409, 'duplicate sellerId')),
    );
    const err = await registerSeller(VALID_REGISTER_BODY).catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(409);
    expect(err.code).toBe('CONFLICT');
  });

  it('400 VALIDATION_ERROR → ApiError(400) inline', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(ecomError('VALIDATION_ERROR', 400)),
    );
    const err = await registerSeller(VALID_REGISTER_BODY).catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(400);
    expect(err.code).toBe('VALIDATION_ERROR');
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
    const err = await getSeller('nope').catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(404);
    expect(err.code).toBe('HTTP_404');
  });

  it('503 → EcommerceUnavailableError (section degrades only)', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(ecomError('SERVICE_UNAVAILABLE', 503)),
    );
    const err = await listSellers().catch((e) => e);
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
    const err = await listSellers().catch((e) => e);
    expect(err).toBeInstanceOf(EcommerceUnavailableError);
    expect(err.reason).toBe('timeout');
  });

  it('an unknown/future field in the response parses without throwing (tolerant read)', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        jsonResponse({ ...SELLER_DETAIL, futureField: 'future_v2', extra: 'x' }),
      ),
    );
    const d = await getSeller('acme-corp');
    expect(d.sellerId).toBe('acme-corp');
    expect((d as Record<string, unknown>)['futureField']).toBe('future_v2');
  });
});
