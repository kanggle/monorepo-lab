/**
 * `/` 는 **누구든** `/dashboards/overview` — `ADR-MONO-074` A8 가 이 파일의 기대값을 바꿨다
 * (`TASK-PC-FE-282` AC-8).
 *
 * 예전 판은 «익명 → `/demo`, 인증 → `/dashboards/overview`» 두 갈래를 쟀다. 그 분기는
 * «익명은 `(console)` 에 못 들어온다» 는 전제 위에 있었고, ADR-MONO-074 가 그 전제를
 * «익명은 들어오지만 백엔드에 닿을 수 없다» 로 바꿨다. 그래서 착지점이 하나가 됐다.
 *
 * 🔴🔴 두 칸을 **여전히 함께** 잰다. 인증 칸만 재면 «익명을 `/demo` 로 보내는» 옛 분기가
 *    되살아나도 초록이고, 익명 칸만 재면 «인증된 운영자를 딴 데로 보내는» 회귀가 초록이다.
 *    그리고 이 라우트가 세션을 **읽지 않는다**는 것도 잰다 — 판정은 이제 `(console)`
 *    레이아웃과 코어의 몫이고, 여기서 다시 하면 «인증» 의 정의가 두 곳에 생긴다.
 */

import { describe, it, expect, vi, beforeEach } from 'vitest';

const { session } = vi.hoisted(() => ({ session: { authed: false, reads: 0 } }));

vi.mock('@/shared/lib/session', () => ({
  isAuthenticated: async () => {
    session.reads += 1;
    return session.authed;
  },
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
  session.reads = 0;
});

describe('root redirect (ADR-MONO-074 A8)', () => {
  it('익명 → /dashboards/overview (같은 주소의 실제 화면, 값은 샘플)', async () => {
    session.authed = false;
    expect(await redirectTarget()).toBe('/dashboards/overview');
  });

  it('🔴 익명을 둘러보기(/demo)로 보내지 않는다 — 옛 분기가 되살아나지 않았다', async () => {
    session.authed = false;
    expect(await redirectTarget()).not.toContain('/demo');
  });

  it('🔵 인증된 운영자 → /dashboards/overview (예전과 한 글자도 다르지 않다)', async () => {
    session.authed = true;
    expect(await redirectTarget()).toBe('/dashboards/overview');
  });

  it('🔵 이 라우트는 세션을 읽지 않는다 — 판정은 (console) 레이아웃이 한다', async () => {
    await redirectTarget();
    expect(session.reads).toBe(0);
  });
});
