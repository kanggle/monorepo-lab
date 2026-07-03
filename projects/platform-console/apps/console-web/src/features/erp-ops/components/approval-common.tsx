import {
  approvalStatusTone,
  isTerminalApprovalStatus,
} from '../api/approval-types';
import { statusToneClass } from '@/shared/ui/StatusBadge';

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
  // Palette centralised in shared/ui/StatusBadge (statusToneClass); this badge
  // keeps its own <span> only for the data-status / data-terminal attributes.
  return (
    <span
      data-testid="approval-status-badge"
      data-status={status}
      data-terminal={terminal ? 'true' : 'false'}
      className={statusToneClass(approvalStatusTone(status))}
    >
      {statusLabel(status)}
    </span>
  );
}
