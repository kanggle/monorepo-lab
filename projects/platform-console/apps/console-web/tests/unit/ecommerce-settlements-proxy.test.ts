import { describe, it, expect, vi, beforeEach } from 'vitest';

/**
 * Same-origin ecommerce-ops settlement proxy route handlers (TASK-PC-FE-221
 * Phase A) — all GET (READ-ONLY):
 *   - domain-facing IAM OIDC token (NOT the operator token); NO X-Tenant-Id;
 *     targets ECOMMERCE_ADMIN_BASE_URL/settlements/**;
 *   - no IAM session → 401 (no upstream call); 503 → 503; 404 SETTLEMENT_NOT_FOUND
 *     → passthrough; query passthrough (page/size + accrual filters).
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

import { GET as accrualsGET } from '@/app/api/ecommerce/settlements/accruals/route';
import { GET as balanceGET } from '@/app/api/ecommerce/settlements/sellers/[id]/balance/route';
import { GET as rateGET } from '@/app/api/ecommerce/settlements/commission-rates/[id]/route';
import { GET as periodsGET } from '@/app/api/ecommerce/settlements/periods/route';
import { GET as payoutsGET } from '@/app/api/ecommerce/settlements/periods/[id]/payouts/route';
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

const ACCRUALS = { items: [], page: 0, size: 20, totalElements: 0 };
const BALANCE = {
  sellerId: 'acme-corp',
  accruedNetMinor: 90000,
  platformCommissionMinor: 10000,
  grossMinor: 100000,
  accrualCount: 1,
  asOf: '2026-06-14T00:00:00Z',
};
const RATE = { sellerId: 'acme-corp', rateBps: 1000, source: 'PLATFORM_DEFAULT' };
const PERIODS = { items: [], page: 0, size: 20, totalElements: 0 };
const PAYOUTS = { items: [], page: 0, size: 20, totalElements: 0 };

beforeEach(() => {
  cookieJar.clear();
  vi.unstubAllGlobals();
});

describe('GET /api/ecommerce/settlements/accruals', () => {
  it('passes through + targets ADMIN base with filters', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    cookieJar.set(OPERATOR_COOKIE, 'OP-MUST-NOT-USE');
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(ACCRUALS));
    vi.stubGlobal('fetch', fetchMock);
    const res = await accrualsGET(
      new Request(
        'http://console.local/api/ecommerce/settlements/accruals?sellerId=acme-corp&orderId=ord-1&page=0&size=20',
      ),
    );
    expect(res.status).toBe(200);
    const [url, init] = fetchMock.mock.calls[0];
    const u = new URL(String(url));
    expect(u.pathname).toBe('/api/admin/settlements/accruals');
    expect(u.searchParams.get('sellerId')).toBe('acme-corp');
    expect(u.searchParams.get('orderId')).toBe('ord-1');
    const h = (init as RequestInit).headers as Record<string, string>;
    expect(h.Authorization).toBe('Bearer GAP-ACCESS');
    expect(h.Authorization).not.toContain('OP-MUST-NOT-USE');
    expect(h['X-Tenant-Id']).toBeUndefined();
  });

  it('no IAM session → 401 (no upstream call)', async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    const res = await accrualsGET(
      new Request('http://console.local/api/ecommerce/settlements/accruals'),
    );
    expect(res.status).toBe(401);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('503 → 503 (section degrades only)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(ecomError('SERVICE_UNAVAILABLE', 503)));
    const res = await accrualsGET(
      new Request('http://console.local/api/ecommerce/settlements/accruals'),
    );
    expect(res.status).toBe(503);
  });
});

describe('GET /api/ecommerce/settlements/sellers/{id}/balance', () => {
  beforeEach(() => cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS'));

  it('passes through the balance + targets ADMIN base', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(BALANCE));
    vi.stubGlobal('fetch', fetchMock);
    const res = await balanceGET(
      new Request('http://console.local/api/ecommerce/settlements/sellers/acme-corp/balance'),
      { params: Promise.resolve({ id: 'acme-corp' }) },
    );
    expect(res.status).toBe(200);
    const body = await res.json();
    expect(body.sellerId).toBe('acme-corp');
    expect(String(fetchMock.mock.calls[0][0])).toBe(
      'http://ecommerce.local/api/admin/settlements/sellers/acme-corp/balance',
    );
  });

  it('404 SETTLEMENT_NOT_FOUND → 404 passthrough', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(ecomError('SETTLEMENT_NOT_FOUND', 404)));
    const res = await balanceGET(
      new Request('http://console.local/api/ecommerce/settlements/sellers/nope/balance'),
      { params: Promise.resolve({ id: 'nope' }) },
    );
    expect(res.status).toBe(404);
    const body = await res.json();
    expect(body.code).toBe('SETTLEMENT_NOT_FOUND');
  });
});

describe('GET /api/ecommerce/settlements/commission-rates/{id}', () => {
  it('passes through the rate', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(RATE));
    vi.stubGlobal('fetch', fetchMock);
    const res = await rateGET(
      new Request('http://console.local/api/ecommerce/settlements/commission-rates/acme-corp'),
      { params: Promise.resolve({ id: 'acme-corp' }) },
    );
    expect(res.status).toBe(200);
    const body = await res.json();
    expect(body.rateBps).toBe(1000);
    expect(String(fetchMock.mock.calls[0][0])).toBe(
      'http://ecommerce.local/api/admin/settlements/commission-rates/acme-corp',
    );
  });
});

describe('GET /api/ecommerce/settlements/periods', () => {
  it('passes through the periods list', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(PERIODS));
    vi.stubGlobal('fetch', fetchMock);
    const res = await periodsGET(
      new Request('http://console.local/api/ecommerce/settlements/periods?page=0&size=20'),
    );
    expect(res.status).toBe(200);
    expect(String(fetchMock.mock.calls[0][0])).toContain(
      '/api/admin/settlements/periods',
    );
  });
});

describe('GET /api/ecommerce/settlements/periods/{id}/payouts', () => {
  it('passes through + targets ADMIN base for the period', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(PAYOUTS));
    vi.stubGlobal('fetch', fetchMock);
    const res = await payoutsGET(
      new Request('http://console.local/api/ecommerce/settlements/periods/2026-06/payouts?page=0&size=20'),
      { params: Promise.resolve({ id: '2026-06' }) },
    );
    expect(res.status).toBe(200);
    const u = new URL(String(fetchMock.mock.calls[0][0]));
    expect(u.pathname).toBe('/api/admin/settlements/periods/2026-06/payouts');
    expect(u.searchParams.get('page')).toBe('0');
    expect(u.searchParams.get('size')).toBe('20');
  });

  it('404 SETTLEMENT_NOT_FOUND → 404 passthrough', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(ecomError('SETTLEMENT_NOT_FOUND', 404)));
    const res = await payoutsGET(
      new Request('http://console.local/api/ecommerce/settlements/periods/nope/payouts'),
      { params: Promise.resolve({ id: 'nope' }) },
    );
    expect(res.status).toBe(404);
    const body = await res.json();
    expect(body.code).toBe('SETTLEMENT_NOT_FOUND');
  });
});
