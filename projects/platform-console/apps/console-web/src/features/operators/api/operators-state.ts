import { redirect } from 'next/navigation';
import { getActiveTenant } from '@/shared/lib/session';
import { ApiError, OperatorsUnavailableError } from '@/shared/api/errors';
import { listOperators } from './operators-api';
import type { OperatorPage, OperatorListParams } from './types';

/**
 * Server-side operators list state for the `(console)/operators` route.
 *
 * Resilience boundary (console-integration-contract § 2.4.3 / § 2.5,
 * mirrors `audit-state.ts` `getAuditListState()`):
 *   - 401 → `redirect('/login')` so the page forces a clean re-login (no
 *     partial authed state — task Failure Scenario).
 *   - `NO_ACTIVE_TENANT` → a distinct, non-error UI state (the operator
 *     must pick a tenant; do NOT send an empty `X-Tenant-Id`).
 *   - 403 `PERMISSION_DENIED` (not SUPER_ADMIN / lacks `operator.manage`) /
 *     403 `TENANT_SCOPE_DENIED` → a `permission` state carrying the
 *     producer code so the client renders an inline "not permitted"
 *     section (no crash, no re-login loop — the operator simply lacks
 *     `operator.manage`).
 *   - 503 / timeout / network → DEGRADED (the operators section renders a
 *     degraded notice; the console shell stays intact — never blank-crash).
 *
 * This is the LIST read only — the privilege-sensitive mutations are
 * client-driven through the same-origin proxy + confirm dialogs.
 */
export interface OperatorsListState {
  page: OperatorPage | null;
  degraded: boolean;
  /** True when no tenant is selected — render the "select a tenant" gate. */
  noTenant: boolean;
  /** Set when the producer returned 403 — inline "not permitted", no crash,
   *  no re-login loop. Carries the producer code for an actionable copy. */
  permissionError: { code: string; message: string } | null;
  /** Echo of the query so the UI can render the filter bar / pager. */
  query: OperatorListParams;
}

export async function getOperatorsListState(
  query: OperatorListParams = {},
): Promise<OperatorsListState> {
  // Pre-flight tenant gate (no empty header ever leaves — § 2.4.3).
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
    const page = await listOperators(query);
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
      // 403 PERMISSION_DENIED (not SUPER_ADMIN) / 403 TENANT_SCOPE_DENIED
      // → inline "not permitted" (no crash, no re-login loop).
      return {
        page: null,
        degraded: false,
        noTenant: false,
        permissionError: { code: err.code, message: err.message },
        query,
      };
    }
    if (err instanceof OperatorsUnavailableError) {
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
