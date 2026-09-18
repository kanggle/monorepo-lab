import { describe, it, expect, vi, beforeEach } from 'vitest';

/**
 * `TASK-MONO-674` — `GET /api/auth/refresh`, the `(console)` guard's idle-expiry hop.
 *
 * -----------------------------------------------------------------------------
 * 무엇을 재는가
 * -----------------------------------------------------------------------------
 * 30분 유휴 뒤 브라우저는 액세스 쿠키를 버리고(`maxAge = expires_in`) 30일짜리 리프레시
 * 쿠키만 남긴다. 가드는 쿠키만 보므로 사유 없이 `/login?redirect=…` 로 튕겼다.
 * 소유자 결정 «ⓐ+ⓑ 갱신+실패시 사유» 의 두 절반을 **따로** 잰다:
 *   ⓐ 갱신이 되면 **요청한 화면으로** 돌아간다(쿠키가 실제로 세워진다);
 *   ⓑ 갱신이 안 되면 **`/login?error=session_expired`** 다(사유 없는 `?redirect=` 가 아니다).
 * 그리고 설계가 떠안은 셋: 회전 경합(두 탭), 루프 상한, 열린 리다이렉트.
 *
 * 🔴 로직을 흉내 낸 사본을 재지 않는다 — 실제 라우트 핸들러를 import 해서 부른다.
 */

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

// TASK-PC-FE-292 — 활성 테넌트 기본값은 레지스트리의 선택지로 정한다(토큰 tenant_id 가 아니다).
// 이 파일의 운영자는 선택지가 `demo-corp` 하나인 운영자다.
vi.mock('@/shared/api/registry-client', () => ({
  fetchRegistry: async () => ({
    products: [
      { productKey: 'iam', displayName: 'IAM', available: true, tenants: ['demo-corp'], baseRoute: '/accounts' },
    ],
  }),
}));

// 경합 대기는 짧게 — 그러나 **0 이 아니게** 해서 «정말 기다렸다» 를 잴 수 있게 한다.
// 제품 값은 아래 칸에서 따로 잰다.
const TEST_GRACE_MS = 60;
vi.mock('@/shared/lib/session-refresh', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/shared/lib/session-refresh')>();
  return { ...actual, REFRESH_RACE_GRACE_MS: 60 };
});

import { GET as refreshGET } from '@/app/api/auth/refresh/route';
import {
  ACCESS_COOKIE,
  REFRESH_COOKIE,
  OPERATOR_COOKIE,
  TENANT_COOKIE,
  ASSUMED_TOKEN_COOKIE,
  ID_TOKEN_COOKIE,
} from '@/shared/lib/session';
import { RE_LOGIN_PATH, SESSION_EXPIRED } from '@/shared/lib/re-login';
import { SESSION_REFRESH_PATH } from '@/shared/lib/login-redirect';

const ORIGIN = 'http://console.local';

function json(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

/** An unsigned JWT-shaped token (the console only decodes, never verifies, the home tenant). */
function jwt(claims: Record<string, unknown>): string {
  return `h.${Buffer.from(JSON.stringify(claims)).toString('base64url')}.s`;
}

type IamReply = Response | Error;
type OperatorReply = Response | Error;

/** fetch stub: IAM `/oauth2/token` refresh + admin operator exchange. */
function stubFetch(iam: () => IamReply, operator: () => OperatorReply = () =>
  json({ accessToken: 'new.op', expiresIn: 3600, tokenType: 'admin' })) {
  const fn = vi.fn((url: string) => {
    const reply = String(url).includes('/api/admin/auth/token-exchange') ? operator() : iam();
    return reply instanceof Error ? Promise.reject(reply) : Promise.resolve(reply);
  });
  vi.stubGlobal('fetch', fn);
  return fn;
}

const IAM_OK = () =>
  json({
    access_token: jwt({ tenant_id: 'demo-corp', sub: 'op-1' }),
    token_type: 'Bearer',
    expires_in: 1800,
    refresh_token: 'rotated.ref',
    id_token: 'new.id',
  });

function get(query: string) {
  return refreshGET(new Request(`${ORIGIN}${SESSION_REFRESH_PATH}${query}`));
}

function location(res: Response): string {
  const loc = res.headers.get('location');
  expect(loc, 'redirect response must carry a Location').not.toBeNull();
  return loc as string;
}

/** The idle-expired state: the browser kept ONLY the 30-day refresh cookie. */
function idleExpired() {
  cookieJar.set(REFRESH_COOKIE, { value: 'old.ref' });
}

beforeEach(() => {
  cookieJar.clear();
  cookieDeletes.length = 0;
  vi.unstubAllGlobals();
});

describe('GET /api/auth/refresh — 유휴 만료 갱신 (TASK-MONO-674)', () => {
  it('🔵 대조군 — 리프레시 쿠키가 없으면(로그인한 적 없음) 예전 그대로 `/login?redirect=` 이고 네트워크 호출이 없다', async () => {
    const fetchFn = stubFetch(IAM_OK);

    const res = await get('?redirect=%2Fecommerce');

    expect(location(res)).toBe(`${ORIGIN}/login?redirect=${encodeURIComponent('/ecommerce')}`);
    expect(location(res)).not.toContain(SESSION_EXPIRED);
    expect(fetchFn).not.toHaveBeenCalled();
    expect(cookieDeletes).toEqual([]);
  });

  it.each([
    ['30분 유휴 — 액세스·operator 쿠키 둘 다 없음', {}],
    ['operator 쿠키만 없음 — operator TTL 이 먼저 끝난 배포', { [ACCESS_COOKIE]: 'still.acc' }],
  ])('🔴🔴 ⓐ %s → 조용히 갱신하고 **요청한 화면으로** 돌아간다(쿼리까지)', async (_label, extra) => {
    idleExpired();
    for (const [k, v] of Object.entries(extra)) cookieJar.set(k, { value: v });
    stubFetch(IAM_OK);

    const target = '/finance/accounts?page=2&sort=asc';
    const res = await get(`?redirect=${encodeURIComponent(target)}`);

    expect(res.status).toBe(307);
    expect(location(res)).toBe(`${ORIGIN}${target}`);
    expect(res.headers.get('cache-control')).toBe('no-store');
    // 가드가 요구하는 두 쿠키가 **실제로** 세워졌다 — 돌아간 화면이 다시 튕기지 않는다.
    expect(cookieJar.get(ACCESS_COOKIE)?.value).toContain('h.');
    expect(cookieJar.get(OPERATOR_COOKIE)?.value).toBe('new.op');
    // 회전된 리프레시 토큰이 다시 저장됐다(`reuse-refresh-tokens=false`).
    expect(cookieJar.get(REFRESH_COOKIE)?.value).toBe('rotated.ref');
    expect(cookieJar.get(ID_TOKEN_COOKIE)?.value).toBe('new.id');
    expect(cookieDeletes).toEqual([]);
  });

  // TASK-PC-FE-292 가 이 칸의 **기전**을 바꿨다(결정이 바꾼 것 — «빨개서 고친» 것이 아니다).
  // 결과(활성 테넌트 = demo-corp)는 그대로다. 달라진 것: 토큰의 tenant_id 를 읽는 대신 콜백과 같은
  // 함수가 레지스트리 선택지에서 고르고 **assume** 한다 — 그래서 assumed 토큰이 같이 서고, 테넌트
  // 쿠키는 스위치와 같은 세션 쿠키(maxAge 없음)라 다음 유휴 갱신은 re-assume 가지를 탄다.
  it('🔴 세션이 **완전해진다** — 활성 테넌트도 콜백과 같은 함수로 되살린다 (assumed 토큰과 함께)', async () => {
    idleExpired();
    stubFetch(IAM_OK);

    await get('?redirect=%2Fdashboards%2Foverview');

    expect(cookieJar.get(TENANT_COOKIE)?.value).toBe('demo-corp');
    expect(cookieJar.get(TENANT_COOKIE)?.opts?.maxAge).toBeUndefined();
    expect(cookieJar.get(ASSUMED_TOKEN_COOKIE)?.value).toBeTruthy();
  });

  it('🔴🔴 ⓑ IAM 이 리프레시를 거절하면(4xx) **사유를 달고** `/login?error=session_expired` — 세션 전체를 지운다', async () => {
    idleExpired();
    stubFetch(() => json({ error: 'invalid_client' }, 401));

    const res = await get('?redirect=%2Fecommerce');
    const url = new URL(location(res));

    expect(url.origin).toBe(ORIGIN);
    expect(`${url.pathname}?error=${url.searchParams.get('error')}`).toBe(RE_LOGIN_PATH);
    expect(url.searchParams.get('redirect')).toBe('/ecommerce');
    expect(cookieDeletes).toEqual(expect.arrayContaining([ACCESS_COOKIE, REFRESH_COOKIE, OPERATOR_COOKIE]));
  });

  it('ⓑ operator 재교환이 불가(5xx/timeout)면 세션 전체를 지우고 session_expired', async () => {
    idleExpired();
    stubFetch(IAM_OK, () => json({ code: 'DOWNSTREAM_ERROR' }, 503));

    const res = await get('?redirect=%2Fecommerce');

    expect(new URL(location(res)).searchParams.get('error')).toBe(SESSION_EXPIRED);
    expect(cookieDeletes).toEqual(expect.arrayContaining([ACCESS_COOKIE, REFRESH_COOKIE, OPERATOR_COOKIE]));
    expect(cookieJar.has(OPERATOR_COOKIE)).toBe(false);
  });

  it('operator 재교환이 fail-closed(401, 운영자 아님)면 콜백과 같게 `/onboarding` — IAM 쿠키는 남기고 operator 는 지운다', async () => {
    idleExpired();
    stubFetch(IAM_OK, () => json({ code: 'TOKEN_INVALID' }, 401));

    const res = await get('?redirect=%2Fecommerce');

    expect(location(res)).toBe(`${ORIGIN}/onboarding`);
    expect(cookieJar.get(REFRESH_COOKIE)?.value).toBe('rotated.ref');
    expect(cookieJar.has(ACCESS_COOKIE)).toBe(true);
    expect(cookieJar.has(OPERATOR_COOKIE)).toBe(false);
    expect(cookieDeletes).not.toContain(REFRESH_COOKIE);
  });

  it.each([
    ['IAM 5xx', () => json({ error: 'server_error' }, 503)],
    ['네트워크 실패', () => new TypeError('fetch failed')],
  ])('ⓑ %s — 일시 장애는 session_expired 로 사유를 보이되 쿠키는 **안 지운다**(다음 방문이 갱신할 수 있게)', async (_l, iam) => {
    idleExpired();
    stubFetch(iam as () => IamReply);

    const res = await get('?redirect=%2Fecommerce');

    expect(new URL(location(res)).searchParams.get('error')).toBe(SESSION_EXPIRED);
    expect(cookieDeletes).toEqual([]);
    expect(cookieJar.get(REFRESH_COOKIE)?.value).toBe('old.ref');
  });
});

describe('🔴🔴 회전 경합 — 두 탭이 동시에 만료를 맞는다 (`reuse-refresh-tokens=false`)', () => {
  it('첫 시도가 `400 invalid_grant` 면 **아무 쿠키도 지우지 않고**, 대기 뒤 `retry=1` 로 **한 번만** 다시 본다', async () => {
    idleExpired();
    stubFetch(() => json({ error: 'invalid_grant' }, 400));

    const started = Date.now();
    const res = await get('?redirect=%2Fecommerce%2Forders');
    const elapsed = Date.now() - started;

    const url = new URL(location(res));
    expect(url.origin).toBe(ORIGIN);
    expect(url.pathname).toBe(SESSION_REFRESH_PATH);
    expect(url.searchParams.get('retry')).toBe('1');
    expect(url.searchParams.get('redirect')).toBe('/ecommerce/orders');
    // 🔴 지웠다면 그 Set-Cookie 가 옆 탭이 방금 세운 새 쿠키를 **덮을** 수 있다.
    expect(cookieDeletes).toEqual([]);
    // 대기가 실제로 있었다(옆 탭의 Set-Cookie 가 도착할 시간).
    expect(elapsed).toBeGreaterThanOrEqual(TEST_GRACE_MS - 10);
  });

  it('`retry=1` 에 옆 탭이 세운 새 세션이 실려 오면 — 강제 로그아웃하지 않고 **요청한 화면으로** 간다', async () => {
    cookieJar.set(REFRESH_COOKIE, { value: 'rotated.by.other.tab' });
    cookieJar.set(ACCESS_COOKIE, { value: 'fresh.acc' });
    cookieJar.set(OPERATOR_COOKIE, { value: 'fresh.op' });
    const fetchFn = stubFetch(IAM_OK);

    const res = await get('?redirect=%2Fecommerce%2Forders&retry=1');

    expect(location(res)).toBe(`${ORIGIN}/ecommerce/orders`);
    expect(fetchFn).not.toHaveBeenCalled();
    expect(cookieDeletes).toEqual([]);
  });

  it('`retry=1` 인데도 세션이 없으면 session_expired — 다시 갱신하지 않고(IAM 호출 0) 쿠키도 안 지운다', async () => {
    idleExpired();
    const fetchFn = stubFetch(IAM_OK);

    const res = await get('?redirect=%2Fecommerce%2Forders&retry=1');
    const url = new URL(location(res));

    expect(url.pathname).toBe('/login');
    expect(url.searchParams.get('error')).toBe(SESSION_EXPIRED);
    expect(url.searchParams.get('redirect')).toBe('/ecommerce/orders');
    expect(fetchFn).not.toHaveBeenCalled();
    expect(cookieDeletes).toEqual([]);
  });

  it('🔵 제품의 대기 값은 0 이 아니다 (테스트는 짧게 바꿔 끼웠다)', async () => {
    const actual = await vi.importActual<typeof import('@/shared/lib/session-refresh')>(
      '@/shared/lib/session-refresh',
    );
    expect(actual.REFRESH_RACE_GRACE_MS).toBeGreaterThanOrEqual(1000);
  });
});

describe('🔴 루프 상한 — 갱신 왕복은 절대 순환하지 않는다', () => {
  const IAM_REPLIES: Array<[string, () => IamReply, (() => OperatorReply)?]> = [
    ['ok', IAM_OK],
    ['invalid_grant (race)', () => json({ error: 'invalid_grant' }, 400)],
    ['invalid_client', () => json({ error: 'invalid_client' }, 401)],
    ['5xx', () => json({}, 500)],
    ['network', () => new TypeError('fetch failed')],
    ['operator unavailable', IAM_OK, () => json({}, 503)],
    ['operator fail-closed', IAM_OK, () => json({}, 401)],
  ];

  it.each(IAM_REPLIES)('첫 시도 [%s] — 갱신 라우트로 되돌아가는 것은 경합 한 경우뿐이고, 그때는 반드시 `retry=1` 이다', async (_l, iam, op) => {
    idleExpired();
    stubFetch(iam, op);

    const loc = new URL(location(await get('?redirect=%2Fecommerce')));

    expect(loc.origin).toBe(ORIGIN);
    if (loc.pathname === SESSION_REFRESH_PATH) {
      expect(loc.searchParams.get('retry')).toBe('1');
    } else {
      expect(['/ecommerce', '/login', '/onboarding']).toContain(loc.pathname);
    }
  });

  it.each([
    ['세션 없음', {}],
    ['리프레시만', { [REFRESH_COOKIE]: 'r' }],
    ['액세스만', { [ACCESS_COOKIE]: 'a', [REFRESH_COOKIE]: 'r' }],
    ['완전', { [ACCESS_COOKIE]: 'a', [OPERATOR_COOKIE]: 'o', [REFRESH_COOKIE]: 'r' }],
  ])('`retry=1` [%s] — 어떤 상태에서도 갱신 라우트로 **다시** 가지 않는다', async (_l, jar) => {
    for (const [k, v] of Object.entries(jar)) cookieJar.set(k, { value: v });
    stubFetch(() => json({ error: 'invalid_grant' }, 400));

    const loc = new URL(location(await get('?redirect=%2Fecommerce&retry=1')));

    expect(loc.pathname).not.toBe(SESSION_REFRESH_PATH);
  });
});

describe('🔴 열린 리다이렉트 — `redirect` 는 공격자 손에 있다', () => {
  it.each([
    '//evil.example/steal',
    'https://evil.example',
    'http:/evil.example',
    '/\\evil.example',
    '/api/auth/refresh?redirect=%2Fecommerce',
    '/login?error=state_mismatch',
    '',
  ])('redirect=%j → 같은 오리진의 `/` 로만 돌아간다', async (raw) => {
    idleExpired();
    stubFetch(IAM_OK);

    const res = await get(`?redirect=${encodeURIComponent(raw)}`);

    expect(location(res)).toBe(`${ORIGIN}/`);
  });

  it('실패 경로도 같은 소독을 거친다 — session_expired 에 외부 주소가 실리지 않는다', async () => {
    idleExpired();
    stubFetch(() => json({ error: 'invalid_client' }, 401));

    const url = new URL(location(await get(`?redirect=${encodeURIComponent('//evil.example')}`)));

    expect(url.origin).toBe(ORIGIN);
    expect(url.searchParams.get('error')).toBe(SESSION_EXPIRED);
    expect(url.searchParams.get('redirect')).toBeNull();
  });
});

describe('🔵 대조군 — 갱신 응답에 `id_token` 이 없을 때 (TASK-MONO-705 ⓐ)', () => {
  // 🔴 이 자리에 «있으면 세운다» 칸을 새로 쓰려다 멈췄다 — **이미 있다**(위 674 칸이
  //    `expect(cookieJar.get(ID_TOKEN_COOKIE)?.value).toBe('new.id')` 를 단언한다).
  //    내가 못 찾은 이유는 `grep console_id_token tests/` 로 물었기 때문이다. 테스트는
  //    리터럴이 아니라 **상수 `ID_TOKEN_COOKIE`** 를 쓴다 — 「무는 가드가 있나」에 grep 은
  //    답하지 못한다. 실제로 없던 것은 **반대 방향** 한 칸뿐이고, 그것이 아래다.
  //
  // 🔵 왜 반대 방향이 필요한가. TASK-MONO-705 ⓐ 는 IAM 이 갱신 응답에 `id_token` 을
  //    싣게 만든 변경이다. 그 뒤로 이 스위트의 픽스처는 **항상** `id_token` 을 갖게 되므로,
  //    콘솔이 «응답에 없어도 무언가를 세운다» 로 망가져도 위 칸은 초록이다. 틀리는 방향이
  //    «없는 값을 지어내는» 쪽이면 로그아웃이 쓰레기 `id_token_hint` 를 보낸다.

  it('응답에 `id_token` 이 없으면 쿠키를 만들지 않는다 — 없는 값을 지어내지 않는다', async () => {
    idleExpired();
    stubFetch(() =>
      json({
        access_token: jwt({ tenant_id: 'demo-corp', sub: 'op-1' }),
        token_type: 'Bearer',
        expires_in: 1800,
        refresh_token: 'rotated.ref',
        // id_token 없음 — TASK-MONO-705 이전의 IAM 이 내던 바로 그 모양이다.
      }),
    );

    await get('?redirect=%2Fecommerce');

    expect(cookieJar.has(ID_TOKEN_COOKIE)).toBe(false);
  });
});
