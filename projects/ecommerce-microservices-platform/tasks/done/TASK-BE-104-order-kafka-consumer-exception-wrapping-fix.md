# Task ID

TASK-BE-104

# Title

order-service Kafka consumer 예외 래핑 수정 — JsonProcessingException not-retryable 우회 버그 및 DLQ KafkaTemplate 타입 불일치

# Status

done

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

order-service의 Kafka consumer들이 `JsonProcessingException`을 `RuntimeException`으로 래핑하여 throw하고 있어, `KafkaConsumerConfig`에서 `addNotRetryableExceptions(JsonProcessingException.class)`로 등록한 설정이 우회되는 버그를 수정한다.

현재 `RuntimeException`으로 감싸져 있어 `DefaultErrorHandler`가 이를 `JsonProcessingException`으로 인식하지 못하고, 불필요한 재시도 2회 후 DLQ로 전송한다. 역직렬화 실패는 재시도해도 결과가 동일하므로 즉시 DLQ로 보내야 한다.

추가로 `KafkaConsumerConfig`에서 DLQ용 `KafkaTemplate<String, String>`을 주입받지만, `application.yml`의 producer value-serializer가 `JsonSerializer`로 설정되어 있어 원본 메시지(String)가 이중 직렬화될 수 있는 문제도 함께 수정한다.

---

# Scope

## In Scope

- `PaymentCompletedEventConsumer`, `PaymentRefundedEventConsumer`, `StockChangedEventConsumer`, `UserWithdrawnEventConsumer`에서 `JsonProcessingException`을 `RuntimeException`으로 래핑하지 않고 직접 throw하도록 수정
- consumer 메서드 시그니처에 `throws JsonProcessingException` 추가 또는 커스텀 not-retryable 예외로 래핑
- `KafkaConsumerConfig`의 DLQ용 `KafkaTemplate` 타입 불일치 수정
- `IllegalArgumentException` (paidAt/refundedAt 파싱 실패)도 not-retryable로 등록
- 기존 테스트 수정 및 새 테스트 추가

## Out of Scope

- Outbox 패턴 도입 (TASK-BE-100에서 다룸)
- 다른 서비스의 Kafka consumer 수정
- DLQ 모니터링/알림 설정

---

# Acceptance Criteria

- [ ] `JsonProcessingException` 발생 시 재시도 없이 즉시 DLQ로 전송된다
- [ ] `IllegalArgumentException` (시각 파싱 실패) 발생 시 재시도 없이 즉시 DLQ로 전송된다
- [ ] DLQ에 전송되는 원본 메시지가 이중 직렬화되지 않는다
- [ ] 정상적인 비즈니스 예외(OrderNotFoundException 등)는 기존대로 재시도된다
- [ ] 모든 consumer의 단위 테스트가 예외 시나리오를 커버한다

---

# Related Specs

- `specs/services/order-service/architecture.md`

# Related Skills

- `.claude/skills/backend/kafka-consumer.md`

---

# Related Contracts

- `specs/contracts/events/payment-events.md`
- `specs/contracts/events/product-events.md`
- `specs/contracts/events/user-events.md`

---

# Target Service

- `order-service`

---

# Architecture

Follow:

- `specs/services/order-service/architecture.md`

---

# Implementation Notes

- consumer 메서드에서 `JsonProcessingException`을 checked exception으로 직접 throw하면 `@KafkaListener`가 이를 `ListenerExecutionFailedException`으로 래핑한다. `DefaultErrorHandler`는 cause chain을 검사하므로 not-retryable로 올바르게 인식된다.
- 또는 커스텀 `MessageDeserializationException extends RuntimeException`을 만들어 래핑하고, 이를 not-retryable로 등록하는 방법도 가능하다.
- DLQ용 `KafkaTemplate`은 `StringSerializer`를 사용하는 별도 빈으로 구성하거나, `DeadLetterPublishingRecoverer`에 `ProducerFactory<String, String>`을 직접 전달한다.
- `IllegalArgumentException`을 `KafkaConsumerConfig.addNotRetryableExceptions()`에 추가한다.

---

# Edge Cases

- consumer 메서드에서 `JsonProcessingException`과 비즈니스 예외가 동시에 발생할 수 있는 경로는 없음 (파싱 먼저 수행)
- `@KafkaListener`의 `ListenerExecutionFailedException` 래핑이 cause chain 검사에 영향을 주지 않는지 확인 필요

---

# Failure Scenarios

- DLQ 토픽이 존재하지 않을 경우 — Kafka auto.create.topics 설정에 의존 (기존 동작 유지)
- DLQ 전송 자체가 실패할 경우 — DefaultErrorHandler의 기본 동작(로그 후 skip)에 의존

---

# Test Requirements

- 단위 테스트: 각 consumer에서 잘못된 JSON 수신 시 `JsonProcessingException`이 래핑 없이 전파되는지 확인
- 단위 테스트: paidAt/refundedAt 파싱 실패 시 적절한 예외 전파 확인
- 단위 테스트: KafkaConsumerConfig의 not-retryable 예외 목록에 필요한 예외가 등록되어 있는지 확인

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added
- [ ] Tests passing
- [ ] Ready for review
