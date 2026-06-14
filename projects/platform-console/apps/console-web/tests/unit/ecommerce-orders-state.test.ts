import { describe, it, expect, vi, beforeEach } from 'vitest';

/**
 * `features/ecommerce-ops/api/order-types.ts` — `allowedTransitions()` state
 * machine + `orders-state.ts` — eligibility/degrade branch coverage
 * (TASK-PC-FE-083 AC-3 / AC-6).
 *
 * Covers:
 *   - Each OrderStatus → expected allowed targets (state machine correctness).
 *   - Terminal statuses have NO transitions.
 *   - Unknown/future status → empty array (fail-safe).
 *   - `getOrdersSectionState` degrade branch (503 / EcommerceUnavailableError).
 *   - `getOrderDetailSectionState` notFound branch (404 ApiError).
 *   - `getOrderDetailSectionState` notEligible branch.
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

// next/navigation redirect mock (server-side)
vi.mock('next/navigation', () => ({
  redirect: vi.fn((url: string) => {
    throw new Error(`REDIRECT:${url}`);
  }),
}));

import {
  allowedTransitions,
} from '@/features/ecommerce-ops/api/order-types';
import {
  getOrdersSectionState,
  getOrderDetailSectionState,
} from '@/features/ecommerce-ops/api/orders-state';
import { ApiError, EcommerceUnavailableError } from '@/shared/api/errors';
import { ACCESS_COOKIE } from '@/shared/lib/session';

// ---------------------------------------------------------------------------
// allowedTransitions() — state machine
// ---------------------------------------------------------------------------

describe('allowedTransitions() — order status state machine', () => {
  it('PENDING → [CONFIRMED, CANCELLED]', () => {
    expect(allowedTransitions('PENDING')).toEqual(['CONFIRMED', 'CANCELLED']);
  });

  it('CONFIRMED → [SHIPPED, CANCELLED]', () => {
    expect(allowedTransitions('CONFIRMED')).toEqual(['SHIPPED', 'CANCELLED']);
  });

  it('SHIPPED → [DELIVERED]', () => {
    expect(allowedTransitions('SHIPPED')).toEqual(['DELIVERED']);
  });

  it('DELIVERED → [] (terminal)', () => {
    expect(allowedTransitions('DELIVERED')).toEqual([]);
  });

  it('CANCELLED → [] (terminal)', () => {
    expect(allowedTransitions('CANCELLED')).toEqual([]);
  });

  it('STUCK_RECOVERY_FAILED → [] (terminal)', () => {
    expect(allowedTransitions('STUCK_RECOVERY_FAILED')).toEqual([]);
  });

  it('unknown/future status → [] (fail-safe, no throw)', () => {
    expect(() => allowedTransitions('FUTURE_STATUS_V9')).not.toThrow();
    expect(allowedTransitions('FUTURE_STATUS_V9')).toEqual([]);
  });
});

// ---------------------------------------------------------------------------
// getOrdersSectionState() — degrade / notEligible branches
// ---------------------------------------------------------------------------

describe('getOrdersSectionState() — eligibility + degrade', () => {
  beforeEach(() => {
    cookieJar.clear();
    vi.unstubAllGlobals();
  });

  it('notEligible=true when eligible=false (no ecommerce call)', async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    const state = await getOrdersSectionState(false);
    expect(state.notEligible).toBe(true);
    expect(state.orders).toBeNull();
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('degraded=true when the gateway returns 503', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    vi.stubGlobal(
      'fetch',
      vi
        .fn()
        .mockResolvedValue(
          new Response(
            JSON.stringify({ code: 'SERVICE_UNAVAILABLE', message: 'down' }),
            { status: 503, headers: { 'Content-Type': 'application/json' } },
          ),
        ),
    );
    const state = await getOrdersSectionState(true);
    expect(state.degraded).toBe(true);
    expect(state.orders).toBeNull();
  });

  it('degraded=true on EcommerceUnavailableError (timeout)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    vi.stubGlobal(
      'fetch',
      vi.fn((_u: string, init?: RequestInit) => {
        return new Promise((_res, rej) => {
          init?.signal?.addEventListener('abort', () => {
            const e = new Error('aborted');
            e.name = 'AbortError';
            rej(e);
          });
        });
      }),
    );
    const state = await getOrdersSectionState(true);
    expect(state.degraded).toBe(true);
  });

  it('forbidden=true on 403', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    vi.stubGlobal(
      'fetch',
      vi
        .fn()
        .mockResolvedValue(
          new Response(
            JSON.stringify({ code: 'FORBIDDEN', message: 'nope' }),
            { status: 403, headers: { 'Content-Type': 'application/json' } },
          ),
        ),
    );
    const state = await getOrdersSectionState(true);
    expect(state.forbidden).toBe(true);
  });

  it('returns orders on happy path', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    const ORDER_LIST = {
      content: [
        {
          orderId: 'ord-1',
          userId: 'u-1',
          status: 'PENDING',
          totalPrice: 15000,
          itemCount: 1,
          firstItemName: 'Tee',
          createdAt: '2026-06-14T00:00:00Z',
        },
      ],
      page: 0,
      size: 20,
      totalElements: 1,
    };
    vi.stubGlobal(
      'fetch',
      vi
        .fn()
        .mockResolvedValue(
          new Response(JSON.stringify(ORDER_LIST), {
            status: 200,
            headers: { 'Content-Type': 'application/json' },
          }),
        ),
    );
    const state = await getOrdersSectionState(true);
    expect(state.orders).not.toBeNull();
    expect(state.orders?.content[0].orderId).toBe('ord-1');
    expect(state.degraded).toBe(false);
    expect(state.notEligible).toBe(false);
  });
});

// ---------------------------------------------------------------------------
// getOrderDetailSectionState() — notFound + degrade + notEligible branches
// ---------------------------------------------------------------------------

describe('getOrderDetailSectionState() — eligibility + degrade + notFound', () => {
  beforeEach(() => {
    cookieJar.clear();
    vi.unstubAllGlobals();
  });

  it('notEligible=true when eligible=false', async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    const state = await getOrderDetailSectionState(false, 'ord-1');
    expect(state.notEligible).toBe(true);
    expect(state.detail).toBeNull();
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('notFound=true on 404 ApiError', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    vi.stubGlobal(
      'fetch',
      vi
        .fn()
        .mockResolvedValue(
          new Response(
            JSON.stringify({ code: 'ORDER_NOT_FOUND', message: 'gone' }),
            { status: 404, headers: { 'Content-Type': 'application/json' } },
          ),
        ),
    );
    const state = await getOrderDetailSectionState(true, 'nope');
    expect(state.notFound).toBe(true);
    expect(state.detail).toBeNull();
  });

  it('degraded=true on 503', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    vi.stubGlobal(
      'fetch',
      vi
        .fn()
        .mockResolvedValue(
          new Response(
            JSON.stringify({ code: 'SERVICE_UNAVAILABLE', message: 'down' }),
            { status: 503, headers: { 'Content-Type': 'application/json' } },
          ),
        ),
    );
    const state = await getOrderDetailSectionState(true, 'ord-1');
    expect(state.degraded).toBe(true);
    expect(state.detail).toBeNull();
  });
});
