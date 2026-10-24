import { describe, it, expect, vi, beforeEach } from 'vitest';

/**
 * TASK-MONO-811 (ADR-MONO-084 P6) — `getIamMetrics(period, tenantId)`: the IAM
 * 개요 «실적» strip read, `GET /api/admin/metrics?period=week|month&tenantId=`
 * on IAM admin-service. Same hardened core as the audit read
 * (`callAdminGateway`): the EXCHANGED operator token (never the IAM OIDC
 * token), the active tenant as `X-Tenant-Id` AND as the `tenantId` query param
 * (admin-service scopes by the param), read-only headers, parsed by the shared
 * schema. Every failure class is asserted through the shared
 * `performanceMetricsCell`, i.e. as the strip will see it.
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
    LOG_LEVEL: 'info' as const,
    NEXT_PUBLIC_APP_URL: 'http://console.local',
  },
}));
vi.mock('@/shared/config/env', () => ({
  clientEnv: { NEXT_PUBLIC_APP_URL: ENV.NEXT_PUBLIC_APP_URL },
  getServerEnv: () => ENV,
}));

import { getIamMetrics } from '@/features/iam-overview/api/iam-metrics-api';
import { performanceMetricsCell } from '@/shared/api/performance-metrics';
import { ApiError } from '@/shared/api/errors';
import { ACCESS_COOKIE, OPERATOR_COOKIE, TENANT_COOKIE } from '@/shared/lib/session';

const BODY = {
  period: 'week',
  timezone: 'Asia/Seoul',
  current: { from: '2026-10-19T00:00:00+09:00', to: '2026-10-24T14:05:00+09:00' },
  previous: { from: '2026-10-12T00:00:00+09:00', to: '2026-10-17T14:05:00+09:00' },
  metrics: [
    { key: 'loginSuccess', unit: 'count', currency: null, current: '412', previous: '398' },
    { key: 'loginFailure', unit: 'count', currency: null, current: '37', previous: '41' },
    { key: 'accountLocked', unit: 'count', currency: null, current: '3', previous: '5' },
    { key: 'newAccount', unit: 'count', currency: null, current: '18', previous: '12' },
  ],
  series: [
    {
      date: '2026-10-19',
      values: { loginSuccess: '80', loginFailure: '7', accountLocked: '1', newAccount: '4' },
    },
  ],
};

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}
function iamError(code: string, status: number) {
  return jsonResponse({ code, message: 'e', timestamp: 't' }, status);
}

beforeEach(() => {
  cookieJar.clear();
  vi.unstubAllGlobals();
  cookieJar.set(ACCESS_COOKIE, 'GAP-OIDC-ACCESS-must-not-leak');
  cookieJar.set(OPERATOR_COOKIE, 'OPERATOR-TOKEN');
  cookieJar.set(TENANT_COOKIE, 'acme');
});

describe('getIamMetrics (TASK-MONO-811)', () => {
  it('GETs /api/admin/metrics?period=&tenantId= with the operator bearer + X-Tenant-Id, read-only', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(BODY));
    vi.stubGlobal('fetch', fetchMock);

    const res = await getIamMetrics('week', 'acme');

    const [url, init] = fetchMock.mock.calls[0];
    expect(String(url)).toBe('http://iam.local/api/admin/metrics?period=week&tenantId=acme');
    expect((init as RequestInit).method).toBe('GET');
    expect((init as RequestInit).body).toBeUndefined();
    const headers = (init as RequestInit).headers as Record<string, string>;
    expect(headers.Authorization).toBe('Bearer OPERATOR-TOKEN');
    expect(headers['X-Tenant-Id']).toBe('acme');
    expect(headers['X-Operator-Reason']).toBeUndefined();
    expect(headers['Idempotency-Key']).toBeUndefined();
    expect(res.metrics.map((m) => m.key)).toEqual([
      'loginSuccess',
      'loginFailure',
      'accountLocked',
      'newAccount',
    ]);
    expect(res.metrics.every((m) => m.unit === 'count' && m.currency === null)).toBe(true);
  });

  it('sends the month period and the given tenant (URL-encoded)', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ ...BODY, period: 'month' }));
    vi.stubGlobal('fetch', fetchMock);
    await getIamMetrics('month', 'a b&c');
    const url = new URL(String(fetchMock.mock.calls[0][0]));
    expect(url.pathname).toBe('/api/admin/metrics');
    expect(url.searchParams.get('period')).toBe('month');
    expect(url.searchParams.get('tenantId')).toBe('a b&c');
  });

  it('a body that does not parse (a number where an integer string belongs — F5) → the strip degrades', async () => {
    const bad = { ...BODY, metrics: [{ ...BODY.metrics[0], current: 412 }] };
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse(bad)));
    expect((await performanceMetricsCell(getIamMetrics('week', 'acme'))).status).toBe('degraded');
  });

  it('403 PERMISSION_DENIED and 403 TENANT_SCOPE_DENIED → forbidden', async () => {
    for (const code of ['PERMISSION_DENIED', 'TENANT_SCOPE_DENIED']) {
      vi.stubGlobal('fetch', vi.fn().mockResolvedValue(iamError(code, 403)));
      expect((await performanceMetricsCell(getIamMetrics('week', 'acme'))).status, code).toBe(
        'forbidden',
      );
    }
  });

  it('401 → rethrown (whole-session re-login), never a degraded strip', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(iamError('TOKEN_INVALID', 401)));
    const err = await performanceMetricsCell(getIamMetrics('week', 'acme')).catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(401);
  });

  it('400 (bad period) / 500 / 503 / network failure → degraded', async () => {
    for (const res of [
      iamError('VALIDATION_ERROR', 400),
      iamError('INTERNAL_ERROR', 500),
      iamError('DOWNSTREAM_ERROR', 503),
    ]) {
      vi.stubGlobal('fetch', vi.fn().mockResolvedValue(res));
      expect((await performanceMetricsCell(getIamMetrics('week', 'acme'))).status).toBe('degraded');
    }
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('fetch failed')));
    expect((await performanceMetricsCell(getIamMetrics('week', 'acme'))).status).toBe('degraded');
  });

  it('no operator token → 401 with NO fetch (no IAM-token fallback)', async () => {
    cookieJar.delete(OPERATOR_COOKIE);
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    const err = await getIamMetrics('week', 'acme').catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(401);
    expect(fetchMock).not.toHaveBeenCalled();
  });
});
