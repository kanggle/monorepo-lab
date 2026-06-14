import { describe, it, expect, vi, beforeEach } from 'vitest';

/**
 * Same-origin ecommerce-ops users proxy route handlers (TASK-PC-FE-084 —
 * § 2.4.10 — READ-ONLY surface):
 *   - GET list proxy: domain-facing IAM OIDC token attached server-side
 *     (NOT the operator token); NO X-Tenant-Id; query params forwarded.
 *   - GET detail proxy: same auth model; 404 passthrough as inline actionable.
 *   - no IAM session → 401 (no upstream call).
 *   - 503 → 503 (section degrades only).
 *   - NO POST/PATCH/DELETE handlers exist on this surface.
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

import { GET as userListGET } from '@/app/api/ecommerce/users/route';
import { GET as userDetailGET } from '@/app/api/ecommerce/users/[id]/route';
import { ACCESS_COOKIE, OPERATOR_COOKIE } from '@/shared/lib/session';

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}
function ecomError(code: string, status: number) {
  return new Response(JSON.stringify({ code, message: 'e', timestamp: 't' }), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

const USER_LIST = {
  content: [
    {
      userId: 'u-1',
      email: 'alice@example.com',
      name: '홍길동',
      nickname: 'alice',
      status: 'ACTIVE',
      createdAt: '2026-06-14T00:00:00Z',
    },
  ],
  page: 0,
  size: 20,
  totalElements: 1,
};

const USER_DETAIL = {
  userId: 'u-1',
  email: 'alice@example.com',
  name: '홍길동',
  nickname: 'alice',
  status: 'ACTIVE',
  createdAt: '2026-06-14T00:00:00Z',
  phone: null,
  profileImageUrl: null,
  updatedAt: '2026-06-14T00:00:00Z',
};

beforeEach(() => {
  cookieJar.clear();
  vi.unstubAllGlobals();
});

describe('GET /api/ecommerce/users (list proxy)', () => {
  it('attaches the domain-facing token (NOT the operator token), NO X-Tenant-Id', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    cookieJar.set(OPERATOR_COOKIE, 'OP-MUST-NOT-USE');
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(USER_LIST));
    vi.stubGlobal('fetch', fetchMock);

    const res = await userListGET(
      new Request('http://console.local/api/ecommerce/users?page=0&size=20'),
    );
    expect(res.status).toBe(200);
    const data = await res.json();
    expect(data.content[0].userId).toBe('u-1');

    const init = fetchMock.mock.calls[0][1] as RequestInit;
    const h = init.headers as Record<string, string>;
    expect(h.Authorization).toBe('Bearer GAP-ACCESS');
    expect(h.Authorization).not.toContain('OP-MUST-NOT-USE');
    expect(h['X-Tenant-Id']).toBeUndefined();
  });

  it('forwards status and email query params to the upstream', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(USER_LIST));
    vi.stubGlobal('fetch', fetchMock);

    await userListGET(
      new Request(
        'http://console.local/api/ecommerce/users?status=ACTIVE&email=alice%40example.com&page=0&size=20',
      ),
    );

    const [url] = fetchMock.mock.calls[0];
    const u = new URL(String(url));
    expect(u.searchParams.get('status')).toBe('ACTIVE');
    expect(u.searchParams.get('email')).toBe('alice@example.com');
  });

  it('no IAM session → 401 (no upstream call)', async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    const res = await userListGET(
      new Request('http://console.local/api/ecommerce/users'),
    );
    expect(res.status).toBe(401);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('503 → 503 (section degrades only)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(ecomError('SERVICE_UNAVAILABLE', 503)),
    );
    const res = await userListGET(
      new Request('http://console.local/api/ecommerce/users'),
    );
    expect(res.status).toBe(503);
  });
});

describe('GET /api/ecommerce/users/{id} (detail proxy)', () => {
  it('attaches the domain-facing token (NOT the operator token), NO X-Tenant-Id', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    cookieJar.set(OPERATOR_COOKIE, 'OP-MUST-NOT-USE');
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(USER_DETAIL));
    vi.stubGlobal('fetch', fetchMock);

    const res = await userDetailGET(
      new Request('http://console.local/api/ecommerce/users/u-1'),
      { params: Promise.resolve({ id: 'u-1' }) },
    );
    expect(res.status).toBe(200);

    const init = fetchMock.mock.calls[0][1] as RequestInit;
    const h = init.headers as Record<string, string>;
    expect(h.Authorization).toBe('Bearer GAP-ACCESS');
    expect(h.Authorization).not.toContain('OP-MUST-NOT-USE');
    expect(h['X-Tenant-Id']).toBeUndefined();
  });

  it('no IAM session → 401 (no upstream call)', async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    const res = await userDetailGET(
      new Request('http://console.local/api/ecommerce/users/u-1'),
      { params: Promise.resolve({ id: 'u-1' }) },
    );
    expect(res.status).toBe(401);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('404 USER_PROFILE_NOT_FOUND → 404 passthrough', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(ecomError('USER_PROFILE_NOT_FOUND', 404)),
    );
    const res = await userDetailGET(
      new Request('http://console.local/api/ecommerce/users/nope'),
      { params: Promise.resolve({ id: 'nope' }) },
    );
    expect(res.status).toBe(404);
    const body = await res.json();
    expect(body.code).toBe('USER_PROFILE_NOT_FOUND');
  });

  it('503 → 503 (section degrades only)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(ecomError('SERVICE_UNAVAILABLE', 503)),
    );
    const res = await userDetailGET(
      new Request('http://console.local/api/ecommerce/users/u-1'),
      { params: Promise.resolve({ id: 'u-1' }) },
    );
    expect(res.status).toBe(503);
  });
});
