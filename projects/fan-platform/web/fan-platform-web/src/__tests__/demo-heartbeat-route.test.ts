// @vitest-environment node
/**
 * `POST /api/demo/heartbeat` — **익명 방문은 데모 EC2 를 살려 두지 않는다**.
 *
 * ═════════════════════════════════════════════════════════════════════════
 * 이 파일이 지키는 사실
 * ═════════════════════════════════════════════════════════════════════════
 * heartbeat 은 텔레메트리가 아니라 **비용을 쓰는 행위**다 — `infra/demo/aws` 의 Lambda 가
 * "heartbeat 이 끊긴 지 N 분" 으로 인스턴스를 stop 하므로, 한 번 보낼 때마다 종료 시각이
 * 뒤로 밀린다. 공개 브라우징이 생긴 뒤로는 크롤러·링크 미리보기·우연한 방문자가 아무
 * 때나 페이지를 열 수 있고, 그 방문에 핑이 딸려 나가면 **아무도 안 쓰는 백엔드가 무기한
 * 켜져 있게 된다.** 증상이 없으므로 아무도 모른다.
 *
 * 🔴🔴 가장 중요한 칸은 **«DEMO_API_BASE 가 설정된 진짜 데모 배포 + 익명 요청»** 이다.
 *    라우트가 env 를 먼저 보고 세션을 나중에 봤다면 이 칸은 «env 가 없어서» 통과하는
 *    다른 칸에 가려 시험되지 않는다. 그래서 라우트는 세션을 먼저 보고, 이 파일은 그
 *    순서에 의존하는 칸을 명시적으로 들고 있다.
 *
 * 🔵 음성 대조군(로그인 + env 있음 → 실제로 전달한다)이 함께 있다. 그게 없으면
 *    «아무것도 안 하는 라우트» 라는 자명한 오답이 모든 칸을 통과한다.
 * ═════════════════════════════════════════════════════════════════════════
 */

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';

const authState = vi.hoisted(() => ({ authed: false }));

vi.mock('@/shared/auth/session', () => ({
  isAuthenticated: async () => authState.authed,
}));

const ORIGINAL_ENV = { ...process.env };
const CONTROL = 'https://control.example';

async function post() {
  const { POST } = await import('@/app/api/demo/heartbeat/route');
  return POST();
}

let fetchSpy: ReturnType<typeof vi.fn>;

beforeEach(() => {
  vi.resetModules();
  vi.unstubAllGlobals();
  authState.authed = false;
  delete process.env.DEMO_API_BASE;
  fetchSpy = vi.fn().mockResolvedValue(new Response(null, { status: 200 }));
  vi.stubGlobal('fetch', fetchSpy);
});

afterEach(() => {
  vi.unstubAllGlobals();
  process.env = { ...ORIGINAL_ENV };
});

describe('익명 — 아무 일도 안 한다', () => {
  it('🔴🔴 DEMO_API_BASE 가 **설정돼 있어도** 익명이면 컨트롤 플레인을 안 부른다', async () => {
    process.env.DEMO_API_BASE = CONTROL;
    authState.authed = false;

    const res = await post();

    expect(res.status).toBe(204);
    // 이 단언이 이 파일의 이유 전체다.
    expect(fetchSpy).not.toHaveBeenCalled();
  });

  it('익명 + env 없음 → 204, 호출 없음', async () => {
    authState.authed = false;
    const res = await post();
    expect(res.status).toBe(204);
    expect(fetchSpy).not.toHaveBeenCalled();
  });

  it('🔵 401 이 아니라 204 다 — 익명 핑은 오류가 아니라 무의미한 요청이다', async () => {
    process.env.DEMO_API_BASE = CONTROL;
    const res = await post();
    expect(res.status).not.toBe(401);
    expect(res.status).toBe(204);
  });
});

describe('로그인 — env 가 있을 때만 전달한다', () => {
  it('🔵 음성 대조군: 로그인 + env 있음 → `<base>/heartbeat` 로 POST 한다', async () => {
    process.env.DEMO_API_BASE = CONTROL;
    authState.authed = true;

    const res = await post();

    expect(res.status).toBe(204);
    expect(fetchSpy).toHaveBeenCalledTimes(1);
    const [url, init] = fetchSpy.mock.calls[0] as [string, RequestInit];
    expect(url).toBe(`${CONTROL}/heartbeat`);
    expect(init.method).toBe('POST');
  });

  it('베이스 끝의 슬래시를 중복시키지 않는다', async () => {
    process.env.DEMO_API_BASE = `${CONTROL}/`;
    authState.authed = true;
    await post();
    expect(fetchSpy.mock.calls[0]?.[0]).toBe(`${CONTROL}/heartbeat`);
  });

  it('🔴 로컬·CI(env 없음) → 로그인해도 아무 데도 안 부른다 — 살려 둘 인스턴스가 없다', async () => {
    authState.authed = true;
    const res = await post();
    expect(res.status).toBe(204);
    expect(fetchSpy).not.toHaveBeenCalled();
  });

  it('🔴 컨트롤 플레인이 죽어도 방문자에게 전가하지 않는다 (204)', async () => {
    process.env.DEMO_API_BASE = CONTROL;
    authState.authed = true;
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new Error('down')));

    const res = await post();
    expect(res.status).toBe(204);
  });
});
