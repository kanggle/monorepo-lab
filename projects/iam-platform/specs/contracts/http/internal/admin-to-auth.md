# Internal HTTP Contract: admin-service → auth-service

admin-service가 운영자 명령으로 auth-service에 강제 로그아웃 / refresh token revoke를 요청한다.

**호출 방향**: admin-service (client) → auth-service (server)
**노출 경로**: `/internal/auth/*`
**인증**: mTLS 또는 내부 서비스 토큰

---

## POST /internal/auth/accounts/{accountId}/force-logout

특정 계정의 모든 세션 강제 종료. 해당 account의 모든 refresh_tokens를 revoke하고 `refresh:invalidate-all:{account_id}` Redis 키를 설정한다.

**Path Parameters**:

| 파라미터 | 타입 | 설명 |
|---|---|---|
| `accountId` | string (UUID) | 대상 계정 |

**Headers**:
- `Idempotency-Key: {admin_action_request_id}` (필수)
- `X-Operator-ID: {operator_id}` (감사 추적용)

**Request**:
```json
{
  "reason": "string (운영자 사유)",
  "operatorId": "string"
}
```

**Response 200**:
```json
{
  "accountId": "string",
  "revokedTokenCount": 5,
  "revokedAt": "2026-04-12T10:00:00Z"
}
```

**Response 200 (이미 revoke됨 — 멱등)**:
```json
{
  "accountId": "string",
  "revokedTokenCount": 0,
  "revokedAt": "2026-04-12T10:00:00Z"
}
```

**Response 404**:
```json
{
  "code": "ACCOUNT_NOT_FOUND",
  "message": "No refresh tokens found for this account",
  "timestamp": "2026-04-12T10:00:00Z"
}
```

**Side Effects**:
- `refresh_tokens` 테이블에서 해당 account_id의 모든 row `revoked=TRUE`
- `refresh:invalidate-all:{account_id}` Redis SET (TTL = refresh token 최대 수명)
- `auth.token.refreshed` 이벤트 발행하지 않음 (이것은 정상 rotation이 아닌 강제 revoke)

---

> **TASK-MONO-299 (ADR-MONO-040 Phase 3 part B 2026-06-18)** — Phase-2 의 `GET /internal/auth/credentials/{accountId}/email` 엔드포인트(account_id → email, login-time exchange 의 DUAL-KEY 레거시 fallback 용)는 **제거**되었다. 운영자 해석이 account_id 단독으로 전환되어(`admin_operators.oidc_subject` 가 part A 로 account_id backfill 됨) 더 이상 email fallback 키가 필요 없다. 아래의 **역방향** `POST .../account-id-by-email`(email → account_id, part A backfill 도구)은 **유지**된다.

---

## POST /internal/auth/credentials/account-id-by-email

**TASK-MONO-298 (ADR-MONO-040 Phase 3 part A)** — 운영자의 로그인 **email** 로부터 `account_id` 를 해석한다. `admin_operators.oidc_subject` 를 email → account_id 로 1회 backfill 하기 위한 매핑 조회가 목적이다 (Phase-3 end-state 키).

> **왜 POST + body 인가** — `email` 은 `confidential` PII 이므로 path/query param 으로 URL·access-log 에 남기면 안 된다(Phase-2 "no PII in query logs" 규율). body 에 `tenantId` 도 함께 싣는다 — `credentials.email` 은 **tenant 별로만** unique 하므로(`uk_credentials_tenant_email`, V0007) global email 조회는 다른 tenant 의 account 로 잘못 해석되어 운영자를 mis-authorize 할 수 있다.

**Request**:
```json
{
  "email": "operator@example.com",
  "tenantId": "acme-corp"
}
```

| 필드 | 타입 | 설명 |
|---|---|---|
| `email` | string | 운영자 로그인 email (서버사이드 정규화: trim + lower-case). 필수 |
| `tenantId` | string | 운영자 tenant 스코프(`admin_operators.tenant_id`). SUPER_ADMIN 은 `'*'` sentinel — credential 도 `'*'` 로 시드됨. 필수 |

**Response 200** (항상 200 — fail-soft 경계):
```json
{
  "accountId": "01928c4a-7e9f-7c00-9a40-d2b1f5e8c200"
}
```

| 필드 | 타입 | 설명 |
|---|---|---|
| `accountId` | string \| null | `(tenantId, email)` 스코프로 해석된 `credentials.account_id`. tenant 매칭 부재 시 cross-tenant **명확성**(global unambiguity) 검사로 fallback: 정확히 1건이면 해석, 2건 이상(모호) 또는 0건이면 `null`(fail-soft — 잘못된 account_id 를 절대 쓰지 않음) |

**Side Effect**: 없음 (read-only).

> **Tenant scoping (CRITICAL)** — `(tenantId, email)` 복합 unique 키로 조회한다. tenant miss 시 `findAllByEmail` 로 **모호성만 판정**하며, 2건 이상이면 `null` 로 fail-soft(다른 tenant 의 account 로 잘못 해석하지 않음). 잘못된 `oidc_subject` 는 운영자를 mis-authorize 하므로 이 스코핑이 정확성의 핵심이다.

> **Caller fail-soft** (admin-service backfill 측) — 호출 실패(타임아웃·5xx·circuit-open·IO 모두) 또는 `accountId=null` → 해당 운영자의 `oidc_subject` 를 **변경하지 않고** 다음 실행에서 재시도한다(RETAINED email fallback 으로 계속 해석 가능). `AuthServiceClient.resolveOperatorAccountId` 가 예외를 삼키고 `Optional.empty()` 를 반환한다.

---

## POST /internal/auth/sessions/{jti}/revoke

특정 세션(refresh token) 하나만 revoke.

**Path Parameters**:

| 파라미터 | 타입 | 설명 |
|---|---|---|
| `jti` | string (UUID) | 대상 refresh token의 JWT ID |

**Headers**:
- `Idempotency-Key: {admin_action_request_id}`
- `X-Operator-ID: {operator_id}`

**Request**:
```json
{
  "reason": "string",
  "operatorId": "string"
}
```

**Response 200**:
```json
{
  "jti": "string",
  "accountId": "string",
  "revoked": true,
  "revokedAt": "2026-04-12T10:00:00Z"
}
```

**Response 404**: 해당 jti의 refresh token 미존재

---

## Caller Constraints (admin-service 측)

- 타임아웃: 연결 3s, 읽기 10s
- 재시도: 2회 (지수 백오프). 404는 재시도 금지
- Circuit breaker: auth-service 장애 시 open → admin에게 502 반환
- Idempotency-Key 필수: 같은 request_id로 재시도 시 멱등 응답
