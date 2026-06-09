import { ApiError } from '@/shared/api/errors';

/**
 * Why the 계정 운영(`/accounts`) list can be empty — distinguishes the cases
 * the user asked to separate (TASK-PC-FE-063 → TASK-MONO-202).
 *
 * Backend signal (console-integration-contract §2.4.1/§2.5; `admin-api.md`
 * `GET /api/admin/accounts`): the unfiltered list requires `account.read`;
 * absent ⇒ the producer returns **403 PERMISSION_DENIED** (TASK-MONO-202 — it
 * no longer collapses "no permission" into an empty-200). So:
 *   - a query error of `403` / `PERMISSION_DENIED` → `forbidden` (권한 없음;
 *     usually surfaced at the page level via `accounts-state`, but a mid-session
 *     permission revocation can surface it here on a client re-query);
 *   - any other query error → `load-error`;
 *   - a search filter active (`searching`) + empty → `no-results`;
 *   - unfiltered + empty (200) ⇒ permission held + **0 accounts** → `empty`
 *     ("등록된 계정이 없습니다") — now unambiguous (no permission would have 403'd).
 */
export type AccountsEmptyReason =
  | 'forbidden'
  | 'load-error'
  | 'no-results'
  | 'empty';

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
  // Unfiltered + empty 200: permission is held (no permission would have been a
  // 403, TASK-MONO-202) ⇒ the tenant genuinely has zero accounts.
  return { reason: 'empty', message: '등록된 계정이 없습니다.' };
}
