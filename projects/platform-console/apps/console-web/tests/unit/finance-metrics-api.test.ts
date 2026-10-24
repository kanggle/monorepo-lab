import { describe, it, expect, vi, beforeEach } from 'vitest';

/**
 * TASK-MONO-812 (ADR-MONO-084 P7) — `getFinanceMetrics(period)`: the Finance
 * 개요 «실적» strip read, `GET /api/finance/accounts/metrics?period=week|month`
 * on finance account-service. Same hardened call site as the overview's account
 * reads (`callFinance`): the domain-facing IAM OIDC token (never the operator
 * token), NO `X-Tenant-Id` (finance resolves the tenant from the token),
 * read-only, parsed by the shared schema. Every failure class is asserted
 * through the shared `performanceMetricsCell`, i.e. as the strip will see it.
 */

const cookieJar = new Map<string, string>();
vi.mock('next/headers', () => ({
  cookies: async () => ({
    get: (n: string) => (cookieJar.has(n) ? { value: cookieJar.get(n)! } : undefined),
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
    FINANCE_BASE_URL: 'http://finance.local',
    FINANCE_TIMEOUT_MS: 50,
    LOG_LEVEL: 'info' as const,
    NEXT_PUBLIC_APP_URL: 'http://console.local',
  },
}));
vi.mock('@/shared/config/env', () => ({
  clientEnv: { NEXT_PUBLIC_APP_URL: ENV.NEXT_PUBLIC_APP_URL },
  getServerEnv: () => ENV,
}));

import * as sessionModule from '@/shared/lib/session';
import { getFinanceMetrics } from '@/features/finance-overview/api/finance-metrics-api';
import { performanceMetricsCell } from '@/shared/api/performance-metrics';
import { ApiError } from '@/shared/api/errors';
import { ACCESS_COOKIE, OPERATOR_COOKIE } from '@/shared/lib/session';

const BODY = {
  period: 'week',
  timezone: 'Asia/Seoul',
  current: { from: '2026-10-19T00:00:00+09:00', to: '2026-10-24T14:05:00+09:00' },
  previous: { from: '2026-10-12T00:00:00+09:00', to: '2026-10-17T14:05:00+09:00' },
  metrics: [
    { key: 'topupCount', unit: 'count', currency: null, current: '2', previous: '1' },
    { key: 'topupAmount:KRW', unit: 'money', currency: 'KRW', current: '500000', previous: '1000000' },
    { key: 'topupAmount:USD', unit: 'money', currency: 'USD', current: '2550', previous: '0' },
    { key: 'transferCount', unit: 'count', currency: null, current: '1', previous: '0' },
    { key: 'transferAmount:KRW', unit: 'money', currency: 'KRW', current: '50000', previous: '0' },
    { key: 'transferAmount:USD', unit: 'money', currency: 'USD', current: '0', previous: '0' },
    { key: 'captureCount', unit: 'count', currency: null, current: '1', previous: '0' },
    { key: 'captureAmount:KRW', unit: 'money', currency: 'KRW', current: '12000', previous: '0' },
    { key: 'captureAmount:USD', unit: 'money', currency: 'USD', current: '0', previous: '0' },
  ],
  series: [
    {
      date: '2026-10-19',
      values: {
        topupCount: '2',
        'topupAmount:KRW': '500000',
        'topupAmount:USD': '2550',
        transferCount: '1',
        'transferAmount:KRW': '50000',
        'transferAmount:USD': '0',
        captureCount: '1',
        'captureAmount:KRW': '12000',
        'captureAmount:USD': '0',
      },
    },
  ],
};

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}
function financeError(code: string, status: number) {
  return jsonResponse({ code, message: 'e', timestamp: 't' }, status);
}

beforeEach(() => {
  cookieJar.clear();
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
  cookieJar.set(ACCESS_COOKIE, 'GAP-OIDC-ACCESS');
  cookieJar.set(OPERATOR_COOKIE, 'OPERATOR-TOKEN-must-not-be-used');
});

describe('getFinanceMetrics (TASK-MONO-812)', () => {
  it('GETs /api/finance/accounts/metrics?period= with the IAM OIDC bearer, no X-Tenant-Id, read-only', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(BODY));
    vi.stubGlobal('fetch', fetchMock);
    const operatorSpy = vi.spyOn(sessionModule, 'getOperatorToken');

    const res = await getFinanceMetrics('week');

    const [url, init] = fetchMock.mock.calls[0];
    expect(String(url)).toBe('http://finance.local/api/finance/accounts/metrics?period=week');
    expect((init as RequestInit).method).toBe('GET');
    expect((init as RequestInit).body).toBeUndefined();
    const headers = (init as RequestInit).headers as Record<string, string>;
    expect(headers.Authorization).toBe('Bearer GAP-OIDC-ACCESS');
    expect(headers['X-Tenant-Id']).toBeUndefined();
    expect(headers['Idempotency-Key']).toBeUndefined();
    expect(headers['X-Operator-Reason']).toBeUndefined();
    expect(operatorSpy).not.toHaveBeenCalled();
    expect(res.metrics.map((m) => m.key)).toEqual(BODY.metrics.map((m) => m.key));
    // AC-2 — each money metric is one currency; KRW and USD are separate keys.
    expect(res.metrics.find((m) => m.key === 'topupAmount:USD')?.currency).toBe('USD');
    expect(res.metrics.find((m) => m.key === 'topupAmount:KRW')?.current).toBe('500000');
  });

  it('sends the month period', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ ...BODY, period: 'month' }));
    vi.stubGlobal('fetch', fetchMock);
    await getFinanceMetrics('month');
    const url = new URL(String(fetchMock.mock.calls[0][0]));
    expect(url.pathname).toBe('/api/finance/accounts/metrics');
    expect(url.searchParams.get('period')).toBe('month');
    expect([...url.searchParams.keys()]).toEqual(['period']);
  });

  it('a body that does not parse (a number where an integer string belongs — F5) → the strip degrades', async () => {
    const bad = { ...BODY, metrics: [{ ...BODY.metrics[1], current: 500000 }] };
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse(bad)));
    expect((await performanceMetricsCell(getFinanceMetrics('week'))).status).toBe('degraded');
  });

  it('an enveloped body ({data, meta}) is NOT the contract shape → degraded, never a partial parse', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse({ data: BODY, meta: {} })));
    expect((await performanceMetricsCell(getFinanceMetrics('week'))).status).toBe('degraded');
  });

  it('403 PERMISSION_DENIED / TENANT_FORBIDDEN → forbidden', async () => {
    for (const code of ['PERMISSION_DENIED', 'TENANT_FORBIDDEN']) {
      vi.stubGlobal('fetch', vi.fn().mockResolvedValue(financeError(code, 403)));
      expect((await performanceMetricsCell(getFinanceMetrics('week'))).status, code).toBe('forbidden');
    }
  });

  it('401 → rethrown (whole-session re-login), never a degraded strip', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(financeError('UNAUTHORIZED', 401)));
    const err = await performanceMetricsCell(getFinanceMetrics('week')).catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(401);
  });

  it('400 (bad period) / 500 / 503 / network failure → degraded', async () => {
    for (const res of [
      financeError('VALIDATION_ERROR', 400),
      financeError('INTERNAL_ERROR', 500),
      financeError('SERVICE_UNAVAILABLE', 503),
    ]) {
      vi.stubGlobal('fetch', vi.fn().mockResolvedValue(res));
      expect((await performanceMetricsCell(getFinanceMetrics('week'))).status).toBe('degraded');
    }
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('fetch failed')));
    expect((await performanceMetricsCell(getFinanceMetrics('week'))).status).toBe('degraded');
  });
});
