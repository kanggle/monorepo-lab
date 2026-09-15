/**
 * 🔴🔴 `TASK-MONO-674` — `(console)` 가드의 **유휴 만료** 갈래. 실제 레이아웃을 부른다.
 *
 * -----------------------------------------------------------------------------
 * 무엇을 재는가
 * -----------------------------------------------------------------------------
 * 30분 유휴 뒤 브라우저는 액세스 쿠키(`maxAge = expires_in`)를 버리고 30일짜리 리프레시
 * 쿠키만 남긴다. 가드는 쿠키만 보므로 사유 없는 `/login?redirect=…` 로 튕겼다. 이제
 * 리프레시 쿠키가 있으면 `GET /api/auth/refresh?redirect=…` 로 보낸다(레이아웃은 쿠키를
 * 못 쓰므로 갱신은 **다음 요청**인 라우트 핸들러가 한다).
 *
 * 🔵 형제 파일 `demo-tour-console-guard-regression.test.tsx` 는 **리프레시 쿠키가 없는**
 *    미인증을 재고, 그 파일의 «가드 뒤 fetch 0» 핀(:94)은 «미인증 요청이 셸 안 코드에
 *    도달하지 않는다» 를 지킨다. 이 파일은 같은 모양의 목으로 **리프레시 쿠키가 있는**
 *    경우를 재고, 그 핀을 여기에도 똑같이 건다 — 가드 앞에 갱신 fetch 를 끼워 넣는 구현은
 *    여기서 빨개져야 한다. (둘째 파일로 뺀 이유: 형제 파일은 동시 진행 중인 다른 작업이
 *    만지는 demo-tour 묶음이라, 거기엔 손대지 않았다.)
 *
 * 🔴 로직 사본을 재지 않는다 — `@/app/(console)/layout` 을 import 해서 호출한다.
 *    `hasRefreshToken()` 도 목킹하지 않았다 — 진짜 함수가 아래 쿠키 저장소를 읽는다.
 */

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';

const { session } = vi.hoisted(() => ({
  session: { refreshCookie: true },
}));

vi.mock('@/shared/lib/session', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/shared/lib/session')>();
  return {
    ...actual,
    isAuthenticated: async () => false,
    getActiveTenant: async () => null,
    getIdToken: async () => null,
    getAccessToken: async () => null,
  };
});

vi.mock('next/headers', () => ({
  headers: async () => new Headers({ 'x-pathname': '/finance/accounts?page=2' }),
  // 쿠키 이름 리터럴은 아래 첫 칸이 제품 상수와 대조한다.
  cookies: async () => ({
    get: (name: string) =>
      name === 'console_refresh_token' && session.refreshCookie
        ? { value: 'idle.refresh' }
        : undefined,
  }),
}));

class RedirectError extends Error {
  constructor(public readonly to: string) {
    super(`NEXT_REDIRECT:${to}`);
  }
}
vi.mock('next/navigation', () => ({
  redirect: (to: string) => {
    throw new RedirectError(to);
  },
  notFound: () => {
    throw new Error('NEXT_NOT_FOUND');
  },
}));

import ConsoleLayout from '@/app/(console)/layout';
import { REFRESH_COOKIE } from '@/shared/lib/session';

async function guardTarget(): Promise<string> {
  let caught: unknown;
  try {
    await ConsoleLayout({ children: null });
  } catch (err) {
    caught = err;
  }
  expect(caught).toBeInstanceOf(RedirectError);
  return (caught as RedirectError).to;
}

beforeEach(() => {
  session.refreshCookie = true;
  vi.stubGlobal(
    'fetch',
    vi.fn(() => {
      throw new Error('가드 뒤 코드가 실행됐습니다 — 미인증 요청이 셸 안으로 들어왔습니다.');
    }),
  );
});

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('🔴🔴 유휴 만료 — 리프레시 쿠키가 남아 있다 (TASK-MONO-674)', () => {
  it('🔵 쿠키 이름이 제품 상수와 같다 (목의 리터럴이 낡으면 아래 칸이 공허해진다)', () => {
    expect(REFRESH_COOKIE).toBe('console_refresh_token');
  });

  it('🔴🔴 사유 없는 `/login?redirect=` 가 **아니라** 갱신 라우트로 보낸다 — 원래 경로를 쿼리까지 싣고', async () => {
    expect(await guardTarget()).toBe(
      `/api/auth/refresh?redirect=${encodeURIComponent('/finance/accounts?page=2')}`,
    );
  });

  it('🔴 둘러보기(`/demo`)로도 로그인 화면으로도 새지 않는다 — 셸은 여전히 닫혀 있다', async () => {
    const to = await guardTarget();
    expect(to.startsWith('/demo')).toBe(false);
    expect(to.startsWith('/login')).toBe(false);
  });

  it('🔴 갱신은 레이아웃이 아니라 **다음 요청**이 한다 — 가드에서 fetch 는 여전히 0', async () => {
    await guardTarget();
    expect(globalThis.fetch).not.toHaveBeenCalled();
  });

  it('🔵 대조군 — 같은 목에서 리프레시 쿠키만 빼면 예전 그대로 `/login?redirect=` (fetch 0)', async () => {
    session.refreshCookie = false;
    expect(await guardTarget()).toBe(
      `/login?redirect=${encodeURIComponent('/finance/accounts?page=2')}`,
    );
    expect(globalThis.fetch).not.toHaveBeenCalled();
  });
});
