// DEMO-RESOLVER-CONSUMER: console-web   (ADR-MONO-068 § D6 = B2 — 구현은 @demo/backend-resolver 하나뿐이다)
/**
 * 쿠키가 **아직 살아 있는** 세션이 데모 종료를 만났을 때 (`TASK-PC-FE-306`,
 * `console-integration-contract.md` § 2.6.2 «Live session»).
 *
 * =============================================================================
 * 🔴🔴 판정은 여기 없다 — 여기 있는 것은 «두 번» 이다
 * =============================================================================
 * 「꺼짐인가」 는 `TASK-PC-FE-305` 의 {@link sessionEndDestination} 하나가 정한다(두 번째
 * 판정을 만들지 않는다). 이 파일이 더하는 것은 소유자 결정 ①(2026-10-04): 작동하는 세션은
 * **서로 다른 두 번의 연속 `unavailable`** 뒤에만 끝낸다. 판독 하나 — 실패한 `/status`
 * 하나도 해석기는 `unavailable` 로 돌려준다 — 로는 절대 로그아웃시키지 않는다.
 *
 * =============================================================================
 * 🔴 «서로 다른» = 해석기의 15초 캐시 창보다 멀리
 * =============================================================================
 * 해석기는 서버 인스턴스마다 스냅샷 하나를 15초 캐시한다(실패도 캐시한다 —
 * `infra/demo/backend-resolver/src/index.ts` `CACHE_TTL_MS`·`resolveSnapshot`). 그래서 15초
 * 안의 두 판독은 **같은 스냅샷**일 수 있다. 첫 판독이 끝난 시각 `f` 와 두 번째 판독이
 * 시작한 시각 `s` 가 `s − f > TTL` 이면, 두 번째가 읽는 스냅샷은(어느 인스턴스든)
 * `s − TTL > f` 이후에 받아진 것이다 ⇒ 다른 왕복.
 * 🔴 {@link DEMO_STATE_SNAPSHOT_TTL_MS} 는 해석기 상수의 **사본**이다(해석기를 바꾸지 않는다 —
 *    Out of Scope). 그래서 `tests/unit/live-session-demo-stop-resolver.test.ts` 가 **실제
 *    해석기**로 «이만큼 떨어지면 다시 가져온다» 를 고정한다 — 해석기 TTL 이 늘면 빨강.
 *
 * =============================================================================
 * 🔴 왜 쿠키인가 — 그리고 왜 라우트만 쓰는가
 * =============================================================================
 * 콘솔은 서버리스라 인스턴스 메모리는 요청 사이에 믿을 수 없다 ⇒ 첫 판독 시각은 방문자의
 * 브라우저에 둔다({@link DEMO_STOP_MARKER_COOKIE}). 레이아웃은 쿠키를 못 쓴다(§ 2.6.1 과 같은
 * 이유) ⇒ 쓰는 자리는 `GET /api/auth/demo-ended` 하나, 레이아웃은 «거기 가야 하는가» 만 정한다.
 * 표식은 자격이 아니다: 위조해도 **자기** 세션을 진짜 `unavailable` 한 번 뒤에 끝내거나 그
 * 끝을 미룰 수 있을 뿐이다(라우트는 신호를 다시 읽는다).
 *
 * 🔴 서버 전용 모듈이다 — `demo-backend.ts`(서버 전용 주소 리터럴)에 닿는다. 클라이언트
 *    컴포넌트는 import 하지 마라(`scripts/check-client-graph-backend-origins.mjs`, 305 CI 수정).
 */

import { cookies } from 'next/headers';
import {
  resolveDemoBackendState,
  type DemoBackendState,
} from '@/shared/config/demo-backend';
import { tokenCookieOpts } from '@/shared/lib/session';
import { isGuardReturnPath } from '@/shared/lib/login-redirect';
import { sessionEndDestination } from '@/shared/lib/session-end';
import { DEMO_ENDED_PATH, DEMO_CHECKED_PARAM } from '@/shared/lib/session-end-params';

/** 해석기 스냅샷 TTL 의 콘솔 쪽 사본(`CACHE_TTL_MS`). 두 판독이 «서로 다른» 최소 간격. */
export const DEMO_STATE_SNAPSHOT_TTL_MS = 15_000;

/** 첫 판독이 이보다 오래됐으면 «연속» 의 증거가 아니다 — 다시 센다. 쿠키 수명도 같다. */
export const DEMO_STOP_MARKER_MAX_AGE_S = 600;
const MARKER_MAX_AGE_MS = DEMO_STOP_MARKER_MAX_AGE_S * 1000;

/** `<firstUnavailableAt>.<checkedAt>`(epoch ms, `0` = 대기 중인 판독 없음). */
export const DEMO_STOP_MARKER_COOKIE = 'console_demo_stop_check';

/** 라우트의 live 모드 파라미터 — 레이아웃이 보낸 홉만 이 값을 단다. */
export const LIVE_PARAM = 'live';
export type LiveIntent = 'check' | 'clear';

export interface DemoStopMarker {
  /** 대기 중인 첫 `unavailable` 판독이 끝난 시각. `0` = 없음. */
  firstUnavailableAt: number;
  /** 라우트가 마지막으로 답한 시각 — 홉 상한. */
  checkedAt: number;
}

export const demoStopMarkerCookieOpts = {
  ...tokenCookieOpts,
  maxAge: DEMO_STOP_MARKER_MAX_AGE_S,
};

/** 형식이 틀린 표식은 **없는 것**으로 읽는다(위조·잘림 — 오늘의 동작으로 남는다). */
export function parseDemoStopMarker(raw: string | null | undefined): DemoStopMarker | null {
  if (typeof raw !== 'string') return null;
  const m = /^(\d{1,15})\.(\d{1,15})$/.exec(raw);
  if (!m) return null;
  return { firstUnavailableAt: Number(m[1]), checkedAt: Number(m[2]) };
}

export function serializeDemoStopMarker(marker: DemoStopMarker): string {
  return `${Math.max(0, Math.trunc(marker.firstUnavailableAt))}.${Math.max(0, Math.trunc(marker.checkedAt))}`;
}

export function parseLiveIntent(raw: string | null | undefined): LiveIntent | null {
  return raw === 'check' || raw === 'clear' ? raw : null;
}

const isOff = (state: DemoBackendState): boolean => sessionEndDestination(state) === 'sample';

/** 레이아웃이 정하는 것 — 오늘 그대로(`stay`)인가, 라우트로 가는가(`check`·`clear`). */
export type LiveShellAction = 'stay' | LiveIntent;

/**
 * 🔴🔴 레이아웃 가드(인증 분기)의 홉 판정. 순수 함수.
 *
 * - `not-demo` → `stay`(물어볼 컨트롤 플레인이 없다 — 표식도 안 본다).
 * - 꺼짐 판독 → 라우트가 TTL 안에 이미 답했으면 `stay`(홉 상한; 표식이 없으면 URL 의
 *   `demo_checked=1` 이 대신한다 — 브라우저가 표식을 거부해도 핑퐁이 안 된다), 아니면 `check`.
 * - 그 밖의 판독 + 대기 중인 첫 판독 → `clear`(연속이 깨졌다). 그 밖 → `stay`.
 */
export function liveShellDemoStopAction(input: {
  state: DemoBackendState;
  marker: DemoStopMarker | null;
  now: number;
  urlChecked: boolean;
}): LiveShellAction {
  const { state, marker, now, urlChecked } = input;
  if (state === 'not-demo') return 'stay';
  if (isOff(state)) {
    const answeredRecently = marker
      ? now - marker.checkedAt <= DEMO_STATE_SNAPSHOT_TTL_MS
      : urlChecked;
    return answeredRecently ? 'stay' : 'check';
  }
  return marker && marker.firstUnavailableAt !== 0 ? 'clear' : 'stay';
}

export type LiveRouteOutcome =
  | { end: true }
  | { end: false; firstUnavailableAt: number };

/**
 * 🔴🔴 라우트(live 모드)의 결과. 순수 함수 — **두 번 연속** 규칙이 사는 곳.
 *
 * @param startedAt 이번 판독을 시작한 시각(«서로 다른» 의 판정에 쓴다)
 * @param settledAt 이번 판독이 끝난 시각(첫 판독으로 기록할 때 쓴다)
 */
export function liveRouteDemoStopOutcome(input: {
  intent: LiveIntent;
  state: DemoBackendState | null;
  marker: DemoStopMarker | null;
  startedAt: number;
  settledAt: number;
}): LiveRouteOutcome {
  const { intent, state, marker, startedAt, settledAt } = input;
  if (intent === 'clear' || state === null || !isOff(state)) {
    return { end: false, firstUnavailableAt: 0 };
  }
  const first = marker?.firstUnavailableAt ?? 0;
  const age = startedAt - first;
  const pending = first !== 0 && age <= MARKER_MAX_AGE_MS;
  if (pending && age > DEMO_STATE_SNAPSHOT_TTL_MS) return { end: true };
  return { end: false, firstUnavailableAt: pending ? first : settledAt };
}

/** 레이아웃 → 라우트(live 모드). 경로 술어는 § 2.6.1 의 PRODUCE 측과 같다. */
export function buildLiveDemoStopRedirectFor(rawPath: string | null, intent: LiveIntent): string {
  const base = `${DEMO_ENDED_PATH}?${LIVE_PARAM}=${intent}`;
  return isGuardReturnPath(rawPath) ? `${base}&redirect=${encodeURIComponent(rawPath)}` : base;
}

/** 경로의 `demo_checked` 표식을 지운다(끝낼 때 · 다시 달기 전). */
export function withoutDemoChecked(path: string): string {
  const url = new URL(path, 'http://placeholder.invalid');
  url.searchParams.delete(DEMO_CHECKED_PARAM);
  return `${url.pathname}${url.search}${url.hash}`;
}

/** 끝내지 않을 때의 복귀 — 요청한 화면 + `demo_checked=1`(홉 상한). */
export function withDemoCheckedReturn(path: string): string {
  const url = new URL(path, 'http://placeholder.invalid');
  url.searchParams.set(DEMO_CHECKED_PARAM, '1');
  return `${url.pathname}${url.search}${url.hash}`;
}

function urlCarriesDemoChecked(rawPath: string | null): boolean {
  if (!rawPath) return false;
  try {
    return new URL(rawPath, 'http://placeholder.invalid').searchParams.get(DEMO_CHECKED_PARAM) === '1';
  } catch {
    return false;
  }
}

/**
 * 레이아웃 가드가 부르는 것 — 홉 목적지, 또는 `null`(오늘 그대로).
 *
 * 🔴 `not-demo` 면 **쿠키도 안 읽고** 끝난다. 해석기는 그 경우 네트워크를 안 탄다
 *    (`index.ts` `controlPlaneBase()` 단락) ⇒ 비데모 배포의 비용은 0.
 * 🔵 데모 배포에서는 `DemoBackendNotice` 와 **같은 모듈의 같은 함수**를 같은 요청에서 부른다 —
 *    같은 캐시 스냅샷, 추가 왕복 없음.
 */
export async function liveSessionDemoStopRedirect(rawPath: string | null): Promise<string | null> {
  const state = await resolveDemoBackendState();
  if (state === 'not-demo') return null;
  const jar = await cookies();
  const action = liveShellDemoStopAction({
    state,
    marker: parseDemoStopMarker(jar.get(DEMO_STOP_MARKER_COOKIE)?.value),
    now: Date.now(),
    urlChecked: urlCarriesDemoChecked(rawPath),
  });
  return action === 'stay' ? null : buildLiveDemoStopRedirectFor(rawPath, action);
}
