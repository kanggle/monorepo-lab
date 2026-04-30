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

---

## Consumer Rules

[auth-events.md](auth-events.md)의 Consumer Rules와 동일:
- 멱등 처리 (eventId dedupe)
- forward-compatible schema
- DLQ: `<topic>.dlq`, 3회 재시도
- trace propagation

### Additive Change Policy (TASK-BE-228)

`tenant_id` 필드는 **additive** 변경이다. 기존 컨슈머(security-service, auth-service)는 이 필드를 무시해도 정상 동작한다. 신규 컨슈머는 반드시 `tenant_id`를 페이로드에서 읽어 테넌트별 처리를 수행해야 한다.
