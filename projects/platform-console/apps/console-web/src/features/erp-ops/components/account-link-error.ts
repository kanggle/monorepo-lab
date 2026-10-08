import { ApiError, ErpUnavailableError, errorDetail } from '@/shared/api/errors';
import { isSampleErrorCode } from '@/shared/sample/codes';

/**
 * Employee ↔ IAM account link error → inline Korean copy (TASK-PC-FE-318;
 * producer `masterdata-api.md` § Employee ↔ IAM account link). Every refusal
 * the producer distinguishes gets its own sentence — collapsing them would
 * hide the reason (e.g. «two-person rule» vs «not addressed to you»).
 *
 * `op` refines the two codes whose meaning depends on who acted:
 *   - `EMPLOYEE_LINK_SELF_ACCEPT` on **propose** = «you proposed your own
 *     account» (rejected early — it could never be accepted); on **accept**
 *     = «the proposer cannot accept» (the authoritative two-person check).
 *
 * 🔴 A `503` / unavailable is an outage, never a statement about the user's
 * data or link.
 */
export type AccountLinkOp =
  | 'propose'
  | 'accept'
  | 'decline'
  | 'revoke'
  | 'unlink'
  | 'read';

export const ACCOUNT_LINK_UNAVAILABLE_MESSAGE =
  '직원 마스터 서비스를 일시적으로 사용할 수 없습니다. 잠시 후 다시 시도하세요.';

function conflictMessage(err: unknown): string {
  switch (errorDetail(err, 'cause')) {
    case 'employee_already_linked':
      return '이 직원은 이미 다른 계정과 연결되어 있습니다. 먼저 연결을 해제하세요.';
    case 'account_already_linked':
      return '이 계정은 이미 이 회사의 다른 직원과 연결되어 있습니다.';
    case 'proposal_pending':
      return '이 직원에게 대기 중인 연결 제안이 이미 있습니다. 그 제안을 철회한 뒤 다시 제안하세요.';
    case 'proposal_not_pending':
      return '이 제안은 이미 처리(수락·거절·철회)되었습니다. 새로고침하세요.';
    case 'not_linked':
      return '이 직원은 연결된 계정이 없습니다. 새로고침하세요.';
    default:
      return '연결 상태가 그사이 바뀌어 처리할 수 없습니다. 새로고침 후 다시 시도하세요.';
  }
}

/**
 * TASK-MONO-777 «이메일로 찾기» copy.
 *
 * 🔴 ONE sentence for «no such operator» AND «tenant out of scope» — the IAM
 * producer already answers both with the same empty list, and the dialog must
 * not re-introduce the distinction (existence non-disclosure, AC-2). The
 * sentence also says what to do next: the typed-id fallback.
 */
export const ACCOUNT_LOOKUP_NOT_FOUND_MESSAGE =
  '이 이메일로 이 테넌트에서 연결할 수 있는 계정을 찾지 못했습니다. 이메일을 확인하거나 계정 ID 를 직접 입력하세요.';

/** Lookup failed for a reason that is NOT about the e-mail (403/401/503/…). */
export const ACCOUNT_LOOKUP_FAILED_MESSAGE =
  '지금은 이메일로 계정을 찾을 수 없습니다. 계정 ID 를 직접 입력할 수 있습니다.';

export function accountLookupErrorMessage(err: unknown): string {
  // Defence in depth: the producer answers out-of-scope with an empty list, but
  // should a `403 TENANT_SCOPE_DENIED` ever reach here it must read EXACTLY like
  // «not found» — a different sentence would tell the user the tenant exists.
  if (err instanceof ApiError && err.code === 'TENANT_SCOPE_DENIED') {
    return ACCOUNT_LOOKUP_NOT_FOUND_MESSAGE;
  }
  if (err instanceof ApiError && err.status === 400 && err.code === 'NO_ACTIVE_TENANT') {
    return '테넌트를 먼저 선택하세요.';
  }
  return ACCOUNT_LOOKUP_FAILED_MESSAGE;
}

export function accountLinkErrorMessage(err: unknown, op: AccountLinkOp): string {
  if (err instanceof ErpUnavailableError) return ACCOUNT_LINK_UNAVAILABLE_MESSAGE;
  if (err instanceof ApiError) {
    if (err.status === 503 && !isSampleErrorCode(err.code)) {
      return ACCOUNT_LINK_UNAVAILABLE_MESSAGE;
    }
    switch (err.code) {
      case 'EMPLOYEE_LINK_SELF_ACCEPT':
        return op === 'propose'
          ? '자기 계정은 제안할 수 없습니다 — 제안한 사람은 수락할 수 없으므로(두 사람 규칙) 다른 인사 담당자가 제안해야 합니다.'
          : '제안한 사람이 수락할 수 없습니다 (두 사람 규칙). 다른 담당자가 다시 제안해야 합니다.';
      case 'EMPLOYEE_LINK_NOT_ADDRESSEE':
        return '내 계정 앞으로 온 제안이 아닙니다. 제안받은 계정으로 로그인한 사람만 수락·거절할 수 있습니다.';
      case 'EMPLOYEE_LINK_CONFLICT':
        return conflictMessage(err);
      case 'EMPLOYEE_LINK_INVALID':
        return '재직(ACTIVE) 중인 직원만 계정과 연결할 수 있습니다.';
      case 'EMPLOYEE_LINK_PROPOSAL_NOT_FOUND':
        return '연결 제안을 찾을 수 없습니다. 새로고침하세요.';
      case 'MASTERDATA_NOT_FOUND':
        return '직원을 찾을 수 없습니다. 목록을 새로고침하세요.';
      case 'IDEMPOTENCY_KEY_REQUIRED':
      case 'IDEMPOTENCY_KEY_CONFLICT':
        return '중복/충돌이 감지되었습니다. 새로고침 후 다시 시도하세요.';
      case 'VALIDATION_ERROR':
        return '입력값이 올바르지 않습니다 (계정 ID 는 1~64자, 철회·해제는 사유가 필요합니다).';
      case 'PERMISSION_DENIED':
      case 'DATA_SCOPE_FORBIDDEN':
      case 'TENANT_FORBIDDEN':
        return op === 'read'
          ? '연결 정보를 볼 권한이 없습니다.'
          : '이 작업을 수행할 권한이 없습니다.';
      default:
        return err.message || '요청을 처리하지 못했습니다.';
    }
  }
  return '요청을 처리하지 못했습니다.';
}
