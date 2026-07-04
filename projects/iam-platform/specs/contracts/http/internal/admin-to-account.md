# Internal HTTP Contract: admin-service → account-service

admin-service가 운영자 명령으로 account-service에 계정 상태 변경(lock/unlock/delete)을 요청한다.

**호출 방향**: admin-service (client) → account-service (server)
**노출 경로**: `/internal/accounts/*`
**인증** (TASK-BE-318b 호출측 / TASK-BE-319b 수신측): `Authorization: Bearer <IAM client_credentials JWT>` — admin-service 가 `admin-service-client` 로 IAM `/oauth2/token` 에서 발급받아 첨부하고, account-service 가 JWKS 서명 + issuer 로 검증한다. 정적 `X-Internal-Token` 은 제거됨.

---

## Tenant Confinement — `X-Tenant-Id` (TASK-BE-467)

모든 **변이(mutation) 엔드포인트** (`/lock`, `/unlock`, `/delete`, `/gdpr-delete`, `/export`) 는 선택적 `X-Tenant-Id` 헤더로 대상 계정을 **행위자의 활성 테넌트**에 가둔다. 이는 읽기 경로(`GET /internal/accounts` 의 `tenantId` 쿼리, TASK-BE-357)와 동일한 자세이며, 변이 경로를 읽기 경로와 **테넌트 패리티**로 맞춘다.

- **헤더 존재 + 구체 slug**: account-service 는 `findById(TenantId.of(header), accountId)` 로 조회한다. 대상 계정이 **다른 테넌트**에 있으면 tenant-scoped 조회가 empty 를 반환 → **`404 ACCOUNT_NOT_FOUND`** (enumeration-safe: 타 테넌트 존재를 확인해 주는 403 을 절대 반환하지 않는다). 계정은 변이되지 않는다.
- **헤더 부재 OR `'*'` (SUPER_ADMIN 플랫폼 스코프)**: `fan-platform` 기본값으로 폴백한다 — BE-467 이전의 하드핀과 **byte-identical (net-zero)**. 현재 유일 보유자인 SUPER_ADMIN(`'*'`) 및 헤더를 생략한 호출자는 오늘의 동작을 그대로 유지한다.

admin-service 는 `QueryTenantScopeGate` (읽기 경로와 공유) 로 행위자의 활성 테넌트를 해소해 이 헤더를 스탬프한다. out-of-scope 테넌트 요청은 account-service 도달 전에 admin-service 에서 `403 TENANT_SCOPE_DENIED` 로 차단된다 (best-effort DENIED `admin_actions` row). account-service 측 `X-Tenant-Id` 처리는 defense-in-depth 이며, 새로운 cross-tenant finder 를 추가하지 않는다.

---

## GET /internal/accounts

테넌트 스코프 계정 목록 페이지네이션 조회. admin-service가 `account.read` 권한 보유 운영자의 요청을 대리하여 호출한다.

**Query Parameters**:

| 파라미터 | 타입 | 설명 |
|---|---|---|
| `tenantId` | string (**required**, TASK-BE-357) | 조회 대상 테넌트. account-service는 이 값으로만 필터한다 (effective-scope 게이트는 admin-service가 호출 전 수행 — lock/unlock 과 동일한 internal-trust 자세). `*` (SUPER_ADMIN 전용, admin-service에서 게이트됨) → 전 테넌트 목록. 누락/공백 → `400 VALIDATION_ERROR` (암묵적 cross-tenant 스캔 금지, fail-closed). |
| `status` | enum (optional, TASK-BE-475) | 계정 상태 필터 `ACTIVE`/`LOCKED`/`DORMANT`/`DELETED`. 미지정 → 전체 상태. admin-service가 허용 목록을 이미 검증하므로 정상 흐름에서는 유효값만 도달하나, account-service도 fail-closed 로 파싱한다(허용 외 → `400 VALIDATION_ERROR`). `"*"` 전 테넌트 분기 포함. `email` 단건 조회 시 무시. |
| `page` | int (default 0) | 페이지 번호 |
| `size` | int (default 20, max 100) | 페이지 크기 |

**Response 200**:
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

**Errors**: 400 `VALIDATION_ERROR` (size > 100, 또는 `tenantId` 누락/공백)

---

## GET /internal/accounts?email=

테넌트 내 이메일 단건 조회. TASK-BE-357 이전에는 `tenant_id='fan-platform'` 에 하드코딩돼 있어 다른 테넌트(예: ecommerce) 계정이 이메일로 검색되지 않았다 — 이제 `tenantId` 로 스코프된다.

**Query Parameters**:

| 파라미터 | 타입 | 설명 |
|---|---|---|
| `email` | string (required) | 조회할 이메일 (정확 일치 — `(tenant_id, email)` 유니크 인덱스, 부분/LIKE 검색 아님) |
| `tenantId` | string (**required**, TASK-BE-357) | 조회 대상 테넌트. 특정 테넌트 → 해당 테넌트 내 정확 일치(0 또는 1행). `*` (SUPER_ADMIN 전용) → 전 테넌트에서 동일 이메일 매칭(테넌트마다 별도 행이 있을 수 있어 0..N행). 누락/공백 → `400 VALIDATION_ERROR`. |

**Response 200** (특정 테넌트 단건 매칭):
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
  "totalElements": 1,
  "page": 0,
  "size": 20,
  "totalPages": 1
}
```

**Errors**: 400 `VALIDATION_ERROR` (`tenantId` 누락/공백)

---

## POST /internal/accounts/{accountId}/lock

운영자에 의한 계정 잠금.

**Path Parameters**:

| 파라미터 | 타입 | 설명 |
|---|---|---|
| `accountId` | string (UUID) | 잠금 대상 |

**Headers**:
- `Idempotency-Key: {admin_action_request_id}` (필수)
- `X-Operator-ID: {operator_id}`
- `X-Tenant-Id: {active_tenant}` (선택, TASK-BE-467 — 부재/`'*'` → `fan-platform` 기본; [Tenant Confinement](#tenant-confinement--x-tenant-id-task-be-467) 참조)

**Request**:
```json
{
  "reason": "ADMIN_LOCK",
  "operatorId": "string",
  "ticketId": "string (optional)"
}
```

**Response 200**:
```json
{
  "accountId": "string",
  "previousStatus": "ACTIVE",
  "currentStatus": "LOCKED",
  "lockedAt": "2026-04-12T10:00:00Z"
}
```

**Errors**: 409 `STATE_TRANSITION_INVALID` (이미 LOCKED/DELETED), 404 `ACCOUNT_NOT_FOUND` (존재하지 않거나 **cross-tenant** 대상 — `X-Tenant-Id` ≠ 계정 테넌트, BE-467)

**Note**: security-to-account의 lock과 같은 엔드포인트를 공유하되, `reason` 필드로 구분 (`ADMIN_LOCK` vs `AUTO_DETECT`). Idempotency-Key 네임스페이스는 다름.

---

## POST /internal/accounts/{accountId}/unlock

운영자에 의한 계정 잠금 해제.

**Headers**: Idempotency-Key + X-Operator-ID + `X-Tenant-Id` (선택, BE-467)

**Request**:
```json
{
  "reason": "ADMIN_UNLOCK",
  "operatorId": "string",
  "ticketId": "string (optional)"
}
```

**Response 200**:
```json
{
  "accountId": "string",
  "previousStatus": "LOCKED",
  "currentStatus": "ACTIVE",
  "unlockedAt": "2026-04-12T10:00:00Z"
}
```

**Errors**: 409 `STATE_TRANSITION_INVALID` (LOCKED가 아닌 상태), 404 `ACCOUNT_NOT_FOUND` (cross-tenant 대상 포함, BE-467)

---

## POST /internal/accounts/{accountId}/delete

운영자에 의한 강제 삭제 (유예 진입).

**Headers**: Idempotency-Key + X-Operator-ID + `X-Tenant-Id` (선택, BE-467)

**Request**:
```json
{
  "reason": "ADMIN_DELETE | REGULATED_DELETION",
  "operatorId": "string",
  "ticketId": "string (optional)"
}
```

**Response 202 Accepted**:
```json
{
  "accountId": "string",
  "previousStatus": "ACTIVE | LOCKED | DORMANT",
  "currentStatus": "DELETED",
  "gracePeriodEndsAt": "2026-05-12T10:00:00Z"
}
```

**Errors**: 409 `STATE_TRANSITION_INVALID` (이미 DELETED), 404 `ACCOUNT_NOT_FOUND` (cross-tenant 대상 포함, BE-467)

---

## GET /internal/accounts/{accountId}/status

계정 상태 조회. admin-service가 명령 전 현재 상태 확인 용도.

auth-to-account.md의 동일 엔드포인트 공유.

---

## POST /internal/accounts/{accountId}/gdpr-delete

GDPR/PIPA 삭제권. 계정 상태를 DELETED로 전이하고 PII를 즉시 마스킹한다.

**Headers**: Idempotency-Key + X-Operator-ID + `X-Tenant-Id` (선택, BE-467)

**Request**:
```json
{
  "reason": "REGULATED_DELETION",
  "operatorId": "string"
}
```

**Response 200**:
```json
{
  "accountId": "string",
  "status": "DELETED",
  "emailHash": "string (SHA-256 hex)",
  "maskedAt": "2026-04-18T10:00:00Z"
}
```

**Errors**: 409 `STATE_TRANSITION_INVALID` (이미 DELETED), 404 `ACCOUNT_NOT_FOUND` (cross-tenant 대상 포함, BE-467)

**Server-side behavior**:
1. AccountStatusMachine.transition() 경유 DELETED 전이
2. 이메일을 SHA-256 해시로 교체 (email_hash 컬럼에 원본 해시 저장)
3. 프로필 PII 필드 NULL 처리 (displayName, phoneNumber, birthDate)
4. deleted_at, masked_at 타임스탬프 기록
5. account.deleted 이벤트 발행 (anonymized=true)

---

## GET /internal/accounts/{accountId}/export

계정 개인 데이터 내보내기.

**Headers**: X-Operator-ID + `X-Tenant-Id` (선택, BE-467)

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

**Errors**: 404 `ACCOUNT_NOT_FOUND` (cross-tenant 대상 포함, BE-467)

---

## Server Constraints (account-service 측)

- 모든 상태 변경은 `AccountStatusMachine.transition()` 경유
- `account_status_history`에 `actor_type=operator`, `actor_id=operatorId`, `reason_code`, `ticket_id` 기록
- 해당 이벤트 발행 (outbox): `account.locked`, `account.unlocked`, `account.deleted`
- Idempotency-Key dedupe: 24시간 TTL

## Caller Constraints (admin-service 측)

- 타임아웃: 연결 3s, 읽기 10s
- 재시도: 2회. 409/404는 재시도 금지
- Circuit breaker 적용
- **감사 기록 먼저**: admin_actions row가 먼저 저장된 후 이 HTTP 호출 수행. 호출 실패 시 admin_actions.outcome=FAILURE로 갱신 (A10 fail-closed)
