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

**Consumers**: auth-service (해당 계정 로그인 즉시 차단), security-service (`account_lock_history` append-only 이력 적재)

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

#### GAP 내부 소비자 의무 표

| 소비자 | 의무 행동 | SLA | 실패 처리 |
|---|---|---|---|
| **security-service** | `login_history`, `suspicious_events`, `account_lock_history` 의 `ip_address(ip_masked)`, `user_agent(user_agent_family)`, `device_fingerprint` 컬럼을 마스킹값으로 UPDATE (`ip_address='0.0.0.0'`, `user_agent='REDACTED'`, `device_fingerprint=SHA256(accountId\|\|salt)` — `salt`는 `app.pii.masking.fingerprint-salt` 로 주입되는 **애플리케이션-수준 고정값**으로 UUID 공간에 대한 pre-image 공격 방지 (TASK-BE-270). 운영 환경은 secret manager 또는 환경변수 override 의무. salt rotation은 기존 마스킹된 row의 hash 일관성을 깨뜨리므로 별도 절차 필요). `tenant_id`, `account_id`는 감사 무결성 보존 (삭제 금지). 마스킹 완료 후 `security.pii.masked` 이벤트를 outbox 로 발행하여 컴플라이언스 audit trail 제공. | 수신 후 24시간 이내 마스킹 완료 | Kafka consumer 실패 → `account.deleted.dlq`. retry 1시간 간격, 5회 초과 시 alert. SLA 24시간은 retry 시간 포함. |
| **community-service** | 사용자 프로필 캐시(있다면) 무효화. 작성 콘텐츠의 `author_name`을 `"(deleted user)"` 로 교체. content 본문은 community-service 자체 정책에 따름 (GAP 강제 범위 외). | 수신 후 24시간 이내 | Kafka consumer 실패 → `account.deleted.dlq`. community-service 자체 retry/alert 정책 적용. |
| **membership-service** | 멤버십 record의 사용자 식별자(이름, 이메일 등) 마스킹. `account_id` FK는 보존 (billing 감사 무결성). | 수신 후 24시간 이내 | Kafka consumer 실패 → `account.deleted.dlq`. membership-service 자체 retry/alert 정책 적용. |
| **admin-service** | `admin_actions.target_id` 는 감사 무결성을 위해 변경하지 않음. 운영자 UI에서 해당 `target_id` 조회 시 `"(deleted user)"` 라벨을 표시. (UI 표시 로직은 admin-service 프레젠테이션 레이어 책임) | 즉시 (UI 렌더링 시점) | UI 표시 실패는 감사 데이터에 영향 없음. |

#### External 소비자 가이드

WMS, ERP, SCM, MES 등 외부 소비자는 자체 시스템에서 `account.deleted(anonymized=true)` 이벤트를 수신할 때 다음을 수행해야 한다:

- 자기 도메인이 보유한 해당 `accountId`의 PII 필드 (이메일, 이름, 전화번호, IP 등) 를 마스킹 또는 삭제.
- audit/billing row의 FK (`account_id`) 는 보존 가능.
- 마스킹 완료를 증명할 수 있는 내부 audit trail 발행 권장 (GAP의 `security.pii.masked` 패턴 참고).
- SLA: 수신 후 **24시간 이내** 마스킹 완료.
- 컴플라이언스 정기 리뷰에서 이행 여부를 증명할 수 있도록 audit trail 보관.

세부 통합 패턴 및 코드 예시: [specs/features/consumer-integration-guide.md § Phase 5 GDPR downstream](../features/consumer-integration-guide.md#gdpr-downstream-처리-accountdeleted).
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
