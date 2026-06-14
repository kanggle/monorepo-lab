import { describe, it, expect, vi, beforeEach } from 'vitest';

/**
 * `features/ecommerce-ops/api/notifications-state.ts` — eligibility/degrade
 * branch coverage (TASK-PC-FE-089 AC / § 2.4.10.4).
 *
 * Covers:
 *   - `getNotificationsSectionState` notEligible branch.
 *   - `getNotificationsSectionState` forbidden branch (403).
 *   - `getNotificationsSectionState` degrade branch (503 / EcommerceUnavailableError).
 *   - `getNotificationsSectionState` happy path.
 *   - `getNotificationDetailSectionState` notFound branch (404 TEMPLATE_NOT_FOUND).
 *   - `getNotificationDetailSectionState` happy path.
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
  getNotificationsSectionState,
  getNotificationDetailSectionState,
} from '@/features/ecommerce-ops/api/notifications-state';
import { ApiError, EcommerceUnavailableError } from '@/shared/api/errors';
import { ACCESS_COOKIE } from '@/shared/lib/session';

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}
function ecomError(code: string, status: number) {
  return new Response(JSON.stringify({ code, message: 'e' }), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

const TEMPLATE_LIST = {
  content: [
    {
      templateId: 'tmpl-1',
      type: 'ORDER_PLACED',
      channel: 'EMAIL',
      subject: '주문이 완료되었습니다',
      createdAt: '2026-06-14T00:00:00Z',
    },
  ],
  page: 0,
  size: 20,
  totalElements: 1,
};

const TEMPLATE_DETAIL = {
  templateId: 'tmpl-1',
  type: 'ORDER_PLACED',
  channel: 'EMAIL',
  subject: '주문이 완료되었습니다',
  body: '안녕하세요 {{name}}님.',
  createdAt: '2026-06-14T00:00:00Z',
  updatedAt: '2026-06-14T00:00:00Z',
};

beforeEach(() => {
  cookieJar.clear();
  vi.unstubAllGlobals();
});

describe('getNotificationsSectionState — section eligibility + resilience', () => {
  it('notEligible=true when eligible=false (no upstream call)', async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    const state = await getNotificationsSectionState(false);
    expect(state.notEligible).toBe(true);
    expect(state.templates).toBeNull();
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('happy path: returns templates list on eligible + 200', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse(TEMPLATE_LIST)));
    const state = await getNotificationsSectionState(true);
    expect(state.notEligible).toBe(false);
    expect(state.degraded).toBe(false);
    expect(state.templates?.content).toHaveLength(1);
    expect(state.templates?.content[0].templateId).toBe('tmpl-1');
  });

  it('degraded=true on EcommerceUnavailableError (503)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(ecomError('SERVICE_UNAVAILABLE', 503)));
    const state = await getNotificationsSectionState(true);
    expect(state.degraded).toBe(true);
    expect(state.templates).toBeNull();
  });

  it('forbidden=true on 403', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(ecomError('ACCESS_DENIED', 403)));
    const state = await getNotificationsSectionState(true);
    expect(state.forbidden).toBe(true);
    expect(state.degraded).toBe(false);
  });

  it('401 → redirect(/login) (whole-session re-login)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(ecomError('UNAUTHORIZED', 401)));
    const err = await getNotificationsSectionState(true).catch((e) => e);
    expect((err as Error).message).toContain('REDIRECT:/login');
  });
});

describe('getNotificationDetailSectionState — detail page resilience', () => {
  it('notEligible=true when eligible=false', async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    const state = await getNotificationDetailSectionState(false, 'tmpl-1');
    expect(state.notEligible).toBe(true);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('happy path: returns detail on eligible + 200', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse(TEMPLATE_DETAIL)));
    const state = await getNotificationDetailSectionState(true, 'tmpl-1');
    expect(state.detail?.templateId).toBe('tmpl-1');
    expect(state.detail?.body).toBeTruthy();
    expect(state.notFound).toBe(false);
  });

  it('notFound=true on 404 TEMPLATE_NOT_FOUND', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(ecomError('TEMPLATE_NOT_FOUND', 404)),
    );
    const state = await getNotificationDetailSectionState(true, 'nope');
    expect(state.notFound).toBe(true);
    expect(state.detail).toBeNull();
  });

  it('degraded=true on 503', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(ecomError('SERVICE_UNAVAILABLE', 503)),
    );
    const state = await getNotificationDetailSectionState(true, 'tmpl-1');
    expect(state.degraded).toBe(true);
    expect(state.detail).toBeNull();
  });
});

describe('getNotificationDetailSectionState — direct error injection', () => {
  it('degraded=true on EcommerceUnavailableError thrown from api layer', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    vi.stubGlobal(
      'fetch',
      vi.fn().mockRejectedValue(
        new EcommerceUnavailableError('timeout', 'TIMEOUT', 'timed out'),
      ),
    );
    const state = await getNotificationDetailSectionState(true, 'tmpl-1');
    expect(state.degraded).toBe(true);
  });

  it('forbidden=true on ApiError(403)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(ecomError('ACCESS_DENIED', 403)),
    );
    const state = await getNotificationDetailSectionState(true, 'tmpl-1');
    expect(state.forbidden).toBe(true);
    expect(state.degraded).toBe(false);
  });
});
