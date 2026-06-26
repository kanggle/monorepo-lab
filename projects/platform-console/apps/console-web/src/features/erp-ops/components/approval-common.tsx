import { isTerminalApprovalStatus } from '../api/approval-types';

/**
 * Shared presentational primitives for the approval surface
 * (TASK-PC-FE-100 split). Used by `ApprovalScreen`, `ApprovalDetail`,
 * and `ApprovalCreateDialog` — the subject/status label maps + the
 * status badge. Pure presentation, no hooks, no data fetching.
 */

export const SUBJECT_LABEL: Record<string, string> = {
  DEPARTMENT: '부서',
  EMPLOYEE: '직원',
};

const STATUS_LABEL: Record<string, string> = {
  DRAFT: '작성중',
  SUBMITTED: '상신됨',
  IN_REVIEW: '검토중',
  APPROVED: '승인됨',
  REJECTED: '반려됨',
  WITHDRAWN: '회수됨',
};

export function statusLabel(status: string): string {
  return STATUS_LABEL[status] ?? status;
}

export function StatusBadge({ status }: { status: string }) {
  const terminal = isTerminalApprovalStatus(status);
  const tone =
    status === 'APPROVED'
      ? 'bg-green-100 text-green-800 dark:bg-green-950/60 dark:text-green-100'
      : status === 'REJECTED' || status === 'WITHDRAWN'
        ? 'bg-red-100 text-red-800 dark:bg-red-950/60 dark:text-red-100'
        : status === 'SUBMITTED'
          ? 'bg-amber-100 text-amber-800 dark:bg-amber-950/60 dark:text-amber-100'
          : status === 'IN_REVIEW'
            ? 'bg-blue-100 text-blue-800 dark:bg-blue-950/60 dark:text-blue-100'
            : 'bg-muted text-muted-foreground';
  return (
    <span
      data-testid="approval-status-badge"
      data-status={status}
      data-terminal={terminal ? 'true' : 'false'}
      className={`inline-block rounded px-1.5 py-0.5 text-xs ${tone}`}
    >
      {statusLabel(status)}
    </span>
  );
}
