import { redirect } from 'next/navigation';
import { getActiveTenant } from '@/shared/lib/session';
import { ApiError } from '@/shared/api/errors';
import { getOperatorOverview } from './overview-api';
import type { OperatorOverview } from './types';

/**
 * Server-side composed-overview state for the `(console)/dashboards` route
 * (TASK-PC-FE-005). Mirrors `audit-state.ts` `getAuditListState()` — the
 * READ-ONLY resilience boundary, but for a composed fan-out:
 *
 *   - **no active tenant** → a distinct, non-error UI state (the operator
 *     must pick a tenant; the fan-out never sends an empty `X-Tenant-Id`
 *     on any leg — § 2.4.4).
 *   - **401 on ANY leg** → `redirect('/login')` so the WHOLE overview
 *     forces a clean re-login (no partial authed state — 401 is never a
 *     per-card degrade; `getOperatorOverview()` re-throws it as an
 *     `ApiError(401)`).
 *   - **per-card 403/503/timeout** → NOT handled here — they are isolated
 *     INSIDE `getOperatorOverview()` as per-card statuses. The overview
 *     itself never hard-fails because one source is down; the page always
 *     renders the full shell with each card's own placeholder.
 *
 * READ-ONLY: this never mutates. The audit leg is meta-audited
 * producer-side — the route issues exactly one bounded fan-out per load
 * (no auto-refetch here; the hook enforces no refetch loop).
 */
export interface OverviewState {
  overview: OperatorOverview | null;
  /** True when no tenant is selected — render the "select a tenant" gate
   *  (never an empty `X-Tenant-Id` on any leg). */
  noTenant: boolean;
}

export async function getOverviewState(): Promise<OverviewState> {
  // Pre-flight tenant gate (no empty header ever leaves on any leg —
  // § 2.4.4). Checked once, before the fan-out, so a missing tenant is a
  // single actionable state rather than three identical per-card errors.
  const tenant = await getActiveTenant();
  if (!tenant) {
    return { overview: null, noTenant: true };
  }

  try {
    const overview = await getOperatorOverview();
    return { overview, noTenant: false };
  } catch (err) {
    // 401 on ANY leg → whole-overview clean re-login (no partial authed
    // state). `getOperatorOverview()` re-throws a leg 401 as ApiError(401).
    if (err instanceof ApiError && err.status === 401) {
      redirect('/login');
    }
    // A NO_ACTIVE_TENANT that raced past the pre-flight gate → the tenant
    // gate state (still never an empty header — the leg blocked itself).
    if (err instanceof ApiError && err.code === 'NO_ACTIVE_TENANT') {
      return { overview: null, noTenant: true };
    }
    // Any other unexpected whole-fan-out failure → re-throw so the route
    // error boundary handles it (per-card failures never reach here —
    // they are isolated inside `getOperatorOverview()`).
    throw err;
  }
}
