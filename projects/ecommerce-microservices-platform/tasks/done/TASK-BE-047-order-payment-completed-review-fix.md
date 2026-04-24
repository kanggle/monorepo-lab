# Task ID

TASK-BE-047

# Title

TASK-BE-043 리뷰 수정 — 서비스 네이밍, CANCELLED 주문 예외 처리, 테스트 보완

# Status

review

# Owner

backend

# Task Tags

- code
- event

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

TASK-BE-043 리뷰에서 발견된 이슈를 수정한다.

주요 수정 사항:
1. `OrderPaymentService` → `PaymentConfirmationService`로 리네이밍 (태스크 스펙 준수)
2. `PaymentConfirmationService`에서 CANCELLED 주문의 `InvalidOrderException`을 catch하여 warn 로그 후 무시하도록 수정 (수용 기준: "이미 CANCELLED 상태인 주문에 PaymentCompleted 수신 → warn 로그, 무시")
3. `paidAt` 파싱 실패 시 `LocalDateTime.now()` 폴백 대신 예외 전파 (DLQ 라우팅 — TASK-BE-046 선행 필요)
4. 테스트 보완 — CANCELLED 주문 예외 처리, 멱등성 시나리오

---

# Scope

## In Scope

- `OrderPaymentService` → `PaymentConfirmationService` 클래스명 변경 및 참조 업데이트
- `PaymentConfirmationService.markPaymentCompleted()`에서 `InvalidOrderException` catch → warn 로그 후 return
- `PaymentCompletedEventConsumer`의 `paidAt` 파싱 실패 시 예외 전파로 변경
- `OrderPaymentServiceTest` → `PaymentConfirmationServiceTest`로 변경 및 테스트 케이스 보완

## Out of Scope

- Order 도메인 로직 변경
- DLQ 설정 (TASK-BE-046에서 처리)
- 새로운 API 엔드포인트 추가

---

# Acceptance Criteria

- [ ] `PaymentConfirmationService` 클래스명이 태스크 스펙(TASK-BE-043)과 일치한다
- [ ] CANCELLED 주문에 PaymentCompleted 이벤트 수신 시 warn 로그를 남기고 정상 완료한다 (예외 전파 없음)
- [ ] `paidAt` 파싱 실패 시 `LocalDateTime.now()` 폴백 대신 예외를 전파한다
- [ ] 테스트가 CANCELLED 주문 시나리오를 정확히 검증한다 (예외 전파가 아닌 정상 완료)
- [ ] 모든 참조(`PaymentCompletedEventConsumer` 등)가 새 클래스명을 사용한다
- [ ] 기존 테스트 전체 통과

---

# Related Specs

- `specs/platform/event-driven-policy.md`
- `specs/services/order-service/architecture.md`
- `specs/platform/testing-strategy.md`

# Related Skills

- `.claude/skills/backend/springboot-api.md`
- `.claude/skills/backend/architecture/ddd.md`
- `.claude/skills/backend/testing-backend.md`
- `.claude/skills/backend/implementation-workflow.md`

---

# Related Contracts

- `specs/contracts/events/payment-events.md` (PaymentCompleted — 소비)

---

# Target Service

- `order-service`

---

# Architecture

Follow:

- `specs/services/order-service/architecture.md`

수정 대상 계층:
- Application: `OrderPaymentService` → `PaymentConfirmationService` 리네이밍, `InvalidOrderException` 처리 추가
- Infrastructure: `PaymentCompletedEventConsumer` — 서비스 참조 변경, paidAt 파싱 실패 시 예외 전파

---

# Implementation Notes

### 1. 서비스 리네이밍

- `OrderPaymentService.java` → `PaymentConfirmationService.java`
- `OrderPaymentServiceTest.java` → `PaymentConfirmationServiceTest.java`
- `PaymentCompletedEventConsumer`의 의존성 참조 변경

### 2. CANCELLED 주문 예외 처리

현재 `markPaymentCompleted()`:
```java
// Order.markPaymentCompleted() 에서 CANCELLED 시 InvalidOrderException throw
// PaymentConfirmationService에서 이 예외를 catch하지 않음 → 상위로 전파
```

변경 후:
```java
try {
    order.markPaymentCompleted(paymentId, paidAt);
    orderRepository.save(order);
} catch (InvalidOrderException e) {
    log.warn("결제 완료 반영 불가 — 주문 상태 부적합: orderId={}, reason={}", orderId, e.getMessage());
    return;
}
```

### 3. paidAt 파싱 실패 처리

현재: `LocalDateTime.now()` 폴백
변경 후: 예외 전파 → DLQ 라우팅 (TASK-BE-046 적용 후)

---

# Edge Cases

- CANCELLED 주문에 PaymentCompleted 수신 → warn 로그, 정상 완료 (ack)
- paidAt가 파싱 불가능한 형식 → 예외 전파, DLQ 라우팅

---

# Failure Scenarios

- 리네이밍 시 참조 누락 → 컴파일 에러로 즉시 감지
- InvalidOrderException catch 추가 후 의도치 않은 예외 무시 → 테스트로 검증

---

# Test Requirements

- 단위 테스트: `PaymentConfirmationService` — CANCELLED 주문 시 예외 전파 없이 warn 로그 확인
- 단위 테스트: `PaymentConfirmationService` — 멱등성 (동일 paymentId 2회 호출)
- 단위 테스트: `PaymentCompletedEventConsumer` — paidAt 파싱 실패 시 예외 전파 확인
- 기존 테스트 전체 통과

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added
- [ ] Tests passing
- [ ] Contracts updated if needed
- [ ] Specs updated first if required
- [ ] Ready for review
