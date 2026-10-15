import { describe, it, expect, vi, beforeEach } from 'vitest';

/**
 * TASK-MONO-773 S6 — console-integration-contract § 2.6.4 «The «logged in · not an operator» shell state».
 *
 * 무엇을 재는가 — 비운영자 표식 쿠키 `console_non_operator` 의 생애:
 *   ① **세우는 곳은 셋뿐이고, 교환 `401` 일 때뿐이다** — 콜백 · `GET /api/auth/refresh` · `POST /api/auth/refresh`.
 *      `403 MFA_REQUIRED` · `5xx` 는 절대 세우지 않는다(대조군 — 계약 «Only a `401` means not an operator»).
 *   ② 콜백 · GET 갱신은 허용 목록(`/new-tenant` · `/guide`) 안의 목적지면 그리로, 밖이면 `/new-tenant` 로 보낸다
 *      (옛 `/onboarding` 이 아니다 — AC-5).
 *   ③ 브라우저 `POST` 갱신의 교환 `401` = `403 NOT_AN_OPERATOR` · IAM 쿠키 **유지**(옛: `401` + 세션 전체 삭제).
 *   ④ **지우는 곳**: 교환 성공(콜백 · 갱신) · 로그아웃(`clearFullSession`).
 *   ⑤ 773 S5 가 IAM 의 `operator_eligibility_unavailable` 상수를 지웠다 — 콘솔도 그 값을 따로 읽지 않는다:
 *      같은 본문은 이제 «그 밖의 `invalid_grant`» 와 같은 길이다(콜백 `token_exchange_failed` · 갱신 회전 경합 재시도).
 *
 * 🔴 실제 라우트 핸들러를 import 해서 부른다 — 로직 사본을 재지 않는다.
 * 🔵 표식 이름은 상수가 아니라 **글자 그대로** 쓴다 — 계약이 정한 이름이 시험의 대상이다.
 */

const MARKER = 'console_non_operator';

const cookieJar = new Map<string, { value: string; opts?: Record<string, unknown> }>();
const cookieDeletes: string[] = [];
const cookiesMock = {
  get: (name: string) => {
    const e = cookieJar.get(name);
    return e ? { value: e.value } : undefined;
  },
  set: (name: string, value: string, opts?: Record<string, unknown>) => {
    cookieJar.set(name, { value, opts });
  },
  delete: (name: string) => {
    cookieJar.delete(name);
    cookieDeletes.push(name);
  },
};
vi.mock('next/headers', () => ({ cookies: async () => cookiesMock }));

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
  publicOrigin: (e: { CONSOLE_PUBLIC_ORIGIN?: string; NEXT_PUBLIC_APP_URL: string }) =>
    e.CONSOLE_PUBLIC_ORIGIN ?? e.NEXT_PUBLIC_APP_URL,
}));
vi.mock('@/shared/lib/session-refresh', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/shared/lib/session-refresh')>();
  return { ...actual, REFRESH_RACE_GRACE_MS: 30 };
});

import { GET as callbackGET } from '@/app/api/auth/callback/route';
import { GET as refreshGET, POST as refreshPOST } from '@/app/api/auth/refresh/route';
import { POST as logoutPOST } from '@/app/api/auth/logout/route';
import {
  ACCESS_COOKIE,
  REFRESH_COOKIE,
  OPERATOR_COOKIE,
  ID_TOKEN_COOKIE,
  PKCE_VERIFIER_COOKIE,
  OAUTH_STATE_COOKIE,
} from '@/shared/lib/session';

const ORIGIN = 'http://console.local';

function json(body: unknown, status: number): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

const TOKENS = {
  access_token: 'iam.acc',
  token_type: 'Bearer',
  expires_in: 1800,
  refresh_token: 'iam.ref',
  id_token: 'iam.id',
};

/** IAM token endpoint answers `tokenReply`; the operator exchange answers `exchangeReply`. */
function stubIam(exchangeReply: () => Response, tokenReply: () => Response = () => json(TOKENS, 200)) {
  const fetchMock = vi.fn((url: string) => {
    const u = String(url);
    if (u.includes('/api/admin/auth/token-exchange')) return Promise.resolve(exchangeReply());
    if (u.includes('/oauth2/token')) return Promise.resolve(tokenReply());
    // registry (default tenant) · revoke · anything else: an empty, harmless answer.
    if (u.includes('/console/registry')) return Promise.resolve(json({ products: [] }, 200));
    return Promise.resolve(new Response(null, { status: 200 }));
  });
  vi.stubGlobal('fetch', fetchMock);
  return fetchMock;
}

const EXCHANGE_401 = () => json({ code: 'TOKEN_INVALID' }, 401);
const EXCHANGE_OK = () => json({ accessToken: 'op.jwt', expiresIn: 3600, tokenType: 'admin' }, 200);
const EXCHANGE_MFA = () => json({ code: 'MFA_REQUIRED' }, 403);
const EXCHANGE_503 = () => json({ code: 'DOWNSTREAM_ERROR' }, 503);

function callback(postLoginPath = '/') {
  cookieJar.set(PKCE_VERIFIER_COOKIE, { value: 'v' });
  cookieJar.set(OAUTH_STATE_COOKIE, { value: `s|${postLoginPath}` });
  return callbackGET(new Request(`${ORIGIN}/api/auth/callback?code=x&state=s`));
}

function refreshGet(target: string) {
  return refreshGET(
    new Request(`${ORIGIN}/api/auth/refresh?redirect=${encodeURIComponent(target)}`),
  );
}

function refreshPost() {
  return refreshPOST(new Request(`${ORIGIN}/api/auth/refresh`, { method: 'POST' }));
}

beforeEach(() => {
  cookieJar.clear();
  cookieDeletes.length = 0;
  vi.unstubAllGlobals();
});

describe('① · ② 콜백 — 교환 401 이 표식을 세우고 «테넌트 생성» 으로 보낸다', () => {
  it('🔴 교환 401 → 307 /new-tenant · 표식 세움(HttpOnly · maxAge = access 의 expires_in) · IAM 쿠키 유지 · 운영자 쿠키 없음', async () => {
    stubIam(EXCHANGE_401);
    const res = await callback('/finance/accounts');

    expect(res.status).toBe(307);
    expect(res.headers.get('location')).toBe(`${ORIGIN}/new-tenant`);
    const marker = cookieJar.get(MARKER);
    expect(marker?.value, 'the non-operator marker is set').toBe('1');
    expect(marker?.opts).toMatchObject({ httpOnly: true, path: '/', maxAge: 1800 });
    expect(cookieJar.get(ACCESS_COOKIE)?.value).toBe('iam.acc');
    expect(cookieJar.get(REFRESH_COOKIE)?.value).toBe('iam.ref');
    expect(cookieJar.get(ID_TOKEN_COOKIE)?.value, 'id_token kept — the logout hint (AC-7)').toBe('iam.id');
    expect(cookieJar.has(OPERATOR_COOKIE)).toBe(false);
  });

  it('🔴 교환 401 · 로그인 목적지가 허용 목록(`/guide`) 이면 그리로', async () => {
    stubIam(EXCHANGE_401);
    const res = await callback('/guide');
    expect(res.headers.get('location')).toBe(`${ORIGIN}/guide`);
    expect(cookieJar.get(MARKER)?.value).toBe('1');
  });

  it('🔴 교환 401 · 로그인 목적지가 `/` 여도 `/onboarding` 이 아니라 `/new-tenant`', async () => {
    stubIam(EXCHANGE_401);
    const res = await callback('/');
    expect(res.headers.get('location')).toBe(`${ORIGIN}/new-tenant`);
  });

  it('🔴 교환 200 → 남은 표식을 지운다(같은 응답에서)', async () => {
    cookieJar.set(MARKER, { value: '1' });
    stubIam(EXCHANGE_OK);
    await callback('/');
    expect(cookieJar.get(OPERATOR_COOKIE)?.value).toBe('op.jwt');
    expect(cookieDeletes, 'marker deleted on a successful exchange').toContain(MARKER);
    expect(cookieJar.has(MARKER)).toBe(false);
  });

  it('🔵 대조군 — 교환 403 MFA_REQUIRED 는 표식을 세우지 않는다(단계 상승)', async () => {
    stubIam(EXCHANGE_MFA);
    const res = await callback('/');
    expect(res.headers.get('location')).toContain('/api/auth/step-up');
    expect(cookieJar.has(MARKER)).toBe(false);
  });

  it('🔵 대조군 — 교환 503 은 표식을 세우지 않는다(세션 불가 — 비운영자 아님)', async () => {
    stubIam(EXCHANGE_503);
    const res = await callback('/');
    expect(res.headers.get('location')).toContain('/login?error=operator_exchange_unavailable');
    expect(cookieJar.has(MARKER)).toBe(false);
  });
});

describe('① · ② GET /api/auth/refresh (가드의 유휴 갱신 홉)', () => {
  beforeEach(() => {
    cookieJar.set(REFRESH_COOKIE, { value: 'old.ref' });
  });

  it('🔴 재교환 401 → 표식 세움 · 허용 목록 밖 목적지 → /new-tenant', async () => {
    stubIam(EXCHANGE_401);
    const res = await refreshGet('/finance/accounts');
    expect(res.headers.get('location')).toBe(`${ORIGIN}/new-tenant`);
    expect(cookieJar.get(MARKER)?.value).toBe('1');
    expect(cookieJar.get(MARKER)?.opts).toMatchObject({ httpOnly: true, maxAge: 1800 });
    expect(cookieJar.get(ACCESS_COOKIE)?.value).toBe('iam.acc');
  });

  it('🔴 재교환 401 · 목적지가 허용 목록 안(`/guide`)이면 그리로', async () => {
    stubIam(EXCHANGE_401);
    const res = await refreshGet('/guide');
    expect(res.headers.get('location')).toBe(`${ORIGIN}/guide`);
  });

  it('🔴 재교환 401 · 목적지 `/new-tenant?step=1` (쿼리 허용 — 경로 같음)', async () => {
    stubIam(EXCHANGE_401);
    const res = await refreshGet('/new-tenant?step=1');
    expect(res.headers.get('location')).toBe(`${ORIGIN}/new-tenant?step=1`);
  });

  it('🔴 재교환 성공 → 표식 지움', async () => {
    cookieJar.set(MARKER, { value: '1' });
    stubIam(EXCHANGE_OK);
    const res = await refreshGet('/dashboards/overview');
    expect(res.headers.get('location')).toBe(`${ORIGIN}/dashboards/overview`);
    expect(cookieDeletes).toContain(MARKER);
  });

  it('🔵 대조군 — 재교환 403 MFA_REQUIRED → 단계 상승 · 표식 없음', async () => {
    stubIam(EXCHANGE_MFA);
    const res = await refreshGet('/finance/accounts');
    expect(res.headers.get('location')).toContain('/api/auth/step-up');
    expect(cookieJar.has(MARKER)).toBe(false);
  });

  it('🔵 대조군 — 재교환 503 → 세션 전체 삭제 · /login?error=session_expired · 표식 없음', async () => {
    stubIam(EXCHANGE_503);
    const res = await refreshGet('/finance/accounts');
    expect(res.headers.get('location')).toContain('/login?error=session_expired');
    expect(cookieJar.has(MARKER)).toBe(false);
  });
});

describe('③ POST /api/auth/refresh (브라우저 클라이언트)', () => {
  beforeEach(() => {
    cookieJar.set(REFRESH_COOKIE, { value: 'old.ref' });
  });

  it('🔴 재교환 401 → 403 { code: NOT_AN_OPERATOR } · IAM 쿠키 유지(삭제 0) · 표식 세움', async () => {
    stubIam(EXCHANGE_401);
    const res = await refreshPost();
    expect(res.status).toBe(403);
    expect(((await res.json()) as { code: string }).code).toBe('NOT_AN_OPERATOR');
    expect(cookieDeletes).not.toContain(ACCESS_COOKIE);
    expect(cookieDeletes).not.toContain(REFRESH_COOKIE);
    expect(cookieJar.get(ACCESS_COOKIE)?.value).toBe('iam.acc');
    expect(cookieJar.get(MARKER)?.value).toBe('1');
  });

  it('🔵 대조군 — 재교환 503 은 지금처럼 401 + 세션 전체 삭제 · 표식 없음', async () => {
    stubIam(EXCHANGE_503);
    const res = await refreshPost();
    expect(res.status).toBe(401);
    expect(cookieDeletes).toContain(ACCESS_COOKIE);
    expect(cookieJar.has(MARKER)).toBe(false);
  });

  it('🔴 재교환 성공 → 표식 지움', async () => {
    cookieJar.set(MARKER, { value: '1' });
    stubIam(EXCHANGE_OK);
    const res = await refreshPost();
    expect(res.status).toBe(200);
    expect(cookieDeletes).toContain(MARKER);
  });
});

describe('④ 로그아웃 — 표식도 지우고, id_token 이 있으면 IdP 세션까지 끝낸다 (AC-7 · R9)', () => {
  it('🔴 표식 삭제 · end_session URL 에 id_token_hint', async () => {
    cookieJar.set(ACCESS_COOKIE, { value: 'iam.acc' });
    cookieJar.set(REFRESH_COOKIE, { value: 'iam.ref' });
    cookieJar.set(ID_TOKEN_COOKIE, { value: 'iam.id' });
    cookieJar.set(MARKER, { value: '1' });
    stubIam(EXCHANGE_OK);

    const res = await logoutPOST();
    const { logoutUrl } = (await res.json()) as { logoutUrl: string };

    expect(cookieDeletes, 'clearFullSession drops the marker').toContain(MARKER);
    expect(cookieJar.has(MARKER)).toBe(false);
    const url = new URL(logoutUrl);
    expect(url.origin + url.pathname).toBe('http://iam.local/connect/logout');
    expect(url.searchParams.get('id_token_hint')).toBe('iam.id');
  });
});

describe('⑤ 퇴역한 IAM 상수 `operator_eligibility_unavailable` — 콘솔은 따로 읽지 않는다 (773 S5 · S6)', () => {
  const RETIRED = () =>
    json({ error: 'invalid_grant', error_description: 'operator_eligibility_unavailable' }, 400);

  it('🔴 콜백 — 그 본문은 그 밖의 invalid_grant 처럼 `token_exchange_failed`', async () => {
    stubIam(EXCHANGE_OK, RETIRED);
    const res = await callback('/');
    expect(res.headers.get('location')).toBe(`${ORIGIN}/login?error=token_exchange_failed`);
  });

  it('🔴 POST 갱신 — 그 본문은 맨 invalid_grant 처럼 회전 경합 재시도(307 retry=1), 503 이 아니다', async () => {
    cookieJar.set(REFRESH_COOKIE, { value: 'old.ref' });
    stubIam(EXCHANGE_OK, RETIRED);
    const res = await refreshPost();
    expect(res.status).toBe(307);
    expect(res.headers.get('location')).toContain('retry=1');
  });
});
