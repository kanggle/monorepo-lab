'use client';

import { StatusBadge } from '@/shared/ui/StatusBadge';
import { Button } from '@/shared/ui/Button';
import { formatDateTime } from '@/shared/lib/datetime';
import {
  accrualTypeTone,
  minorToWon,
  rateBpsToPercent,
  type AccrualsResponse,
} from '../api/settlement-types';

interface AccrualsTableProps {
  rows: AccrualsResponse['items'];
  pagination: {
    prevDisabled: boolean;
    nextDisabled: boolean;
    pageInfo: string;
    onPrev: () => void;
    onNext: () => void;
  };
}

/**
 * Settlement accrual-lines table + pagination (TASK-PC-FE-221 Phase A,
 * presentational only). Append-only ledger: ACCRUAL (positive commission) +
 * REVERSAL (negative clawback). Money renders via {@link minorToWon} (integer,
 * sign-preserving); the rate via {@link rateBpsToPercent}. Query/pagination state
 * stays owned by the parent section.
 */
export function AccrualsTable({ rows, pagination }: AccrualsTableProps) {
  return (
    <>
      <div className="overflow-x-auto">
        <table
          className="mb-3 data-table"
          data-testid="settlements-accruals-table"
        >
          <caption className="sr-only">정산 적립 라인</caption>
          <thead>
            <tr className="border-b border-border text-left">
              <th scope="col" className="p-2">
                발생일시
              </th>
              <th scope="col" className="p-2">
                셀러
              </th>
              <th scope="col" className="p-2">
                주문
              </th>
              <th scope="col" className="p-2">
                유형
              </th>
              <th scope="col" className="p-2 text-right">
                총액
              </th>
              <th scope="col" className="p-2 text-right">
                수수료율
              </th>
              <th scope="col" className="p-2 text-right">
                수수료
              </th>
              <th scope="col" className="p-2 text-right">
                셀러 정산액
              </th>
            </tr>
          </thead>
          <tbody>
            {rows.map((a, i) => (
              <tr
                key={a.accrualId}
                data-testid={`accrual-row-${i}`}
                className="border-b border-border"
              >
                <td className="p-2 text-sm text-muted-foreground">
                  {formatDateTime(a.occurredAt)}
                </td>
                <td className="p-2 font-mono text-xs">{a.sellerId}</td>
                <td className="p-2 font-mono text-xs">{a.orderId}</td>
                <td className="p-2" data-testid={`accrual-row-type-${i}`}>
                  <StatusBadge tone={accrualTypeTone(a.type)}>
                    {a.type}
                  </StatusBadge>
                </td>
                <td className="p-2 text-right tabular-nums">
                  {minorToWon(a.grossMinor)}
                </td>
                <td className="p-2 text-right tabular-nums">
                  {rateBpsToPercent(a.rateBps)}
                </td>
                <td className="p-2 text-right tabular-nums">
                  {minorToWon(a.commissionMinor)}
                </td>
                <td className="p-2 text-right tabular-nums">
                  {minorToWon(a.sellerNetMinor)}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <nav
        className="flex items-center justify-between"
        aria-label="정산 라인 페이지 이동"
      >
        <Button
          variant="secondary"
          disabled={pagination.prevDisabled}
          onClick={pagination.onPrev}
          data-testid="settlements-accruals-prev"
        >
          이전
        </Button>
        <span
          className="text-sm text-muted-foreground"
          data-testid="settlements-accruals-pageinfo"
        >
          {pagination.pageInfo}
        </span>
        <Button
          variant="secondary"
          disabled={pagination.nextDisabled}
          onClick={pagination.onNext}
          data-testid="settlements-accruals-next"
        >
          다음
        </Button>
      </nav>
    </>
  );
}
