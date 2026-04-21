# Task ID

TASK-BE-028

# Title

payment-service 부트스트랩 — 프로젝트 구조, DB 스키마, 도메인 모델

# Status

ready

# Owner

backend

# Task Tags

- code

---

# Required Sections (must exist)

- Goal
- Scope (in/out)
- Acceptance Criteria
- Related Specs
- Related Contracts
- Edge Cases
- Failure Scenarios

---

# Goal

payment-service의 기반 구조를 구축한다. 이후 태스크(TASK-BE-029, TASK-BE-030)가 이 위에서 구현된다.

이 태스크 완료 후: payment-service가 Spring Boot 애플리케이션으로 기동되고, payments 테이블이 생성되며, Payment 애그리거트가 DDD 방식으로 구현된다.

---

# Scope

## In Scope

- `build.gradle`, `application.yml` 설정 (포트 8087, PostgreSQL, Flyway)
- Flyway 마이그레이션: `V1__create_payments_table.sql`
- 도메인 모델
  - `PaymentStatus` enum (PENDING, COMPLETED, FAILED, REFUNDED)
  - `Payment` 애그리거트 (`@Entity`) — `create()`, `complete()`, `refund()` 메서드
- 도메인 예외
  - `PaymentNotFoundException`
  - `InvalidPaymentException`
  - `PaymentAlreadyProcessedException`
- Repository: `PaymentRepository` 인터페이스 (save, findById, findByOrderId)
- Infrastructure: `PaymentJpaRepository`, `PaymentRepositoryImpl`
- 공통 인프라: `ErrorResponse`, `GlobalExceptionHandler`
- `PaymentServiceApplication`

## Out of Scope

- 이벤트 소비/발행 (TASK-BE-029, TASK-BE-030)
- HTTP API (TASK-BE-030)

---

# Acceptance Criteria

- [ ] `PaymentServiceApplication`이 기동되고 `/actuator/health`가 200을 반환한다
- [ ] `Payment.create(orderId, userId, amount)` 호출 시 PENDING 상태로 생성된다
- [ ] `Payment.complete()` 호출 시 PENDING → COMPLETED 전이된다
- [ ] `Payment.complete()` 이미 COMPLETED이면 멱등 처리된다 (예외 없음)
- [ ] `Payment.complete()` PENDING이 아니고 COMPLETED도 아닌 경우 예외가 발생한다
- [ ] `Payment.refund()` 호출 시 COMPLETED → REFUNDED 전이된다
- [ ] `Payment.refund()` COMPLETED가 아닌 경우 예외가 발생한다
- [ ] Flyway가 기동 시 `payments` 테이블을 생성한다
- [ ] `GlobalExceptionHandler`가 PaymentNotFoundException → 404, InvalidPaymentException → 400을 반환한다
- [ ] 도메인 단위 테스트가 추가된다

---

# Related Specs

- `specs/services/payment-service/architecture.md`
- `specs/services/payment-service/overview.md`
- `specs/platform/testing-strategy.md`
- `specs/platform/error-handling.md`

# Related Contracts

- `specs/contracts/http/payment-api.md`
- `specs/contracts/events/payment-events.md`

---

# Target Service

- `payment-service`

---

# Architecture

Hexagonal Architecture:
- Domain: `Payment`, `PaymentStatus`, `PaymentRepository` (port), exceptions
- Application: (TASK-BE-029, TASK-BE-030에서 추가)
- Infrastructure: `PaymentJpaRepository`, `PaymentRepositoryImpl`, `ErrorResponse`, `GlobalExceptionHandler`

---

# Implementation Notes

### DB Schema

```sql
CREATE TABLE payments (
    payment_id   VARCHAR(36)    NOT NULL PRIMARY KEY,
    order_id     VARCHAR(36)    NOT NULL UNIQUE,
    user_id      VARCHAR(255)   NOT NULL,
    amount       BIGINT         NOT NULL,
    status       VARCHAR(20)    NOT NULL,
    created_at   TIMESTAMP      NOT NULL,
    paid_at      TIMESTAMP,
    refunded_at  TIMESTAMP
);
CREATE INDEX idx_payments_order_id ON payments(order_id);
CREATE INDEX idx_payments_user_id  ON payments(user_id);
```

### Payment.complete() 멱등

이미 COMPLETED이면 아무 작업 없이 반환. OrderPlaced 이벤트 중복 처리 안전.

---

# Edge Cases

- `Payment.complete()` REFUNDED 상태에서 호출 → InvalidPaymentException
- `Payment.refund()` PENDING 상태에서 호출 → InvalidPaymentException

---

# Failure Scenarios

- DB 연결 실패 → Spring 기동 실패 (Flyway validation error)

---

# Test Requirements

- 단위 테스트: `PaymentTest`
  - PENDING으로 생성, complete() PENDING→COMPLETED, complete() 멱등, complete() FAILED→예외
  - refund() COMPLETED→REFUNDED, refund() PENDING→예외

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added
- [ ] Tests passing
- [ ] Contracts updated if needed
- [ ] Specs updated first if required
- [ ] Ready for review
