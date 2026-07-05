'use client';

import type { FormEvent } from 'react';
import { messageForCode } from '@/shared/api/errors';
import type { OutboundOrderPage, OutboundListParams } from '../api/types';
import { OutboundOrdersFilter } from './OutboundOrdersFilter';
import { OutboundOrdersDataTable } from './OutboundOrdersDataTable';

/**
 * Orders table region of the wms outbound screen (TASK-PC-FE-101 split) —
 * the status filter form, the forbidden / degraded / empty notices, the
 * orders table, and the pagination nav. Pure presentation: all state +
 * handlers live in the `OutboundOpsScreen` container and arrive via props.
 *
 * ── SPLIT (TASK-PC-FE-198) ── the status filter form moved to
 * `OutboundOrdersFilter` and the loaded-branch table + pagination to
 * `OutboundOrdersDataTable`; this file keeps the heading + the forbidden /
 * degraded / empty / table branching.
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

  return (
    <>
      {/* ── Orders table ─────────────────────────────────────────────────── */}
      <h2 className="mb-3 text-lg font-medium text-foreground">출고 주문</h2>
      <OutboundOrdersFilter
        statusFid={statusFid}
        statusFilter={statusFilter}
        onStatusFilterChange={onStatusFilterChange}
        onSubmitFilter={onSubmitFilter}
      />

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
        <OutboundOrdersDataTable
          ordersData={ordersData}
          query={query}
          onPrevPage={onPrevPage}
          onNextPage={onNextPage}
          onDrill={onDrill}
        />
      )}
    </>
  );
}
