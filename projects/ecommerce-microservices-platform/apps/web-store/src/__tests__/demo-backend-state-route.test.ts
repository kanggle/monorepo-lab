/**
 * `GET /api/demo/backend-state` — 배너의 **방문 시점** 판정 (TASK-MONO-654).
 *
 * 🔴🔴 이 스위트에서 **문구만큼 중요한 칸이 `Cache-Control` 이다.** 이 티켓의 결함은
 *    「판정이 틀렸다」가 아니라 **「판정이 캐시된 사본에 굳었다」** 였다. 응답이 어디선가
 *    한 겹이라도 캐시되면 정적 사본 대신 **엣지 사본**에 같은 판정이 굳고, 그러면 이
 *    티켓이 고친 것이 **한 층 위에서 그대로 재발한다.**
 *    ⇒ 「200 을 준다」만 재면 그 재발을 못 잡는다.
 */
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';

const ORIGINAL_ENV = { ...process.env };

let fetchMock: ReturnType<typeof vi.fn>;

function stubControlPlane(body: unknown, ok = true) {
  fetchMock = vi.fn().mockResolvedValue({ ok, json: async () => body } as unknown as Response);
  vi.stubGlobal('fetch', fetchMock);
}

async function get(): Promise<Response> {
  const { GET } = await import('@/app/api/demo/backend-state/route');
  return GET();
}

beforeEach(() => {
  vi.resetModules();
  vi.unstubAllGlobals();
  delete process.env.DEMO_API_BASE;
});

afterEach(() => {
  vi.unstubAllGlobals();
  vi.resetModules();
  process.env = { ...ORIGINAL_ENV };
});

describe('GET /api/demo/backend-state', () => {
  it('데모 배포 + 백엔드 꺼짐 → unavailable', async () => {
    process.env.DEMO_API_BASE = 'https://control.example';
    stubControlPlane({ state: 'stopped' });

    const res = await get();
    expect(res.status).toBe(200);
    await expect(res.json()).resolves.toEqual({ state: 'unavailable' });
  });

  it('데모 배포 + 백엔드 켜짐 → running', async () => {
    process.env.DEMO_API_BASE = 'https://control.example';
    stubControlPlane({ state: 'running', ip: '13.125.1.2' });

    const res = await get();
    await expect(res.json()).resolves.toEqual({ state: 'running' });
  });

  it('🔴 로컬·CI(DEMO_API_BASE 없음) → not-demo, 그리고 컨트롤 플레인을 **안 부른다**', async () => {
    stubControlPlane({ state: 'stopped' });

    const res = await get();
    await expect(res.json()).resolves.toEqual({ state: 'not-demo' });
    // 🔴 판정은 «not-demo 를 돌려줬다» 가 아니라 **«아무 데도 안 물어봤다»** 다 —
    //    전자만 보면 판정 순서가 뒤집혀도 초록이다.
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('🔴 컨트롤 플레인 조회가 실패해도 **침묵하지 않는다** — unavailable 로 내려간다', async () => {
    process.env.DEMO_API_BASE = 'https://control.example';
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new Error('down')));

    const res = await get();
    await expect(res.json()).resolves.toEqual({ state: 'unavailable' });
  });

  // 🔴🔴 이 티켓의 결함이 재발하는 자리
  it('🔴🔴 응답이 캐시되지 않는다 — no-store', async () => {
    process.env.DEMO_API_BASE = 'https://control.example';
    stubControlPlane({ state: 'stopped' });

    const res = await get();
    const cc = res.headers.get('cache-control') ?? '';
    expect(cc).toContain('no-store');
  });

  it('🔴 라우트가 force-dynamic 이다 — 프리렌더되면 이 티켓이 그대로 재발한다', async () => {
    const mod = await import('@/app/api/demo/backend-state/route');
    expect(mod.dynamic).toBe('force-dynamic');
  });
});
