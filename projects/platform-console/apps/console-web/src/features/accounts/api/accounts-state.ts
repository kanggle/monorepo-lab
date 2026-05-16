import { redirect } from 'next/navigation';
import { getActiveTenant } from '@/shared/lib/session';
import { ApiError, AccountsUnavailableError } from '@/shared/api/errors';
import { searchAccounts } from './accounts-api';
import type { AccountPage, AccountSearchParams } from './types';

/**
 * Server-side accounts list state for the `(console)/accounts` route.
 *
 * Resilience boundary (console-integration-contract § 2.4.1 / § 2.5,
 * mirrors `catalog-api.ts` `getCatalog()`):
 *   - 401/403 → rethrow `ApiError` so the page forces a clean re-login
 *     (no partial authed state — task Failure Scenario).
 *   - `NO_ACTIVE_TENANT` → a distinct, non-error UI state (the operator must
 *     pick a tenant; do NOT send an empty `X-Tenant-Id`).
 *   - 503 / timeout / network → DEGRADED (the accounts section renders a
 *     degraded notice; the console shell stays intact — never blank-crash).
 *   - `account.read` not granted ⇒ the producer returns an empty page (not
 *     403) ⇒ rendered as an empty/insufficient-permission state.
 */
export interface AccountsListState {
  page: AccountPage | null;
  degraded: boolean;
  /** True when no tenant is selected — render the "select a tenant" gate. */
  noTenant: boolean;
  /** Echo of the query so the UI can render the search box / pager. */
  query: AccountSearchParams;
}

export async function getAccountsListState(
  query: AccountSearchParams = {},
): Promise<AccountsListState> {
  // Pre-flight tenant gate (no empty header ever leaves — § 2.4.1).
  const tenant = await getActiveTenant();
  if (!tenant) {
    return { page: null, degraded: false, noTenant: true, query };
  }

  try {
    const page = await searchAccounts(query);
    return { page, degraded: false, noTenant: false, query };
  } catch (err) {
    if (err instanceof ApiError && (err.status === 401 || err.status === 403)) {
      // No partial authed state → clean re-login.
      redirect('/login');
    }
    if (err instanceof ApiError && err.code === 'NO_ACTIVE_TENANT') {
      return { page: null, degraded: false, noTenant: true, query };
    }
    if (err instanceof AccountsUnavailableError) {
      // Degrade ONLY this section — shell intact (§ 2.5).
      return { page: null, degraded: true, noTenant: false, query };
    }
    // A genuine producer 400/404/422 list error → degrade rather than crash.
    return { page: null, degraded: true, noTenant: false, query };
  }
}
