# Event Contract: account-service

account-service가 발행하는 Kafka 이벤트. 계정 생성 및 상태 변경 알림.

**발행 방식**: Outbox 패턴 (T3)
**파티션 키**: `account_id`

---

## Event Envelope

[auth-events.md](auth-events.md)와 동일한 표준 envelope.

---

## account.created

회원가입 완료 시 발행.

**Topic**: `account.created`

**Schema version**: 2 (TASK-BE-228: `tenant_id` 필드 추가)

**Payload**:
```json
{
  "accountId": "string (UUID)",
  "tenantId": "string (slug, e.g. 'fan-platform')",
  "emailHash": "string (SHA256[:10], PII 마스킹)",
  "status": "ACTIVE",
  "locale": "ko-KR",
  "createdAt": "2026-04-12T10:00:00Z"
}
```

**Consumers**: auth-service (credential 초기화 확인, 선택), 미래 서비스 (환영 이메일 등)

---

## account.status.changed

계정 상태 전이 시 발행. 모든 전이에 대한 일반 이벤트.

**Topic**: `account.status.changed`

**Schema version**: 2 (TASK-BE-228: `tenant_id` 필드 추가)

**Payload**:
```json
{
  "accountId": "string",
  "tenantId": "string (slug, e.g. 'fan-platform')",
  "previousStatus": "ACTIVE",
  "currentStatus": "LOCKED",
  "reasonCode": "ADMIN_LOCK | AUTO_DETECT | USER_REQUEST | DORMANT_365D | REGULATED_DELETION | ADMIN_UNLOCK | USER_RECOVERY",
  "actorType": "user | operator | system",
  "actorId": "string | null",
  "occurredAt": "2026-04-12T10:00:00Z"
}
```

**Consumers**: auth-service (세션 무효화 판단), security-service (이력 보강)

---

## tenant.subscription.changed

테넌트↔도메인 구독(`tenant_domain_subscription`)의 생명주기 상태 전이 시 발행 (ADR-MONO-023 D4). 구독 생성(`subscribe`)·정지(`suspend`)·재개(`resume`)·해지(`cancel`) 모든 mutation 에서 발행되는 일반 이벤트.

**Topic**: `tenant.subscription.changed`

**Schema version**: 1 (신규 — ADR-MONO-023 step 2)

**Aggregate / 파티션 키**: 이 이벤트는 account 가 아니라 **구독** aggregate 이다. outbox `aggregate_type = "TenantDomainSubscription"`, `aggregate_id = "<tenantId>:<domainKey>"` (account_id 파티션 키 관례 비적용 — 본 이벤트 한정 예외).

**Payload**:
```json
{
  "eventId": "string (UUID v7)",
  "tenantId": "string (slug, e.g. 'acme-corp')",
  "domainKey": "string (catalog key: wms|scm|erp|finance)",
  "previousStatus": "PENDING | ACTIVE | SUSPENDED | null",
  "currentStatus": "PENDING | ACTIVE | SUSPENDED | CANCELLED",
  "reason": "string | null (운영자 사유, 선택)",
  "actorType": "operator | system",
  "actorId": "string | null",
  "occurredAt": "2026-06-10T10:00:00Z"
}
```

> **`previousStatus = null`**: 신규 `subscribe`(생성) 시 — 이전 상태가 없음.
> **평면 분리 (ADR-023 D2)**: 이 이벤트는 **entitlement 평면**의 변경만 알린다. consumer 는 이 이벤트로 IAM 평면(operator 할당·RBAC)을 변경해서는 안 된다 — suspend/cancel 시에도 할당·RBAC 는 보존된다(GCP billing↔IAM parity). 카탈로그(ADR-019 D4)와 `entitled_domains`(ADR-019 D5)는 ACTIVE 만 투영하므로, 비-ACTIVE 전이는 다음 카탈로그 읽기/토큰 발급 시 자연 반영된다(별도 wiring 불요).

**Consumers**: (현재 필수 consumer 없음 — fire-and-forget) console cache 무효화·미래 billing/notification 등 비동기 소비자용. 발행 자체가 변경의 durable 기록이며, 운영자 행위 감사는 admin-service(`subscription.manage`) 측에서 별도 기록(ADR-023 D3).

---

## account.locked

상태가 LOCKED로 전이될 때 발행. `status.changed`와 동시 발행 (특화 이벤트).

**Topic**: `account.locked`

**Schema version**: 2 (TASK-BE-228: `tenant_id` 필드 추가)

**Payload**:
```json
{
  "eventId": "string (UUID v7)",
  "accountId": "string",
  "tenantId": "string (slug, e.g. 'fan-platform')",
  "reasonCode": "ADMIN_LOCK | AUTO_DETECT | PASSWORD_FAILURE_THRESHOLD",
  "actorType": "operator | system",
  "actorId": "string | null",
  "lockedAt": "2026-04-12T10:00:00Z"
}
```

**Consumer 해석 규칙** (TASK-BE-041b, TASK-BE-041b-fix):
- `eventId` → security-service `account_lock_history.event_id` (멱등 키). 누락 시 consumer는 메시지를 DLQ로 라우팅한다 (invalid message).
- `reasonCode` → security-service `account_lock_history.reason`
- `actorId` → `locked_by` (누락 시 `00000000-0000-0000-0000-000000000000` system 관례값)
- `actorType=operator` → `source=admin`, `actorType=system` → `source=system`
- payload의 대체 필드 `reason`, `lockedBy`, `source`도 허용 (forward compatibility)
- `tenant_id`는 additive field — 기존 컨슈머는 무시해도 무방 (forward-compatible)

**Consumers**: auth-service (해당 계정의 **기존 세션 폐기** — TASK-BE-601), security-service (`account_lock_history` append-only 이력 적재)

> **정정 (TASK-BE-601, 2026-09-25)**: 이 줄은 오래전부터 auth-service 를 소비자로 적어 두었지만 **소비자 코드는 없었다**
> (`@KafkaListener` 0). 잠금 뒤 «새 로그인 차단» 은 이 이벤트가 아니라 로그인 시점의 상태 조회가 한다(TASK-BE-600).
> 이 이벤트로 auth-service 가 하는 일은 **잠금 전에 받은 세션을 끊는 것**이다.

**auth-service 소비 규칙** (TASK-BE-601 · `AccountLockedConsumer` → `RevokeSessionsOnAccountLockedUseCase`):
- **group** `auth-service-account-locked` (security-service 그룹과 독립 — 두 서비스가 모든 잠금을 받는다). `auto-offset-reset=earliest`:
  커밋 오프셋이 없을 때(첫 기동 · 오프셋 만료)만 적용된다. `latest` 는 그 창의 잠금을 건너뛰어 잠긴 계정의 세션을 살려 두므로 금지
  (`platform/service-types/event-consumer.md`). 대가(안전한 쪽): 첫 기동 때 보존 기간(compose 7일) 안의 잠금이 재생되어, 그 사이 **해제된** 계정도
  현재 세션을 한 번 잃고 다시 로그인한다.
- **봉투**: flat(루트 필드) — `payload` 래퍼도 허용. `eventId` 필수·UUID(dedupe 키) · `accountId` 필수 · **`tenantId` 필수**(누락 시
  security-service 와 같은 규칙으로 재시도 없이 `account.locked.dlq`). 읽을 수 없는 봉투도 재시도 없이 DLQ. `reasonCode` · `lockedAt` 은 선택(로그 · 지연 메트릭).
  `eventVersion`/`schemaVersion` 이 있으면 1·2 만 받고 그 밖은 DLQ(없으면 현재 = v2). 모르는 필드는 무시한다.
- **동작**: `ForceLogoutUseCase` 의 **net-zero** 경로로 계정의 모든 refresh 를 폐기 — 레거시 `refresh_tokens` 행 + **SAS 인가(`oauth2_authorization`)의
  refresh·access 무효화** + Redis 무효화 마커. 테넌트 한정 오버로드(BE-468)에 `tenantId` 를 넘기지 않는다: 그 한정은 «다른 테넌트 운영자의 행위» 를 막는
  장치이고, 이 이벤트는 account-service 가 자기 계정에 대해 알리는 사실이며 계정 id(UUID)가 계정 하나를 정확히 가리킨다. 넘기면 자격 행이 없는(소셜 전용)
  계정에서 잠금이 **조용한 no-op** 이 된다. SAS 폐기는 인가 principal 의 `account_id` 로 확정해 같은 이메일의 다른 테넌트 계정을 건드리지 않는다.
- **멱등**: `eventId` 당 1회(`processed_events` 에 같은 트랜잭션으로 기록). 재전달은 no-op + `outcome=duplicate` 로 센다 — 두 번째 폐기로 세지 않는다.
  («해제 → 재로그인» 뒤에 옛 잠금 이벤트가 재전달돼 **새 세션**을 끊는 것을 막는 것이 dedupe 의 실제 이유.)
- **실패**: 삼키지 않는다. 폐기 실패 → 트랜잭션 롤백(dedupe 행 포함) → 지수 백오프 3회 → `account.locked.dlq`(파티션은 프로듀서가 선택 — DLQ 는 1 파티션).
  메트릭 `outbox.dlq.size{reason}`(security-service 와 같은 이름).
- 🔵 **수용한 틈 (소유자 결정 2026-09-25)**: 잠금 → 이 소비까지의 전파 지연 동안에는 잠금 전 세션의 refresh 가 **성공할 수 있다**. «refresh 때마다 상태 조회»
  (fail-closed)는 account-service 장애가 모든 활성 세션의 갱신 장애로 번져 기각됐다. 지연은 `auth.account_locked.propagation.lag`(lockedAt → 폐기) 로 본다.
  auth-service 가 내려가 있는 동안의 잠금은 Kafka 가 보관하고 복귀 후 소비된다. 이미 발급된 access token 은 TTL 동안 서명상 유효하다 —
  Redis `access:invalidate-before:{accountId}` 마커를 읽는 iam gateway(`JwtAuthenticationFilter`)는 즉시 거부하고, 마커를 읽지 않는 검증자에게는
  TTL 까지 유효하다. 이 Redis 마커 쓰기는 기존 동작대로 Redis 장애 시 WARN 후 계속한다 — refresh 폐기의 권위는 DB(SAS 인가 · `refresh_tokens`)다.

---

## account.unlocked

잠금 해제 시 발행.

**Topic**: `account.unlocked`

**Schema version**: 2 (TASK-BE-228: `tenant_id` 필드 추가)

**Payload**:
```json
{
  "accountId": "string",
  "tenantId": "string (slug, e.g. 'fan-platform')",
  "reasonCode": "ADMIN_UNLOCK | USER_RECOVERY",
  "actorType": "operator | user",
  "actorId": "string | null",
  "unlockedAt": "2026-04-12T10:00:00Z"
}
```

**Consumers**: auth-service (로그인 제한 해제)

---

## account.deleted

계정 삭제 (유예 진입) 시 발행.

**Topic**: `account.deleted`

**Schema version**: 2 (TASK-BE-228: `tenant_id` 필드 추가)

**Payload**:
```json
{
  "accountId": "string",
  "tenantId": "string (slug, e.g. 'fan-platform')",
  "reasonCode": "USER_REQUEST | ADMIN_DELETE | REGULATED_DELETION",
  "actorType": "user | operator",
  "actorId": "string | null",
  "deletedAt": "2026-04-12T10:00:00Z",
  "gracePeriodEndsAt": "2026-05-12T10:00:00Z",
  "anonymized": false
}
```

유예 종료 후 PII 익명화 완료 시 동일 토픽에 `anonymized: true`로 재발행.

**Consumers**: auth-service (전체 세션 즉시 무효화), security-service (이력 보관 유지, PII는 anonymized 이벤트 이후 마스킹)

### Consumer Obligations (TASK-BE-258)

`account.deleted(anonymized=true)` 이벤트를 수신한 모든 소비 서비스는 자체 보유 PII를 마스킹할 GDPR/PIPA 의무를 진다. 유예 진입(`anonymized=false`) 이벤트는 **마스킹 대상이 아니다** — 유예 종료 후 발행되는 `anonymized=true`만 처리한다.

#### IAM 내부 소비자 의무 표

| 소비자 | 의무 행동 | SLA | 실패 처리 |
|---|---|---|---|
| **security-service** | `login_history`, `suspicious_events`, `account_lock_history` 의 `ip_address(ip_masked)`, `user_agent(user_agent_family)`, `device_fingerprint` 컬럼을 마스킹값으로 UPDATE (`ip_address='0.0.0.0'`, `user_agent='REDACTED'`, `device_fingerprint=SHA256(accountId\|\|salt)` — `salt`는 `app.pii.masking.fingerprint-salt` 로 주입되는 **애플리케이션-수준 고정값**으로 UUID 공간에 대한 pre-image 공격 방지 (TASK-BE-270). 운영 환경은 secret manager 또는 환경변수 override 의무. salt rotation은 기존 마스킹된 row의 hash 일관성을 깨뜨리므로 별도 절차 필요). `tenant_id`, `account_id`는 감사 무결성 보존 (삭제 금지). 마스킹 완료 후 `security.pii.masked` 이벤트를 outbox 로 발행하여 컴플라이언스 audit trail 제공. | 수신 후 24시간 이내 마스킹 완료 | Kafka consumer 실패 → `account.deleted.dlq`. retry 1시간 간격, 5회 초과 시 alert. SLA 24시간은 retry 시간 포함. |
| **admin-service** | `admin_actions.target_id` 는 감사 무결성을 위해 변경하지 않음. 운영자 UI에서 해당 `target_id` 조회 시 `"(deleted user)"` 라벨을 표시. (UI 표시 로직은 admin-service 프레젠테이션 레이어 책임) | 즉시 (UI 렌더링 시점) | UI 표시 실패는 감사 데이터에 영향 없음. |

> **TASK-MONO-394 (2026-07-14)**: `community-service` · `membership-service` 행이 제거되었다 — 두 서비스는 **RETIRED**(iam 의 FROZEN product-layer demo 였고, `fan-platform` 이 같은 도메인을 실제 배포하면서 중복이 되었다). 이 표는 **살아있는 IAM 내부 소비자만** 남긴다. GDPR 삭제 전파의 실제 소비자는 이제 `security-service` 와 `admin-service` 두 곳이다.

#### External 소비자 가이드

WMS, ERP, SCM, MES 등 외부 소비자는 자체 시스템에서 `account.deleted(anonymized=true)` 이벤트를 수신할 때 다음을 수행해야 한다:

- 자기 도메인이 보유한 해당 `accountId`의 PII 필드 (이메일, 이름, 전화번호, IP 등) 를 마스킹 또는 삭제.
- audit/billing row의 FK (`account_id`) 는 보존 가능.
- 마스킹 완료를 증명할 수 있는 내부 audit trail 발행 권장 (IAM의 `security.pii.masked` 패턴 참고).
- SLA: 수신 후 **24시간 이내** 마스킹 완료.
- 컴플라이언스 정기 리뷰에서 이행 여부를 증명할 수 있도록 audit trail 보관.

세부 통합 패턴 및 코드 예시: [specs/features/consumer-integration-guide.md § Phase 5 GDPR downstream](../../features/consumer-integration-guide.md#gdpr-downstream-처리-accountdeleted).
보안 이벤트 audit trail 스펙: [specs/contracts/events/security-events.md § security.pii.masked](security-events.md#securitypiimasked-task-be-258).

---

## account.roles.changed

계정의 역할(role) 목록이 변경될 때 발행. provisioning API 가 role set 을 교체하거나 단건 add/remove 한 경우.

**Topic**: `account.roles.changed`

**Schema version**: 3 (TASK-BE-255: `before_roles`, `after_roles`, `changed_by` 필드 추가)

**Payload**:
```json
{
  "accountId": "string",
  "tenantId": "string (required, TASK-BE-248)",
  "roles": ["WAREHOUSE_ADMIN", "OPERATOR"],
  "beforeRoles": ["WAREHOUSE_ADMIN"],
  "afterRoles": ["WAREHOUSE_ADMIN", "OPERATOR"],
  "changedBy": "sys-wms-backend",
  "actorType": "provisioning_system",
  "actorId": "string | null",
  "occurredAt": "2026-04-12T10:00:00Z"
}
```

**필드 설명** (TASK-BE-255):
- `roles` — backward-compat alias for `afterRoles`. v2 컨슈머가 그대로 동작하도록 유지.
- `beforeRoles` — 변경 직전의 role set (정렬 보장 없음). 컨슈머가 추가/제거 차분을 직접 계산할 수 있게 한다.
- `afterRoles` — 변경 직후의 role set. `roles` 와 동일.
- `changedBy` — 변경을 수행한 운영자 ID 또는 시스템 식별자. provisioning API 의 `operatorId` 가 그대로 들어간다. operatorId 누락 시 호출 테넌트 ID 가 기본값.

**발행 조건**: role set 이 실제로 변경된 경우에만 발행. add/remove 가 멱등적으로 no-op 이면 (이미 존재하는 role 재추가, 미존재 role 제거 시도) 이벤트는 발행되지 않는다.

**Consumers**:
- 기존 v2 consumer (admin-service 권한 변경 감사) — `roles` 필드만 읽고 새 필드는 무시 (forward-compatible).
- 신규 v3 consumer — `beforeRoles` / `afterRoles` 차분으로 added/removed 를 직접 계산. `changedBy` 로 audit chain 강화.

---

## Consumer Rules

[auth-events.md](auth-events.md)의 Consumer Rules와 동일:
- 멱등 처리 (eventId dedupe)
- forward-compatible schema
- DLQ: `<topic>.dlq`, 3회 재시도
- trace propagation

### Additive Change Policy (TASK-BE-228)

`tenant_id` 필드는 **additive** 변경이다. 기존 컨슈머(security-service, auth-service)는 이 필드를 무시해도 정상 동작한다. 신규 컨슈머는 반드시 `tenant_id`를 페이로드에서 읽어 테넌트별 처리를 수행해야 한다.

### Additive Change Policy (TASK-BE-255)

`account.roles.changed` 의 `beforeRoles` / `afterRoles` / `changedBy` 필드는 **additive** 변경 (v2 → v3). 기존 v2 컨슈머는 이 세 필드를 무시해도 정상 동작하며, 기존 `roles` 필드 (= `afterRoles`) 가 그대로 유지된다. 신규 v3 컨슈머는 `beforeRoles`/`afterRoles` 차분을 통해 added/removed 역할을 직접 계산할 수 있다.
