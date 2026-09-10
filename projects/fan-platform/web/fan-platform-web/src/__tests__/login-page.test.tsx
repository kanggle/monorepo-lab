/**
 * `/login` — 실패의 **원인을 이름대는가** (`TASK-FAN-FE-020`).
 *
 * 🔴 판정은 **렌더된 DOM** 으로 한다. 형제(`DemoBackendNotice.test.tsx`)가 적어 둔 이유
 *    그대로다 — 이 저장소는 *"렌더는 되는데 아무것도 안 보인다"* 로 여러 번 데였다.
 *
 * 🔴 음성 칸마다 **대조군**(`oidc-signin` 버튼이 여전히 있다)을 붙인다. 그게 없으면
 *    페이지를 통째로 깨뜨려도 "경고가 안 보인다" 칸들이 전부 초록이 된다.
 *
 * 🔵 해석기는 mock 하지 않는다 — `DEMO_API_BASE` + stub `fetch` 로 **진짜 해석기**를
 *    태운다(형제 테스트와 같은 축). 해석기를 mock 하면 「데모 판정이 실제로 저 값에
 *    달려 있는가」를 재지 못하고 내 mock 을 재게 된다.
 */

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen } from '@testing-library/react';

// Auth.js 는 이 테스트의 대상이 아니다 — 모듈 로드 부작용만 끊는다.
vi.mock('@/shared/auth/auth', () => ({ signIn: vi.fn() }));

const ORIGINAL_ENV = { ...process.env };

function stubStatus(body: unknown, ok = true) {
  vi.stubGlobal(
    'fetch',
    vi.fn().mockResolvedValue({ ok, json: async () => body } as unknown as Response),
  );
}

async function renderLogin(search: { from?: string; error?: string } = {}) {
  const { default: LoginPage } = await import('@/app/(auth)/login/page');
  const el = await LoginPage({ searchParams: Promise.resolve(search) });
  render(<div data-testid="host">{el}</div>);
}

/** 음성 칸의 대조군 — 페이지가 살아서 렌더됐다는 증거. */
function expectPageStillRendered() {
  expect(screen.getByTestId('oidc-signin')).toBeInTheDocument();
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

describe('/login — 데모가 꺼져 있을 때 (AC-2 / AC-3)', () => {
  it('🔴 error 없이 들어와도 데모가 꺼져 있으면 **미리** 말한다 — 누르기 전에 알려준다', async () => {
    process.env.DEMO_API_BASE = 'https://control.example';
    stubStatus({ state: 'stopped' });

    await renderLogin();

    const notice = screen.getByTestId('login-demo-off');
    expect(notice).toHaveTextContent('데모 서버가 꺼져 있어');
    // 🔵 "고장" 이 아니라 **무엇을 하면 되는지**.
    expect(notice).toHaveTextContent('데모 시작');
  });

  it('🔴🔴 `?error=Configuration` + 데모 꺼짐 → 데모 문구만. generic 「잠시 후 다시 시도」는 **동시에 뜨지 않는다** (AC-3)', async () => {
    process.env.DEMO_API_BASE = 'https://control.example';
    stubStatus({ state: 'stopped' });

    await renderLogin({ error: 'Configuration' });

    expect(screen.getByTestId('login-demo-off')).toBeInTheDocument();
    // 🔴 이 칸이 이 티켓의 본체다. 두 문장이 같이 뜨면 화면이 스스로 모순된다 —
    //    하나는 "기다리면 된다", 다른 하나는 "켜야 한다".
    expect(screen.queryByTestId('login-error')).toBeNull();
    expect(screen.getByTestId('host').textContent).not.toContain(
      '잠시 후 다시 시도해주세요',
    );
  });

  it('🔴 데모 문구는 **재시도를 약속하지 않는다** — 꺼진 동안 재시도는 같은 결과다', async () => {
    process.env.DEMO_API_BASE = 'https://control.example';
    stubStatus({ state: 'stopped' });

    await renderLogin({ error: 'Configuration' });

    // 🔴 bite: 옛 문구("잠시 후 다시 시도해주세요")로 되돌리면 빨개진다.
    expect(screen.getByTestId('login-demo-off')).toHaveTextContent(
      '다시 시도해도 같은 결과입니다',
    );
  });

  it('🔴 반쪽 응답(state=running 인데 ip 없음) → 꺼진 것으로 말한다 — 주소를 못 만들면 못 켠 것이다', async () => {
    process.env.DEMO_API_BASE = 'https://control.example';
    stubStatus({ state: 'running' });

    await renderLogin({ error: 'Configuration' });
    expect(screen.getByTestId('login-demo-off')).toBeInTheDocument();
  });
});

describe('/login — 코드별 문구와 fallback (AC-1)', () => {
  // 🔵 아래 칸들은 전부 데모가 **켜져 있는** 상태다. 그래야 코드 분기가 도달된다.
  beforeEach(() => {
    process.env.DEMO_API_BASE = 'https://control.example';
    stubStatus({ state: 'running', ip: '13.125.1.2' });
  });

  it('🔴 데모가 켜져 있는데 Configuration 이면 그것은 **진짜 설정 결함**이라 다르게 말한다', async () => {
    await renderLogin({ error: 'Configuration' });

    const alert = screen.getByTestId('login-error');
    expect(alert).toHaveTextContent('IAM 인증 서버에 연결할 수 없습니다');
    // 🔴 데모 문구가 진짜 결함을 **가리지 않는다**.
    expect(screen.queryByTestId('login-demo-off')).toBeNull();
  });

  it('AccessDenied 는 권한 문제로 말한다 — 재시도를 권하지 않는다', async () => {
    await renderLogin({ error: 'AccessDenied' });
    expect(screen.getByTestId('login-error')).toHaveTextContent(
      '이 계정으로는 로그인할 수 없습니다',
    );
  });

  it('🔴🔴 알 수 없는 코드도 **조용하지 않다** — fallback 이 이 AC 의 본체다', async () => {
    await renderLogin({ error: 'SomeCodeAuthjsAddsLater' });

    const alert = screen.getByTestId('login-error');
    expect(alert).toBeInTheDocument();
    expect(alert).toHaveTextContent('로그인에 실패했습니다');
  });

  it('error 파라미터가 없으면 경고가 없다 (대조군: 페이지는 렌더된다)', async () => {
    await renderLogin();

    expect(screen.queryByTestId('login-error')).toBeNull();
    expect(screen.queryByTestId('login-demo-off')).toBeNull();
    expectPageStillRendered();
  });
});

describe('/login — 데모가 아닌 배포 (AC-4)', () => {
  it('🔴 로컬·CI(DEMO_API_BASE 없음) → 데모 문구 없음. 데모가 아닌 곳에서 "꺼졌다" 는 거짓말이다', async () => {
    stubStatus({ state: 'stopped' });

    await renderLogin({ error: 'Configuration' });

    expect(screen.queryByTestId('login-demo-off')).toBeNull();
    // 코드별 문구는 정상 동작해야 한다 — 데모가 아니어도 실패는 설명돼야 한다.
    expect(screen.getByTestId('login-error')).toHaveTextContent(
      'IAM 인증 서버에 연결할 수 없습니다',
    );
    expectPageStillRendered();
  });

  it('🔵 로컬·CI 에서는 컨트롤 플레인을 **부르지도 않는다**', async () => {
    const spy = vi
      .fn()
      .mockResolvedValue({ ok: true, json: async () => ({}) } as unknown as Response);
    vi.stubGlobal('fetch', spy);

    await renderLogin();
    expect(spy).not.toHaveBeenCalled();
  });
});
