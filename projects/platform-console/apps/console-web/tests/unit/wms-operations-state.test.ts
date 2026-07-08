import { describe, it, expect, vi, beforeEach } from 'vitest';

/**
 * `features/wms-ops/api/operations-state.ts` — the server-side 운영설정
 * (operations settings) section state for the dedicated `/wms/operations`
 * route (TASK-PC-FE-224, mirrors `wms-master-state.test.ts` for the
 * eligibility gate + resilience shape, but asserts the TWO INDEPENDENT
 * cells — settings + projection-status — resolve on their own):
 *   - not wms-eligible → `notEligible` block, NO wms call fabricated;
 *   - eligible + both ok → settings/projection both resolve `ok`;
 *   - ONE leg 403/503 while the other stays `ok` (task Failure Scenario:
 *     a whole-section degrade must never blank the healthy section);
 *   - 401 on EITHER leg → whole-session re-login (redirect), never a
 *     per-cell degrade.
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

import { getWmsOperationsState } from '@/features/wms-ops/api/operations-state';
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

const SETTINGS = {
  content: [
    {
      key: 'inventory.reservation.ttl_hours',
      scope: 'GLOBAL',
      valueJson: 24,
      description: 'Reservation TTL in hours',
      version: 3,
    },
    {
      key: 'inventory.low_stock.default_threshold_qty',
      scope: 'GLOBAL',
      valueJson: 10,
      description: 'Default low-stock threshold quantity',
      version: 1,
    },
  ],
  page: { number: 0, size: 20, totalElements: 2, totalPages: 1 },
  sort: 'key,asc',
};

const PROJECTION = {
  projections: [
    {
      topic: 'wms.inventory.adjusted.v1',
      consumerGroup: 'admin-projection',
      lagSeconds: 1.4,
      lastEventAt: '2026-07-08T01:00:00Z',
      lastProjectedAt: '2026-07-08T01:00:01.400Z',
      lifetimeApplied: 12048,
      lifetimeIgnoredDuplicate: 17,
      lifetimeFailed: 0,
    },
  ],
  worstLagSeconds: 4.8,
};

/** Route the mocked `fetch` by upstream path — settings vs. projection-status. */
function routedFetchMock(opts: {
  settings?: { status: number; body?: unknown };
  projection?: { status: number; body?: unknown };
}) {
  return vi.fn((url: string) => {
    const u = String(url);
    if (u.includes('/settings')) {
      const { status = 200, body = SETTINGS } = opts.settings ?? {};
      return Promise.resolve(
        status === 200 ? jsonResponse(body) : wmsError('X', status),
      );
    }
    if (u.includes('/operations/projection-status')) {
      const { status = 200, body = PROJECTION } = opts.projection ?? {};
      return Promise.resolve(
        status === 200 ? jsonResponse(body) : wmsError('X', status),
      );
    }
    throw new Error(`unexpected upstream url: ${u}`);
  });
}

beforeEach(() => {
  cookieJar.clear();
  vi.unstubAllGlobals();
});

describe('getWmsOperationsState — eligibility gate (§ 2.4.5, TASK-PC-FE-224)', () => {
  it('not eligible → notEligible block, NO wms call fabricated', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);

    const state = await getWmsOperationsState(false);
    expect(state.notEligible).toBe(true);
    expect(state.settings).toBeNull();
    expect(state.projection).toBeNull();
    expect(fetchMock).not.toHaveBeenCalled();
  });
});

describe('getWmsOperationsState — parallel fan-out (both cells)', () => {
  it('eligible + both legs ok → settings + projection both resolve ok', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    vi.stubGlobal('fetch', routedFetchMock({}));

    const state = await getWmsOperationsState(true);
    expect(state.notEligible).toBe(false);
    expect(state.settingsStatus).toBe('ok');
    expect(state.settings).toHaveLength(2);
    expect(state.settings?.[0].key).toBe('inventory.reservation.ttl_hours');
    expect(state.projectionStatus).toBe('ok');
    expect(state.projection?.projections).toHaveLength(1);
    expect(state.projection?.worstLagSeconds).toBe(4.8);
  });
});

describe('getWmsOperationsState — independent cells (task Failure Scenarios)', () => {
  it('settings 403 while projection stays ok → only settings degrades to forbidden', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    vi.stubGlobal(
      'fetch',
      routedFetchMock({ settings: { status: 403 } }),
    );

    const state = await getWmsOperationsState(true);
    expect(state.settingsStatus).toBe('forbidden');
    expect(state.settings).toBeNull();
    expect(state.projectionStatus).toBe('ok');
    expect(state.projection).not.toBeNull();
  });

  it('projection 503 while settings stays ok → only projection degrades', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    vi.stubGlobal(
      'fetch',
      routedFetchMock({ projection: { status: 503 } }),
    );

    const state = await getWmsOperationsState(true);
    expect(state.projectionStatus).toBe('degraded');
    expect(state.projection).toBeNull();
    expect(state.settingsStatus).toBe('ok');
    expect(state.settings).not.toBeNull();
  });

  it('both legs 503 → both cells degrade independently, no crash', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    vi.stubGlobal(
      'fetch',
      routedFetchMock({
        settings: { status: 503 },
        projection: { status: 503 },
      }),
    );

    const state = await getWmsOperationsState(true);
    expect(state.settingsStatus).toBe('degraded');
    expect(state.projectionStatus).toBe('degraded');
  });

  it('settings-key producer omission (empty content) parses fine — screen filters, state stays ok', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    vi.stubGlobal(
      'fetch',
      routedFetchMock({
        settings: {
          status: 200,
          body: { content: [], page: { number: 0, size: 20, totalElements: 0, totalPages: 0 } },
        },
      }),
    );

    const state = await getWmsOperationsState(true);
    expect(state.settingsStatus).toBe('ok');
    expect(state.settings).toEqual([]);
  });
});

describe('getWmsOperationsState — 401 on either leg → whole-session re-login', () => {
  it('settings 401 → redirect(/login), not a per-cell degrade', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    vi.stubGlobal(
      'fetch',
      routedFetchMock({ settings: { status: 401 } }),
    );

    const err = await getWmsOperationsState(true).catch((e) => e);
    expect(err).toBeInstanceOf(Error);
    expect((err as Error).message).toBe('REDIRECT:/login');
  });

  it('projection 401 → redirect(/login), even though settings resolved fine', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    vi.stubGlobal(
      'fetch',
      routedFetchMock({ projection: { status: 401 } }),
    );

    const err = await getWmsOperationsState(true).catch((e) => e);
    expect(err).toBeInstanceOf(Error);
    expect((err as Error).message).toBe('REDIRECT:/login');
  });
});
