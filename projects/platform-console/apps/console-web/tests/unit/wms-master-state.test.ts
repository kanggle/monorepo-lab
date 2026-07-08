import { describe, it, expect, vi, beforeEach } from 'vitest';

/**
 * `features/wms-ops/api/master-state.ts` — the server-side 마스터(master
 * reference data) section state for the dedicated `/wms/master` route
 * (TASK-PC-FE-223, mirrors `wms-inbound-state.test.ts`):
 *   - not wms-eligible → `notEligible` block, NO wms call fabricated
 *     (no cross-tenant call; the console never sends a tenant — wms
 *     resolves it from the JWT claim);
 *   - eligible → seeds the default tab (`locations`) refs (IAM OIDC token,
 *     server-side);
 *   - 403 → `forbidden` (inline, no crash);
 *   - 503/timeout → `degraded` (this section only — shell intact);
 *   - the read-model-lag hint is surfaced when present.
 *
 * 401 → whole-session re-login is exercised here (mirrors
 * `wms-inbound-state.test.ts` — no separate proxy-test split for this
 * single-read state).
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

import { getWmsMasterState } from '@/features/wms-ops/api/master-state';
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

const REFS = {
  content: [],
  page: { number: 0, size: 20, totalElements: 0, totalPages: 0 },
};

beforeEach(() => {
  cookieJar.clear();
  vi.unstubAllGlobals();
});

describe('getWmsMasterState — eligibility gate (§ 2.4.5, TASK-PC-FE-223)', () => {
  it('not eligible → notEligible block, NO wms call fabricated', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);

    const state = await getWmsMasterState(false);
    expect(state.notEligible).toBe(true);
    expect(state.refs).toBeNull();
    // No cross-tenant call ever fabricated.
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('eligible → seeds the default tab (locations) refs (IAM OIDC token, server-side)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    const fetchMock = vi.fn((_u: string, _init?: RequestInit) =>
      Promise.resolve(jsonResponse(REFS)),
    );
    vi.stubGlobal('fetch', fetchMock);

    const state = await getWmsMasterState(true);
    expect(state.notEligible).toBe(false);
    expect(state.degraded).toBe(false);
    expect(state.refs).not.toBeNull();
    expect(state.refType).toBe('locations');
    const [url, init] = fetchMock.mock.calls[0];
    const h = (init as RequestInit).headers as Record<string, string>;
    expect(h.Authorization).toBe('Bearer GAP-ACCESS');
    expect(String(url)).toContain('/dashboard/refs/locations');
  });

  it('surfaces the read-model-lag hint when the producer set it', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    vi.stubGlobal(
      'fetch',
      vi.fn(() =>
        Promise.resolve(
          jsonResponse(REFS, 200, { 'X-Read-Model-Lag-Seconds': '9' }),
        ),
      ),
    );
    const state = await getWmsMasterState(true);
    expect(state.lagSeconds).toBe(9);
  });

  it('403 → forbidden (inline, no crash)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    vi.stubGlobal(
      'fetch',
      vi.fn(() => Promise.resolve(wmsError('FORBIDDEN', 403))),
    );
    const state = await getWmsMasterState(true);
    expect(state.forbidden).toBe(true);
    expect(state.degraded).toBe(false);
  });

  it('503 → degraded (this section only — shell intact)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    vi.stubGlobal(
      'fetch',
      vi.fn(() => Promise.resolve(wmsError('SERVICE_UNAVAILABLE', 503))),
    );
    const state = await getWmsMasterState(true);
    expect(state.degraded).toBe(true);
    expect(state.notEligible).toBe(false);
  });

  it('401 → whole-session re-login (redirect, not a per-section degrade)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    vi.stubGlobal(
      'fetch',
      vi.fn(() => Promise.resolve(wmsError('UNAUTHORIZED', 401))),
    );
    const err = await getWmsMasterState(true).catch((e) => e);
    expect(err).toBeInstanceOf(Error);
    expect((err as Error).message).toBe('REDIRECT:/login');
  });
});
