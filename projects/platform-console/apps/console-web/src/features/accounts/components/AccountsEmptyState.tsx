import { classifyAccountsEmpty } from '../lib/classify-empty';

/**
 * `AccountsScreen` empty-state paragraph (TASK-PC-FE-210 split). Pure
 * presentational — distinguishes 검색 결과 없음 vs 조회 권한 없음 vs
 * load-error vs empty via {@link classifyAccountsEmpty} (TASK-PC-FE-063 /
 * TASK-MONO-202) and renders the same `data-testid="accounts-empty"` /
 * `data-empty-reason` paragraph the pre-split container rendered inline.
 */
export interface AccountsEmptyStateProps {
  isError: boolean;
  error: unknown;
  hasEmailFilter: boolean;
}

export function AccountsEmptyState({
  isError,
  error,
  hasEmailFilter,
}: AccountsEmptyStateProps) {
  // Distinguish 검색 결과 없음 vs 조회 권한 없음 as far as the backend
  // allows (the producer returns empty-200 for no-permission, so the
  // unfiltered-empty case is an honest union). TASK-PC-FE-063.
  const empty = classifyAccountsEmpty(isError, error, hasEmailFilter);
  return (
    <p
      className="text-sm text-muted-foreground"
      data-testid="accounts-empty"
      data-empty-reason={empty.reason}
    >
      {empty.message}
    </p>
  );
}
