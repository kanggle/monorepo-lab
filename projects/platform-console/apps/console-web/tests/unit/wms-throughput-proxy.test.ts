import { describe, it, expect, vi, beforeEach } from 'vitest';

/**
 * TASK-PC-FE-358 — same-origin `GET /api/wms/throughput` proxy (contract
 * § 2.4.5 routing note). Mirrors `master-refs-proxy.test.ts`:
 *   - validation → `400 VALIDATION_ERROR` BEFORE any upstream call;
 *   - IAM OIDC token attached server-side, no mutation artifacts, query
 *     forwarded verbatim;
 *   - 401 → 401 · 403 → 403 · 503 → 503 (shared `mapWmsError`).
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

import { GET } from '@/app/api/wms/throughput/route';
import { ACCESS_COOKIE, OPERATOR_COOKIE } from '@/shared/lib/session';

const WH = '01910000-0000-7000-8000-000000000001';

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}
function wmsError(code: string, status: number) {
  return new Response(JSON.stringify({ error: { code, message: 'e', timestamp: 't' } }), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

const BODY = {
  warehouseId: WH,
  from: '2026-10-09',
  to: '2026-10-22',
  days: [
    {
      date: '2026-10-20',
      inbound: { putawayCount: 3, qtyReceived: 30 },
      outbound: { shipmentCount: 1, qtyShipped: 5 },
    },
  ],
  totals: {
    inbound: { putawayCount: 3, qtyReceived: 30 },
    outbound: { shipmentCount: 1, qtyShipped: 5 },
  },
};

function req(qs: string) {
  return GET(new Request(`http://console.local/api/wms/throughput${qs}`));
}

const VALID = `?warehouseId=${WH}&from=2026-10-09&to=2026-10-22`;

beforeEach(() => {
  cookieJar.clear();
  vi.unstubAllGlobals();
});

describe('GET /api/wms/throughput proxy (TASK-PC-FE-358)', () => {
  it('valid query → forwarded verbatim with the IAM OIDC token, no mutation artifacts; body passed through', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    cookieJar.set(OPERATOR_COOKIE, 'OP-MUST-NOT-USE');
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(BODY));
    vi.stubGlobal('fetch', fetchMock);

    const res = await req(VALID);
    expect(res.status).toBe(200);
    expect(await res.json()).toEqual(BODY);

    const [url, init] = fetchMock.mock.calls[0];
    const h = (init as RequestInit).headers as Record<string, string>;
    expect(h.Authorization).toBe('Bearer GAP-ACCESS');
    expect(h['Idempotency-Key']).toBeUndefined();
    expect(h['X-Operator-Reason']).toBeUndefined();
    expect(h['X-Tenant-Id']).toBeUndefined();
    expect((init as RequestInit).method).toBe('GET');
    const upstream = new URL(String(url));
    expect(upstream.pathname).toBe('/api/v1/admin/dashboard/throughput');
    expect(upstream.searchParams.get('warehouseId')).toBe(WH);
    expect(upstream.searchParams.get('from')).toBe('2026-10-09');
    expect(upstream.searchParams.get('to')).toBe('2026-10-22');
  });

  it.each([
    ['no params', ''],
    ['non-UUID warehouseId', '?warehouseId=wh-1&from=2026-10-09&to=2026-10-22'],
    ['missing from', `?warehouseId=${WH}&to=2026-10-22`],
    ['missing to', `?warehouseId=${WH}&from=2026-10-09`],
    ['non-ISO date', `?warehouseId=${WH}&from=20261009&to=2026-10-22`],
    ['impossible date', `?warehouseId=${WH}&from=2026-02-30&to=2026-03-02`],
    ['to < from', `?warehouseId=${WH}&from=2026-10-22&to=2026-10-21`],
    ['range > 90 days', `?warehouseId=${WH}&from=2026-01-01&to=2026-04-01`],
  ])('%s → 400 VALIDATION_ERROR, NO upstream call', async (_l, qs) => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    const res = await req(qs);
    expect(res.status).toBe(400);
    expect((await res.json()).code).toBe('VALIDATION_ERROR');
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('401 from wms → 401 (whole-session re-login signal)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(wmsError('UNAUTHORIZED', 401)));
    expect((await req(VALID)).status).toBe(401);
  });

  it('no IAM session (half session) → 401, no upstream call', async () => {
    cookieJar.set(OPERATOR_COOKIE, 'OPERATOR-ONLY-HALF-SESSION');
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    expect((await req(VALID)).status).toBe(401);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('403 from wms → 403', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(wmsError('FORBIDDEN', 403)));
    expect((await req(VALID)).status).toBe(403);
  });

  it('503 from wms → 503', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(wmsError('SERVICE_UNAVAILABLE', 503)));
    expect((await req(VALID)).status).toBe(503);
  });
});
