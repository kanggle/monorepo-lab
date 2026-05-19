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

/**
 * GAP operators-management surface degrade signal
 * (console-integration-contract § 2.4.3 / § 2.5). Sibling of
 * {@link AccountsUnavailableError} / {@link AuditUnavailableError} —
 * identical resilience posture for the most privilege-sensitive Phase-2
 * slice: a `503 DOWNSTREAM_ERROR` / `503 CIRCUIT_OPEN` / timeout on a GAP
 * operators call degrades ONLY the operators section (the console shell
 * stays intact). Auth failures (401) are raised as {@link ApiError} so the
 * caller forces a clean re-login (no partial authed state). Inline-
 * recoverable producer errors (`403 PERMISSION_DENIED` — not SUPER_ADMIN /
 * lacks `operator.manage`, `403 TENANT_SCOPE_DENIED`,
 * `409 OPERATOR_EMAIL_CONFLICT`, `400 ROLE_NOT_FOUND`/`VALIDATION_ERROR`/
 * `STATE_TRANSITION_INVALID`/`SELF_SUSPEND_FORBIDDEN`/
 * `CURRENT_PASSWORD_MISMATCH`/`PASSWORD_POLICY_VIOLATION`,
 * `404 OPERATOR_NOT_FOUND`) are raised as {@link ApiError} so the UI
 * renders an inline actionable message without crashing. No token /
 * operator email / password is ever placed in this error.
 */
export class OperatorsUnavailableError extends Error {
  readonly reason: 'timeout' | 'circuit_open' | 'downstream';
  readonly code: string;
  constructor(
    reason: OperatorsUnavailableError['reason'],
    code: string,
    message: string,
  ) {
    super(message);
    this.name = 'OperatorsUnavailableError';
    this.reason = reason;
    this.code = code;
  }
}

/**
 * wms `admin-service` operations surface degrade signal
 * (console-integration-contract § 2.4.5 / § 2.5). Sibling of
 * {@link AccountsUnavailableError} / {@link AuditUnavailableError} /
 * {@link OperatorsUnavailableError} — identical resilience posture for the
 * first **non-GAP** federated domain section: a `503 SERVICE_UNAVAILABLE` /
 * timeout / network failure on a wms call degrades ONLY the wms section (the
 * console shell + the GAP sections stay intact). Auth failures (`401` —
 * the GAP OIDC session expired) are raised as {@link ApiError} so the caller
 * forces a clean WHOLE-SESSION re-login (no partial authed state — this is
 * NOT a per-section degrade). Inline-recoverable producer errors
 * (`403 FORBIDDEN` role-insufficient, `404` not-found,
 * `400 VALIDATION_ERROR`, `422 STATE_TRANSITION_INVALID` alert already
 * acknowledged, `409 DUPLICATE_REQUEST`) are raised as {@link ApiError} so
 * the UI renders an inline actionable message without crashing.
 *
 * NOTE the credential divergence: unlike the GAP siblings, the wms
 * credential is the GAP OIDC access token itself (the wms gateway requires
 * it; the #569 invariant is GAP-domain-scoped — § 2.4.5). No token / PII is
 * ever placed in this error.
 */
export class WmsUnavailableError extends Error {
  readonly reason: 'timeout' | 'circuit_open' | 'downstream';
  readonly code: string;
  constructor(
    reason: WmsUnavailableError['reason'],
    code: string,
    message: string,
  ) {
    super(message);
    this.name = 'WmsUnavailableError';
    this.reason = reason;
    this.code = code;
  }
}

/**
 * scm gateway operations surface degrade signal
 * (console-integration-contract § 2.4.6 / § 2.5). Sibling of
 * {@link WmsUnavailableError} — the SECOND **non-GAP** federated domain
 * section (read-only). A `503 SERVICE_UNAVAILABLE` / `503 NODE_UNREACHABLE`
 * / timeout / network failure on an scm call degrades ONLY the scm section
 * (the console shell + the GAP/wms sections stay intact). Auth failures
 * (`401` — the GAP OIDC session expired) are raised as {@link ApiError} so
 * the caller forces a clean WHOLE-SESSION re-login (no partial authed
 * state — NOT a per-section degrade). Inline-recoverable producer errors
 * (`403 TENANT_FORBIDDEN`/`FORBIDDEN`/`PERMISSION_DENIED`,
 * `404 PO_NOT_FOUND`/`NODE_NOT_FOUND`, `400|422 VALIDATION_ERROR`) are
 * raised as {@link ApiError} so the UI renders an inline actionable
 * message without crashing.
 *
 * `429 RATE_LIMIT_EXCEEDED` carries a `Retry-After` and is modeled by
 * {@link ScmRateLimitedError} (a bounded backoff, NOT a degrade) — the
 * console must not auto-retry-storm the rate-limited gateway.
 *
 * NOTE the credential reuse: like the wms sibling (NOT the GAP ones), the
 * scm credential is the GAP OIDC access token itself — the § 2.4.5
 * per-domain credential rule reused verbatim (the #569 invariant is
 * GAP-domain-scoped — § 2.4.6). The scm error envelope is **flat**
 * `{ code, message, timestamp }` (DISTINCT from wms's nested
 * `{ error: { code } }`). No token / PII is ever placed in this error.
 */
export class ScmUnavailableError extends Error {
  readonly reason: 'timeout' | 'circuit_open' | 'downstream';
  readonly code: string;
  constructor(
    reason: ScmUnavailableError['reason'],
    code: string,
    message: string,
  ) {
    super(message);
    this.name = 'ScmUnavailableError';
    this.reason = reason;
    this.code = code;
  }
}

/**
 * scm `429 RATE_LIMIT_EXCEEDED` signal (console-integration-contract
 * § 2.4.6 / task Edge Case "429 Retry-After"). The scm gateway returns
 * `Retry-After: 1`; the console honours it with ONE bounded backoff +
 * an inline "rate-limited, retrying" notice — it MUST NOT auto-retry-storm
 * into the gateway. `retryAfterSeconds` is the parsed `Retry-After`
 * (defaulted to a small bound when absent/invalid). No token / PII here.
 */
export class ScmRateLimitedError extends Error {
  readonly code = 'RATE_LIMIT_EXCEEDED';
  readonly retryAfterSeconds: number;
  constructor(retryAfterSeconds: number, message: string) {
    super(message);
    this.name = 'ScmRateLimitedError';
    this.retryAfterSeconds = retryAfterSeconds;
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
  // --- operators (TASK-PC-FE-004 / §2.4.3) -------------------------------
  OPERATOR_MANAGE_REQUIRED:
    '운영자 관리는 SUPER_ADMIN(operator.manage 권한)만 수행할 수 있습니다.',
  TENANT_SCOPE_DENIED_CREATE:
    "플랫폼 스코프(*) 운영자는 플랫폼 스코프 운영자만 생성할 수 있습니다. 이 작업을 수행할 권한이 없습니다.",
  OPERATOR_EMAIL_CONFLICT:
    '같은 테넌트에 동일한 이메일의 운영자가 이미 존재합니다.',
  ROLE_NOT_FOUND:
    '존재하지 않는 역할이 포함되어 있습니다. 역할 목록을 새로고침한 뒤 다시 시도하세요.',
  OPERATOR_NOT_FOUND: '대상 운영자를 찾을 수 없습니다.',
  SELF_SUSPEND_FORBIDDEN: '본인 계정은 정지할 수 없습니다.',
  CURRENT_PASSWORD_MISMATCH: '현재 비밀번호가 일치하지 않습니다.',
  PASSWORD_POLICY_VIOLATION:
    '새 비밀번호가 정책을 충족하지 않습니다 (10자 이상, 영문·숫자·특수문자 각 1자 이상).',
  PASSWORD_CONFIRM_MISMATCH: '새 비밀번호와 확인 입력이 일치하지 않습니다.',
  // --- wms operations (TASK-PC-FE-007 / §2.4.5) --------------------------
  FORBIDDEN: '이 작업을 수행할 권한이 없습니다. (역할 확인 필요)',
  NOT_FOUND: '대상을 찾을 수 없습니다.',
  DUPLICATE_REQUEST:
    '이전 요청과 다른 내용으로 같은 작업이 재시도되었습니다.',
  WMS_NOT_ELIGIBLE:
    'wms 운영 화면에 접근할 권한(테넌트 스코프)이 없습니다. 운영자에게 문의하세요.',
  ALERT_ALREADY_ACKNOWLEDGED:
    '이미 확인 처리된 알림입니다. 목록을 새로고침하세요.',
  // --- scm operations (TASK-PC-FE-008 / §2.4.6) --------------------------
  PO_NOT_FOUND: '대상 발주(PO)를 찾을 수 없습니다.',
  NODE_NOT_FOUND: '대상 노드를 찾을 수 없습니다.',
  NODE_UNREACHABLE:
    '해당 노드는 이벤트를 보고한 적이 없어 조회할 수 없습니다.',
  RATE_LIMIT_EXCEEDED:
    'scm 게이트웨이 요청이 일시적으로 제한되었습니다. 잠시 후 자동으로 다시 시도합니다.',
  SCM_NOT_ELIGIBLE:
    'scm 운영 화면에 접근할 권한(테넌트 스코프)이 없습니다. 운영자에게 문의하세요.',
};

export function messageForCode(code: string, fallback?: string): string {
  return MESSAGES[code] ?? fallback ?? '알 수 없는 오류가 발생했습니다.';
}
