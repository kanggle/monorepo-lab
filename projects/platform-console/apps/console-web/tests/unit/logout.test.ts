import { describe, it, expect, vi, beforeEach } from 'vitest';

/**
 * `POST /api/auth/logout` — TASK-PC-FE-033 (RP-initiated OIDC logout).
 *
 * The route now clears ALL console session cookies (access / refresh /
 * operator / tenant / assumed / id_token) and returns `200 { logoutUrl }`
 * (NOT 204): the client navigates the browser to `logoutUrl` so the IdP
 * terminates its own session. With no `id_token` cookie there is no
 * `id_token_hint`, so `logoutUrl` falls back to the local `<app>/login`.
 * Cookie clearing is the source of truth for "logged out" even if the GAP
 * revoke fails.
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
    OIDC_ISSUER_URL: 'http://iam.local',
    OIDC_CLIENT_ID: 'platform-console-web',
    OIDC_REDIRECT_URI: 'http://console.local/api/auth/callback',
    OIDC_SCOPE: 'openid profile email tenant.read',
    CONSOLE_REGISTRY_URL: 'http://iam.local/api/admin/console/registry',
    REGISTRY_TIMEOUT_MS: 5000,
    CONSOLE_TOKEN_EXCHANGE_URL: 'http://iam.local/api/admin/auth/token-exchange',
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
  it('clears the operator cookie alongside access/refresh/tenant (200 { logoutUrl })', async () => {
    cookieJar.set(ACCESS_COOKIE, 'a');
    cookieJar.set(REFRESH_COOKIE, 'r');
    cookieJar.set(OPERATOR_COOKIE, 'op');
    cookieJar.set(TENANT_COOKIE, 'wms');
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(null, { status: 200 })));

    const res = await logoutPOST();
    expect(res.status).toBe(200);
    const body = (await res.json()) as { logoutUrl: string };
    // No id_token cookie set → local-only fallback to <app>/login.
    expect(body.logoutUrl).toBe('http://console.local/login');
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
    expect(res.status).toBe(200);
    expect(cookieDeletes).toContain(OPERATOR_COOKIE);
  });
});
