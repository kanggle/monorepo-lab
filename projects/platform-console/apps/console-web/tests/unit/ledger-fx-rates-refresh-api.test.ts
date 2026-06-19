import { describe, it, expect, vi, beforeEach } from 'vitest';

/**
 * `features/ledger-ops/api/ledger-fx-rates-api.ts` — `refreshFxRates()`
 * server API (TASK-MONO-300 Scope B) + `FxRatesRefreshResponseSchema`.
 *
 * Core assertions:
 *   - domain-facing IAM OIDC token (getDomainFacingToken()) — NEVER getOperatorToken();
 *   - POST, NO request body (unconditional refresh), NO Content-Type (no body),
 *     NO Idempotency-Key, NO X-Operator-Reason, NO X-Tenant-Id;
 *   - correct upstream URL: `LEDGER_BASE_URL/api/finance/ledger/fx-rates/refresh`;
 *   - feed-disabled → 200 no-op (`feedEnabled:false, refreshed:0`);
 *   - F5: `refreshed` is a plain integer count (NOT money — z.number() correct);
 *   - schema rejects non-integer / negative `refreshed`;
 *   - 401 / 403 / 503 / timeout error taxonomy;
 *   - F7: sanitised logPath, no token logged.
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
    CONSOLE_TOKEN_EXCHANGE_URL: 'http://iam.local/api/admin/auth/token-exchange',
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
import { refreshFxRates } from '@/features/ledger-ops/api/ledger-api';
import { FxRatesRefreshResponseSchema } from '@/features/ledger-ops/api/types';
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
    JSON.stringify({ code, message, timestamp: '2026-06-19T00:00:00.000Z' }),
    { status, headers: { 'Content-Type': 'application/json' } },
  );
}

// A normal producer success: 2 pairs upserted, feed enabled.
const REFRESH_ENVELOPE_OK = {
  data: { feedEnabled: true, refreshed: 2 },
  meta: { timestamp: '2026-06-19T11:00:00Z' },
};

// Feed-disabled producer 200 no-op.
const REFRESH_ENVELOPE_DISABLED = {
  data: { feedEnabled: false, refreshed: 0 },
  meta: { timestamp: '2026-06-19T11:00:00Z' },
};

beforeEach(() => {
  cookieJar.clear();
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

// ===========================================================================
// 1. Per-domain credential — domain-facing IAM OIDC token, NEVER operator.
// ===========================================================================

describe('refreshFxRates — per-domain credential (domain-facing token, NEVER operator)', () => {
  it('uses the IAM OIDC ACCESS cookie (NOT the operator token)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-OIDC-ACCESS-required-by-ledger');
    cookieJar.set(OPERATOR_COOKIE, 'OPERATOR-TOKEN-must-not-be-used');
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(REFRESH_ENVELOPE_OK));
    vi.stubGlobal('fetch', fetchMock);

    await refreshFxRates();

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
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse(REFRESH_ENVELOPE_OK)));

    await refreshFxRates();

    expect(getDomainFacingSpy).toHaveBeenCalled();
    expect(getOperatorSpy).not.toHaveBeenCalled();
  });
});

// ===========================================================================
// 2. POST, NO body, NO mutation artifacts.
// ===========================================================================

describe('refreshFxRates — POST, no body, no mutation artifacts', () => {
  beforeEach(() => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-OIDC-ACCESS');
  });

  it('sends a POST with NO body, NO Content-Type, NO Idempotency-Key, NO X-Operator-Reason, NO X-Tenant-Id', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(REFRESH_ENVELOPE_OK));
    vi.stubGlobal('fetch', fetchMock);

    await refreshFxRates();

    const [, init] = fetchMock.mock.calls[0];
    const h = (init as RequestInit).headers as Record<string, string>;
    expect((init as RequestInit).method).toBe('POST');
    // No body — the refresh is unconditional; Content-Type MUST be absent.
    expect((init as RequestInit).body).toBeUndefined();
    expect(h['Content-Type']).toBeUndefined();
    // The three absent headers (honest difference from mutations with a body).
    expect(h['Idempotency-Key']).toBeUndefined();
    expect(h['X-Operator-Reason']).toBeUndefined();
    expect(h['X-Tenant-Id']).toBeUndefined();
  });
});

// ===========================================================================
// 3. Correct upstream URL.
// ===========================================================================

describe('refreshFxRates — upstream URL', () => {
  beforeEach(() => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-OIDC-ACCESS');
  });

  it('calls the correct upstream path on LEDGER_BASE_URL', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(REFRESH_ENVELOPE_OK));
    vi.stubGlobal('fetch', fetchMock);

    await refreshFxRates();

    const url = String(fetchMock.mock.calls[0][0]);
    expect(url).toBe('http://finance.local/api/finance/ledger/fx-rates/refresh');
  });
});

// ===========================================================================
// 4. Feed-disabled 200 no-op (`feedEnabled:false, refreshed:0`).
// ===========================================================================

describe('refreshFxRates — feed-disabled 200 no-op', () => {
  beforeEach(() => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-OIDC-ACCESS');
  });

  it('feed-disabled → 200 {feedEnabled:false, refreshed:0} (NOT an error)', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse(REFRESH_ENVELOPE_DISABLED)));

    const result = await refreshFxRates();

    expect(result.feedEnabled).toBe(false);
    expect(result.refreshed).toBe(0);
    expect(typeof result.refreshed).toBe('number');
  });

  it('feed-enabled with 2 upserts → {feedEnabled:true, refreshed:2}', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse(REFRESH_ENVELOPE_OK)));

    const result = await refreshFxRates();

    expect(result.feedEnabled).toBe(true);
    expect(result.refreshed).toBe(2);
    expect(typeof result.refreshed).toBe('number');
  });
});

// ===========================================================================
// 5. FxRatesRefreshResponseSchema — F5: `refreshed` is a plain count (number).
// ===========================================================================

describe('FxRatesRefreshResponseSchema — shape validation', () => {
  it('accepts valid {feedEnabled, refreshed} — refreshed is a non-negative integer', () => {
    const result = FxRatesRefreshResponseSchema.safeParse({ feedEnabled: true, refreshed: 3 });
    expect(result.success).toBe(true);
    if (result.success) {
      expect(result.data.refreshed).toBe(3);
      expect(typeof result.data.refreshed).toBe('number');
    }
  });

  it('rejects a string refreshed (F5 — count must be a number, not a string)', () => {
    const result = FxRatesRefreshResponseSchema.safeParse({
      feedEnabled: true,
      refreshed: '3',
    });
    expect(result.success).toBe(false);
  });

  it('rejects a negative refreshed (non-negative invariant)', () => {
    const result = FxRatesRefreshResponseSchema.safeParse({
      feedEnabled: true,
      refreshed: -1,
    });
    expect(result.success).toBe(false);
  });

  it('is passthrough — unknown fields do not throw', () => {
    const result = FxRatesRefreshResponseSchema.safeParse({
      feedEnabled: true,
      refreshed: 0,
      unknownField: 'future-producer-field',
    });
    expect(result.success).toBe(true);
  });
});

// ===========================================================================
// 6. Error mapping — 401 / 403 / 503 / timeout.
// ===========================================================================

describe('refreshFxRates — error mapping (401 / 403 / 503 / timeout)', () => {
  beforeEach(() => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-OIDC-ACCESS');
  });

  it('401 → ApiError(401) whole-session re-login', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(ledgerError('UNAUTHORIZED', 401)));
    const err = await refreshFxRates().catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(401);
  });

  it('403 TENANT_FORBIDDEN → ApiError(403) inline (token not finance-scoped)', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(ledgerError('TENANT_FORBIDDEN', 403)));
    const err = await refreshFxRates().catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(403);
    expect(err.code).toBe('TENANT_FORBIDDEN');
  });

  it('503 → LedgerUnavailableError (ONLY ledger section degrades)', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(ledgerError('SERVICE_UNAVAILABLE', 503)));
    const err = await refreshFxRates().catch((e) => e);
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
    const err = await refreshFxRates().catch((e) => e);
    expect(err).toBeInstanceOf(LedgerUnavailableError);
    expect(err.reason).toBe('timeout');
  });

  it('no IAM session → 401 with NO upstream fetch (whole-session re-login signal)', async () => {
    cookieJar.clear(); // no access cookie
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    const err = await refreshFxRates().catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(401);
    expect(fetchMock).not.toHaveBeenCalled();
  });
});

// ===========================================================================
// 7. F7 — sanitised logPath carries no token.
// ===========================================================================

describe('refreshFxRates — F7 (sanitised logPath, no token logged)', () => {
  it('the log output carries NO token', async () => {
    cookieJar.set(ACCESS_COOKIE, 'SUPER-SECRET-ACCESS-TOKEN-99999');
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse(REFRESH_ENVELOPE_OK)));
    const logSpy = vi.spyOn(console, 'log').mockImplementation(() => {});
    const infoSpy = vi.spyOn(console, 'info').mockImplementation(() => {});
    const warnSpy = vi.spyOn(console, 'warn').mockImplementation(() => {});
    const errorSpy = vi.spyOn(console, 'error').mockImplementation(() => {});

    await refreshFxRates();

    const all = [
      ...logSpy.mock.calls,
      ...infoSpy.mock.calls,
      ...warnSpy.mock.calls,
      ...errorSpy.mock.calls,
    ]
      .map((args) => args.map(String).join(' '))
      .join('\n');

    expect(all).not.toContain('SUPER-SECRET-ACCESS-TOKEN-99999');
  });
});
