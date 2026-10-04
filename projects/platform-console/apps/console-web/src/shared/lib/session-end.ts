/**
 * 강제 재로그인이 **데모 종료** 때문일 때 어디로 보내는가 (`TASK-PC-FE-305`,
 * `console-integration-contract.md` § 2.6.2).
 *
 * =============================================================================
 * 🔴🔴 왜 이 판정이 생겼나
 * =============================================================================
 * 로그인한 채로 데모 백엔드가 꺼진 뒤 콘솔로 돌아오면, 갱신이 실패하고
 * `/login?error=session_expired` 에 닿아 「데모 서버가 종료되어 다시 로그인해야
 * 합니다」(TASK-PC-FE-299) 를 봤다. 그런데 그 순간의 로그인은 **성공할 수 없다** —
 * IdP 가 백엔드와 같이 꺼져 있다. 출구 없는 벽이다. 소유자의 기대는 로그인 없이 보는
 * 샘플 셸(ADR-MONO-074)이었다.
 *
 * =============================================================================
 * 🔵 판정 자리는 하나다 — 401 지점 53곳이 아니다
 * =============================================================================
 * 모든 강제 재로그인(갱신 실패 · 서버측 401 · 온보딩 가드)은 이미
 * `/login?error=session_expired` 하나로 모인다. 그래서 판정은 그 착지(`/login` 페이지)
 * 에서 한 번, 쿠키를 지우는 라우트(`/api/auth/demo-ended`)에서 한 번 더(쿠키를 지우는
 * GET 은 호출자를 믿지 않는다) — 둘 다 **이 파일의 같은 함수**를 부른다.
 *
 * =============================================================================
 * 🔴 신호의 한계 — 숨기지 않는다
 * =============================================================================
 * 신호는 `resolveDemoBackendState()` 다(DemoBackendNotice · PC-FE-299 문구와 같은 함수).
 * 그 `unavailable` 은 「컨트롤 플레인이 `state≠running` 이라고 답했다」와 「`/status`
 * 자체가 실패했다」를 **둘 다** 담는다(`infra/demo/backend-resolver/README.md` 표).
 * 콘솔은 공유 해석기를 통해서는 둘을 가를 수 없다 — 이 판정은 PC-FE-299 문구가 이미
 * 하던 `unavailable` 의 읽기를 그대로 물려받는다. 가르려면 공유 해석기를 바꿔야 한다
 * (이 티켓 밖 — 티켓 본문의 소유자 결정 항목).
 */

import type { DemoBackendState } from '@/shared/config/demo-backend';
import { resolveRefreshReturnPath } from '@/shared/lib/login-redirect';
import { RE_LOGIN_PATH } from '@/shared/lib/re-login';

/** 세션을 끝내고 샘플 셸로 보내는 라우트 핸들러(페이지는 쿠키를 못 지운다). */
export const DEMO_ENDED_PATH = '/api/auth/demo-ended';

/** 샘플 셸이 안내를 띄우는 표식 — `?signed_out=demo_stopped`. */
export const SIGNED_OUT_PARAM = 'signed_out';
export const SIGNED_OUT_DEMO_STOPPED = 'demo_stopped';

/**
 * 루프 상한. 라우트가 신호를 다시 읽어 `unavailable` 이 **아니면** 이 표식을 달아
 * `/login` 으로 돌려보내고, `/login` 은 이 표식이 있으면 다시 넘기지 않는다
 * (두 서버 인스턴스의 15초 캐시가 서로 다른 답을 들고 있는 경우의 핑퐁을 끊는다).
 */
export const DEMO_CHECKED_PARAM = 'demo_checked';

/** 판정 결과. 🔴 「샘플」은 세션을 **지운 뒤** 의 착지다 — 죽은 쿠키를 샘플로 재해석하지 않는다. */
export type SessionEndDestination = 'sample' | 'login';

/**
 * 🔴🔴 유일한 판정. 데모 백엔드가 꺼져 있다는 **실제 신호**(`unavailable`)일 때만 샘플.
 *
 * - `unavailable` → `sample` (로그인이 성공할 수 없다 — IdP 도 꺼져 있다)
 * - `running` → `login` (진짜 세션 만료 — 다시 로그인하면 된다)
 * - `starting` → `login` (켜지는 중이지 꺼진 것이 아니다 — PC-FE-299 의 같은 판단)
 * - `not-demo` → `login` (데모가 아닌 배포 — 물어볼 컨트롤 플레인이 없다)
 *
 * 갱신 에러의 모양은 입력이 **아니다** — 그것으로 데모 종료를 추측하지 않는다.
 */
export function sessionEndDestination(state: DemoBackendState): SessionEndDestination {
  return state === 'unavailable' ? 'sample' : 'login';
}

/**
 * 샘플 셸로 돌려보낼 경로 (CONSUME 측, 공격자 입력).
 *
 * 갱신 라우트와 **같은** 소독(`resolveRefreshReturnPath` — `//`·`/\`·절대 URL·`/login…`·
 * `/api/…` 거절)에 `/onboarding…` 거절을 하나 더 건다: 온보딩은 pre-operator 세션만
 * 들이므로 세션을 지운 방문자를 거기 보내면 다시 강제 재로그인으로 꺾인다.
 */
export function sampleReturnPath(raw: string | null | undefined): string {
  const path = resolveRefreshReturnPath(raw);
  return path === '/onboarding' || path.startsWith('/onboarding/') || path.startsWith('/onboarding?')
    ? '/'
    : path;
}

/** `/login` → 세션 종료 라우트. 목적지는 라우트가 다시 소독한다. */
export function buildDemoEndedRedirectFor(raw: string | null | undefined): string {
  const target = sampleReturnPath(raw);
  return target === '/'
    ? DEMO_ENDED_PATH
    : `${DEMO_ENDED_PATH}?redirect=${encodeURIComponent(target)}`;
}

/** 샘플 착지 경로에 안내 표식을 단다(기존 쿼리는 보존). */
export function withDemoSignedOutNotice(path: string): string {
  const url = new URL(path, 'http://placeholder.invalid');
  url.searchParams.set(SIGNED_OUT_PARAM, SIGNED_OUT_DEMO_STOPPED);
  return `${url.pathname}${url.search}${url.hash}`;
}

/** 라우트가 신호에 동의하지 않을 때의 착지 — 일반 강제 재로그인 + 루프 상한 표식. */
export function buildDemoCheckedReLoginFor(target: string): string {
  const url = new URL(RE_LOGIN_PATH, 'http://placeholder.invalid');
  url.searchParams.set(DEMO_CHECKED_PARAM, '1');
  if (target !== '/') url.searchParams.set('redirect', target);
  return `${url.pathname}${url.search}`;
}
