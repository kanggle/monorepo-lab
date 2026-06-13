import { describe, it, expect, vi, beforeEach } from 'vitest';
import path from 'node:path';
import { readFileSync, readdirSync, statSync } from 'node:fs';

/**
 * `features/ledger-ops/api/ledger-api.ts` — reconciliation statement-detail
 * read (TASK-PC-FE-075 — § 2.4.7.1: `getStatement`). STRICTLY READ-ONLY.
 *
 * Core assertions (mirrors ledger-account-api.test.ts):
 *   - domain-facing IAM OIDC token (`getDomainFacingToken()`) ONLY — the
 *     operator token (`getOperatorToken()`) is ABSENT;
 *   - GET only — NO body, NO Idempotency-Key, NO X-Operator-Reason,
 *     NO X-Tenant-Id;
 *   - the statementId is `encodeURIComponent`-encoded on the producer path;
 *   - F5 round-trip: match `money` survives as a bit-exact minor-units string;
 *   - 404 RECONCILIATION_STATEMENT_NOT_FOUND → ApiError(404);
 *   - 503 / timeout → LedgerUnavailableError;
 *   - a stray 429 → plain ApiError (no retry, NO Retry-After branch);
 *   - sanitised `logPath` carries NO statementId (F7).
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
import { getStatement } from '@/features/ledger-ops/api/ledger-api';
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

const STATEMENT_ENVELOPE = {
  data: {
    statementId: 'stmt-1',
    ledgerAccountCode: 'CUSTOMER_WALLET:acc-1',
    source: 'BANK_FEED',
    statementDate: '2026-06-13',
    matchedCount: 2,
    discrepancyCount: 1,
    matches: [
      {
        statementLineExternalRef: 'ext-ref-001',
        journalEntryId: 'je-123',
        money: M('9007199254740993'), // > 2^53 — bit-exact F5 guard
      },
      {
        statementLineExternalRef: 'ext-ref-002',
        journalEntryId: 'je-456',
        money: M('13500', 'USD'),
      },
    ],
    discrepancies: [
      {
        discrepancyId: 'd-1',
        type: 'AMOUNT_MISMATCH',
        externalRef: 'ext-ref-003',
        journalEntryId: 'je-789',
        expectedMinor: '100',
        actualMinor: '105',
        currency: 'KRW',
        status: 'OPEN',
      },
    ],
  },
  meta: { timestamp: '2026-06-13T00:00:00Z' },
};

beforeEach(() => {
  cookieJar.clear();
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

// ===========================================================================
// 1. Per-domain credential — domain-facing IAM OIDC token, NEVER operator
// ===========================================================================

describe('statement api — per-domain credential (getStatement)', () => {
  it('uses the IAM OIDC ACCESS cookie (NOT the operator token)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-OIDC-ACCESS-required-by-ledger');
    cookieJar.set(OPERATOR_COOKIE, 'OPERATOR-TOKEN-must-not-be-used');
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(STATEMENT_ENVELOPE));
    vi.stubGlobal('fetch', fetchMock);

    await getStatement('stmt-1');

    const [, init] = fetchMock.mock.calls[0];
    const h = (init as RequestInit).headers as Record<string, string>;
    expect(h.Authorization).toBe('Bearer GAP-OIDC-ACCESS-required-by-ledger');
    expect(h.Authorization).not.toContain('OPERATOR-TOKEN-must-not-be-used');
  });

  it('uses getDomainFacingToken() and NEVER getOperatorToken()', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-OIDC-ACCESS');
    cookieJar.set(OPERATOR_COOKIE, 'OPERATOR-TOKEN');
    const getDomainFacingSpy = vi.spyOn(sessionModule, 'getDomainFacingToken');
    const getOperatorSpy = vi.spyOn(sessionModule, 'getOperatorToken');
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(jsonResponse(STATEMENT_ENVELOPE)),
    );

    await getStatement('stmt-1');

    expect(getDomainFacingSpy).toHaveBeenCalled();
    // The operator-token path is ABSENT for the statement read.
    expect(getOperatorSpy).not.toHaveBeenCalled();
  });
});

// ===========================================================================
// 2. STRICTLY GET — no mutation artifacts.
// ===========================================================================

describe('statement api — STRICTLY GET (no Idempotency-Key / X-Operator-Reason / X-Tenant-Id / body)', () => {
  beforeEach(() => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-OIDC-ACCESS');
  });

  it('GET only, NO Idempotency-Key, NO X-Operator-Reason, NO X-Tenant-Id, NO body', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(STATEMENT_ENVELOPE));
    vi.stubGlobal('fetch', fetchMock);

    await getStatement('stmt-1');

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
// 3. statementId is URL-encoded on the path.
// ===========================================================================

describe('statement api — statementId is URL-encoded on the path', () => {
  beforeEach(() => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-OIDC-ACCESS');
  });

  it('encodeURIComponent is applied to statementId (special chars encoded)', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(STATEMENT_ENVELOPE));
    vi.stubGlobal('fetch', fetchMock);

    await getStatement('stmt/with:special chars');

    const url = String(fetchMock.mock.calls[0][0]);
    // Special chars must be encoded.
    expect(url).toContain('stmt%2Fwith%3Aspecial%20chars');
    expect(url).not.toContain('stmt/with:special chars');
    expect(url).toContain(
      'http://finance.local/api/finance/ledger/reconciliation/statements/stmt%2Fwith%3Aspecial%20chars',
    );
  });

  it('a plain alphanumeric statementId passes through (no double-encoding)', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(STATEMENT_ENVELOPE));
    vi.stubGlobal('fetch', fetchMock);

    await getStatement('stmt-1');

    const url = String(fetchMock.mock.calls[0][0]);
    expect(url).toContain(
      'http://finance.local/api/finance/ledger/reconciliation/statements/stmt-1',
    );
  });
});

// ===========================================================================
// 4. F5 — match money round-trips bit-exact as minor-units strings.
// ===========================================================================

describe('statement api — F5 money invariant (bit-exact minor-units strings, no Number coercion)', () => {
  beforeEach(() => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-OIDC-ACCESS');
  });

  it('large integer match money survives round-trip as a bit-exact string (> 2^53)', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(jsonResponse(STATEMENT_ENVELOPE)),
    );
    const result = await getStatement('stmt-1');
    // The first match money is the > 2^53 value — a JS Number would lose precision.
    expect(result.matches[0].money.amount).toBe('9007199254740993');
    expect(typeof result.matches[0].money.amount).toBe('string');
  });

  it('USD match money (scale 2) round-trips bit-exact as a string', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(jsonResponse(STATEMENT_ENVELOPE)),
    );
    const result = await getStatement('stmt-1');
    expect(result.matches[1].money.amount).toBe('13500');
    expect(result.matches[1].money.currency).toBe('USD');
    expect(typeof result.matches[1].money.amount).toBe('string');
  });

  it('discrepancy expectedMinor / actualMinor survive as strings', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(jsonResponse(STATEMENT_ENVELOPE)),
    );
    const result = await getStatement('stmt-1');
    expect(result.discrepancies[0].expectedMinor).toBe('100');
    expect(result.discrepancies[0].actualMinor).toBe('105');
    expect(typeof result.discrepancies[0].expectedMinor).toBe('string');
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
// 5. Error mapping — 404 RECONCILIATION_STATEMENT_NOT_FOUND / 503 / timeout / 429.
// ===========================================================================

describe('statement api — error mapping (404 / 503 / timeout / 429)', () => {
  beforeEach(() => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-OIDC-ACCESS');
  });

  it('404 RECONCILIATION_STATEMENT_NOT_FOUND → ApiError(404) inline actionable', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        ledgerError('RECONCILIATION_STATEMENT_NOT_FOUND', 404, 'no such statement'),
      ),
    );
    const err = await getStatement('nope').catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(404);
    expect(err.code).toBe('RECONCILIATION_STATEMENT_NOT_FOUND');
    expect(err.message).toBe('no such statement');
  });

  it('503 → LedgerUnavailableError (section degrade only)', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(ledgerError('SERVICE_UNAVAILABLE', 503)),
    );
    const err = await getStatement('stmt-1').catch((e) => e);
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
    const err = await getStatement('stmt-1').catch((e) => e);
    expect(err).toBeInstanceOf(LedgerUnavailableError);
    expect(err.reason).toBe('timeout');
  });

  // No 429 / Retry-After branch — the ledger has no documented 429.
  it('a stray 429 → plain ApiError, NO retry, NO Retry-After honour', async () => {
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

    const err = await getStatement('stmt-1').catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(429);
    // EXACTLY ONE fetch — no retry, no Retry-After honour.
    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(n).toBe(1);
  });
});

// ===========================================================================
// 6. F7 — sanitised logPath carries NO statementId.
// ===========================================================================

describe('statement api — F7 (sanitised logPath, no statementId logged)', () => {
  it('the log path carries NO statementId — only the {id} placeholder', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-OIDC-ACCESS');
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(jsonResponse(STATEMENT_ENVELOPE)),
    );
    const logSpy = vi.spyOn(console, 'log').mockImplementation(() => {});
    const infoSpy = vi.spyOn(console, 'info').mockImplementation(() => {});
    const warnSpy = vi.spyOn(console, 'warn').mockImplementation(() => {});
    const errorSpy = vi.spyOn(console, 'error').mockImplementation(() => {});

    await getStatement('SUPER-SECRET-STMT-ID-12345');

    const all = [
      ...logSpy.mock.calls,
      ...infoSpy.mock.calls,
      ...warnSpy.mock.calls,
      ...errorSpy.mock.calls,
    ]
      .map((args) => args.map(String).join(' '))
      .join('\n');

    expect(all).not.toContain('GAP-OIDC-ACCESS'); // token
    expect(all).not.toContain('SUPER-SECRET-STMT-ID-12345'); // statement id
    expect(all).not.toContain('9007199254740993'); // match money amount
  });
});

// ===========================================================================
// 7. Tolerant parsing — unknown source / type / status values.
// ===========================================================================

describe('statement api — tolerant parsing (unknown enum values never throw)', () => {
  beforeEach(() => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-OIDC-ACCESS');
  });

  it('an unknown / future source + discrepancy type + status parse without throwing', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        jsonResponse({
          data: {
            ...STATEMENT_ENVELOPE.data,
            source: 'FUTURE_SOURCE_TYPE',
            discrepancies: [
              {
                ...STATEMENT_ENVELOPE.data.discrepancies[0],
                type: 'FUTURE_DISCREPANCY_TYPE',
                status: 'FUTURE_STATUS',
              },
            ],
          },
          meta: { timestamp: 'x' },
        }),
      ),
    );
    const result = await getStatement('stmt-1');
    expect(result.source).toBe('FUTURE_SOURCE_TYPE');
    expect(result.discrepancies[0].type).toBe('FUTURE_DISCREPANCY_TYPE');
    expect(result.discrepancies[0].status).toBe('FUTURE_STATUS');
  });
});

// ===========================================================================
// 8. Module exports — getStatement exported alongside prior reads.
// ===========================================================================

describe('statement api — module exports (read-only, no new mutation)', () => {
  it('the module exports getStatement alongside all prior reads + resolve', async () => {
    const mod = await import('@/features/ledger-ops/api/ledger-api');
    const keys = Object.keys(mod);
    expect(keys).toContain('getTrialBalance');
    expect(keys).toContain('getJournalEntry');
    expect(keys).toContain('listDiscrepancies');
    expect(keys).toContain('resolveDiscrepancy'); // the ledger's ONLY mutation
    expect(keys).toContain('getAccountBalance');
    expect(keys).toContain('getAccountEntries');
    // TASK-PC-FE-075 — new statement read.
    expect(keys).toContain('getStatement');
  });
});
