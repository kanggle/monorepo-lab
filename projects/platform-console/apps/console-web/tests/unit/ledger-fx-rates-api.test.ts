import { describe, it, expect, vi, beforeEach } from 'vitest';

/**
 * `features/ledger-ops/api/ledger-api.ts` — FX 환율 피드 read
 * (TASK-PC-FE-092 — § 2.4.7.1: `getFxRates`, consuming FIN-BE-033
 * `GET /api/finance/ledger/fx-rates`).
 *
 * Core assertions (mirrors ledger-lots-api.test.ts):
 *   - domain-facing IAM OIDC token (`getDomainFacingToken()`) ONLY — the
 *     operator token (`getOperatorToken()`) is ABSENT;
 *   - GET only — NO body, NO Idempotency-Key, NO X-Operator-Reason,
 *     NO X-Tenant-Id;
 *   - F5 round-trip: `rate` survives as a bit-exact decimal string (incl.
 *     high-precision e.g. "1300.12345678") — NEVER Number-coerced;
 *   - empty cache (rates: [], feedEnabled: true) is a 200 success
 *     (NOT a 404 / error);
 *   - 400 VALIDATION_ERROR → ApiError(400);
 *   - 403 TENANT_FORBIDDEN → ApiError(403);
 *   - 503 / timeout → LedgerUnavailableError;
 *   - a stray 429 → plain ApiError (no retry, NO Retry-After branch);
 *   - sanitised `logPath` carries no token (F7);
 *   - module exports `getFxRates`.
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
import { getFxRates } from '@/features/ledger-ops/api/ledger-api';
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
    JSON.stringify({ code, message, timestamp: '2026-06-15T00:00:00.000Z' }),
    { status, headers: { 'Content-Type': 'application/json' } },
  );
}

const FX_RATES_ENVELOPE = {
  data: {
    feedEnabled: true,
    rates: [
      {
        baseCurrency: 'KRW',
        foreignCurrency: 'USD',
        // High-precision decimal — F5 must survive as a string.
        rate: '1300.12345678',
        asOf: '2026-06-15T00:00:00Z',
        source: 'ECB',
        fetchedAt: '2026-06-15T00:01:00Z',
        ageSeconds: 60,
        stale: false,
      },
    ],
  },
  meta: { timestamp: '2026-06-15T00:01:00Z' },
};

const EMPTY_ENVELOPE = {
  data: {
    feedEnabled: true,
    rates: [],
  },
  meta: { timestamp: '2026-06-15T00:00:00Z' },
};

beforeEach(() => {
  cookieJar.clear();
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

// ===========================================================================
// 1. Per-domain credential — domain-facing IAM OIDC token, NEVER operator.
// ===========================================================================

describe('fx-rates api — per-domain credential (getFxRates)', () => {
  it('uses the IAM OIDC ACCESS cookie (NOT the operator token)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-OIDC-ACCESS-required-by-ledger');
    cookieJar.set(OPERATOR_COOKIE, 'OPERATOR-TOKEN-must-not-be-used');
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(FX_RATES_ENVELOPE));
    vi.stubGlobal('fetch', fetchMock);

    await getFxRates();

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
      vi.fn().mockResolvedValue(jsonResponse(FX_RATES_ENVELOPE)),
    );

    await getFxRates();

    expect(getDomainFacingSpy).toHaveBeenCalled();
    // The operator-token path is ABSENT for the fx-rates read.
    expect(getOperatorSpy).not.toHaveBeenCalled();
  });
});

// ===========================================================================
// 2. STRICTLY GET — no mutation artifacts.
// ===========================================================================

describe('fx-rates api — STRICTLY GET (no Idempotency-Key / X-Operator-Reason / X-Tenant-Id / body)', () => {
  beforeEach(() => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-OIDC-ACCESS');
  });

  it('getFxRates: GET only, NO Idempotency-Key, NO X-Operator-Reason, NO X-Tenant-Id, NO body', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(FX_RATES_ENVELOPE));
    vi.stubGlobal('fetch', fetchMock);

    await getFxRates();

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
// 3. Correct upstream URL.
// ===========================================================================

describe('fx-rates api — upstream URL', () => {
  beforeEach(() => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-OIDC-ACCESS');
  });

  it('calls the correct upstream path on LEDGER_BASE_URL', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(FX_RATES_ENVELOPE));
    vi.stubGlobal('fetch', fetchMock);

    await getFxRates();

    const url = String(fetchMock.mock.calls[0][0]);
    expect(url).toBe('http://finance.local/api/finance/ledger/fx-rates');
  });
});

// ===========================================================================
// 4. F5 — rate is a precision-exact decimal string, NEVER Number-coerced.
// ===========================================================================

describe('fx-rates api — F5 rate invariant (bit-exact decimal string, no Number coercion)', () => {
  beforeEach(() => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-OIDC-ACCESS');
  });

  it('rate survives round-trip as a string (incl. high precision "1300.12345678")', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(jsonResponse(FX_RATES_ENVELOPE)),
    );
    const result = await getFxRates();
    const r = result.rates[0];
    expect(r.rate).toBe('1300.12345678');
    expect(typeof r.rate).toBe('string');
    // ageSeconds IS a number (duration, not money).
    expect(r.ageSeconds).toBe(60);
    expect(typeof r.ageSeconds).toBe('number');
    expect(r.stale).toBe(false);
    expect(r.baseCurrency).toBe('KRW');
    expect(r.foreignCurrency).toBe('USD');
    expect(result.feedEnabled).toBe(true);
  });

  it('a very-high-precision rate (8 decimal places) is preserved verbatim', async () => {
    const envelope = {
      data: {
        feedEnabled: true,
        rates: [
          {
            baseCurrency: 'USD',
            foreignCurrency: 'JPY',
            rate: '157.98765432',
            asOf: '2026-06-15T00:00:00Z',
            source: 'FIXER',
            fetchedAt: '2026-06-15T00:01:00Z',
            ageSeconds: 120,
            stale: false,
          },
        ],
      },
      meta: { timestamp: '2026-06-15T00:01:00Z' },
    };
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(jsonResponse(envelope)),
    );
    const result = await getFxRates();
    expect(result.rates[0].rate).toBe('157.98765432');
    expect(typeof result.rates[0].rate).toBe('string');
  });
});

// ===========================================================================
// 5. Empty cache is a normal 200 — NOT a 404 / error.
// ===========================================================================

describe('fx-rates api — empty cache (200, NOT 404)', () => {
  beforeEach(() => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-OIDC-ACCESS');
  });

  it('an empty cache parses to rates: [], feedEnabled preserved (no throw, no error)', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(jsonResponse(EMPTY_ENVELOPE)),
    );
    const result = await getFxRates();
    expect(result.rates).toEqual([]);
    expect(result.feedEnabled).toBe(true);
  });
});

// ===========================================================================
// 6. Error mapping — 400 / 403 / 503 / timeout / 429.
// ===========================================================================

describe('fx-rates api — error mapping (400 / 403 / 503 / timeout / 429)', () => {
  beforeEach(() => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-OIDC-ACCESS');
  });

  it('400 VALIDATION_ERROR → ApiError(400) inline actionable', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(ledgerError('VALIDATION_ERROR', 400, 'bad request')),
    );
    const err = await getFxRates().catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(400);
    expect(err.code).toBe('VALIDATION_ERROR');
  });

  it('403 TENANT_FORBIDDEN → ApiError(403) inline (not scoped)', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(ledgerError('TENANT_FORBIDDEN', 403)),
    );
    const err = await getFxRates().catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(403);
    expect(err.code).toBe('TENANT_FORBIDDEN');
  });

  it('503 → LedgerUnavailableError (section degrade only)', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(ledgerError('SERVICE_UNAVAILABLE', 503)),
    );
    const err = await getFxRates().catch((e) => e);
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
    const err = await getFxRates().catch((e) => e);
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

    const err = await getFxRates().catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(429);
    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(n).toBe(1);
  });
});

// ===========================================================================
// 7. F7 — sanitised logPath carries no token.
// ===========================================================================

describe('fx-rates api — F7 (sanitised logPath, no token logged)', () => {
  it('the log output carries NO token', async () => {
    cookieJar.set(ACCESS_COOKIE, 'SUPER-SECRET-ACCESS-TOKEN-12345');
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(jsonResponse(FX_RATES_ENVELOPE)),
    );
    const logSpy = vi.spyOn(console, 'log').mockImplementation(() => {});
    const infoSpy = vi.spyOn(console, 'info').mockImplementation(() => {});
    const warnSpy = vi.spyOn(console, 'warn').mockImplementation(() => {});
    const errorSpy = vi.spyOn(console, 'error').mockImplementation(() => {});

    await getFxRates();

    const all = [
      ...logSpy.mock.calls,
      ...infoSpy.mock.calls,
      ...warnSpy.mock.calls,
      ...errorSpy.mock.calls,
    ]
      .map((args) => args.map(String).join(' '))
      .join('\n');

    expect(all).not.toContain('SUPER-SECRET-ACCESS-TOKEN-12345');
  });
});

// ===========================================================================
// 8. Module exports getFxRates alongside prior reads.
// ===========================================================================

describe('fx-rates api — module exports (reads only, getFxRates added)', () => {
  it('the module exports getFxRates + all prior reads + the single resolve', async () => {
    const mod = await import('@/features/ledger-ops/api/ledger-api');
    const keys = Object.keys(mod);
    expect(keys).toContain('getTrialBalance');
    expect(keys).toContain('getAccountBalance');
    expect(keys).toContain('getStatement');
    expect(keys).toContain('resolveDiscrepancy');
    expect(keys).toContain('getPositionLots');
    // TASK-PC-FE-092 — new FX rates read.
    expect(keys).toContain('getFxRates');
  });
});
