import { describe, it, expect, vi, beforeEach } from 'vitest';
import { readFileSync, readdirSync, statSync } from 'node:fs';
import path from 'node:path';

/**
 * `features/ledger-ops/api/ledger-api.ts` — the security-critical core of
 * TASK-PC-FE-072 (the SECOND finance-product service section, the
 * `ledger-service` alongside the FE-009 `account-service`).
 * STRICTLY READ-ONLY.
 *
 * THE CENTRAL ASSERTIONS (console-integration-contract § 2.4.7.1 — REUSE
 * of the § 2.4.7 / § 2.4.5 per-domain credential rule, NOT re-derived):
 *
 *   - every ledger call's bearer is the **domain-facing IAM OIDC access
 *     token** (`getDomainFacingToken()`), NEVER the exchanged operator
 *     token (`getOperatorToken()` is ABSENT — the #569 invariant is
 *     GAP-domain-scoped and does NOT generalise to the ledger);
 *   - the console sends NO `X-Tenant-Id` (the ledger resolves tenant from
 *     the JWT `tenant_id ∈ {finance,*}` claim producer-side);
 *   - EVERY call is a pure GET — NO mutation artifacts anywhere (no
 *     Idempotency-Key, no X-Operator-Reason, no body, no ledger write);
 *   - the ledger FLAT error envelope `{ code, message, details?, timestamp }`
 *     is parsed (NOT wms's NESTED `{ error: { code } }`);
 *   - **NO 429 / Retry-After / backoff branch** (the ledger has no
 *     documented 429; a stray 429 falls through as a surfaced `ApiError`
 *     with NO retry, NO Retry-After honour);
 *   - 401 → ApiError(401); 403 → ApiError(403); 404 (entry/period/recon) →
 *     ApiError inline; 503/timeout → LedgerUnavailableError.
 *
 * F5 (§ 2.4.7.1 NORMATIVE): large minor-units + exact decimal rate
 * round-trip bit-exact; a static grep over the on-disk
 * `features/ledger-ops/` source asserts NO `Number()` / `parseFloat()` /
 * `parseInt()` on a line referencing `amount` or `exchangeRate`.
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

// Spy the session module so we can assert WHICH credential accessor the
// ledger client uses (the central per-domain-credential assertion).
import * as sessionModule from '@/shared/lib/session';

import {
  getTrialBalance,
  listPeriods,
  getPeriod,
  getJournalEntry,
  listDiscrepancies,
  getDiscrepancy,
  resolveDiscrepancy,
} from '@/features/ledger-ops/api/ledger-api';
import { ApiError, LedgerUnavailableError } from '@/shared/api/errors';
import { ACCESS_COOKIE, OPERATOR_COOKIE } from '@/shared/lib/session';
import {
  formatMoney,
  MoneySchema,
  TrialBalanceSchema,
  JournalEntrySchema,
} from '@/features/ledger-ops/api/types';

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

/** ledger FLAT error envelope (same wire shape as scm/finance but a
 *  DISTINCT producer; NOT wms's nested `{ error: { code } }`). */
function ledgerError(code: string, status: number, message = 'err') {
  return new Response(
    JSON.stringify({
      code,
      message,
      timestamp: '2026-05-20T00:00:00.000Z',
    }),
    { status, headers: { 'Content-Type': 'application/json' } },
  );
}

const M = (amount: string, currency = 'KRW') => ({ amount, currency });

const TRIAL_BALANCE_ENVELOPE = {
  data: {
    accounts: [
      {
        ledgerAccountCode: '1000',
        debitTotal: M('1234567890123'), // large minor-units string — F5
        creditTotal: M('0'),
        baseDebitTotal: M('1234567890123'),
        baseCreditTotal: M('0'),
      },
    ],
    grandDebitTotal: M('1234567890123'),
    grandCreditTotal: M('1234567890123'),
    grandBaseDebitTotal: M('1234567890123'),
    grandBaseCreditTotal: M('1234567890123'),
    inBalance: true,
  },
  meta: { timestamp: '2026-05-20T00:00:00Z' },
};

const PERIODS_ENVELOPE = {
  data: [
    { periodId: '2026-05', status: 'OPEN', from: 'a', to: 'b', entryCount: 3 },
    { periodId: '2026-04', status: 'CLOSED', from: 'c', to: 'd', entryCount: 9 },
  ],
  meta: { page: 0, size: 20, totalElements: 2, timestamp: 'x' },
};

const PERIOD_DETAIL_ENVELOPE = {
  data: {
    periodId: '2026-04',
    status: 'CLOSED',
    from: 'c',
    to: 'd',
    snapshot: {
      accounts: [{ ledgerAccountCode: '1000', debitTotal: M('100'), creditTotal: M('0') }],
      grandDebitTotal: M('100'),
      grandCreditTotal: M('100'),
      inBalance: true,
    },
  },
  meta: { timestamp: 'x' },
};

const ENTRY_ENVELOPE = {
  data: {
    entryId: 'je-1',
    postedAt: '2026-05-19T10:00:00Z',
    source: { sourceType: 'TRANSACTION' },
    lines: [
      {
        ledgerAccountCode: '1000',
        direction: 'DEBIT',
        money: M('13500', 'USD'),
        exchangeRate: '13.5', // exact decimal string — F5
        baseAmount: M('182250'),
      },
    ],
    balanced: true,
  },
  meta: { timestamp: 'x' },
};

const DISCREPANCIES_ENVELOPE = {
  data: [
    {
      discrepancyId: 'd-1',
      type: 'AMOUNT_MISMATCH',
      externalRef: 'bank-ref-1',
      journalEntryId: 'je-9',
      expectedMinor: '100',
      actualMinor: '105',
      currency: 'KRW',
      status: 'OPEN',
    },
  ],
  meta: { page: 0, size: 20, totalElements: 1, timestamp: 'x' },
};

const DISCREPANCY_DETAIL_ENVELOPE = {
  data: {
    discrepancyId: 'd-2',
    type: 'UNMATCHED_EXTERNAL',
    externalRef: 'bank-ref-2',
    journalEntryId: null,
    expectedMinor: '0',
    actualMinor: '500',
    currency: 'KRW',
    status: 'RESOLVED',
    resolution: {
      resolutionType: 'MANUAL_MATCH',
      note: 'matched to je-x',
      resolvedBy: 'op-1',
      resolvedAt: '2026-05-20T00:00:00Z',
    },
  },
  meta: { timestamp: 'x' },
};

beforeEach(() => {
  cookieJar.clear();
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

// ===========================================================================
// 1. Per-domain credential — domain-facing IAM OIDC token, NEVER operator
// ===========================================================================

describe('ledger-api — per-domain credential selection (REUSE of § 2.4.7/§ 2.4.5; the INVERSE of #569)', () => {
  it('sends the IAM OIDC ACCESS cookie as the bearer (NOT the operator token)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-OIDC-ACCESS-required-by-ledger');
    cookieJar.set(OPERATOR_COOKIE, 'OPERATOR-TOKEN-must-not-be-used');

    const fetchMock = vi
      .fn()
      .mockResolvedValue(jsonResponse(TRIAL_BALANCE_ENVELOPE));
    vi.stubGlobal('fetch', fetchMock);

    await getTrialBalance();

    const [url, init] = fetchMock.mock.calls[0];
    const headers = (init as RequestInit).headers as Record<string, string>;
    expect(headers.Authorization).toBe(
      'Bearer GAP-OIDC-ACCESS-required-by-ledger',
    );
    expect(headers.Authorization).not.toContain(
      'OPERATOR-TOKEN-must-not-be-used',
    );
    expect(String(url)).toContain(
      'http://finance.local/api/finance/ledger/trial-balance',
    );
  });

  it('uses getDomainFacingToken() and NEVER getOperatorToken() for the ledger (pins the per-domain rule)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-OIDC-ACCESS');
    cookieJar.set(OPERATOR_COOKIE, 'OPERATOR-TOKEN');
    const getDomainFacingSpy = vi.spyOn(sessionModule, 'getDomainFacingToken');
    const getOperatorSpy = vi.spyOn(sessionModule, 'getOperatorToken');
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(jsonResponse(PERIODS_ENVELOPE)),
    );

    await listPeriods({ page: 0, size: 20 });

    expect(getDomainFacingSpy).toHaveBeenCalled();
    // The operator-token path is ABSENT for the ledger.
    expect(getOperatorSpy).not.toHaveBeenCalled();
  });

  it('throws 401 with NO fetch when the IAM session is absent (whole-session re-login signal)', async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);

    const err = await getTrialBalance().catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(401);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('sends NO X-Tenant-Id (the ledger resolves tenant from the JWT claim)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-OIDC-ACCESS');
    const fetchMock = vi
      .fn()
      .mockResolvedValue(jsonResponse(TRIAL_BALANCE_ENVELOPE));
    vi.stubGlobal('fetch', fetchMock);

    await getTrialBalance();

    const headers = (fetchMock.mock.calls[0][1] as RequestInit)
      .headers as Record<string, string>;
    expect(headers['X-Tenant-Id']).toBeUndefined();
    expect(headers['X-Request-Id']).toBeTruthy();
  });
});

// ===========================================================================
// 2. STRICTLY read-only — every call is a pure GET, no mutation artifacts.
// ===========================================================================

describe('ledger-api — STRICTLY read-only (no mutation artifacts anywhere; § 2.4.7.1)', () => {
  beforeEach(() => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-OIDC-ACCESS');
  });

  it('every read is a pure GET with NO mutation artifacts', async () => {
    const calls: Array<[string, RequestInit]> = [];
    const fetchMock = vi.fn((u: string, init?: RequestInit) => {
      calls.push([String(u), init as RequestInit]);
      const us = String(u);
      if (us.includes('/periods/')) {
        return Promise.resolve(jsonResponse(PERIOD_DETAIL_ENVELOPE));
      }
      if (us.includes('/periods')) {
        return Promise.resolve(jsonResponse(PERIODS_ENVELOPE));
      }
      if (us.includes('/entries/')) {
        return Promise.resolve(jsonResponse(ENTRY_ENVELOPE));
      }
      if (us.includes('/discrepancies/')) {
        return Promise.resolve(jsonResponse(DISCREPANCY_DETAIL_ENVELOPE));
      }
      if (us.includes('/discrepancies')) {
        return Promise.resolve(jsonResponse(DISCREPANCIES_ENVELOPE));
      }
      return Promise.resolve(jsonResponse(TRIAL_BALANCE_ENVELOPE));
    });
    vi.stubGlobal('fetch', fetchMock);

    await getTrialBalance();
    await listPeriods({ page: 0, size: 20 });
    await getPeriod('2026-04');
    await getJournalEntry('je-1');
    await listDiscrepancies({ status: 'OPEN', page: 0, size: 20 });
    await getDiscrepancy('d-2');

    expect(calls.length).toBe(6);
    for (const [, init] of calls) {
      const h = init.headers as Record<string, string>;
      // The SIX reads are pure GETs with NO mutation artifacts. (The resolve
      // mutation — the ONLY POST — is asserted separately in section 7.)
      expect(init.method).toBe('GET');
      expect(init.body).toBeUndefined();
      expect(h['Idempotency-Key']).toBeUndefined();
      expect(h['X-Operator-Reason']).toBeUndefined();
      expect(h['Content-Type']).toBeUndefined();
    }
    // The api module exports the six read functions + EXACTLY ONE mutation
    // (`resolveDiscrepancy` — TASK-PC-FE-073) + two account-drill reads
    // (TASK-PC-FE-074: `getAccountBalance` + `getAccountEntries`) + one
    // statement-detail read (TASK-PC-FE-075: `getStatement`). No OTHER
    // ledger write (manual posting / revaluation / settlement / statement
    // ingest), no entry list/search (id-driven).
    const mod = await import('@/features/ledger-ops/api/ledger-api');
    expect(Object.keys(mod).sort()).toEqual(
      [
        'getTrialBalance',
        'listPeriods',
        'getPeriod',
        'getJournalEntry',
        'listDiscrepancies',
        'getDiscrepancy',
        'resolveDiscrepancy',
        // TASK-PC-FE-074 — account-level drill reads (read-only, GET only)
        'getAccountBalance',
        'getAccountEntries',
        // TASK-PC-FE-075 — reconciliation statement-detail read (read-only, GET only)
        'getStatement',
      ].sort(),
    );
  });

  it('the proxy directory exposes ONLY GET reads + EXACTLY ONE POST (the resolve mutation; no PUT/PATCH/DELETE)', async () => {
    const proxyRoot = path.resolve(__dirname, '../../src/app/api/ledger');
    function walk(p: string): string[] {
      const out: string[] = [];
      for (const name of readdirSync(p)) {
        const full = path.join(p, name);
        if (statSync(full).isDirectory()) out.push(...walk(full));
        else out.push(full);
      }
      return out;
    }
    const tsFiles = walk(proxyRoot).filter((f) => f.endsWith('.ts'));
    expect(tsFiles.length).toBeGreaterThan(0);

    let postRoutes = 0;
    for (const f of tsFiles) {
      const src = readFileSync(f, 'utf8');
      if (path.basename(f) === '_proxy.ts') continue;
      const isResolve = f.replace(/\\/g, '/').endsWith(
        'reconciliation/discrepancies/[id]/resolve/route.ts',
      );
      if (isResolve) {
        // The ONLY mutation route — POST only (no GET/PUT/PATCH/DELETE).
        expect(src).toMatch(/export\s+async\s+function\s+POST\b/);
        expect(src).not.toMatch(/export\s+async\s+function\s+GET\b/);
        postRoutes += 1;
      } else {
        // Every other route is a pure GET read.
        expect(src).toMatch(/export\s+async\s+function\s+GET\b/);
        expect(src).not.toMatch(/export\s+async\s+function\s+POST\b/);
      }
      expect(src).not.toMatch(/export\s+async\s+function\s+PUT\b/);
      expect(src).not.toMatch(/export\s+async\s+function\s+PATCH\b/);
      expect(src).not.toMatch(/export\s+async\s+function\s+DELETE\b/);
    }
    // EXACTLY ONE mutation route in the whole ledger proxy tree.
    expect(postRoutes).toBe(1);
  });
});

// ===========================================================================
// 3. F5 — money/rate are precision-exact strings (NEVER a Number).
// ===========================================================================

describe('ledger-api / types — F5 money invariant (precision-exact string; NO float/Number coercion)', () => {
  beforeEach(() => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-OIDC-ACCESS');
  });

  it('MoneySchema rejects a number `amount` — never accepted', () => {
    const result = MoneySchema.safeParse({ amount: 12345 as unknown, currency: 'KRW' });
    expect(result.success).toBe(false);
  });

  it('a large KRW minor-units amount round-trips bit-exact through the schema', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(jsonResponse(TRIAL_BALANCE_ENVELOPE)),
    );
    const tb = await getTrialBalance();
    expect(tb.accounts[0].debitTotal.amount).toBe('1234567890123');
    expect(typeof tb.accounts[0].debitTotal.amount).toBe('string');
    // Render + re-parse — the string body survives untouched.
    const rendered = formatMoney(tb.accounts[0].debitTotal);
    expect(rendered).toBe('1234567890123 KRW');
    const reparsed = MoneySchema.parse({
      amount: rendered.split(' ')[0],
      currency: 'KRW',
    });
    expect(reparsed.amount).toBe('1234567890123');
  });

  it('the exact decimal exchangeRate "13.5" survives the schema verbatim (never floated)', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(jsonResponse(ENTRY_ENVELOPE)),
    );
    const entry = await getJournalEntry('je-1');
    expect(entry.lines[0].exchangeRate).toBe('13.5');
    expect(typeof entry.lines[0].exchangeRate).toBe('string');
    // The multi-currency USD money is preserved as a string too.
    expect(entry.lines[0].money.amount).toBe('13500');
    expect(entry.lines[0].baseAmount.amount).toBe('182250');
  });

  it('TrialBalanceSchema / JournalEntrySchema require their F5 money fields', () => {
    expect(
      TrialBalanceSchema.safeParse({ accounts: [], inBalance: true }).success,
    ).toBe(false);
    expect(
      JournalEntrySchema.safeParse({ entryId: 'x', source: { sourceType: 'X' } })
        .success,
    ).toBe(false);
  });

  // The headline F5 grep — NEVER `Number()` / `parseFloat()` / `parseInt()`
  // on any line referencing `amount` OR `exchangeRate` in
  // `features/ledger-ops/`. Runs against the REAL on-disk source.
  it('the on-disk `features/ledger-ops/` source applies NO Number()/parseFloat()/parseInt() to `amount`/`exchangeRate`', () => {
    const root = path.resolve(__dirname, '../../src/features/ledger-ops');
    function walk(p: string): string[] {
      const out: string[] = [];
      for (const name of readdirSync(p)) {
        const full = path.join(p, name);
        if (statSync(full).isDirectory()) out.push(...walk(full));
        else out.push(full);
      }
      return out;
    }
    const files = walk(root).filter(
      (f) => f.endsWith('.ts') || f.endsWith('.tsx'),
    );
    expect(files.length).toBeGreaterThan(0);
    const offenders: string[] = [];
    for (const f of files) {
      const lines = readFileSync(f, 'utf8').split(/\r?\n/);
      lines.forEach((line, i) => {
        const trimmed = line.trim();
        if (
          trimmed.startsWith('*') ||
          trimmed.startsWith('//') ||
          trimmed.startsWith('/*')
        ) {
          return;
        }
        if (!/\bamount\b/.test(line) && !/\bexchangeRate\b/.test(line)) return;
        if (
          /\bNumber\s*\(/.test(line) ||
          /\bparseFloat\s*\(/.test(line) ||
          /\bparseInt\s*\(/.test(line)
        ) {
          offenders.push(`${f}:${i + 1}: ${line.trim()}`);
        }
      });
    }
    expect(offenders).toEqual([]);
  });
});

// ===========================================================================
// 4. confidential / F7 — no token / balance / line / code logging
// ===========================================================================

describe('ledger-api — confidential / F7 (no token / balance / line / code / id logging; § 2.4.7.1)', () => {
  beforeEach(() => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-OIDC-ACCESS');
  });

  it('logs neither the token nor the response body (success path)', async () => {
    const F7_ENTRY = {
      data: {
        ...ENTRY_ENVELOPE.data,
        lines: [
          {
            ledgerAccountCode: 'LEDGER-ACCT-CODE-SECRET',
            direction: 'DEBIT',
            money: M('13500', 'USD'),
            exchangeRate: '13.5',
            baseAmount: M('182250'),
          },
        ],
      },
      meta: { timestamp: 'x' },
    };
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(jsonResponse(F7_ENTRY)),
    );
    const logSpy = vi.spyOn(console, 'log').mockImplementation(() => {});
    const infoSpy = vi.spyOn(console, 'info').mockImplementation(() => {});
    const warnSpy = vi.spyOn(console, 'warn').mockImplementation(() => {});
    const errorSpy = vi.spyOn(console, 'error').mockImplementation(() => {});

    await getJournalEntry('je-secret-id');

    const all = [
      ...logSpy.mock.calls,
      ...infoSpy.mock.calls,
      ...warnSpy.mock.calls,
      ...errorSpy.mock.calls,
    ]
      .map((args) => args.map(String).join(' '))
      .join('\n');
    expect(all).not.toContain('GAP-OIDC-ACCESS'); // token
    expect(all).not.toContain('je-secret-id'); // entry id (sanitised path)
    expect(all).not.toContain('182250'); // base minor-units line value
    expect(all).not.toContain('13.5'); // exchangeRate provenance
    // The sanitised log path carries only the `{id}` shape — never a real
    // ledger account code. Use a distinctive code so the assertion is not a
    // UUID-hex false positive.
    expect(all).not.toContain('LEDGER-ACCT-CODE-SECRET');
  });
});

// ===========================================================================
// 5. ledger FLAT error envelope (NOT wms NESTED) + § 2.5 mapping.
//    NO 429 / Retry-After branch is taken (the ledger has no documented 429).
// ===========================================================================

describe('ledger-api — ledger FLAT error envelope (NOT wms NESTED) + § 2.5 + no-429-branch', () => {
  beforeEach(() => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-OIDC-ACCESS');
  });

  it('401 → ApiError(401) — whole-session re-login', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(ledgerError('UNAUTHORIZED', 401)),
    );
    const err = await getTrialBalance().catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(401);
    expect(err.code).toBe('UNAUTHORIZED');
  });

  it('403 TENANT_FORBIDDEN → ApiError(403) inline "not scoped"', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(ledgerError('TENANT_FORBIDDEN', 403)),
    );
    const err = await listPeriods().catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(403);
    expect(err.code).toBe('TENANT_FORBIDDEN');
  });

  it('404 JOURNAL_ENTRY_NOT_FOUND → ApiError(404) inline actionable (FLAT code parsed)', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(ledgerError('JOURNAL_ENTRY_NOT_FOUND', 404, 'no such entry')),
    );
    const err = await getJournalEntry('nope').catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(404);
    // From the FLAT top-level `code`, NOT a nested `error.code`.
    expect(err.code).toBe('JOURNAL_ENTRY_NOT_FOUND');
    expect(err.message).toBe('no such entry');
  });

  it('a wms-NESTED { error: { code } } body is NOT mis-parsed as ledger', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        new Response(
          JSON.stringify({ error: { code: 'WMS_NESTED_SHAPE', message: 'x' } }),
          { status: 422, headers: { 'Content-Type': 'application/json' } },
        ),
      ),
    );
    const err = await getDiscrepancy('d-1').catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(422);
    // The ledger parser reads the FLAT top-level `code` — a wms-nested body
    // has none → synthetic fallback (NOT 'WMS_NESTED_SHAPE').
    expect(err.code).toBe('HTTP_422');
  });

  it('503 → LedgerUnavailableError (ONLY the ledger section degrades)', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(ledgerError('SERVICE_UNAVAILABLE', 503)),
    );
    const err = await getTrialBalance().catch((e) => e);
    expect(err).toBeInstanceOf(LedgerUnavailableError);
    expect(err.reason).toBe('downstream');
  });

  it('timeout → LedgerUnavailableError(timeout)', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn((_u: string, init?: RequestInit) => {
        return new Promise((_res, rej) => {
          init?.signal?.addEventListener('abort', () => {
            const e = new Error('aborted');
            e.name = 'AbortError';
            rej(e);
          });
        });
      }),
    );
    const err = await listDiscrepancies().catch((e) => e);
    expect(err).toBeInstanceOf(LedgerUnavailableError);
    expect(err.reason).toBe('timeout');
  });

  it('a malformed / non-JSON error body does NOT crash (defensive parse)', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        new Response('not json', {
          status: 404,
          headers: { 'Content-Type': 'text/plain' },
        }),
      ),
    );
    const err = await getPeriod('x').catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(404);
    expect(err.code).toBe('HTTP_404'); // synthetic fallback, no throw
  });

  // The HEADLINE no-429 assertion (the honest difference from scm § 2.4.6).
  it('NO 429 handling path exists for the ledger — a stray 429 surfaces as a generic ApiError, NO retry, NO Retry-After honour', async () => {
    let n = 0;
    const fetchMock = vi.fn(() => {
      n += 1;
      return Promise.resolve(
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
    });
    vi.stubGlobal('fetch', fetchMock);

    const err = await getTrialBalance().catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(429);
    // EXACTLY ONE fetch — no retry, no Retry-After honour, no storm.
    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(n).toBe(1);
  });
});

// ===========================================================================
// 6. tolerant parsing — unknown / future enums never throw.
// ===========================================================================

describe('ledger-api / types — tolerant parsing (unknown enum → no throw; § 2.4.7.1)', () => {
  beforeEach(() => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-OIDC-ACCESS');
  });

  it('an unknown / future sourceType parses without throwing', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        jsonResponse({
          data: {
            ...ENTRY_ENVELOPE.data,
            source: { sourceType: 'FUTURE_SOURCE_TYPE' },
          },
          meta: { timestamp: 'x' },
        }),
      ),
    );
    const entry = await getJournalEntry('je-1');
    expect(entry.source.sourceType).toBe('FUTURE_SOURCE_TYPE');
  });

  it('an unknown / future period status + discrepancy type parse without throwing', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        jsonResponse({
          data: [
            {
              discrepancyId: 'd-9',
              type: 'FUTURE_DISCREPANCY_TYPE',
              expectedMinor: '1',
              actualMinor: '2',
              currency: 'KRW',
              status: 'FUTURE_STATUS',
            },
          ],
          meta: { page: 0, size: 20, totalElements: 1 },
        }),
      ),
    );
    const r = await listDiscrepancies();
    expect(r.data[0].type).toBe('FUTURE_DISCREPANCY_TYPE');
    expect(r.data[0].status).toBe('FUTURE_STATUS');
  });
});

// ===========================================================================
// 7. resolveDiscrepancy — the ledger's FIRST and ONLY mutation (PC-FE-073).
//    Header matrix: domain-facing token; body { resolutionType, note };
//    NO Idempotency-Key (the KEY deviation); NO X-Operator-Reason;
//    NO X-Tenant-Id. 409/422/404 → ApiError; 503/timeout → degrade.
// ===========================================================================

const RESOLVED_ENVELOPE = {
  data: {
    discrepancyId: 'd-1',
    type: 'AMOUNT_MISMATCH',
    externalRef: 'bank-ref-1',
    journalEntryId: 'je-9',
    expectedMinor: '100',
    actualMinor: '105',
    currency: 'KRW',
    status: 'RESOLVED',
    resolution: {
      resolutionType: 'WRITTEN_OFF',
      note: 'fx gap below threshold',
      resolvedBy: 'op-1',
      resolvedAt: '2026-05-20T00:00:00Z',
    },
  },
  meta: { timestamp: 'x' },
};

describe('ledger-api — resolveDiscrepancy (the ONLY mutation; PC-FE-073 header matrix)', () => {
  beforeEach(() => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-OIDC-ACCESS-required-by-ledger');
    cookieJar.set(OPERATOR_COOKIE, 'OPERATOR-TOKEN-must-not-be-used');
  });

  it('POSTs the domain-facing token + body { resolutionType, note }; NO Idempotency-Key, NO X-Operator-Reason, NO X-Tenant-Id', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValue(jsonResponse(RESOLVED_ENVELOPE));
    vi.stubGlobal('fetch', fetchMock);

    const result = await resolveDiscrepancy('d-1', {
      resolutionType: 'WRITTEN_OFF',
      note: 'fx gap below threshold',
    });

    const [url, init] = fetchMock.mock.calls[0];
    const h = (init as RequestInit).headers as Record<string, string>;
    expect((init as RequestInit).method).toBe('POST');
    // domain-facing IAM OIDC token — NEVER the operator token.
    expect(h.Authorization).toBe('Bearer GAP-OIDC-ACCESS-required-by-ledger');
    expect(h.Authorization).not.toContain('OPERATOR-TOKEN-must-not-be-used');
    // THE KEY DEVIATION from the delegation-revoke template: NO Idempotency-Key.
    expect(h['Idempotency-Key']).toBeUndefined();
    // NO X-Operator-Reason — the reason rides in the body `note`.
    expect(h['X-Operator-Reason']).toBeUndefined();
    // NO X-Tenant-Id — tenant from the JWT claim.
    expect(h['X-Tenant-Id']).toBeUndefined();
    // A body IS present (Content-Type added) — this is the mutation.
    expect(h['Content-Type']).toBe('application/json');
    const body = JSON.parse(String((init as RequestInit).body));
    expect(body).toEqual({
      resolutionType: 'WRITTEN_OFF',
      note: 'fx gap below threshold',
    });
    expect(body.idempotencyKey).toBeUndefined();
    expect(String(url)).toBe(
      'http://finance.local/api/finance/ledger/reconciliation/discrepancies/d-1/resolve',
    );
    // The returned discrepancy reflects RESOLVED + the resolution sub-object.
    expect(result.status).toBe('RESOLVED');
    expect(result.resolution?.resolutionType).toBe('WRITTEN_OFF');
  });

  it('uses getDomainFacingToken() and NEVER getOperatorToken() for the resolve', async () => {
    const getDomainFacingSpy = vi.spyOn(sessionModule, 'getDomainFacingToken');
    const getOperatorSpy = vi.spyOn(sessionModule, 'getOperatorToken');
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(jsonResponse(RESOLVED_ENVELOPE)),
    );
    await resolveDiscrepancy('d-1', {
      resolutionType: 'ACCEPTED',
      note: 'accepted',
    });
    expect(getDomainFacingSpy).toHaveBeenCalled();
    expect(getOperatorSpy).not.toHaveBeenCalled();
  });

  it('409 RECONCILIATION_ALREADY_RESOLVED → ApiError(409) inline', async () => {
    vi.stubGlobal(
      'fetch',
      vi
        .fn()
        .mockResolvedValue(ledgerError('RECONCILIATION_ALREADY_RESOLVED', 409)),
    );
    const err = await resolveDiscrepancy('d-1', {
      resolutionType: 'ACCEPTED',
      note: 'x',
    }).catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(409);
    expect(err.code).toBe('RECONCILIATION_ALREADY_RESOLVED');
  });

  it('422 RECONCILIATION_PERIOD_LOCKED → ApiError(422) inline', async () => {
    vi.stubGlobal(
      'fetch',
      vi
        .fn()
        .mockResolvedValue(ledgerError('RECONCILIATION_PERIOD_LOCKED', 422)),
    );
    const err = await resolveDiscrepancy('d-1', {
      resolutionType: 'ACCEPTED',
      note: 'x',
    }).catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(422);
    expect(err.code).toBe('RECONCILIATION_PERIOD_LOCKED');
  });

  it('404 RECONCILIATION_DISCREPANCY_NOT_FOUND → ApiError(404) inline', async () => {
    vi.stubGlobal(
      'fetch',
      vi
        .fn()
        .mockResolvedValue(
          ledgerError('RECONCILIATION_DISCREPANCY_NOT_FOUND', 404),
        ),
    );
    const err = await resolveDiscrepancy('nope', {
      resolutionType: 'ACCEPTED',
      note: 'x',
    }).catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(404);
    expect(err.code).toBe('RECONCILIATION_DISCREPANCY_NOT_FOUND');
  });

  it('503 → LedgerUnavailableError (the ledger section degrades, resolve re-enables on retry)', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(ledgerError('SERVICE_UNAVAILABLE', 503)),
    );
    const err = await resolveDiscrepancy('d-1', {
      resolutionType: 'ACCEPTED',
      note: 'x',
    }).catch((e) => e);
    expect(err).toBeInstanceOf(LedgerUnavailableError);
    expect(err.reason).toBe('downstream');
  });

  it('timeout → LedgerUnavailableError(timeout)', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn((_u: string, init?: RequestInit) => {
        return new Promise((_res, rej) => {
          init?.signal?.addEventListener('abort', () => {
            const e = new Error('aborted');
            e.name = 'AbortError';
            rej(e);
          });
        });
      }),
    );
    const err = await resolveDiscrepancy('d-1', {
      resolutionType: 'ACCEPTED',
      note: 'x',
    }).catch((e) => e);
    expect(err).toBeInstanceOf(LedgerUnavailableError);
    expect(err.reason).toBe('timeout');
  });

  it('no IAM session → 401 with NO fetch (whole-session re-login signal)', async () => {
    cookieJar.clear();
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    const err = await resolveDiscrepancy('d-1', {
      resolutionType: 'ACCEPTED',
      note: 'x',
    }).catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(401);
    expect(fetchMock).not.toHaveBeenCalled();
  });
});
