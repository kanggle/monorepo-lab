import { describe, it, expect, vi, beforeEach } from 'vitest';

/**
 * `features/ledger-ops/api/ledger-api.ts` — FX position open-lots drill read
 * (TASK-PC-FE-091 — § 2.4.7.1: `getPositionLots`, consuming `ledger-api.md`
 * § 12 / FIN-BE-028).
 *
 * Core assertions (mirrors ledger-account-api.test.ts):
 *   - domain-facing IAM OIDC token (`getDomainFacingToken()`) ONLY — the
 *     operator token (`getOperatorToken()`) is ABSENT;
 *   - GET only — NO body, NO Idempotency-Key, NO X-Operator-Reason,
 *     NO X-Tenant-Id;
 *   - the colon-form account code (`CUSTOMER_WALLET:acc-1`) AND the currency
 *     are URL-encoded on the producer path (the colon becomes `%3A`);
 *   - F5 round-trip: every `*Minor` field survives as bit-exact minor-units
 *     strings (incl. > 2^53 values), never Number-coerced;
 *   - empty position (lots: [], totals "0", lotCount 0) is a 200 success
 *     (NOT a 404 / error);
 *   - 400 VALIDATION_ERROR (bad currency) → ApiError(400);
 *   - 403 TENANT_FORBIDDEN → ApiError(403);
 *   - 503 / timeout → LedgerUnavailableError;
 *   - a stray 429 → plain ApiError (no retry, NO Retry-After branch);
 *   - sanitised `logPath` carries NEITHER account code NOR currency (F7).
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
import { getPositionLots } from '@/features/ledger-ops/api/ledger-api';
import { ApiError, LedgerUnavailableError } from '@/shared/api/errors';
import { ACCESS_COOKIE, OPERATOR_COOKIE } from '@/shared/lib/session';

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

const LOTS_ENVELOPE = {
  data: {
    lots: [
      {
        lotId: 'lot-1',
        currency: 'USD',
        acquiredAt: '2026-01-01T00:00:00Z',
        seq: 1,
        // > 2^53 minor-units — F5 precision must survive as a string.
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
  meta: { timestamp: '2026-06-13T00:00:00Z' },
};

const EMPTY_ENVELOPE = {
  data: {
    lots: [],
    totalRemainingForeignMinor: '0',
    totalCarryingBaseMinor: '0',
    lotCount: 0,
  },
  meta: { timestamp: 'x' },
};

beforeEach(() => {
  cookieJar.clear();
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

// ===========================================================================
// 1. Per-domain credential — domain-facing IAM OIDC token, NEVER operator.
// ===========================================================================

describe('position-lots api — per-domain credential (getPositionLots)', () => {
  it('uses the IAM OIDC ACCESS cookie (NOT the operator token)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-OIDC-ACCESS-required-by-ledger');
    cookieJar.set(OPERATOR_COOKIE, 'OPERATOR-TOKEN-must-not-be-used');
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(LOTS_ENVELOPE));
    vi.stubGlobal('fetch', fetchMock);

    await getPositionLots('CUSTOMER_WALLET:acc-1', 'USD');

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
      vi.fn().mockResolvedValue(jsonResponse(LOTS_ENVELOPE)),
    );

    await getPositionLots('ASSET:1000', 'USD');

    expect(getDomainFacingSpy).toHaveBeenCalled();
    // The operator-token path is ABSENT for the position-lots read.
    expect(getOperatorSpy).not.toHaveBeenCalled();
  });
});

// ===========================================================================
// 2. STRICTLY GET — no mutation artifacts.
// ===========================================================================

describe('position-lots api — STRICTLY GET (no Idempotency-Key / X-Operator-Reason / X-Tenant-Id / body)', () => {
  beforeEach(() => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-OIDC-ACCESS');
  });

  it('getPositionLots: GET only, NO Idempotency-Key, NO X-Operator-Reason, NO X-Tenant-Id, NO body', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(LOTS_ENVELOPE));
    vi.stubGlobal('fetch', fetchMock);

    await getPositionLots('ASSET:1000', 'USD');

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
// 3. Colon-form code + currency URL-encoding on the path.
// ===========================================================================

describe('position-lots api — account code + currency URL-encoded on the path', () => {
  beforeEach(() => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-OIDC-ACCESS');
  });

  it('encodeURIComponent is applied to the colon-form code (colon → %3A) and the path shape is correct', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(LOTS_ENVELOPE));
    vi.stubGlobal('fetch', fetchMock);

    await getPositionLots('CUSTOMER_WALLET:acc-1', 'USD');

    const url = String(fetchMock.mock.calls[0][0]);
    expect(url).toContain('CUSTOMER_WALLET%3Aacc-1');
    expect(url).not.toContain('/CUSTOMER_WALLET:acc-1/');
    expect(url).toBe(
      'http://finance.local/api/finance/ledger/settlements/CUSTOMER_WALLET%3Aacc-1/USD/lots',
    );
  });

  it('the currency is passed through on the path segment', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(LOTS_ENVELOPE));
    vi.stubGlobal('fetch', fetchMock);

    await getPositionLots('ASSET:1000', 'EUR');

    const url = String(fetchMock.mock.calls[0][0]);
    expect(url).toContain('/ASSET%3A1000/EUR/lots');
  });
});

// ===========================================================================
// 4. F5 — every *Minor field round-trips bit-exact as a minor-units string.
// ===========================================================================

describe('position-lots api — F5 money invariant (bit-exact minor-units strings, no Number coercion)', () => {
  beforeEach(() => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-OIDC-ACCESS');
  });

  it('every *Minor field + the totals survive round-trip as strings (incl. > 2^53)', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(jsonResponse(LOTS_ENVELOPE)),
    );
    const result = await getPositionLots('CUSTOMER_WALLET:acc-1', 'USD');
    const lot = result.lots[0];
    expect(lot.originalForeignMinor).toBe('9007199254740993');
    expect(typeof lot.originalForeignMinor).toBe('string');
    expect(lot.remainingForeignMinor).toBe('9007199254740993');
    expect(lot.originalBaseMinor).toBe('1300000');
    expect(lot.carryingBaseMinor).toBe('1300000');
    expect(result.totalRemainingForeignMinor).toBe('9007199254740993');
    expect(typeof result.totalRemainingForeignMinor).toBe('string');
    expect(result.totalCarryingBaseMinor).toBe('1300000');
    // seq + lotCount ARE numbers (index / count, not money).
    expect(lot.seq).toBe(1);
    expect(result.lotCount).toBe(1);
    expect(lot.currency).toBe('USD');
    expect(lot.sourceJournalEntryId).toBe('je-acq-1');
  });
});

// ===========================================================================
// 5. Empty position is a normal 200 — NOT a 404 / error.
// ===========================================================================

describe('position-lots api — empty position (200, NOT 404)', () => {
  beforeEach(() => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-OIDC-ACCESS');
  });

  it('an empty position parses to lots: [], totals "0", lotCount 0 (no throw, no error)', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(jsonResponse(EMPTY_ENVELOPE)),
    );
    const result = await getPositionLots('ASSET:1000', 'KRW');
    expect(result.lots).toEqual([]);
    expect(result.totalRemainingForeignMinor).toBe('0');
    expect(result.totalCarryingBaseMinor).toBe('0');
    expect(result.lotCount).toBe(0);
  });
});

// ===========================================================================
// 6. Error mapping — 400 / 403 / 503 / timeout / 429.
// ===========================================================================

describe('position-lots api — error mapping (400 / 403 / 503 / timeout / 429)', () => {
  beforeEach(() => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-OIDC-ACCESS');
  });

  it('400 VALIDATION_ERROR (unsupported currency) → ApiError(400) inline actionable', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(ledgerError('VALIDATION_ERROR', 400, 'bad currency')),
    );
    const err = await getPositionLots('ASSET:1000', 'ZZZ').catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(400);
    expect(err.code).toBe('VALIDATION_ERROR');
  });

  it('403 TENANT_FORBIDDEN → ApiError(403) inline (not scoped)', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(ledgerError('TENANT_FORBIDDEN', 403)),
    );
    const err = await getPositionLots('ASSET:1000', 'USD').catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(403);
    expect(err.code).toBe('TENANT_FORBIDDEN');
  });

  it('503 → LedgerUnavailableError (section degrade only)', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(ledgerError('SERVICE_UNAVAILABLE', 503)),
    );
    const err = await getPositionLots('ASSET:1000', 'USD').catch((e) => e);
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
    const err = await getPositionLots('ASSET:1000', 'USD').catch((e) => e);
    expect(err).toBeInstanceOf(LedgerUnavailableError);
    expect(err.reason).toBe('timeout');
  });

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

    const err = await getPositionLots('ASSET:1000', 'USD').catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(429);
    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(n).toBe(1);
  });
});

// ===========================================================================
// 7. F7 — sanitised logPath carries NEITHER account code NOR currency.
// ===========================================================================

describe('position-lots api — F7 (sanitised logPath, no account code / currency logged)', () => {
  it('the log path carries NO account code, NO currency, NO token, NO amount', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-OIDC-ACCESS');
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(jsonResponse(LOTS_ENVELOPE)),
    );
    const logSpy = vi.spyOn(console, 'log').mockImplementation(() => {});
    const infoSpy = vi.spyOn(console, 'info').mockImplementation(() => {});
    const warnSpy = vi.spyOn(console, 'warn').mockImplementation(() => {});
    const errorSpy = vi.spyOn(console, 'error').mockImplementation(() => {});

    await getPositionLots('SUPER-SECRET-ACCOUNT-CODE:12345', 'USD');

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
    expect(all).not.toContain('9007199254740993'); // minor-units amount
  });
});

// ===========================================================================
// 8. Module exports the new read alongside the prior reads (no new mutation).
// ===========================================================================

describe('position-lots api — module exports (reads only, no new mutation)', () => {
  it('the module exports getPositionLots + all prior reads + the single resolve', async () => {
    const mod = await import('@/features/ledger-ops/api/ledger-api');
    const keys = Object.keys(mod);
    expect(keys).toContain('getTrialBalance');
    expect(keys).toContain('getAccountBalance');
    expect(keys).toContain('getStatement');
    expect(keys).toContain('resolveDiscrepancy');
    // TASK-PC-FE-091 — new position-lots read.
    expect(keys).toContain('getPositionLots');
  });
});
