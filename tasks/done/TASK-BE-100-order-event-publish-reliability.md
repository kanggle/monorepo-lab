# Task ID

TASK-BE-100

# Title

order-service 이벤트 발행 신뢰성 개선 — TransactionalEventListener 기반 트랜잭션 후 발행 전환

# Status

review

# Owner

backend

# Task Tags

- code
- event
- test

---

# Required Sections (must exist)

- Goal
- Scope (in/out)
- Acceptance Criteria
- Related Specs
- Related Contracts
- Edge Cases
- Failure Scenarios

If any section is missing or incomplete, this task must not be implemented.

---

# Goal

order-service의 이벤트 발행이 DB 트랜잭션 커밋 이후에만 수행되도록 변경하여 데이터-이벤트 불일치를 방지한다.

현재 `OrderPlacementService`, `OrderCancellationService`, `UserWithdrawalOrderService`에서 `@Transactional` 메서드 내에서 `kafkaTemplate.send()`를 try-catch로 직접 호출하고 있다. 이 구조는 두 가지 문제를 야기한다:
1. DB 커밋 성공 + Kafka 실패 → 이벤트 유실 (로그만 남김)
2. Kafka 전송 성공 + DB 롤백 → 팬텀 이벤트 발행

`@TransactionalEventListener(phase = AFTER_COMMIT)`을 사용하여 트랜잭션 커밋 후에만 이벤트를 발행하도록 변경한다.

---

# Scope

## In Scope

- `ApplicationEventPublisher`를 통한 Spring 이벤트 발행으로 전환
- `@TransactionalEventListener(phase = AFTER_COMMIT)` 핸들러 작성
- `OrderPlacementService`, `OrderCancellationService`, `UserWithdrawalOrderService`의 Kafka 직접 호출 제거
- 기존 테스트 수정 및 신규 테스트 추가

## Out of Scope

- Transactional Outbox 패턴 (테이블 기반 이벤트 저장소) 도입
- 다른 서비스의 이벤트 발행 패턴 변경
- Kafka 토픽 구조 변경

---

# Acceptance Criteria

- [ ] 이벤트 발행은 DB 트랜잭션 커밋 이후에만 수행된다
- [ ] DB 트랜잭션 롤백 시 이벤트가 발행되지 않는다
- [ ] application 서비스에서 `KafkaTemplate` 직접 참조가 제거된다
- [ ] 이벤트 발행 실패 시 로그 및 메트릭이 기록된다
- [ ] 기존 이벤트 포맷(envelope)이 변경되지 않는다
- [ ] 단위 테스트 및 통합 테스트가 추가된다

---

# Related Specs

- `specs/services/order-service/architecture.md`
- `specs/services/order-service/observability.md`

# Related Skills

- `.claude/skills/backend/spring-event.md`
- `.claude/skills/backend/kafka-producer.md`

---

# Related Contracts

- `specs/contracts/events/order-events.md`

---

# Target Service

- `order-service`

---

# Architecture

Follow:

- `specs/services/order-service/architecture.md`

---

# Implementation Notes

- application 서비스에서 `ApplicationEventPublisher.publishEvent()`로 내부 Spring 이벤트를 발행한다.
- infrastructure 레이어에 `@TransactionalEventListener(phase = AFTER_COMMIT)` 핸들러를 작성하여 Kafka 전송을 수행한다.
- TASK-BE-099(포트 인터페이스 분리)가 먼저 완료되면 포트를 통해 구현하고, 아니면 이 태스크에서 함께 처리할 수 있다.
- `UserWithdrawalOrderService`의 루프 내 이벤트 발행도 동일 패턴으로 전환한다.

---

# Edge Cases

- 트랜잭션 커밋 후 Kafka 발행 실패 — 로그 + 메트릭 기록 (현재와 동일한 best-effort)
- `UserWithdrawalOrderService`에서 다건 주문 취소 시 — 각 주문별 이벤트가 모두 커밋 후 발행되어야 한다
- `@TransactionalEventListener`가 트랜잭션 없이 호출되는 경우 — 이벤트 무시됨 (fallbackExecution 설정 검토)

---

# Failure Scenarios

- Kafka 브로커 다운 시 — 이벤트 유실 (best-effort, 로그 기록)
- Spring Event 발행 시 예외 — 트랜잭션에 영향 없음 (AFTER_COMMIT이므로)
- 이벤트 리스너 내 예외 — 다른 리스너에 영향 주지 않도록 처리

---

# Test Requirements

- 단위 테스트: 서비스가 `ApplicationEventPublisher`를 통해 이벤트를 발행하는지 확인
- 단위 테스트: 트랜잭션 커밋 후 핸들러가 Kafka로 전송하는지 확인
- 통합 테스트: DB 저장 + 이벤트 발행이 원자적으로 동작하는지 확인
- 통합 테스트: 트랜잭션 롤백 시 이벤트 미발행 확인

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added
- [ ] Tests passing
- [ ] 이벤트 포맷 변경 없음 확인
- [ ] Ready for review
