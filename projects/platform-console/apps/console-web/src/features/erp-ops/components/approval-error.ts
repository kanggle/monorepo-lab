import { ApiError, ErpUnavailableError } from '@/shared/api/errors';
import type { ApprovalTransition } from '../api/approval-types';

/**
 * Maps an approval producer error to an inline-actionable Korean message
 * (TASK-PC-FE-051 AC-4 — graceful, NO crash). The console operator may not
 * be the request's authorized approver / submitter, so a 403
 * `APPROVAL_NOT_AUTHORIZED_APPROVER` is a NORMAL, expected outcome surfaced
 * as a clear inline notice — never an error boundary.
 *
 * `transition` (optional) refines the not-authorized message: a `withdraw`
 * is submitter-only, an `approve`/`reject` is approver-only — the producer
 * reuses the same code for both with `details.role` discriminating, but the
 * console gives the operator the right hint from the action they took.
 */
export function approvalErrorMessage(
  err: unknown,
  transition?: ApprovalTransition,
): string {
  if (err instanceof ErpUnavailableError) {
    return '결재 서비스를 일시적으로 사용할 수 없습니다. 잠시 후 다시 시도하세요.';
  }
  if (err instanceof ApiError) {
    switch (err.code) {
      case 'APPROVAL_NOT_AUTHORIZED_APPROVER':
        return transition === 'withdraw'
          ? '회수 권한 없음 (기안자 본인만 회수할 수 있습니다).'
          : '결재 권한 없음 (지정된 결재자만 승인/반려할 수 있습니다).';
      case 'APPROVAL_STATUS_TRANSITION_INVALID':
        return '현재 상태에서는 이 작업을 수행할 수 없습니다. 목록을 새로고침하세요.';
      case 'APPROVAL_ALREADY_FINALIZED':
        return '이미 완료/반려/회수된 결재입니다. 새 요청으로만 재처리할 수 있습니다.';
      case 'APPROVAL_ROUTE_INVALID':
        return '결재선이 올바르지 않습니다 (자기결재 또는 대상 master 미해소). 결재자/대상을 확인하세요.';
      case 'APPROVAL_REQUEST_NOT_FOUND':
        return '대상 결재 요청을 찾을 수 없습니다. 목록을 새로고침하세요.';
      case 'IDEMPOTENCY_KEY_REQUIRED':
      case 'IDEMPOTENCY_KEY_CONFLICT':
        return '중복/충돌이 감지되었습니다. 새로고침 후 다시 시도하세요.';
      case 'VALIDATION_ERROR':
        return '입력값이 올바르지 않습니다 (반려/회수는 사유가 필요합니다).';
      case 'PERMISSION_DENIED':
      case 'DATA_SCOPE_FORBIDDEN':
      case 'TENANT_FORBIDDEN':
        return '이 작업을 수행할 권한이 없습니다.';
      case 'EXTERNAL_TRAFFIC_REJECTED':
        return 'erp 는 내부 전용 경계입니다. 콘솔 SSO 세션으로만 조회할 수 있습니다.';
      default:
        return err.message || '요청을 처리하지 못했습니다.';
    }
  }
  return '요청을 처리하지 못했습니다.';
}
