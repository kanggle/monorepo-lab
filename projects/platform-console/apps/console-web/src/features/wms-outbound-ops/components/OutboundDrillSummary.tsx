'use client';

import { StatusBadge } from '@/shared/ui/StatusBadge';
import type { OutboundOrderDetail, OutboundSaga } from '../api/types';
import { outboundStatusTone } from './outbound-ops-helpers';

/**
 * Order-drill summary field grid (TASK-PC-FE-198 split) — the `<dl>` header
 * of the outbound order drill (order number, status badge, saga state,
 * version). Pure presentation; the {@link OutboundOrderDrill} container owns
 * the state and renders this only once detail + saga are loaded.
 */
export interface OutboundDrillSummaryProps {
  detail: OutboundOrderDetail;
  saga: OutboundSaga;
}

export function OutboundDrillSummary({ detail, saga }: OutboundDrillSummaryProps) {
  return (
    <dl className="mb-4 grid grid-cols-2 gap-2 text-sm sm:grid-cols-4">
      <div>
        <dt className="text-muted-foreground">주문번호</dt>
        <dd data-testid="outbound-drill-orderno">
          {detail.orderNo ?? detail.orderId}
        </dd>
      </div>
      <div>
        <dt className="text-muted-foreground">상태</dt>
        <dd data-testid="outbound-drill-status">
          <StatusBadge tone={outboundStatusTone(detail.status)}>
            {detail.status}
          </StatusBadge>
        </dd>
      </div>
      <div>
        <dt className="text-muted-foreground">saga 상태</dt>
        <dd data-testid="outbound-drill-saga">{saga.state}</dd>
      </div>
      <div>
        <dt className="text-muted-foreground">version</dt>
        <dd>{detail.version}</dd>
      </div>
    </dl>
  );
}
