/**
 * `TASK-PC-FE-278` — **재로그인이 재로그인이 되는가.**
 *
 * -----------------------------------------------------------------------------
 * 무엇을 재는가
 * -----------------------------------------------------------------------------
 * `console-integration-contract` 는 스무 곳 넘게 *"`401` → forced **whole-session**
 * re-login (no partial authed state)"* 를 요구하고, § 2.4.6/§ 2.4.7 은 **"never a
 * re-login loop"** 라고 못박았다. 그런데 코드가 하던 일은 이랬다:
 *
 * ```
 *   /ecommerce ──401──▶ redirect('/login') ──/login 이 쿠키만 보고──▶ /console
 * ```
 *
 * `isAuthenticated()` 는 **백엔드에 묻지 않는다**. 백엔드가 토큰을 거절해도 쿠키는
 * 멀쩡하므로 재로그인하러 보낸 사람이 카탈로그로 되돌아왔다 — 반짝임 하나, 설명 0,
 * 세션은 죽은 채로.
 *
 * 🔴 **이 스위트의 본체는 「루프가 끊겼는가」이지 「문구가 보이는가」가 아니다.**
 *    문구만 재면 `redirect('/console')` 를 되살려도 초록일 수 있다.
 *
 * 🔴 **대조군이 이 파일의 절반이다** — 마커 없는 방문(운영자가 직접 `/login` 을 친 경우)은
 *    **예전 그대로** `/console` 로 가야 한다. 그 칸이 없으면 「루프를 끊었다」와
 *    「단락 회로를 통째로 지웠다」가 같은 초록으로 보인다.
 */

import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import { SESSION_EXPIRED, RE_LOGIN_PATH } from '@/shared/lib/re-login';

const redirectMock = vi.fn();
vi.mock('next/navigation', () => ({
  redirect: (path: string) => {
    redirectMock(path);
    throw new Error(`REDIRECT:${path}`);
  },
}));

const isAuthenticatedMock = vi.fn();
vi.mock('@/shared/lib/session', () => ({
  isAuthenticated: () => isAuthenticatedMock(),
}));

vi.mock('next/link', () => ({
  default: ({ children, href }: { children: React.ReactNode; href: string }) => (
    <a href={href}>{children}</a>
  ),
}));

// 데모 위젯 둘은 이 스위트의 대상이 아니다 — 각자 자기 테스트가 있다.
// 🔴 **동기** 컴포넌트로 목한다. `async () => null` 로 두면 React 가 async Client
//    Component 로 읽어 suspend 하고, 렌더 결과가 통째로 비어 `host` 조차 못 찾는다
//    (실제로 밟았다). 실물은 async 서버 컴포넌트지만 여기서 재는 것은 그들이 아니다.
vi.mock('@/widgets/demo-notice/DemoBackendNotice', () => ({
  DemoBackendNotice: () => null,
}));
vi.mock('@/widgets/demo-credentials/DemoLoginCredentials', () => ({
  DemoLoginCredentials: () => null,
}));

async function renderLogin(sp: { error?: string; redirect?: string } = {}) {
  const { default: LoginPage } = await import('@/app/(auth)/login/page');
  const el = await LoginPage({ searchParams: Promise.resolve(sp) });
  render(<div data-testid="host">{el}</div>);
}

beforeEach(() => {
  redirectMock.mockClear();
  isAuthenticatedMock.mockReset();
});

describe('재로그인 루프 (TASK-PC-FE-278)', () => {
  it('🔴🔴 401 마커를 달고 오면 쿠키가 남아 있어도 /console 로 되튕기지 않는다 — 루프의 심장', async () => {
    // 쿠키는 살아 있다. 백엔드가 거절했을 뿐이고, `isAuthenticated()` 는 그것을 모른다.
    isAuthenticatedMock.mockResolvedValue(true);

    await renderLogin({ error: SESSION_EXPIRED });

    // 🔴 이 단언이 본체다. 예전 코드는 여기서 '/console' 로 꺾였다.
    expect(redirectMock).not.toHaveBeenCalled();
    // 그리고 방문자에게 실제로 재로그인 수단이 주어진다.
    expect(screen.getByTestId('host')).toBeInTheDocument();
  });

  it('🔴 그리고 왜 로그아웃됐는지 말한다 — 반짝임만 남기지 않는다', async () => {
    isAuthenticatedMock.mockResolvedValue(true);

    await renderLogin({ error: SESSION_EXPIRED });

    expect(screen.getByTestId('host')).toHaveTextContent('세션이 만료');
  });

  it('🔵 대조군 — 마커가 없으면 예전 그대로 /console 로 보낸다 (편의를 뺏지 않았다)', async () => {
    isAuthenticatedMock.mockResolvedValue(true);

    await expect(renderLogin()).rejects.toThrow('REDIRECT:/console');
    expect(redirectMock).toHaveBeenCalledWith('/console');
  });

  it('🔵 대조군 — 다른 error 코드는 단락 회로를 그대로 탄다 (마커만 특별하다)', async () => {
    isAuthenticatedMock.mockResolvedValue(true);

    await expect(renderLogin({ error: 'state_mismatch' })).rejects.toThrow(
      'REDIRECT:/console',
    );
  });

  it('익명 방문자는 마커와 무관하게 로그인 화면을 본다', async () => {
    isAuthenticatedMock.mockResolvedValue(false);

    await renderLogin({ error: SESSION_EXPIRED });

    expect(redirectMock).not.toHaveBeenCalled();
    expect(screen.getByTestId('host')).toHaveTextContent('세션이 만료');
  });

  it('🔵 마커 상수와 401 지점들이 쓰는 경로가 어긋나지 않는다', () => {
    expect(RE_LOGIN_PATH).toBe(`/login?error=${SESSION_EXPIRED}`);
    expect(RE_LOGIN_PATH).toBe('/login?error=session_expired');
  });
});
