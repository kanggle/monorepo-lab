import { describe, it, expect, vi, beforeEach } from 'vitest';

/**
 * `features/ledger-ops/api/ledger-api.ts` — FX 환율 history 드릴 read
 * (TASK-PC-FE-104 — § 2.4.7.1: `getFxRateHistory`, consuming FIN-BE-040
 * `GET /api/finance/ledger/fx-rates/{foreignCurrency}/history`).
 *
 * Core assertions (mirrors ledger-fx-rates-api.test.ts):
 *   - domain-facing IAM OIDC token (`getDomainFacingToken()`) ONLY — the
 *     operator token (`getOperatorToken()`) is ABSENT;
 *   - GET only — NO body, NO Idempotency-Key, NO X-Operator-Reason,
 *     NO X-Tenant-Id;
 *   - upstream URL = `.../fx-rates/{enc(foreign)}/history[?limit=N]`;
 *   - F5 round-trip: `rate` survives as a bit-exact decimal string — NEVER
 *     Number-coerced;
 *   - empty history (quotes: []) is a 200 success (NOT a 404 / error);
 *   - 400 / 403 → ApiError; 503 / timeout → LedgerUnavailableError;
 *   - a stray 429 → plain ApiError (no retry, NO Retry-After branch);
 *   - sanitised `logPath` carries no token (F7);
 *   - module exports `getFxRateHistory`.
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
import { getFxRateHistory } from '@/features/ledger-ops/api/ledger-api';
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
    JSON.stringify({ code, message, timestamp: '2026-06-16T00:00:00.000Z' }),
    { status, headers: { 'Content-Type': 'application/json' } },
  );
}

const HISTORY_ENVELOPE = {
  data: {
    base: 'KRW',
    foreign: 'USD',
    quotes: [
      {
        // High-precision decimal — F5 must survive as a string.
        rate: '1300.12345678',
        asOf: '2026-06-15T07:00:00Z',
        fetchedAt: '2026-06-15T07:00:05Z',
        source: 'stub',
      },
      {
        rate: '1299.50000000',
        asOf: '2026-06-15T06:00:00Z',
        fetchedAt: '2026-06-15T06:00:05Z',
        source: 'stub',
      },
    ],
  },
  meta: { timestamp: '2026-06-15T07:00:05Z' },
};

const EMPTY_ENVELOPE = {
  data: { base: 'KRW', foreign: 'XXX', quotes: [] },
  meta: { timestamp: '2026-06-16T00:00:00Z' },
};

beforeEach(() => {
  cookieJar.clear();
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

// ===========================================================================
// 1. Per-domain credential — domain-facing IAM OIDC token, NEVER operator.
// ===========================================================================

describe('fx-history api — per-domain credential (getFxRateHistory)', () => {
  it('uses the IAM OIDC ACCESS cookie (NOT the operator token)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-OIDC-ACCESS-required-by-ledger');
    cookieJar.set(OPERATOR_COOKIE, 'OPERATOR-TOKEN-must-not-be-used');
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(HISTORY_ENVELOPE));
    vi.stubGlobal('fetch', fetchMock);

    await getFxRateHistory('USD', 10);

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
      vi.fn().mockResolvedValue(jsonResponse(HISTORY_ENVELOPE)),
    );

    await getFxRateHistory('USD', 10);

    expect(getDomainFacingSpy).toHaveBeenCalled();
    expect(getOperatorSpy).not.toHaveBeenCalled();
  });
});

// ===========================================================================
// 2. STRICTLY GET — no mutation artifacts.
// ===========================================================================

describe('fx-history api — STRICTLY GET (no Idempotency-Key / X-Operator-Reason / X-Tenant-Id / body)', () => {
  beforeEach(() => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-OIDC-ACCESS');
  });

  it('GET only, NO Idempotency-Key, NO X-Operator-Reason, NO X-Tenant-Id, NO body, NO Content-Type', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(HISTORY_ENVELOPE));
    vi.stubGlobal('fetch', fetchMock);

    await getFxRateHistory('USD', 10);

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
// 3. Correct upstream URL (currency path param + optional limit query).
// ===========================================================================

describe('fx-history api — upstream URL (per-pair + limit)', () => {
  beforeEach(() => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-OIDC-ACCESS');
  });

  it('encodes the foreign currency on the path + appends ?limit when provided', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(HISTORY_ENVELOPE));
    vi.stubGlobal('fetch', fetchMock);

    await getFxRateHistory('USD', 25);

    const url = String(fetchMock.mock.calls[0][0]);
    expect(url).toBe(
      'http://finance.local/api/finance/ledger/fx-rates/USD/history?limit=25',
    );
  });

  it('omits the ?limit query entirely when no limit is supplied', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(HISTORY_ENVELOPE));
    vi.stubGlobal('fetch', fetchMock);

    await getFxRateHistory('JPY');

    const url = String(fetchMock.mock.calls[0][0]);
    expect(url).toBe(
      'http://finance.local/api/finance/ledger/fx-rates/JPY/history',
    );
    expect(url).not.toContain('limit');
  });
});

// ===========================================================================
// 4. F5 — rate is a precision-exact decimal string, NEVER Number-coerced.
// ===========================================================================

describe('fx-history api — F5 rate invariant (bit-exact decimal string)', () => {
  beforeEach(() => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-OIDC-ACCESS');
  });

  it('rate survives round-trip as a string (incl. high precision "1300.12345678")', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(jsonResponse(HISTORY_ENVELOPE)),
    );
    const result = await getFxRateHistory('USD', 10);
    expect(result.base).toBe('KRW');
    expect(result.foreign).toBe('USD');
    expect(result.quotes).toHaveLength(2);
    const q0 = result.quotes[0];
    expect(q0.rate).toBe('1300.12345678');
    expect(typeof q0.rate).toBe('string');
    expect(q0.source).toBe('stub');
    expect(q0.asOf).toBe('2026-06-15T07:00:00Z');
    expect(q0.fetchedAt).toBe('2026-06-15T07:00:05Z');
  });

  it('a trailing-zero rate "1299.50000000" is preserved verbatim', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(jsonResponse(HISTORY_ENVELOPE)),
    );
    const result = await getFxRateHistory('USD', 10);
    expect(result.quotes[1].rate).toBe('1299.50000000');
    expect(typeof result.quotes[1].rate).toBe('string');
  });
});

// ===========================================================================
// 5. Empty history is a normal 200 — NOT a 404 / error.
// ===========================================================================

describe('fx-history api — empty history (200, NOT 404)', () => {
  beforeEach(() => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-OIDC-ACCESS');
  });

  it('an unknown / never-polled foreign code parses to quotes: [] (no throw)', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(jsonResponse(EMPTY_ENVELOPE)),
    );
    const result = await getFxRateHistory('XXX', 10);
    expect(result.quotes).toEqual([]);
    expect(result.base).toBe('KRW');
    expect(result.foreign).toBe('XXX');
  });
});

// ===========================================================================
// 6. Error mapping — 400 / 403 / 503 / timeout / 429.
// ===========================================================================

describe('fx-history api — error mapping (400 / 403 / 503 / timeout / 429)', () => {
  beforeEach(() => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-OIDC-ACCESS');
  });

  it('400 VALIDATION_ERROR → ApiError(400) inline actionable', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(ledgerError('VALIDATION_ERROR', 400, 'bad request')),
    );
    const err = await getFxRateHistory('USD', 10).catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(400);
    expect(err.code).toBe('VALIDATION_ERROR');
  });

  it('403 TENANT_FORBIDDEN → ApiError(403) inline (not scoped)', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(ledgerError('TENANT_FORBIDDEN', 403)),
    );
    const err = await getFxRateHistory('USD', 10).catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(403);
    expect(err.code).toBe('TENANT_FORBIDDEN');
  });

  it('503 → LedgerUnavailableError (section degrade only)', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(ledgerError('SERVICE_UNAVAILABLE', 503)),
    );
    const err = await getFxRateHistory('USD', 10).catch((e) => e);
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
    const err = await getFxRateHistory('USD', 10).catch((e) => e);
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
            headers: { 'Content-Type': 'application/json', 'Retry-After': '1' },
          },
        ),
      );
    });
    vi.stubGlobal('fetch', fetchMock);

    const err = await getFxRateHistory('USD', 10).catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(429);
    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(n).toBe(1);
  });
});

// ===========================================================================
// 7. F7 — sanitised logPath carries no token.
// ===========================================================================

describe('fx-history api — F7 (sanitised logPath, no token logged)', () => {
  it('the log output carries NO token', async () => {
    cookieJar.set(ACCESS_COOKIE, 'SUPER-SECRET-ACCESS-TOKEN-12345');
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(jsonResponse(HISTORY_ENVELOPE)),
    );
    const logSpy = vi.spyOn(console, 'log').mockImplementation(() => {});
    const infoSpy = vi.spyOn(console, 'info').mockImplementation(() => {});
    const warnSpy = vi.spyOn(console, 'warn').mockImplementation(() => {});
    const errorSpy = vi.spyOn(console, 'error').mockImplementation(() => {});

    await getFxRateHistory('USD', 10);

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
// 8. Module exports getFxRateHistory alongside prior reads.
// ===========================================================================

describe('fx-history api — module exports (reads only, getFxRateHistory added)', () => {
  it('the module exports getFxRateHistory + the prior FX reads', async () => {
    const mod = await import('@/features/ledger-ops/api/ledger-api');
    const keys = Object.keys(mod);
    expect(keys).toContain('getFxRates');
    expect(keys).toContain('getPositionLots');
    // TASK-PC-FE-104 — new FX history read.
    expect(keys).toContain('getFxRateHistory');
  });
});
