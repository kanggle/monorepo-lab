# HTTP Contract: admin-service (Public API — Operator Only)

운영자 전용. 게이트웨이에서 `/api/admin/*` 경로에 **별도 인증 필터 체인** 적용. 일반 사용자 JWT로는 접근 불가.

base path: `/api/admin`

모든 요청에 필수: `X-Operator-Reason` 헤더 (감사 사유)

---

## Authentication

모든 `/api/admin/*` 경로는 기본적으로 **operator JWT 필수**이다:

- `Authorization: Bearer <operator-token>` 필수 (`token_type = "admin"`, `iss = "admin-service"`)
- 토큰 발급·서명·검증은 admin-service 자체 IdP가 소유 ([specs/services/admin-service/architecture.md](../../services/admin-service/architecture.md) Admin IdP Boundary 참조)
- `X-Operator-Reason` 헤더 필수 (감사 사유)

### Exceptions (no operator JWT required)

아래 서브트리는 위 기본 규칙의 **예외**로, 정식 operator JWT 없이도 호출 가능하다. `OperatorAuthenticationFilter.shouldNotFilter`의 완화 범위는 **정확히 이 경로들로 한정**된다.

| Method | Path | 대체 인증 | X-Operator-Reason |
|---|---|---|---|
| `POST` | `/api/admin/auth/login` | 없음 (username + password + 선택적 TOTP 코드 body) | 요구 없음 |
| `POST` | `/api/admin/auth/2fa/enroll` | **bootstrap token 필수** | 요구 없음 |
| `POST` | `/api/admin/auth/2fa/verify` | **bootstrap token 필수** | 요구 없음 |
| `POST` | `/api/admin/auth/refresh` | 없음 (refresh JWT body) | 요구 없음 |
| `GET` | `/.well-known/admin/jwks.json` | 없음 (public key 노출) | 요구 없음 |

위 경로 외의 어떤 `/api/admin/*` 요청도 operator JWT + `X-Operator-Reason`이 없으면 401/400으로 거부된다.

#### Bootstrap Token (2FA sub-tree)

`/api/admin/auth/2fa/enroll` 및 `/api/admin/auth/2fa/verify`는 정식 operator JWT 대신 **bootstrap token**(`token_type = "admin_bootstrap"`, TTL 10분, scope = `["2fa_enroll", "2fa_verify"]`, `jti` 1회 소비)을 요구한다. bootstrap token은 `POST /api/admin/auth/login`이 password verify 성공 + 2FA 미완료 상태일 때 응답 body로 발급한다. 상세 규약은 [specs/services/admin-service/security.md](../../services/admin-service/security.md) "Bootstrap Token" 섹션 참조. 구현은 TASK-BE-029.

#### X-Operator-Reason in Exceptions sub-tree

본 서브트리의 요청은 "다른 대상에 대한 운영 명령"이 아니라 **요청자 본인의 인증 플로우**이므로 `X-Operator-Reason` 헤더를 요구하지 않는다. `admin_actions` 기록이 발생하는 경우(예: enroll/verify)는 reason 필드에 상수 `"<self_enrollment>"`로 기록한다.

---

## Authorization Model

모든 mutation 및 read endpoint는 필요한 **permission key**를 선언한다 ([specs/services/admin-service/rbac.md](../../services/admin-service/rbac.md)). 운영자의 role 집합이 보유한 permission 합집합에 요청 endpoint의 permission이 포함되어야 통과한다. 누락 시 `403 PERMISSION_DENIED`.

- Permission key catalog: `account.read`, `account.lock`, `account.unlock`, `account.force_logout`, `audit.read`, `security.event.read`
- Annotation이 선언되지 않은 endpoint는 **fail-closed로 deny**되며 `admin_actions`에 `outcome=DENIED, permission_used="<missing>"` 기록
- 권한 거부는 request 단위로 감사 row 1건 기록 (dedup 없음)
- Operator 식별자는 JWT `sub` 클레임에서 추출 (`token_type = "admin"` 필수)

---

## GET /api/admin/accounts

계정 검색. 이메일이 있으면 이메일로 단건 검색하고, 없으면 `account.read` 권한 보유 시 전체 계정 목록을 페이지네이션으로 반환한다.

**Auth required**: Yes (operator token, `token_type=admin`)
**Required permission**: `account.read`
**Granted to roles**: `SUPER_ADMIN`, `SUPPORT_READONLY`

**Query Parameters**:

| 파라미터 | 타입 | 설명 |
|---|---|---|
| `email` | string (optional) | 이메일로 단건 검색. 없으면 전체 목록 반환 |
| `page` | int (default 0) | 전체 목록 조회 시 페이지 번호 |
| `size` | int (default 20, max 100) | 전체 목록 조회 시 페이지 크기 |

**동작 규칙**:
- `email` 파라미터 있음 → 이메일로 단건 검색. `account.read` 권한 불필요 (기존 동작 유지).
- `email` 파라미터 없음 + `account.read` 권한 보유 → 전체 계정 목록 페이지네이션 반환.
- `email` 파라미터 없음 + `account.read` 권한 미보유 → 빈 목록 반환 (403 아님).
- `size` > 100 → 400 `VALIDATION_ERROR`.

**Response 200** (email 없음, account.read 보유):
```json
{
  "content": [
    {
      "id": "string",
      "email": "string",
      "status": "ACTIVE",
      "createdAt": "2026-01-01T00:00:00Z"
    }
  ],
  "totalElements": 150,
  "page": 0,
  "size": 20,
  "totalPages": 8
}
```

**Response 200** (email 있음 또는 account.read 미보유):
```json
{
  "content": [],
  "totalElements": 0,
  "page": 0,
  "size": 20,
  "totalPages": 0
}
```

**Errors**:

| Status | Code | 조건 |
|---|---|---|
| 401 | `TOKEN_INVALID` | operator token 만료/변조 |
| 400 | `VALIDATION_ERROR` | size > 100 |
| 503 | `DOWNSTREAM_ERROR` | account-service 호출 실패 |
| 503 | `CIRCUIT_OPEN` | account-service circuit breaker OPEN |

---

## POST /api/admin/accounts/{accountId}/lock

계정 강제 잠금.

**Auth required**: Yes (operator token, `token_type=admin`)
**Required permission**: `account.lock`
**Granted to roles**: `SUPER_ADMIN`, `SUPPORT_LOCK`

**Headers**:
- `Authorization: Bearer <operator-token>`
- `X-Operator-Reason: string (required, 감사 사유)`
- `Idempotency-Key: string (required)`

**Request**:
```json
{
  "reason": "string (required, 상세 사유)",
  "ticketId": "string (optional, 내부 티켓 번호)"
}
```

**Response 200**:
```json
{
  "accountId": "string",
  "previousStatus": "ACTIVE",
  "currentStatus": "LOCKED",
  "operatorId": "string",
  "lockedAt": "2026-04-12T10:00:00Z",
  "auditId": "string (admin_actions.id)"
}
```

**Errors**:

| Status | Code | 조건 |
|---|---|---|
| 401 | `TOKEN_INVALID` | operator token 만료/변조 |
| 403 | `PERMISSION_DENIED` | role 부족 |
| 400 | `STATE_TRANSITION_INVALID` | 이미 LOCKED 또는 DELETED 상태 |
| 400 | `REASON_REQUIRED` | X-Operator-Reason 또는 body reason 누락 |
| 404 | `ACCOUNT_NOT_FOUND` | 대상 계정 미존재 |
| 503 | `DOWNSTREAM_ERROR` | account-service 호출 실패 (5xx/timeout) |
| 503 | `CIRCUIT_OPEN` | account-service circuit breaker OPEN (호출 자체 거부) |

**Side Effects**: admin_actions 감사 기록 + `admin.action.performed` 이벤트 + account-service에 내부 HTTP lock 명령. `503 DOWNSTREAM_ERROR`/`503 CIRCUIT_OPEN` 시에도 `admin_actions`에 `outcome=FAILURE` 행이 기록된다 (A10 fail-closed).

---

## POST /api/admin/accounts/bulk-lock

여러 계정을 한 번의 요청으로 순차 잠금. 보안 사고 대응 시 사용.

**Auth required**: Yes (operator token, `token_type=admin`)
**Required permission**: `account.lock`
**Granted to roles**: `SUPER_ADMIN`, `SUPPORT_LOCK`

**Headers**:
- `Authorization: Bearer <operator-token>`
- `X-Operator-Reason: string (required)` — 감사 헤더
- `Idempotency-Key: string (required, ≤64자, UUID 권장)`

**Request**:
```json
{
  "accountIds": ["acc-1", "acc-2", "..."],
  "reason": "string (required, ≥8자)",
  "ticketId": "string (optional)"
}
```

**Constraints**:
- `accountIds.length` ≤ 100. 초과 시 `422 BATCH_SIZE_EXCEEDED`.
- 중복 `accountId`는 서버에서 1회만 처리 (입력 순서 보존 dedup).
- `reason`은 최소 8자.

**Response 200**:
```json
{
  "results": [
    { "accountId": "acc-1", "outcome": "LOCKED" },
    { "accountId": "acc-2", "outcome": "NOT_FOUND",      "error": { "code": "ACCOUNT_NOT_FOUND", "message": "..." } },
    { "accountId": "acc-3", "outcome": "ALREADY_LOCKED", "error": { "code": "STATE_TRANSITION_INVALID", "message": "..." } },
    { "accountId": "acc-4", "outcome": "FAILURE",        "error": { "code": "DOWNSTREAM_ERROR", "message": "..." } }
  ]
}
```

- `outcome` ∈ `{LOCKED, NOT_FOUND, ALREADY_LOCKED, FAILURE}`
- 부분 실패 허용: 일부 계정 실패 시에도 응답은 200. 전체 롤백 없음.
- 각 타겟 계정별로 `admin_actions` 1행 기록 (NOT_FOUND/FAILURE 포함).

**Idempotency**:
- `(operator_id, Idempotency-Key)`가 유일 키.
- 동일 key로 재요청 시 body(SHA-256 기준 canonical hash = sorted dedup accountIds + reason + ticketId) 일치하면 **이전 응답 본문을 그대로 반환**하며 추가 다운스트림 호출·감사 row 기록 없음.
- 동일 key + 다른 payload 시 `409 IDEMPOTENCY_KEY_CONFLICT`.
- 저장 TTL 정책은 운영 정책(out-of-scope). 별도 cleanup 작업이 책임.

**Errors**:

| Status | Code | 조건 |
|---|---|---|
| 401 | `TOKEN_INVALID` | operator token 만료/변조 |
| 403 | `PERMISSION_DENIED` | `account.lock` 미보유 |
| 400 | `REASON_REQUIRED` | `X-Operator-Reason` 누락 |
| 400 | `VALIDATION_ERROR` | 필수 헤더/필드 누락, reason<8자, accountIds 비어있음 |
| 422 | `BATCH_SIZE_EXCEEDED` | accountIds>100 |
| 409 | `IDEMPOTENCY_KEY_CONFLICT` | 동일 key 재사용·payload 불일치 |
| 503 | `CIRCUIT_OPEN` | 전체 요청이 circuit open으로 소화되지 않은 드문 경우 (개별 row 레벨 CB OPEN은 outcome=FAILURE로 기록) |

**Side Effects**: accountId별 `admin_actions` 1행, `admin.action.performed` outbox 이벤트 N건, `admin_bulk_lock_idempotency` 1행.

---

## POST /api/admin/accounts/{accountId}/unlock

계정 잠금 해제.

**Auth required**: Yes (operator token, `token_type=admin`)
**Required permission**: `account.unlock`
**Granted to roles**: `SUPER_ADMIN`, `SUPPORT_LOCK`

**Headers**: Authorization + X-Operator-Reason + Idempotency-Key

**Request**:
```json
{
  "reason": "string (required)",
  "ticketId": "string (optional)"
}
```

**Response 200**:
```json
{
  "accountId": "string",
  "previousStatus": "LOCKED",
  "currentStatus": "ACTIVE",
  "operatorId": "string",
  "unlockedAt": "2026-04-12T10:00:00Z",
  "auditId": "string"
}
```

**Errors**: lock과 동일 구조 (503 `DOWNSTREAM_ERROR` + 503 `CIRCUIT_OPEN` 포함). `STATE_TRANSITION_INVALID`는 LOCKED가 아닌 상태에서 unlock 시도 시.

---

## POST /api/admin/sessions/{accountId}/revoke

특정 계정의 모든 세션 강제 종료 (refresh token 전체 revoke).

**Auth required**: Yes (operator token, `token_type=admin`)
**Required permission**: `account.force_logout`
**Granted to roles**: `SUPER_ADMIN`, `SUPPORT_LOCK`, `SECURITY_ANALYST`

**Headers**: Authorization + X-Operator-Reason + Idempotency-Key

**Request**:
```json
{
  "reason": "string (required)"
}
```

**Response 200**:
```json
{
  "accountId": "string",
  "revokedSessionCount": 3,
  "operatorId": "string",
  "revokedAt": "2026-04-12T10:00:00Z",
  "auditId": "string"
}
```

**Errors**:

| Status | Code | 조건 |
|---|---|---|
| 401 | `TOKEN_INVALID` | — |
| 403 | `PERMISSION_DENIED` | — |
| 404 | `ACCOUNT_NOT_FOUND` | — |
| 503 | `DOWNSTREAM_ERROR` | auth-service 호출 실패 (5xx/timeout) |
| 503 | `CIRCUIT_OPEN` | auth-service circuit breaker OPEN (호출 자체 거부) |

**Side Effects**: auth-service에 내부 HTTP force-logout 명령 + admin_actions 기록. `503 DOWNSTREAM_ERROR`/`503 CIRCUIT_OPEN` 시에도 `admin_actions`에 `outcome=FAILURE` 행이 기록된다 (A10 fail-closed).

---

## GET /api/admin/audit

감사 로그 조회 (통합 뷰: admin_actions + login_history + suspicious_events).

**Auth required**: Yes (operator token, `token_type=admin`)
**Required permission**: `audit.read` (기본). `source=login_history` 또는 `source=suspicious` 필터 사용 시 `security.event.read`도 **추가로** 요구 (union이 아닌 intersection 검증 — 두 권한 모두 필요)
**Granted to roles**:
- `audit.read` only: `SUPPORT_LOCK` (admin_actions만 조회 가능, security source 필터 사용 시 403)
- `audit.read` + `security.event.read`: `SUPER_ADMIN`, `SUPPORT_READONLY`, `SECURITY_ANALYST` (모든 source 조회 가능)

**Query Parameters**:

| 파라미터 | 타입 | 설명 |
|---|---|---|
| `accountId` | string (optional) | 특정 계정 필터 |
| `actionCode` | string (optional) | ACCOUNT_LOCK, SESSION_REVOKE 등 |
| `from` | ISO 8601 datetime | 시작 시각 |
| `to` | ISO 8601 datetime | 종료 시각 |
| `source` | string (optional) | `admin` / `login_history` / `suspicious` |
| `page` | int (default 0) | — |
| `size` | int (default 20, max 100) | — |

**Response 200**:
```json
{
  "content": [
    {
      "source": "admin",
      "auditId": "string",
      "actionCode": "ACCOUNT_LOCK",
      "operatorId": "string",
      "targetId": "string",
      "reason": "string",
      "outcome": "SUCCESS",
      "occurredAt": "2026-04-12T10:00:00Z"
    },
    {
      "source": "login_history",
      "eventId": "string",
      "accountId": "string",
      "outcome": "FAILURE",
      "ipMasked": "192.168.*.*",
      "geoCountry": "KR",
      "occurredAt": "2026-04-12T09:58:00Z"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 150,
  "totalPages": 8
}
```

**Errors**:

| Status | Code | 조건 |
|---|---|---|
| 401 | `TOKEN_INVALID` | — |
| 403 | `PERMISSION_DENIED` | `audit.read` 또는 source별 추가 권한(`security.event.read`) 부족 |
| 422 | `VALIDATION_ERROR` | from > to, size > 100 등 |

**Note**: 이 조회 자체가 **meta-audit**로 기록됨 ([rules/traits/audit-heavy.md](../../../rules/traits/audit-heavy.md) A5). PII는 마스킹됨 (IP 일부, 이메일 미포함).

---

## POST /api/admin/auth/login

운영자 자체 로그인. Argon2id 패스워드 검증 + (role 중 `require_2fa=TRUE`가 있으면) TOTP 또는 recovery code 검증 후 operator JWT를 발급한다.

**Auth required**: 없음 (body 기반 인증)
**Required permission**: 없음 (self-login)
**X-Operator-Reason**: 요구 없음 (`admin_actions.reason = "<self_login>"` 상수 기록)

**Headers**: 없음

**Request**:
```json
{
  "operatorId": "string (UUID v7, required)",
  "password":   "string (required)",
  "totpCode":   "string (6 digits, optional)",
  "recoveryCode": "string (optional, XXXX-XXXX-XXXX 형식)"
}
```

- `totpCode`와 `recoveryCode`는 **택일**. 둘 다 제공하거나 둘 다 빠지면 400.
- `require_2fa=FALSE` 운영자(=2FA 비요구 role 집합)는 둘 다 생략하고 호출한다.
- `recoveryCode`는 서버가 대문자/하이픈 normalize 후 Argon2id `verify` 비교한다.

**Response 200**:
```json
{
  "accessToken": "eyJhbGciOi... (operator JWT)",
  "expiresIn": 3600,
  "refreshToken": "eyJhbGciOi... (operator refresh JWT)",
  "refreshExpiresIn": 2592000
}
```

- `accessToken`: `{sub: operator_uuid, iss: "admin-service", jti: uuidV7, iat, exp, token_type: "admin"}`.
- `expiresIn`: 초 단위 TTL (기본 3600, `admin.jwt.access-token-ttl-seconds`로 조정).
- `refreshToken`: `{sub: operator_uuid, iss: "admin-service", jti: uuid, iat, exp, token_type: "admin_refresh"}`. 발급 시 `admin_operator_refresh_tokens(jti)` row가 함께 INSERT 된다(같은 트랜잭션). TASK-BE-040.
- `refreshExpiresIn`: 초 단위 TTL (기본 2,592,000 = 30일, `admin.jwt.refresh-token-ttl-seconds`로 조정).

**Response 401 (ENROLLMENT_REQUIRED, 2FA 등록이 필요한 경우 body 확장)**:
```json
{
  "code": "ENROLLMENT_REQUIRED",
  "message": "Operator must complete 2FA enrollment before login",
  "bootstrapToken": "eyJhbGciOi...",
  "bootstrapExpiresIn": 600
}
```

`bootstrapToken`은 `POST /api/admin/auth/2fa/enroll` 및 `/2fa/verify`에만 사용 가능한 1회용 토큰이다 ([security.md §Bootstrap Token](../../services/admin-service/security.md)).

**Errors**:

| Status | Code | 조건 |
|---|---|---|
| 400 | `VALIDATION_ERROR` | 필수 필드(`operatorId`/`password`) 누락 또는 형식 오류 |
| 400 | `BAD_REQUEST` | `totpCode`와 `recoveryCode`가 동시에 제공되었거나, 2FA가 필요한데 둘 다 미제공 |
| 401 | `INVALID_CREDENTIALS` | operator 조회 미스 또는 password_hash 불일치. 미스 경로도 dummy Argon2id verify 수행 (타이밍 완화) |
| 401 | `ENROLLMENT_REQUIRED` | 2FA 필수이나 `admin_operator_totp` row 부재. body에 `bootstrapToken` 포함 |
| 401 | `INVALID_2FA_CODE` | TOTP 검증 실패 (±1 window 밖) |
| 401 | `INVALID_RECOVERY_CODE` | recovery code가 어떤 저장된 hash와도 일치하지 않음 (optimistic lock 1회 retry 후) |
| 500 | `AUDIT_FAILURE` | 성공 경로 감사 row 기록 실패 (fail-closed). 실패 경로의 secondary 감사 실패는 삼켜지고 원래 응답이 유지됨 |

감사: `action_code = OPERATOR_LOGIN`, `target_type = OPERATOR`, `target_id = operator_id`, `permission_used = auth.login`, `reason = "<self_login>"`, `twofa_used = TRUE|FALSE` (2FA 경로 여부), `outcome = SUCCESS|FAILURE`.

---

## POST /api/admin/auth/refresh

운영자 refresh JWT를 회전시켜 새 access + refresh 쌍을 발급한다. TASK-BE-040.

**Auth required**: 없음 (body의 refresh JWT가 인증 수단)
**Required permission**: 없음 (self-managed session)
**X-Operator-Reason**: 요구 없음 (`admin_actions.reason = "<self_refresh>"` 상수 기록)

**Headers**: 없음

**Request**:
```json
{
  "refreshToken": "string (required, operator refresh JWT)"
}
```

**Behavior**:
1. JWT 서명/exp/iss 검증 + `token_type=admin_refresh` 확인
2. `admin_operator_refresh_tokens.findByJti(jti)` 조회 — 미존재 → 401 `INVALID_REFRESH_TOKEN`
3. row.`revoked_at != null` → **재사용 탐지**: 해당 operator의 모든 미revoked refresh token을 `revoke_reason=REUSE_DETECTED`로 일괄 revoke + 401 `REFRESH_TOKEN_REUSE_DETECTED`
4. 정상: 기존 jti를 `revoke_reason=ROTATED`로 revoke, 새 access + 새 refresh 발급, 새 row insert(`rotated_from`=기존 jti)

**Response 200**:
```json
{
  "accessToken": "eyJhbGciOi...",
  "expiresIn": 3600,
  "refreshToken": "eyJhbGciOi...",
  "refreshExpiresIn": 2592000
}
```

**Errors**:

| Status | Code | 조건 |
|---|---|---|
| 400 | `VALIDATION_ERROR` | `refreshToken` 누락 |
| 401 | `INVALID_REFRESH_TOKEN` | 서명/exp/issuer 실패, `token_type` 불일치, jti 미등록, operator 불일치 |
| 401 | `REFRESH_TOKEN_REUSE_DETECTED` | 이미 revoked된 jti의 재사용 — 체인 전체 invalidate |

감사: `action_code = OPERATOR_REFRESH`, `target_type = OPERATOR`, `target_id = operator_id`, `permission_used = auth.refresh`, `reason = "<self_refresh>"`, `outcome = SUCCESS|FAILURE`(REUSE_DETECTED 시 `downstream_detail = "REUSE_DETECTED"`).

---

## POST /api/admin/auth/logout

운영자 자체 logout. access JWT의 jti를 Redis 블랙리스트에 등록하고(잔여 TTL), 선택적으로 제공된 refresh token을 revoke한다. 204를 응답. TASK-BE-040.

**Auth required**: Yes (operator JWT, `token_type=admin`) — 본인 확인 위해 인증 필요. 029-1 bypass 목록에 포함되지 않는다.
**Required permission**: 없음 (self-managed session)
**X-Operator-Reason**: 요구 없음 (`admin_actions.reason = "<self_logout>"` 상수 기록)

**Headers**:
- `Authorization: Bearer <operator-token>`

**Request** (optional body):
```json
{
  "refreshToken": "string (optional)"
}
```

**Behavior**:
- access JWT의 jti를 Redis SETEX `admin:jti:blacklist:{jti}`, TTL = (access exp - now). `OperatorAuthenticationFilter`는 이 키를 매 요청마다 확인하여 hit 시 401 `TOKEN_REVOKED` 반환. Redis 다운 시 fail-closed (401).
- `refreshToken`이 제공되면 그 jti를 `revoke_reason=LOGOUT`으로 revoke. 미제공이거나 검증 실패 시 무시 (best-effort).

**Response 204**: 본문 없음

**Errors**:

| Status | Code | 조건 |
|---|---|---|
| 401 | `TOKEN_INVALID` | operator JWT 누락/만료/변조 |
| 401 | `TOKEN_REVOKED` | 이미 logout된 jti로 재호출 |
| 500 | `INTERNAL_ERROR` | Redis blacklist 쓰기 실패 — 클라이언트 재시도 |

감사: `action_code = OPERATOR_LOGOUT`, `target_type = OPERATOR`, `target_id = operator_id`, `permission_used = auth.logout`, `reason = "<self_logout>"`, `outcome = SUCCESS|FAILURE`.

---

## POST /api/admin/auth/2fa/enroll

운영자 자신의 TOTP 2FA 최초(또는 재) 등록. 서버가 160-bit secret 생성·AES-GCM 암호화 저장, Argon2id hash된 10개 recovery codes를 동시 발급한다.

**Auth required**: Bootstrap token (operator JWT 불가). `Authorization: Bearer <bootstrap-token>`, `token_type = "admin_bootstrap"`, `jti` 1회 소비 (상세: [security.md §Bootstrap Token](../../services/admin-service/security.md)).
**Required permission**: 없음 (self-enrollment)
**X-Operator-Reason**: 요구 없음 (`admin_actions.reason = "<self_enrollment>"` 상수 기록)

**Headers**:
- `Authorization: Bearer <bootstrap-token>`

**Request**: body 없음

**Response 200**:
```json
{
  "otpauthUri": "otpauth://totp/admin-service:op@example.com?secret=...&issuer=admin-service&algorithm=SHA1&digits=6&period=30",
  "recoveryCodes": ["A1B2-C3D4-E5F6", "..."],
  "enrolledAt": "2026-04-14T10:00:00Z"
}
```

`recoveryCodes`는 **평문 1회 응답 후 서버 저장은 Argon2id 해시만**. 재호출 시 기존 secret + recovery codes는 전부 무효화되고 새 값으로 대체된다.

**Errors**:

| Status | Code | 조건 |
|---|---|---|
| 401 | `INVALID_BOOTSTRAP_TOKEN` | bootstrap token 부재/만료/재사용(jti 소비됨)/서명 실패/`token_type` 불일치 |
| 500 | `AUDIT_FAILURE` | 감사 row 기록 실패 (fail-closed) |

감사: `action_code = OPERATOR_2FA_ENROLL`, `target_type = OPERATOR`, `target_id = operator_id`, `permission_used = auth.2fa_enroll`, `twofa_used = FALSE`, `outcome = SUCCESS|FAILURE`.

---

## POST /api/admin/auth/2fa/verify

Enroll 직후 또는 로그인 플로우(TASK-BE-029-3)에서 운영자가 제출한 TOTP 코드를 검증한다. 성공 시 `admin_operator_totp.last_used_at`이 갱신된다.

**Auth required**: Bootstrap token.
**Required permission**: 없음 (self-enrollment)
**X-Operator-Reason**: 요구 없음

**Headers**:
- `Authorization: Bearer <bootstrap-token>`

**Request**:
```json
{
  "totpCode": "string (6 digits, required)"
}
```

**Response 200**:
```json
{ "verified": true }
```

**Errors**:

| Status | Code | 조건 |
|---|---|---|
| 400 | `VALIDATION_ERROR` | `totpCode`이 6자리 숫자가 아님 |
| 401 | `INVALID_BOOTSTRAP_TOKEN` | bootstrap token 부재/만료/재사용/서명 실패 |
| 401 | `INVALID_2FA_CODE` | 코드가 ±1 window(30s step) 안에서 일치하지 않음 (enrollment 미존재 시 동일 코드 반환) |
| 500 | `AUDIT_FAILURE` | 감사 row 기록 실패 |

감사: `action_code = OPERATOR_2FA_VERIFY`, `target_type = OPERATOR`, `permission_used = auth.2fa_verify`, `twofa_used = FALSE` (본 엔드포인트 자체는 로그인 경로가 아님 — `twofa_used = TRUE`는 029-3의 `/login` 감사 row에서 기록).

---

## POST /api/admin/auth/2fa/recovery-codes/regenerate

운영자가 TOTP 백업 복구 코드를 모두 소진하거나 분실한 경우 새로운 10개 코드 세트를 발급받는다. 호출 시 기존 `recovery_codes_hashed`는 즉시 완전 교체되며 이전 코드는 무효화된다.

**Auth required**: Yes (operator JWT, `token_type=admin`) — 정식 operator 토큰 필요. bootstrap token 사용 불가.
**Required permission**: 없음 (self-service: 본인의 복구 코드만 재발급)
**X-Operator-Reason**: 요구 없음 (`admin_actions.reason = "<self_recovery_regenerate>"` 상수 기록)

**Headers**:
- `Authorization: Bearer <operator-token>`

**Request**: body 없음

**Response 200**:
```json
{
  "recoveryCodes": ["A1B2-C3D4-E5F6", "G7H8-I9J0-K1L2", "..."]
}
```

`recoveryCodes`는 **평문 1회 응답 후 서버 저장은 Argon2id 해시만**. 정확히 10개 반환된다. 응답 직후 클라이언트가 안전하게 저장해야 하며 서버는 이후 평문 코드를 보유하지 않는다.

**Errors**:

| Status | Code | 조건 |
|---|---|---|
| 401 | `TOKEN_INVALID` | operator JWT 누락/만료/변조 |
| 401 | `TOKEN_REVOKED` | 이미 logout된 jti |
| 404 | `TOTP_NOT_ENROLLED` | `admin_operator_totp` row 부재 (TOTP 미등록 상태에서는 재발급 불가 — 먼저 `/api/admin/auth/2fa/enroll` 필요) |

**Side Effects**: `admin_operator_totp.recovery_codes_hashed` 컬럼이 새 JSON 배열로 완전히 교체된다 (이전 코드 즉시 무효화). `last_used_at`도 함께 갱신된다.

**Note**: 본 엔드포인트는 평문 복구 코드를 응답으로만 반환하며 서버 로그에 출력하지 않는다 (R4 준수).

---

## Operator Roles

**Reference matrix**: Role × permission 매트릭스의 canonical source는 [specs/services/admin-service/rbac.md — Seed Matrix](../../services/admin-service/rbac.md#seed-matrix-role--permission) 한 곳이다. 본 계약에서는 중복 테이블을 유지하지 않는다 (drift 방지).

### 403 Response Shape

`PERMISSION_DENIED` 응답은 본 문서의 [Common Error Format](#common-error-format)을 따른다. permission 관련 추가 필드는 응답에 **노출하지 않는다** (attack surface 축소 — 클라이언트는 어떤 permission이 누락되었는지 알 수 없다). 거부 상세는 `admin_actions.detail`에만 기록된다.

```json
{
  "code": "PERMISSION_DENIED",
  "message": "Operator is not authorized to perform this action.",
  "timestamp": "2026-04-13T10:00:00Z"
}
```

---

## POST /api/admin/accounts/{accountId}/gdpr-delete

GDPR/PIPA 삭제권 이행. 계정 상태를 DELETED로 전이하고 PII를 즉시 마스킹한다 (이메일 SHA-256 해시 교체, 프로필 필드 NULL 처리).

**Auth required**: Yes (operator token, `token_type=admin`)
**Required permission**: `account.lock` (기존 권한 재사용)
**Granted to roles**: `SUPER_ADMIN`, `SUPPORT_LOCK`

**Headers**:
- `Authorization: Bearer <operator-token>`
- `X-Operator-Reason: string (required, 감사 사유)`
- `Idempotency-Key: string (required)`

**Request**:
```json
{
  "reason": "string (required, GDPR 삭제 사유)",
  "ticketId": "string (optional, 내부 티켓 번호)"
}
```

**Response 200**:
```json
{
  "accountId": "string",
  "status": "DELETED",
  "maskedAt": "2026-04-18T10:00:00Z",
  "auditId": "string (admin_actions.id)"
}
```

**Errors**:

| Status | Code | 조건 |
|---|---|---|
| 401 | `TOKEN_INVALID` | operator token 만료/변조 |
| 403 | `PERMISSION_DENIED` | role 부족 |
| 400 | `STATE_TRANSITION_INVALID` | 이미 DELETED 상태 |
| 400 | `REASON_REQUIRED` | X-Operator-Reason 또는 body reason 누락 |
| 404 | `ACCOUNT_NOT_FOUND` | 대상 계정 미존재 |
| 503 | `DOWNSTREAM_ERROR` | account-service 호출 실패 (5xx/timeout) |
| 503 | `CIRCUIT_OPEN` | account-service circuit breaker OPEN |

**Side Effects**: admin_actions 감사 기록 + `admin.action.performed` 이벤트 + account-service에 내부 HTTP GDPR 삭제 명령. PII 마스킹은 account-service 내에서 동일 트랜잭션으로 수행.

---

## GET /api/admin/accounts/{accountId}/export

GDPR/PIPA 이식권 이행. 계정의 개인 데이터를 JSON으로 내보낸다.

**Auth required**: Yes (operator token, `token_type=admin`)
**Required permission**: `audit.read`
**Granted to roles**: `SUPER_ADMIN`, `SUPPORT_READONLY`, `SECURITY_ANALYST`

**Headers**:
- `Authorization: Bearer <operator-token>`
- `X-Operator-Reason: string (required, 감사 사유)`

**Response 200**:
```json
{
  "accountId": "string",
  "email": "string",
  "status": "string",
  "createdAt": "2026-01-01T00:00:00Z",
  "profile": {
    "displayName": "string",
    "phoneNumber": "string",
    "birthDate": "1990-01-15",
    "locale": "ko-KR",
    "timezone": "Asia/Seoul"
  },
  "exportedAt": "2026-04-18T10:00:00Z"
}
```

**Errors**:

| Status | Code | 조건 |
|---|---|---|
| 401 | `TOKEN_INVALID` | operator token 만료/변조 |
| 403 | `PERMISSION_DENIED` | role 부족 |
| 404 | `ACCOUNT_NOT_FOUND` | 대상 계정 미존재 |
| 503 | `DOWNSTREAM_ERROR` | account-service 호출 실패 (5xx/timeout) |
| 503 | `CIRCUIT_OPEN` | account-service circuit breaker OPEN |

**Side Effects**: 이 조회 자체가 **meta-audit**로 기록됨 (admin_actions에 action_code=DATA_EXPORT, outcome=SUCCESS). PII가 마스킹되지 않은 원본 데이터 접근이므로 감사 추적 필수.

---

## GET /api/admin/me

현재 로그인한 운영자의 정보를 반환한다.

**Auth required**: Yes (operator token, `token_type=admin`)
**Required permission**: 없음 (유효한 operator JWT만 필요)

**Response 200**:
```json
{
  "operatorId": "string (UUID v7)",
  "email": "string",
  "displayName": "string",
  "status": "ACTIVE",
  "roles": ["SUPER_ADMIN"],
  "totpEnrolled": true,
  "lastLoginAt": "2026-04-24T10:00:00Z",
  "createdAt": "2026-01-01T00:00:00Z"
}
```

**Errors**:

| Status | Code | 조건 |
|---|---|---|
| 401 | `TOKEN_INVALID` | operator token 만료/변조 |

---

## GET /api/admin/operators

전체 운영자 목록 조회 (페이지네이션).

**Auth required**: Yes (operator token, `token_type=admin`)
**Required permission**: `operator.manage`
**Granted to roles**: `SUPER_ADMIN`

**Query Parameters**:

| 파라미터 | 타입 | 설명 |
|---|---|---|
| `status` | string (optional) | `ACTIVE` 또는 `SUSPENDED` 필터. 미지정 시 전체 반환 |
| `page` | int (default 0) | 페이지 번호 |
| `size` | int (default 20, max 100) | 페이지 크기 |

**Response 200**:
```json
{
  "content": [
    {
      "operatorId": "string (UUID v7)",
      "email": "string",
      "displayName": "string",
      "status": "ACTIVE",
      "roles": ["SUPPORT_LOCK"],
      "totpEnrolled": false,
      "lastLoginAt": "2026-04-24T10:00:00Z",
      "createdAt": "2026-01-01T00:00:00Z"
    }
  ],
  "totalElements": 10,
  "page": 0,
  "size": 20,
  "totalPages": 1
}
```

**Errors**:

| Status | Code | 조건 |
|---|---|---|
| 401 | `TOKEN_INVALID` | operator token 만료/변조 |
| 403 | `PERMISSION_DENIED` | `operator.manage` 권한 없음 |
| 400 | `VALIDATION_ERROR` | size > 100 또는 status 값 오류 |

---

## POST /api/admin/operators

신규 운영자 계정 생성.

**Auth required**: Yes (operator token, `token_type=admin`)
**Required permission**: `operator.manage`
**Granted to roles**: `SUPER_ADMIN`

**Headers**:
- `Authorization: Bearer <operator-token>`
- `X-Operator-Reason: string (required)`
- `Idempotency-Key: string (required)`

**Request**:
```json
{
  "email": "string (required, valid email format)",
  "displayName": "string (required, 1–64자)",
  "password": "string (required, ≥10자, 영문+숫자+특수문자 각 1자 이상)",
  "roles": ["SUPPORT_LOCK"]
}
```

**Response 201**:
```json
{
  "operatorId": "string (UUID v7)",
  "email": "string",
  "displayName": "string",
  "status": "ACTIVE",
  "roles": ["SUPPORT_LOCK"],
  "totpEnrolled": false,
  "createdAt": "2026-04-24T10:00:00Z",
  "auditId": "string (admin_actions.id)"
}
```

**Errors**:

| Status | Code | 조건 |
|---|---|---|
| 401 | `TOKEN_INVALID` | operator token 만료/변조 |
| 403 | `PERMISSION_DENIED` | `operator.manage` 권한 없음 |
| 409 | `OPERATOR_EMAIL_CONFLICT` | 동일 email 운영자 이미 존재 |
| 400 | `ROLE_NOT_FOUND` | `roles` 배열에 존재하지 않는 role 이름 포함 |
| 400 | `VALIDATION_ERROR` | email 형식 오류 / password 정책 위반 / displayName 길이 초과 |

**Side Effects**: `admin_actions`에 `action_code=OPERATOR_CREATE` 기록.

---

## PATCH /api/admin/operators/{operatorId}/roles

운영자의 역할 목록 전체 교체.

**Auth required**: Yes (operator token, `token_type=admin`)
**Required permission**: `operator.manage`
**Granted to roles**: `SUPER_ADMIN`

**Headers**:
- `Authorization: Bearer <operator-token>`
- `X-Operator-Reason: string (required)`

**Path Variable**: `operatorId` — 대상 운영자의 `admin_operators.operator_id` (UUID v7)

**Request**:
```json
{
  "roles": ["SUPPORT_READONLY", "SECURITY_ANALYST"]
}
```

`roles` 빈 배열(`[]`) 허용 — 역할 전부 제거.

**Response 200**:
```json
{
  "operatorId": "string",
  "roles": ["SUPPORT_READONLY", "SECURITY_ANALYST"],
  "auditId": "string"
}
```

**Errors**:

| Status | Code | 조건 |
|---|---|---|
| 401 | `TOKEN_INVALID` | operator token 만료/변조 |
| 403 | `PERMISSION_DENIED` | `operator.manage` 권한 없음 |
| 404 | `OPERATOR_NOT_FOUND` | operatorId 미존재 |
| 400 | `ROLE_NOT_FOUND` | roles 배열에 존재하지 않는 role 이름 포함 |

**Side Effects**: `admin_actions`에 `action_code=OPERATOR_ROLE_CHANGE` 기록. Redis 권한 캐시 즉시 invalidate (`admin:operator:perm:{operatorId}`).

---

## PATCH /api/admin/operators/{operatorId}/status

운영자 계정 상태 변경.

**Auth required**: Yes (operator token, `token_type=admin`)
**Required permission**: `operator.manage`
**Granted to roles**: `SUPER_ADMIN`

**Headers**:
- `Authorization: Bearer <operator-token>`
- `X-Operator-Reason: string (required)`

**Path Variable**: `operatorId` — 대상 운영자의 `admin_operators.operator_id` (UUID v7)

**Request**:
```json
{
  "status": "SUSPENDED"
}
```

허용 값: `ACTIVE`, `SUSPENDED`.

**Response 200**:
```json
{
  "operatorId": "string",
  "previousStatus": "ACTIVE",
  "currentStatus": "SUSPENDED",
  "auditId": "string"
}
```

**Errors**:

| Status | Code | 조건 |
|---|---|---|
| 401 | `TOKEN_INVALID` | operator token 만료/변조 |
| 403 | `PERMISSION_DENIED` | `operator.manage` 권한 없음 |
| 404 | `OPERATOR_NOT_FOUND` | operatorId 미존재 |
| 400 | `SELF_SUSPEND_FORBIDDEN` | 본인 계정 SUSPENDED 시도 |
| 400 | `STATE_TRANSITION_INVALID` | 현재 status와 요청 status가 동일 |
| 400 | `VALIDATION_ERROR` | status 값이 허용 목록 외 |

**Side Effects**: `SUSPENDED` 처리 시 해당 운영자의 모든 refresh token 즉시 무효화. `admin_actions`에 `action_code=OPERATOR_STATUS_CHANGE` 기록.

---

## PATCH /api/admin/operators/me/password

운영자 자신의 비밀번호를 변경한다.

**Auth**: Operator JWT (유효한 토큰만 필요, 별도 permission 없음)

**Request**

```
PATCH /api/admin/operators/me/password
Content-Type: application/json
```

```json
{
  "currentPassword": "OldPass1!",
  "newPassword":     "NewPass2@"
}
```

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `currentPassword` | string | ✅ | 현재 비밀번호 (평문) |
| `newPassword` | string | ✅ | 새 비밀번호 (평문). 8자 이상, 4종 문자(대·소·숫·특) 중 3종 이상 |

**Response**

`204 No Content` — 변경 성공, 본문 없음.

**Errors**

| HTTP | code | 설명 |
|---|---|---|
| 400 | `CURRENT_PASSWORD_MISMATCH` | 현재 비밀번호 불일치 |
| 400 | `PASSWORD_POLICY_VIOLATION` | 새 비밀번호가 정책 미충족 |
| 401 | `TOKEN_INVALID` | JWT 없음 또는 만료 |

---

## Common Error Format

```json
{
  "code": "UPPER_SNAKE_CASE",
  "message": "Human-readable (no PII)",
  "timestamp": "2026-04-12T10:00:00Z"
}
```
