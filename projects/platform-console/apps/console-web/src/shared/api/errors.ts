/**
 * API error envelope. GAP/admin-service uses `{ code, message, timestamp }`
 * (console-registry-api.md § Errors → admin-api.md Common Error Format).
 */
export class ApiError extends Error {
  readonly status: number;
  readonly code: string;
  readonly timestamp?: string;

  constructor(status: number, code: string, message: string, timestamp?: string) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.code = code;
    this.timestamp = timestamp;
  }
}

/** Thrown when the registry call times out or the breaker is open. */
export class RegistryUnavailableError extends Error {
  readonly reason: 'timeout' | 'circuit_open' | 'downstream' | 'unauthorized';
  constructor(reason: RegistryUnavailableError['reason'], message: string) {
    super(message);
    this.name = 'RegistryUnavailableError';
    this.reason = reason;
  }
}

const MESSAGES: Record<string, string> = {
  TOKEN_INVALID: '세션이 만료되었습니다. 다시 로그인해주세요.',
  TOKEN_REVOKED: '세션이 종료되었습니다. 다시 로그인해주세요.',
  DOWNSTREAM_ERROR: '하위 서비스 호출에 실패했습니다.',
  CIRCUIT_OPEN: '서비스가 일시적으로 응답할 수 없습니다.',
  TENANT_FORBIDDEN: '선택한 테넌트에 대한 권한이 없습니다.',
};

export function messageForCode(code: string, fallback?: string): string {
  return MESSAGES[code] ?? fallback ?? '알 수 없는 오류가 발생했습니다.';
}
