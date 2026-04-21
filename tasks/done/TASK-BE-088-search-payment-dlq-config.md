# Task ID

TASK-BE-088

# Title

search-service, payment-service Kafka DLQ 설정 추가 — DeadLetterPublishingRecoverer 적용

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

`specs/platform/event-driven-policy.md`는 모든 컨슈머 그룹에 DLQ를 요구하지만, search-service와 payment-service에는 DLQ 설정이 없다.

현재 문제:
- search-service: `ProductCreatedConsumer`에서 `JsonProcessingException` 발생 시 에러 로그만 남기고 메시지를 무시 → 검색 인덱스 불일치
- payment-service: `PaymentProcessingService`에서 `catch (Exception e)`로 광범위한 예외 처리 → 실패 원인 파악 어려움

TASK-BE-046에서 order-service에 적용한 DLQ 패턴(`DeadLetterPublishingRecoverer` + `DefaultErrorHandler` + `FixedBackOff`)을 search-service와 payment-service에 동일하게 적용한다.

---

# Scope

## In Scope

- search-service에 `KafkaConsumerConfig` 추가 (DLQ + 재시도 설정)
- payment-service에 `KafkaConsumerConfig` 추가 (DLQ + 재시도 설정)
- 기존 silent catch 제거 → 예외를 상위로 전파하여 DLQ 라우팅
- 단위 테스트 추가

## Out of Scope

- DLQ 메시지 재처리 로직 (별도 태스크)
- auth-service, user-service DLQ (이미 구현됨)
- order-service DLQ (TASK-BE-046에서 완료)

---

# Acceptance Criteria

- [ ] search-service의 모든 Kafka 컨슈머에서 처리 실패 시 `{topic}.DLT` 토픽으로 메시지가 라우팅된다
- [ ] payment-service의 모든 Kafka 컨슈머에서 처리 실패 시 `{topic}.DLT` 토픽으로 메시지가 라우팅된다
- [ ] search-service `ProductCreatedConsumer`의 silent catch가 제거된다
- [ ] payment-service의 과도하게 넓은 `catch (Exception e)` 블록이 특정 예외로 변경된다
- [ ] 재시도 설정이 `FixedBackOff(1000, 3)`으로 구성된다
- [ ] 단위 테스트가 DLQ 라우팅을 검증한다

---

# Related Specs

- `specs/platform/event-driven-policy.md`
- `specs/services/search-service/architecture.md`
- `specs/services/payment-service/architecture.md`

# Related Skills

- `.claude/skills/messaging/kafka.md`
- `.claude/skills/backend/testing-backend.md`

---

# Related Contracts

- `specs/contracts/events/product-events.md`
- `specs/contracts/events/order-events.md`

---

# Target Service

- `search-service`
- `payment-service`

---

# Architecture

Follow:

- `specs/services/search-service/architecture.md`
- `specs/services/payment-service/architecture.md`

---

# Implementation Notes

- order-service의 `KafkaConsumerConfig` 패턴을 참고하여 동일한 구조로 구현
- search-service: `ProductCreatedConsumer`, `ProductUpdatedConsumer`, `ProductDeletedConsumer`, `StockChangedConsumer`에 적용
- payment-service: `OrderPlacedEventConsumer`, `OrderCancelledEventConsumer`에 적용
- silent catch 패턴 제거 후 예외를 상위로 전파해야 DLQ가 동작함

---

# Edge Cases

- 역직렬화 실패 시 DLQ 라우팅 확인
- 비즈니스 로직 예외 vs 일시적 장애 구분
- 동일 메시지 재시도 후 DLQ 이동

---

# Failure Scenarios

- Kafka broker 연결 실패 시 재시도 동작 확인
- DLT 토픽 자체 발행 실패 시 처리
- 재시도 횟수 초과 후 정상적으로 DLQ 이동

---

# Test Requirements

- DLQ 라우팅 단위 테스트 (각 서비스)
- 재시도 후 DLQ 이동 검증
- 기존 정상 흐름 회귀 테스트

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added
- [ ] Tests passing
- [ ] Contracts updated if needed
- [ ] Specs updated first if required
- [ ] Ready for review
