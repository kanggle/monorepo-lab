import { describe, it, expect, vi, beforeEach } from 'vitest';

/**
 * Same-origin finance ledger FX 환율 피드 proxy route handler
 * (TASK-PC-FE-092 — § 2.4.7.1, FIN-BE-033):
 *   - GET-only: the domain-facing IAM OIDC access token attached server-side
 *     (NOT the operator token); no mutation artifacts; STRICTLY READ-ONLY.
 *   - no path parameters — global list read;
 *   - empty cache → 200 passthrough (rates: [], NOT 404);
 *   - 403 → 403 passthrough; 503/timeout → 503;
 *   - **no 429 / Retry-After branch** (a stray 429 lands as a passthrough);
 *   - GET-only: the route exports no POST/PUT/PATCH/DELETE.
 *   - F5: `rate` stays a string (no Number coercion) through the proxy.
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
    CONSOLE_TOKEN_EXCHANGE_URL:
      'http://iam.local/api/admin/auth/token-exchange',
    TOKEN_EXCHANGE_TIMEOUT_MS: 50,
    IAM_ADMIN_API_BASE: 'http://iam.local',
    ACCOUNTS_TIMEOUT_MS: 50,
    AUDIT_TIMEOUT_MS: 50,
    OPERATORS_TIMEOUT_MS: 50,
    FINANCE_BASE_URL: 'http://finance.local',
    FINANCE_TIMEOUT_MS: 50,
    LEDGER_BASE_URL: 'http://finance.local',
    LEDGER_TIMEOUT_MS: 50,
    LOG_LEVEL: 'info' as const,
    NEXT_PUBLIC_APP_URL: 'http://console.local',
  },
}));
vi.mock('@/shared/config/env', () => ({
  clientEnv: { NEXT_PUBLIC_APP_URL: ENV.NEXT_PUBLIC_APP_URL },
  getServerEnv: () => ENV,
}));

import { GET as fxRatesGET } from '@/app/api/ledger/fx-rates/route';
import * as fxRatesRoute from '@/app/api/ledger/fx-rates/route';
import { ACCESS_COOKIE, OPERATOR_COOKIE } from '@/shared/lib/session';

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}
function ledgerError(code: string, status: number) {
  return new Response(
    JSON.stringify({ code, message: 'e', timestamp: 't' }),
    { status, headers: { 'Content-Type': 'application/json' } },
  );
}

const FX_RATES_ENV = {
  data: {
    feedEnabled: true,
    rates: [
      {
        baseCurrency: 'KRW',
        foreignCurrency: 'USD',
        rate: '1300.12345678',
        asOf: '2026-06-15T00:00:00Z',
        source: 'ECB',
        fetchedAt: '2026-06-15T00:01:00Z',
        ageSeconds: 60,
        stale: false,
      },
    ],
  },
  meta: { timestamp: 'x' },
};

const EMPTY_ENV = {
  data: {
    feedEnabled: true,
    rates: [],
  },
  meta: { timestamp: 'x' },
};

function fxRatesReq() {
  // GET takes no args — global list route, no path params.
  return fxRatesGET();
}

beforeEach(() => {
  cookieJar.clear();
  vi.unstubAllGlobals();
});

describe('GET /api/ledger/fx-rates proxy (TASK-PC-FE-092, read-only)', () => {
  it('returns the upstream payload, uses the domain-facing token (NOT the operator token), GET, no mutation artifacts', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    cookieJar.set(OPERATOR_COOKIE, 'OP-MUST-NOT-USE');
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(FX_RATES_ENV));
    vi.stubGlobal('fetch', fetchMock);

    const res = await fxRatesReq();
    expect(res.status).toBe(200);
    const body = await res.json();
    // F5: rate string preserved untouched.
    expect(body.rates[0].rate).toBe('1300.12345678');
    expect(typeof body.rates[0].rate).toBe('string');
    expect(body.feedEnabled).toBe(true);

    const [url, init] = fetchMock.mock.calls[0];
    const h = (init as RequestInit).headers as Record<string, string>;
    expect(h.Authorization).toBe('Bearer GAP-ACCESS');
    expect(h.Authorization).not.toContain('OP-MUST-NOT-USE');
    expect(h['X-Tenant-Id']).toBeUndefined();
    expect(h['Idempotency-Key']).toBeUndefined();
    expect(h['X-Operator-Reason']).toBeUndefined();
    expect((init as RequestInit).method).toBe('GET');
    expect((init as RequestInit).body).toBeUndefined();
    // No path params — the upstream path is fixed.
    expect(String(url)).toBe(
      'http://finance.local/api/finance/ledger/fx-rates',
    );
  });

  it('an empty cache is a 200 passthrough (rates: [], NOT 404)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse(EMPTY_ENV)));
    const res = await fxRatesReq();
    expect(res.status).toBe(200);
    const body = await res.json();
    expect(body.rates).toEqual([]);
    expect(body.feedEnabled).toBe(true);
  });

  it('403 TENANT_FORBIDDEN → 403', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(ledgerError('TENANT_FORBIDDEN', 403)),
    );
    const res = await fxRatesReq();
    expect(res.status).toBe(403);
  });

  it('503 → 503 (ledger section degrades)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(ledgerError('SERVICE_UNAVAILABLE', 503)),
    );
    const res = await fxRatesReq();
    expect(res.status).toBe(503);
  });

  it('a stray 429 falls through as a passthrough — NO Retry-After branch, NO retry storm', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(
        JSON.stringify({ code: 'RATE_LIMIT_EXCEEDED', message: 'x' }),
        {
          status: 429,
          headers: { 'Content-Type': 'application/json', 'Retry-After': '1' },
        },
      ),
    );
    vi.stubGlobal('fetch', fetchMock);
    const res = await fxRatesReq();
    expect(res.status).toBe(429);
    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(res.headers.get('Retry-After')).toBeNull();
  });

  it('no IAM session → 401 (no upstream call)', async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    const res = await fxRatesReq();
    expect(res.status).toBe(401);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('GET-only: the route exports no POST/PUT/PATCH/DELETE', () => {
    expect(typeof fxRatesRoute.GET).toBe('function');
    expect((fxRatesRoute as Record<string, unknown>).POST).toBeUndefined();
    expect((fxRatesRoute as Record<string, unknown>).PUT).toBeUndefined();
    expect((fxRatesRoute as Record<string, unknown>).PATCH).toBeUndefined();
    expect((fxRatesRoute as Record<string, unknown>).DELETE).toBeUndefined();
  });
});
