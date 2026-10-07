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

이메일 소유권 확인. 인증 메일(`POST /signup/resend-verification-email` 이 발송)의 토큰을 검증한다.

**로그인·소비자 이용은 비차단이다**: 이 엔드포인트 호출 여부와 무관하게 계정은 ACTIVE 상태로 로그인·쇼핑·팬 이용이
가능하다. 완료 시 `email_verified_at` 필드가 채워진다.

🔴 **회사 권한이 붙는 쓰기는 이 값을 요구한다** (ADR-MONO-080 D3 · R1, TASK-MONO-770). 풀 계정에 회사 권한이 **붙는 순간**
(지금 존재하는 것: 셀러 구성원 수락 = [internal/consumer-site-roles.md](internal/consumer-site-roles.md) `site-roles:grant`)
`email_verified_at` 이 비어 있으면 `403 EMAIL_NOT_VERIFIED` 로 거절된다. 이미 붙은 권한은 소급해 회수하지 않는다 —
인증은 붙일 때의 증거일 뿐 유지 조건이 아니다(ADR-MONO-080 D5).

**브라우저 경로**: 사람은 이 JSON 엔드포인트를 직접 부르지 않는다. 메일의 링크는 IdP(auth-service) 화면
`GET /verify-email?token=…` 로 가고, 그 화면이 확인 버튼(`POST /verify-email`)으로 이 엔드포인트를 **서버 측에서**
호출한다(`/signup` 프록시와 같은 모양 — [auth-api.md § IdP 브라우저 화면 — 이메일 인증](auth-api.md)). 그래서 IAM
게이트웨이의 public-paths 는 바뀌지 않는다.

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
| 422 | `VERIFICATION_EMAIL_UNDELIVERABLE` | **영구 발송 실패** — 메일 서버가 이 주소를 받지 않는다(주소 형식 오류 · 수신자 거부). 재시도해도 같다 (TASK-MONO-770) |
| 429 | `RATE_LIMITED` | 5분 내 재발송 재시도 |
| 503 | `VERIFICATION_EMAIL_SEND_FAILED` | **일시 발송 실패** — 메일 서버 연결·인증·시간 초과 등. 잠시 뒤 재시도하면 될 수 있다 (TASK-MONO-770) |

**Side Effects**:
- 신규 토큰 (UUID v4) 생성 후 Redis 저장 (`email-verify:{token}` TTL 24h)
- 재발송 레이트 리밋 마커 저장 (`email-verify:rate:{accountId}` TTL 300s, `setIfAbsent`)
- `EmailVerificationNotifier`로 이메일 전송 — 🔴 **TASK-MONO-770 부터 best-effort 가 아니다.** 예전에는 발송 실패를 WARN 으로
  삼키고 204 를 냈다 — 메일이 안 나갔는데 화면은 «보냈다» 고 말하는 상태였고, 인증이 회사 권한의 조건이 된 지금(ADR-MONO-080
  D3) 그 거짓은 «권한을 못 받음» 으로 보인다(ADR-080 § 새로 생기는 위험). 발송이 실패하면:
  - 방금 발급한 토큰을 **지운다**(아무도 받지 못한 토큰을 24시간 살려 둘 이유가 없다).
  - 레이트 리밋 마커를 **되돌린다** — 메일이 나가지 않았으므로 5분을 기다리게 할 이유가 없다(«재시도» 가 바로 가능해야 한다).
  - 실패의 **종류**를 응답한다: 일시 → `503 VERIFICATION_EMAIL_SEND_FAILED`, 영구 → `422 VERIFICATION_EMAIL_UNDELIVERABLE`.
    판별은 어댑터가 한다(주소 형식 오류·수신자 거부 = 영구, 그 밖의 모든 실패 = 일시 — 판정 불가는 일시 쪽이다:
    영구라고 잘못 말하면 될 일을 포기하라고 안내하게 된다, signup.md § 실패의 종류를 구별해 보고한다 와 같은 원칙).
- Redis 장애 시 레이트 리밋만 fail-open — 토큰 저장 실패는 503

**Delivery (TASK-MONO-770 — 구현체는 설정 하나로 고른다, 프로필이 아니다)**:

| `iam.mail.enabled` | 프로필 | 등록되는 `EmailVerificationNotifier` |
|---|---|---|
| `true` | 무관 | **SMTP 어댑터** — 표준 `spring.mail.*`(host · port · username · password · properties)로 아무 SMTP 서버에나 보낸다(데모 = Mailpit, 운영 = 예: AWS SES SMTP 엔드포인트). 공급자별 코드 없음 |
| `false`/미설정 | `prod` 아님 | 로깅 스텁(수신자 마스킹 · 토큰 미기록) |
| `false`/미설정 | `prod` | **없음 → 컨텍스트 기동 실패(fail-fast)** — 운영에서 메일을 조용히 버리는 일이 없게(TASK-BE-236 의 보장 유지) |

- 🔴 **왜 프로필이 아니라 설정인가**: 데모는 IAM 을 `SPRING_PROFILES_ACTIVE=e2e` 로 띄운다(`docker-compose.e2e.yml` → `infra/demo`
  오버라이드). `@Profile("prod")` 어댑터는 데모에서 **영영 돌지 않는다.**
- 메일 본문의 링크 = `{iam.mail.verification-link-base-url}?token=<token>` (IdP 의 `GET /verify-email` 화면).
- 발신 주소 = `iam.mail.from`.
- R4(`rules/traits/regulated.md`): 어댑터는 토큰과 수신 주소 전체를 **로그에 남기지 않는다**(수신자는 마스킹).
  발송 실패 예외의 메시지도 그대로 남기지 않는다 — SMTP 오류 문구는 수신 주소를 담을 수 있다.

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

🔴 **소비자 계정 풀 (`TASK-BE-619`)**: 풀 계정(팬·스토어가 함께 쓰는 IAM 계정)이 사이트 토큰으로 부르면 **풀 계정 하나**가 삭제된다 — 그 사람의 **모든 소비자
사이트**에서(소유자 결정 2026-10-03: 풀 계정 삭제는 본인 또는 플랫폼 관리자). «이 사이트만 그만 쓰기» 는 아래 `DELETE /api/accounts/me/site-membership` 이다.
응답에 `scope: "ACCOUNT"` · `siteTenantId: null` 이 더해진다(내부 `/delete` 와 같은 DTO — [admin-to-account.md](internal/admin-to-account.md)).

---

## DELETE /api/accounts/me/site-membership

**`TASK-BE-619`** — «이 사이트 탈퇴»: 로그인한 **풀 계정**이 토큰의 사이트(게이트웨이 `X-Tenant-Id` = 토큰 `tenant_id`)만 그만 쓴다. 그 사이트 멤버십이 `LEFT`
(`left_by = SELF`)가 되고, 그 사이트의 사이트 역할(`consumer_site_roles`)이 지워진다. **계정 · PII · 다른 사이트 멤버십은 그대로**다. 다음 authorize · refresh 부터 그 사이트
토큰이 발급되지 않는다. 다시 그 사이트에 로그인해 동의 화면에서 «동의» 하면 돌아온다(소유자 결정 2026-10-03 «다시 동의하면 복귀» —
[multi-tenancy.md § 소비자 계정 풀 § 5](../../features/multi-tenancy.md)).

**Auth required**: Yes (`X-Account-Id` · `X-Tenant-Id`, gateway 주입). Request body 없음.

**Response 200**:
```json
{
  "accountId": "string (UUID)",
  "siteTenantId": "ecommerce",
  "membershipStatus": "LEFT",
  "leftBy": "SELF | OPERATOR",
  "leftAt": "2026-10-03T10:00:00Z"
}
```

- **멱등**: 이미 `LEFT` 면 쓰기 없이 지금 행을 답한다. 그 사이트 운영자가 먼저 내보냈다면 `leftBy = OPERATOR` 그대로다(본인 탈퇴가 «복귀 가능» 으로 낮추지 않는다).
- **`TASK-BE-621`**: 그 사이트 운영자가 이 사람을 그 사이트에서 **잠갔다면**(멤버십 `LOCKED`) 쓰기 없이 `membershipStatus = "LOCKED"` · `leftBy = null` 로 답한다 —
  «탈퇴 → 다시 동의» 로 잠금을 벗는 길을 만들지 않는다. (잠긴 사이트로는 토큰이 발급되지 않으므로 이 응답은 이미 받은 access token 이 남아 있을 때만 닿는다.)
- **이벤트 없음** — `account.*` 를 내지 않는다(계정의 수명주기는 바뀌지 않았다).

**Errors**:

| Status | Code | 조건 |
|---|---|---|
| 409 | `SITE_MEMBERSHIP_REQUIRED` | 그 사이트의 멤버십이 없다 — 사이트별 계정(풀이 아님: «탈퇴» 가 곧 `DELETE /api/accounts/me`), 멤버십 행 없음, 소비자 사이트가 아닌 테넌트 |

---

## Common Error Format

```json
{
  "code": "UPPER_SNAKE_CASE",
  "message": "Human-readable (no PII)",
  "timestamp": "2026-04-12T10:00:00Z"
}
```
