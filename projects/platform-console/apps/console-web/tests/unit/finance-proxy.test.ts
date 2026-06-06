import { describe, it, expect, vi, beforeEach } from 'vitest';

/**
 * Same-origin finance-ops proxy route handlers (TASK-PC-FE-009 —
 * § 2.4.7):
 *   - read GET (account by id, balances, transactions): GAP OIDC
 *     access token attached server-side (NOT the operator token);
 *     no mutation artifacts; STRICTLY READ-ONLY (GET-only routes).
 *   - 401 → 401 (whole-session re-login signal; no partial authed
 *     state).
 *   - 403 → 403 (token not finance-scoped inline, no crash).
 *   - 404 ACCOUNT_NOT_FOUND → 404 inline actionable.
 *   - 400 / 422 → passthrough.
 *   - 503 / timeout → 503 (finance section degrades only; shell
 *     intact).
 *   - **no 429 / Retry-After branch** (finance has no documented
 *     429; a stray 429 lands as a passthrough — NO retry storm,
 *     NO Retry-After branch).
 *
 * There is NO mutation proxy route at all (no finance write, no v2
 * admin-service surface).
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
    WMS_ADMIN_BASE_URL: 'http://wms.local/api/v1/admin',
    WMS_TIMEOUT_MS: 50,
    SCM_GATEWAY_BASE_URL: 'http://scm.local',
    SCM_TIMEOUT_MS: 50,
    FINANCE_BASE_URL: 'http://finance.local',
    FINANCE_TIMEOUT_MS: 50,
    LOG_LEVEL: 'info' as const,
    NEXT_PUBLIC_APP_URL: 'http://console.local',
  },
}));
vi.mock('@/shared/config/env', () => ({
  clientEnv: { NEXT_PUBLIC_APP_URL: ENV.NEXT_PUBLIC_APP_URL },
  getServerEnv: () => ENV,
}));

import { GET as accountGET } from '@/app/api/finance/accounts/[accountId]/route';
import { GET as balancesGET } from '@/app/api/finance/accounts/[accountId]/balances/route';
import { GET as txnsGET } from '@/app/api/finance/accounts/[accountId]/transactions/route';
import { ACCESS_COOKIE, OPERATOR_COOKIE } from '@/shared/lib/session';

function jsonResponse(
  body: unknown,
  status = 200,
  headers: Record<string, string> = {},
) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json', ...headers },
  });
}
function financeError(code: string, status: number) {
  return new Response(
    JSON.stringify({ code, message: 'e', timestamp: 't' }),
    { status, headers: { 'Content-Type': 'application/json' } },
  );
}

const ACCOUNT_ENV = {
  data: {
    accountId: 'acct-1',
    status: 'ACTIVE',
    currency: 'KRW',
    kycLevel: 'BASIC',
  },
  meta: { timestamp: 'x' },
};
const BALANCES_ENV = {
  data: [
    {
      currency: 'KRW',
      ledger: '1234567890123',
      available: '1000000',
      held: '0',
    },
  ],
  meta: { timestamp: 'x' },
};
const TXNS_ENV = {
  data: [
    {
      transactionId: 't-1',
      type: 'HOLD',
      status: 'ACTIVE',
      money: { amount: '150000', currency: 'KRW' },
    },
  ],
  meta: { page: 0, size: 20, totalElements: 1, timestamp: 'x' },
};

beforeEach(() => {
  cookieJar.clear();
  vi.unstubAllGlobals();
});

describe('GET /api/finance/accounts/{accountId} proxy (read-only)', () => {
  it('attaches the GAP OIDC access token (NOT the operator token)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    cookieJar.set(OPERATOR_COOKIE, 'OP-MUST-NOT-USE');
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(ACCOUNT_ENV));
    vi.stubGlobal('fetch', fetchMock);

    const res = await accountGET(
      new Request('http://console.local/api/finance/accounts/acct-1'),
      { params: Promise.resolve({ accountId: 'acct-1' }) },
    );
    expect(res.status).toBe(200);

    const [url, init] = fetchMock.mock.calls[0];
    const h = (init as RequestInit).headers as Record<string, string>;
    expect(h.Authorization).toBe('Bearer GAP-ACCESS');
    expect(h.Authorization).not.toContain('OP-MUST-NOT-USE');
    expect(h['X-Tenant-Id']).toBeUndefined();
    expect(h['Idempotency-Key']).toBeUndefined();
    expect(h['X-Operator-Reason']).toBeUndefined();
    expect((init as RequestInit).method).toBe('GET');
    expect(String(url)).toContain(
      'http://finance.local/api/finance/accounts/acct-1',
    );
  });

  it('no GAP session → 401 (no upstream call)', async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    const res = await accountGET(
      new Request('http://console.local/api/finance/accounts/acct-1'),
      { params: Promise.resolve({ accountId: 'acct-1' }) },
    );
    expect(res.status).toBe(401);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('403 TENANT_FORBIDDEN → 403 (inline not scoped)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(financeError('TENANT_FORBIDDEN', 403)),
    );
    const res = await accountGET(
      new Request('http://console.local/api/finance/accounts/acct-1'),
      { params: Promise.resolve({ accountId: 'acct-1' }) },
    );
    expect(res.status).toBe(403);
  });

  it('404 ACCOUNT_NOT_FOUND → 404 inline actionable', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(financeError('ACCOUNT_NOT_FOUND', 404)),
    );
    const res = await accountGET(
      new Request('http://console.local/api/finance/accounts/nope'),
      { params: Promise.resolve({ accountId: 'nope' }) },
    );
    expect(res.status).toBe(404);
    const b = await res.json();
    expect(b.code).toBe('ACCOUNT_NOT_FOUND');
  });

  it('503 from finance → 503 (finance section degrades only)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(financeError('SERVICE_UNAVAILABLE', 503)),
    );
    const res = await accountGET(
      new Request('http://console.local/api/finance/accounts/acct-1'),
      { params: Promise.resolve({ accountId: 'acct-1' }) },
    );
    expect(res.status).toBe(503);
  });

  it('a stray 429 falls through as a passthrough — NO Retry-After branch, NO retry storm', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(
        JSON.stringify({ code: 'RATE_LIMIT_EXCEEDED', message: 'x' }),
        {
          status: 429,
          headers: {
            'Content-Type': 'application/json',
            'Retry-After': '1',
          },
        },
      ),
    );
    vi.stubGlobal('fetch', fetchMock);
    const res = await accountGET(
      new Request('http://console.local/api/finance/accounts/acct-1'),
      { params: Promise.resolve({ accountId: 'acct-1' }) },
    );
    // The proxy passes the 429 through (no fabricated backoff /
    // Retry-After branch — finance has no documented 429; § 2.4.7
    // honest difference from scm).
    expect(res.status).toBe(429);
    // EXACTLY ONE upstream fetch — no retry storm.
    expect(fetchMock).toHaveBeenCalledTimes(1);
    // The proxy must NOT set a Retry-After header (would be a
    // fabricated backoff signal).
    expect(res.headers.get('Retry-After')).toBeNull();
  });
});

describe('GET /api/finance/accounts/{accountId}/balances proxy (read-only)', () => {
  it('forwards balances WITHOUT touching `amount` strings (F5 — string preserved)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(jsonResponse(BALANCES_ENV)),
    );
    const res = await balancesGET(
      new Request(
        'http://console.local/api/finance/accounts/acct-1/balances',
      ),
      { params: Promise.resolve({ accountId: 'acct-1' }) },
    );
    expect(res.status).toBe(200);
    const body = await res.json();
    // The minor-units string travels through untouched.
    expect(body.data[0].ledger).toBe('1234567890123');
    expect(typeof body.data[0].ledger).toBe('string');
  });
});

describe('GET /api/finance/accounts/{accountId}/transactions proxy (read-only)', () => {
  it('forwards filters + paginates', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(TXNS_ENV));
    vi.stubGlobal('fetch', fetchMock);
    const res = await txnsGET(
      new Request(
        'http://console.local/api/finance/accounts/acct-1/transactions?type=HOLD&status=ACTIVE&size=99',
      ),
      { params: Promise.resolve({ accountId: 'acct-1' }) },
    );
    expect(res.status).toBe(200);
    const [url, init] = fetchMock.mock.calls[0];
    expect((init as RequestInit).method).toBe('GET');
    const upstream = new URL(String(url));
    expect(upstream.pathname).toBe(
      '/api/finance/accounts/acct-1/transactions',
    );
    expect(upstream.searchParams.get('type')).toBe('HOLD');
    expect(upstream.searchParams.get('status')).toBe('ACTIVE');
    expect(upstream.searchParams.get('size')).toBe('99');
    expect(upstream.searchParams.get('page')).toBe('0');
  });
});
