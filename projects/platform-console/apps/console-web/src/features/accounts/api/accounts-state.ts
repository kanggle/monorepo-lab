import { redirect } from 'next/navigation';
import { getActiveTenant } from '@/shared/lib/session';
import { getSelfRolesOrNull } from '@/shared/api/iam-operators-read';
import {
  accountsAccessTier,
  type AccountsAccessTier,
} from '@/shared/guide/permission-map';
import { ApiError, AccountsUnavailableError } from '@/shared/api/errors';
import { searchAccounts } from './accounts-api';
import type { AccountPage, AccountSearchParams } from './types';

/**
 * Server-side accounts list state for the `(console)/accounts` route.
 *
 * Resilience boundary (console-integration-contract § 2.4.1 / § 2.5,
 * mirrors `catalog-api.ts` `getCatalog()`):
 *   - 401 `TOKEN_INVALID` → rethrow via `redirect('/login')` (auth failure; no
 *     partial authed state — task Failure Scenario).
 *   - 403 `PERMISSION_DENIED` → a distinct **forbidden** state, NOT a re-login
 *     (TASK-MONO-202: the unfiltered list requires `account.read`; absent ⇒ the
 *     producer now returns 403, which is an AUTHORIZATION denial — redirecting to
 *     login would loop the operator forever). Rendered as "조회 권한이 없습니다".
 *   - `NO_ACTIVE_TENANT` → a distinct, non-error UI state (the operator must
 *     pick a tenant; do NOT send an empty `X-Tenant-Id`).
 *   - 503 / timeout / network → DEGRADED (the accounts section renders a
 *     degraded notice; the console shell stays intact — never blank-crash).
 *   - empty `200` page ⇒ permission held + **0 accounts** → "등록된 계정이 없습니다".
 */
export interface AccountsListState {
  page: AccountPage | null;
  degraded: boolean;
  /** True when no tenant is selected — render the "select a tenant" gate. */
  noTenant: boolean;
  /** True on 403 PERMISSION_DENIED — render the 권한 없음 state (not re-login). */
  forbidden: boolean;
  /** Echo of the query so the UI can render the search box / pager. */
  query: AccountSearchParams;
}

export async function getAccountsListState(
  query: AccountSearchParams = {},
): Promise<AccountsListState> {
  // Pre-flight tenant gate (no empty header ever leaves — § 2.4.1).
  const tenant = await getActiveTenant();
  if (!tenant) {
    return { page: null, degraded: false, noTenant: true, forbidden: false, query };
  }

  try {
    const page = await searchAccounts(query);
    return { page, degraded: false, noTenant: false, forbidden: false, query };
  } catch (err) {
    if (err instanceof ApiError && err.status === 401) {
      // Auth failure → clean re-login (no partial authed state).
      redirect('/login?error=session_expired');
    }
    if (
      err instanceof ApiError &&
      (err.status === 403 || err.code === 'PERMISSION_DENIED')
    ) {
      // Authorization denial (account.read absent) — a distinct forbidden state,
      // NOT a re-login (TASK-MONO-202; redirecting would loop the operator).
      return { page: null, degraded: false, noTenant: false, forbidden: true, query };
    }
    if (err instanceof ApiError && err.code === 'NO_ACTIVE_TENANT') {
      return { page: null, degraded: false, noTenant: true, forbidden: false, query };
    }
    if (err instanceof AccountsUnavailableError) {
      // Degrade ONLY this section — shell intact (§ 2.5).
      return { page: null, degraded: true, noTenant: false, forbidden: false, query };
    }
    // A genuine producer 400/404/422 list error → degrade rather than crash.
    return { page: null, degraded: true, noTenant: false, forbidden: false, query };
  }
}

/**
 * TASK-PC-FE-326 — which mode of `/accounts` the signed-in caller may use
 * (see `accountsAccessTier()` doc, `shared/guide/permission-map.ts`). The
 * page calls this FIRST: `'full'`/`'none'` keep today's behaviour verbatim
 * (fall through to {@link getAccountsListState}, which 403s into the
 * `forbidden` state for `'none'`); `'search-only'` skips the unfiltered-list
 * call entirely (it would only 403) and renders the email-search-only
 * screen instead.
 */
export async function getAccountsAccessTier(): Promise<AccountsAccessTier> {
  const roles = await getSelfRolesOrNull();
  return accountsAccessTier(roles);
}

export interface AccountsSearchOnlyState {
  /** Same "select a tenant first" gate as the full-list path — the email
   *  search is tenant-scoped too (TASK-BE-357). */
  noTenant: boolean;
}

export async function getAccountsSearchOnlyState(): Promise<AccountsSearchOnlyState> {
  const tenant = await getActiveTenant();
  return { noTenant: !tenant };
}
