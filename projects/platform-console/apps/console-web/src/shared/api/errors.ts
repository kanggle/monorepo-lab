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

/**
 * GAP accounts surface degrade signal (console-integration-contract § 2.4.1 /
 * § 2.5). Mirrors {@link RegistryUnavailableError}: a `503 DOWNSTREAM_ERROR` /
 * `503 CIRCUIT_OPEN` / timeout on a GAP accounts call must degrade ONLY the
 * accounts section — the console shell stays intact. Auth failures
 * (401/403) are raised as {@link ApiError} so the caller forces a clean
 * re-login (no partial authed state). Inline-recoverable producer errors
 * (`400 STATE_TRANSITION_INVALID`/`REASON_REQUIRED`, `404 ACCOUNT_NOT_FOUND`,
 * `409 IDEMPOTENCY_KEY_CONFLICT`, `422 BATCH_SIZE_EXCEEDED`) are raised as
 * {@link ApiError} too so the UI can render an inline actionable message
 * without crashing. No token / PII is ever placed in this error.
 */
export class AccountsUnavailableError extends Error {
  readonly reason: 'timeout' | 'circuit_open' | 'downstream';
  readonly code: string;
  constructor(
    reason: AccountsUnavailableError['reason'],
    code: string,
    message: string,
  ) {
    super(message);
    this.name = 'AccountsUnavailableError';
    this.reason = reason;
    this.code = code;
  }
}

/**
 * GAP audit + security read surface degrade signal
 * (console-integration-contract § 2.4.2 / § 2.5). Sibling of
 * {@link AccountsUnavailableError} — identical resilience posture for the
 * READ-ONLY audit slice: a `503 DOWNSTREAM_ERROR` / `503 CIRCUIT_OPEN` /
 * timeout on a GAP audit call degrades ONLY the audit section (the console
 * shell stays intact). Auth failures (401) are raised as {@link ApiError}
 * so the caller forces a clean re-login (no partial authed state).
 * Inline-recoverable producer errors (`403 PERMISSION_DENIED` — incl. the
 * intersection-permission rule, `403 TENANT_SCOPE_DENIED`,
 * `422 VALIDATION_ERROR`) are raised as {@link ApiError} so the UI renders
 * an inline actionable message without crashing. No token / audit-row PII
 * is ever placed in this error.
 */
export class AuditUnavailableError extends Error {
  readonly reason: 'timeout' | 'circuit_open' | 'downstream';
  readonly code: string;
  constructor(
    reason: AuditUnavailableError['reason'],
    code: string,
    message: string,
  ) {
    super(message);
    this.name = 'AuditUnavailableError';
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
  PERMISSION_DENIED: '이 작업을 수행할 권한이 없습니다.',
  STATE_TRANSITION_INVALID:
    '현재 계정 상태에서는 이 작업을 수행할 수 없습니다.',
  REASON_REQUIRED: '감사 사유를 입력해야 합니다.',
  ACCOUNT_NOT_FOUND: '대상 계정을 찾을 수 없습니다.',
  BATCH_SIZE_EXCEEDED: '한 번에 처리할 수 있는 계정 수를 초과했습니다 (최대 100).',
  IDEMPOTENCY_KEY_CONFLICT:
    '이전 요청과 다른 내용으로 같은 작업이 재시도되었습니다.',
  VALIDATION_ERROR: '입력값이 올바르지 않습니다.',
  NO_ACTIVE_TENANT: '테넌트를 먼저 선택해주세요.',
  TENANT_SCOPE_DENIED: '해당 테넌트에 대한 조회 권한이 없습니다.',
  AUDIT_RANGE_INVALID: '시작 시각이 종료 시각보다 늦을 수 없습니다.',
  SECURITY_EVENT_READ_REQUIRED:
    '로그인 이력·의심 활동을 조회하려면 보안 이벤트 조회 권한(security.event.read)이 필요합니다.',
};

export function messageForCode(code: string, fallback?: string): string {
  return MESSAGES[code] ?? fallback ?? '알 수 없는 오류가 발생했습니다.';
}
