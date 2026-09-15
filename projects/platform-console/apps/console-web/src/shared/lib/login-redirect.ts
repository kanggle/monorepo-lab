/**
 * 레이아웃 가드의 **PRODUCE 측** 술어 — `?redirect=<path>` 를 만든다
 * (Gap D / F6 — `TASK-PC-FE-115`, 추출 `TASK-PC-FE-280`).
 *
 * =============================================================================
 * 🔴🔴 왜 `(console)/layout.tsx` 밖으로 나왔나
 * =============================================================================
 * 이 규칙은 서버 컴포넌트 안의 **비-export 함수**였고, 그래서 그것을 «지키는»
 * 테스트(`tests/unit/layout-login-redirect.test.ts`)가 로직을 **로컬에 재구현**해
 * 두고 그 재구현을 검사했다. 그 파일이 스스로 적어 둔 말이 증거다:
 *
 *     "Mirrors the sanitisation logic in layout.tsx buildLoginRedirect()."
 *     function buildLoginRedirectFrom(raw: string | null): string { … }
 *
 * ⇒ 진짜 함수가 **계산에 한 번도 안 들어갔다.** layout 의 규칙이 바뀌어도 그 스위트는
 * 초록이다. `TASK-PC-FE-279` 가 고친 것과 같은 부류이고, 그 티켓의 판별자
 * («`@/` 에서 아무것도 import 하지 않는 테스트»)가 이 파일을 찾아냈다.
 *
 * 고침은 테스트를 손보는 것이 아니라 **정의를 하나로 만드는 것**이다: 순수한 부분을
 * 여기로 빼고, layout 은 헤더를 읽어 이 함수에 넘기기만 한다. 이제 테스트와 제품이
 * **같은 함수**를 쓴다.
 *
 * =============================================================================
 * 🔵 `sanitizeReturnPath` 와 **합치지 않는다** — 그것은 결정이고 기록돼 있다
 * =============================================================================
 * `shared/lib/return-path.ts` 가 그 이유를 이미 적어 뒀다:
 *
 * > This is the CONSUME side (the attacker-controllable `?redirect=` query
 * > param). The layout guard `buildLoginRedirect()` … is the PRODUCE side — it
 * > derives the param from the trusted `x-pathname` header and layers on extra
 * > destination rules (`/login`, `/api/`), so it stays a **deliberately
 * > separate, stricter predicate** rather than a call site here.
 *
 * 🔴 그래서 두 규칙이 다른 것은 **결함이 아니다**(`sanitizeReturnPath` 는 `/\` 를
 * 거르고 이쪽은 안 거른다 — 이쪽의 입력은 미들웨어가 넣은 **신뢰된** `x-pathname`
 * 이고, 소비 측이 어차피 다시 거른다). 여기서 «일관성» 을 명분으로 합치지 마라.
 *
 * =============================================================================
 * 🔴 `TASK-MONO-674` — 가드의 목적지가 **둘**이 됐다
 * =============================================================================
 * 리프레시 쿠키가 있으면 가드는 `/login` 이 아니라 `GET /api/auth/refresh` 로 보낸다
 * (유휴 만료 = 브라우저가 30분짜리 액세스 쿠키를 먼저 버린 상태). 두 목적지가 **같은
 * 술어**로 경로를 싣도록 술어를 {@link isGuardReturnPath} 하나로 뺐다 — 한쪽만 고치면
 * 다른 쪽이 조용히 열린다(`TASK-PC-FE-253` 이 겪은 그 모양이다).
 *
 * 리프레시 라우트는 그 경로를 **쿼리에서** 다시 읽으므로 거기서는 입력이 공격자 손에
 * 있다 ⇒ {@link resolveRefreshReturnPath} 가 소비 측 소독(`sanitizeReturnPath`)과 이
 * 술어를 **둘 다** 건다. 합친 것이 아니라 **겹쳐 건 것**이다.
 */

import { sanitizeReturnPath } from '@/shared/lib/return-path';

/** 유휴 만료 갱신 라우트(`GET`) — `TASK-MONO-674`. `POST` 는 브라우저 401 재시도용. */
export const SESSION_REFRESH_PATH = '/api/auth/refresh';

/** 회전 경합 재시도 표식. 이 값이 붙은 요청은 **다시 갱신을 시도하지 않는다**(루프 상한). */
export const REFRESH_RETRY_PARAM = 'retry';

/**
 * 가드가 목적지로 실어 보낼 수 있는 경로인가 — 같은 사이트 절대 경로만. 거절:
 *   - `/` 로 시작하지 않는다 (절대 URL)
 *   - `//…` (프로토콜 상대 — 브라우저가 외부 오리진으로 접는다)
 *   - `/login…` (의미 없는 자기 참조)
 *   - `/api/…` (페이지 목적지가 아니다 — 🔴 갱신 라우트 자신을 목적지로 삼는 순환도 여기서 막힌다)
 *   - `null` / 빈 값
 */
export function isGuardReturnPath(raw: string | null): raw is string {
  return (
    raw !== null &&
    raw.startsWith('/') &&
    !raw.startsWith('//') &&
    !raw.startsWith('/login') &&
    !raw.startsWith('/api/')
  );
}

/**
 * `x-pathname` 원본 → 로그인 리다이렉트 목적지.
 *
 * 같은 사이트 절대 경로만 `?redirect=` 로 실어 보낸다. 그 외는 전부 맨 `/login`
 * (거절 목록은 {@link isGuardReturnPath}).
 */
export function buildLoginRedirectFor(raw: string | null): string {
  if (!isGuardReturnPath(raw)) return '/login';
  return `/login?redirect=${encodeURIComponent(raw)}`;
}

/**
 * `x-pathname` 원본 → 유휴 만료 갱신 라우트 목적지 (`TASK-MONO-674`).
 *
 * 가드가 «세션 불완전 + 리프레시 쿠키 있음» 을 볼 때만 쓴다. 경로 술어는
 * {@link buildLoginRedirectFor} 와 **같다** — 거절된 경로는 파라미터 없이 보낸다
 * (라우트가 `/` 로 복귀시킨다).
 */
export function buildSessionRefreshRedirectFor(raw: string | null): string {
  if (!isGuardReturnPath(raw)) return SESSION_REFRESH_PATH;
  return `${SESSION_REFRESH_PATH}?redirect=${encodeURIComponent(raw)}`;
}

/**
 * 갱신 라우트가 **쿼리에서 읽은** `redirect` → 복귀 경로 (CONSUME 측, 공격자 입력).
 *
 * 소비 측 소독(`sanitizeReturnPath` — `//`, `/\`, 절대 URL) 뒤에 가드 술어
 * (`/login…`, `/api/…`)를 한 번 더 건다. 어느 쪽에서든 거절되면 `/`.
 */
export function resolveRefreshReturnPath(raw: string | null | undefined): string {
  const safe = sanitizeReturnPath(raw);
  return isGuardReturnPath(safe) ? safe : '/';
}
