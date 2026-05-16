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

/**
 * Operator-token exchange (RFC 8693) failure taxonomy
 * (console-integration-contract § 2.6 fail-closed mapping / ADR-MONO-014):
 *
 *   - `fail_closed` — GAP returned `401 TOKEN_INVALID`: the subject token is
 *     invalid OR the OIDC subject is not mapped to an active operator
 *     (not provisioned). The caller MUST force re-login (`not_provisioned`);
 *     NO operator cookie is set / an existing one is dropped. The GAP token
 *     is never accepted as an `/api/admin/**` credential.
 *   - `unavailable` — `400`/`5xx`/timeout/network/unexpected `tokenType`:
 *     session-unavailable. NO operator cookie; the console never falls back
 *     to the GAP OIDC token on the operator boundary (the #569 defect).
 *
 * No `subject_token`/operator token value is ever placed in this error or
 * logged (security invariant).
 */
export class OperatorExchangeError extends Error {
  readonly reason: 'fail_closed' | 'unavailable';
  readonly code: string;
  constructor(
    reason: OperatorExchangeError['reason'],
    code: string,
    message: string,
  ) {
    super(message);
    this.name = 'OperatorExchangeError';
    this.reason = reason;
    this.code = code;
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
