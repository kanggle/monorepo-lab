import Link from 'next/link';
import { Button } from '@/shared/ui/Button';
import type { ApproveResult } from '../api/types';

/**
 * The approve-success affordance for {@link ReplenishmentScreen} (TASK-PC-FE-190
 * split). Surfaces the materialised DRAFT `poId` / `poStatus` after a successful
 * approve (incl. the idempotent re-approve path returning the existing poId) and
 * the explicit "PO created as DRAFT — submit it in Procurement" hand-off (this
 * screen NEVER submits). Renders nothing when there is no approved result.
 * Pure presentation — markup + testids preserved verbatim from the pre-split
 * screen (`repl-approved-*`).
 */
export function ApprovedDraftBanner({
  approved,
  onDismiss,
}: {
  approved: ApproveResult | null;
  onDismiss: () => void;
}) {
  if (!approved) return null;
  return (
    <div
      role="status"
      data-testid="repl-approved-draft"
      className="mb-6 rounded-md border-2 border-emerald-500 bg-emerald-50 px-4 py-3 text-sm text-emerald-900 dark:border-emerald-600 dark:bg-emerald-950/50 dark:text-emerald-100"
    >
      <p className="font-medium">
        보충 추천이 승인되어 <strong>DRAFT</strong> 발주가 생성되었습니다.
      </p>
      <p className="mt-1">
        PO:{' '}
        <span data-testid="repl-approved-poid" className="font-mono">
          {approved.poId ?? '—'}
        </span>{' '}
        · 상태:{' '}
        <span data-testid="repl-approved-postatus" className="font-medium">
          {approved.poStatus ?? '—'}
        </span>
      </p>
      <p className="mt-2">
        이 발주는 <strong>DRAFT</strong> 상태입니다 — 제출(SUBMIT)은 이
        화면이 아니라 <strong>조달(Procurement)</strong> 에서 별도로
        진행해야 합니다.{' '}
        <Link
          href="/scm"
          data-testid="repl-procurement-link"
          className="underline focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
        >
          조달(발주) 화면으로 이동
        </Link>
      </p>
      <Button
        variant="ghost"
        size="sm"
        onClick={onDismiss}
        data-testid="repl-approved-dismiss"
        className="mt-2"
      >
        닫기
      </Button>
    </div>
  );
}
