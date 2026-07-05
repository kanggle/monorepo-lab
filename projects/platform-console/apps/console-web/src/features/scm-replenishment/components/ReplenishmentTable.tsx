import { Button } from '@/shared/ui/Button';
import { StatusBadge } from '@/shared/ui/StatusBadge';
import {
  suggestionStatusTone,
  canApprove,
  canDismiss,
  type Suggestion,
} from '../api/types';

type ActionKind = 'approve' | 'dismiss';

/**
 * The suggestion table + pagination for {@link ReplenishmentScreen} (TASK-PC-FE-190
 * split) — rendered only in the loaded/non-empty branch (the parent owns the
 * forbidden / rate-limited / degraded / empty branches). Each row shows the
 * `triggerAvailableQty` that explains WHY it was suggested; the approve / dismiss
 * buttons gate on `canApprove` / `canDismiss` (+ any pending action). Pure
 * presentation — markup + testids preserved verbatim (`repl-table`,
 * `repl-row-*`, `repl-approve-*`, `repl-dismiss-*`, `repl-prev/next/pageinfo`).
 *
 * Pagination note: prev-disabled keys off the REQUESTED `queryPage` while
 * pageinfo + next-disabled key off the RETURNED `dataPage` / `totalElements` —
 * exactly as the pre-split screen did.
 */
export function ReplenishmentTable({
  rows,
  queryPage,
  dataPage,
  totalPages,
  totalElements,
  onPrev,
  onNext,
  onAction,
  actionPending,
}: {
  rows: Suggestion[];
  queryPage: number;
  dataPage: number;
  totalPages: number;
  totalElements: number;
  onPrev: () => void;
  onNext: () => void;
  onAction: (kind: ActionKind, suggestion: Suggestion) => void;
  actionPending: boolean;
}) {
  return (
    <>
      <table className="mb-3 data-table" data-testid="repl-table">
        <caption className="sr-only">보충 추천 목록</caption>
        <thead>
          <tr className="border-b border-border text-left">
            <th scope="col" className="p-2">
              SKU
            </th>
            <th scope="col" className="p-2">
              창고
            </th>
            <th scope="col" className="p-2">
              공급사
            </th>
            <th scope="col" className="p-2">
              추천 수량
            </th>
            <th scope="col" className="p-2">
              트리거 가용재고
            </th>
            <th scope="col" className="p-2">
              상태
            </th>
            <th scope="col" className="p-2">
              PO
            </th>
            <th scope="col" className="p-2">
              작업
            </th>
          </tr>
        </thead>
        <tbody>
          {rows.map((s, i) => {
            const approveOk = canApprove(s.status);
            const dismissOk = canDismiss(s.status);
            return (
              <tr
                key={s.id}
                data-testid={`repl-row-${i}`}
                className="border-b border-border"
              >
                <td className="p-2">{s.skuCode ?? '—'}</td>
                <td className="p-2">{s.warehouseId ?? '—'}</td>
                <td className="p-2">{s.supplierId ?? '—'}</td>
                <td className="p-2">{s.suggestedQty ?? '—'}</td>
                <td className="p-2" data-testid={`repl-row-trigger-${i}`}>
                  {s.triggerAvailableQty ?? '—'}
                </td>
                <td className="p-2" data-testid={`repl-row-status-${i}`}>
                  <StatusBadge tone={suggestionStatusTone(s.status)}>
                    {s.status ?? '—'}
                  </StatusBadge>
                </td>
                <td className="p-2">{s.materializedPoId ?? '—'}</td>
                <td className="p-2">
                  <div className="flex gap-2">
                    <Button
                      size="sm"
                      onClick={() => onAction('approve', s)}
                      disabled={!approveOk || actionPending}
                      data-testid={`repl-approve-${i}`}
                    >
                      승인
                    </Button>
                    <Button
                      variant="secondary"
                      size="sm"
                      onClick={() => onAction('dismiss', s)}
                      disabled={!dismissOk || actionPending}
                      data-testid={`repl-dismiss-${i}`}
                    >
                      기각
                    </Button>
                  </div>
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
      <nav
        className="mb-8 flex items-center justify-between"
        aria-label="보충 추천 페이지 이동"
      >
        <Button
          variant="secondary"
          disabled={queryPage <= 0}
          onClick={onPrev}
          data-testid="repl-prev"
        >
          이전
        </Button>
        <span className="text-sm text-muted-foreground" data-testid="repl-pageinfo">
          {`${dataPage + 1} / ${totalPages} 페이지 · 총 ${totalElements}건`}
        </span>
        <Button
          variant="secondary"
          disabled={dataPage + 1 >= totalPages}
          onClick={onNext}
          data-testid="repl-next"
        >
          다음
        </Button>
      </nav>
    </>
  );
}
