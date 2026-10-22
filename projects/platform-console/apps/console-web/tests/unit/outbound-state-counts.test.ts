import { describe, it, expect, vi, beforeEach } from 'vitest';

/**
 * TASK-PC-FE-362 — `getOutboundSectionState` status totals (§ 2.4.5.1): 7
 * extra `GET /orders?status=…&size=1` reads, each its own cell, counted once
 * with the same credential as the page-0 list.
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
    WMS_OUTBOUND_BASE_URL: 'http://wms.local/api/v1/outbound',
    WMS_OUTBOUND_TIMEOUT_MS: 50,
    LOG_LEVEL: 'info' as const,
    NEXT_PUBLIC_APP_URL: 'http://console.local',
  },
}));
vi.mock('@/shared/config/env', () => ({
  clientEnv: { NEXT_PUBLIC_APP_URL: ENV.NEXT_PUBLIC_APP_URL },
  getServerEnv: () => ENV,
}));

import { getOutboundSectionState } from '@/features/wms-outbound-ops/api/outbound-state';
import { ACCESS_COOKIE } from '@/shared/lib/session';

function page(total: number) {
  return new Response(
    JSON.stringify({
      content: [],
      page: { number: 0, size: 1, totalElements: total, totalPages: total },
    }),
    { status: 200, headers: { 'Content-Type': 'application/json' } },
  );
}
function wmsError(code: string, status: number) {
  return new Response(
    JSON.stringify({ error: { code, message: 'e', timestamp: 't' } }),
    { status, headers: { 'Content-Type': 'application/json' } },
  );
}

const TOTALS: Record<string, number> = {
  PICKING: 2,
  PICKED: 3,
  PACKING: 0,
  PACKED: 4,
  SHIPPED: 10,
  CANCELLED: 1,
  BACKORDERED: 5,
};

/** Route by the `status` query; `override` replaces one status' response. */
function router(override: Record<string, () => Response> = {}) {
  return vi.fn((url: string, _init?: RequestInit) => {
    const u = new URL(String(url));
    const status = u.searchParams.get('status');
    if (status && override[status]) return Promise.resolve(override[status]());
    return Promise.resolve(page(status ? TOTALS[status] : 40));
  });
}

const statusCallsOf = (m: ReturnType<typeof vi.fn>) =>
  m.mock.calls
    .map((c) => new URL(String(c[0])))
    .filter((u) => u.searchParams.has('status'));

beforeEach(() => {
  cookieJar.clear();
  cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
  vi.unstubAllGlobals();
});

describe('getOutboundSectionState — status totals (TASK-PC-FE-362)', () => {
  it('7 status reads (size=1) in contract order + the page-0 list = 8 calls', async () => {
    const f = router();
    vi.stubGlobal('fetch', f);
    const state = await getOutboundSectionState(true);
    expect(f).toHaveBeenCalledTimes(8);
    const calls = statusCallsOf(f);
    expect(calls).toHaveLength(7);
    expect(calls.every((u) => u.searchParams.get('size') === '1')).toBe(true);
    expect(calls.every((u) => u.pathname.endsWith('/orders'))).toBe(true);
    expect(state.statusCounts.map((c) => [c.status, c.count, c.cellStatus])).toEqual([
      ['PICKING', 2, 'ok'],
      ['PICKED', 3, 'ok'],
      ['PACKING', 0, 'ok'],
      ['PACKED', 4, 'ok'],
      ['SHIPPED', 10, 'ok'],
      ['CANCELLED', 1, 'ok'],
      ['BACKORDERED', 5, 'ok'],
    ]);
    // same credential on every call
    for (const c of f.mock.calls) {
      const h = (c[1] as RequestInit).headers as Record<string, string>;
      expect(h.Authorization).toBe('Bearer GAP-ACCESS');
    }
  });

  it('not eligible → no call, no counts', async () => {
    const f = router();
    vi.stubGlobal('fetch', f);
    const state = await getOutboundSectionState(false);
    expect(f).not.toHaveBeenCalled();
    expect(state.statusCounts).toEqual([]);
  });

  it('one 503 → only that cell degraded; the page and the other cells stay ok', async () => {
    vi.stubGlobal(
      'fetch',
      router({ PACKED: () => wmsError('SERVICE_UNAVAILABLE', 503) }),
    );
    const state = await getOutboundSectionState(true);
    expect(state.degraded).toBe(false);
    expect(state.orders).not.toBeNull();
    const by = Object.fromEntries(state.statusCounts.map((c) => [c.status, c]));
    expect(by.PACKED).toMatchObject({ count: null, cellStatus: 'degraded' });
    expect(by.PICKING).toMatchObject({ count: 2, cellStatus: 'ok' });
    expect(by.BACKORDERED).toMatchObject({ count: 5, cellStatus: 'ok' });
  });

  it('one 403 on a status read → that cell forbidden, page still renders', async () => {
    vi.stubGlobal(
      'fetch',
      router({ CANCELLED: () => wmsError('FORBIDDEN', 403) }),
    );
    const state = await getOutboundSectionState(true);
    expect(state.forbidden).toBe(false);
    expect(
      state.statusCounts.find((c) => c.status === 'CANCELLED'),
    ).toMatchObject({ count: null, cellStatus: 'forbidden' });
  });

  it('a 401 on ONE status read → whole-session re-login (not a degraded cell)', async () => {
    vi.stubGlobal(
      'fetch',
      router({ SHIPPED: () => wmsError('UNAUTHORIZED', 401) }),
    );
    const err = await getOutboundSectionState(true).catch((e) => e);
    expect((err as Error).message).toBe('REDIRECT:/login?error=session_expired');
  });
});
