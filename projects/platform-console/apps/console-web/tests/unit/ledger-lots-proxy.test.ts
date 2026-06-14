import { describe, it, expect, vi, beforeEach } from 'vitest';

/**
 * Same-origin finance ledger FX-position open-lots proxy route handler
 * (TASK-PC-FE-091 — § 2.4.7.1, row 10 / `ledger-api.md` § 12):
 *   - GET-only: the domain-facing IAM OIDC access token attached server-side
 *     (NOT the operator token); no mutation artifacts; STRICTLY READ-ONLY.
 *   - the colon-form account code AND currency are re-encoded upstream;
 *   - empty position → 200 passthrough (lots: [], NOT 404);
 *   - 400 VALIDATION_ERROR → 400 passthrough; 403 → 403; 503/timeout → 503;
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

import { GET as lotsGET } from '@/app/api/ledger/settlements/[ledgerAccountCode]/[currency]/lots/route';
import * as lotsRoute from '@/app/api/ledger/settlements/[ledgerAccountCode]/[currency]/lots/route';
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

const LOTS_ENV = {
  data: {
    lots: [
      {
        lotId: 'lot-1',
        currency: 'USD',
        acquiredAt: '2026-01-01T00:00:00Z',
        seq: 1,
        originalForeignMinor: '9007199254740993',
        remainingForeignMinor: '9007199254740993',
        originalBaseMinor: '1300000',
        carryingBaseMinor: '1300000',
        sourceJournalEntryId: 'je-acq-1',
      },
    ],
    totalRemainingForeignMinor: '9007199254740993',
    totalCarryingBaseMinor: '1300000',
    lotCount: 1,
  },
  meta: { timestamp: 'x' },
};

const EMPTY_ENV = {
  data: {
    lots: [],
    totalRemainingForeignMinor: '0',
    totalCarryingBaseMinor: '0',
    lotCount: 0,
  },
  meta: { timestamp: 'x' },
};

function lotsReq(code: string, currency: string) {
  return lotsGET(
    new Request(
      `http://console.local/api/ledger/settlements/${encodeURIComponent(code)}/${currency}/lots`,
    ),
    { params: Promise.resolve({ ledgerAccountCode: code, currency }) },
  );
}

beforeEach(() => {
  cookieJar.clear();
  vi.unstubAllGlobals();
});

describe('GET /api/ledger/settlements/{code}/{currency}/lots proxy (TASK-PC-FE-091, read-only)', () => {
  it('returns the upstream payload, uses the domain-facing token (NOT the operator token), GET, no mutation artifacts', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    cookieJar.set(OPERATOR_COOKIE, 'OP-MUST-NOT-USE');
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(LOTS_ENV));
    vi.stubGlobal('fetch', fetchMock);

    const res = await lotsReq('CUSTOMER_WALLET:acc-1', 'USD');
    expect(res.status).toBe(200);
    const body = await res.json();
    // F5: minor-units strings preserved untouched.
    expect(body.lots[0].remainingForeignMinor).toBe('9007199254740993');
    expect(typeof body.lots[0].remainingForeignMinor).toBe('string');
    expect(body.totalCarryingBaseMinor).toBe('1300000');
    expect(body.lotCount).toBe(1);

    const [url, init] = fetchMock.mock.calls[0];
    const h = (init as RequestInit).headers as Record<string, string>;
    expect(h.Authorization).toBe('Bearer GAP-ACCESS');
    expect(h.Authorization).not.toContain('OP-MUST-NOT-USE');
    expect(h['X-Tenant-Id']).toBeUndefined();
    expect(h['Idempotency-Key']).toBeUndefined();
    expect(h['X-Operator-Reason']).toBeUndefined();
    expect((init as RequestInit).method).toBe('GET');
    expect((init as RequestInit).body).toBeUndefined();
    // colon-form code re-encoded upstream; currency on the path.
    expect(String(url)).toBe(
      'http://finance.local/api/finance/ledger/settlements/CUSTOMER_WALLET%3Aacc-1/USD/lots',
    );
  });

  it('an empty position is a 200 passthrough (lots: [], NOT 404)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse(EMPTY_ENV)));
    const res = await lotsReq('ASSET:1000', 'KRW');
    expect(res.status).toBe(200);
    const body = await res.json();
    expect(body.lots).toEqual([]);
    expect(body.lotCount).toBe(0);
  });

  it('400 VALIDATION_ERROR (unsupported currency) → 400 passthrough (inline actionable)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(ledgerError('VALIDATION_ERROR', 400)),
    );
    const res = await lotsReq('ASSET:1000', 'ZZZ');
    expect(res.status).toBe(400);
    const b = await res.json();
    expect(b.code).toBe('VALIDATION_ERROR');
  });

  it('403 TENANT_FORBIDDEN → 403', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(ledgerError('TENANT_FORBIDDEN', 403)),
    );
    const res = await lotsReq('ASSET:1000', 'USD');
    expect(res.status).toBe(403);
  });

  it('503 → 503 (ledger section degrades)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(ledgerError('SERVICE_UNAVAILABLE', 503)),
    );
    const res = await lotsReq('ASSET:1000', 'USD');
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
    const res = await lotsReq('ASSET:1000', 'USD');
    expect(res.status).toBe(429);
    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(res.headers.get('Retry-After')).toBeNull();
  });

  it('no IAM session → 401 (no upstream call)', async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    const res = await lotsReq('ASSET:1000', 'USD');
    expect(res.status).toBe(401);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('GET-only: the route exports no POST/PUT/PATCH/DELETE', () => {
    expect(typeof lotsRoute.GET).toBe('function');
    expect((lotsRoute as Record<string, unknown>).POST).toBeUndefined();
    expect((lotsRoute as Record<string, unknown>).PUT).toBeUndefined();
    expect((lotsRoute as Record<string, unknown>).PATCH).toBeUndefined();
    expect((lotsRoute as Record<string, unknown>).DELETE).toBeUndefined();
  });
});
