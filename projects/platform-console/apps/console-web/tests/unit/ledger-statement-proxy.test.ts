import { describe, it, expect, vi, beforeEach } from 'vitest';

/**
 * Same-origin finance ledger reconciliation statement-detail proxy route
 * (TASK-PC-FE-075 — § 2.4.7.1 `GET /api/ledger/reconciliation/statements/{id}`).
 *
 * Assertions:
 *   - domain-facing IAM OIDC access token (NOT the operator token);
 *   - GET only — NO body, NO Idempotency-Key, NO X-Operator-Reason,
 *     NO X-Tenant-Id;
 *   - 200 → passes the upstream payload to the client;
 *   - 404 RECONCILIATION_STATEMENT_NOT_FOUND → 404 passthrough;
 *   - 503 → 503 (ledger section degrades only).
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

import { GET as statementGET } from '@/app/api/ledger/reconciliation/statements/[statementId]/route';
import { ACCESS_COOKIE, OPERATOR_COOKIE } from '@/shared/lib/session';

const M = (amount: string, currency = 'KRW') => ({ amount, currency });

const STATEMENT_DATA = {
  statementId: 'stmt-1',
  ledgerAccountCode: 'CUSTOMER_WALLET:acc-1',
  source: 'BANK_FEED',
  statementDate: '2026-06-13',
  matchedCount: 1,
  discrepancyCount: 0,
  matches: [
    {
      statementLineExternalRef: 'ext-ref-001',
      journalEntryId: 'je-123',
      money: M('9007199254740993'), // > 2^53 — F5 guard
    },
  ],
  discrepancies: [],
};

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

/** Build a params object as Next.js App Router passes to route handlers. */
function mkParams(statementId: string) {
  return { params: Promise.resolve({ statementId }) };
}

beforeEach(() => {
  cookieJar.clear();
  vi.unstubAllGlobals();
});

describe('GET /api/ledger/reconciliation/statements/[statementId] proxy', () => {
  it('attaches the domain-facing IAM OIDC access token (NOT the operator token)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    cookieJar.set(OPERATOR_COOKIE, 'OP-MUST-NOT-USE');
    const fetchMock = vi
      .fn()
      .mockResolvedValue(jsonResponse({ data: STATEMENT_DATA, meta: { timestamp: 'x' } }));
    vi.stubGlobal('fetch', fetchMock);

    const req = new Request('http://console.local/api/ledger/reconciliation/statements/stmt-1');
    const res = await statementGET(req, mkParams('stmt-1'));
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
      'http://finance.local/api/finance/ledger/reconciliation/statements/stmt-1',
    );
  });

  it('200 → passes the upstream payload to the client (F5 bit-exact round-trip)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        jsonResponse({ data: STATEMENT_DATA, meta: { timestamp: 'x' } }),
      ),
    );

    const req = new Request('http://console.local/api/ledger/reconciliation/statements/stmt-1');
    const res = await statementGET(req, mkParams('stmt-1'));
    expect(res.status).toBe(200);
    const body = await res.json() as typeof STATEMENT_DATA;
    expect(body.statementId).toBe('stmt-1');
    // F5: match money is a string
    expect(body.matches[0].money.amount).toBe('9007199254740993');
    expect(typeof body.matches[0].money.amount).toBe('string');
  });

  it('404 RECONCILIATION_STATEMENT_NOT_FOUND → 404 passthrough (inline actionable)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        ledgerError('RECONCILIATION_STATEMENT_NOT_FOUND', 404),
      ),
    );

    const req = new Request('http://console.local/api/ledger/reconciliation/statements/nope');
    const res = await statementGET(req, mkParams('nope'));
    expect(res.status).toBe(404);
  });

  it('503 from the ledger → 503 (ledger section degrades only)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(ledgerError('SERVICE_UNAVAILABLE', 503)),
    );

    const req = new Request('http://console.local/api/ledger/reconciliation/statements/stmt-1');
    const res = await statementGET(req, mkParams('stmt-1'));
    expect(res.status).toBe(503);
  });

  it('no IAM session → 401 (no upstream call)', async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);

    const req = new Request('http://console.local/api/ledger/reconciliation/statements/stmt-1');
    const res = await statementGET(req, mkParams('stmt-1'));
    expect(res.status).toBe(401);
    expect(fetchMock).not.toHaveBeenCalled();
  });
});
