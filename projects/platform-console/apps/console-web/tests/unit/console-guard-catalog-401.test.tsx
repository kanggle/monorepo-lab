/**
 * 🔴🔴 `TASK-MONO-690` — `(console)` 가드의 **카탈로그 401** 갈래. 실제 레이아웃을 부른다.
 *
 * -----------------------------------------------------------------------------
 * 무엇을 재는가
 * -----------------------------------------------------------------------------
 * `TASK-MONO-674` 는 **쿠키가 없을 때**의 분기만 고쳤다(형제 파일
 * `console-guard-idle-refresh.test.tsx`). 그 바로 아래 **쿠키는 있는데(=
 * `isAuthenticated()` 가 `true`) 백엔드의 `getCatalog()` 가 401 을 주는** 분기는
 * 건드리지 않았다 — 그 분기는 `redirect(await buildLoginRedirect())` 로 표지 없는
 * `/login?redirect=…` 를 만들었고, `/login` 은 쿠키만 보고(`isAuthenticated()`) 그
 * 방문을 즉시 `/console` 로 되튕겼다(Goal 표). 이 파일은 그 갈래를 **진짜 레이아웃**
 * 으로 고정한다.
 *
 * 🔴 로직 사본을 재지 않는다 — `@/app/(console)/layout` 을 import 해서 호출한다.
 *    (`console-guard-idle-refresh.test.tsx` 와 같은 원칙.)
 *
 * 🔴 두 번째 칸(마커 → `/login` 되튕김 없음)은 **여기서 다시 재지 않는다** —
 *    `relogin-loop.test.tsx` 의 «루프의 심장» 칸이 이미 **같은 마커**(`RE_LOGIN_PATH`
 *    가 실어 보내는 `error=session_expired`)로 그것을 잰다. 이 파일이 증명해야 하는
 *    것은 오직 **이 분기가 그 마커를 실제로 만드는가**이고, 마지막 칸이 두 파일을
 *    같은 상수로 맞물린다.
 */

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';

vi.mock('@/shared/lib/session', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/shared/lib/session')>();
  return {
    ...actual,
    // isAuthenticated() reads BOTH cookies for real — stub it directly so this
    // suite states its premise (cookies present) without re-deriving it from a
    // cookie jar mock (that's the idle-refresh file's job).
    isAuthenticated: async () => true,
    isSampleVisitor: async () => false,
    getActiveTenant: async () => null,
    getIdToken: async () => null,
    getAccessToken: async () => 'live.access.token',
  };
});

vi.mock('next/headers', () => ({
  headers: async () => new Headers({ 'x-pathname': '/ecommerce/orders?page=2' }),
}));

// 🔴 layout.tsx 는 `@/shared/api/errors` 의 **진짜** `ApiError` 로 `instanceof` 를
//    검사한다 — 이 목이 별도 클래스를 던지면 그 검사가 false 가 되어 401 분기가
//    한 번도 실행되지 않는다(처음 이 파일을 짰을 때 실제로 밟았다: `guardTarget()`
//    이 아무것도 못 던져 `caught` 가 `undefined` 였다). 진짜 클래스를 import 해서 쓴다.
vi.mock('@/features/catalog', async () => {
  const { ApiError } = await import('@/shared/api/errors');
  return {
    getCatalog: () => {
      throw new ApiError(401, 'TOKEN_INVALID', '세션이 만료되었습니다.');
    },
  };
});

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
import { RE_LOGIN_PATH, SESSION_EXPIRED } from '@/shared/lib/re-login';

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
  vi.stubGlobal(
    'fetch',
    vi.fn(() => {
      throw new Error(
        '가드 뒤 코드가 실행됐습니다 — 401 뒤의 org-hierarchy fetch 까지 내려갔습니다.',
      );
    }),
  );
});

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('🔴🔴 카탈로그 401 — 쿠키는 살아 있다 (TASK-MONO-690)', () => {
  it('🔴🔴 마커 없는 `/login?redirect=` 가 아니라 RE_LOGIN_PATH 로 보낸다', async () => {
    // 예전 코드: redirect(await buildLoginRedirect()) === '/login?redirect=%2Fecommerce%2Forders%3Fpage%3D2'
    // (표지 없음 — Goal 표의 결함 그대로). bite: 이 자리를 되돌리면 이 칸이 빨개진다.
    expect(await guardTarget()).toBe(RE_LOGIN_PATH);
  });

  it('🔴 그 목적지가 실제로 `error=session_expired` 마커를 싣는다', async () => {
    const to = await guardTarget();
    const url = new URL(to, 'http://localhost');
    expect(url.searchParams.get('error')).toBe(SESSION_EXPIRED);
  });

  it('🔴 가드는 org-hierarchy 를 부르기 전에 리다이렉트로 끊는다 — fetch 는 여전히 0', async () => {
    await guardTarget();
    expect(globalThis.fetch).not.toHaveBeenCalled();
  });

  it('🔵 이 상수는 `/login` 페이지가 되튕김을 스킵하는 바로 그 마커다 (relogin-loop.test.tsx 와 같은 값)', () => {
    // relogin-loop.test.tsx 의 «루프의 심장» 칸이 `error: SESSION_EXPIRED` 를 달고
    // 오면 /login 이 /console 로 되튕기지 않음을 증명한다. 여기서는 이 레이아웃이
    // 정확히 그 값을 만든다는 것만 증명한다 — 두 파일이 같은 상수를 잡고 있으므로
    // 어느 한쪽이 상수와 어긋나면 둘 중 하나가 빨개진다.
    expect(RE_LOGIN_PATH).toBe(`/login?error=${SESSION_EXPIRED}`);
  });
});
