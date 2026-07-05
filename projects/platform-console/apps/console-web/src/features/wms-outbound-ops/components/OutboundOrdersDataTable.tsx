'use client';

import { Button } from '@/shared/ui/Button';
import { StatusBadge } from '@/shared/ui/StatusBadge';
import { outboundStatusTone } from './outbound-ops-helpers';
import type { OutboundOrderPage, OutboundListParams } from '../api/types';

/**
 * The 출고 주문 table + pagination nav for {@link OutboundOrdersTable}
 * (TASK-PC-FE-198 split) — rendered only in the loaded/non-empty branch (the
 * parent owns the forbidden / degraded / empty branches). Each row carries a
 * 상세 drill affordance; pagination keys prev-disabled off the REQUESTED
 * `query.page` while pageinfo + next-disabled key off the RETURNED
 * `ordersData.page`. Pure presentation — markup + testids preserved verbatim
 * (`outbound-table`, `outbound-row-*`, `outbound-row-status-*`,
 * `outbound-drill-*`, `outbound-prev/next/pageinfo`).
 */
export interface OutboundOrdersDataTableProps {
  ordersData: OutboundOrderPage;
  query: OutboundListParams;
  onPrevPage: () => void;
  onNextPage: () => void;
  onDrill: (orderId: string) => void;
}

export function OutboundOrdersDataTable({
  ordersData,
  query,
  onPrevPage,
  onNextPage,
  onDrill,
}: OutboundOrdersDataTableProps) {
  const rows = ordersData.content;
  const totalPages = Math.max(1, ordersData.page.totalPages);

  return (
    <>
      <table className="mb-3 data-table" data-testid="outbound-table">
        <caption className="sr-only">출고 주문 목록</caption>
        <thead>
          <tr className="border-b border-border text-left">
            <th scope="col" className="p-2">
              주문번호
            </th>
            <th scope="col" className="p-2">
              상태
            </th>
            <th scope="col" className="p-2">
              saga
            </th>
            <th scope="col" className="p-2">
              라인 수
            </th>
            <th scope="col" className="p-2">
              생성 시각 (UTC)
            </th>
            <th scope="col" className="p-2">
              작업
            </th>
          </tr>
        </thead>
        <tbody>
          {rows.map((o, i) => (
            <tr
              key={o.orderId}
              data-testid={`outbound-row-${i}`}
              className="border-b border-border"
            >
              <td className="p-2">{o.orderNo ?? o.orderId}</td>
              <td className="p-2" data-testid={`outbound-row-status-${i}`}>
                <StatusBadge tone={outboundStatusTone(o.status)}>
                  {o.status ?? '—'}
                </StatusBadge>
              </td>
              <td className="p-2">{o.sagaState ?? '—'}</td>
              <td className="p-2">{o.lineCount ?? '—'}</td>
              <td className="p-2">{o.createdAt ?? '—'}</td>
              <td className="p-2">
                <Button
                  variant="secondary"
                  size="sm"
                  onClick={() => onDrill(o.orderId)}
                  data-testid={`outbound-drill-${i}`}
                >
                  상세
                </Button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
      <nav
        className="mb-8 flex items-center justify-between"
        aria-label="출고 주문 페이지 이동"
      >
        <Button
          variant="secondary"
          disabled={(query.page ?? 0) <= 0}
          onClick={onPrevPage}
          data-testid="outbound-prev"
        >
          이전
        </Button>
        <span
          className="text-sm text-muted-foreground"
          data-testid="outbound-pageinfo"
        >
          {`${ordersData.page.number + 1} / ${totalPages} 페이지 · 총 ${ordersData.page.totalElements}건`}
        </span>
        <Button
          variant="secondary"
          disabled={ordersData.page.number + 1 >= ordersData.page.totalPages}
          onClick={onNextPage}
          data-testid="outbound-next"
        >
          다음
        </Button>
      </nav>
    </>
  );
}
