'use client';

import type { Dispatch, SetStateAction } from 'react';
import { Button } from '@/shared/ui/Button';
import type { AccountPage } from '../api/types';
import type { AccountsQuery } from './accounts-screen-helpers';

/**
 * `AccountsTable` pagination nav (TASK-PC-FE-210 split). Presentational — the
 * container passes the `setQuery` dispatcher as `onPageChange` so the
 * functional prev/next updates are preserved. Single-email searches show
 * "단건 검색" and pin the prev/next buttons disabled, exactly as before. Every
 * `data-testid` / aria / label is byte-identical.
 */
export interface AccountsPaginationProps {
  page: AccountPage;
  query: AccountsQuery;
  onPageChange: Dispatch<SetStateAction<AccountsQuery>>;
}

export function AccountsPagination({
  page,
  query,
  onPageChange,
}: AccountsPaginationProps) {
  return (
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
  );
}
