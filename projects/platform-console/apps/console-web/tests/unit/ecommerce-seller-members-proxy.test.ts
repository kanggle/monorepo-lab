import { describe, it, expect, vi, beforeEach } from 'vitest';

/**
 * TASK-MONO-752 — same-origin seller MEMBERS proxies (ADR-MONO-079 D5):
 *   GET  /api/ecommerce/sellers/{id}/members     → ECOMMERCE_ADMIN_BASE_URL/sellers/{id}/members
 *   POST /api/ecommerce/sellers/{id}/invitations → ECOMMERCE_ADMIN_BASE_URL/sellers/{id}/invitations (201)
 * Domain-facing IAM OIDC token, no X-Tenant-Id; the invite body is zod-validated before the upstream.
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

import { GET as membersGET } from '@/app/api/ecommerce/sellers/[id]/members/route';
import { POST as invitePOST } from '@/app/api/ecommerce/sellers/[id]/invitations/route';
import { ACCESS_COOKIE, OPERATOR_COOKIE } from '@/shared/lib/session';

function json(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

const ctx = (id = 'acme-corp') => ({ params: Promise.resolve({ id }) });

beforeEach(() => {
  cookieJar.clear();
  vi.unstubAllGlobals();
});

describe('GET /api/ecommerce/sellers/{id}/members', () => {
  it('targets the admin members path with the domain token, no X-Tenant-Id', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    cookieJar.set(OPERATOR_COOKIE, 'OP-MUST-NOT-USE');
    const fetchMock = vi.fn().mockResolvedValue(json({ members: [], invitations: [] }));
    vi.stubGlobal('fetch', fetchMock);

    const res = await membersGET(new Request('http://console.local/x'), ctx());
    expect(res.status).toBe(200);
    expect(await res.json()).toEqual({ members: [], invitations: [] });

    const [url, init] = fetchMock.mock.calls[0];
    expect(String(url)).toBe('http://ecommerce.local/api/admin/sellers/acme-corp/members');
    const h = (init as RequestInit).headers as Record<string, string>;
    expect(h.Authorization).toBe('Bearer GAP-ACCESS');
    expect(h['X-Tenant-Id']).toBeUndefined();
  });

  it('404 SELLER_NOT_FOUND passthrough', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(json({ code: 'SELLER_NOT_FOUND', message: 'e' }, 404)));
    const res = await membersGET(new Request('http://console.local/x'), ctx('nope'));
    expect(res.status).toBe(404);
  });
});

describe('POST /api/ecommerce/sellers/{id}/invitations', () => {
  function post(body: unknown, id = 'acme-corp') {
    return invitePOST(
      new Request('http://console.local/x', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body),
      }),
      ctx(id),
    );
  }

  it('201 with the token; forwards { email } to the admin invitations path', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    const fetchMock = vi.fn().mockResolvedValue(
      json({ invitationId: 'inv-1', email: 'a@b.co', expiresAt: '2026-10-10T00:00:00Z', token: 'T' }, 201),
    );
    vi.stubGlobal('fetch', fetchMock);

    const res = await post({ email: 'a@b.co' });
    expect(res.status).toBe(201);
    expect((await res.json()).token).toBe('T');
    const [url, init] = fetchMock.mock.calls[0];
    expect(String(url)).toBe('http://ecommerce.local/api/admin/sellers/acme-corp/invitations');
    expect((init as RequestInit).method).toBe('POST');
    expect(JSON.parse(String((init as RequestInit).body))).toEqual({ email: 'a@b.co' });
  });

  it('an invalid email → 422 VALIDATION_ERROR (the shared badRequest) before any upstream call', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    const res = await post({ email: 'nope' });
    expect(res.status).toBe(422);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('409 SELLER_NOT_ACTIVE passthrough', async () => {
    cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(json({ code: 'SELLER_NOT_ACTIVE', message: 'e' }, 409)));
    const res = await post({ email: 'a@b.co' });
    expect(res.status).toBe(409);
    expect((await res.json()).code).toBe('SELLER_NOT_ACTIVE');
  });

  it('no IAM session → 401 (no upstream call)', async () => {
    cookieJar.set(OPERATOR_COOKIE, 'OPERATOR-ONLY-HALF-SESSION');
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    const res = await post({ email: 'a@b.co' });
    expect(res.status).toBe(401);
    expect(fetchMock).not.toHaveBeenCalled();
  });
});
