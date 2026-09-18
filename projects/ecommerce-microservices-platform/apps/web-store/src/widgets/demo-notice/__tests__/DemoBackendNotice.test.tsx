/**
 * `DemoBackendNotice` — 데모 꺼짐을 **렌더된 출력으로** 판정한다 (TASK-MONO-580 AC-4).
 *
 * 🔴 모델 속성이나 "함수가 무엇을 돌려주나" 로 판정하지 않는다. 방문자가 보는 것은 DOM 이고,
 *    이 저장소는 *"렌더는 되는데 아무것도 안 보인다"* 로 여러 번 데였다.
 *
 * 🔴 음성 칸에 **"그래도 레이아웃은 렌더된다"** 대조군이 붙어 있다 — 배너가 안 보이는 것이
 *    *"조건이 거짓"* 때문인지 *"렌더 자체가 죽었다"* 때문인지 갈라야 한다.
 *
 * =============================================================================
 * 🔴🔴 TASK-MONO-654 — 이 스위트는 두 축으로 나뉜다
 * =============================================================================
 * 이 티켓 전에는 판정이 **서버 컴포넌트**에 있었고, 그래서 프리렌더된 사본에 구워졌다.
 * 이제 축이 둘이다:
 *
 *  § A **껍데기가 판정하지 않는가** — AC-3 의 bite. 백엔드 상태를 바꿔도 서버 껍데기의
 *      출력이 **안 변해야** 한다. 변하면 그 값은 프리렌더 시점에 구워질 수 있는 값이다.
 *  § B **클라이언트가 방문 시점에 판정하는가** — 위/아래 두 상태를 주고 배너의 유무를
 *      비교한다(AC-3 의 「실행 비교」).
 *
 * 🔴 § A 와 § B 는 **서로를 대신하지 못한다.** § B 만 있으면 누군가 판정을 서버로
 *    되돌려도 초록이고(클라이언트 컴포넌트는 여전히 잘 동작하므로), § A 만 있으면
 *    배너가 아예 안 뜨는 판이 초록이다.
 */

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';

const ORIGINAL_ENV = { ...process.env };

beforeEach(() => {
  // 🔵 이 파일은 이제 **클라이언트를 모킹하지 않는다** (TASK-MONO-708). 껍데기 축(§ A)은
  //    `DemoBackendNoticeShell.test.tsx` 로 나갔고, 그쪽은 파일 최상단 `vi.mock` 으로 호이스팅한다.
  //    🔴 한 파일이 같은 모듈을 «모킹했다 풀었다» 하는 구조가 2026-09-17 main CI 의 무작위
  //    빨강(976 중 1)을 가능하게 한 모양이었다 — 그 구조를 없앴다.
  vi.doUnmock('../DemoBackendNoticeClient'); // 남의 파일이 남긴 등록이 있어도 안전하게
  vi.resetModules();
  vi.unstubAllGlobals();
  delete process.env.DEMO_API_BASE;
});

afterEach(() => {
  vi.unstubAllGlobals();
  vi.resetModules();
  process.env = { ...ORIGINAL_ENV };
});

// =============================================================================
// § B — 클라이언트가 방문 시점에 판정한다  (AC-3 의 실행 비교 + 대조군)
// =============================================================================
describe('DemoBackendNoticeClient — 방문 시점 판정', () => {
  /** 탐침 라우트의 응답을 세운다. `never` 는 «아직 안 돌아왔다» 를 만든다. */
  function stubProbe(result: { state: string } | 'reject' | 'never') {
    if (result === 'never') {
      vi.stubGlobal(
        'fetch',
        vi.fn().mockReturnValue(new Promise<never>(() => {})),
      );
      return;
    }
    if (result === 'reject') {
      vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new Error('offline')));
      return;
    }
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({ ok: true, json: async () => result } as unknown as Response),
    );
  }

  async function renderClient() {
    vi.doUnmock('../DemoBackendNoticeClient');
    vi.resetModules();
    const { DemoBackendNoticeClient } = await import('../DemoBackendNoticeClient');
    render(
      <div data-testid="host">
        <DemoBackendNoticeClient />
      </div>,
    );
  }

  it('🔴 탐침이 «꺼짐» 을 주면 배너가 보이고 이유를 말한다', async () => {
    stubProbe({ state: 'unavailable' });
    await renderClient();

    const notice = await screen.findByTestId('demo-backend-notice');
    expect(notice).toHaveTextContent('데모 서버가 꺼져 있어');
    // 🔵 "고장" 이 아니라 **무엇을 하면 되는지**를 말해야 한다.
    expect(notice).toHaveTextContent('데모 시작');
  });

  // 🔴🔴 TASK-MONO-642 — 위 두 단언만으로는 **그 티켓의 결함을 못 잡는다.**
  //    옛 문구(「상품 데이터를 불러올 수 없습니다」)도 「데모 서버가 꺼져 있어」와
  //    「데모 시작」을 그대로 포함했다. 즉 두 리터럴은 결함이 있든 없든 통과한다.
  //    🔵 리터럴 고정 자체는 유지한다 — 배너의 **문장이 방문자와의 계약**이다.
  //    🔴 654 가 문구를 **옮기기만** 했으므로 이 칸은 그대로 따라와야 한다. 안 따라오면
  //       「옮기면서 조용히 문구가 바뀌었다」를 아무도 못 잡는다.
  it('🔴 배너가 «그리고 있는 것»과 어긋나지 않는다 — 「불러올 수 없」다고 말하지 않는다', async () => {
    stubProbe({ state: 'unavailable' });
    await renderClient();
    const notice = await screen.findByTestId('demo-backend-notice');

    // (a) 화면이 그리는 것을 말한다
    expect(notice).toHaveTextContent('샘플');
    // (b) 🔴 **이 칸이 bite 다.** 옛 문구를 되살리면 여기서 빨개진다.
    expect(notice.textContent).not.toContain('불러올 수 없');
    // (c) 🔴 잠긴 사실을 **안 지웠는가** — 이것까지 지우면 방문자가 장바구니·로그인이
    //     왜 안 되는지 모른다. 배너를 부드럽게 만드는 것이 목적이 아니다.
    expect(notice).toHaveTextContent('로그인 후 기능');
  });

  // ===========================================================================
  // 🔴🔴 TASK-MONO-668 — 「켜지는 중」은 「켜졌다」와 **다른 화면**이다 (AC-3 실행 비교)
  // ===========================================================================
  // 🔴 선언 grep 이 아니라 **렌더된 DOM 두 개를 비교한다.** 같은 하네스에 running 과
  //    starting 을 주고 출력이 다른지를 본다 — 「starting 분기가 코드에 있다」는 이것을 못 잰다.
  async function renderedFor(state: string): Promise<string> {
    stubProbe({ state });
    const { unmount, container } = await (async () => {
      vi.doUnmock('../DemoBackendNoticeClient');
      vi.resetModules();
      const { DemoBackendNoticeClient } = await import('../DemoBackendNoticeClient');
      return render(
        <div data-testid="host">
          <DemoBackendNoticeClient />
        </div>,
      );
    })();
    await waitFor(() => expect(globalThis.fetch).toHaveBeenCalled());
    // 탐침 결과가 반영될 때까지 한 틱 기다린다 — starting 이면 배너가, running 이면 무(無)가 선다.
    await new Promise((r) => setTimeout(r, 0));
    const html = container.innerHTML;
    unmount();
    return html;
  }

  it('🔴🔴 실행 비교 — 탐침이 «켜지는 중» 을 주면 «켜짐» 과 **다른** 화면이 나온다', async () => {
    const running = await renderedFor('running');
    const starting = await renderedFor('starting');

    expect(starting).not.toBe(running);
    // 🔵 «다르다» 가 «엉뚱한 배너» 로 성립하면 안 된다 — 무엇이 달라졌는지 단언한다.
    expect(starting).toContain('데모 서버가 켜지는 중입니다');
    expect(running).not.toContain('켜지는 중');
  });

  it('🔴 «켜지는 중» 배너는 «꺼져 있어» 배너가 **아니다** — 켜라고 말하지 않는다', async () => {
    stubProbe({ state: 'starting' });
    await renderClient();

    const notice = await screen.findByTestId('demo-backend-starting');
    expect(notice).toHaveTextContent('데모 서버가 켜지는 중입니다');
    expect(notice.getAttribute('role')).toBe('status');
    // 🔴 이미 켜졌다 — 「서버를 켠 뒤」는 방문자를 론처로 돌려보내 중복 기동을 누르게 한다.
    expect(notice.textContent).not.toContain('꺼져 있어');
    expect(notice.textContent).not.toContain('켠 뒤');
    // 🔴 켜지는 중에 무엇이 그려지는지는 잰 적이 없다 — 「샘플」을 주장하지 않는다(642 규칙).
    expect(notice.textContent).not.toContain('샘플');
    expect(screen.queryByTestId('demo-backend-notice')).toBeNull();
  });

  it('🔵 대조군 — 탐침이 «꺼짐» 을 주면 «켜지는 중» 배너는 없다 (두 값을 한 화면으로 뭉치지 않는다)', async () => {
    stubProbe({ state: 'unavailable' });
    await renderClient();

    await screen.findByTestId('demo-backend-notice');
    expect(screen.queryByTestId('demo-backend-starting')).toBeNull();
  });

  it('🔵 대조군 — 탐침이 «켜짐» 을 주면 배너가 없다', async () => {
    stubProbe({ state: 'running' });
    await renderClient();

    await waitFor(() => expect(globalThis.fetch).toHaveBeenCalled());
    expect(screen.queryByTestId('demo-backend-notice')).toBeNull();
    // 대조군의 대조군: 렌더 자체는 살아 있다.
    expect(screen.getByTestId('host')).toBeInTheDocument();
  });

  it('🔴 로컬·CI(not-demo) → 배너 없음 — 데모가 아닌 곳에서 "꺼졌다" 는 거짓말이다', async () => {
    stubProbe({ state: 'not-demo' });
    await renderClient();

    await waitFor(() => expect(globalThis.fetch).toHaveBeenCalled());
    expect(screen.queryByTestId('demo-backend-notice')).toBeNull();
    expect(screen.getByTestId('host')).toBeInTheDocument();
  });

  // 🔴🔴 AC-1 의 셋째 칸 — 「아직 모른다」와 「물어봤는데 꺼져 있다」를 **같은 화면으로
  //    만들지 마라**(TASK-MONO-636 이 이름 붙인 축). 탐침 전에는 아무 말도 하지 않는다.
  it('🔴 탐침이 아직 안 돌아왔으면 배너가 **없다** — 「모른다」는 「꺼졌다」가 아니다', async () => {
    stubProbe('never');
    await renderClient();

    expect(screen.queryByTestId('demo-backend-notice')).toBeNull();
    expect(screen.getByTestId('host')).toBeInTheDocument();
  });

  // 🔴🔴 이것은 위 칸과 **다른 축**이다. 컨트롤 플레인 조회 실패는 서버가 판정해
  //    `unavailable` 로 내려보내고(그때 배너는 옳다), 이 칸은 그 판정을 **받지도 못한**
  //    경우다. 원인을 모르는데 "데모 서버가 꺼져 있어" 라고 말하면 지어내는 것이다.
  it('🔴 탐침 자체가 실패하면 배너가 **없다** — 관측 안 한 원인을 지어내지 않는다', async () => {
    stubProbe('reject');
    await renderClient();

    await waitFor(() => expect(globalThis.fetch).toHaveBeenCalled());
    expect(screen.queryByTestId('demo-backend-notice')).toBeNull();
    expect(screen.getByTestId('host')).toBeInTheDocument();
  });
});
