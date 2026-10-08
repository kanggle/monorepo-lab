import { describe, it, expect, vi, beforeEach } from 'vitest';

/**
 * TASK-MONO-777 — same-origin IAM operator e-mail lookup proxy
 * (`GET /api/operators/lookup` → `GET /api/admin/operators/lookup`,
 * console contract § 2.4.8 «Account selection»):
 *   - forwards `email` + the ACTIVE tenant as `tenantId` with the exchanged
 *     operator token (the IAM `/api/admin/**` credential);
 *   - passes the producer's `{ content }` through;
 *   - 🔴 AC-3: a producer 403 stays 403 — never rewritten to 401 (which the
 *     browser client would turn into refresh → `/login`);
 *   - a blank e-mail is refused without an upstream call.
 */

const cookieJar = new Map<string, string>();
vi.mock('next/headers', () => ({
  cookies: async () => ({
    get: (n: string) => (cookieJar.has(n) ? { value: cookieJar.get(n)! } : undefined),
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
    ACCOUNTS_TIMEOUT_MS: 50,
    AUDIT_TIMEOUT_MS: 50,
    OPERATORS_TIMEOUT_MS: 50,
    LOG_LEVEL: 'info' as const,
    NEXT_PUBLIC_APP_URL: 'http://console.local',
  },
}));
vi.mock('@/shared/config/env', () => ({
  clientEnv: { NEXT_PUBLIC_APP_URL: ENV.NEXT_PUBLIC_APP_URL },
  getServerEnv: () => ENV,
}));

import { GET } from '@/app/api/operators/lookup/route';
import { OPERATOR_COOKIE, TENANT_COOKIE } from '@/shared/lib/session';

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

beforeEach(() => {
  cookieJar.clear();
  vi.unstubAllGlobals();
  cookieJar.set(OPERATOR_COOKIE, 'OP');
  cookieJar.set(TENANT_COOKIE, 'demo-corp');
});

describe('GET /api/operators/lookup (TASK-MONO-777)', () => {
  it('forwards email + active tenant with the operator token and passes the content through', async () => {
    const hit = { accountId: 'acc-ad03', displayName: 'Demo Operator', tenantId: 'demo-corp' };
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse({ content: [hit] }));
    vi.stubGlobal('fetch', fetchMock);

    const res = await GET(
      new Request('http://console.local/api/operators/lookup?email=%20demo%2Btag%40demo.com%20'),
    );
    expect(res.status).toBe(200);
    expect(await res.json()).toEqual({ content: [hit] });

    const [url, init] = fetchMock.mock.calls[0];
    const u = new URL(String(url));
    expect(u.pathname).toBe('/api/admin/operators/lookup');
    expect(u.searchParams.get('email')).toBe('demo+tag@demo.com');
    expect(u.searchParams.get('tenantId')).toBe('demo-corp');
    expect((init.headers as Record<string, string>).Authorization).toBe('Bearer OP');
  });

  it('🔴 AC-3 — a producer 403 stays 403 (never rewritten to 401)', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(jsonResponse({ code: 'PERMISSION_DENIED', message: 'x' }, 403)),
    );
    const res = await GET(new Request('http://console.local/api/operators/lookup?email=a@b.c'));
    expect(res.status).toBe(403);
    expect((await res.json()).code).toBe('PERMISSION_DENIED');
  });

  it('a blank email → 400 without calling IAM', async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    const res = await GET(new Request('http://console.local/api/operators/lookup?email=%20%20'));
    expect(res.status).toBe(400);
    expect(fetchMock).not.toHaveBeenCalled();
  });
});
