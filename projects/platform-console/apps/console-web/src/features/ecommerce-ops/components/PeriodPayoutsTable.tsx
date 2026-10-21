'use client';

import { StatusBadge } from '@/shared/ui/StatusBadge';
import { Button } from '@/shared/ui/Button';
import { formatDateTime } from '@/shared/lib/datetime';
import { ProportionBar } from '@/shared/ui/InlineBar';
import {
  payoutStatusTone,
  minorToWon,
  type PayoutsResponse,
} from '../api/settlement-types';
import { payoutBarMax, payoutBarScopeText } from '../lib/payout-bars';

interface PeriodPayoutsTableProps {
  rows: PayoutsResponse['items'];
  /** Producer `totalElements` — the bars compare only the rows on this page, so the screen says so when this page is not the whole set (TASK-PC-FE-359). */
  totalElements: number;
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
 *
 * TASK-PC-FE-359 — a per-seller `payableNetMinor` bar beside each amount
 * (shared `ProportionBar`, decorative, coloured by the payout status tone).
 * The scale is the largest POSITIVE amount on THIS page; a zero / negative
 * (clawback) amount draws no bar, its text stands alone. The amounts are
 * integer `number`s (not F5 strings), so no chart-scale exception is used.
 */
export function PeriodPayoutsTable({
  rows,
  totalElements,
  pagination,
}: PeriodPayoutsTableProps) {
  const max = payoutBarMax(rows);
  const hasNonPositive = rows.some((p) => !(p.payableNetMinor > 0));
  return (
    <>
      <p
        className="mb-2 text-xs text-muted-foreground"
        data-testid="settlements-payouts-bar-scope"
      >
        {payoutBarScopeText(rows.length, totalElements)}
        {hasNonPositive ? (
          <span data-testid="settlements-payouts-bar-nonpositive">
            {' 0원이거나 음수(되돌림)인 지급액은 막대 없이 금액만 표시합니다.'}
          </span>
        ) : null}
      </p>
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
                  <div className="flex items-center justify-end gap-2">
                    {p.payableNetMinor > 0 ? (
                      <ProportionBar
                        value={p.payableNetMinor}
                        max={max}
                        tone={payoutStatusTone(p.status)}
                        className="w-20"
                        testid={`payout-row-bar-${i}`}
                      />
                    ) : null}
                    <span>{minorToWon(p.payableNetMinor)}</span>
                  </div>
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
