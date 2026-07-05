import { redirect } from 'next/navigation';
import { getActiveTenant } from '@/shared/lib/session';
import { ApiError, PartnershipsUnavailableError } from '@/shared/api/errors';
import { listPartnerships } from './partnerships-api';
import type { PartnershipList, PartnershipListParams } from './types';

/**
 * Server-side partnership list state for the `(console)/partnerships` route
 * (TASK-PC-FE-187). Mirrors `operators-state.ts` `getOperatorsListState()`.
 *
 * Resilience boundary (§ 2.5):
 *   - 401 → `redirect('/login')` so the page forces a clean re-login (no
 *     partial authed state).
 *   - `NO_ACTIVE_TENANT` → a distinct, non-error UI state (the operator must
 *     pick a tenant; do NOT send an empty `X-Tenant-Id`).
 *   - 403 `PERMISSION_DENIED` (lacks `partnership.manage`) /
 *     403 `PARTNERSHIP_SCOPE_DENIED` → a `permission` state carrying the
 *     producer code so the client renders an inline "not permitted" section
 *     (no crash, no re-login loop).
 *   - 503 / timeout / network → DEGRADED (the partnership section renders a
 *     degraded notice; the console shell stays intact).
 */
export interface PartnershipsListState {
  page: PartnershipList | null;
  degraded: boolean;
  /** True when no tenant is selected — render the "select a tenant" gate. */
  noTenant: boolean;
  /** Set when the producer returned 403 — inline "not permitted", no crash. */
  permissionError: { code: string; message: string } | null;
  /** Echo of the query so the UI can render the filter bar / pager. */
  query: PartnershipListParams;
}

export async function getPartnershipsListState(
  query: PartnershipListParams = {},
): Promise<PartnershipsListState> {
  // Pre-flight tenant gate (no empty header ever leaves — D2).
  const tenant = await getActiveTenant();
  if (!tenant) {
    return {
      page: null,
      degraded: false,
      noTenant: true,
      permissionError: null,
      query,
    };
  }

  try {
    const page = await listPartnerships(query);
    return {
      page,
      degraded: false,
      noTenant: false,
      permissionError: null,
      query,
    };
  } catch (err) {
    if (err instanceof ApiError && err.status === 401) {
      // No partial authed state → clean re-login.
      redirect('/login');
    }
    if (err instanceof ApiError && err.code === 'NO_ACTIVE_TENANT') {
      return {
        page: null,
        degraded: false,
        noTenant: true,
        permissionError: null,
        query,
      };
    }
    if (err instanceof ApiError && err.status === 403) {
      // 403 PERMISSION_DENIED (lacks partnership.manage) /
      // 403 PARTNERSHIP_SCOPE_DENIED → inline "not permitted".
      return {
        page: null,
        degraded: false,
        noTenant: false,
        permissionError: { code: err.code, message: err.message },
        query,
      };
    }
    if (err instanceof PartnershipsUnavailableError) {
      // Degrade ONLY this section — shell intact (§ 2.5).
      return {
        page: null,
        degraded: true,
        noTenant: false,
        permissionError: null,
        query,
      };
    }
    // A genuine unexpected producer error → degrade rather than crash.
    return {
      page: null,
      degraded: true,
      noTenant: false,
      permissionError: null,
      query,
    };
  }
}
