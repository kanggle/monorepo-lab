import { describe, it, expect, vi, beforeEach } from 'vitest';
import path from 'node:path';
import { readFileSync, readdirSync, statSync } from 'node:fs';

/**
 * `features/ledger-ops/api/ledger-api.ts` — account-level drill reads
 * (TASK-PC-FE-074 — § 2.4.7.1: `getAccountBalance` + `getAccountEntries`).
 *
 * Core assertions (mirrors ledger-api.test.ts section 1 / 2 / 3 / 5):
 *   - domain-facing IAM OIDC token (`getDomainFacingToken()`) ONLY — the
 *     operator token (`getOperatorToken()`) is ABSENT;
 *   - GET only — NO body, NO Idempotency-Key, NO X-Operator-Reason,
 *     NO X-Tenant-Id;
 *   - the colon-form code (`CUSTOMER_WALLET:acc-1`) is URL-encoded on the
 *     producer path (the colon becomes `%3A`);
 *   - F5 round-trip: `debitTotal` / `creditTotal` / `balance` + entry
 *     `money` survive as bit-exact minor-units strings;
 *   - 404 LEDGER_ACCOUNT_NOT_FOUND → ApiError(404);
 *   - 503 / timeout → LedgerUnavailableError;
 *   - a stray 429 → plain ApiError (no retry, NO Retry-After branch);
 *   - sanitised `logPath` carries NO account code (F7).
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

import * as sessionModule from '@/shared/lib/session';
import {
  getAccountBalance,
  getAccountEntries,
} from '@/features/ledger-ops/api/ledger-api';
import { ApiError, LedgerUnavailableError } from '@/shared/api/errors';
import { ACCESS_COOKIE, OPERATOR_COOKIE } from '@/shared/lib/session';

const M = (amount: string, currency = 'KRW') => ({ amount, currency });

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}
function ledgerError(code: string, status: number, message = 'err') {
  return new Response(
    JSON.stringify({ code, message, timestamp: '2026-06-13T00:00:00.000Z' }),
    { status, headers: { 'Content-Type': 'application/json' } },
  );
}

const BALANCE_ENVELOPE = {
  data: {
    ledgerAccountCode: 'CUSTOMER_WALLET:acc-1',
    type: 'LIABILITY',
    normalSide: 'CREDIT',
    debitTotal: M('1234567890123'), // large KRW minor-units string — F5
    creditTotal: M('9876543210987'),
    balance: M('8641975320864'),
    balanceSide: 'CREDIT',
  },
  meta: { timestamp: '2026-06-13T00:00:00Z' },
};

const ENTRIES_ENVELOPE = {
  data: [
    {
      entryId: 'je-acct-1',
      postedAt: '2026-06-13T10:00:00Z',
      direction: 'CREDIT',
      money: M('13500', 'USD'),
      counterpartyLines: null,
    },
  ],
  meta: { page: 0, size: 20, totalElements: 1, timestamp: 'x' },
};

beforeEach(() => {
  cookieJar.clear();
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

// ===========================================================================
// 1. Per-domain credential — domain-facing IAM OIDC token, NEVER operator
// ===========================================================================

describe('account-drill api — per-domain credential (getAccountBalance / getAccountEntries)', () => {
  it('getAccountBalance: uses the IAM OIDC ACCESS cookie (NOT the operator token)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-OIDC-ACCESS-required-by-ledger');
    cookieJar.set(OPERATOR_COOKIE, 'OPERATOR-TOKEN-must-not-be-used');
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(BALANCE_ENVELOPE));
    vi.stubGlobal('fetch', fetchMock);

    await getAccountBalance('CUSTOMER_WALLET:acc-1');

    const [, init] = fetchMock.mock.calls[0];
    const h = (init as RequestInit).headers as Record<string, string>;
    expect(h.Authorization).toBe(
      'Bearer GAP-OIDC-ACCESS-required-by-ledger',
    );
    expect(h.Authorization).not.toContain('OPERATOR-TOKEN-must-not-be-used');
  });

  it('getAccountEntries: uses the IAM OIDC ACCESS cookie (NOT the operator token)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-OIDC-ACCESS-required-by-ledger');
    cookieJar.set(OPERATOR_COOKIE, 'OPERATOR-TOKEN-must-not-be-used');
    const fetchMock = vi
      .fn()
      .mockResolvedValue(jsonResponse(ENTRIES_ENVELOPE));
    vi.stubGlobal('fetch', fetchMock);

    await getAccountEntries('CUSTOMER_WALLET:acc-1');

    const [, init] = fetchMock.mock.calls[0];
    const h = (init as RequestInit).headers as Record<string, string>;
    expect(h.Authorization).toBe(
      'Bearer GAP-OIDC-ACCESS-required-by-ledger',
    );
    expect(h.Authorization).not.toContain('OPERATOR-TOKEN-must-not-be-used');
  });

  it('uses getDomainFacingToken() and NEVER getOperatorToken()', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-OIDC-ACCESS');
    cookieJar.set(OPERATOR_COOKIE, 'OPERATOR-TOKEN');
    const getDomainFacingSpy = vi.spyOn(sessionModule, 'getDomainFacingToken');
    const getOperatorSpy = vi.spyOn(sessionModule, 'getOperatorToken');
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(jsonResponse(BALANCE_ENVELOPE)),
    );

    await getAccountBalance('ASSET:1000');

    expect(getDomainFacingSpy).toHaveBeenCalled();
    // The operator-token path is ABSENT for the account-drill reads.
    expect(getOperatorSpy).not.toHaveBeenCalled();
  });
});

// ===========================================================================
// 2. STRICTLY GET — no mutation artifacts.
// ===========================================================================

describe('account-drill api — STRICTLY GET (no Idempotency-Key / X-Operator-Reason / X-Tenant-Id / body)', () => {
  beforeEach(() => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-OIDC-ACCESS');
  });

  it('getAccountBalance: GET only, NO Idempotency-Key, NO X-Operator-Reason, NO X-Tenant-Id, NO body', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(BALANCE_ENVELOPE));
    vi.stubGlobal('fetch', fetchMock);

    await getAccountBalance('ASSET:1000');

    const [, init] = fetchMock.mock.calls[0];
    const h = (init as RequestInit).headers as Record<string, string>;
    expect((init as RequestInit).method).toBe('GET');
    expect((init as RequestInit).body).toBeUndefined();
    expect(h['Idempotency-Key']).toBeUndefined();
    expect(h['X-Operator-Reason']).toBeUndefined();
    expect(h['X-Tenant-Id']).toBeUndefined();
    expect(h['Content-Type']).toBeUndefined();
  });

  it('getAccountEntries: GET only, NO Idempotency-Key, NO X-Operator-Reason, NO X-Tenant-Id, NO body', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValue(jsonResponse(ENTRIES_ENVELOPE));
    vi.stubGlobal('fetch', fetchMock);

    await getAccountEntries('ASSET:1000', { page: 0, size: 20 });

    const [, init] = fetchMock.mock.calls[0];
    const h = (init as RequestInit).headers as Record<string, string>;
    expect((init as RequestInit).method).toBe('GET');
    expect((init as RequestInit).body).toBeUndefined();
    expect(h['Idempotency-Key']).toBeUndefined();
    expect(h['X-Operator-Reason']).toBeUndefined();
    expect(h['X-Tenant-Id']).toBeUndefined();
    expect(h['Content-Type']).toBeUndefined();
  });
});

// ===========================================================================
// 3. Colon-form code URL-encoding on the path.
// ===========================================================================

describe('account-drill api — colon-form code is URL-encoded on the path', () => {
  beforeEach(() => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-OIDC-ACCESS');
  });

  it('getAccountBalance: encodeURIComponent is applied (colon → %3A)', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(BALANCE_ENVELOPE));
    vi.stubGlobal('fetch', fetchMock);

    await getAccountBalance('CUSTOMER_WALLET:acc-1');

    const url = String(fetchMock.mock.calls[0][0]);
    // The colon is encoded — the producer path segment must not contain a raw colon.
    expect(url).toContain('CUSTOMER_WALLET%3Aacc-1');
    expect(url).not.toContain('/CUSTOMER_WALLET:acc-1/');
    expect(url).toContain(
      'http://finance.local/api/finance/ledger/accounts/CUSTOMER_WALLET%3Aacc-1/balance',
    );
  });

  it('getAccountEntries: encodeURIComponent is applied (colon → %3A)', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValue(jsonResponse(ENTRIES_ENVELOPE));
    vi.stubGlobal('fetch', fetchMock);

    await getAccountEntries('CUSTOMER_WALLET:acc-1', { page: 0, size: 20 });

    const url = String(fetchMock.mock.calls[0][0]);
    expect(url).toContain('CUSTOMER_WALLET%3Aacc-1');
    expect(url).toContain(
      'http://finance.local/api/finance/ledger/accounts/CUSTOMER_WALLET%3Aacc-1/entries',
    );
  });

  it('pagination params are forwarded on getAccountEntries', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValue(jsonResponse(ENTRIES_ENVELOPE));
    vi.stubGlobal('fetch', fetchMock);

    await getAccountEntries('ASSET:1000', { page: 2, size: 50 });

    const url = new URL(String(fetchMock.mock.calls[0][0]));
    expect(url.searchParams.get('page')).toBe('2');
    expect(url.searchParams.get('size')).toBe('50');
  });
});

// ===========================================================================
// 4. F5 — money round-trips bit-exact as minor-units strings.
// ===========================================================================

describe('account-drill api — F5 money invariant (bit-exact minor-units strings, no Number coercion)', () => {
  beforeEach(() => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-OIDC-ACCESS');
  });

  it('getAccountBalance: large KRW minor-units amount survives round-trip as a string', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(jsonResponse(BALANCE_ENVELOPE)),
    );
    const result = await getAccountBalance('CUSTOMER_WALLET:acc-1');
    expect(result.debitTotal.amount).toBe('1234567890123');
    expect(typeof result.debitTotal.amount).toBe('string');
    expect(result.creditTotal.amount).toBe('9876543210987');
    expect(result.balance.amount).toBe('8641975320864');
    // Tolerant free-string fields
    expect(result.type).toBe('LIABILITY');
    expect(result.normalSide).toBe('CREDIT');
    expect(result.balanceSide).toBe('CREDIT');
  });

  it('getAccountEntries: entry money survives round-trip as a string', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(jsonResponse(ENTRIES_ENVELOPE)),
    );
    const result = await getAccountEntries('CUSTOMER_WALLET:acc-1');
    expect(result.data[0].money.amount).toBe('13500');
    expect(typeof result.data[0].money.amount).toBe('string');
    expect(result.data[0].money.currency).toBe('USD');
  });

  // The F5 grep guard — must not introduce any Number()/parseFloat()/parseInt()
  // on lines referencing `amount` in the features/ledger-ops/ source tree.
  it('the on-disk source still has NO Number()/parseFloat()/parseInt() on `amount`/`exchangeRate` lines (F5 guard)', () => {
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
// 5. Error mapping — 404 LEDGER_ACCOUNT_NOT_FOUND / 503 / timeout / 429.
// ===========================================================================

describe('account-drill api — error mapping (404 / 503 / timeout / 429)', () => {
  beforeEach(() => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-OIDC-ACCESS');
  });

  it('getAccountBalance: 404 LEDGER_ACCOUNT_NOT_FOUND → ApiError(404) inline actionable', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(ledgerError('LEDGER_ACCOUNT_NOT_FOUND', 404, 'no such account')),
    );
    const err = await getAccountBalance('CUSTOMER_WALLET:nope').catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(404);
    expect(err.code).toBe('LEDGER_ACCOUNT_NOT_FOUND');
    expect(err.message).toBe('no such account');
  });

  it('getAccountEntries: 404 LEDGER_ACCOUNT_NOT_FOUND → ApiError(404) inline actionable', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(ledgerError('LEDGER_ACCOUNT_NOT_FOUND', 404, 'no such account')),
    );
    const err = await getAccountEntries('CUSTOMER_WALLET:nope').catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(404);
    expect(err.code).toBe('LEDGER_ACCOUNT_NOT_FOUND');
  });

  it('getAccountBalance: 503 → LedgerUnavailableError (section degrade only)', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(ledgerError('SERVICE_UNAVAILABLE', 503)),
    );
    const err = await getAccountBalance('ASSET:1000').catch((e) => e);
    expect(err).toBeInstanceOf(LedgerUnavailableError);
    expect(err.reason).toBe('downstream');
  });

  it('getAccountEntries: timeout → LedgerUnavailableError(timeout)', async () => {
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
    const err = await getAccountEntries('ASSET:1000').catch((e) => e);
    expect(err).toBeInstanceOf(LedgerUnavailableError);
    expect(err.reason).toBe('timeout');
  });

  // No 429 / Retry-After branch — the ledger has no documented 429. A stray
  // 429 surfaces as a generic ApiError with EXACTLY ONE fetch (no retry, no
  // Retry-After honour).
  it('getAccountBalance: a stray 429 → plain ApiError, NO retry, NO Retry-After honour', async () => {
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

    const err = await getAccountBalance('ASSET:1000').catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(429);
    // EXACTLY ONE fetch — no retry, no Retry-After honour.
    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(n).toBe(1);
  });
});

// ===========================================================================
// 6. F7 — sanitised logPath carries NO account code.
// ===========================================================================

describe('account-drill api — F7 (sanitised logPath, no account code logged)', () => {
  it('getAccountBalance: the log path carries NO account code — only the {code} placeholder', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-OIDC-ACCESS');
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(jsonResponse(BALANCE_ENVELOPE)),
    );
    const logSpy = vi.spyOn(console, 'log').mockImplementation(() => {});
    const infoSpy = vi.spyOn(console, 'info').mockImplementation(() => {});
    const warnSpy = vi.spyOn(console, 'warn').mockImplementation(() => {});
    const errorSpy = vi.spyOn(console, 'error').mockImplementation(() => {});

    await getAccountBalance('SUPER-SECRET-ACCOUNT-CODE:12345');

    const all = [
      ...logSpy.mock.calls,
      ...infoSpy.mock.calls,
      ...warnSpy.mock.calls,
      ...errorSpy.mock.calls,
    ]
      .map((args) => args.map(String).join(' '))
      .join('\n');

    expect(all).not.toContain('GAP-OIDC-ACCESS'); // token
    expect(all).not.toContain('SUPER-SECRET-ACCOUNT-CODE'); // account code
    expect(all).not.toContain('1234567890123'); // balance amount
  });

  it('getAccountEntries: the log path carries NO account code', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-OIDC-ACCESS');
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(jsonResponse(ENTRIES_ENVELOPE)),
    );
    const logSpy = vi.spyOn(console, 'log').mockImplementation(() => {});
    const infoSpy = vi.spyOn(console, 'info').mockImplementation(() => {});
    const warnSpy = vi.spyOn(console, 'warn').mockImplementation(() => {});
    const errorSpy = vi.spyOn(console, 'error').mockImplementation(() => {});

    await getAccountEntries('SUPER-SECRET-ACCOUNT-CODE:12345');

    const all = [
      ...logSpy.mock.calls,
      ...infoSpy.mock.calls,
      ...warnSpy.mock.calls,
      ...errorSpy.mock.calls,
    ]
      .map((args) => args.map(String).join(' '))
      .join('\n');

    expect(all).not.toContain('SUPER-SECRET-ACCOUNT-CODE');
    expect(all).not.toContain('GAP-OIDC-ACCESS');
  });
});

// ===========================================================================
// 7. Tolerant parsing — unknown type / normalSide / balanceSide / direction.
// ===========================================================================

describe('account-drill api — tolerant parsing (unknown enum values never throw)', () => {
  beforeEach(() => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-OIDC-ACCESS');
  });

  it('an unknown / future type + normalSide + balanceSide parse without throwing', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        jsonResponse({
          data: {
            ...BALANCE_ENVELOPE.data,
            type: 'FUTURE_ACCOUNT_TYPE',
            normalSide: 'FUTURE_SIDE',
            balanceSide: 'FUTURE_SIDE',
          },
          meta: { timestamp: 'x' },
        }),
      ),
    );
    const result = await getAccountBalance('ASSET:1000');
    expect(result.type).toBe('FUTURE_ACCOUNT_TYPE');
    expect(result.normalSide).toBe('FUTURE_SIDE');
    expect(result.balanceSide).toBe('FUTURE_SIDE');
  });

  it('an unknown / future direction on an entry parses without throwing', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        jsonResponse({
          data: [{ ...ENTRIES_ENVELOPE.data[0], direction: 'FUTURE_DIRECTION' }],
          meta: ENTRIES_ENVELOPE.meta,
        }),
      ),
    );
    const result = await getAccountEntries('ASSET:1000');
    expect(result.data[0].direction).toBe('FUTURE_DIRECTION');
  });
});

// ===========================================================================
// 8. The ledger api module now exports the account-drill functions alongside
//    the prior reads + the single resolve mutation (no new mutation added).
// ===========================================================================

describe('account-drill api — module exports (reads only, no new mutation)', () => {
  it('the module exports both new account-drill functions + all prior reads + resolve', async () => {
    const mod = await import('@/features/ledger-ops/api/ledger-api');
    const keys = Object.keys(mod);
    // Core pre-existing reads + one mutation + new account-drill reads.
    expect(keys).toContain('getTrialBalance');
    expect(keys).toContain('listPeriods');
    expect(keys).toContain('getPeriod');
    expect(keys).toContain('getJournalEntry');
    expect(keys).toContain('listDiscrepancies');
    expect(keys).toContain('getDiscrepancy');
    expect(keys).toContain('resolveDiscrepancy');
    // TASK-PC-FE-074 — new account-drill reads.
    expect(keys).toContain('getAccountBalance');
    expect(keys).toContain('getAccountEntries');
  });
});
