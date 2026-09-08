/**
 * `DemoBackendNotice` — 데모 꺼짐을 **렌더된 출력으로** 판정한다 (TASK-MONO-580 AC-4).
 *
 * 🔴 모델 속성이나 "함수가 무엇을 돌려주나" 로 판정하지 않는다. 방문자가 보는 것은 DOM 이고,
 *    이 저장소는 *"렌더는 되는데 아무것도 안 보인다"* 로 여러 번 데였다.
 *
 * 🔴 음성 칸(로컬·CI)에 **"그래도 레이아웃은 렌더된다"** 대조군이 붙어 있다 — 배너가 안
 *    보이는 것이 *"조건이 거짓"* 때문인지 *"렌더 자체가 죽었다"* 때문인지 갈라야 한다.
 */

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen } from '@testing-library/react';

const ORIGINAL_ENV = { ...process.env };

async function renderNotice() {
  const { DemoBackendNotice } = await import('../DemoBackendNotice');
  // 서버 컴포넌트는 Promise 를 돌려준다 — await 해서 엘리먼트를 얻는다.
  const el = await DemoBackendNotice();
  render(<div data-testid="host">{el}</div>);
}

function stubStatus(body: unknown, ok = true) {
  vi.stubGlobal(
    'fetch',
    vi.fn().mockResolvedValue({ ok, json: async () => body } as unknown as Response),
  );
}

beforeEach(() => {
  vi.resetModules();
  vi.unstubAllGlobals();
  delete process.env.DEMO_API_BASE;
});

afterEach(() => {
  vi.unstubAllGlobals();
  process.env = { ...ORIGINAL_ENV };
});

describe('DemoBackendNotice', () => {
  it('🔴 데모 배포 + 백엔드 꺼짐 → 배너가 보이고 이유를 말한다', async () => {
    process.env.DEMO_API_BASE = 'https://control.example';
    stubStatus({ state: 'stopped' });

    await renderNotice();

    const notice = screen.getByTestId('demo-backend-notice');
    expect(notice).toBeInTheDocument();
    expect(notice).toHaveTextContent('데모 서버가 꺼져 있어');
    // 🔵 "고장" 이 아니라 **무엇을 하면 되는지**를 말해야 한다.
    expect(notice).toHaveTextContent('데모 시작');
  });

  // 🔴🔴 TASK-MONO-642 — 위 두 단언만으로는 **이 티켓의 결함을 못 잡는다.**
  //    옛 문구(「상품 데이터를 불러올 수 없습니다」)도 「데모 서버가 꺼져 있어」와
  //    「데모 시작」을 그대로 포함했다. 즉 두 리터럴은 결함이 있든 없든 통과한다.
  //    🔵 리터럴 고정 자체는 유지한다 — 배너의 **문장이 방문자와의 계약**이다.
  //       다만 이 티켓이 고치는 부분을 덮는 칸을 **더한다.**
  it('🔴 배너가 «그리고 있는 것»과 어긋나지 않는다 — 「불러올 수 없」다고 말하지 않는다', async () => {
    process.env.DEMO_API_BASE = 'https://control.example';
    stubStatus({ state: 'stopped' });

    await renderNotice();
    const notice = screen.getByTestId('demo-backend-notice');

    // (a) 화면이 그리는 것을 말한다
    expect(notice).toHaveTextContent('샘플');
    // (b) 🔴 **이 칸이 bite 다.** 옛 문구를 되살리면 여기서 빨개진다.
    expect(notice.textContent).not.toContain('불러올 수 없');
    // (c) 🔴 잠긴 사실을 **안 지웠는가** — 이것까지 지우면 방문자가 장바구니·로그인이
    //     왜 안 되는지 모른다. 배너를 부드럽게 만드는 것이 목적이 아니다.
    expect(notice).toHaveTextContent('실시간 기능');
  });

  it('🔴 데모 배포 + 컨트롤 플레인 조회 실패 → 배너가 보인다 (침묵하지 않는다)', async () => {
    process.env.DEMO_API_BASE = 'https://control.example';
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new Error('down')));

    await renderNotice();
    expect(screen.getByTestId('demo-backend-notice')).toBeInTheDocument();
  });

  it('데모 배포 + 백엔드 켜짐 → 배너 없음', async () => {
    process.env.DEMO_API_BASE = 'https://control.example';
    stubStatus({ state: 'running', ip: '13.125.1.2' });

    await renderNotice();
    expect(screen.queryByTestId('demo-backend-notice')).toBeNull();
    // 대조군: 렌더 자체는 살아 있다.
    expect(screen.getByTestId('host')).toBeInTheDocument();
  });

  it('🔴 로컬·CI(DEMO_API_BASE 없음) → 배너 없음 — 데모가 아닌 곳에서 "꺼졌다" 는 거짓말이다', async () => {
    stubStatus({ state: 'stopped' });

    await renderNotice();
    expect(screen.queryByTestId('demo-backend-notice')).toBeNull();
    expect(screen.getByTestId('host')).toBeInTheDocument();
  });
});
