import { describe, it, expect, vi, beforeEach } from 'vitest';

/**
 * `features/ecommerce-ops/api/settlements-state.ts` — eligibility/degrade branch
 * coverage (TASK-PC-FE-221 Phase A AC-2). Mirrors `ecommerce-sellers-state`.
 *
 * Covers:
 *   - `getSettlementsSectionState` notEligible / degrade / forbidden / happy
 *     (seeds BOTH accruals + periods, in parallel);
 *   - `getPeriodPayoutsSectionState` notEligible / notFound / degrade / forbidden
 *     / happy.
 *
 * Uses ECOMMERCE_ADMIN_BASE_URL (admin subtree) — same as sellers.
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
    ECOMMERCE_ADMIN_BASE_URL: 'http://ecommerce.local/api/admin',
    ECOMMERCE_PUBLIC_BASE_URL: 'http://ecommerce.local/api',
    ECOMMERCE_TIMEOUT_MS: 50,
    LOG_LEVEL: 'info' as const,
    NEXT_PUBLIC_APP_URL: 'http://console.local',
  },
}));
vi.mock('@/shared/config/env', () => ({
  clientEnv: { NEXT_PUBLIC_APP_URL: ENV.NEXT_PUBLIC_APP_URL },
  getServerEnv: () => ENV,
}));

vi.mock('next/navigation', () => ({
  redirect: vi.fn((url: string) => {
    throw new Error(`REDIRECT:${url}`);
  }),
}));

import {
  getSettlementsSectionState,
  getPeriodPayoutsSectionState,
} from '@/features/ecommerce-ops/api/settlements-state';
import { ACCESS_COOKIE } from '@/shared/lib/session';

const ACCRUALS = { items: [], page: 0, size: 20, totalElements: 0 };
const PERIODS = {
  items: [
    {
      periodId: '2026-06',
      from: '2026-06-01T00:00:00Z',
      to: '2026-06-30T23:59:59Z',
      status: 'OPEN',
      closedAt: null,
      sellerCount: 1,
    },
  ],
  page: 0,
  size: 20,
  totalElements: 1,
};
const PAYOUTS = { items: [], page: 0, size: 20, totalElements: 0 };

function okFor(url: string) {
  const body = url.includes('/accruals')
    ? ACCRUALS
    : url.includes('/payouts')
      ? PAYOUTS
      : PERIODS;
  return new Response(JSON.stringify(body), {
    status: 200,
    headers: { 'Content-Type': 'application/json' },
  });
}
function errResponse(code: string, status: number) {
  return new Response(JSON.stringify({ code, message: 'err' }), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

describe('getSettlementsSectionState() — eligibility + degrade', () => {
  beforeEach(() => {
    cookieJar.clear();
    vi.unstubAllGlobals();
  });

  it('notEligible=true when eligible=false (no ecommerce call)', async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    const state = await getSettlementsSectionState(false);
    expect(state.notEligible).toBe(true);
    expect(state.accruals).toBeNull();
    expect(state.periods).toBeNull();
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('seeds BOTH accruals + periods on the happy path', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    vi.stubGlobal(
      'fetch',
      vi.fn((u: string) => Promise.resolve(okFor(String(u)))),
    );
    const state = await getSettlementsSectionState(true);
    expect(state.accruals).not.toBeNull();
    expect(state.periods?.items[0].periodId).toBe('2026-06');
    expect(state.degraded).toBe(false);
    expect(state.notEligible).toBe(false);
  });

  it('degraded=true when the gateway returns 503', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(errResponse('SERVICE_UNAVAILABLE', 503)));
    const state = await getSettlementsSectionState(true);
    expect(state.degraded).toBe(true);
    expect(state.accruals).toBeNull();
  });

  it('forbidden=true on 403', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(errResponse('TENANT_FORBIDDEN', 403)));
    const state = await getSettlementsSectionState(true);
    expect(state.forbidden).toBe(true);
    expect(state.degraded).toBe(false);
  });
});

describe('getPeriodPayoutsSectionState() — eligibility + degrade + notFound', () => {
  beforeEach(() => {
    cookieJar.clear();
    vi.unstubAllGlobals();
  });

  it('notEligible=true when eligible=false', async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    const state = await getPeriodPayoutsSectionState(false, '2026-06');
    expect(state.notEligible).toBe(true);
    expect(state.payouts).toBeNull();
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('notFound=true on 404 SETTLEMENT_NOT_FOUND', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(errResponse('SETTLEMENT_NOT_FOUND', 404)));
    const state = await getPeriodPayoutsSectionState(true, 'nope');
    expect(state.notFound).toBe(true);
    expect(state.payouts).toBeNull();
  });

  it('degraded=true on 503', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(errResponse('SERVICE_UNAVAILABLE', 503)));
    const state = await getPeriodPayoutsSectionState(true, '2026-06');
    expect(state.degraded).toBe(true);
  });

  it('forbidden=true on 403', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(errResponse('TENANT_FORBIDDEN', 403)));
    const state = await getPeriodPayoutsSectionState(true, '2026-06');
    expect(state.forbidden).toBe(true);
  });

  it('returns payouts on the happy path (echoes periodId)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    vi.stubGlobal(
      'fetch',
      vi.fn((u: string) => Promise.resolve(okFor(String(u)))),
    );
    const state = await getPeriodPayoutsSectionState(true, '2026-06');
    expect(state.periodId).toBe('2026-06');
    expect(state.payouts).not.toBeNull();
    expect(state.notFound).toBe(false);
    expect(state.degraded).toBe(false);
  });
});
