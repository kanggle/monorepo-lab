export class ApiError extends Error {
  readonly status: number;
  readonly code: string;
  readonly timestamp?: string;
  readonly extra: Record<string, unknown>;
  constructor(status: number, code: string, message: string, timestamp?: string, extra: Record<string, unknown> = {}) {
    super(message);
    this.status = status;
    this.code = code;
    this.timestamp = timestamp;
    this.extra = extra;
  }
}

const MESSAGES_KO: Record<string, string> = {
  CREDENTIALS_INVALID: '이메일 또는 비밀번호가 올바르지 않습니다.',
  ACCOUNT_LOCKED: '잠긴 계정입니다. 관리자에게 문의하세요.',
  ACCOUNT_DORMANT: '휴면 상태 계정입니다.',
  ACCOUNT_DELETED: '삭제된 계정입니다.',
  LOGIN_RATE_LIMITED: '로그인 시도가 너무 많습니다. 잠시 후 다시 시도해주세요.',
  VALIDATION_ERROR: '입력 값을 확인해주세요.',
  TOKEN_INVALID: '세션이 만료되었습니다. 다시 로그인해주세요.',
  PERMISSION_DENIED: '이 작업을 수행할 권한이 없습니다.',
  STATE_TRANSITION_INVALID: '현재 상태에서 허용되지 않은 전환입니다.',
  REASON_REQUIRED: '감사 사유(Operator Reason)가 필요합니다.',
  ACCOUNT_NOT_FOUND: '계정을 찾을 수 없습니다.',
  ENROLLMENT_REQUIRED: '2FA 등록이 필요합니다.',
  DOWNSTREAM_ERROR: '하위 서비스 호출에 실패했습니다.',
  INVALID_STATE: '세션이 만료되었습니다. 다시 시도해주세요.',
  INVALID_CODE: '소셜 로그인 인가 코드가 유효하지 않습니다.',
  EMAIL_REQUIRED: '소셜 계정에서 이메일 정보를 가져올 수 없습니다.',
  PROVIDER_ERROR: '소셜 로그인 서비스에 일시적인 문제가 발생했습니다.',
  UNSUPPORTED_PROVIDER: '지원하지 않는 소셜 로그인 제공자입니다.',
};

export function messageForCode(code: string, fallback?: string): string {
  return MESSAGES_KO[code] ?? fallback ?? '알 수 없는 오류가 발생했습니다.';
}
