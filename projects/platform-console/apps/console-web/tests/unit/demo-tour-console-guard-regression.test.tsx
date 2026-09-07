/**
 * 🔴🔴 **회귀선** — 공개 둘러보기를 추가했다고 해서 `(console)` 이 열리지 않았다.
 *
 * 이 파일이 재는 명제는 하나다: `app/(console)/layout.tsx` 는 **여전히** 첫 동작으로
 * `isAuthenticated()` 를 물어보고, 거짓이면 `/login?redirect=…` 로 튕긴다. 그 레이아웃
 * 뒤에 65개 화면(실제 고객·주문·재무·계정 데이터)이 있으므로, 이 한 줄이 이 저장소에서
 * 가장 비싼 한 줄이다.
 *
 * 🔴 왜 «가드 로직을 흉내낸 순수 함수» 를 테스트하지 않는가 — 이미 그런 테스트가 있고
 *    (`layout-login-redirect.test.ts`, `buildLoginRedirect` 의 소독 규칙 사본), 그 테스트는
 *    **가드가 통째로 지워져도 초록이다.** 사본을 재면 사본이 옳은지만 알 수 있다.
 *    그래서 여기서는 **실제 모듈을 임포트해서 호출**하고 redirect 가 나는지를 잰다.
 *
 * 🔵 `getCatalog()` 등 무거운 하위 호출은 **의도적으로 목킹하지 않았다**. 가드가 살아
 *    있으면 그 코드에 도달하지 못하므로, 만약 그것들이 불린다면 그 자체가 가드가 죽었다는
 *    신호다(그리고 fetch 스텁이 던져서 다른 문구로 실패한다 — 초록으로는 안 끝난다).
 */

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';

const { session } = vi.hoisted(() => ({
  session: { authed: false },
}));

vi.mock('@/shared/lib/session', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/shared/lib/session')>();
  return {
    ...actual,
    isAuthenticated: async () => session.authed,
    getActiveTenant: async () => null,
    getIdToken: async () => null,
    getAccessToken: async () => null,
  };
});

vi.mock('next/headers', () => ({
  headers: async () => new Headers({ 'x-pathname': '/finance/accounts' }),
  cookies: async () => ({ get: () => undefined }),
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

beforeEach(() => {
  session.authed = false;
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

describe('🔴 (console) 인증 가드는 둘러보기 추가 이후에도 그대로다', () => {
  it('미인증 요청은 `/login?redirect=<원래경로>` 로 튕긴다', async () => {
    await expect(ConsoleLayout({ children: null })).rejects.toMatchObject({
      to: `/login?redirect=${encodeURIComponent('/finance/accounts')}`,
    });
  });

  it('🔴 둘러보기(`/demo`)로 튕기지 **않는다** — 운영자는 로그인해야 한다', async () => {
    let caught: unknown;
    try {
      await ConsoleLayout({ children: null });
    } catch (err) {
      caught = err;
    }
    expect(caught).toBeInstanceOf(RedirectError);
    expect((caught as RedirectError).to.startsWith('/demo')).toBe(false);
    expect((caught as RedirectError).to.startsWith('/login')).toBe(true);
  });

  it('가드가 막았으므로 하위 도메인 호출이 **한 번도** 일어나지 않는다', async () => {
    await expect(ConsoleLayout({ children: null })).rejects.toBeInstanceOf(
      RedirectError,
    );
    expect(globalThis.fetch).not.toHaveBeenCalled();
  });
});
