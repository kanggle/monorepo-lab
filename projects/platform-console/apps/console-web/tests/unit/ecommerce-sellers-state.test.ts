import { describe, it, expect, vi, beforeEach } from 'vitest';

/**
 * `features/ecommerce-ops/api/seller-types.ts` — schema passthrough tolerance;
 * `sellers-state.ts` — eligibility/degrade branch coverage
 * (TASK-PC-FE-090 AC).
 *
 * Covers:
 *   - SellerSummarySchema parses without throwing (passthrough tolerance).
 *   - SELLER_STATUS_VALUES const is ACTIVE-only (v1).
 *   - `getSellersSectionState` degrade / notEligible / forbidden branches.
 *   - `getSellerDetailSectionState` notFound / degrade / notEligible branches.
 *   - Happy path: returns sellers / detail on 200.
 *
 * Uses ECOMMERCE_ADMIN_BASE_URL (admin subtree) — same as users/products.
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
  SellerSummarySchema,
  SellerDetailSchema,
  SellerListSchema,
  SELLER_STATUS_VALUES,
} from '@/features/ecommerce-ops/api/seller-types';
import {
  getSellersSectionState,
  getSellerDetailSectionState,
} from '@/features/ecommerce-ops/api/sellers-state';
import { ACCESS_COOKIE } from '@/shared/lib/session';

// ---------------------------------------------------------------------------
// seller-types — schema tolerance
// ---------------------------------------------------------------------------

describe('seller-types — schema passthrough tolerance + ACTIVE-only status', () => {
  it('SELLER_STATUS_VALUES contains only ACTIVE (v1)', () => {
    expect(SELLER_STATUS_VALUES).toContain('ACTIVE');
    expect(SELLER_STATUS_VALUES).toHaveLength(1);
  });

  it('SellerSummarySchema parses with extra unknown fields (passthrough)', () => {
    const result = SellerSummarySchema.parse({
      sellerId: 'acme-corp',
      displayName: 'Acme Corporation',
      status: 'ACTIVE',
      createdAt: '2026-06-14T00:00:00Z',
      futureField: 'extra',
    });
    expect(result.sellerId).toBe('acme-corp');
    expect((result as Record<string, unknown>).futureField).toBe('extra');
  });

  it('SellerDetailSchema parses nullable updatedAt', () => {
    const result = SellerDetailSchema.parse({
      sellerId: 'acme-corp',
      displayName: 'Acme Corporation',
      status: 'ACTIVE',
      createdAt: '2026-06-14T00:00:00Z',
      updatedAt: null,
    });
    expect(result.updatedAt).toBeNull();
  });

  it('SellerDetailSchema tolerates a missing updatedAt (optional)', () => {
    expect(() =>
      SellerDetailSchema.parse({
        sellerId: 'acme-corp',
        displayName: 'Acme Corporation',
        status: 'ACTIVE',
        createdAt: '2026-06-14T00:00:00Z',
      }),
    ).not.toThrow();
  });

  it('SellerListSchema parses a valid list envelope', () => {
    const result = SellerListSchema.parse({
      content: [
        {
          sellerId: 'acme-corp',
          displayName: 'Acme Corporation',
          status: 'ACTIVE',
          createdAt: '2026-06-14T00:00:00Z',
        },
      ],
      page: 0,
      size: 20,
      totalElements: 1,
    });
    expect(result.content).toHaveLength(1);
    expect(result.content[0].sellerId).toBe('acme-corp');
    expect(result.totalElements).toBe(1);
  });
});

// ---------------------------------------------------------------------------
// getSellersSectionState() — degrade / notEligible / forbidden branches
// ---------------------------------------------------------------------------

const SELLER_LIST_RESPONSE = {
  content: [
    {
      sellerId: 'acme-corp',
      displayName: 'Acme Corporation',
      status: 'ACTIVE',
      createdAt: '2026-06-14T00:00:00Z',
    },
  ],
  page: 0,
  size: 20,
  totalElements: 1,
};

function okResponse(body: unknown) {
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

describe('getSellersSectionState() — eligibility + degrade', () => {
  beforeEach(() => {
    cookieJar.clear();
    vi.unstubAllGlobals();
  });

  it('notEligible=true when eligible=false (no ecommerce call)', async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    const state = await getSellersSectionState(false);
    expect(state.notEligible).toBe(true);
    expect(state.sellers).toBeNull();
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('degraded=true when the gateway returns 503', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(errResponse('SERVICE_UNAVAILABLE', 503)));
    const state = await getSellersSectionState(true);
    expect(state.degraded).toBe(true);
    expect(state.sellers).toBeNull();
  });

  it('degraded=true on timeout', async () => {
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
    const state = await getSellersSectionState(true);
    expect(state.degraded).toBe(true);
  });

  it('forbidden=true on 403', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(errResponse('FORBIDDEN', 403)));
    const state = await getSellersSectionState(true);
    expect(state.forbidden).toBe(true);
  });

  it('returns sellers on happy path', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(okResponse(SELLER_LIST_RESPONSE)));
    const state = await getSellersSectionState(true);
    expect(state.sellers).not.toBeNull();
    expect(state.sellers?.content[0].sellerId).toBe('acme-corp');
    expect(state.degraded).toBe(false);
    expect(state.notEligible).toBe(false);
  });
});

// ---------------------------------------------------------------------------
// getSellerDetailSectionState() — notFound + degrade + notEligible
// ---------------------------------------------------------------------------

describe('getSellerDetailSectionState() — eligibility + degrade + notFound', () => {
  beforeEach(() => {
    cookieJar.clear();
    vi.unstubAllGlobals();
  });

  it('notEligible=true when eligible=false', async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    const state = await getSellerDetailSectionState(false, 'acme-corp');
    expect(state.notEligible).toBe(true);
    expect(state.detail).toBeNull();
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('notFound=true on 404 SELLER_NOT_FOUND', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(errResponse('SELLER_NOT_FOUND', 404)));
    const state = await getSellerDetailSectionState(true, 'nope');
    expect(state.notFound).toBe(true);
    expect(state.detail).toBeNull();
  });

  it('degraded=true on 503', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(errResponse('SERVICE_UNAVAILABLE', 503)));
    const state = await getSellerDetailSectionState(true, 'acme-corp');
    expect(state.degraded).toBe(true);
    expect(state.detail).toBeNull();
  });

  it('forbidden=true on 403', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(errResponse('FORBIDDEN', 403)));
    const state = await getSellerDetailSectionState(true, 'acme-corp');
    expect(state.forbidden).toBe(true);
    expect(state.detail).toBeNull();
  });

  it('returns detail on happy path', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    const DETAIL = {
      sellerId: 'acme-corp',
      displayName: 'Acme Corporation',
      status: 'ACTIVE',
      createdAt: '2026-06-14T00:00:00Z',
    };
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(okResponse(DETAIL)));
    const state = await getSellerDetailSectionState(true, 'acme-corp');
    expect(state.detail).not.toBeNull();
    expect(state.detail?.sellerId).toBe('acme-corp');
    expect(state.notFound).toBe(false);
    expect(state.degraded).toBe(false);
  });
});
