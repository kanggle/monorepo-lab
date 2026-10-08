import { ApiError, ErpUnavailableError, errorDetail } from '@/shared/api/errors';
import { isSampleErrorCode } from '@/shared/sample/codes';
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
 *
 * TASK-PC-FE-318 (approval v2.4 — `TASK-MONO-776`): the person fields are
 * employee ids, resolved from the caller's linked employee. Each new refusal
 * gets its OWN copy — `APPROVAL_ACTOR_NOT_LINKED` (my account is linked to no
 * employee), `APPROVAL_APPROVER_UNLINKED` (pointing at `details.stageIndex`),
 * `APPROVAL_ROUTE_INVALID` by `details.cause`. 🔴 A `503` («could not ask
 * masterdata who you are») is an OUTAGE and is worded as one — it must never
 * read as «your account is not linked» or any other defect of the user's
 * data. Client-side the 503 arrives as a plain `ApiError(503)` (the
 * same-origin proxy's body), not an `ErpUnavailableError`, so both are
 * checked.
 */
export const APPROVAL_UNAVAILABLE_MESSAGE =
  '결재 서비스를 일시적으로 사용할 수 없습니다. 잠시 후 다시 시도하세요.';

function routeInvalidMessage(err: unknown): string {
  switch (errorDetail(err, 'cause')) {
    case 'self_approval':
      return '자기 자신(내 계정과 연결된 직원)을 결재자로 지정할 수 없습니다. 다른 결재자를 고르세요.';
    case 'approver_unresolved':
      return '결재선에 없는 직원이나 재직(ACTIVE) 중이 아닌 직원이 있습니다. 결재자를 다시 고르세요.';
    case 'subject_unresolved':
      return '결재 대상(부서/직원)을 찾을 수 없거나 폐기되었습니다. 대상을 확인하세요.';
    case 'duplicate_stage_approver':
      return '같은 결재자가 두 단계 이상에 들어 있습니다. 결재선을 확인하세요.';
    default:
      return '결재선이 올바르지 않습니다 (자기결재 또는 대상 master 미해소). 결재자/대상을 확인하세요.';
  }
}

function approverUnlinkedMessage(err: unknown): string {
  const stage = errorDetail(err, 'stageIndex');
  const where =
    typeof stage === 'number' ? `${stage + 1}단계 결재자` : '결재선의 결재자 중 한 명';
  return `${where}에게 연결된 계정이 없어 상신할 수 없습니다 — 그 결재자는 결재함에서 이 건을 볼 수 없습니다. 결재선을 바꾸거나, 그 직원의 계정 연결을 먼저 요청하세요.`;
}

export function approvalErrorMessage(
  err: unknown,
  transition?: ApprovalTransition,
): string {
  if (err instanceof ErpUnavailableError) {
    return APPROVAL_UNAVAILABLE_MESSAGE;
  }
  if (err instanceof ApiError) {
    // A sample visitor's 503 keeps its own copy (already rewritten by the
    // client from the code — ADR-MONO-074).
    if (err.status === 503 && !isSampleErrorCode(err.code)) {
      return APPROVAL_UNAVAILABLE_MESSAGE;
    }
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
        return routeInvalidMessage(err);
      case 'APPROVAL_APPROVER_UNLINKED':
        return approverUnlinkedMessage(err);
      case 'APPROVAL_ACTOR_NOT_LINKED':
        return '내 계정이 직원과 연결되어 있지 않아 결재를 작성·처리할 수 없습니다. 인사 담당자에게 직원 연결을 요청하세요 (연결 제안이 오면 ERP 개요에서 수락합니다).';
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
      // TASK-PC-FE-054 — delegation-specific codes (§v2.1 AMENDMENT).
      case 'DELEGATION_INVALID':
        return errorDetail(err, 'cause') === 'delegate_unresolved'
          ? '위임받을 직원이 없거나 재직(ACTIVE) 중이 아닙니다.'
          : '자기 위임이거나 유효기간이 올바르지 않습니다 (validTo < validFrom).';
      case 'DELEGATION_NOT_FOUND':
        return '위임을 찾을 수 없습니다. 목록을 새로고침하세요.';
      default:
        return err.message || '요청을 처리하지 못했습니다.';
    }
  }
  return '요청을 처리하지 못했습니다.';
}
