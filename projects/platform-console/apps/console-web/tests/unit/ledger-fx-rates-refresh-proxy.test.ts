import { describe, it, expect, vi, beforeEach } from 'vitest';

/**
 * Same-origin ledger FX 환율 수동 refresh proxy route handler
 * (TASK-MONO-300 Scope B — `POST /api/ledger/fx-rates/refresh`):
 *   - POST only (no GET/PUT/PATCH/DELETE);
 *   - domain-facing token attached server-side (NOT the operator token);
 *   - no request body to the upstream (unconditional refresh);
 *   - feed-disabled → 200 `{feedEnabled:false, refreshed:0}` (no-op);
 *   - 403 → 403 passthrough; 503/timeout → 503;
 *   - no IAM session → 401 (no upstream call).
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

import { POST as fxRefreshPOST } from '@/app/api/ledger/fx-rates/refresh/route';
import * as fxRefreshRoute from '@/app/api/ledger/fx-rates/refresh/route';
import { ACCESS_COOKIE, OPERATOR_COOKIE } from '@/shared/lib/session';

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}
function ledgerError(code: string, status: number) {
  return new Response(
    JSON.stringify({ code, message: 'e', timestamp: 't' }),
    { status, headers: { 'Content-Type': 'application/json' } },
  );
}

const REFRESH_ENV_OK = {
  data: { feedEnabled: true, refreshed: 3 },
  meta: { timestamp: '2026-06-19T11:00:00Z' },
};
const REFRESH_ENV_DISABLED = {
  data: { feedEnabled: false, refreshed: 0 },
  meta: { timestamp: '2026-06-19T11:00:00Z' },
};

beforeEach(() => {
  cookieJar.clear();
  vi.unstubAllGlobals();
});

describe('POST /api/ledger/fx-rates/refresh proxy (TASK-MONO-300, mutation)', () => {
  it('returns the upstream payload, uses domain-facing token (NOT operator), POST, no body to upstream', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    cookieJar.set(OPERATOR_COOKIE, 'OP-MUST-NOT-USE');
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(REFRESH_ENV_OK));
    vi.stubGlobal('fetch', fetchMock);

    const res = await fxRefreshPOST();
    expect(res.status).toBe(200);
    const body = await res.json();
    expect(body.feedEnabled).toBe(true);
    expect(body.refreshed).toBe(3);
    expect(typeof body.refreshed).toBe('number');

    const [url, init] = fetchMock.mock.calls[0];
    const h = (init as RequestInit).headers as Record<string, string>;
    expect(h.Authorization).toBe('Bearer GAP-ACCESS');
    expect(h.Authorization).not.toContain('OP-MUST-NOT-USE');
    expect((init as RequestInit).method).toBe('POST');
    // The upstream call carries NO body (unconditional refresh).
    expect((init as RequestInit).body).toBeUndefined();
    expect(h['Content-Type']).toBeUndefined();
    expect(h['X-Tenant-Id']).toBeUndefined();
    expect(h['Idempotency-Key']).toBeUndefined();
    expect(String(url)).toBe(
      'http://finance.local/api/finance/ledger/fx-rates/refresh',
    );
  });

  it('feed-disabled → 200 {feedEnabled:false, refreshed:0} (no-op, NOT an error)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse(REFRESH_ENV_DISABLED)));

    const res = await fxRefreshPOST();
    expect(res.status).toBe(200);
    const body = await res.json();
    expect(body.feedEnabled).toBe(false);
    expect(body.refreshed).toBe(0);
  });

  it('403 TENANT_FORBIDDEN → 403', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(ledgerError('TENANT_FORBIDDEN', 403)));
    const res = await fxRefreshPOST();
    expect(res.status).toBe(403);
  });

  it('503 → 503 (ledger section degrades)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(ledgerError('SERVICE_UNAVAILABLE', 503)));
    const res = await fxRefreshPOST();
    expect(res.status).toBe(503);
  });

  it('no IAM session → 401 (no upstream call)', async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    const res = await fxRefreshPOST();
    expect(res.status).toBe(401);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('POST-only: the route exports no GET/PUT/PATCH/DELETE', () => {
    expect(typeof fxRefreshRoute.POST).toBe('function');
    expect((fxRefreshRoute as Record<string, unknown>).GET).toBeUndefined();
    expect((fxRefreshRoute as Record<string, unknown>).PUT).toBeUndefined();
    expect((fxRefreshRoute as Record<string, unknown>).PATCH).toBeUndefined();
    expect((fxRefreshRoute as Record<string, unknown>).DELETE).toBeUndefined();
  });
});
