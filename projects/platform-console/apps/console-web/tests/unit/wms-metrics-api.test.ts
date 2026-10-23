import { describe, it, expect, vi, beforeEach } from 'vitest';

/**
 * TASK-MONO-808 (ADR-MONO-084 P3) — `getWmsMetrics(period)`: the 개요 «실적»
 * strip read, `GET /dashboard/metrics?period=week|month` on the wms
 * admin-service. Same hardened client as every other wms read (IAM OIDC bearer,
 * nested error envelope), parsed by the shared performance-metrics schema.
 * Called WITHOUT `warehouseId` by the overview (warehouse-global).
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
    LOG_LEVEL: 'info' as const,
    NEXT_PUBLIC_APP_URL: 'http://console.local',
  },
}));
vi.mock('@/shared/config/env', () => ({
  clientEnv: { NEXT_PUBLIC_APP_URL: ENV.NEXT_PUBLIC_APP_URL },
  getServerEnv: () => ENV,
}));

import { getWmsMetrics } from '@/features/wms-ops/api/wms-api';
import { performanceMetricsCell } from '@/shared/api/performance-metrics';
import { ApiError, WmsUnavailableError } from '@/shared/api/errors';
import { ACCESS_COOKIE } from '@/shared/lib/session';

const BODY = {
  period: 'week',
  timezone: 'Asia/Seoul',
  current: { from: '2026-10-19T00:00:00+09:00', to: '2026-10-24T14:05:00+09:00' },
  previous: { from: '2026-10-12T00:00:00+09:00', to: '2026-10-17T14:05:00+09:00' },
  metrics: [
    { key: 'qtyReceived', unit: 'count', currency: null, current: '1200', previous: '900' },
    { key: 'qtyShipped', unit: 'count', currency: null, current: '800', previous: '850' },
    { key: 'shipmentCount', unit: 'count', currency: null, current: '40', previous: '41' },
  ],
  series: [
    { date: '2026-10-19', values: { qtyReceived: '200', qtyShipped: '100', shipmentCount: '5' } },
  ],
};

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

function wmsError(code: string, status: number) {
  return new Response(
    JSON.stringify({ error: { code, message: 'err', timestamp: '2026-10-24T00:00:00Z' } }),
    { status, headers: { 'Content-Type': 'application/json' } },
  );
}

beforeEach(() => {
  cookieJar.clear();
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
  cookieJar.set(ACCESS_COOKIE, 'GAP-OIDC-ACCESS');
});

describe('getWmsMetrics (TASK-MONO-808)', () => {
  it('GETs /dashboard/metrics?period= with no warehouseId and returns the parsed body', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(BODY));
    vi.stubGlobal('fetch', fetchMock);

    const res = await getWmsMetrics('week');

    const [url, init] = fetchMock.mock.calls[0];
    expect(String(url)).toBe('http://wms.local/api/v1/admin/dashboard/metrics?period=week');
    expect((init as RequestInit).method).toBe('GET');
    expect(res.metrics.map((m) => m.key)).toEqual(['qtyReceived', 'qtyShipped', 'shipmentCount']);
    expect(res.metrics.every((m) => m.unit === 'count' && m.currency === null)).toBe(true);
  });

  it('passes warehouseId only when given', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ ...BODY, period: 'month' }));
    vi.stubGlobal('fetch', fetchMock);
    await getWmsMetrics('month', 'wh-1');
    expect(String(fetchMock.mock.calls[0][0])).toBe(
      'http://wms.local/api/v1/admin/dashboard/metrics?period=month&warehouseId=wh-1',
    );
  });

  it('a count with no currency field reads as null (producer drops nulls)', async () => {
    const metrics = BODY.metrics.map(({ currency: _c, ...rest }) => {
      void _c;
      return rest;
    });
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse({ ...BODY, metrics })));
    const res = await getWmsMetrics('week');
    expect(res.metrics.every((m) => m.currency === null)).toBe(true);
  });

  it('400 VALIDATION_ERROR → ApiError(400); the cell degrades', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(wmsError('VALIDATION_ERROR', 400)));
    const err = await getWmsMetrics('week').catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(400);
    expect(await performanceMetricsCell(getWmsMetrics('week'))).toEqual({
      status: 'degraded',
      value: null,
    });
  });

  it('a number where an integer string belongs (F5) → not a crash; the cell degrades', async () => {
    const bad = { ...BODY, metrics: [{ ...BODY.metrics[0], current: 1200 }] };
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse(bad)));
    const err = await getWmsMetrics('week').catch((e) => e);
    expect(err).toBeInstanceOf(WmsUnavailableError);
    expect((await performanceMetricsCell(getWmsMetrics('week'))).status).toBe('degraded');
  });

  it('403 → forbidden cell; 401 → rethrown from the cell (whole-session re-login)', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(wmsError('FORBIDDEN', 403)));
    expect((await performanceMetricsCell(getWmsMetrics('week'))).status).toBe('forbidden');

    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(wmsError('UNAUTHORIZED', 401)));
    const err = await performanceMetricsCell(getWmsMetrics('week')).catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(401);
  });
});
