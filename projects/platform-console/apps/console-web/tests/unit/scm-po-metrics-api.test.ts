import { describe, it, expect, vi, beforeEach } from 'vitest';

/**
 * TASK-MONO-810 (ADR-MONO-084 P5) — `getPoMetrics(period)`: the scm 개요 «실적»
 * strip read, `GET /api/v1/procurement/po/metrics?period=week|month` on the scm
 * gateway. Same hardened client as every other scm read (IAM OIDC bearer, no
 * X-Tenant-Id, flat error envelope), parsed by the shared schema. Per-currency
 * money keys (`poAmount:<CUR>`) parse as separate metrics.
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
    SCM_GATEWAY_BASE_URL: 'http://scm.local',
    SCM_TIMEOUT_MS: 50,
    LOG_LEVEL: 'info' as const,
    NEXT_PUBLIC_APP_URL: 'http://console.local',
  },
}));
vi.mock('@/shared/config/env', () => ({
  clientEnv: { NEXT_PUBLIC_APP_URL: ENV.NEXT_PUBLIC_APP_URL },
  getServerEnv: () => ENV,
}));

import { getPoMetrics } from '@/features/scm-ops/api/po-metrics-api';
import { performanceMetricsCell } from '@/shared/api/performance-metrics';
import { ApiError } from '@/shared/api/errors';
import { ACCESS_COOKIE } from '@/shared/lib/session';

const BODY = {
  period: 'week',
  timezone: 'Asia/Seoul',
  current: { from: '2026-10-19T00:00:00+09:00', to: '2026-10-24T14:05:00+09:00' },
  previous: { from: '2026-10-12T00:00:00+09:00', to: '2026-10-17T14:05:00+09:00' },
  metrics: [
    { key: 'poCount', unit: 'count', currency: null, current: '12', previous: '9' },
    { key: 'poAmount:KRW', unit: 'money', currency: 'KRW', current: '1250000', previous: '980000' },
    { key: 'poAmount:USD', unit: 'money', currency: 'USD', current: '123456', previous: '0' },
    { key: 'receivedCount', unit: 'count', currency: null, current: '4', previous: '2' },
  ],
  series: [
    {
      date: '2026-10-19',
      values: { poCount: '2', 'poAmount:KRW': '250000', 'poAmount:USD': '0', receivedCount: '1' },
    },
  ],
};

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}
function scmError(code: string, status: number) {
  return new Response(JSON.stringify({ code, message: 'e', timestamp: 't' }), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

beforeEach(() => {
  cookieJar.clear();
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
  cookieJar.set(ACCESS_COOKIE, 'GAP-OIDC-ACCESS');
});

describe('getPoMetrics (TASK-MONO-810)', () => {
  it('GETs /api/v1/procurement/po/metrics?period= with the IAM bearer and returns the parsed body', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(BODY));
    vi.stubGlobal('fetch', fetchMock);

    const res = await getPoMetrics('week');

    const [url, init] = fetchMock.mock.calls[0];
    expect(String(url)).toBe('http://scm.local/api/v1/procurement/po/metrics?period=week');
    expect((init as RequestInit).method).toBe('GET');
    const headers = (init as RequestInit).headers as Record<string, string>;
    expect(headers.Authorization).toBe('Bearer GAP-OIDC-ACCESS');
    expect(headers['X-Tenant-Id']).toBeUndefined();
    expect(res.metrics.map((m) => m.key)).toEqual([
      'poCount',
      'poAmount:KRW',
      'poAmount:USD',
      'receivedCount',
    ]);
    // One money metric per currency — each carries its own currency.
    expect(res.metrics.filter((m) => m.unit === 'money').map((m) => m.currency)).toEqual([
      'KRW',
      'USD',
    ]);
  });

  it('sends the month period', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ ...BODY, period: 'month' }));
    vi.stubGlobal('fetch', fetchMock);
    await getPoMetrics('month');
    expect(String(fetchMock.mock.calls[0][0])).toBe(
      'http://scm.local/api/v1/procurement/po/metrics?period=month',
    );
  });

  it('a `{ data, meta }`-enveloped body parses too (the procurement service envelope)', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(jsonResponse({ data: BODY, meta: { timestamp: 'x' } })),
    );
    const res = await getPoMetrics('week');
    expect(res.period).toBe('week');
    expect(res.metrics).toHaveLength(4);
  });

  it('a money metric without a currency → parse failure; the cell degrades', async () => {
    const bad = {
      ...BODY,
      metrics: [{ key: 'poAmount:KRW', unit: 'money', currency: null, current: '1', previous: '0' }],
    };
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse(bad)));
    expect((await performanceMetricsCell(getPoMetrics('week'))).status).toBe('degraded');
  });

  it('a number where an integer string belongs (F5) → the cell degrades, no crash', async () => {
    const bad = { ...BODY, metrics: [{ ...BODY.metrics[1], current: 1250000 }] };
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse(bad)));
    expect((await performanceMetricsCell(getPoMetrics('week'))).status).toBe('degraded');
  });

  it('400 → ApiError(400) → degraded; 403 → forbidden; 401 → rethrown (whole-session re-login)', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(scmError('VALIDATION_ERROR', 400)));
    const err = await getPoMetrics('week').catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(400);
    expect((await performanceMetricsCell(getPoMetrics('week'))).status).toBe('degraded');

    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(scmError('PERMISSION_DENIED', 403)));
    expect((await performanceMetricsCell(getPoMetrics('week'))).status).toBe('forbidden');

    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(scmError('UNAUTHORIZED', 401)));
    const e401 = await performanceMetricsCell(getPoMetrics('week')).catch((e) => e);
    expect(e401).toBeInstanceOf(ApiError);
    expect(e401.status).toBe(401);
  });

  it('503 → the cell degrades', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(scmError('SERVICE_UNAVAILABLE', 503)));
    expect((await performanceMetricsCell(getPoMetrics('week'))).status).toBe('degraded');
  });
});
