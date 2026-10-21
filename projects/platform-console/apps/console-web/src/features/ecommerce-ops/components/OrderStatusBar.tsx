import { StackedBar, type BarSegment } from '@/shared/ui/InlineBar';
import type { OrderStatusCount } from '../api/overview-state';
import { orderStatusTone } from '../api/order-types';

/**
 * One stacked bar over the order-status distribution (TASK-PC-FE-357). Server
 * component (NO `'use client'`). The buckets already arrive in lifecycle order
 * (`ORDER_STATUS_VALUES`); each segment takes the status' `orderStatusTone`.
 * The numbers stay in the `<dl>` beside it — the bar is decorative.
 *
 * Draws nothing when the distribution is incomplete (any bucket not `ok` /
 * null count — a partial distribution would read as the whole) or empty list.
 * All-zero → the «주문이 없습니다.» note instead of an empty bar.
 */
export function buildOrderStatusSegments(
  buckets: readonly OrderStatusCount[],
): BarSegment[] | null {
  if (buckets.length === 0) return null;
  if (buckets.some((b) => b.cellStatus !== 'ok' || b.count === null)) {
    return null;
  }
  return buckets.map((b) => ({
    key: b.status,
    value: b.count,
    tone: orderStatusTone(b.status),
  }));
}

export function OrderStatusBar({
  buckets,
}: {
  buckets: readonly OrderStatusCount[];
}) {
  const segments = buildOrderStatusSegments(buckets);
  if (!segments) return null;
  const total = segments.reduce((acc, s) => acc + (s.value ?? 0), 0);
  if (total <= 0) {
    return (
      <p
        className="mb-2 text-sm text-muted-foreground"
        data-testid="ecommerce-order-status-empty"
      >
        주문이 없습니다.
      </p>
    );
  }
  return (
    <div className="mb-2">
      <StackedBar segments={segments} testid="ecommerce-order-status-bar" />
    </div>
  );
}
