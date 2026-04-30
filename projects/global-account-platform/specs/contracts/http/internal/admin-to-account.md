# Internal HTTP Contract: admin-service → account-service

admin-service가 운영자 명령으로 account-service에 계정 상태 변경(lock/unlock/delete)을 요청한다.

**호출 방향**: admin-service (client) → account-service (server)
**노출 경로**: `/internal/accounts/*`
**인증**: mTLS 또는 내부 서비스 토큰

---

## GET /internal/accounts

전체 계정 목록 페이지네이션 조회. admin-service가 `account.read` 권한 보유 운영자의 요청을 대리하여 호출한다.

**Query Parameters**:

| 파라미터 | 타입 | 설명 |
|---|---|---|
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

**Errors**: 400 `VALIDATION_ERROR` (size > 100)

---

## GET /internal/accounts?email=

이메일로 단건 계정 조회 (기존 동작).

**Query Parameters**: `email` (string, required)

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
  "totalElements": 1,
  "page": 0,
  "size": 20,
  "totalPages": 1
}
```

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

**Errors**: 409 `STATE_TRANSITION_INVALID` (이미 LOCKED/DELETED), 404 `ACCOUNT_NOT_FOUND`

**Note**: security-to-account의 lock과 같은 엔드포인트를 공유하되, `reason` 필드로 구분 (`ADMIN_LOCK` vs `AUTO_DETECT`). Idempotency-Key 네임스페이스는 다름.

---

## POST /internal/accounts/{accountId}/unlock

운영자에 의한 계정 잠금 해제.

**Headers**: Idempotency-Key + X-Operator-ID

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

**Errors**: 409 `STATE_TRANSITION_INVALID` (LOCKED가 아닌 상태), 404 `ACCOUNT_NOT_FOUND`

---

## POST /internal/accounts/{accountId}/delete

운영자에 의한 강제 삭제 (유예 진입).

**Headers**: Idempotency-Key + X-Operator-ID

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

**Errors**: 409 `STATE_TRANSITION_INVALID` (이미 DELETED), 404 `ACCOUNT_NOT_FOUND`

---

## GET /internal/accounts/{accountId}/status

계정 상태 조회. admin-service가 명령 전 현재 상태 확인 용도.

auth-to-account.md의 동일 엔드포인트 공유.

---

## POST /internal/accounts/{accountId}/gdpr-delete

GDPR/PIPA 삭제권. 계정 상태를 DELETED로 전이하고 PII를 즉시 마스킹한다.

**Headers**: Idempotency-Key + X-Operator-ID

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

**Errors**: 409 `STATE_TRANSITION_INVALID` (이미 DELETED), 404 `ACCOUNT_NOT_FOUND`

**Server-side behavior**:
1. AccountStatusMachine.transition() 경유 DELETED 전이
2. 이메일을 SHA-256 해시로 교체 (email_hash 컬럼에 원본 해시 저장)
3. 프로필 PII 필드 NULL 처리 (displayName, phoneNumber, birthDate)
4. deleted_at, masked_at 타임스탬프 기록
5. account.deleted 이벤트 발행 (anonymized=true)

---

## GET /internal/accounts/{accountId}/export

계정 개인 데이터 내보내기.

**Headers**: X-Operator-ID

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

**Errors**: 404 `ACCOUNT_NOT_FOUND`

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
