'use client';

import type { Dispatch, SetStateAction } from 'react';
import { Button } from '@/shared/ui/Button';
import type { AccountPage, AccountSummary } from '../api/types';
import { AccountStatusBadge } from './AccountStatusBadge';
import type { AccountsQuery, ActionKind } from './accounts-screen-helpers';

/**
 * `AccountsScreen` result region (extracted in TASK-PC-FE-111 —
 * behavior-preserving god-file split): the accounts table (per-row select +
 * lock/unlock/revoke/export/GDPR action buttons + status badge) and the
 * pagination nav. Pure presentational — every `data-testid` / aria / class /
 * label is byte-identical to the pre-split container; all state lives in the
 * container, which passes the `setQuery` dispatcher as `onPageChange` so the
 * functional prev/next updates are preserved.
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
              <td className="p-2 text-muted-foreground">{acc.createdAt}</td>
              <td className="p-2">
                <div className="flex flex-wrap gap-2">
                  <Button
                    variant="secondary"
                    size="sm"
                    onClick={() => onAction('lock', acc)}
                    data-testid={`action-lock-${acc.id}`}
                  >
                    잠금
                  </Button>
                  <Button
                    variant="secondary"
                    size="sm"
                    onClick={() => onAction('unlock', acc)}
                    data-testid={`action-unlock-${acc.id}`}
                  >
                    잠금 해제
                  </Button>
                  <Button
                    variant="secondary"
                    size="sm"
                    onClick={() => onAction('revoke-session', acc)}
                    data-testid={`action-revoke-${acc.id}`}
                  >
                    세션 종료
                  </Button>
                  <Button
                    variant="secondary"
                    size="sm"
                    onClick={() => onExport(acc)}
                    data-testid={`action-export-${acc.id}`}
                  >
                    내보내기
                  </Button>
                  <Button
                    variant="secondary"
                    size="sm"
                    className="text-destructive"
                    onClick={() => onAction('gdpr-delete', acc)}
                    data-testid={`action-gdpr-${acc.id}`}
                  >
                    GDPR 삭제
                  </Button>
                </div>
              </td>
            </tr>
          ))}
        </tbody>
      </table>

      <nav
        className="mt-4 flex items-center justify-between"
        aria-label="페이지 이동"
      >
        <Button
          variant="secondary"
          disabled={query.page <= 0 || !!query.email}
          onClick={() =>
            onPageChange((q) => ({ ...q, page: Math.max(0, q.page - 1) }))
          }
          data-testid="accounts-prev"
        >
          이전
        </Button>
        <span
          className="text-sm text-muted-foreground"
          data-testid="accounts-pageinfo"
        >
          {query.email
            ? '단건 검색'
            : `${page.page + 1} / ${Math.max(1, page.totalPages)} 페이지 · 총 ${page.totalElements}건`}
        </span>
        <Button
          variant="secondary"
          disabled={!!query.email || page.page + 1 >= page.totalPages}
          onClick={() => onPageChange((q) => ({ ...q, page: q.page + 1 }))}
          data-testid="accounts-next"
        >
          다음
        </Button>
      </nav>
    </>
  );
}
