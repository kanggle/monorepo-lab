import { StackedBar, type BarSegment } from '@/shared/ui/InlineBar';
import type { ScmPoStatusCount } from '../api/overview-state';
import { poStatusTone } from './scm-ops-helpers';

/**
 * One stacked bar over the PO-status distribution (TASK-PC-FE-363) — the twin
 * of `ShippingStatusBar` / `ApprovalStatusBar` (TASK-PC-FE-360). Server
 * component (NO `'use client'`). The buckets arrive in lifecycle order
 * (`PO_STATUS_BUCKETS`); each segment takes the status' `poStatusTone`. The
 * numbers stay in the `<dl>` beside it — the bar is decorative.
 *
 * Every bucket is a full-population status-filtered `totalElements`
 * (`?status=<S>&size=1`), never a first-page count. Draws nothing when the
 * distribution is incomplete (any bucket not `ok` / null count — a partial
 * distribution would read as the whole) or empty list. All-zero → the
 * «발주가 없습니다.» note instead of an empty bar. (SETTLED / CLOSED are real
 * statuses the producer cannot yet write — settlement-service is deferred — so
 * they count 0 today and contribute no segment.)
 */
export function buildPoStatusSegments(
  buckets: readonly ScmPoStatusCount[],
): BarSegment[] | null {
  if (buckets.length === 0) return null;
  if (buckets.some((b) => b.cellStatus !== 'ok' || b.count === null)) {
    return null;
  }
  return buckets.map((b) => ({
    key: b.status,
    value: b.count,
    tone: poStatusTone(b.status),
  }));
}

export function PoStatusBar({
  buckets,
}: {
  buckets: readonly ScmPoStatusCount[];
}) {
  const segments = buildPoStatusSegments(buckets);
  if (!segments) return null;
  const total = segments.reduce((acc, s) => acc + (s.value ?? 0), 0);
  if (total <= 0) {
    return (
      <p
        className="mb-2 text-sm text-muted-foreground"
        data-testid="scm-po-status-empty"
      >
        발주가 없습니다.
      </p>
    );
  }
  return (
    <div className="mb-2">
      <StackedBar segments={segments} testid="scm-po-status-bar" />
    </div>
  );
}
