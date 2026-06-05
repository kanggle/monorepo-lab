# Task ID

TASK-BE-041b-fix-idempotency-and-dlq-metrics

# Title

security-service — 041b 리뷰 수정: AccountLockedConsumer 멱등성 + DLQ 메트릭 + 테스트 보완

# Status

ready

# Owner

backend

# Task Tags

- code
- event
- db
- test

# depends_on

- TASK-BE-041b-security-account-locked-consumer (완료됨)

---

# Goal

TASK-BE-041b 코드 리뷰에서 발견된 Critical 2건 + Warning 2건을 수정한다.

## Critical 1 — 멱등성 결함 (idempotency broken on normal path)

account-service outbox는 flat payload (`{"accountId":...,"reasonCode":...}`)를 Kafka에 발행하며 envelope의 `eventId`를 포함하지 않는다. 현재 `AccountLockedConsumer`는 `eventId`가 없으면 매 처리마다 `UUID.randomUUID()`를 생성하므로, Kafka의 at-least-once 재전송 시 동일 메시지에 다른 `event_id`가 부여되어 `account_lock_history`에 중복 row가 삽입된다. `event-consumer.md` 및 `architecture.md` Boundary Rules의 멱등 처리 요건 위반.

**수정 방향:**
- account-service `AccountEventPublisher.publishAccountLocked`의 flat payload에 `eventId`(UUID v7)를 추가한다.
- 해당 필드 추가를 `specs/contracts/events/account-events.md`의 `account.locked` payload 정의에 반영한다.
- `AccountLockedConsumer`의 synthetic UUID 생성 경로를 제거하거나, `eventId` 부재 시 DLQ로 라우팅하도록 변경한다(contract 준수: eventId가 없는 메시지는 invalid).

## Critical 2 — account.locked.dlq가 DLQ 메트릭에서 누락

`SecurityMetricsConfig.DLQ_TOPICS` 목록에 `account.locked.dlq`가 없다. `event-consumer.md`는 "Operator MUST be alerted when DLQ depth > 0"을 요구하며, `platform/service-types/event-consumer.md`의 Observability 항목은 `dlq_depth` 메트릭을 명시한다. 새 consumer를 추가할 때 DLQ 토픽을 메트릭 목록에 함께 등록해야 한다.

**수정 방향:**
- `SecurityMetricsConfig.DLQ_TOPICS`에 `"account.locked.dlq"` 추가.

## Warning 1 — @DataJpaTest 슬라이스 테스트 누락

태스크 스코프 §5 및 task의 Test Requirements에 명시된 `@DataJpaTest`로 `AccountLockHistoryJpaRepository` 쿼리를 검증하는 슬라이스 테스트가 없다.

**수정 방향:**
- `AccountLockHistoryRepositoryTest` (또는 `AccountLockHistoryJpaRepositoryTest`) 신설.
- `findByAccountIdOrderByOccurredAtDesc` 쿼리 결과 순서 검증.
- `event_id` unique constraint 중복 삽입 시도 → `DataIntegrityViolationException` 검증.

## Warning 2 — DlqRoutingIntegrationTest에 account.locked 커버리지 없음

태스크 스코프 §5: "기존 `DlqRoutingIntegrationTest`가 새 consumer도 자동 커버하는지 확인 (파라미터화 가능)". 현재 `DlqRoutingIntegrationTest`는 `auth.login.succeeded`와 `auth.login.failed` 토픽만 테스트한다. `account.locked` 토픽의 poison-pill → DLQ 라우팅 시나리오가 통합 테스트에 포함되지 않았다.

**수정 방향:**
- `DlqRoutingIntegrationTest`에 `account.locked` 토픽 파라미터화 케이스 추가, 또는 별도 시나리오 메서드 추가.

---

# Scope

## In Scope

1. `apps/account-service/src/main/java/com/example/account/application/event/AccountEventPublisher.java` — `publishAccountLocked`에 `eventId` 필드 추가
2. `specs/contracts/events/account-events.md` — `account.locked` payload에 `eventId` 필드 추가
3. `apps/security-service/src/main/java/com/example/security/consumer/AccountLockedConsumer.java` — synthetic UUID fallback 제거 또는 DLQ 라우팅으로 변경
4. `apps/security-service/src/main/java/com/example/security/infrastructure/config/SecurityMetricsConfig.java` — `DLQ_TOPICS`에 `account.locked.dlq` 추가
5. `apps/security-service/src/test/` — `AccountLockHistoryJpaRepositoryTest` 신설 (`@DataJpaTest`)
6. `apps/security-service/src/test/.../integration/DlqRoutingIntegrationTest.java` — `account.locked` 토픽 커버리지 추가
7. `apps/account-service/src/test/` — `publishAccountLocked` eventId 포함 여부 단위 테스트

## Out of Scope

- account.locked payload의 다른 필드 변경
- account-service 외 다른 서비스의 outbox envelope 구조 변경
- security-service HTTP 엔드포인트

---

# Acceptance Criteria

- [ ] account-service outbox가 발행하는 `account.locked` flat payload에 `eventId`(UUID) 포함
- [ ] `specs/contracts/events/account-events.md`의 `account.locked` payload에 `eventId` 명시
- [ ] 동일 Kafka 메시지 2회 수신 → `account_lock_history` row 1개만 존재 (멱등성 보장)
- [ ] `SecurityMetricsConfig.DLQ_TOPICS`에 `account.locked.dlq` 포함
- [ ] `AccountLockHistoryJpaRepositoryTest` (@DataJpaTest) 통과
- [ ] `DlqRoutingIntegrationTest`에 `account.locked` 토픽 poison-pill 케이스 포함 및 통과
- [ ] `./gradlew :apps:security-service:test :apps:account-service:test` 통과

---

# Related Specs

- `specs/services/security-service/architecture.md`
- `specs/contracts/events/account-events.md`
- `platform/service-types/event-consumer.md`

# Related Contracts

- `specs/contracts/events/account-events.md`

---

# Target Service

- `apps/security-service`
- `apps/account-service` (publishAccountLocked eventId 추가)

---

# Edge Cases

- account-service가 `eventId` 없이 발행한 기존 메시지(레거시) — consumer는 `eventId` 없는 메시지를 DLQ로 라우팅하거나 Kafka offset 기반 합성 키를 사용해야 함. 구현 선택 시 기존 flat payload 수신 backward compatibility를 고려할 것

---

# Failure Scenarios

- `eventId` 추가 후 account-service 배포 전 security-service가 먼저 배포된 경우: consumer가 `eventId` 없는 이전 메시지를 만날 수 있음 → DLQ 라우팅이 올바른 선택

---

# Test Requirements

- `AccountLockHistoryJpaRepositoryTest`: `@DataJpaTest`, Testcontainers MySQL
- `DlqRoutingIntegrationTest`: `account.locked` 파라미터 케이스
- account-service `AccountEventPublisherTest`: `publishAccountLocked` payload에 `eventId` 포함 검증

---

# Definition of Done

- [ ] Critical 2건 수정 완료
- [ ] Warning 2건 수정 완료
- [ ] 테스트 통과
- [ ] Ready for review
