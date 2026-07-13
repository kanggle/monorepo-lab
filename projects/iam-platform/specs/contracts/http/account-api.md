# HTTP Contract: account-service (Public API)

모든 엔드포인트는 gateway 경유. base path: `/api/accounts`

> **`X-Tenant-Id` (TASK-BE-507)** — 소비자 표면 전체가 tenant-aware 다. 게이트웨이가 토큰의 `tenant_id` claim 을 이 헤더로 전파하며(BE-230), account-service 는 이를 계정 조회/생성의 스코프로 쓴다. **부재/공백/`*` → `fan-platform`** 이므로 헤더 없는 기존 호출자는 BE-507 이전과 바이트 동일하게 동작한다. 적용 대상: `POST /signup`, `GET /me`, `PATCH /me/profile`, `GET /me/status`, `DELETE /me`, `POST /signup/resend-verification-email`.
>
> 예외 — `POST /signup/verify-email` 은 **토큰 자체가 인증**이라 헤더를 받지 않는다. tenant 는 발급 시점에 토큰에 실려(`email-verify:{token}` → `{tenantId}|{accountId}`) 검증 경로가 스스로 스코프한다.

---

## POST /api/accounts/signup

회원가입. 신규 계정과 프로필을 생성한다.

**Auth required**: No

**Headers** (TASK-BE-507):

| Header | 필수 | 의미 |
|---|---|---|
| `X-Tenant-Id` | 선택 | **계정이 태어날 tenant.** 호출자(auth-service)가 가입을 개시한 **OIDC client** 로부터 유도한 값 (`SavedRequestTenantResolver`) — 예: web-store 클라이언트 → `ecommerce`. **부재/공백/`*` → `fan-platform`** (BE-507 이전 동작과 바이트 동일 ⇒ 헤더 없는 호출자 무손실). 이메일 유일성은 `(tenant_id, email)` 복합 unique 로 **tenant 안에서만** 강제되므로, 같은 이메일이 서로 다른 tenant 에 존재할 수 있다. |

> **왜 body 가 아니라 헤더인가**: tenant 는 사용자가 입력하는 값이 아니라 호출 컨텍스트다. `X-Tenant-Id` 는 이미 게이트웨이가 토큰 claim 으로부터 전파하는 축(BE-230)이며, account-service 의 다른 내부 경로도 같은 헤더를 쓴다(BE-467).

**Request**:
```json
{
  "email": "string (required, email format, unique within the tenant)",
  "password": "string (required, min 8, complexity rule per PasswordPolicy)",
  "displayName": "string (optional, max 100)",
  "locale": "string (optional, default 'ko-KR')",
  "timezone": "string (optional, default 'Asia/Seoul')"
}
```

**Response 201**:
```json
{
  "accountId": "string (UUID)",
  "email": "string",
  "status": "ACTIVE",
  "createdAt": "2026-04-12T10:00:00Z"
}
```

**Errors**:

| Status | Code | 조건 |
|---|---|---|
| 409 | `ACCOUNT_ALREADY_EXISTS` | **해당 tenant 안에서** 이메일 중복 |
| 422 | `VALIDATION_ERROR` | 이메일 형식, 패스워드 복잡도 미달 |
| 429 | `RATE_LIMITED` | 가입 시도 rate limit 초과 |
| 404 | `TENANT_NOT_FOUND` | `X-Tenant-Id` 가 미등록 tenant (TASK-BE-507 — 유령 tenant 로 계정이 태어나지 않는다) |
| 409 | `TENANT_SUSPENDED` | `X-Tenant-Id` 가 SUSPENDED tenant (TASK-BE-507; `GlobalExceptionHandler` 가 이 예외를 409 로 매핑한다 — provisioning 경로와 동일) |
| 503 | `AUTH_SERVICE_UNAVAILABLE` | Authentication service is temporarily unavailable |

**Side Effects**:
- `account.created` 이벤트 발행 (outbox)
- auth-service에 credential 생성 요청 (내부 HTTP 또는 이벤트 — 구현 시 결정)
- `signup:dedup:{email_hash}` Redis 5분 TTL

---

## GET /api/accounts/me

현재 로그인된 사용자의 계정 + 프로필 조회.

**Auth required**: Yes

**Response 200**:
```json
{
  "accountId": "string (UUID)",
  "email": "string",
  "status": "ACTIVE",
  "profile": {
    "displayName": "string | null",
    "phoneNumber": "string | null (masked: 010-****-1234)",
    "birthDate": "string | null (YYYY-MM-DD)",
    "locale": "string",
    "timezone": "string",
    "preferences": {}
  },
  "createdAt": "2026-04-12T10:00:00Z"
}
```

**Errors**:

| Status | Code | 조건 |
|---|---|---|
| 401 | `TOKEN_INVALID` | access token 만료/변조 |
| 404 | `ACCOUNT_NOT_FOUND` | 삭제된 계정 (유예 중이어도 자기 자신 조회는 가능) |

**Note**: `phoneNumber`는 응답에서 **마스킹** ([rules/traits/regulated.md](../../../../../rules/traits/regulated.md) R4). 전문은 반환하지 않음.

---

## PATCH /api/accounts/me/profile

프로필 부분 수정.

**Auth required**: Yes

**Request** (partial update — 포함된 필드만 변경):
```json
{
  "displayName": "string (optional, max 100)",
  "phoneNumber": "string (optional, E.164 format)",
  "birthDate": "string (optional, YYYY-MM-DD)",
  "locale": "string (optional)",
  "timezone": "string (optional)",
  "preferences": {} 
}
```

**Response 200**: 변경된 프로필 전체 (GET /me의 profile 구조와 동일)

**Errors**:

| Status | Code | 조건 |
|---|---|---|
| 401 | `TOKEN_INVALID` | — |
| 409 | `CONFLICT` | 낙관적 락 충돌 (동시 수정) |
| 422 | `VALIDATION_ERROR` | phoneNumber 형식 등 |

**Side Effects**: 없음 (프로필 변경은 이벤트 미발행 — 상태 변경이 아님)

---

## GET /api/accounts/me/status

계정 상태 조회.

**Auth required**: Yes

**Response 200**:
```json
{
  "accountId": "string",
  "status": "ACTIVE | LOCKED | DORMANT | DELETED",
  "statusChangedAt": "2026-04-12T10:00:00Z",
  "reason": "string | null"
}
```

**Errors**:

| Status | Code | 조건 |
|---|---|---|
| 401 | `TOKEN_INVALID` | — |

---

## POST /api/accounts/signup/verify-email

이메일 소유권 확인. 회원가입 후 발송된 이메일 인증 토큰을 검증한다.

비차단 설계: 이 엔드포인트 호출 여부와 무관하게 계정은 ACTIVE 상태로 서비스 이용 가능하다.
완료 시 `email_verified_at` 필드가 채워진다.

**Auth required**: No (토큰 자체가 인증 수단)

**Request**:
```json
{
  "token": "string (required, UUID format)"
}
```

**Response 200**:
```json
{
  "accountId": "string (UUID)",
  "emailVerifiedAt": "2026-04-26T10:00:00Z"
}
```

**Errors**:

| Status | Code | 조건 |
|---|---|---|
| 400 | `TOKEN_EXPIRED_OR_INVALID` | 토큰 만료, 존재하지 않음, 또는 이미 사용됨 |
| 409 | `EMAIL_ALREADY_VERIFIED` | 해당 계정의 이메일이 이미 인증된 상태 |
| 400 | `VALIDATION_ERROR` | token 누락 또는 형식 오류 |

**Side Effects**:
- `accounts.email_verified_at` 갱신 (트랜잭션 내)
- 토큰을 Redis에서 1회용으로 삭제 (verifyEmail commit 이후, best-effort)

---

## POST /api/accounts/signup/resend-verification-email

이메일 인증 메일 재발송. 새 토큰을 발급하여 사용자 이메일로 전송한다.

**Auth required**: Yes (`X-Account-Id` 헤더, gateway 주입)

**Request**: body 없음

**Response 204 No Content**

**Errors**:

| Status | Code | 조건 |
|---|---|---|
| 401 | `TOKEN_INVALID` | `X-Account-Id` 헤더 누락 (gateway 미인증) |
| 404 | `ACCOUNT_NOT_FOUND` | 해당 accountId 의 계정이 없음 |
| 409 | `EMAIL_ALREADY_VERIFIED` | 이미 인증된 이메일 |
| 429 | `RATE_LIMITED` | 5분 내 재발송 재시도 |

**Side Effects**:
- 신규 토큰 (UUID v4) 생성 후 Redis 저장 (`email-verify:{token}` TTL 24h)
- 재발송 레이트 리밋 마커 저장 (`email-verify:rate:{accountId}` TTL 300s, `setIfAbsent`)
- `EmailVerificationNotifier`로 이메일 전송 (best-effort)
- Redis 장애 시 레이트 리밋만 fail-open — 토큰 저장 실패는 503

---

## DELETE /api/accounts/me

계정 삭제 요청 (유예 진입).

**Auth required**: Yes

**Request**:
```json
{
  "password": "string (required, 재인증)",
  "reason": "string (optional, 탈퇴 사유)"
}
```

**Response 202 Accepted**:
```json
{
  "accountId": "string",
  "status": "DELETED",
  "gracePeriodEndsAt": "2026-05-12T10:00:00Z",
  "message": "Account scheduled for deletion. You can recover within the grace period."
}
```

**Errors**:

| Status | Code | 조건 |
|---|---|---|
| 401 | `INVALID_CREDENTIALS` | 패스워드 재인증 실패 |
| 400 | `STATE_TRANSITION_INVALID` | 이미 DELETED 상태 |

**Side Effects**:
- 상태 전이 `→ DELETED` (상태 기계 경유)
- `account.deleted` 이벤트 발행
- auth-service의 모든 세션 무효화 (이벤트 소비)
- 30일 후 PII 익명화 배치 실행

---

## Common Error Format

```json
{
  "code": "UPPER_SNAKE_CASE",
  "message": "Human-readable (no PII)",
  "timestamp": "2026-04-12T10:00:00Z"
}
```
