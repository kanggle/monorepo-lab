import { ApiError } from '@/shared/api/errors';

/**
 * Why the 계정 운영(`/accounts`) list can be empty — distinguishes the two
 * cases the user asked to separate (TASK-PC-FE-063), AS FAR AS THE BACKEND
 * ALLOWS.
 *
 * Backend constraint (console-integration-contract §2.4.1; `accounts-state.ts`):
 * the producer returns an **empty 200 page (NOT 403)** when `account.read` is
 * not granted — so at the data layer "no permission" and "tenant has zero
 * accounts" are identical. The only signals available to the console are:
 *   - a client re-query that DOES surface `403` / `PERMISSION_DENIED` → pure
 *     `forbidden` (rare: most denials are the empty-200 path above);
 *   - whether a search filter is active (`searching`) → an empty result while
 *     searching is unambiguously `no-results`.
 *
 * Hence the unfiltered-empty case is reported as the honest union
 * `forbidden-or-empty` rather than over-claiming a permission denial. A pure
 * permission distinction needs a backend signal (a separate task).
 */
export type AccountsEmptyReason =
  | 'forbidden'
  | 'load-error'
  | 'no-results'
  | 'forbidden-or-empty';

export interface AccountsEmptyInfo {
  reason: AccountsEmptyReason;
  message: string;
}

export function classifyAccountsEmpty(
  isError: boolean,
  error: unknown,
  searching: boolean,
): AccountsEmptyInfo {
  if (isError) {
    const forbidden =
      error instanceof ApiError &&
      (error.status === 403 || error.code === 'PERMISSION_DENIED');
    return forbidden
      ? { reason: 'forbidden', message: '조회 권한이 없습니다.' }
      : { reason: 'load-error', message: '목록을 불러올 수 없습니다.' };
  }
  if (searching) {
    return { reason: 'no-results', message: '검색 결과가 없습니다.' };
  }
  // Unfiltered + empty: the producer collapses "account.read not granted" and
  // "tenant has zero accounts" into the same empty-200 — report the union.
  return {
    reason: 'forbidden-or-empty',
    message: '조회 권한이 없거나 등록된 계정이 없습니다.',
  };
}
