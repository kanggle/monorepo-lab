import { describe, it, expect, vi, beforeEach } from 'vitest';

/**
 * Same-origin ecommerce settlement MUTATION proxy handlers (TASK-PC-FE-221
 * Phase B):
 *   - PUT commission-rates/{id}: Zod SHAPE-validated body; bad body → 422 (no
 *     upstream); 422 COMMISSION_RATE_INVALID passthrough (body preserved, NOT 500).
 *   - POST periods: Zod SHAPE-validated body; 422 PERIOD_WINDOW_INVALID passthrough.
 *   - POST periods/{id}/close (bodyless): 200 passthrough; 409 PERIOD_ALREADY_CLOSED
 *     body preserved.
 *   - POST periods/{id}/payouts/execute (bodyless): 200; 409 PERIOD_NOT_CLOSED
 *     body preserved.
 *   - no IAM session → 401.
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

import { PUT as ratePUT } from '@/app/api/ecommerce/settlements/commission-rates/[id]/route';
import { POST as periodsPOST } from '@/app/api/ecommerce/settlements/periods/route';
import { POST as closePOST } from '@/app/api/ecommerce/settlements/periods/[id]/close/route';
import { POST as executePOST } from '@/app/api/ecommerce/settlements/periods/[id]/payouts/execute/route';
import { ACCESS_COOKIE } from '@/shared/lib/session';

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

function putReq(body: unknown) {
  return new Request(
    'http://console.local/api/ecommerce/settlements/commission-rates/acme-corp',
    {
      method: 'PUT',
      body: JSON.stringify(body),
      headers: { 'Content-Type': 'application/json' },
    },
  );
}
function postPeriodsReq(body: unknown) {
  return new Request('http://console.local/api/ecommerce/settlements/periods', {
    method: 'POST',
    body: JSON.stringify(body),
    headers: { 'Content-Type': 'application/json' },
  });
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

beforeEach(() => {
  cookieJar.clear();
  vi.unstubAllGlobals();
});

describe('PUT /api/ecommerce/settlements/commission-rates/{id}', () => {
  it('valid body → passthrough 200 + ADMIN base', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(RATE));
    vi.stubGlobal('fetch', fetchMock);
    const res = await ratePUT(putReq({ rateBps: 1500 }), {
      params: Promise.resolve({ id: 'acme-corp' }),
    });
    expect(res.status).toBe(200);
    expect(String(fetchMock.mock.calls[0][0])).toBe(
      'http://ecommerce.local/api/admin/settlements/commission-rates/acme-corp',
    );
  });

  it('invalid body (rateBps missing) → 422 (no upstream call)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    const res = await ratePUT(putReq({}), {
      params: Promise.resolve({ id: 'acme-corp' }),
    });
    expect(res.status).toBe(422);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('422 COMMISSION_RATE_INVALID → passthrough (body preserved, NOT 500)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(ecomError('COMMISSION_RATE_INVALID', 422)));
    const res = await ratePUT(putReq({ rateBps: 99999 }), {
      params: Promise.resolve({ id: 'acme-corp' }),
    });
    expect(res.status).toBe(422);
    const body = await res.json();
    expect(body.code).toBe('COMMISSION_RATE_INVALID');
  });

  it('no IAM session → 401 (no upstream call)', async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    const res = await ratePUT(putReq({ rateBps: 1500 }), {
      params: Promise.resolve({ id: 'acme-corp' }),
    });
    expect(res.status).toBe(401);
    expect(fetchMock).not.toHaveBeenCalled();
  });
});

describe('POST /api/ecommerce/settlements/periods (open)', () => {
  it('valid body → 201', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse(PERIOD, 201)));
    const res = await periodsPOST(
      postPeriodsReq({ from: '2026-07-01T00:00:00Z', to: '2026-08-01T00:00:00Z' }),
    );
    expect(res.status).toBe(201);
  });

  it('invalid body (missing to) → 422 (no upstream call)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    const res = await periodsPOST(postPeriodsReq({ from: '2026-07-01T00:00:00Z' }));
    expect(res.status).toBe(422);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('422 PERIOD_WINDOW_INVALID → passthrough (body preserved)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(ecomError('PERIOD_WINDOW_INVALID', 422)));
    const res = await periodsPOST(
      postPeriodsReq({ from: '2026-08-01T00:00:00Z', to: '2026-07-01T00:00:00Z' }),
    );
    expect(res.status).toBe(422);
    const body = await res.json();
    expect(body.code).toBe('PERIOD_WINDOW_INVALID');
  });
});

describe('POST /api/ecommerce/settlements/periods/{id}/close', () => {
  it('200 passthrough + targets ADMIN close path', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    const fetchMock = vi
      .fn()
      .mockResolvedValue(jsonResponse({ ...PERIOD, status: 'CLOSED', payouts: [] }));
    vi.stubGlobal('fetch', fetchMock);
    const res = await closePOST(
      new Request('http://console.local/api/ecommerce/settlements/periods/2026-07/close', {
        method: 'POST',
      }),
      { params: Promise.resolve({ id: '2026-07' }) },
    );
    expect(res.status).toBe(200);
    expect(String(fetchMock.mock.calls[0][0])).toBe(
      'http://ecommerce.local/api/admin/settlements/periods/2026-07/close',
    );
  });

  it('409 PERIOD_ALREADY_CLOSED → passthrough (body preserved, NOT 500)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(ecomError('PERIOD_ALREADY_CLOSED', 409)));
    const res = await closePOST(
      new Request('http://console.local/api/ecommerce/settlements/periods/2026-07/close', {
        method: 'POST',
      }),
      { params: Promise.resolve({ id: '2026-07' }) },
    );
    expect(res.status).toBe(409);
    const body = await res.json();
    expect(body.code).toBe('PERIOD_ALREADY_CLOSED');
  });
});

describe('POST /api/ecommerce/settlements/periods/{id}/payouts/execute', () => {
  it('200 passthrough (Payout[])', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse([]));
    vi.stubGlobal('fetch', fetchMock);
    const res = await executePOST(
      new Request('http://console.local/api/ecommerce/settlements/periods/2026-07/payouts/execute', {
        method: 'POST',
      }),
      { params: Promise.resolve({ id: '2026-07' }) },
    );
    expect(res.status).toBe(200);
    expect(String(fetchMock.mock.calls[0][0])).toBe(
      'http://ecommerce.local/api/admin/settlements/periods/2026-07/payouts/execute',
    );
  });

  it('409 PERIOD_NOT_CLOSED → passthrough (body preserved, NOT 500)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(ecomError('PERIOD_NOT_CLOSED', 409)));
    const res = await executePOST(
      new Request('http://console.local/api/ecommerce/settlements/periods/2026-07/payouts/execute', {
        method: 'POST',
      }),
      { params: Promise.resolve({ id: '2026-07' }) },
    );
    expect(res.status).toBe(409);
    const body = await res.json();
    expect(body.code).toBe('PERIOD_NOT_CLOSED');
  });
});
