import { ApiError, messageForCode } from '@/shared/api/errors';
import type { OperatorInvitation } from '../api/invitation-types';

/**
 * User-facing copy for the operator-invitation surface (TASK-MONO-772 S5 —
 * console-integration-contract § 2.4.3 «등록 → 초대»). Pure functions — no
 * state. Kept feature-local (single consumer): two producer codes the
 * invitation surface shares with other screens (`ROLE_GRANT_FORBIDDEN`,
 * `TENANT_SCOPE_DENIED`) carry copy in the shared map that is written for
 * those OTHER screens (operator groups / list reads) and would mislead here.
 */

/** Codes whose answer means «the row moved under you — refresh the list». */
export const INVITATION_STALE_CODES = new Set([
  'OPERATOR_INVITATION_NOT_PENDING',
  'OPERATOR_INVITATION_NOT_FOUND',
]);

const INVITATION_MESSAGES: Record<string, string> = {
  OPERATOR_INVITATION_ALREADY_PENDING:
    '이미 대기 중인 초대가 있습니다. 대기 중인 초대 목록에서 «다시 보내기»를 사용하세요.',
  OPERATOR_EMAIL_CONFLICT: '이미 이 테넌트의 운영자입니다.',
  OPERATOR_INVITATION_NOT_PENDING:
    '이 초대는 그 사이 수락되었거나 취소되었습니다. 목록을 새로 불러왔습니다.',
  OPERATOR_INVITATION_NOT_FOUND:
    '이 초대를 찾을 수 없습니다(이미 취소되었거나 권한 범위 밖). 목록을 새로 불러왔습니다.',
  ROLE_GRANT_FORBIDDEN:
    '본인이 부여할 수 없는 역할이 포함되어 있습니다(역할 무상승 규칙). 역할을 줄인 뒤 다시 시도하세요.',
  TENANT_SCOPE_DENIED: '이 테넌트에 운영자를 초대할 권한이 없습니다.',
  TENANT_NOT_FOUND: '초대할 테넌트를 찾을 수 없습니다. 테넌트를 다시 선택하세요.',
};

const UNAVAILABLE_MESSAGE =
  '초대 서비스가 일시적으로 응답하지 않습니다. 잠시 후 목록을 확인한 뒤 다시 시도하세요.';

/** Inline message for a failed invite / cancel / resend (never a crash). */
export function invitationErrorMessage(err: unknown): string | null {
  if (!err) return null;
  if (err instanceof ApiError) {
    const mapped = INVITATION_MESSAGES[err.code];
    if (mapped) return mapped;
    // 503 / timeout → only this section's action failed (shell intact).
    if (err.status >= 500) return UNAVAILABLE_MESSAGE;
    return messageForCode(err.code, err.message);
  }
  return '작업을 완료하지 못했습니다. 잠시 후 다시 시도하세요.';
}

export function errorCode(err: unknown): string | null {
  return err instanceof ApiError ? err.code : null;
}

/** `delivery.status` → label (contract § 2.4.3 wording). */
export function deliveryLabel(inv: Pick<OperatorInvitation, 'delivery'>): string {
  switch (inv.delivery?.status) {
    case 'SENT':
      return '보냄';
    case 'FAILED_TRANSIENT':
      return '보내지 못함 — 다시 보내기';
    case 'FAILED_PERMANENT':
      return '이 주소로는 보낼 수 없음';
    case undefined:
      return '발송 대기';
    default:
      // Forward-compatible: an unknown future value is shown verbatim-neutral.
      return '발송 상태 알 수 없음';
  }
}

/**
 * Notice copy after an invite / resend response — a FAILED_* delivery is a
 * failure of DELIVERY, not of the invitation (OD-4 «발송 실패는 화면에»):
 * the invitation exists and is listed.
 */
export function deliveryNoticeCopy(
  inv: Pick<OperatorInvitation, 'delivery' | 'email'>,
  via: 'invite' | 'resend',
): string {
  const verb = via === 'invite' ? '초대를 만들었습니다' : '새 링크를 만들었습니다';
  switch (inv.delivery?.status) {
    case 'SENT':
      return `${inv.email} 에게 초대 메일을 보냈습니다. 수락하면 운영자 목록에 나타납니다.`;
    case 'FAILED_TRANSIENT':
      return `${verb}. 다만 메일을 보내지 못했습니다(일시적인 문제). «다시 보내기»로 다시 시도하세요.`;
    case 'FAILED_PERMANENT':
      return `${verb}. 다만 이 주소로는 메일을 보낼 수 없습니다. 주소를 확인하세요 — 다시 보내기는 할 수 있지만 성공을 약속하지는 않습니다.`;
    default:
      return `${verb}. 메일 발송 결과는 아직 확인되지 않았습니다 — 대기 중인 초대 목록에서 확인하세요.`;
  }
}
