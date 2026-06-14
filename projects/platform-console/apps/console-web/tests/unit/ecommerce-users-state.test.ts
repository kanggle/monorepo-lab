import { describe, it, expect, vi, beforeEach } from 'vitest';

/**
 * `features/ecommerce-ops/api/user-types.ts` — schema passthrough tolerance +
 * nullable fields; `users-state.ts` — eligibility/degrade branch coverage
 * (TASK-PC-FE-084 AC-3 / AC-6).
 *
 * Covers:
 *   - UserSummarySchema parses without throwing (passthrough tolerance).
 *   - nullable fields (nickname, phone, profileImageUrl) parse correctly.
 *   - USER_STATUS_VALUES const is complete and correct.
 *   - `getUsersSectionState` degrade branch (503 / EcommerceUnavailableError).
 *   - `getUserDetailSectionState` notFound branch (404 ApiError).
 *   - `getUserDetailSectionState` notEligible branch.
 *   - `getUserDetailSectionState` forbidden branch.
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
  UserSummarySchema,
  UserDetailSchema,
  UserListSchema,
  USER_STATUS_VALUES,
} from '@/features/ecommerce-ops/api/user-types';
import {
  getUsersSectionState,
  getUserDetailSectionState,
} from '@/features/ecommerce-ops/api/users-state';
import { ACCESS_COOKIE } from '@/shared/lib/session';

// ---------------------------------------------------------------------------
// user-types — schema tolerance
// ---------------------------------------------------------------------------

describe('user-types — schema passthrough tolerance + nullable fields', () => {
  it('USER_STATUS_VALUES contains ACTIVE, SUSPENDED, WITHDRAWN', () => {
    expect(USER_STATUS_VALUES).toContain('ACTIVE');
    expect(USER_STATUS_VALUES).toContain('SUSPENDED');
    expect(USER_STATUS_VALUES).toContain('WITHDRAWN');
    expect(USER_STATUS_VALUES).toHaveLength(3);
  });

  it('UserSummarySchema parses with null nickname without throwing', () => {
    const result = UserSummarySchema.parse({
      userId: 'u-1',
      email: 'alice@example.com',
      name: '홍길동',
      nickname: null,
      status: 'ACTIVE',
      createdAt: '2026-06-14T00:00:00Z',
    });
    expect(result.nickname).toBeNull();
  });

  it('UserSummarySchema parses with extra unknown fields (passthrough)', () => {
    const result = UserSummarySchema.parse({
      userId: 'u-1',
      email: 'alice@example.com',
      name: '홍길동',
      status: 'ACTIVE',
      createdAt: '2026-06-14T00:00:00Z',
      futureField: 'extra',
    });
    expect((result as Record<string, unknown>).futureField).toBe('extra');
  });

  it('UserDetailSchema parses nullable phone and profileImageUrl', () => {
    const result = UserDetailSchema.parse({
      userId: 'u-1',
      email: 'alice@example.com',
      name: '홍길동',
      status: 'ACTIVE',
      createdAt: '2026-06-14T00:00:00Z',
      phone: null,
      profileImageUrl: null,
    });
    expect(result.phone).toBeNull();
    expect(result.profileImageUrl).toBeNull();
  });

  it('UserDetailSchema tolerates an unknown/future status (no throw)', () => {
    expect(() =>
      UserDetailSchema.parse({
        userId: 'u-1',
        email: 'test@example.com',
        name: 'Test',
        status: 'FUTURE_STATUS_V9',
        createdAt: '2026-06-14T00:00:00Z',
      }),
    ).not.toThrow();
  });

  it('UserListSchema parses a valid list envelope', () => {
    const result = UserListSchema.parse({
      content: [
        {
          userId: 'u-1',
          email: 'alice@example.com',
          name: '홍길동',
          status: 'ACTIVE',
          createdAt: '2026-06-14T00:00:00Z',
        },
      ],
      page: 0,
      size: 20,
      totalElements: 1,
    });
    expect(result.content).toHaveLength(1);
    expect(result.totalElements).toBe(1);
  });
});

// ---------------------------------------------------------------------------
// getUsersSectionState() — degrade / notEligible branches
// ---------------------------------------------------------------------------

describe('getUsersSectionState() — eligibility + degrade', () => {
  beforeEach(() => {
    cookieJar.clear();
    vi.unstubAllGlobals();
  });

  it('notEligible=true when eligible=false (no ecommerce call)', async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    const state = await getUsersSectionState(false);
    expect(state.notEligible).toBe(true);
    expect(state.users).toBeNull();
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
    const state = await getUsersSectionState(true);
    expect(state.degraded).toBe(true);
    expect(state.users).toBeNull();
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
    const state = await getUsersSectionState(true);
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
    const state = await getUsersSectionState(true);
    expect(state.forbidden).toBe(true);
  });

  it('returns users on happy path', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    const USER_LIST = {
      content: [
        {
          userId: 'u-1',
          email: 'alice@example.com',
          name: '홍길동',
          status: 'ACTIVE',
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
          new Response(JSON.stringify(USER_LIST), {
            status: 200,
            headers: { 'Content-Type': 'application/json' },
          }),
        ),
    );
    const state = await getUsersSectionState(true);
    expect(state.users).not.toBeNull();
    expect(state.users?.content[0].userId).toBe('u-1');
    expect(state.degraded).toBe(false);
    expect(state.notEligible).toBe(false);
  });
});

// ---------------------------------------------------------------------------
// getUserDetailSectionState() — notFound + degrade + notEligible branches
// ---------------------------------------------------------------------------

describe('getUserDetailSectionState() — eligibility + degrade + notFound', () => {
  beforeEach(() => {
    cookieJar.clear();
    vi.unstubAllGlobals();
  });

  it('notEligible=true when eligible=false', async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    const state = await getUserDetailSectionState(false, 'u-1');
    expect(state.notEligible).toBe(true);
    expect(state.detail).toBeNull();
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('notFound=true on 404 USER_PROFILE_NOT_FOUND', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    vi.stubGlobal(
      'fetch',
      vi
        .fn()
        .mockResolvedValue(
          new Response(
            JSON.stringify({ code: 'USER_PROFILE_NOT_FOUND', message: 'gone' }),
            { status: 404, headers: { 'Content-Type': 'application/json' } },
          ),
        ),
    );
    const state = await getUserDetailSectionState(true, 'nope');
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
    const state = await getUserDetailSectionState(true, 'u-1');
    expect(state.degraded).toBe(true);
    expect(state.detail).toBeNull();
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
    const state = await getUserDetailSectionState(true, 'u-1');
    expect(state.forbidden).toBe(true);
    expect(state.detail).toBeNull();
  });
});
