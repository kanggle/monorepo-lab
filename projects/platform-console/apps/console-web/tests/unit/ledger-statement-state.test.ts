import { describe, it, expect, vi, beforeEach } from 'vitest';

/**
 * `features/ledger-ops/api/ledger-state.ts` — statement-id branch
 * (TASK-PC-FE-075 — § 2.4.7.1).
 *
 * Assertions:
 *   - ?statementId= seeds the statement detail;
 *   - 404 RECONCILIATION_STATEMENT_NOT_FOUND → inline `statementNotFound`;
 *   - no statementId → statement null;
 *   - existing entryId + accountCode + statementId together → all seeded
 *     in a single Promise.all.
 */

const cookieJar = new Map<string, string>();
vi.mock('next/headers', () => ({
  cookies: async () => ({
    get: (n: string) =>
      cookieJar.has(n) ? { value: cookieJar.get(n)! } : undefined,
  }),
}));
vi.mock('next/navigation', () => ({
  redirect: (to: string) => {
    throw new Error(`REDIRECT:${to}`);
  },
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

import { getLedgerSectionState } from '@/features/ledger-ops/api/ledger-state';
import { ACCESS_COOKIE } from '@/shared/lib/session';

const M = (amount: string, currency = 'KRW') => ({ amount, currency });

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

const TB_ENV = {
  data: {
    accounts: [],
    grandDebitTotal: M('0'),
    grandCreditTotal: M('0'),
    grandBaseDebitTotal: M('0'),
    grandBaseCreditTotal: M('0'),
    inBalance: true,
  },
  meta: { timestamp: 'x' },
};
const PERIODS_ENV = {
  data: [],
  meta: { page: 0, size: 20, totalElements: 0, timestamp: 'x' },
};
const DISC_ENV = {
  data: [],
  meta: { page: 0, size: 20, totalElements: 0, timestamp: 'x' },
};

const STATEMENT_ENV = {
  data: {
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
        money: M('1234567890123'), // large KRW — F5 guard
      },
    ],
    discrepancies: [],
  },
  meta: { timestamp: 'x' },
};

const BALANCE_ENV = {
  data: {
    ledgerAccountCode: 'CUSTOMER_WALLET:acc-1',
    type: 'LIABILITY',
    normalSide: 'CREDIT',
    debitTotal: M('0'),
    creditTotal: M('0'),
    balance: M('0'),
    balanceSide: 'CREDIT',
  },
  meta: { timestamp: 'x' },
};
const ENTRIES_ENV = {
  data: [],
  meta: { page: 0, size: 20, totalElements: 0, timestamp: 'x' },
};
const ENTRY_ENV = {
  data: {
    entryId: 'je-1',
    source: { sourceType: 'TRANSACTION' },
    lines: [],
    balanced: true,
  },
  meta: { timestamp: 'x' },
};

function routedWithStatement() {
  return vi.fn((url: string, _init?: RequestInit) => {
    const u = String(url);
    // statement: /reconciliation/statements/{id}
    if (u.includes('/reconciliation/statements/'))
      return Promise.resolve(jsonResponse(STATEMENT_ENV));
    // account balance/entries
    if (u.includes('/accounts/') && u.includes('/balance'))
      return Promise.resolve(jsonResponse(BALANCE_ENV));
    if (u.includes('/accounts/') && u.includes('/entries'))
      return Promise.resolve(jsonResponse(ENTRIES_ENV));
    // journal entry
    if (u.includes('/entries/') && !u.includes('/accounts/') && !u.includes('/reconciliation/'))
      return Promise.resolve(jsonResponse(ENTRY_ENV));
    // discrepancy queue
    if (u.includes('/discrepancies'))
      return Promise.resolve(jsonResponse(DISC_ENV));
    if (u.includes('/periods'))
      return Promise.resolve(jsonResponse(PERIODS_ENV));
    return Promise.resolve(jsonResponse(TB_ENV));
  });
}

beforeEach(() => {
  cookieJar.clear();
  vi.unstubAllGlobals();
});

describe('getLedgerSectionState — TASK-PC-FE-075 statementId branch', () => {
  it('eligible + statementId (no entryId, no accountCode) → seeds statement + 3 browsable index reads', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    const fetchMock = routedWithStatement();
    vi.stubGlobal('fetch', fetchMock);

    const state = await getLedgerSectionState(true, null, null, 'stmt-1');

    expect(state.notEligible).toBe(false);
    expect(state.statement).not.toBeNull();
    expect(state.statement?.statementId).toBe('stmt-1');
    expect(state.statementNotFound).toBe(false);
    // F5: match money is a string.
    expect(state.statement?.matches[0].money.amount).toBe('1234567890123');
    expect(typeof state.statement?.matches[0].money.amount).toBe('string');
    // 4 fetches total: 3 index + statement.
    expect(fetchMock.mock.calls.length).toBe(4);
  });

  it('eligible (no statementId) → statement is null', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    vi.stubGlobal('fetch', routedWithStatement());

    const state = await getLedgerSectionState(true, null, null, null);

    expect(state.statement).toBeNull();
    expect(state.statementNotFound).toBe(false);
  });

  it('404 RECONCILIATION_STATEMENT_NOT_FOUND → statementNotFound: true (NOT notFound, NOT degraded)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    vi.stubGlobal(
      'fetch',
      vi.fn((url: string) => {
        const u = String(url);
        if (u.includes('/reconciliation/statements/'))
          return Promise.resolve(
            ledgerError('RECONCILIATION_STATEMENT_NOT_FOUND', 404),
          );
        if (u.includes('/discrepancies'))
          return Promise.resolve(jsonResponse(DISC_ENV));
        if (u.includes('/periods'))
          return Promise.resolve(jsonResponse(PERIODS_ENV));
        return Promise.resolve(jsonResponse(TB_ENV));
      }),
    );

    const state = await getLedgerSectionState(true, null, null, 'nope');

    expect(state.statementNotFound).toBe(true);
    expect(state.notFound).toBe(false);
    expect(state.accountNotFound).toBe(false);
    expect(state.degraded).toBe(false);
    expect(state.forbidden).toBe(false);
  });

  it('eligible + entryId + accountCode + statementId → all 4 id-driven reads seeded together (6 total fetches)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    const fetchMock = routedWithStatement();
    vi.stubGlobal('fetch', fetchMock);

    const state = await getLedgerSectionState(
      true,
      'je-1',
      'CUSTOMER_WALLET:acc-1',
      'stmt-1',
    );

    expect(state.entry).not.toBeNull();
    expect(state.accountBalance).not.toBeNull();
    expect(state.accountEntries).not.toBeNull();
    expect(state.statement).not.toBeNull();
    expect(state.notFound).toBe(false);
    expect(state.accountNotFound).toBe(false);
    expect(state.statementNotFound).toBe(false);
    // 6 fetches: 3 index + entry + balance + entries + statement = 7.
    // (The base 3 are always seeded; statement is a 4th id-driven read.)
    expect(fetchMock.mock.calls.length).toBe(7);
  });

  it('the statementId is URL-encoded in the upstream path', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    const fetchMock = routedWithStatement();
    vi.stubGlobal('fetch', fetchMock);

    await getLedgerSectionState(true, null, null, 'stmt/special:id');

    const stmtCalls = fetchMock.mock.calls.filter(([u]) =>
      String(u).includes('/reconciliation/statements/'),
    );
    expect(stmtCalls.length).toBe(1);
    expect(String(stmtCalls[0][0])).toContain('stmt%2Fspecial%3Aid');
    expect(String(stmtCalls[0][0])).not.toContain('stmt/special:id');
  });
});
