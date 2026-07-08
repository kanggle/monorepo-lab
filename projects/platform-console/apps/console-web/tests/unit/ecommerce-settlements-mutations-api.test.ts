import { describe, it, expect, vi, beforeEach } from 'vitest';

/**
 * `features/ecommerce-ops/api/settlements-api.ts` mutations (TASK-PC-FE-221
 * Phase B): setCommissionRate / openPeriod / closePeriod / executePayouts.
 *
 * Asserts per mutation:
 *   - method + URL (ECOMMERCE_ADMIN_BASE_URL + /settlements/**);
 *   - body-bearing calls (PUT rate, POST open) send a JSON body + Content-Type;
 *   - bodyless calls (POST close, POST execute) send NO body + NO Content-Type;
 *   - domain-facing token; NO X-Tenant-Id; NO Idempotency-Key;
 *   - producer 422/409 → ApiError(status) inline (COMMISSION_RATE_INVALID /
 *     PERIOD_WINDOW_INVALID / PERIOD_ALREADY_CLOSED / PERIOD_NOT_CLOSED).
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
  setCommissionRate,
  openPeriod,
  closePeriod,
  executePayouts,
} from '@/features/ecommerce-ops/api/settlements-api';
import { ApiError } from '@/shared/api/errors';
import { ACCESS_COOKIE, OPERATOR_COOKIE } from '@/shared/lib/session';

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}
function ecomError(code: string, status: number) {
  return new Response(
    JSON.stringify({ code, message: 'e', timestamp: 't' }),
    { status, headers: { 'Content-Type': 'application/json' } },
  );
}

const RATE = { sellerId: 'acme-corp', rateBps: 1500, source: 'SELLER_OVERRIDE' };
const PERIOD = {
  periodId: '2026-07',
  from: '2026-07-01T00:00:00Z',
  to: '2026-08-01T00:00:00Z',
  status: 'OPEN',
  closedAt: null,
  sellerCount: null,
};
const CLOSE_RESULT = {
  ...PERIOD,
  status: 'CLOSED',
  closedAt: '2026-08-01T00:00:00Z',
  sellerCount: 2,
  payouts: [
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
};
const EXECUTED = [
  {
    payoutId: 'po-1',
    sellerId: 'acme-corp',
    payableNetMinor: 90000,
    commissionMinor: 10000,
    accrualCount: 1,
    status: 'PAID',
    payoutReference: 'SIM-REF-1',
    paidAt: '2026-08-01T01:00:00Z',
  },
];

beforeEach(() => {
  cookieJar.clear();
  vi.unstubAllGlobals();
  cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
  cookieJar.set(OPERATOR_COOKIE, 'OP-MUST-NOT-USE');
});

describe('setCommissionRate — PUT with body', () => {
  it('PUT ADMIN /settlements/commission-rates/{id} with { rateBps } + Content-Type', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(RATE));
    vi.stubGlobal('fetch', fetchMock);
    const r = await setCommissionRate('acme-corp', { rateBps: 1500 });
    expect(r.source).toBe('SELLER_OVERRIDE');
    const [url, init] = fetchMock.mock.calls[0];
    expect(String(url)).toBe(
      'http://ecommerce.local/api/admin/settlements/commission-rates/acme-corp',
    );
    expect((init as RequestInit).method).toBe('PUT');
    const headers = (init as RequestInit).headers as Record<string, string>;
    expect(headers['Content-Type']).toBe('application/json');
    expect(headers.Authorization).toBe('Bearer GAP-ACCESS');
    expect(headers.Authorization).not.toContain('OP-MUST-NOT-USE');
    expect(headers['X-Tenant-Id']).toBeUndefined();
    expect(headers['Idempotency-Key']).toBeUndefined();
    expect(JSON.parse((init as RequestInit).body as string)).toEqual({
      rateBps: 1500,
    });
  });

  it('422 COMMISSION_RATE_INVALID → ApiError(422) inline', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(ecomError('COMMISSION_RATE_INVALID', 422)));
    const err = await setCommissionRate('acme-corp', { rateBps: 99999 }).catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(422);
    expect(err.code).toBe('COMMISSION_RATE_INVALID');
  });
});

describe('openPeriod — POST with body', () => {
  it('POST ADMIN /settlements/periods with { from, to } + Content-Type', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(PERIOD, 201));
    vi.stubGlobal('fetch', fetchMock);
    const p = await openPeriod({
      from: '2026-07-01T00:00:00Z',
      to: '2026-08-01T00:00:00Z',
    });
    expect(p.periodId).toBe('2026-07');
    const [url, init] = fetchMock.mock.calls[0];
    expect(String(url)).toBe('http://ecommerce.local/api/admin/settlements/periods');
    expect((init as RequestInit).method).toBe('POST');
    const headers = (init as RequestInit).headers as Record<string, string>;
    expect(headers['Content-Type']).toBe('application/json');
    expect(JSON.parse((init as RequestInit).body as string)).toEqual({
      from: '2026-07-01T00:00:00Z',
      to: '2026-08-01T00:00:00Z',
    });
  });

  it('422 PERIOD_WINDOW_INVALID → ApiError(422) inline', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(ecomError('PERIOD_WINDOW_INVALID', 422)));
    const err = await openPeriod({ from: 'b', to: 'a' }).catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(422);
    expect(err.code).toBe('PERIOD_WINDOW_INVALID');
  });
});

describe('closePeriod — bodyless POST (NO Content-Type)', () => {
  it('POST ADMIN /settlements/periods/{id}/close with NO body + NO Content-Type', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(CLOSE_RESULT));
    vi.stubGlobal('fetch', fetchMock);
    const res = await closePeriod('2026-07');
    expect(res.status).toBe('CLOSED');
    expect(res.payouts).toHaveLength(1);
    const [url, init] = fetchMock.mock.calls[0];
    expect(String(url)).toBe(
      'http://ecommerce.local/api/admin/settlements/periods/2026-07/close',
    );
    expect((init as RequestInit).method).toBe('POST');
    expect((init as RequestInit).body).toBeUndefined();
    const headers = (init as RequestInit).headers as Record<string, string>;
    expect(headers['Content-Type']).toBeUndefined();
    expect(headers.Authorization).toBe('Bearer GAP-ACCESS');
  });

  it('409 PERIOD_ALREADY_CLOSED → ApiError(409) inline', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(ecomError('PERIOD_ALREADY_CLOSED', 409)));
    const err = await closePeriod('2026-07').catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(409);
    expect(err.code).toBe('PERIOD_ALREADY_CLOSED');
  });
});

describe('executePayouts — bodyless POST (NO Content-Type)', () => {
  it('POST ADMIN /settlements/periods/{id}/payouts/execute → Payout[] (post-exec status)', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(EXECUTED));
    vi.stubGlobal('fetch', fetchMock);
    const res = await executePayouts('2026-07');
    expect(res).toHaveLength(1);
    expect(res[0].status).toBe('PAID');
    expect(res[0].payoutReference).toBe('SIM-REF-1');
    const [url, init] = fetchMock.mock.calls[0];
    expect(String(url)).toBe(
      'http://ecommerce.local/api/admin/settlements/periods/2026-07/payouts/execute',
    );
    expect((init as RequestInit).method).toBe('POST');
    expect((init as RequestInit).body).toBeUndefined();
    const headers = (init as RequestInit).headers as Record<string, string>;
    expect(headers['Content-Type']).toBeUndefined();
  });

  it('409 PERIOD_NOT_CLOSED → ApiError(409) inline', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(ecomError('PERIOD_NOT_CLOSED', 409)));
    const err = await executePayouts('2026-07').catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(409);
    expect(err.code).toBe('PERIOD_NOT_CLOSED');
  });
});
