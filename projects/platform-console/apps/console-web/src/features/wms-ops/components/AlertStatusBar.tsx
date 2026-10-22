import { StackedBar, type BarSegment } from '@/shared/ui/InlineBar';
import type { StatusTone } from '@/shared/ui/StatusBadge';
import type { WmsAlertStatusCount } from '../api/overview-state';

/**
 * One stacked bar over the alert-acknowledgement distribution
 * (TASK-PC-FE-363) — the twin of `ShippingStatusBar` / `ApprovalStatusBar`
 * (TASK-PC-FE-360). Server component (NO `'use client'`). 미확인 takes the
 * `warning` tone (an attention signal), 확인 `neutral`. The numbers stay in
 * the `<dl>` beside it — the bar is decorative.
 *
 * Both buckets are full-population `totalElements` of the admin read model
 * filtered on `acknowledged_at IS NULL` / `IS NOT NULL` — two complete,
 * disjoint partitions. Draws nothing when either bucket is not `ok` / null
 * (a partial distribution would read as the whole) or the list is empty.
 * All-zero → the «알림이 없습니다.» note instead of an empty bar.
 */
export const ALERT_STATUS_TONE: Record<WmsAlertStatusCount['key'], StatusTone> =
  {
    unacknowledged: 'warning',
    acknowledged: 'neutral',
  };

export function alertStatusTone(key: WmsAlertStatusCount['key']): StatusTone {
  return ALERT_STATUS_TONE[key] ?? 'neutral';
}

export function buildAlertStatusSegments(
  buckets: readonly WmsAlertStatusCount[],
): BarSegment[] | null {
  if (buckets.length === 0) return null;
  if (buckets.some((b) => b.cellStatus !== 'ok' || b.count === null)) {
    return null;
  }
  return buckets.map((b) => ({
    key: b.key,
    value: b.count,
    tone: alertStatusTone(b.key),
  }));
}

export function AlertStatusBar({
  buckets,
}: {
  buckets: readonly WmsAlertStatusCount[];
}) {
  const segments = buildAlertStatusSegments(buckets);
  if (!segments) return null;
  const total = segments.reduce((acc, s) => acc + (s.value ?? 0), 0);
  if (total <= 0) {
    return (
      <p
        className="mb-2 text-sm text-muted-foreground"
        data-testid="wms-alert-status-empty"
      >
        알림이 없습니다.
      </p>
    );
  }
  return (
    <div className="mb-2">
      <StackedBar segments={segments} testid="wms-alert-status-bar" />
    </div>
  );
}
