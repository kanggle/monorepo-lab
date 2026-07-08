import { describe, it, expect, vi, beforeEach } from 'vitest';

/**
 * Same-origin wms 마스터(master reference data) proxy route handler
 * (TASK-PC-FE-223 — `GET /api/wms/master/refs/{type}`, § 1.7):
 *   - IAM OIDC access token attached server-side (NOT the operator token);
 *     no mutation artifacts.
 *   - forwards `q`/`status`/`page`/`size` filters, caps size.
 *   - **type whitelist**: an unsupported `{type}` → `400 VALIDATION_ERROR`,
 *     BEFORE any upstream call (task § Failure Scenarios).
 *   - 401 → 401 (whole-session re-login signal; no partial authed state).
 *   - 403 → 403 (role-insufficient inline, no crash).
 *   - 503 / timeout → 503 (wms section degrades only; shell intact).
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

import { GET as refsGET } from '@/app/api/wms/master/refs/[type]/route';
import { ACCESS_COOKIE, OPERATOR_COOKIE } from '@/shared/lib/session';

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}
function wmsError(code: string, status: number) {
  return new Response(
    JSON.stringify({ error: { code, message: 'e', timestamp: 't' } }),
    { status, headers: { 'Content-Type': 'application/json' } },
  );
}

const REFS = {
  content: [],
  page: { number: 0, size: 20, totalElements: 0, totalPages: 0 },
};

function refsReq(type: string, qs = '') {
  return refsGET(
    new Request(`http://console.local/api/wms/master/refs/${type}${qs}`),
    { params: Promise.resolve({ type }) },
  );
}

beforeEach(() => {
  cookieJar.clear();
  vi.unstubAllGlobals();
});

describe('GET /api/wms/master/refs/{type} proxy (TASK-PC-FE-223)', () => {
  it('attaches the IAM OIDC access token (NOT the operator token), forwards q/status/size', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    cookieJar.set(OPERATOR_COOKIE, 'OP-MUST-NOT-USE');
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(REFS));
    vi.stubGlobal('fetch', fetchMock);

    const res = await refsReq(
      'locations',
      '?q=WH01&status=ACTIVE&size=999',
    );
    expect(res.status).toBe(200);

    const [url, init] = fetchMock.mock.calls[0];
    const h = (init as RequestInit).headers as Record<string, string>;
    expect(h.Authorization).toBe('Bearer GAP-ACCESS');
    expect(h.Authorization).not.toContain('OP-MUST-NOT-USE');
    expect(h['X-Tenant-Id']).toBeUndefined();
    expect(h['Idempotency-Key']).toBeUndefined();
    expect(h['X-Operator-Reason']).toBeUndefined();
    const upstream = new URL(String(url));
    expect(upstream.pathname).toContain('/dashboard/refs/locations');
    expect(upstream.searchParams.get('q')).toBe('WH01');
    expect(upstream.searchParams.get('status')).toBe('ACTIVE');
    expect(upstream.searchParams.get('size')).toBe('100'); // capped
  });

  it.each(['warehouses', 'zones', 'locations', 'skus', 'lots', 'partners'])(
    'a supported type (%s) passes the whitelist and reaches the upstream call',
    async (type) => {
      cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
      const fetchMock = vi.fn().mockResolvedValue(jsonResponse(REFS));
      vi.stubGlobal('fetch', fetchMock);

      const res = await refsReq(type);
      expect(res.status).toBe(200);
      expect(fetchMock).toHaveBeenCalledTimes(1);
      const upstream = new URL(String(fetchMock.mock.calls[0][0]));
      expect(upstream.pathname).toContain(`/dashboard/refs/${type}`);
    },
  );

  it('an unsupported type → 400 VALIDATION_ERROR, NO upstream call (whitelist guard)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);

    const res = await refsReq('invented-type');
    expect(res.status).toBe(400);
    const body = await res.json();
    expect(body.code).toBe('VALIDATION_ERROR');
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('401 from wms → 401 (whole-session re-login signal)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(wmsError('UNAUTHORIZED', 401)));
    const res = await refsReq('locations');
    expect(res.status).toBe(401);
  });

  it('no IAM session → 401 (no upstream call)', async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    const res = await refsReq('locations');
    expect(res.status).toBe(401);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('403 from wms → 403 (role-insufficient inline)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(wmsError('FORBIDDEN', 403)));
    const res = await refsReq('locations');
    expect(res.status).toBe(403);
  });

  it('503 from wms → 503 (wms section degrades only)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(wmsError('SERVICE_UNAVAILABLE', 503)),
    );
    const res = await refsReq('locations');
    expect(res.status).toBe(503);
  });
});
