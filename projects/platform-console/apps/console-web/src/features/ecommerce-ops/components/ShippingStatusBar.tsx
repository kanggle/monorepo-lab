import { StackedBar, type BarSegment } from '@/shared/ui/InlineBar';
import type { ShippingStatusCount } from '../api/overview-state';
import { shippingStatusTone } from '../api/shipping-types';

/**
 * One stacked bar over the shipping-status distribution (TASK-PC-FE-360) — the
 * twin of `OrderStatusBar`. Server component (NO `'use client'`). The buckets
 * arrive in lifecycle order (`SHIPPING_STATUS_VALUES`); each segment takes the
 * status' `shippingStatusTone`. The numbers stay in the `<dl>` beside it — the
 * bar is decorative.
 *
 * Draws nothing when the distribution is incomplete (any bucket not `ok` /
 * null count — a partial distribution would read as the whole) or empty list.
 * All-zero → the «배송이 없습니다.» note instead of an empty bar. Only the four
 * known statuses are counted: an unknown future status would make the sum
 * differ from the list total (accepted — the bar is a proportion of the known
 * lifecycle, the list screen stays authoritative).
 */
export function buildShippingStatusSegments(
  buckets: readonly ShippingStatusCount[],
): BarSegment[] | null {
  if (buckets.length === 0) return null;
  if (buckets.some((b) => b.cellStatus !== 'ok' || b.count === null)) {
    return null;
  }
  return buckets.map((b) => ({
    key: b.status,
    value: b.count,
    tone: shippingStatusTone(b.status),
  }));
}

export function ShippingStatusBar({
  buckets,
}: {
  buckets: readonly ShippingStatusCount[];
}) {
  const segments = buildShippingStatusSegments(buckets);
  if (!segments) return null;
  const total = segments.reduce((acc, s) => acc + (s.value ?? 0), 0);
  if (total <= 0) {
    return (
      <p
        className="mb-2 text-sm text-muted-foreground"
        data-testid="ecommerce-shipping-status-empty"
      >
        배송이 없습니다.
      </p>
    );
  }
  return (
    <div className="mb-2">
      <StackedBar segments={segments} testid="ecommerce-shipping-status-bar" />
    </div>
  );
}
