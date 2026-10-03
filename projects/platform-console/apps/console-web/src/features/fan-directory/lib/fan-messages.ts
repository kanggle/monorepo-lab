import { ApiError } from '@/shared/api/errors';
import type { StatusTone } from '@/shared/ui/StatusBadge';

/**
 * Operator-facing copy for the fan artist-service error codes (artist-api § Common error
 * codes). Kept LOCAL to the fan feature on purpose: the shared `messageForCode` map already
 * owns `GROUP_NAME_CONFLICT` with an IAM operator-GROUP meaning, so reusing it would show
 * the wrong sentence for an artist-group name clash.
 */
const FAN_MESSAGES: Record<string, string> = {
  AGENCY_NAME_CONFLICT:
    '같은 이름의 소속사가 이미 있습니다(공백만 다른 이름도 같은 이름으로 봅니다). 다른 이름을 쓰세요.',
  AGENCY_NOT_FOUND: '소속사를 찾을 수 없습니다. 목록을 새로고침하세요.',
  AGENCY_ARCHIVED:
    '보관된 소속사입니다 — 새 소속 · 이름 변경 · 셀러 연결을 할 수 없습니다.',
  STORE_SELLER_NOT_FOUND:
    '스토어에 그 ID 의 셀러가 없습니다. 이커머스 셀러 화면에서 셀러 ID 를 확인하세요. (아무것도 저장되지 않았습니다)',
  STORE_SELLER_CLOSED: '폐점(CLOSED)된 셀러는 연결할 수 없습니다. (아무것도 저장되지 않았습니다)',
  STORE_SELLER_LOOKUP_UNAVAILABLE:
    '스토어 셀러를 확인할 수 없어 연결하지 않았습니다 — 아무것도 저장되지 않았습니다. 팬 서비스가 스토어에 묻는 경로가 아직 배선되지 않았습니다(TASK-MONO-759). 연결 해제는 지금도 됩니다.',
  STAGE_NAME_CONFLICT: '같은 활동명의 아티스트가 이미 있습니다.',
  ARTIST_ACCOUNT_CONFLICT: '그 계정은 이미 다른 아티스트로 등록되어 있습니다.',
  GROUP_NAME_CONFLICT: '같은 이름의 아티스트 그룹이 이미 있습니다.',
  ARTIST_NOT_FOUND: '아티스트를 찾을 수 없습니다.',
  ARTIST_GROUP_NOT_FOUND: '아티스트 그룹을 찾을 수 없습니다.',
  STATE_TRANSITION_INVALID: '지금 상태에서는 그 상태로 바꿀 수 없습니다.',
  ILLEGAL_STATE: '보관된 항목은 소속을 바꿀 수 없습니다.',
  VALIDATION_ERROR: '입력값을 확인하세요.',
  TENANT_FORBIDDEN:
    '이 화면은 fan-platform 테넌트로 전환한 플랫폼 운영자만 쓸 수 있습니다. 상단 스위처에서 fan-platform 을 선택하세요.',
  FORBIDDEN: '이 작업을 할 권한이 없습니다.',
};

export function fanMessageForCode(code: string, fallback: string): string {
  return FAN_MESSAGES[code] ?? fallback;
}

/** The producer code of a failed mutation (`SERVICE_UNAVAILABLE` for a non-ApiError). */
export function errorCode(err: unknown): string {
  return err instanceof ApiError ? err.code : 'SERVICE_UNAVAILABLE';
}

export function fanErrorMessage(err: unknown, fallback: string): string {
  return fanMessageForCode(errorCode(err), fallback);
}

export function agencyStatusTone(status: string): StatusTone {
  return status === 'ACTIVE' ? 'success' : status === 'ARCHIVED' ? 'neutral' : 'warning';
}

export function artistStatusTone(status: string): StatusTone {
  switch (status) {
    case 'PUBLISHED':
      return 'success';
    case 'DRAFT':
      return 'progress';
    case 'ARCHIVED':
      return 'neutral';
    default:
      return 'warning';
  }
}
