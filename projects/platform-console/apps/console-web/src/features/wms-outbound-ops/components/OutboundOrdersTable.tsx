'use client';

import type { FormEvent } from 'react';
import { Button } from '@/shared/ui/Button';
import { messageForCode } from '@/shared/api/errors';
import {
  STATUS_FILTER_OPTIONS,
} from './outbound-ops-helpers';
import type { OutboundOrderPage, OutboundListParams } from '../api/types';

/**
 * Orders table region of the wms outbound screen (TASK-PC-FE-101 split) —
 * the status filter form, the forbidden / degraded / empty notices, the
 * orders table, and the pagination nav. Pure presentation: all state +
 * handlers live in the `OutboundOpsScreen` container and arrive via props.
 */
export interface OutboundOrdersTableProps {
  statusFid: string;
  statusFilter: string;
  onStatusFilterChange: (value: string) => void;
  onSubmitFilter: (e: FormEvent) => void;
  ordersForbidden: boolean;
  ordersDegraded: boolean;
  ordersData: OutboundOrderPage;
  query: OutboundListParams;
  onPrevPage: () => void;
  onNextPage: () => void;
  onDrill: (orderId: string) => void;
}

export function OutboundOrdersTable({
  statusFid,
  statusFilter,
  onStatusFilterChange,
  onSubmitFilter,
  ordersForbidden,
  ordersDegraded,
  ordersData,
  query,
  onPrevPage,
  onNextPage,
  onDrill,
}: OutboundOrdersTableProps) {
  const rows = ordersData.content;
  const totalPages = Math.max(1, ordersData.page.totalPages);

  return (
    <>
      {/* ── Orders table ─────────────────────────────────────────────────── */}
      <h2 className="mb-3 text-lg font-medium text-foreground">출고 주문</h2>
      <form
        onSubmit={onSubmitFilter}
        className="mb-4 flex flex-wrap items-end gap-3"
        role="search"
        aria-label="출고 주문 필터"
      >
        <div>
          <label
            htmlFor={statusFid}
            className="block text-sm font-medium text-foreground"
          >
            상태
          </label>
          <select
            id={statusFid}
            value={statusFilter}
            onChange={(e) => onStatusFilterChange(e.target.value)}
            data-testid="outbound-status-filter"
            className="mt-1 rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
          >
            {STATUS_FILTER_OPTIONS.map((s) => (
              <option key={s || 'all'} value={s}>
                {s || '전체'}
              </option>
            ))}
          </select>
        </div>
        <Button type="submit" data-testid="outbound-filter-submit">
          조회
        </Button>
      </form>

      {ordersForbidden ? (
        <div
          role="status"
          data-testid="outbound-forbidden"
          className="mb-8 rounded-md border border-border bg-muted px-4 py-3 text-sm text-muted-foreground"
        >
          {messageForCode('FORBIDDEN')}
        </div>
      ) : ordersDegraded ? (
        <div
          role="status"
          data-testid="outbound-degraded"
          className="mb-8 rounded-md border border-border bg-muted px-4 py-3 text-sm text-muted-foreground"
        >
          wms 출고 정보를 일시적으로 불러올 수 없습니다. 콘솔의 다른 기능은
          계속 사용할 수 있습니다.
        </div>
      ) : rows.length === 0 ? (
        <p
          className="mb-8 text-sm text-muted-foreground"
          data-testid="outbound-empty"
        >
          표시할 출고 주문이 없습니다.
        </p>
      ) : (
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
                    {o.status ?? '—'}
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
      )}
    </>
  );
}
