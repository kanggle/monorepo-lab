import { ApiError } from '@/shared/api/errors';
import {
  classifyInProcessFailure,
  formatUnavailableCause,
  type UnavailableCause,
} from '@/shared/api/unavailable-cause';
import { logger } from '@/shared/lib/logger';
import { composeOperatorOverview } from '@/shared/composition/operator-overview-composition';
import { answerError } from '@/shared/composition/composition-answer';
import { OperatorOverviewSchema, type OperatorOverview } from './operator-overview-types';

/**
 * SERVER-ONLY half of `features/operator-overview/api` (TASK-MONO-585).
 *
 * <p>Split out of `operator-overview-api.ts` for the same reason as the
 * domain-health sibling: that module is reachable from the client graph (the
 * React Query hook), so anything it imports ships to the browser. This module
 * reads the session (through the composition) and must stay out of that graph —
 * the split IS the enforcement. See `shared/config/env.ts` for the measurement
 * that made this necessary.
 */

/**
 * Server-side discriminated state for the SSR route entry. Mirrors
 * the existing `features/dashboards/api/overview-state.ts` shape so
 * the page handles the same three outcomes uniformly:
 *
 *   - `noTenant: true` — render the "select a tenant" gate (the composition
 *     answered 400 NO_ACTIVE_TENANT before any outbound).
 *   - `unauthorized: true` — the page calls `redirect('/login')`
 *     (the composition answered 401 TOKEN_INVALID; no partial authed state).
 *   - `overview` present — render `<OperatorOverviewScreen>`.
 *
 * Per-card degrade lives INSIDE `overview.cards[i].status` (the 200
 * payload); it is NEVER a state field here. A whole-fan-out failure
 * (a 502 answer, or the composition throwing) surfaces as `bffUnavailable: true`.
 */
export interface OperatorOverviewState {
  overview: OperatorOverview | null;
  noTenant: boolean;
  unauthorized: boolean;
  bffUnavailable: boolean;
  /**
   * 🔴 TASK-MONO-711 ② — `bffUnavailable` 이 **왜** 참인지. 그 플래그만으로는
   * 실패의 종류(응답 상태 · 전송 실패 · 타임아웃)가 구별되지 않는다. 상시 켜진 신호는
   * 꺼진 것과 같다(배경: `shared/api/unavailable-cause.ts`).
   * 🔵 `bffUnavailable === false` 면 `undefined` 다 — 없는 사유를 지어내지 않는다.
   * 🔴 방문자에게 보이는 값이 아니다(로그·진단용).
   */
  unavailableCause?: UnavailableCause;
}

/**
 * Server-side SSR state. Used by the page entry only.
 *
 * 🔴 TASK-PC-FE-350 — runs the route's composition IN-PROCESS
 * (`composeOperatorOverview()`, the same function the route handler calls). It
 * used to fetch `/api/console/dashboards/operator-overview` over HTTP at
 * `selfOrigin()`, forwarding the Cookie header by hand (TASK-PC-FE-030). That hop
 * added a name lookup and undici's 10 s connect timeout; with libuv's threadpool
 * held by un-cancellable `getaddrinfo` calls the lookup queued and the whole
 * overview degraded (`transport:TypeError/UND_ERR_CONNECT_TIMEOUT` — 350 § AC-0).
 * The session is now read where the composition runs — `next/headers` `cookies()`
 * of this page's own request — exactly as the route reads it.
 *
 * The answer is mapped through the same `ApiError` the HTTP hop produced, so every
 * branch below means what it meant before.
 */
export async function getOperatorOverviewState(): Promise<OperatorOverviewState> {
  try {
    const answer = await composeOperatorOverview();
    if (answer.status !== 200) throw answerError(answer);
    const overview = OperatorOverviewSchema.parse(answer.body);
    return {
      overview,
      noTenant: false,
      unauthorized: false,
      bffUnavailable: false,
    };
  } catch (err) {
    if (err instanceof ApiError) {
      if (err.status === 400 && err.code === 'NO_ACTIVE_TENANT') {
        return {
          overview: null,
          noTenant: true,
          unauthorized: false,
          bffUnavailable: false,
        };
      }
      if (err.status === 401) {
        return {
          overview: null,
          noTenant: false,
          unauthorized: true,
          bffUnavailable: false,
        };
      }
    }
    // In-process: a thrown TypeError is a code fault, not a transport failure.
    const unavailableCause = classifyInProcessFailure(err);
    logger.warn('operator_overview_bff_unavailable', {
      cause: formatUnavailableCause(unavailableCause),
    });
    return {
      overview: null,
      noTenant: false,
      unauthorized: false,
      bffUnavailable: true,
      unavailableCause,
    };
  }
}
