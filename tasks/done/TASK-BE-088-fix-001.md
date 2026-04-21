# Task ID

TASK-BE-088-fix-001

# Title

TASK-BE-088 리뷰 수정 — payment-service catch 블록 특정 예외 변경 및 DLQ 라우팅 테스트 보강

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

TASK-BE-088 리뷰에서 발견된 이슈 2건을 수정한다.

**이슈 1 (HIGH):** payment-service의 `PaymentProcessingService`, `PaymentRefundService`에서 `kafkaTemplate.send()` 호출 시 `catch (Exception e)` 블록이 그대로 유지되어 있다. AC에서 "과도하게 넓은 `catch (Exception e)` 블록이 특정 예외로 변경된다"고 요구하므로, 특정 예외 타입으로 변경해야 한다.

**이슈 2 (MEDIUM):** search-service와 payment-service 모두 DLQ 라우팅을 직접 검증하는 테스트가 부족하다. `KafkaConsumerConfigTest`에서 `DefaultErrorHandler` 인스턴스 타입만 확인하고, `DeadLetterPublishingRecoverer` 라우팅, `FixedBackOff` 재시도 횟수, `JsonProcessingException` non-retryable 설정을 검증하지 않는다.

---

# Scope

## In Scope

- `PaymentProcessingService.processPayment()`의 `catch (Exception e)`를 특정 예외 타입으로 변경
- `PaymentRefundService.refundPayment()`의 `catch (Exception e)`를 특정 예외 타입으로 변경
- search-service `KafkaConsumerConfigTest`에 DLQ 라우팅 상세 검증 테스트 추가
- payment-service `KafkaConsumerConfigTest`에 DLQ 라우팅 상세 검증 테스트 추가

## Out of Scope

- KafkaConsumerConfig 구현 변경 (정상 구현됨)
- 컨슈머 예외 전파 로직 변경 (정상 구현됨)
- order-service 변경

---

# Acceptance Criteria

- [ ] `PaymentProcessingService`의 `kafkaTemplate.send()` catch 블록이 특정 예외 타입(예: `KafkaException`)으로 변경된다
- [ ] `PaymentRefundService`의 `kafkaTemplate.send()` catch 블록이 특정 예외 타입으로 변경된다
- [ ] search-service `KafkaConsumerConfigTest`에서 `FixedBackOff` 재시도 횟수(3회)를 검증한다
- [ ] search-service `KafkaConsumerConfigTest`에서 `JsonProcessingException`이 non-retryable로 설정되었음을 검증한다
- [ ] payment-service `KafkaConsumerConfigTest`에서 동일한 검증을 수행한다
- [ ] 기존 테스트가 깨지지 않는다

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

# Edge Cases

- `kafkaTemplate.send()`에서 발생 가능한 예외 타입 확인 (KafkaException, InterruptException 등)
- 특정 예외로 변경 후에도 모든 발행 실패가 로깅되는지 확인

---

# Failure Scenarios

- 특정 예외 타입으로 변경 후 예상치 못한 예외가 잡히지 않는 경우
- 테스트 보강 시 리플렉션 접근 불가

---

# Test Requirements

- KafkaConsumerConfig 상세 검증 테스트 (FixedBackOff, non-retryable 설정)
- PaymentProcessingService 이벤트 발행 실패 테스트 갱신
- PaymentRefundService 이벤트 발행 실패 테스트 갱신

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added
- [ ] Tests passing
- [ ] Contracts updated if needed
- [ ] Specs updated first if required
- [ ] Ready for review
