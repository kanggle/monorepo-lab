import { describe, it, expect, vi, beforeEach } from 'vitest';

/**
 * `features/wms-ops/api/wms-state.ts` — the server-side section state
 * (TASK-PC-FE-007 / § 2.4.5):
 *   - not wms-eligible → `notEligible` block, NO wms call fabricated
 *     (no cross-tenant call; the console never sends a tenant — wms
 *     resolves it from the JWT claim);
 *   - eligible → seeds inventory + alerts (IAM OIDC token, server-side);
 *   - 403 → `forbidden` (inline, no crash);
 *   - 503/timeout → `degraded` (wms section only — shell intact);
 *   - the read-model-lag hint is surfaced when present.
 *
 * 401 → whole-session re-login is exercised by the api/proxy tests (it
 * triggers `next/navigation` redirect; pinned there to keep this unit
 * focused on the state shape).
 */

const cookieJar = new Map<string, string>();
vi.mock('next/headers', () => ({
  cookies: async () => ({
    get: (n: string) =>
      cookieJar.has(n) ? { value: cookieJar.get(n)! } : undefined,
  }),
}));
vi.mock('next/navigation', () => ({
  redirect: (to: string) => {
    throw new Error(`REDIRECT:${to}`);
  },
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

import { getWmsSectionState } from '@/features/wms-ops/api/wms-state';
import { ACCESS_COOKIE } from '@/shared/lib/session';

function jsonResponse(
  body: unknown,
  status = 200,
  headers: Record<string, string> = {},
) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json', ...headers },
  });
}
function wmsError(code: string, status: number) {
  return new Response(
    JSON.stringify({ error: { code, message: 'e', timestamp: 't' } }),
    { status, headers: { 'Content-Type': 'application/json' } },
  );
}

const INV = {
  content: [],
  page: { number: 0, size: 20, totalElements: 0, totalPages: 0 },
};
const ALERTS = {
  content: [],
  page: { number: 0, size: 20, totalElements: 0, totalPages: 0 },
};

beforeEach(() => {
  cookieJar.clear();
  vi.unstubAllGlobals();
});

describe('getWmsSectionState — eligibility gate (§ 2.4.5)', () => {
  it('not eligible → notEligible block, NO wms call fabricated', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);

    const state = await getWmsSectionState(false);
    expect(state.notEligible).toBe(true);
    expect(state.inventory).toBeNull();
    expect(state.alerts).toBeNull();
    // No cross-tenant call ever fabricated.
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('eligible → seeds inventory + alerts (IAM OIDC token, server-side)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    const fetchMock = vi.fn((_u: string, _init?: RequestInit) =>
      Promise.resolve(jsonResponse(INV)),
    );
    vi.stubGlobal('fetch', fetchMock);

    const state = await getWmsSectionState(true);
    expect(state.notEligible).toBe(false);
    expect(state.degraded).toBe(false);
    expect(state.inventory).not.toBeNull();
    expect(state.alerts).not.toBeNull();
    // Both seeded reads carry the IAM OIDC access token.
    const h = (fetchMock.mock.calls[0][1] as RequestInit)
      .headers as Record<string, string>;
    expect(h.Authorization).toBe('Bearer GAP-ACCESS');
  });

  it('surfaces the read-model-lag hint when the producer set it', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    vi.stubGlobal(
      'fetch',
      vi.fn(() =>
        Promise.resolve(
          jsonResponse(INV, 200, { 'X-Read-Model-Lag-Seconds': '9' }),
        ),
      ),
    );
    const state = await getWmsSectionState(true);
    expect(state.lagSeconds).toBe(9);
  });

  it('403 → forbidden (inline, no crash)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    vi.stubGlobal(
      'fetch',
      vi.fn(() => Promise.resolve(wmsError('FORBIDDEN', 403))),
    );
    const state = await getWmsSectionState(true);
    expect(state.forbidden).toBe(true);
    expect(state.degraded).toBe(false);
  });

  it('503 → degraded (wms section only — shell intact)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    vi.stubGlobal(
      'fetch',
      vi.fn(() => Promise.resolve(wmsError('SERVICE_UNAVAILABLE', 503))),
    );
    const state = await getWmsSectionState(true);
    expect(state.degraded).toBe(true);
    expect(state.notEligible).toBe(false);
  });

  it('401 → whole-session re-login (redirect, not a per-section degrade)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    vi.stubGlobal(
      'fetch',
      vi.fn(() => Promise.resolve(wmsError('UNAUTHORIZED', 401))),
    );
    const err = await getWmsSectionState(true).catch((e) => e);
    expect(err).toBeInstanceOf(Error);
    expect((err as Error).message).toBe('REDIRECT:/login');
  });
});
