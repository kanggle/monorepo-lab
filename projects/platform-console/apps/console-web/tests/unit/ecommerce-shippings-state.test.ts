import { describe, it, expect, vi, beforeEach } from 'vitest';

/**
 * `features/ecommerce-ops/api/shipping-types.ts` — `allowedNextStatus()` linear
 * state machine + `shippings-state.ts` — eligibility/degrade branch coverage
 * (TASK-PC-FE-088 AC / § 2.4.10.3).
 *
 * Covers:
 *   - Each ShippingStatus → single allowed next status (linear machine).
 *   - Terminal status (DELIVERED) → null.
 *   - Unknown/future status → null (fail-safe).
 *   - `getShippingsSectionState` degrade branch (503 / EcommerceUnavailableError).
 *   - `getShippingsSectionState` notEligible branch.
 *   - `getShippingsSectionState` forbidden branch (403).
 *   - `getShippingsSectionState` happy path.
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
  allowedNextStatus,
  ShippingSummarySchema,
  ShippingSchema,
  UpdateShippingStatusBodySchema,
} from '@/features/ecommerce-ops/api/shipping-types';
import { getShippingsSectionState } from '@/features/ecommerce-ops/api/shippings-state';
import { ApiError, EcommerceUnavailableError } from '@/shared/api/errors';
import { ACCESS_COOKIE } from '@/shared/lib/session';

// ---------------------------------------------------------------------------
// allowedNextStatus() — linear state machine
// ---------------------------------------------------------------------------

describe('allowedNextStatus() — shipping status linear state machine', () => {
  it('PREPARING → SHIPPED (single successor)', () => {
    expect(allowedNextStatus('PREPARING')).toBe('SHIPPED');
  });

  it('SHIPPED → IN_TRANSIT (single successor)', () => {
    expect(allowedNextStatus('SHIPPED')).toBe('IN_TRANSIT');
  });

  it('IN_TRANSIT → DELIVERED (single successor)', () => {
    expect(allowedNextStatus('IN_TRANSIT')).toBe('DELIVERED');
  });

  it('DELIVERED → null (terminal)', () => {
    expect(allowedNextStatus('DELIVERED')).toBeNull();
  });

  it('unknown/future status → null (fail-safe, no throw)', () => {
    expect(() => allowedNextStatus('FUTURE_STATUS_V9')).not.toThrow();
    expect(allowedNextStatus('FUTURE_STATUS_V9')).toBeNull();
  });

  it('does not allow backward transitions (SHIPPED → PREPARING is null)', () => {
    // PREPARING is not in the forward map from SHIPPED
    expect(allowedNextStatus('DELIVERED')).toBeNull();
    expect(allowedNextStatus('IN_TRANSIT')).not.toBe('PREPARING');
    expect(allowedNextStatus('IN_TRANSIT')).not.toBe('SHIPPED');
  });
});

// ---------------------------------------------------------------------------
// wmsRouted / deductWmsInventory schema (TASK-MONO-305 — ADR-MONO-022 D4 v2(c))
// ---------------------------------------------------------------------------

describe('shipping schemas — wmsRouted read + deductWmsInventory write', () => {
  const summaryBase = {
    shippingId: 'ship-1',
    orderId: 'ord-1',
    status: 'PREPARING',
    createdAt: '2026-06-14T00:00:00Z',
  };

  it('ShippingSummary defaults wmsRouted to false when absent (no degrade)', () => {
    const parsed = ShippingSummarySchema.parse(summaryBase);
    expect(parsed.wmsRouted).toBe(false);
  });

  it('ShippingSummary carries wmsRouted=true when the producer sends it', () => {
    const parsed = ShippingSummarySchema.parse({
      ...summaryBase,
      wmsRouted: true,
    });
    expect(parsed.wmsRouted).toBe(true);
  });

  it('Shipping (detail) defaults wmsRouted to false when absent', () => {
    const parsed = ShippingSchema.parse(summaryBase);
    expect(parsed.wmsRouted).toBe(false);
  });

  it('UpdateShippingStatusBody accepts deductWmsInventory', () => {
    const parsed = UpdateShippingStatusBodySchema.parse({
      status: 'SHIPPED',
      carrier: 'CJ대한통운',
      trackingNumber: 'TRK-001',
      deductWmsInventory: true,
    });
    expect(parsed.deductWmsInventory).toBe(true);
  });

  it('UpdateShippingStatusBody leaves deductWmsInventory undefined when omitted', () => {
    const parsed = UpdateShippingStatusBodySchema.parse({ status: 'IN_TRANSIT' });
    expect(parsed.deductWmsInventory).toBeUndefined();
  });
});

// ---------------------------------------------------------------------------
// getShippingsSectionState() — eligibility + degrade branches
// ---------------------------------------------------------------------------

describe('getShippingsSectionState() — eligibility + degrade', () => {
  beforeEach(() => {
    cookieJar.clear();
    vi.unstubAllGlobals();
  });

  it('notEligible=true when eligible=false (no ecommerce call)', async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    const state = await getShippingsSectionState(false);
    expect(state.notEligible).toBe(true);
    expect(state.shippings).toBeNull();
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
    const state = await getShippingsSectionState(true);
    expect(state.degraded).toBe(true);
    expect(state.shippings).toBeNull();
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
    const state = await getShippingsSectionState(true);
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
    const state = await getShippingsSectionState(true);
    expect(state.forbidden).toBe(true);
  });

  it('returns shippings on happy path', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    const SHIPPING_LIST = {
      content: [
        {
          shippingId: 'ship-1',
          orderId: 'ord-1',
          userId: 'u-1',
          status: 'PREPARING',
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
          new Response(JSON.stringify(SHIPPING_LIST), {
            status: 200,
            headers: { 'Content-Type': 'application/json' },
          }),
        ),
    );
    const state = await getShippingsSectionState(true);
    expect(state.shippings).not.toBeNull();
    expect(state.shippings?.content[0].shippingId).toBe('ship-1');
    expect(state.degraded).toBe(false);
    expect(state.notEligible).toBe(false);
    expect(state.forbidden).toBe(false);
  });
});

// ---------------------------------------------------------------------------
// EcommerceUnavailableError type guard (used by state mappers)
// ---------------------------------------------------------------------------

describe('EcommerceUnavailableError classification', () => {
  it('is distinguishable from ApiError', () => {
    const api = new ApiError(503, 'SVC_UNAVAIL', 'down');
    const unavail = new EcommerceUnavailableError('downstream', 'SVC_UNAVAIL', 'down');
    expect(unavail).toBeInstanceOf(EcommerceUnavailableError);
    expect(api).toBeInstanceOf(ApiError);
    expect(api).not.toBeInstanceOf(EcommerceUnavailableError);
  });
});
