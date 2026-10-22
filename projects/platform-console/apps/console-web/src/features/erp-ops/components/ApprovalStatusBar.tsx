import { StackedBar, type BarSegment } from '@/shared/ui/InlineBar';
import type { ApprovalStatusCount } from '../api/overview-state';
import { approvalStatusTone } from '../api/approval-types';

/**
 * One stacked bar over the approval-status distribution (TASK-PC-FE-360) — the
 * twin of the ecommerce `OrderStatusBar`. Server component (NO `'use client'`).
 * The buckets arrive in lifecycle order (`APPROVAL_STATUSES`); each segment
 * takes the status' `approvalStatusTone`. The numbers stay in the `<dl>` beside
 * it — the bar is decorative.
 *
 * Draws nothing when the distribution is incomplete (any bucket not `ok` /
 * null count — a partial distribution would read as the whole) or empty list.
 * All-zero → the «결재가 없습니다.» note instead of an empty bar. Only the six
 * known statuses are counted: an unknown future status would make the sum
 * differ from the list total (accepted — the approval screen stays
 * authoritative).
 */
export function buildApprovalStatusSegments(
  buckets: readonly ApprovalStatusCount[],
): BarSegment[] | null {
  if (buckets.length === 0) return null;
  if (buckets.some((b) => b.cellStatus !== 'ok' || b.count === null)) {
    return null;
  }
  return buckets.map((b) => ({
    key: b.status,
    value: b.count,
    tone: approvalStatusTone(b.status),
  }));
}

export function ApprovalStatusBar({
  buckets,
}: {
  buckets: readonly ApprovalStatusCount[];
}) {
  const segments = buildApprovalStatusSegments(buckets);
  if (!segments) return null;
  const total = segments.reduce((acc, s) => acc + (s.value ?? 0), 0);
  if (total <= 0) {
    return (
      <p
        className="mb-2 text-sm text-muted-foreground"
        data-testid="erp-overview-approval-status-empty"
      >
        결재가 없습니다.
      </p>
    );
  }
  return (
    <div className="mb-2">
      <StackedBar
        segments={segments}
        testid="erp-overview-approval-status-bar"
      />
    </div>
  );
}
