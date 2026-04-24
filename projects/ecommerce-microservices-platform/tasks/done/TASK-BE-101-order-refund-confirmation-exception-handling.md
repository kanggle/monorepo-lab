# Task ID

TASK-BE-101

# Title

order-service PaymentRefundConfirmationService 예외 처리 누락 수정 — InvalidOrderException catch 추가

# Status

ready

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

`PaymentRefundConfirmationService.markRefunded()`에서 `InvalidOrderException`을 catch하지 않아, CANCELLED가 아닌 상태의 주문에 대한 환불 이벤트 수신 시 예외가 전파되어 Kafka 컨슈머 재시도/DLQ 라우팅이 발생하는 문제를 수정한다.

동일 패턴의 `PaymentConfirmationService.markPaymentCompleted()`는 `InvalidOrderException`을 catch하여 warn 로그를 남기고 정상 반환하고 있으므로, `PaymentRefundConfirmationService`도 동일한 방어 처리를 적용한다.

---

# Scope

## In Scope

- `PaymentRefundConfirmationService.markRefunded()`에 `InvalidOrderException` catch 블록 추가
- 경고 로그 남기고 정상 반환하도록 처리
- 단위 테스트 추가

## Out of Scope

- 다른 서비스의 예외 처리 패턴 변경
- Kafka DLQ 설정 변경
- 도메인 모델 변경

---

# Acceptance Criteria

- [ ] CANCELLED가 아닌 상태의 주문에 대한 환불 이벤트 수신 시 예외가 전파되지 않는다
- [ ] `InvalidOrderException` 발생 시 warn 로그가 기록된다
- [ ] 정상 환불 처리 로직은 변경되지 않는다
- [ ] 단위 테스트가 추가된다

---

# Related Specs

- `specs/services/order-service/architecture.md`

# Related Skills

- `.claude/skills/backend/event-consumer.md`

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

- `PaymentConfirmationService.markPaymentCompleted()`의 예외 처리 패턴을 따른다.
- `try { order.markRefunded(...) } catch (InvalidOrderException e) { log.warn(...); }` 형태로 처리한다.

---

# Edge Cases

- PENDING 상태 주문에 대한 환불 이벤트 — catch 후 warn 로그
- CONFIRMED 상태 주문에 대한 환불 이벤트 — catch 후 warn 로그
- 이미 환불된 주문에 대한 중복 이벤트 — 기존 멱등성 처리로 정상 반환 (변경 없음)

---

# Failure Scenarios

- `InvalidOrderException` catch 후 정상 반환 — 재시도/DLQ 라우팅 방지
- 그 외 예외는 기존 DLQ 메커니즘으로 처리

---

# Test Requirements

- 단위 테스트: CANCELLED가 아닌 상태에서 markRefunded 호출 시 예외 미전파 확인
- 단위 테스트: 정상 환불 처리 확인 (기존 테스트 유지)

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added
- [ ] Tests passing
- [ ] Ready for review
