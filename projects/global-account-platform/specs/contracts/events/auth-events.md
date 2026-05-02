# Event Contract: auth-service

auth-service가 발행하는 모든 Kafka 이벤트. security-service가 primary consumer.

**발행 방식**: Outbox 패턴 — DB 트랜잭션 커밋 후 relay가 Kafka에 발행 ([rules/traits/transactional.md](../../../rules/traits/transactional.md) T3)
**파티션 키**: `account_id` (같은 계정의 이벤트 순서 보장)
**IP 마스킹**: 모든 payload의 `ipMasked` 필드는 [specs/services/auth-service/device-session.md](../../services/auth-service/device-session.md#ip-masking-format) "IP Masking Format" 절의 표준을 따른다 (IPv4 `192.168.*.*`, IPv6 `2001:db8:85a3::*`).

---

## Event Envelope (공통)

모든 이벤트는 [libs/java-messaging](../../../libs/java-messaging)의 표준 envelope을 따른다:

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
- `loginMethod`: 로그인 방식을 나타내는 optional enum 필드. `EMAIL_PASSWORD | OAUTH_GOOGLE | OAUTH_KAKAO | OAUTH_MICROSOFT`. 필드 생략 또는 `null`은 `EMAIL_PASSWORD`로 간주 (backward-compatible). 소셜 로그인 시 auth-service가 provider에 맞는 값을 설정한다.
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

**Consumers**: security-service (DeviceChangeRule 입력, login_history 내 device 컬럼 정합성)

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

**Consumers**: security-service (login_history outcome=SESSION_REVOKED 기록)

---

## Consumer Rules

- **멱등 처리 필수**: `eventId`(UUID v7) 기반 dedupe. Redis + MySQL 이중 방어 (T8)
- **순서 보장**: 같은 `account_id`의 이벤트는 같은 파티션에 도착. 교차 계정 순서 보장은 하지 않음
- **schema tolerance**: 알 수 없는 필드는 무시 (forward-compatible). `schemaVersion`이 지원 범위 밖이면 DLQ로 이관
- **DLQ**: `<topic>.dlq`. 3회 지수 백오프 재시도 후 이관 ([rules/traits/integration-heavy.md](../../../rules/traits/integration-heavy.md) I5)
- **trace propagation**: envelope 또는 Kafka 헤더의 `traceparent`를 MDC로 복원
