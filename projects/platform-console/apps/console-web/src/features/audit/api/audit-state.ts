import { redirect } from 'next/navigation';
import { getActiveTenant } from '@/shared/lib/session';
import { ApiError, AuditUnavailableError } from '@/shared/api/errors';
import { queryAudit } from './audit-api';
import type { AuditPage, AuditQueryParams } from './types';

/**
 * Server-side audit list state for the `(console)/audit` route.
 *
 * Resilience boundary (console-integration-contract § 2.4.2 / § 2.5,
 * mirrors `accounts-state.ts` `getAccountsListState()`):
 *   - 401 → rethrow via `redirect('/login')` so the page forces a clean
 *     re-login (no partial authed state — task Failure Scenario).
 *   - `NO_ACTIVE_TENANT` → a distinct, non-error UI state (the operator
 *     must pick a tenant; do NOT send an empty `X-Tenant-Id`).
 *   - 403 `PERMISSION_DENIED` (incl. the intersection-permission rule) /
 *     403 `TENANT_SCOPE_DENIED` / 422 `VALIDATION_ERROR` → a `permission`
 *     state carrying the producer code so the client renders an inline,
 *     actionable, non-crashing message.
 *   - 503 / timeout / network → DEGRADED (the audit section renders a
 *     degraded notice; the console shell stays intact — never blank-crash).
 *
 * READ-ONLY: this never mutates. The query is meta-audited producer-side —
 * the route issues exactly one call (no auto-refetch here).
 */
export interface AuditListState {
  page: AuditPage | null;
  degraded: boolean;
  /** True when no tenant is selected — render the "select a tenant" gate. */
  noTenant: boolean;
  /** Set when the producer returned 403/422 — inline, no crash, no re-login
   *  loop. Carries the producer code for an actionable message. */
  permissionError: { code: string; message: string } | null;
  /** Echo of the query so the UI can render the filter bar / pager. */
  query: AuditQueryParams;
}

export async function getAuditListState(
  query: AuditQueryParams = {},
): Promise<AuditListState> {
  // Pre-flight tenant gate (no empty header ever leaves — § 2.4.2).
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
    const page = await queryAudit(query);
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
    if (
      err instanceof ApiError &&
      (err.status === 403 || err.status === 422)
    ) {
      // 403 PERMISSION_DENIED / 403 TENANT_SCOPE_DENIED / 422 → inline
      // actionable (no crash, no re-login loop).
      return {
        page: null,
        degraded: false,
        noTenant: false,
        permissionError: { code: err.code, message: err.message },
        query,
      };
    }
    if (err instanceof AuditUnavailableError) {
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
