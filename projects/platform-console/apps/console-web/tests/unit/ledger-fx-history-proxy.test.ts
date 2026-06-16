import { describe, it, expect, vi, beforeEach } from 'vitest';

/**
 * Same-origin finance ledger FX 환율 history proxy route handler
 * (TASK-PC-FE-104 — § 2.4.7.1, FIN-BE-040):
 *   - GET-only: the domain-facing IAM OIDC access token attached server-side
 *     (NOT the operator token); no mutation artifacts; STRICTLY READ-ONLY.
 *   - per-pair: the `[foreignCurrency]` path param is forwarded to the upstream;
 *   - the `?limit=` query is parsed + forwarded; a non-numeric / absent limit
 *     degrades to no upstream `limit` (producer default 50);
 *   - empty history → 200 passthrough (quotes: [], NOT 404);
 *   - 403 → 403 passthrough; 503/timeout → 503;
 *   - **no 429 / Retry-After branch** (a stray 429 lands as a passthrough);
 *   - GET-only: the route exports no POST/PUT/PATCH/DELETE.
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

import { GET as historyGET } from '@/app/api/ledger/fx-rates/[foreignCurrency]/history/route';
import * as historyRoute from '@/app/api/ledger/fx-rates/[foreignCurrency]/history/route';
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

const HISTORY_ENV = {
  data: {
    base: 'KRW',
    foreign: 'USD',
    quotes: [
      {
        rate: '1300.12345678',
        asOf: '2026-06-15T07:00:00Z',
        fetchedAt: '2026-06-15T07:00:05Z',
        source: 'stub',
      },
    ],
  },
  meta: { timestamp: 'x' },
};

const EMPTY_ENV = {
  data: { base: 'KRW', foreign: 'XXX', quotes: [] },
  meta: { timestamp: 'x' },
};

function historyReq(currency: string, limit?: string) {
  const base = `http://console.local/api/ledger/fx-rates/${currency}/history`;
  const url = limit !== undefined ? `${base}?limit=${limit}` : base;
  return historyGET(new Request(url), {
    params: Promise.resolve({ foreignCurrency: currency }),
  });
}

beforeEach(() => {
  cookieJar.clear();
  vi.unstubAllGlobals();
});

describe('GET /api/ledger/fx-rates/[foreignCurrency]/history proxy (TASK-PC-FE-104, read-only)', () => {
  it('returns the upstream payload, uses the domain-facing token (NOT the operator token), GET, no mutation artifacts', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    cookieJar.set(OPERATOR_COOKIE, 'OP-MUST-NOT-USE');
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(HISTORY_ENV));
    vi.stubGlobal('fetch', fetchMock);

    const res = await historyReq('USD', '25');
    expect(res.status).toBe(200);
    const body = await res.json();
    expect(body.base).toBe('KRW');
    expect(body.foreign).toBe('USD');
    // F5: rate string preserved untouched.
    expect(body.quotes[0].rate).toBe('1300.12345678');
    expect(typeof body.quotes[0].rate).toBe('string');

    const [url, init] = fetchMock.mock.calls[0];
    const h = (init as RequestInit).headers as Record<string, string>;
    expect(h.Authorization).toBe('Bearer GAP-ACCESS');
    expect(h.Authorization).not.toContain('OP-MUST-NOT-USE');
    expect(h['X-Tenant-Id']).toBeUndefined();
    expect(h['Idempotency-Key']).toBeUndefined();
    expect(h['X-Operator-Reason']).toBeUndefined();
    expect((init as RequestInit).method).toBe('GET');
    expect((init as RequestInit).body).toBeUndefined();
    // per-pair path + the limit forwarded.
    expect(String(url)).toBe(
      'http://finance.local/api/finance/ledger/fx-rates/USD/history?limit=25',
    );
  });

  it('forwards no upstream limit when the ?limit query is absent (producer default 50)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(HISTORY_ENV));
    vi.stubGlobal('fetch', fetchMock);

    await historyReq('USD');

    const url = String(fetchMock.mock.calls[0][0]);
    expect(url).toBe(
      'http://finance.local/api/finance/ledger/fx-rates/USD/history',
    );
    expect(url).not.toContain('limit');
  });

  it('a non-numeric ?limit degrades to no upstream limit (no crash)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(HISTORY_ENV));
    vi.stubGlobal('fetch', fetchMock);

    const res = await historyReq('USD', 'abc');
    expect(res.status).toBe(200);
    const url = String(fetchMock.mock.calls[0][0]);
    expect(url).not.toContain('limit');
  });

  it('an empty history is a 200 passthrough (quotes: [], NOT 404)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse(EMPTY_ENV)));
    const res = await historyReq('XXX', '10');
    expect(res.status).toBe(200);
    const body = await res.json();
    expect(body.quotes).toEqual([]);
  });

  it('403 TENANT_FORBIDDEN → 403', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(ledgerError('TENANT_FORBIDDEN', 403)),
    );
    const res = await historyReq('USD', '10');
    expect(res.status).toBe(403);
  });

  it('503 → 503 (ledger section degrades)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(ledgerError('SERVICE_UNAVAILABLE', 503)),
    );
    const res = await historyReq('USD', '10');
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
    const res = await historyReq('USD', '10');
    expect(res.status).toBe(429);
    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(res.headers.get('Retry-After')).toBeNull();
  });

  it('no IAM session → 401 (no upstream call)', async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    const res = await historyReq('USD', '10');
    expect(res.status).toBe(401);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('GET-only: the route exports no POST/PUT/PATCH/DELETE', () => {
    expect(typeof historyRoute.GET).toBe('function');
    expect((historyRoute as Record<string, unknown>).POST).toBeUndefined();
    expect((historyRoute as Record<string, unknown>).PUT).toBeUndefined();
    expect((historyRoute as Record<string, unknown>).PATCH).toBeUndefined();
    expect((historyRoute as Record<string, unknown>).DELETE).toBeUndefined();
  });
});
