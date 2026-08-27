/**
 * `DemoBackendNotice` (fan) — 데모 꺼짐을 **렌더된 출력으로** 판정한다 (`TASK-MONO-586` AC-3).
 *
 * 🔴 모델 속성이나 "함수가 무엇을 돌려주나" 로 판정하지 않는다. 방문자가 보는 것은 DOM 이고,
 *    이 저장소는 *"렌더는 되는데 아무것도 안 보인다"* 로 여러 번 데였다.
 *
 * 🔴 음성 칸(로컬·CI·켜짐)에 **"그래도 레이아웃은 렌더된다"** 대조군이 붙어 있다 — 배너가
 *    안 보이는 것이 *"조건이 거짓"* 때문인지 *"렌더 자체가 죽었다"* 때문인지 갈라야 한다.
 *    그 대조군이 없으면 위젯을 통째로 지워도 세 칸이 초록이다.
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

describe('DemoBackendNotice (fan)', () => {
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

  it('🔴 데모 배포 + 컨트롤 플레인 조회 실패 → 배너가 보인다 (침묵하지 않는다)', async () => {
    process.env.DEMO_API_BASE = 'https://control.example';
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new Error('down')));

    await renderNotice();
    expect(screen.getByTestId('demo-backend-notice')).toBeInTheDocument();
  });

  it('🔴 반쪽 응답(state=running 인데 ip 없음) → 배너가 보인다 — 주소를 못 만들면 꺼진 것과 같다', async () => {
    process.env.DEMO_API_BASE = 'https://control.example';
    stubStatus({ state: 'running' });

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

  it('🔵 로컬·CI 에서는 컨트롤 플레인을 **부르지도 않는다** — 없는 주소로 나가는 요청이 없어야 한다', async () => {
    const spy = vi.fn().mockResolvedValue({ ok: true, json: async () => ({}) } as unknown as Response);
    vi.stubGlobal('fetch', spy);

    await renderNotice();
    expect(spy).not.toHaveBeenCalled();
  });
});
