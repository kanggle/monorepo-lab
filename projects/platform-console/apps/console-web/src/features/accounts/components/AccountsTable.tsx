'use client';

import type { Dispatch, SetStateAction } from 'react';
import type { AccountPage, AccountSummary } from '../api/types';
import { formatDateTime } from '@/shared/lib/datetime';
import { AccountStatusBadge } from './AccountStatusBadge';
import { AccountRowActions } from './AccountRowActions';
import { AccountsPagination } from './AccountsPagination';
import type { AccountsQuery, ActionKind } from './accounts-screen-helpers';

/**
 * `AccountsScreen` result region (extracted in TASK-PC-FE-111 —
 * behavior-preserving god-file split): the accounts table (per-row select +
 * lock/unlock/revoke/export/GDPR action buttons + status badge) and the
 * pagination nav. Pure presentational — every `data-testid` / aria / class /
 * label is byte-identical to the pre-split container; all state lives in the
 * container, which passes the `setQuery` dispatcher as `onPageChange` so the
 * functional prev/next updates are preserved. TASK-PC-FE-210 further extracted
 * the per-row action cluster ({@link AccountRowActions}) and the pagination nav
 * ({@link AccountsPagination}) into cohesive presentational siblings.
 */

interface AccountsTableProps {
  rows: AccountSummary[];
  page: AccountPage;
  query: AccountsQuery;
  selected: Set<string>;
  onToggleSelect: (id: string) => void;
  /** Single-target row ops (lock / unlock / revoke-session / gdpr-delete). */
  onAction: (kind: ActionKind, account: AccountSummary) => void;
  onExport: (account: AccountSummary) => void;
  onPageChange: Dispatch<SetStateAction<AccountsQuery>>;
}

export function AccountsTable({
  rows,
  page,
  query,
  selected,
  onToggleSelect,
  onAction,
  onExport,
  onPageChange,
}: AccountsTableProps) {
  return (
    <>
      <table className="data-table" data-testid="accounts-table">
        <caption className="sr-only">계정 목록</caption>
        <thead>
          <tr className="border-b border-border text-left">
            <th scope="col" className="p-2">
              <span className="sr-only">일괄 선택</span>
            </th>
            <th scope="col" className="p-2">
              이메일
            </th>
            <th scope="col" className="p-2">
              상태
            </th>
            <th scope="col" className="p-2">
              생성일
            </th>
            <th scope="col" className="p-2">
              작업
            </th>
          </tr>
        </thead>
        <tbody>
          {rows.map((acc) => (
            <tr
              key={acc.id}
              data-testid={`account-row-${acc.id}`}
              className="border-b border-border"
            >
              <td className="p-2">
                <input
                  type="checkbox"
                  checked={selected.has(acc.id)}
                  onChange={() => onToggleSelect(acc.id)}
                  aria-label={`${acc.email} 일괄 작업 선택`}
                  data-testid={`account-select-${acc.id}`}
                />
              </td>
              <td className="p-2">{acc.email}</td>
              <td className="p-2">
                <AccountStatusBadge status={acc.status} />
              </td>
              <td className="p-2 text-muted-foreground">{formatDateTime(acc.createdAt)}</td>
              <td className="p-2">
                <AccountRowActions
                  account={acc}
                  onAction={onAction}
                  onExport={onExport}
                />
              </td>
            </tr>
          ))}
        </tbody>
      </table>

      <AccountsPagination
        page={page}
        query={query}
        onPageChange={onPageChange}
      />
    </>
  );
}
