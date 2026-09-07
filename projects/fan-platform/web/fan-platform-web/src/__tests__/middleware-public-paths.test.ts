// @vitest-environment node
/**
 * 미들웨어 — **두 방향을 다 잰다**.
 *
 * ═════════════════════════════════════════════════════════════════════════
 * 왜 한 방향으로는 부족한가
 * ═════════════════════════════════════════════════════════════════════════
 * 「공개 경로가 열린다」만 단언하면 **미들웨어를 통째로 지워도 초록**이다(전부 열리니까).
 * 「보호 경로가 닫힌다」만 단언하면 **전부 닫아도 초록**이다. 두 오답이 각각 한쪽 칸을
 * 통과하므로, 이 파일은 두 방향을 나란히 들고 있어야 한다.
 *
 * 그리고 세 번째 축이 있다 — **판정 실패**. `TASK-FAN-FE-019` 가 고친 결함은 "auth.js 가
 * 설정 오류로 500 을 내면 그 JSON 본문이 `auth()` 의 반환값이 되고, `!session` 술어가
 * 그것을 «세션이 있다» 로 읽는다" 였다. 그래서 아래에 **깨진 세션 페이로드** 칸이 있고,
 * 그 칸의 기대값은 «열린다» 가 아니라 **«/login 으로 꺾인다»** 다. 공개 브라우징이
 * 생겼다고 그 성질이 약해지면 안 된다.
 *
 * 🔴 `/membership` 과 `/membership/history` 가 **한 접두사를 공유하면서 반대 판정**을
 *    받는 것이 이 목록에서 가장 위험한 칸이다(전자는 공개 소개, 후자는 내 결제 이력).
 *    두 칸이 나란히 있다.
 *
 * 🔵 node 환경에서 돈다 — `next/server` 의 `NextRequest`/`NextResponse` 가 전역
 *    `Request`/`Response` 를 요구하고, jsdom 환경에는 그것이 없다.
 * ═════════════════════════════════════════════════════════════════════════
 */

import { describe, it, expect, vi, beforeEach } from 'vitest';

/** `auth()` 가 무엇을 돌려줄지 칸마다 갈아 끼운다. */
const authState = vi.hoisted(() => ({
  value: null as unknown,
  throws: false,
}));

vi.mock('@/shared/auth/auth', () => ({
  auth: async () => {
    if (authState.throws) throw new Error('auth.js exploded');
    return authState.value;
  },
}));

const SIGNED_IN = { user: { name: '팬' }, accountId: 'acc-1' };
/** auth.js 가 설정 오류로 500 을 냈을 때 `auth()` 가 그대로 돌려주는 본문. */
const CONFIG_ERROR_PAYLOAD = { message: 'There was a problem with the server configuration.' };
/** 사일런트 리프레시 실패로 `session` 콜백이 익명으로 강등시킨 세션(F3). */
const DEGRADED_SESSION = { user: undefined, expires: '2026-01-01' };

async function run(pathname: string, search = '') {
  const { middleware } = await import('@/middleware');
  const { NextRequest } = await import('next/server');
  const request = new NextRequest(new URL(`http://localhost${pathname}${search}`));
  return middleware(request);
}

/** `NextResponse.next()` 의 지문. 리다이렉트가 아니라 «통과» 를 뜻한다. */
function isPassThrough(res: Awaited<ReturnType<typeof run>>): boolean {
  return res.headers.get('x-middleware-next') === '1' && res.headers.get('location') === null;
}

function redirectTarget(res: Awaited<ReturnType<typeof run>>): URL | null {
  const location = res.headers.get('location');
  return location === null ? null : new URL(location, 'http://localhost');
}

const PUBLIC_PATHS = [
  '/',
  '/artists',
  '/artists/0199de80-0000-7000-8000-00000000a001',
  '/posts/abc-123',
  '/membership',
  '/login',
];

const PROTECTED_PATHS = [
  '/compose',
  '/me',
  '/me/posts',
  '/notifications',
  '/membership/history',
  // 🔴 판별자. 미들웨어는 라우팅보다 **먼저** 도므로 존재하지 않는 경로도 꺾여야 한다.
  //    여기서 «통과» 가 나오면 그것은 "그런 페이지가 없다" 가 아니라 «가드를 안 거쳤다» 다.
  '/nonexistent-xyz',
  // heartbeat 은 허용 목록에 없다 — 익명 핑이 데모 EC2 를 살려 두면 안 된다.
  '/api/demo/heartbeat',
];

beforeEach(() => {
  vi.resetModules();
  authState.value = null;
  authState.throws = false;
});

describe('방향 ① — 공개 경로는 **익명으로 통과**한다', () => {
  it('🔵 모집단이 비어 있지 않다', () => {
    expect(PUBLIC_PATHS.length).toBeGreaterThan(0);
  });

  for (const path of PUBLIC_PATHS) {
    it(`익명 ${path} → 통과 (리다이렉트 없음)`, async () => {
      authState.value = null;
      const res = await run(path);
      expect(isPassThrough(res), `${path} 가 꺾였다`).toBe(true);
    });
  }

  it('🔴 쿼리스트링이 붙어도 공개다 — `/artists?q=…` 는 검색이고 검색은 공개 기능이다', async () => {
    authState.value = null;
    expect(isPassThrough(await run('/artists', '?q=%EB%A3%A8%EB%AF%B8'))).toBe(true);
  });

  it('🔴 auth() 가 터져도 공개 경로는 **안 묻는다** — 판정이 없으니 판정 실패도 없다', async () => {
    authState.throws = true;
    for (const path of PUBLIC_PATHS) {
      expect(isPassThrough(await run(path)), `${path}`).toBe(true);
    }
  });
});

describe('방향 ② — 보호 경로는 익명이면 **여전히 /login 으로 꺾인다**', () => {
  it('🔵 모집단이 비어 있지 않다', () => {
    expect(PROTECTED_PATHS.length).toBeGreaterThan(0);
  });

  for (const path of PROTECTED_PATHS) {
    it(`익명 ${path} → /login?from=${path}`, async () => {
      authState.value = null;
      const res = await run(path);
      const target = redirectTarget(res);
      expect(target, `${path} 가 안 꺾였다`).not.toBeNull();
      expect(target?.pathname).toBe('/login');
      expect(target?.searchParams.get('from')).toBe(path);
    });
  }

  it('🔴 `from` 에 쿼리스트링까지 보존된다', async () => {
    authState.value = null;
    const target = redirectTarget(await run('/me/posts', '?page=2'));
    expect(target?.searchParams.get('from')).toBe('/me/posts?page=2');
  });

  it('로그인 상태면 보호 경로가 통과한다 (음성 대조군 — 전부 닫는 오답을 배제한다)', async () => {
    authState.value = SIGNED_IN;
    for (const path of PROTECTED_PATHS) {
      expect(isPassThrough(await run(path)), `${path}`).toBe(true);
    }
  });
});

describe('방향 ③ — **깨진 세션 페이로드**에서도 fail-closed 다', () => {
  it('🔴🔴 auth.js 설정 오류 본문은 «세션 있음» 이 아니다 → /login', async () => {
    authState.value = CONFIG_ERROR_PAYLOAD;
    const target = redirectTarget(await run('/me'));
    expect(target?.pathname).toBe('/login');
  });

  it('🔴 사일런트 리프레시 실패로 강등된 세션(F3)도 → /login', async () => {
    authState.value = DEGRADED_SESSION;
    expect(redirectTarget(await run('/compose'))?.pathname).toBe('/login');
  });

  it('🔴 auth() 가 throw 해도 → /login (판정 불가는 «열림» 이 아니다)', async () => {
    authState.throws = true;
    expect(redirectTarget(await run('/notifications'))?.pathname).toBe('/login');
  });

  it('🔴 판별자: 설정이 깨진 상태에서 존재하지 않는 경로도 꺾인다', async () => {
    authState.value = CONFIG_ERROR_PAYLOAD;
    expect(redirectTarget(await run('/nonexistent-xyz'))?.pathname).toBe('/login');
  });

  it('🔴🔴 깨진 세션이어도 **공개 경로는 열린 채로 남는다** — 사이트 전체가 죽지 않는다', async () => {
    authState.value = CONFIG_ERROR_PAYLOAD;
    expect(isPassThrough(await run('/'))).toBe(true);
    expect(isPassThrough(await run('/artists'))).toBe(true);
    expect(isPassThrough(await run('/login'))).toBe(true);
  });
});

describe('🔴🔴 /membership 과 /membership/history — 한 접두사, 반대 판정', () => {
  it('/membership 은 공개 소개다 → 익명 통과', async () => {
    authState.value = null;
    expect(isPassThrough(await run('/membership'))).toBe(true);
  });

  it('/membership/history 는 **내 결제 이력**이다 → 익명이면 /login', async () => {
    authState.value = null;
    expect(redirectTarget(await run('/membership/history'))?.pathname).toBe('/login');
  });

  it('🔴 접두사 매칭 실수 방지: /membership-admin 같은 경로는 열리지 않는다', async () => {
    authState.value = null;
    expect(redirectTarget(await run('/membership-admin'))?.pathname).toBe('/login');
  });

  it('🔴 `/artists` 도 마찬가지 — `/artistsxyz` 는 공개가 아니다', async () => {
    authState.value = null;
    expect(redirectTarget(await run('/artistsxyz'))?.pathname).toBe('/login');
  });
});
