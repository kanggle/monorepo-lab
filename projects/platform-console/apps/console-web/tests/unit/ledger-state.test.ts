import { describe, it, expect, vi, beforeEach } from 'vitest';

/**
 * `features/ledger-ops/api/ledger-state.ts` — the server-side section state
 * (TASK-PC-FE-072 / § 2.4.7.1):
 *   - not finance-eligible → `notEligible` block, NO ledger call
 *     fabricated (no cross-tenant call; the console never sends a tenant);
 *   - eligible (no entryId) → seeds the browsable index reads (trial
 *     balance + periods + OPEN discrepancy queue), NO entry call;
 *   - eligible + entryId → additionally seeds the journal entry (id-driven);
 *   - 403 → `forbidden`; 404 JOURNAL_ENTRY_NOT_FOUND → `notFound`;
 *   - 503 / timeout → `degraded`; stray 429 → `degraded`;
 *   - 401 → whole-session re-login (redirect).
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

const M = (amount: string, currency = 'KRW') => ({ amount, currency });

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
const ENTRY_ENV = {
  data: {
    entryId: 'je-1',
    source: { sourceType: 'TRANSACTION' },
    lines: [],
    balanced: true,
  },
  meta: { timestamp: 'x' },
};

function routed() {
  return vi.fn((url: string, _init?: RequestInit) => {
    const u = String(url);
    if (u.includes('/entries/')) return Promise.resolve(jsonResponse(ENTRY_ENV));
    if (u.includes('/periods')) return Promise.resolve(jsonResponse(PERIODS_ENV));
    if (u.includes('/discrepancies'))
      return Promise.resolve(jsonResponse(DISC_ENV));
    return Promise.resolve(jsonResponse(TB_ENV));
  });
}

beforeEach(() => {
  cookieJar.clear();
  vi.unstubAllGlobals();
});

describe('getLedgerSectionState — eligibility + id gates (§ 2.4.7.1)', () => {
  it('not eligible → notEligible block, NO ledger call fabricated', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);

    const state = await getLedgerSectionState(false, 'je-1');
    expect(state.notEligible).toBe(true);
    expect(state.trialBalance).toBeNull();
    expect(state.periods).toBeNull();
    expect(state.discrepancies).toBeNull();
    expect(state.entry).toBeNull();
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('eligible (no entryId) → seeds the browsable index reads, NO entry call', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    const fetchMock = routed();
    vi.stubGlobal('fetch', fetchMock);

    const state = await getLedgerSectionState(true, null);
    expect(state.notEligible).toBe(false);
    expect(state.trialBalance).not.toBeNull();
    expect(state.periods).not.toBeNull();
    expect(state.discrepancies).not.toBeNull();
    expect(state.entry).toBeNull();
    // The OPEN discrepancy queue is seeded with status=OPEN.
    const discCall = fetchMock.mock.calls.find((c) =>
      String(c[0]).includes('/discrepancies'),
    );
    expect(String(discCall?.[0])).toContain('status=OPEN');
    // No entry call when no entryId.
    expect(
      fetchMock.mock.calls.some((c) => String(c[0]).includes('/entries/')),
    ).toBe(false);
    // 3 browsable index reads.
    expect(fetchMock.mock.calls.length).toBe(3);
  });

  it('eligible + entryId → additionally seeds the journal entry (IAM OIDC token, no X-Tenant-Id)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    const fetchMock = routed();
    vi.stubGlobal('fetch', fetchMock);

    const state = await getLedgerSectionState(true, 'je-1');
    expect(state.trialBalance).not.toBeNull();
    expect(state.entry).not.toBeNull();
    expect(state.notFound).toBe(false);
    expect(fetchMock.mock.calls.length).toBe(4);
    for (const [, init] of fetchMock.mock.calls) {
      const h = (init as RequestInit).headers as Record<string, string>;
      expect(h.Authorization).toBe('Bearer GAP-ACCESS');
      expect(h['X-Tenant-Id']).toBeUndefined();
    }
  });

  it('403 → forbidden (inline, no crash)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    vi.stubGlobal(
      'fetch',
      vi.fn(() => Promise.resolve(ledgerError('TENANT_FORBIDDEN', 403))),
    );
    const state = await getLedgerSectionState(true, null);
    expect(state.forbidden).toBe(true);
    expect(state.degraded).toBe(false);
  });

  it('404 JOURNAL_ENTRY_NOT_FOUND (seeded entryId) → notFound (inline actionable, no crash)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    vi.stubGlobal(
      'fetch',
      vi.fn((url: string) => {
        const u = String(url);
        if (u.includes('/entries/'))
          return Promise.resolve(ledgerError('JOURNAL_ENTRY_NOT_FOUND', 404));
        if (u.includes('/periods'))
          return Promise.resolve(jsonResponse(PERIODS_ENV));
        if (u.includes('/discrepancies'))
          return Promise.resolve(jsonResponse(DISC_ENV));
        return Promise.resolve(jsonResponse(TB_ENV));
      }),
    );
    const state = await getLedgerSectionState(true, 'nope');
    expect(state.notFound).toBe(true);
    expect(state.degraded).toBe(false);
    expect(state.forbidden).toBe(false);
  });

  it('503 → degraded (ledger section only — shell + other sections intact)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    vi.stubGlobal(
      'fetch',
      vi.fn(() => Promise.resolve(ledgerError('SERVICE_UNAVAILABLE', 503))),
    );
    const state = await getLedgerSectionState(true, null);
    expect(state.degraded).toBe(true);
    expect(state.notEligible).toBe(false);
  });

  it('a stray 429 (no documented ledger 429) → degraded (NOT a fabricated backoff)', async () => {
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
    const state = await getLedgerSectionState(true, null);
    expect(state.degraded).toBe(true);
    expect(state.notFound).toBe(false);
    expect(fetchMock.mock.calls.length).toBeLessThanOrEqual(3);
    for (const [, init] of fetchMock.mock.calls) {
      expect((init as RequestInit).method).toBe('GET');
    }
  });

  it('401 → whole-session re-login (redirect, not a per-section degrade)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    vi.stubGlobal(
      'fetch',
      vi.fn(() => Promise.resolve(ledgerError('UNAUTHORIZED', 401))),
    );
    const err = await getLedgerSectionState(true, null).catch((e) => e);
    expect(err).toBeInstanceOf(Error);
    expect((err as Error).message).toBe('REDIRECT:/login');
  });
});
