/**
 * `DemoBackendNotice` **껍데기** — 판정을 굽지 않는다 (TASK-MONO-654 § A 의 bite).
 *
 * =============================================================================
 * 🔴🔴 왜 § B 와 **다른 파일**인가 (TASK-MONO-708)
 * =============================================================================
 * 이 칸은 원래 `DemoBackendNotice.test.tsx` 안에 § A 로 함께 있었고, 클라이언트 자식을
 * 상수 마커로 바꾸려고 **테스트 안에서** `vi.resetModules()` → `vi.doMock(...)` →
 * 동적 `import()` 를 **호출마다** 반복했다. 같은 파일의 § B 는 반대로 `vi.doUnmock()` 을
 * 하고 진짜 클라이언트를 import 한다 ⇒ **한 파일이 같은 모듈을 모킹했다 풀었다** 한다.
 *
 * 2026-09-17 main CI 에서 그 칸이 **코드 변경 없이** 한 번 빨개졌다(#3902 머지 커밋,
 * 976 중 1). 실패 모양이 기전을 가리킨다:
 *
 *     AssertionError: expected '<div data-testid="host"><span data-te…'
 *                         to be '<div data-testid="host"></div>'
 *
 * 즉 **첫 호출(`up`)의 출력이 비었다** — 그 호출에서는 모킹이 안 먹어 진짜 클라이언트가
 * 렌더됐고(탐침 전이라 아무것도 안 그린다), 둘째 호출에서야 마커가 나왔다.
 * 🔴 원인을 «관측으로» 지목하지는 못했다(로컬 재현 0/30 · CI 이력은 재실행이 결론을 덮는다)
 * — 그래서 고친 것은 «원인» 이 아니라 **그 실패 모양이 가능한 구조**다:
 *
 *   · 모킹을 **파일 최상단 `vi.mock`**(호이스팅)으로 옮겨 테스트 실행 중 등록/해제가 없다.
 *   · 껍데기는 **정적 import** 한다 — 호출마다 모듈 그래프를 다시 세우지 않는다.
 *   · 클라이언트를 진짜로 쓰는 칸(§ B)은 이 파일에 **없다** — 한 파일이 같은 모듈에
 *     대해 두 세계를 오가지 않는다.
 *
 * 🔴 bite 는 그대로다: 껍데기에 조건이 생기면(=판정을 굽기 시작하면) 아래 첫 칸이 빨개진다.
 */

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render } from '@testing-library/react';

// 🔴 호이스팅된다 — 이 파일의 어떤 import 보다 먼저 등록되고, 테스트 중에 바뀌지 않는다.
vi.mock('../DemoBackendNoticeClient', () => ({
  DemoBackendNoticeClient: () => <span data-testid="client-marker" />,
}));

import { DemoBackendNotice } from '../DemoBackendNotice';

const ORIGINAL_ENV = { ...process.env };

beforeEach(() => {
  vi.unstubAllGlobals();
  delete process.env.DEMO_API_BASE;
});

afterEach(() => {
  vi.unstubAllGlobals();
  process.env = { ...ORIGINAL_ENV };
});

/**
 * 껍데기 **자신의** 출력만 잰다. 백엔드 상태는 `/status` 탐침으로 주어지는데, 껍데기가
 * 그것을 보지 않는다는 것이 이 파일의 주장이다.
 */
async function shellHtml(state: 'running' | 'stopped'): Promise<string> {
  process.env.DEMO_API_BASE = 'https://control.example';
  vi.stubGlobal(
    'fetch',
    vi.fn().mockResolvedValue({
      ok: true,
      json: async () =>
        state === 'running' ? { state: 'running', ip: '13.125.1.2' } : { state: 'stopped' },
    } as unknown as Response),
  );
  // 🔵 `await` 는 sync 반환도 그대로 통과시킨다 — 옛 async 판과 새 sync 판을 **둘 다**
  //    이 하네스로 잴 수 있어야 bite 가 성립한다.
  const el = await DemoBackendNotice();
  const { container } = render(<div data-testid="host">{el}</div>);
  return container.innerHTML;
}

describe('DemoBackendNotice (서버 껍데기) — 판정을 굽지 않는다', () => {
  it('🔴🔴 bite — 백엔드가 켜져 있든 꺼져 있든 껍데기의 출력이 **같다**', async () => {
    const up = await shellHtml('running');
    const down = await shellHtml('stopped');

    // 🔴 이것이 이 축의 핵심 단언이다. 옛 판(껍데기가 `resolveDemoBackendState()` 를
    //    await 하고 조건부로 배너를 그리던 판)은 두 값이 **다르다**.
    expect(down).toBe(up);

    // 🔵 대조군 — 출력이 «같다» 가 «둘 다 비었다» 로 성립하면 안 된다.
    expect(up).toContain('client-marker');
  });

  it('🔴 껍데기의 출력에 배너 문구가 **없다** — 판정은 여기서 안 내려진다', async () => {
    const down = await shellHtml('stopped');
    expect(down).not.toContain('데모 서버가 꺼져 있어');
    expect(down).not.toContain('demo-backend-notice');
  });
});
