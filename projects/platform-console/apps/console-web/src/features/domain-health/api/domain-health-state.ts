import { ApiError } from '@/shared/api/errors';
import {
  classifyInProcessFailure,
  formatUnavailableCause,
  type UnavailableCause,
} from '@/shared/api/unavailable-cause';
import { logger } from '@/shared/lib/logger';
import { composeDomainHealth } from '@/shared/composition/domain-health-composition';
import { answerError } from '@/shared/composition/composition-answer';
import { DomainHealthSchema, type DomainHealth } from './types';

/**
 * SERVER-ONLY half of `features/domain-health/api` (TASK-MONO-585).
 *
 * <p>Split out of `domain-health-api.ts`, which is in the CLIENT graph: the
 * `<RetryButton>` client component imports `use-domain-health`, which imports
 * that module. Everything reachable from there ships to the browser — and that
 * is exactly how the console's 12 backend URLs ended up in a client chunk
 * (`shared/config/env.ts` header carries the measurement). This module reads the
 * session (through the composition), so it must stay OUT of that graph; the
 * split is the enforcement, not a convention.
 *
 * <p>The schema is shared with the sibling (both callers parse the same envelope).
 */

/**
 * Server-side discriminated state for the SSR route entry. Mirrors
 * `features/operator-overview/api/operator-overview-state.ts`
 * `OperatorOverviewState` so the page handles the same three outcomes
 * uniformly:
 *
 *   - `noTenant: true` — the composition answered 400 NO_ACTIVE_TENANT.
 *   - `unauthorized: true` — the composition answered 401 (no IAM session).
 *   - `health` present — render `<DomainHealthScreen>`.
 *
 * Per-card degrade lives INSIDE `health.cards[i].status` (the 200
 * payload); it is NEVER a state field here. A whole-fan-out failure
 * (a 502 answer, or the composition throwing) surfaces as `bffUnavailable: true`.
 */
export interface DomainHealthState {
  health: DomainHealth | null;
  noTenant: boolean;
  unauthorized: boolean;
  bffUnavailable: boolean;
  /** 🔴 TASK-MONO-711 ② — 왜 참인지. 개요 쪽과 **같은 타입**을 쓴다(두 집이 갈라지지 않게). */
  unavailableCause?: UnavailableCause;
}

/**
 * 🔴 TASK-PC-FE-350 — runs the route's composition IN-PROCESS
 * (`composeDomainHealth()`, the same function the route handler calls) instead of
 * fetching `/api/console/dashboards/domain-health` at `selfOrigin()` with a
 * hand-forwarded Cookie header (TASK-PC-FE-037). See the operator-overview
 * sibling for the measured reason.
 */
export async function getDomainHealthState(): Promise<DomainHealthState> {
  try {
    const answer = await composeDomainHealth();
    if (answer.status !== 200) throw answerError(answer);
    const health = DomainHealthSchema.parse(answer.body);
    return {
      health,
      noTenant: false,
      unauthorized: false,
      bffUnavailable: false,
    };
  } catch (err) {
    if (err instanceof ApiError) {
      if (err.status === 400 && err.code === 'NO_ACTIVE_TENANT') {
        return {
          health: null,
          noTenant: true,
          unauthorized: false,
          bffUnavailable: false,
        };
      }
      if (err.status === 401) {
        return {
          health: null,
          noTenant: false,
          unauthorized: true,
          bffUnavailable: false,
        };
      }
    }
    // In-process: a thrown TypeError is a code fault, not a transport failure.
    const unavailableCause = classifyInProcessFailure(err);
    logger.warn('domain_health_bff_unavailable', {
      cause: formatUnavailableCause(unavailableCause),
    });
    return {
      health: null,
      noTenant: false,
      unauthorized: false,
      bffUnavailable: true,
      unavailableCause,
    };
  }
}
