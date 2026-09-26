# Event Contract: auth-service

auth-service가 발행하는 모든 Kafka 이벤트. security-service가 primary consumer.

**발행 방식**: Outbox 패턴 — DB 트랜잭션 커밋 후 relay가 Kafka에 발행 ([rules/traits/transactional.md](../../../../../rules/traits/transactional.md) T3)
**파티션 키**: `account_id` (같은 계정의 이벤트 순서 보장)
**IP 마스킹**: 모든 payload의 `ipMasked` 필드는 [specs/services/auth-service/device-session.md](../../services/auth-service/device-session.md#ip-masking-format) "IP Masking Format" 절의 표준을 따른다 (IPv4 `192.168.*.*`, IPv6 `2001:db8:85a3::*`).

---

## Event Envelope (공통)

모든 이벤트는 [libs/java-messaging](../../../../../libs/java-messaging)의 표준 envelope을 따른다:

```json
{
  "eventId": "string (UUID v7)",
  "eventType": "auth.login.attempted",
  "source": "auth-service",
  "occurredAt": "2026-04-12T10:00:00.123Z",
  "schemaVersion": 1,
  "partitionKey": "string (account_id or email_hash)",
  "payload": { ... }
}
```

---

## 로그인 이벤트의 발행 경로 (TASK-BE-599)

`auth.login.attempted` · `auth.login.failed` · `auth.login.succeeded` 의
**살아 있는 발행자는 SAS 브라우저 폼 로그인**이다 — `POST /login` →
`CredentialAuthenticationProvider` → `LoginEventRecorder` (→ `OutboxAuthEventPublisher`).
**TASK-BE-602 부터 소셜 로그인도** 같은 `LoginEventRecorder` 로 발행한다 — 아래 [소셜 로그인 경로](#소셜-로그인-경로-task-be-602) 절.
`LoginUseCase` 도 같은 이벤트를 내는 코드를 갖고 있지만 TASK-BE-398 이후 호출자가 없다.
(2026-08-01 BE-398 ~ BE-599 머지 전까지는 이 세 이벤트의 발행자가 **하나도 없었다**.)

🔴 **`auth.session.created` 는 현재 발행자가 없다.** 폼 로그인 경로는 디바이스 세션을 등록하지
않는다(소유자 결정, BE-599 AC-0 ⓒ 철회 — 브라우저 폼이 기기 fingerprint 를 보내지 않아
[device-session.md D3](../../services/auth-service/device-session.md) 에 따라 매 로그인이 «새 기기» 가
되기 때문). 이 이벤트를 내는 코드는 호출자 없는 `LoginUseCase` 에만 있다. 되살리는 조건 = 안정적인
기기 식별 쿠키가 생길 때.

이 경로의 규칙 — 스키마는 아래 각 절 그대로이고, 이 절은 «이 발행자가 필드를 어떻게 채우는가» 다:

| 항목 | 폼 로그인 경로의 값 |
|---|---|
| 발행 순서 | 자격 조회 → `attempted` → (계정 상태 조회 — TASK-BE-600) → (비밀번호 불일치) `failed` / (비밀번호 일치 + 비-ACTIVE) `failed` / (성공) `succeeded`. 상태 조회 **실패**는 `failed` 없이 로그인 거부(fail-closed — `attempted` 만 남는다). 디바이스 세션 등록 · `session.created` 없음 |
| `accountId` (attempted/failed) | 자격을 **찾았으면 채운다** — 비밀번호 불일치도 포함(VelocityRule 은 `accountId` 없는 실패를 세지 않는다). 없는 이메일 · 테넌트 모호 → `null` |
| `tenantId` | 자격을 찾았으면 **그 계정의 테넌트**(크로스-테넌트 폴백으로 찾았어도 마찬가지 — 시작 client 의 테넌트가 아니다). 못 찾았으면 시작 OIDC client 의 테넌트, 그것도 없으면 `fan-platform` |
| `failureReason` | `CREDENTIALS_INVALID`(없는 이메일 · 비밀번호 불일치) 또는 `LOGIN_TENANT_AMBIGUOUS`. **`RATE_LIMITED` 는 이 경로에서 나오지 않는다** — rate-limit 을 적용하지 않기 때문(소유자 결정, BE-599 AC-0 ⓑ 미채택: 공유 데모 계정이 N회 실패 뒤 막히면 안 된다). **TASK-BE-600 부터 `ACCOUNT_LOCKED` · `ACCOUNT_DORMANT` · `ACCOUNT_DELETED` 가 나온다** — 비밀번호가 **맞고** 계정 상태가 그것일 때만(`accountId` 채움). 비밀번호가 틀리면 상태와 무관하게 `CREDENTIALS_INVALID`(VelocityRule 에 가는 비밀번호 실패는 그대로다). 계약 밖 상태 값이면 `failed` 를 내지 않는다(enum 에 없는 값을 만들지 않는다). HTTP 응답은 이 값들과 무관하게 모두 같은 `/login?error` 다(소유자 결정, BE-600 AC-1 ⓐ) |
| `failCount` | 항상 `0` — auth-service 측 실패 카운터가 없다(같은 이유). security-service VelocityRule 은 이 필드를 읽지 않고 자체 카운터로 센다 |
| `sessionJti` (succeeded) | `null` — 비밀번호 검증 시점에는 refresh token 이 아직 없다(SAS 가 이후 `/oauth2/token` 에서 발급) |
| `deviceId` / `isNewDevice` (succeeded) | 둘 다 `null` — 디바이스 세션을 등록하지 않으므로. 두 필드는 아래 `auth.login.succeeded` 절에서 **optional·additive** 이고 `null` 은 «알 수 없음(legacy)» 으로 정의돼 있어 계약에 맞다. 소비자(DeviceChangeRule)는 그때 `deviceFingerprint` 폴백으로 가는데, 브라우저 폼은 `X-Device-Fingerprint` 를 보내지 않아 `deviceFingerprint` 도 `null` 이므로 **규칙이 발화하지 않는다**(`DeviceChangeRule.java` 의 `fp == null \|\| fp.isBlank()` → NONE, 테스트 `DeviceChangeRuleTest.formLoginShape_noDeviceSignal_doesNotFire`) |
| `ipMasked` · `userAgentFamily` · `geoCountry` | 다른 auth-service 진입점과 같은 `SessionContexts.fromRequest` — IP 는 `request.getRemoteAddr()`. 코드는 `X-Forwarded-For` 를 직접 읽지 않는다. 🔴 `application.yml` 에는 `server.forward-headers-strategy` 가 **없어서** 프록시 뒤라면 프록시의 주소가 마스킹된다. 데모 overlay(`infra/demo/iam-traefik.override.yml`)는 `SERVER_FORWARD_HEADERS_STRATEGY: FRAMEWORK` 를 켜므로, 거기서는 Spring `ForwardedHeaderFilter` 가 `X-Forwarded-For` 첫 값을 `getRemoteAddr()` 로 돌려준다(spring-web 6.2.1 바이트코드로 확인 — 실제 값은 라이브 창에서 판정, BE-599 AC-4). `geoCountry` 는 `X-Geo-Country` 헤더, 없으면 `XX` |
| 실패 격리 | 이벤트 쓰기가 실패해도 **로그인 결과는 바뀌지 않는다**(텔레메트리) |
| 입력이 비어 있음 | 이메일/비밀번호 공백 제출은 로그인 시도로 치지 않는다 — 이벤트 없음 |

### 소셜 로그인 경로 (TASK-BE-602)

**TASK-BE-602 부터 소셜 로그인도 발행한다** — `GET /login/oauth/{provider}/callback` → `OAuthLoginUseCase.resolveBrowserLogin`
→ `LoginEventRecorder`(폼과 **같은** 레코더). 스키마 변경 없음 — 새 enum 값은 `loginMethod` 의 `OAUTH_NAVER` 하나(아래 `auth.login.succeeded` 절).

| 항목 | 소셜 경로의 값 |
|---|---|
| 발행 조건 | account-service `status-with-tenant` 가 **200 으로 답한 뒤에만** 낸다 — 그 응답이 계정의 실제 테넌트를 주는 유일한 출처라서다([auth-to-account.md](../http/internal/auth-to-account.md#get-internalaccountsaccountidstatus-with-tenant), 소유자 결정 TASK-BE-602 AC-0). 그 앞 단계의 실패(state 불일치 · 공급자 오류 · 이메일 미제공 · `socialSignup` 실패)와 **조회 실패**(fail-closed)· **404**(규칙 미적용 → 통과)는 이벤트를 **내지 않는다** — 테넌트를 모르는 채로 내면 시작 client 의 테넌트를 쓰게 되는데 그것이 BE-507 이전 계정에서 틀린 값이다(VELOCITY 가 틀린 테넌트 키로 센다). 필수 `tenantId` 를 추측으로 채우지 않는다 |
| 발행 순서 | `attempted` → (ACTIVE) `succeeded` / (LOCKED · DORMANT · DELETED) `failed`. 계약 밖 상태 값이면 `failed` 없이 거부(`attempted` 만 — 폼과 같은 규칙). 신원 행 upsert 가 실패하면(DB) `attempted` 만 남는다 |
| `accountId` | 해소된 계정(신원 행 또는 `socialSignup` 응답) — 항상 채운다 |
| `emailHash` | 공급자가 준 이메일의 해시(`SHA256[:10]`, 폼과 같은 함수). 이 단계에서는 이메일이 **항상 있다**(이메일 없는 콜백은 그 전에 거부되고 이벤트가 없다) ⇒ 필수 필드 규칙 그대로, 선택화하지 않는다 |
| `tenantId` | `status-with-tenant` 응답의 `tenantId` = **계정 행의 테넌트**. 시작 client 의 테넌트가 **아니다**(발급 토큰의 `tenant_id` 와 다를 수 있다 — BE-507 이전 계정) |
| `failureReason` | `ACCOUNT_LOCKED` · `ACCOUNT_DORMANT` · `ACCOUNT_DELETED` 만(기존 enum 값). 소셜에는 비밀번호가 없어 `CREDENTIALS_INVALID` 가 나오지 않는다 |
| `failCount` · `sessionJti` · `deviceId` · `isNewDevice` | 폼과 같다(`0` · `null` · `null` · `null`) |
| `loginMethod` (succeeded) | `OAUTH_<PROVIDER>` — `OAUTH_GOOGLE` · `OAUTH_KAKAO` · `OAUTH_MICROSOFT` · `OAUTH_NAVER` |
| 실패 격리 | 폼과 같다 — 이벤트 쓰기가 실패해도 로그인 결과는 바뀌지 않는다 |

🔵 소비자 영향(2026-09-25 코드 읽기): security-service `AbstractAuthEventConsumer` 가 검사하는 필드는 `eventId` · `tenantId` 뿐이고
`emailHash` · `loginMethod` 는 읽지 않는다(`AuthEventMapper`). account-service `LoginSucceededConsumer` 는 `accountId` · `tenantId` 로
`findById(tenantId, accountId)` 해 `last_login_succeeded_at` 을 갱신한다 — 계정의 실제 테넌트를 싣기 때문에 스토어 소셜 계정의
마지막 로그인 시각도 갱신된다(시작 client 의 테넌트를 실으면 BE-507 이전 계정은 조용히 no-op 이었을 것).

---

## auth.login.attempted

모든 로그인 시도에 발행 (성공·실패 불문). security-service가 login_history에 기록.

**Topic**: `auth.login.attempted`

**Schema version**: 2 (TASK-BE-248: `tenant_id` required)

**Payload**:
```json
{
  "accountId": "string | null (미존재 이메일이면 null)",
  "emailHash": "string (SHA256[:10])",
  "tenantId": "string (required, 테넌트 컨텍스트. 미확정 이메일이면 known 테넌트 컨텍스트 또는 'fan-platform' 기본값 사용)",
  "ipMasked": "192.168.*.*",
  "userAgentFamily": "Chrome 120",
  "deviceFingerprint": "string (hashed)",
  "geoCountry": "KR",
  "timestamp": "2026-04-12T10:00:00Z"
}
```

**필드 노트** (TASK-BE-248):
- `tenantId`: 항상 required. consumer는 누락 시 DLQ로 라우팅한다.

**Consumers**: security-service

---

## auth.login.failed

로그인 실패 시 발행. attempted와 별도로 발행되며, 실패 원인을 포함.

**Topic**: `auth.login.failed`

**Schema version**: 2 (TASK-BE-248: `tenant_id` required)

**Payload**:
```json
{
  "accountId": "string | null",
  "emailHash": "string",
  "tenantId": "string (required, TASK-BE-248)",
  "failureReason": "CREDENTIALS_INVALID | ACCOUNT_LOCKED | ACCOUNT_DORMANT | ACCOUNT_DELETED | RATE_LIMITED | LOGIN_TENANT_AMBIGUOUS",
  "failCount": 3,
  "ipMasked": "192.168.*.*",
  "userAgentFamily": "Chrome 120",
  "deviceFingerprint": "string",
  "geoCountry": "KR",
  "timestamp": "2026-04-12T10:00:00Z"
}
```

**필드 노트** (TASK-BE-248):
- `tenantId`: 항상 required. consumer는 누락 시 DLQ로 라우팅한다. VelocityRule은 `(tenantId, accountId)` 단위로 카운터를 분리한다.
- `failureReason`: 이 enum의 `CREDENTIALS_INVALID` 값은 **HTTP 응답 code와 별개 계약**이다. 자격 증명 실패의 HTTP code는 `INVALID_CREDENTIALS`로 통일되었으나(TASK-MONO-246, platform-common canonical), 본 `failureReason` enum은 security-service가 소비하는 독립 Kafka 계약이므로 `CREDENTIALS_INVALID`를 유지한다. **두 문자열을 통일하지 말 것** — 동일하게 보여도 다른 네임스페이스다(HTTP 응답 vs 이벤트 enum).

- (TASK-BE-599) 폼 로그인 경로에서 `failureReason` 은 `CREDENTIALS_INVALID` · `LOGIN_TENANT_AMBIGUOUS` 뿐이고 `failCount` 는 항상 `0` 이다(rate-limit 미적용 — 위 «발행 경로» 절). **(TASK-BE-600 갱신)** 여기에 `ACCOUNT_LOCKED` · `ACCOUNT_DORMANT` · `ACCOUNT_DELETED` 가 더해졌다(비밀번호 일치 + 비-ACTIVE). 새 enum 값은 없다 — 셋 다 이미 이 enum 에 있던 값이다. security-service `AuthEventMapper.resolveFailureOutcome` 은 `RATE_LIMITED` 외 모든 값을 `FAILURE` 로 읽는다.

**Consumers**: security-service (VelocityRule 평가)

---

## auth.login.succeeded

로그인 성공 시 발행.

**Topic**: `auth.login.succeeded`

**Schema version**: 2 (TASK-BE-248: `tenant_id` required confirmed)

**Payload**:
```json
{
  "accountId": "string",
  "tenantId": "string (발급된 토큰의 tenant_id. 필수)",
  "ipMasked": "192.168.*.*",
  "userAgentFamily": "Chrome 120",
  "deviceFingerprint": "string",
  "geoCountry": "KR",
  "sessionJti": "string (발급된 refresh token의 jti)",
  "deviceId": "string | null (UUID, device_sessions.device_id — optional, additive)",
  "isNewDevice": "boolean | null (optional, additive)",
  "timestamp": "2026-04-12T10:00:00Z"
}
```

**필드 노트** (TASK-BE-025):
- `deviceId`: 이 로그인에 사용된 `device_sessions.device_id`. auth-service가 발급하는 opaque UUID. 필드 생략/`null`은 레거시 이벤트로 간주.
- `isNewDevice`: `true`면 device_session row가 **이번 로그인 트랜잭션에서 새로 생성**됨. `false`면 기존 active row의 `last_seen_at`만 touch됨. `null`이면 알 수 없음 (legacy) — 소비자는 fingerprint fallback 사용.
- 두 필드 모두 **optional·additive**. 기존 consumer는 필드를 무시해도 정상 동작 (forward-compatible). 소비자는 unknown-field-tolerant 파싱을 유지해야 한다.

**필드 노트** (OAuth Social Login):
- `loginMethod`: 로그인 방식을 나타내는 optional enum 필드. `EMAIL_PASSWORD | OAUTH_GOOGLE | OAUTH_KAKAO | OAUTH_MICROSOFT | OAUTH_NAVER`. 필드 생략 또는 `null`은 `EMAIL_PASSWORD`로 간주 (backward-compatible). 소셜 로그인 시 auth-service가 provider에 맞는 값을 설정한다. (TASK-BE-599 시점에는 소셜 경로가 이 이벤트를 발행하지 않아 관측값이 필드 생략뿐이었다.) **TASK-BE-602**: 소셜 경로가 발행을 시작하며 `OAUTH_<PROVIDER>` 가 실제로 나온다 — `OAUTH_NAVER` 는 이때 추가(TASK-BE-397 이 Naver 를 지원 provider 로 넣을 때 이 enum 이 따라오지 않았다). 폼 로그인은 여전히 필드를 생략한다(=`EMAIL_PASSWORD`). 이 필드는 `auth.login.succeeded` 에만 있다 — `attempted` · `failed` 에는 없다(소셜 실패와 폼 실패를 이벤트로 구별하지 않는다; 필요해지면 additive 로 추가).

**필드 노트** (TASK-BE-599 — 폼 로그인 경로): `sessionJti` · `deviceId` · `isNewDevice` 가 모두 `null` 이다(디바이스 세션 미등록 — 위 «로그인 이벤트의 발행 경로» 절). `isNewDevice=null` 은 이 절의 정의상 «알 수 없음» 이고, fingerprint 도 없어 DeviceChangeRule 은 발화하지 않는다.
- 이 필드는 **additive**. 기존 consumer는 필드를 무시해도 정상 동작 (forward-compatible).

**Consumers**: security-service (GeoAnomalyRule, DeviceChangeRule 평가, login_history 기록). DeviceChangeRule은 `isNewDevice`가 제공되면 이를 authoritative signal로 사용하고, 없으면 `deviceFingerprint` 기반 known-device 비교로 fallback한다.

---

## auth.token.refreshed

Refresh token rotation 성공 시 발행.

**Topic**: `auth.token.refreshed`

**Schema version**: 2 (TASK-BE-248: `tenant_id` required confirmed)

**Payload**:
```json
{
  "accountId": "string",
  "tenantId": "string (토큰의 tenant_id)",
  "previousJti": "string (소비된 토큰)",
  "newJti": "string (새로 발급된 토큰)",
  "ipMasked": "192.168.*.*",
  "deviceFingerprint": "string",
  "timestamp": "2026-04-12T10:00:00Z"
}
```

**필드 노트** (TASK-BE-603):
- `accountId`: **계정 UUID**. SAS `refresh_token` grant 경로(`SasRefreshTokenAuthenticationProvider`)는 SAS 인가의 principal details `account_id` 를 싣는다. 🔴 TASK-BE-603 이전에는 principal name(= **로그인 이메일**, PII)을 실었다 — 필드·스키마 불변, **값의 의미만 바로잡힘**. 소비자(security-service)는 처음부터 UUID 를 전제했다(`login_history.account_id` VARCHAR(36) · `account.deleted` 익명화 · 탐지 규칙 키) — 그래서 이전의 이메일 값은 36자 초과 시 적재 실패, 이하이면 UUID 이력과 갈라진 행이 되었다. 배포 이후 발행분부터 UUID 로 통일된다(과거 행은 이행하지 않음). 같은 교정이 이 경로의 `auth.token.reuse.detected` · `auth.token.tenant.mismatch` · `auth.session.revoked`(재사용 cascade) 에도 적용된다.
- `tenantId` (TASK-BE-604): SAS 경로는 **세션의 로그인 시점 테넌트**(= 토큰의 `tenant_id`, 위 정의 그대로)를 싣는다. 이전엔 client 의 테넌트를 실었다 — 교차 테넌트 로그인 세션(콘솔 client 로 로그인한 소비자 테넌트 자격)에서만 값이 달라진다(`iam` → 그 계정의 테넌트).

**Consumers**: security-service (login_history에 outcome=REFRESH 기록)

---

## auth.token.reuse.detected

이미 rotation된 refresh token의 재사용 탐지. **보안 critical 이벤트**.

**Topic**: `auth.token.reuse.detected`

**Schema version**: 2 (TASK-BE-259: `tenant_id` required)

**Payload**:
```json
{
  "accountId": "string",
  "tenantId": "string (required, 재사용된 refresh token DB row의 tenant_id. 미존재 시 'fan-platform' 기본값)",
  "reusedJti": "string (재사용 시도된 토큰)",
  "originalRotationAt": "2026-04-12T09:50:00Z",
  "reuseAttemptAt": "2026-04-12T10:00:00Z",
  "ipMasked": "192.168.*.*",
  "deviceFingerprint": "string",
  "sessionsRevoked": true,
  "revokedCount": 5
}
```

**필드 노트** (TASK-BE-259):
- `tenantId`: 항상 required. publisher (`AuthEventPublisher.publishTokenReuseDetected`) 는 null/blank 시 `IllegalArgumentException` 을 던진다. consumer (security-service) 는 누락 메시지를 DLQ 로 라우팅하고, per-tenant reuse 카운터(`reuse:{tenantId}:{accountId}`)에 활용한다. 다른 auth-events 와 정합 (TASK-BE-248 시리즈).
- `accountId` (TASK-BE-603): **계정 UUID**. SAS 경로도 principal details `account_id` 를 싣는다(이전엔 로그인 이메일 — 그래서 `TokenReuseRule` 의 자동 잠금 `/internal/accounts/{accountId}/lock` 이 이메일로 호출되었다). `auth.token.refreshed` 필드 노트 참조. `revokedCount` 는 배수 기간 동안 이메일 키 미러 행 폐기분을 포함한다.

**Consumers**: security-service → 즉시 `auto.lock.triggered` 발행 (최고 우선순위)

---

## auth.token.tenant.mismatch

Refresh token rotation 시 제출된 token의 `tenant_id`와 새로 발급할 token의 `tenant_id`가 불일치. 보안 이벤트.

**Topic**: `auth.token.tenant.mismatch`

**Schema version**: 2 (TASK-BE-248: tenant_id fields are inherently required)

**Payload**:
```json
{
  "accountId": "string",
  "submittedTenantId": "string (제출된 refresh token의 tenant_id)",
  "expectedTenantId": "string (새 token의 tenant_id)",
  "reusedJti": "string (문제가 된 refresh token의 jti)",
  "ipMasked": "192.168.*.*",
  "deviceFingerprint": "string",
  "detectedAt": "2026-04-12T10:00:00Z"
}
```

**필드 노트** (TASK-BE-603): `accountId` = 계정 UUID(SAS 경로 포함 — 이전엔 로그인 이메일).

**필드 노트** (TASK-BE-604, 2026-09-26): SAS 경로의 `expectedTenantId` = **세션의 로그인 시점 테넌트**(SAS 인가의 principal details
`tenant_id` = 새 토큰에 실릴 `tenant_id` — 위 정의 그대로), `submittedTenantId` = 미러 행의 테넌트. BE-604 이전의 SAS 경로는
`expectedTenantId` 에 **client 의 테넌트**를 실었다 — 그래서 교차 테넌트 로그인 세션(콘솔 client · 소비자 테넌트 자격)은 refresh
마다 이 이벤트를 냈고, 그 refresh 는 SAS 기본 provider 로 흘러 200 이 되었다(BE-604 AC-0 ③(b)). 지금은 그런 세션이 이 이벤트를
내지 않고, 이 이벤트가 나면 그 refresh 는 실제로 `400 invalid_grant` 로 거부된다. 스키마 불변 — 값의 의미만 정의에 맞춰졌다.

**Consumers**: security-service (최고 우선순위 보안 이벤트)

---

## auth.session.created

신규 device session이 생성될 때 발행 (로그인 성공 경로의 device_session insert 직후, 동일 트랜잭션 outbox).

**Topic**: `auth.session.created`

**Schema version**: 2 (TASK-BE-248: `tenant_id` required)

**Payload**:
```json
{
  "accountId": "string",
  "tenantId": "string (required, TASK-BE-248)",
  "deviceId": "string (UUID v7, device_sessions.device_id)",
  "sessionJti": "string (이 device에 최초 발급된 refresh token의 jti)",
  "deviceFingerprintHash": "string (fingerprint SHA256, 관측용)",
  "userAgentFamily": "Chrome 120",
  "ipMasked": "192.168.*.*",
  "geoCountry": "KR",
  "issuedAt": "2026-04-13T10:00:00Z",
  "evictedDeviceIds": ["string"]
}
```

**필드 노트**:
- `evictedDeviceIds`: concurrent-session policy에 의해 이 로그인과 **동일 트랜잭션**에서 eviction된 이전 device들의 `device_id` 목록. 없으면 빈 배열. 각 evicted device는 별도로 `auth.session.revoked` 이벤트도 발행된다 (reason=`EVICTED_BY_LIMIT`)
- fingerprint 원문은 발행하지 않음. 해시만.
- 🔴 (TASK-BE-599) **현재 발행자 없음.** 이 이벤트를 내는 코드는 호출자 없는 `LoginUseCase`(BE-398 이후)에만 있고, 살아 있는 로그인 경로(SAS 폼 로그인)는 디바이스 세션을 등록하지 않으므로 이 이벤트를 내지 않는다(소유자 결정 AC-0 ⓒ 철회). 되살리는 조건 = 안정적인 기기 식별 쿠키가 생길 때.

**Consumers**: (현재 없음 — 미구현). 설계 의도는 security-service 가 DeviceChangeRule 입력 · login_history device 컬럼 정합성에 쓰는 것이나, **security-service 에 이 토픽의 `@KafkaListener` 는 없다**(TASK-BE-599 확인 2026-09-25: 리스너는 `auth.login.attempted/failed/succeeded` · `auth.token.refreshed/reuse.detected` 뿐). DeviceChangeRule 은 이 이벤트가 아니라 `auth.login.succeeded` 의 `isNewDevice`/`deviceId` 로 판정한다. 토픽 자체는 relay 매핑과 e2e `kafka-init` 목록에 있지만, 현재는 발행자도 소비자도 없다(위 필드 노트).

---

## auth.session.revoked

device session이 revoke될 때 발행. 사용자 명시 revoke, concurrent-session eviction, token reuse cascade, admin 강제 로그아웃 모두 동일 토픽.

**Topic**: `auth.session.revoked`

**Schema version**: 2 (TASK-BE-248: `tenant_id` required)

**Payload**:
```json
{
  "accountId": "string",
  "tenantId": "string (required, TASK-BE-248)",
  "deviceId": "string (UUID v7)",
  "reason": "USER_REQUESTED | EVICTED_BY_LIMIT | TOKEN_REUSE | ADMIN_FORCED | LOGOUT_OTHERS",
  "revokedJtis": ["string"],
  "revokedAt": "2026-04-13T10:00:00Z",
  "actor": {
    "type": "USER | ADMIN | SYSTEM",
    "accountId": "string | null"
  }
}
```

**필드 노트**:
- `revokedJtis`: 해당 device_session에 연결되어 이번 revoke로 `revoked=TRUE` 처리된 `refresh_tokens.jti` 목록 (일반적으로 활성 1개, rotation 이력 포함 시 다수 가능)
- `actor.type`:
  - `USER` — 본인이 `DELETE /api/accounts/me/sessions/*` 호출
  - `ADMIN` — admin-service 경유 강제 revoke
  - `SYSTEM` — eviction, token reuse cascade 등 자동화 경로

**Consumers**: (현재 없음 — 미구현). 설계 의도는 security-service 가 이 이벤트를 소비해 `login_history` 에 `outcome=SESSION_REVOKED` 행을 기록하는 것이나, **현재 그 `@KafkaListener` 는 존재하지 않는다** (TASK-BE-513 재측정 확인 2026-07-16). `login_history` 는 `auth.login.attempted/failed/succeeded` + `auth.token.refreshed/reuse.detected` 로만 채워진다. 이 소비를 실제로 구현하려면 별도 후속(기능)으로 분리한다 — 계약이 없는 소비를 있는 것처럼 선언하지 않는다.

---

## Consumer Rules

- **멱등 처리 필수**: `eventId`(UUID v7) 기반 dedupe. Redis + MySQL 이중 방어 (T8)
- **순서 보장**: 같은 `account_id`의 이벤트는 같은 파티션에 도착. 교차 계정 순서 보장은 하지 않음
- **schema tolerance**: 알 수 없는 필드는 무시 (forward-compatible). `schemaVersion`이 지원 범위 밖이면 DLQ로 이관
- **DLQ**: `<topic>.dlq`. 3회 지수 백오프 재시도 후 이관 ([rules/traits/integration-heavy.md](../../../../../rules/traits/integration-heavy.md) I5)
- **trace propagation**: envelope 또는 Kafka 헤더의 `traceparent`를 MDC로 복원
