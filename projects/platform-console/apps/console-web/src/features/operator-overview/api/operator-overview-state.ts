import { ApiError } from '@/shared/api/errors';
import { selfOrigin } from '@/shared/config/self-origin';
import { fetchOperatorOverview } from './operator-overview-api';
import type { OperatorOverview } from './operator-overview-types';

/**
 * SERVER-ONLY half of `features/operator-overview/api` (TASK-MONO-585).
 *
 * <p>Split out of `operator-overview-api.ts` for the same reason as the
 * domain-health sibling: that module is reachable from the client graph (the
 * React Query hook), so anything it imports ships to the browser. This module
 * resolves this app's own absolute origin and must stay out of that graph —
 * the split IS the enforcement. See `shared/config/env.ts` for the measurement
 * that made this necessary.
 */

/**
 * Server-side discriminated state for the SSR route entry. Mirrors
 * the existing `features/dashboards/api/overview-state.ts` shape so
 * the page handles the same three outcomes uniformly:
 *
 *   - `noTenant: true` — render the "select a tenant" gate (BFF
 *     proxy fast-failed with 400 NO_ACTIVE_TENANT before any outbound).
 *   - `unauthorized: true` — the page calls `redirect('/login')`
 *     (BFF returned 401 TOKEN_INVALID; no partial authed state).
 *   - `overview` present — render `<OperatorOverviewScreen>`.
 *
 * Per-card degrade lives INSIDE `overview.cards[i].status` (the 200
 * payload); it is NEVER a state field here. A whole-fan-out failure
 * (proxy 502 BAD_GATEWAY) surfaces as `bffUnavailable: true`.
 */
export interface OperatorOverviewState {
  overview: OperatorOverview | null;
  noTenant: boolean;
  unauthorized: boolean;
  bffUnavailable: boolean;
}

/**
 * Server-side SSR fetch wrapper. Calls the same Next.js proxy URL
 * server-to-server (the proxy is in-process); reads the result and
 * maps non-2xx into the discriminated state. Used by the page entry
 * only.
 */
export async function getOperatorOverviewState(): Promise<OperatorOverviewState> {
  try {
    // TASK-PC-FE-030 — forward the page's request cookies to the
    // in-process proxy fetch. Next.js Node `fetch` does NOT auto-forward
    // cookies on internal calls (`credentials: 'include'` is browser-only),
    // so without this explicit header the proxy's `cookies()` reads empty
    // → 400 NO_ACTIVE_TENANT → `noTenant: true` even when the browser
    // session has the active-tenant cookie.
    const { cookies } = await import('next/headers');
    const cookieHeader = (await cookies()).toString();
    // 🔴 Node's `fetch` needs an ABSOLUTE url on this leg. `selfOrigin()` is
    //    this app's own origin, NOT a backend address.
    const overview = await fetchOperatorOverview(cookieHeader, selfOrigin());
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
    return {
      overview: null,
      noTenant: false,
      unauthorized: false,
      bffUnavailable: true,
    };
  }
}
