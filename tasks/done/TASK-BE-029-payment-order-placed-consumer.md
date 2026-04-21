# Task ID

TASK-BE-029

# Title

payment-service OrderPlaced 이벤트 소비 — 결제 생성 + 처리 + PaymentCompleted 이벤트 발행

# Status

ready

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

---

# Goal

order-service가 발행하는 OrderPlaced 이벤트를 소비하여 결제를 생성하고 처리(시뮬레이션)한다. 처리 완료 후 PaymentCompleted 이벤트를 발행한다.

이 태스크 완료 후: 주문이 생성되면 결제가 자동으로 처리되고 PaymentCompleted 이벤트가 발행된다.

---

# Scope

## In Scope

- `infrastructure/event/OrderPlacedEvent.java` — 인바운드 이벤트 레코드 (order-service 계약 기반)
- `infrastructure/event/OrderPlacedEventConsumer.java` — `@EventListener` 어댑터
- `application/event/PaymentCompletedEvent.java` — 아웃바운드 이벤트 레코드
- `application/service/PaymentProcessingService.java` — 결제 처리 유스케이스
  - Payment 생성(PENDING) → 결제 처리(시뮬레이션, 항상 성공) → COMPLETED 전이 → 저장 → PaymentCompleted 이벤트 발행
- 멱등성: 동일 orderId에 대해 이미 Payment가 존재하면 처리 생략 (중복 이벤트 안전)
- 단위 테스트 및 통합 테스트 추가

## Out of Scope

- 실제 결제 게이트웨이 연동 (시뮬레이션으로 대체)
- PaymentFailed 이벤트 (항상 성공 시나리오에서는 불필요)
- Kafka 연동 (현재 단계: Spring ApplicationEvent 기반)

---

# Acceptance Criteria

- [ ] OrderPlaced 이벤트 수신 시 Payment가 생성되고 COMPLETED 상태로 저장된다
- [ ] PaymentCompleted 이벤트가 발행되고 orderId, userId, amount, paidAt이 포함된다
- [ ] 동일 orderId에 대해 중복 이벤트 수신 시 중복 Payment가 생성되지 않는다 (멱등)
- [ ] OrderPlaced 이벤트 처리 실패 시 예외가 외부로 전파되지 않는다 (로그만)
- [ ] `PaymentProcessingService`가 application 레이어에 위치한다
- [ ] `OrderPlacedEventConsumer`가 infrastructure/event 레이어에 위치한다
- [ ] 단위 테스트 및 통합 테스트가 추가된다
- [ ] 기존 모든 테스트가 통과한다

---

# Related Specs

- `specs/services/payment-service/architecture.md`
- `specs/platform/event-driven-policy.md`
- `specs/platform/testing-strategy.md`

# Related Contracts

- `specs/contracts/events/order-events.md` (OrderPlaced — inbound)
- `specs/contracts/events/payment-events.md` (PaymentCompleted — outbound)

---

# Target Service

- `payment-service`

---

# Architecture

Hexagonal Architecture:
- Inbound Adapter: `OrderPlacedEventConsumer`
- Application: `PaymentProcessingService`
- Domain: `Payment.create()`, `Payment.complete()`
- Outbound (internal event): `PaymentCompletedEvent`

---

# Implementation Notes

### PaymentProcessingService

```java
@Transactional
public void processPayment(String orderId, String userId, long amount) {
    if (paymentRepository.findByOrderId(orderId).isPresent()) {
        log.info("Payment already exists for orderId={}, skipping", orderId);
        return;
    }
    Payment payment = Payment.create(orderId, userId, amount);
    payment.complete();
    paymentRepository.save(payment);
    eventPublisher.publishEvent(PaymentCompletedEvent.from(payment));
}
```

### 멱등성

`findByOrderId`로 기존 결제 확인. 이미 존재하면 처리 생략.

---

# Edge Cases

- OrderPlaced payload의 amount(totalPrice)가 0 → 결제 생성 후 COMPLETED (0원 결제 허용)
- OrderPlaced 이벤트에 필수 필드 누락 → 경고 로그 후 무시

---

# Failure Scenarios

- DB 저장 실패 → 예외 catch, 경고 로그
- 이벤트 발행 후 저장 실패 시 → 트랜잭션 롤백, PaymentCompleted 이벤트는 트랜잭션 커밋 후 발행 필요

---

# Test Requirements

- 단위 테스트: `PaymentProcessingServiceTest`
  - 정상 처리 → COMPLETED 저장, 이벤트 발행 확인
  - 중복 orderId → 저장/이벤트 미발행 확인
- 단위 테스트: `OrderPlacedEventConsumerTest`
  - 정상 이벤트 → processPayment 호출
  - 처리 중 예외 → 외부 전파 없음
- 통합 테스트: `PaymentProcessingIntegrationTest`
  - OrderPlaced 이벤트 발행 → Payment COMPLETED 확인

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added
- [ ] Tests passing
- [ ] Contracts updated if needed
- [ ] Specs updated first if required
- [ ] Ready for review
