import { ApiError } from '@/shared/api/errors';
import { selfOrigin } from '@/shared/config/self-origin';
import { fetchDomainHealth } from './domain-health-api';
import type { DomainHealth } from './types';

/**
 * SERVER-ONLY half of `features/domain-health/api` (TASK-MONO-585).
 *
 * <p>Split out of `domain-health-api.ts`, which is in the CLIENT graph: the
 * `<RetryButton>` client component imports `use-domain-health`, which imports
 * that module. Everything reachable from there ships to the browser — and that
 * is exactly how the console's 12 backend URLs ended up in a client chunk
 * (`shared/config/env.ts` header carries the measurement). This module resolves
 * this app's own absolute origin, so it must stay OUT of that graph; the split
 * is the enforcement, not a convention.
 *
 * <p>Nothing else moved: the fetch, the schema and the error mapping are still
 * in the sibling, shared by both callers.
 */

/**
 * Server-side discriminated state for the SSR route entry. Mirrors
 * `features/operator-overview/api/operator-overview-state.ts`
 * `OperatorOverviewState` so the page handles the same three outcomes
 * uniformly:
 *
 *   - `noTenant: true` — proxy fast-failed with 400 NO_ACTIVE_TENANT.
 *   - `unauthorized: true` — proxy returned 401 (Spring Security
 *     rejected the inbound `Authorization` bearer).
 *   - `health` present — render `<DomainHealthScreen>`.
 *
 * Per-card degrade lives INSIDE `health.cards[i].status` (the 200
 * payload); it is NEVER a state field here. A whole-fan-out failure
 * (proxy 502 BAD_GATEWAY) surfaces as `bffUnavailable: true`.
 */
export interface DomainHealthState {
  health: DomainHealth | null;
  noTenant: boolean;
  unauthorized: boolean;
  bffUnavailable: boolean;
}

export async function getDomainHealthState(): Promise<DomainHealthState> {
  try {
    // TASK-PC-FE-037 — forward the page's request cookies to the in-process
    // proxy fetch (the operator-overview sibling already does this via
    // TASK-PC-FE-030). Next.js Node `fetch` does NOT auto-forward cookies on
    // internal calls (`credentials: 'include'` is browser-only), so without
    // this explicit header the proxy's `cookies()` reads empty → 400
    // NO_ACTIVE_TENANT → `noTenant: true` even when the session HAS an active
    // tenant.
    const { cookies } = await import('next/headers');
    const cookieHeader = (await cookies()).toString();
    // 🔴 Node's `fetch` needs an ABSOLUTE url here — this leg is server→server
    //    even though the proxy route runs in-process. `selfOrigin()` is this
    //    app's own origin, NOT a backend address (see that module's header).
    const health = await fetchDomainHealth(cookieHeader, selfOrigin());
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
    return {
      health: null,
      noTenant: false,
      unauthorized: false,
      bffUnavailable: true,
    };
  }
}
