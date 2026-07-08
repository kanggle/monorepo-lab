'use client';

import { StatusBadge } from '@/shared/ui/StatusBadge';
import { Button } from '@/shared/ui/Button';
import { formatDateTime } from '@/shared/lib/datetime';
import {
  payoutStatusTone,
  minorToWon,
  type PayoutsResponse,
} from '../api/settlement-types';

interface PeriodPayoutsTableProps {
  rows: PayoutsResponse['items'];
  pagination: {
    prevDisabled: boolean;
    nextDisabled: boolean;
    pageInfo: string;
    onPrev: () => void;
    onNext: () => void;
  };
}

/**
 * Per-period payouts table + pagination (TASK-PC-FE-221 Phase A, presentational
 * only). READ display of the seller payouts a CLOSED period folded its accruals
 * into. Money renders via {@link minorToWon}; `paidAt` is a record timestamp.
 * Payout execution (PENDING → PAID/FAILED) is Phase B — no action column here.
 */
export function PeriodPayoutsTable({ rows, pagination }: PeriodPayoutsTableProps) {
  return (
    <>
      <div className="overflow-x-auto">
        <table
          className="mb-3 data-table"
          data-testid="settlements-payouts-table"
        >
          <caption className="sr-only">기간 지급 내역</caption>
          <thead>
            <tr className="border-b border-border text-left">
              <th scope="col" className="p-2">
                지급 ID
              </th>
              <th scope="col" className="p-2">
                셀러
              </th>
              <th scope="col" className="p-2">
                상태
              </th>
              <th scope="col" className="p-2 text-right">
                지급액
              </th>
              <th scope="col" className="p-2 text-right">
                수수료
              </th>
              <th scope="col" className="p-2 text-right">
                라인 수
              </th>
              <th scope="col" className="p-2">
                지급 참조
              </th>
              <th scope="col" className="p-2">
                지급일시
              </th>
            </tr>
          </thead>
          <tbody>
            {rows.map((p, i) => (
              <tr
                key={p.payoutId}
                data-testid={`payout-row-${i}`}
                className="border-b border-border"
              >
                <td className="p-2 font-mono text-xs">{p.payoutId}</td>
                <td className="p-2 font-mono text-xs">{p.sellerId}</td>
                <td className="p-2" data-testid={`payout-row-status-${i}`}>
                  <StatusBadge tone={payoutStatusTone(p.status)}>
                    {p.status}
                  </StatusBadge>
                </td>
                <td className="p-2 text-right tabular-nums">
                  {minorToWon(p.payableNetMinor)}
                </td>
                <td className="p-2 text-right tabular-nums">
                  {minorToWon(p.commissionMinor)}
                </td>
                <td className="p-2 text-right tabular-nums">
                  {p.accrualCount}
                </td>
                <td className="p-2 font-mono text-xs text-muted-foreground">
                  {p.payoutReference ?? '—'}
                </td>
                <td className="p-2 text-sm text-muted-foreground">
                  {formatDateTime(p.paidAt)}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <nav
        className="flex items-center justify-between"
        aria-label="지급 내역 페이지 이동"
      >
        <Button
          variant="secondary"
          disabled={pagination.prevDisabled}
          onClick={pagination.onPrev}
          data-testid="settlements-payouts-prev"
        >
          이전
        </Button>
        <span
          className="text-sm text-muted-foreground"
          data-testid="settlements-payouts-pageinfo"
        >
          {pagination.pageInfo}
        </span>
        <Button
          variant="secondary"
          disabled={pagination.nextDisabled}
          onClick={pagination.onNext}
          data-testid="settlements-payouts-next"
        >
          다음
        </Button>
      </nav>
    </>
  );
}
