import { describe, it, expect, vi, beforeEach } from 'vitest';

/**
 * Same-origin wms 운영설정(operations settings) proxy route handlers
 * (TASK-PC-FE-224):
 *   - `GET /api/wms/settings` (§ 5.1) — IAM OIDC access token attached
 *     server-side (NOT the operator token); forwards
 *     `keyPrefix`/`scope`/`warehouseId`/`page`/`size`; no mutation
 *     artifacts.
 *   - `GET /api/wms/operations/projection-status` (§ 6.2) — same
 *     credential; no query params.
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

import { GET as settingsGET } from '@/app/api/wms/settings/route';
import { GET as projectionStatusGET } from '@/app/api/wms/operations/projection-status/route';
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

const SETTINGS = {
  content: [
    { key: 'inventory.reservation.ttl_hours', valueJson: 24 },
  ],
  page: { number: 0, size: 20, totalElements: 1, totalPages: 1 },
};
const PROJECTION = {
  projections: [{ topic: 'wms.inventory.adjusted.v1', lagSeconds: 1.4 }],
  worstLagSeconds: 4.8,
};

function settingsReq(qs = '') {
  return settingsGET(
    new Request(`http://console.local/api/wms/settings${qs}`),
  );
}
function projectionReq() {
  return projectionStatusGET();
}

beforeEach(() => {
  cookieJar.clear();
  vi.unstubAllGlobals();
});

describe('GET /api/wms/settings proxy (TASK-PC-FE-224)', () => {
  it('attaches the IAM OIDC access token (NOT the operator token), forwards filters', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    cookieJar.set(OPERATOR_COOKIE, 'OP-MUST-NOT-USE');
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(SETTINGS));
    vi.stubGlobal('fetch', fetchMock);

    const res = await settingsReq(
      '?keyPrefix=inventory&scope=GLOBAL&size=999',
    );
    expect(res.status).toBe(200);
    const body = await res.json();
    expect(body).toEqual(SETTINGS);

    const [url, init] = fetchMock.mock.calls[0];
    const h = (init as RequestInit).headers as Record<string, string>;
    expect(h.Authorization).toBe('Bearer GAP-ACCESS');
    expect(h.Authorization).not.toContain('OP-MUST-NOT-USE');
    expect(h['X-Tenant-Id']).toBeUndefined();
    expect(h['Idempotency-Key']).toBeUndefined();
    expect(h['X-Operator-Reason']).toBeUndefined();
    const upstream = new URL(String(url));
    expect(upstream.pathname).toContain('/settings');
    expect(upstream.searchParams.get('keyPrefix')).toBe('inventory');
    expect(upstream.searchParams.get('scope')).toBe('GLOBAL');
    expect(upstream.searchParams.get('size')).toBe('100'); // capped
  });

  it('401 from wms → 401 (whole-session re-login signal)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(wmsError('UNAUTHORIZED', 401)),
    );
    const res = await settingsReq();
    expect(res.status).toBe(401);
  });

  it('no IAM session → 401 (no upstream call)', async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    const res = await settingsReq();
    expect(res.status).toBe(401);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('403 from wms → 403 (role-insufficient inline; task Edge Case — a WMS_VIEWER 403 is producer role-mapping drift, still surfaced as forbidden here)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(wmsError('FORBIDDEN', 403)),
    );
    const res = await settingsReq();
    expect(res.status).toBe(403);
  });

  it('503 from wms → 503 (wms section degrades only)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(wmsError('SERVICE_UNAVAILABLE', 503)),
    );
    const res = await settingsReq();
    expect(res.status).toBe(503);
  });
});

describe('GET /api/wms/operations/projection-status proxy (TASK-PC-FE-224)', () => {
  it('attaches the IAM OIDC access token (NOT the operator token), no query params', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    cookieJar.set(OPERATOR_COOKIE, 'OP-MUST-NOT-USE');
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(PROJECTION));
    vi.stubGlobal('fetch', fetchMock);

    const res = await projectionReq();
    expect(res.status).toBe(200);
    const body = await res.json();
    expect(body).toEqual(PROJECTION);

    const [url, init] = fetchMock.mock.calls[0];
    const h = (init as RequestInit).headers as Record<string, string>;
    expect(h.Authorization).toBe('Bearer GAP-ACCESS');
    expect(h.Authorization).not.toContain('OP-MUST-NOT-USE');
    expect(h['X-Tenant-Id']).toBeUndefined();
    expect(h['Idempotency-Key']).toBeUndefined();
    const upstream = new URL(String(url));
    expect(upstream.pathname).toContain('/operations/projection-status');
  });

  it('401 from wms → 401 (whole-session re-login signal)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(wmsError('UNAUTHORIZED', 401)),
    );
    const res = await projectionReq();
    expect(res.status).toBe(401);
  });

  it('403 from wms → 403 (role-insufficient inline)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(wmsError('FORBIDDEN', 403)),
    );
    const res = await projectionReq();
    expect(res.status).toBe(403);
  });

  it('503 from wms → 503 (wms section degrades only)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(wmsError('SERVICE_UNAVAILABLE', 503)),
    );
    const res = await projectionReq();
    expect(res.status).toBe(503);
  });
});
