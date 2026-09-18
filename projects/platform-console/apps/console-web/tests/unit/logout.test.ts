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
  // TASK-MONO-358 — see auth-routes.test.ts: the post-logout target is built
  // from the runtime-resolvable origin, so the mock must export it.
  publicOrigin: (e: {
    CONSOLE_PUBLIC_ORIGIN?: string;
    NEXT_PUBLIC_APP_URL: string;
  }) => e.CONSOLE_PUBLIC_ORIGIN ?? e.NEXT_PUBLIC_APP_URL,
}));

import { POST as logoutPOST } from '@/app/api/auth/logout/route';
import {
  ACCESS_COOKIE,
  REFRESH_COOKIE,
  OPERATOR_COOKIE,
  TENANT_COOKIE,
  ID_TOKEN_COOKIE,
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

  it('still clears the operator cookie even if IAM revoke fails', async () => {
    cookieJar.set(ACCESS_COOKIE, 'a');
    cookieJar.set(OPERATOR_COOKIE, 'op');
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new Error('revoke down')));

    const res = await logoutPOST();
    expect(res.status).toBe(200);
    expect(cookieDeletes).toContain(OPERATOR_COOKIE);
  });
});

describe('🔴 RP-initiated logout — `id_token` 쿠키가 있으면 IdP 세션까지 끝낸다 (TASK-MONO-705)', () => {
  // 🔴 이 파일에는 **로컬 폴백 칸만** 있었다(«id_token 쿠키가 없으면 <app>/login»).
  //    그 칸은 `logoutUrl` 이 항상 로컬이어도 초록이다 — 즉 «IdP 세션을 끝낸다» 쪽을
  //    무는 단언이 없었다. 2026-09-18 데모 창에서 실측된 피해가 정확히 그 방향이다:
  //    id_token 이 없으면 로그아웃이 로컬 폴백으로 떨어지고 IdP 세션이 살아남아
  //    다음 «로그인» 이 **비밀번호 없이** 들어간다.
  // 🔵 TASK-MONO-705 ⓐ 로 IAM 이 갱신 응답에도 id_token 을 싣게 됐으므로, 이제 이 경로가
  //    로그인 직후뿐 아니라 **갱신 이후에도** 성립해야 한다. 이 칸이 그것을 지킨다.

  it('id_token 쿠키가 있으면 `/connect/logout` 으로 가고 `id_token_hint` 를 싣는다', async () => {
    cookieJar.set(ACCESS_COOKIE, 'a');
    cookieJar.set(ID_TOKEN_COOKIE, 'rotated.id.jwt');
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(null, { status: 200 })));

    const res = await logoutPOST();
    const { logoutUrl } = (await res.json()) as { logoutUrl: string };
    const url = new URL(logoutUrl);

    expect(url.origin + url.pathname).toBe('http://iam.local/connect/logout');
    expect(url.searchParams.get('id_token_hint')).toBe('rotated.id.jwt');
    expect(url.searchParams.get('client_id')).toBe('platform-console-web');
    // post_logout_redirect_uri 는 IAM 클라이언트 등록값과 **정확히** 일치해야 한다
    // (SAS 는 문자열 동등 비교다 — 쿼리가 붙으면 매칭이 깨진다).
    expect(url.searchParams.get('post_logout_redirect_uri')).toBe('http://console.local/login');
    expect(cookieDeletes).toContain(ID_TOKEN_COOKIE);
  });
});
