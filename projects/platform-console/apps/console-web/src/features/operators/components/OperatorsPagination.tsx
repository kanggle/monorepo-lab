'use client';

import { Button } from '@/shared/ui/Button';
import type { OperatorPage } from '../api/types';

/**
 * Pagination nav for the operators list (TASK-PC-FE-209 split of
 * `OperatorsTable`). Presentational — the current page + page hooks live in
 * the `OperatorsScreen` container and arrive via props.
 */
export interface OperatorsPaginationProps {
  page: OperatorPage | undefined;
  currentPage: number;
  onPrevPage: () => void;
  onNextPage: () => void;
}

export function OperatorsPagination({
  page,
  currentPage,
  onPrevPage,
  onNextPage,
}: OperatorsPaginationProps) {
  return (
    <nav
      className="mt-4 flex items-center justify-between"
      aria-label="페이지 이동"
    >
      <Button
        variant="secondary"
        disabled={currentPage <= 0}
        onClick={onPrevPage}
        data-testid="operators-prev"
      >
        이전
      </Button>
      <span
        className="text-sm text-muted-foreground"
        data-testid="operators-pageinfo"
      >
        {page
          ? `${page.page + 1} / ${Math.max(1, page.totalPages)} 페이지 · 총 ${page.totalElements}명`
          : '—'}
      </span>
      <Button
        variant="secondary"
        disabled={
          !page || page.page + 1 >= page.totalPages
        }
        onClick={onNextPage}
        data-testid="operators-next"
      >
        다음
      </Button>
    </nav>
  );
}
