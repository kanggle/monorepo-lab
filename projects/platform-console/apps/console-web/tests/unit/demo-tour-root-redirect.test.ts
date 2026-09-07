/**
 * `/` 의 두 갈래 — **익명은 둘러보기로, 로그인한 운영자는 오늘과 똑같이**.
 *
 * 🔴🔴 두 칸을 **함께** 재는 것이 요점이다. 익명 칸만 재면 "인증된 사용자도 /demo 로
 *    보내는" 회귀가 통과한다(그 회귀는 화면이 멀쩡히 떠서 아무 알람도 안 울린다 — 운영자가
 *    로그인했는데 샘플 화면을 보게 되고, 그것을 «데이터가 안 들어왔다» 로 읽는다).
 */

import { describe, it, expect, vi, beforeEach } from 'vitest';

const { session } = vi.hoisted(() => ({ session: { authed: false } }));

vi.mock('@/shared/lib/session', () => ({
  isAuthenticated: async () => session.authed,
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
}));

import RootIndex from '@/app/page';

async function redirectTarget(): Promise<string> {
  try {
    await RootIndex();
  } catch (err) {
    if (err instanceof RedirectError) return err.to;
    throw err;
  }
  throw new Error('`/` 가 redirect 하지 않았습니다 — 이 라우트는 항상 redirect 합니다.');
}

beforeEach(() => {
  session.authed = false;
});

describe('root redirect', () => {
  it('익명 → /demo (공개 둘러보기)', async () => {
    session.authed = false;
    expect(await redirectTarget()).toBe('/demo');
  });

  it('🔴 인증된 운영자 → /dashboards/overview (오늘과 **한 글자도** 다르지 않다)', async () => {
    session.authed = true;
    expect(await redirectTarget()).toBe('/dashboards/overview');
  });

  it('🔴 인증된 운영자를 둘러보기로 보내지 않는다', async () => {
    session.authed = true;
    expect(await redirectTarget()).not.toContain('/demo');
  });
});
