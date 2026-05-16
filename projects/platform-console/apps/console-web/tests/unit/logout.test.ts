import { describe, it, expect, vi, beforeEach } from 'vitest';

/**
 * `POST /api/auth/logout` must clear the operator cookie too (in addition to
 * the GAP access/refresh/tenant cookies) — TASK-PC-FE-002a. Cookie clearing
 * is the source of truth for "logged out" even if GAP revoke fails.
 */

const cookieJar = new Map<string, string>();
const cookieDeletes: string[] = [];
vi.mock('next/headers', () => ({
  cookies: async () => ({
    get: (n: string) =>
      cookieJar.has(n) ? { value: cookieJar.get(n)! } : undefined,
    delete: (n: string) => {
      cookieJar.delete(n);
      cookieDeletes.push(n);
    },
  }),
}));

const { ENV } = vi.hoisted(() => ({
  ENV: {
    OIDC_ISSUER_URL: 'http://gap.local',
    OIDC_CLIENT_ID: 'platform-console-web',
    OIDC_REDIRECT_URI: 'http://console.local/api/auth/callback',
    OIDC_SCOPE: 'openid profile email tenant.read',
    CONSOLE_REGISTRY_URL: 'http://gap.local/api/admin/console/registry',
    REGISTRY_TIMEOUT_MS: 5000,
    CONSOLE_TOKEN_EXCHANGE_URL: 'http://gap.local/api/admin/auth/token-exchange',
    TOKEN_EXCHANGE_TIMEOUT_MS: 5000,
    LOG_LEVEL: 'info' as const,
    NEXT_PUBLIC_APP_URL: 'http://console.local',
  },
}));
vi.mock('@/shared/config/env', () => ({
  clientEnv: { NEXT_PUBLIC_APP_URL: ENV.NEXT_PUBLIC_APP_URL },
  getServerEnv: () => ENV,
}));

import { POST as logoutPOST } from '@/app/api/auth/logout/route';
import {
  ACCESS_COOKIE,
  REFRESH_COOKIE,
  OPERATOR_COOKIE,
  TENANT_COOKIE,
} from '@/shared/lib/session';

beforeEach(() => {
  cookieJar.clear();
  cookieDeletes.length = 0;
  vi.unstubAllGlobals();
});

describe('POST /api/auth/logout', () => {
  it('clears the operator cookie alongside access/refresh/tenant (204)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'a');
    cookieJar.set(REFRESH_COOKIE, 'r');
    cookieJar.set(OPERATOR_COOKIE, 'op');
    cookieJar.set(TENANT_COOKIE, 'wms');
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(null, { status: 200 })));

    const res = await logoutPOST();
    expect(res.status).toBe(204);
    expect(cookieDeletes).toContain(ACCESS_COOKIE);
    expect(cookieDeletes).toContain(REFRESH_COOKIE);
    expect(cookieDeletes).toContain(OPERATOR_COOKIE);
    expect(cookieDeletes).toContain(TENANT_COOKIE);
  });

  it('still clears the operator cookie even if GAP revoke fails', async () => {
    cookieJar.set(ACCESS_COOKIE, 'a');
    cookieJar.set(OPERATOR_COOKIE, 'op');
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new Error('revoke down')));

    const res = await logoutPOST();
    expect(res.status).toBe(204);
    expect(cookieDeletes).toContain(OPERATOR_COOKIE);
  });
});
