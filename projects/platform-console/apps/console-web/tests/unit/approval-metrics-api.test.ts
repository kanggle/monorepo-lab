import { describe, it, expect, vi, beforeEach } from 'vitest';

/**
 * TASK-MONO-809 (ADR-MONO-084 P4) — `getApprovalMetrics(period)`: the 개요
 * «실적» strip read, `GET /api/erp/approval/metrics?period=week|month` on the
 * erp approval-service. Same hardened client as every other approval read
 * (IAM OIDC bearer, flat error envelope), parsed by the shared schema.
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
    ERP_BASE_URL: 'http://erp.local',
    ERP_TIMEOUT_MS: 50,
    LOG_LEVEL: 'info' as const,
    NEXT_PUBLIC_APP_URL: 'http://console.local',
  },
}));
vi.mock('@/shared/config/env', () => ({
  clientEnv: { NEXT_PUBLIC_APP_URL: ENV.NEXT_PUBLIC_APP_URL },
  getServerEnv: () => ENV,
}));

import { getApprovalMetrics } from '@/features/erp-ops/api/approval-api';
import { performanceMetricsCell } from '@/shared/api/performance-metrics';
import { ApiError, ErpUnavailableError } from '@/shared/api/errors';
import { ACCESS_COOKIE } from '@/shared/lib/session';

const BODY = {
  period: 'week',
  timezone: 'Asia/Seoul',
  current: { from: '2026-10-19T00:00:00+09:00', to: '2026-10-24T14:05:00+09:00' },
  previous: { from: '2026-10-12T00:00:00+09:00', to: '2026-10-17T14:05:00+09:00' },
  metrics: [
    { key: 'submitted', unit: 'count', currency: null, current: '12', previous: '9' },
    { key: 'approved', unit: 'count', currency: null, current: '8', previous: '8' },
    { key: 'rejected', unit: 'count', currency: null, current: '1', previous: '2' },
  ],
  series: [{ date: '2026-10-19', values: { submitted: '2', approved: '1', rejected: '0' } }],
};

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}
function erpError(code: string, status: number) {
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

describe('getApprovalMetrics (TASK-MONO-809)', () => {
  it('GETs /api/erp/approval/metrics?period= with the IAM bearer and returns the parsed body', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(BODY));
    vi.stubGlobal('fetch', fetchMock);

    const res = await getApprovalMetrics('week');

    const [url, init] = fetchMock.mock.calls[0];
    expect(String(url)).toBe('http://erp.local/api/erp/approval/metrics?period=week');
    expect((init as RequestInit).method).toBe('GET');
    const headers = (init as RequestInit).headers as Record<string, string>;
    expect(headers.Authorization).toBe('Bearer GAP-OIDC-ACCESS');
    expect(headers['X-Tenant-Id']).toBeUndefined();
    expect(res.metrics.map((m) => m.key)).toEqual(['submitted', 'approved', 'rejected']);
    expect(res.metrics.every((m) => m.unit === 'count' && m.currency === null)).toBe(true);
  });

  it('sends the month period', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ ...BODY, period: 'month' }));
    vi.stubGlobal('fetch', fetchMock);
    await getApprovalMetrics('month');
    expect(String(fetchMock.mock.calls[0][0])).toBe(
      'http://erp.local/api/erp/approval/metrics?period=month',
    );
  });

  it('a count with no currency field reads as null (producer drops nulls)', async () => {
    const metrics = BODY.metrics.map(({ currency: _c, ...rest }) => {
      void _c;
      return rest;
    });
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse({ ...BODY, metrics })));
    const res = await getApprovalMetrics('week');
    expect(res.metrics.every((m) => m.currency === null)).toBe(true);
  });

  it('400 VALIDATION_ERROR -> ApiError(400); the cell degrades', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(erpError('VALIDATION_ERROR', 400)));
    const err = await getApprovalMetrics('week').catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(400);
    expect(await performanceMetricsCell(getApprovalMetrics('week'))).toEqual({
      status: 'degraded',
      value: null,
    });
  });

  it('a number where an integer string belongs (F5) -> not a crash; the cell degrades', async () => {
    const bad = { ...BODY, metrics: [{ ...BODY.metrics[0], current: 12 }] };
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse(bad)));
    const err = await getApprovalMetrics('week').catch((e) => e);
    expect(err).toBeInstanceOf(ErpUnavailableError);
    expect((await performanceMetricsCell(getApprovalMetrics('week'))).status).toBe('degraded');
  });

  it('403 -> forbidden cell; 401 -> rethrown from the cell (whole-session re-login)', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(erpError('FORBIDDEN', 403)));
    expect((await performanceMetricsCell(getApprovalMetrics('week'))).status).toBe('forbidden');

    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(erpError('UNAUTHORIZED', 401)));
    const err = await performanceMetricsCell(getApprovalMetrics('week')).catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(401);
  });
});
