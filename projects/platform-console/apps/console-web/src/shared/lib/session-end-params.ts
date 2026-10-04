/**
 * `TASK-PC-FE-305` — 데모 종료 세션 끝(계약 § 2.6.2)의 **URL 표식 상수만**.
 *
 * 🔴🔴 이 파일은 클라이언트 컴포넌트(`DemoSignedOutNotice`)가 import 한다. 그래서 아무것도
 *    import 하지 않는 순수 상수 모듈이어야 한다 — 판정 모듈(`session-end.ts`)은 데모 상태
 *    타입 때문에 `shared/config/demo-backend.ts` 를 import 하고, 그 파일은 서버 전용
 *    폴백 주소를 리터럴로 들고 있다. 클라이언트 그래프에 그것이 닿으면
 *    `scripts/check-client-graph-backend-origins.mjs`(ADR-MONO-067 D1 — 브라우저는 백엔드
 *    주소를 모른다)가 RED 다. 첫 판이 정확히 그렇게 물렸다(`import type` 도 그래프에 든다).
 *    ⇒ 상수는 여기, 판정은 `session-end.ts`(서버 전용).
 */

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
