# Task ID

TASK-BE-030

# Title

결제 조회 API + OrderCancelled 이벤트 소비 — 환불 처리 + PaymentRefunded 이벤트 발행

# Status

ready

# Owner

backend

# Task Tags

- code
- api
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

결제 조회 HTTP API와 주문 취소 시 환불 처리 기능을 구현한다.

이 태스크 완료 후: 사용자가 orderId로 결제 정보를 조회할 수 있고, 주문이 취소되면 자동으로 환불이 처리된다.

---

# Scope

## In Scope

- `PaymentQueryService` — orderId로 결제 조회, userId 소유권 검증
- `PaymentController` — GET /api/payments/orders/{orderId}
- `infrastructure/event/OrderCancelledEvent.java` — 인바운드 이벤트 레코드
- `infrastructure/event/OrderCancelledEventConsumer.java` — `@EventListener` 어댑터
- `application/service/PaymentRefundService.java` — 환불 처리 유스케이스
  - Payment 조회 → COMPLETED이면 REFUNDED 전이 → 저장 → PaymentRefunded 이벤트 발행
  - 이미 REFUNDED이면 멱등 처리 (중복 이벤트 안전)
  - Payment가 없으면 무시 (결제 전 취소된 주문)
- `application/event/PaymentRefundedEvent.java` — 아웃바운드 이벤트 레코드
- Presentation DTO: `PaymentDetailResponse`
- 단위 테스트 및 통합 테스트 추가

## Out of Scope

- Kafka 연동 (현재 단계: Spring ApplicationEvent 기반)
- DLQ 처리

---

# Acceptance Criteria

- [ ] GET /api/payments/orders/{orderId} 정상 요청 시 200과 결제 상세 정보를 반환한다
- [ ] X-User-Id 헤더 누락 시 400 반환, code=INVALID_PAYMENT_REQUEST
- [ ] 존재하지 않는 orderId 조회 시 404 반환, code=PAYMENT_NOT_FOUND
- [ ] 다른 사용자의 결제 조회 시 403 반환, code=UNAUTHORIZED
- [ ] OrderCancelled 이벤트 수신 시 COMPLETED 상태 결제가 REFUNDED로 전이된다
- [ ] PaymentRefunded 이벤트가 발행되고 orderId, userId, amount, refundedAt이 포함된다
- [ ] 이미 REFUNDED 상태인 경우 중복 이벤트를 멱등 처리한다
- [ ] Payment가 없는 orderId의 OrderCancelled 이벤트는 무시된다 (경고 로그만)
- [ ] OrderCancelled 이벤트 처리 실패 시 예외가 외부로 전파되지 않는다
- [ ] 단위 테스트 및 통합 테스트가 추가된다
- [ ] 기존 모든 테스트가 통과한다

---

# Related Specs

- `specs/services/payment-service/architecture.md`
- `specs/platform/event-driven-policy.md`
- `specs/platform/error-handling.md`
- `specs/platform/testing-strategy.md`

# Related Contracts

- `specs/contracts/http/payment-api.md`
- `specs/contracts/events/order-events.md` (OrderCancelled — inbound)
- `specs/contracts/events/payment-events.md` (PaymentRefunded — outbound)

---

# Target Service

- `payment-service`

---

# Architecture

Hexagonal Architecture:
- Inbound HTTP Adapter: `PaymentController`
- Inbound Event Adapter: `OrderCancelledEventConsumer`
- Application: `PaymentQueryService`, `PaymentRefundService`
- Domain: `Payment.refund()`
- Outbound (internal event): `PaymentRefundedEvent`

---

# Implementation Notes

### PaymentQueryService

```java
public PaymentDetail getPaymentByOrderId(String orderId, String requestingUserId) {
    Payment payment = paymentRepository.findByOrderId(orderId)
            .orElseThrow(() -> new PaymentNotFoundException(orderId));
    if (!payment.getUserId().equals(requestingUserId)) {
        throw new UnauthorizedPaymentAccessException();
    }
    return PaymentDetail.from(payment);
}
```

### PaymentRefundService

```java
@Transactional
public void refundPayment(String orderId) {
    Optional<Payment> paymentOpt = paymentRepository.findByOrderId(orderId);
    if (paymentOpt.isEmpty()) {
        log.info("No payment found for orderId={}, skipping refund", orderId);
        return;
    }
    Payment payment = paymentOpt.get();
    if (payment.getStatus() == PaymentStatus.REFUNDED) {
        log.info("Payment already refunded for orderId={}, skipping", orderId);
        return;
    }
    payment.refund();
    paymentRepository.save(payment);
    eventPublisher.publishEvent(PaymentRefundedEvent.from(payment));
}
```

---

# Edge Cases

- Payment가 PENDING 상태에서 OrderCancelled 이벤트 → `Payment.refund()` 예외 → catch, 경고 로그
- Payment가 없는 orderId → 무시 (결제 전 취소된 주문 가능)
- 이미 REFUNDED → 멱등 처리 (재처리 생략)

---

# Failure Scenarios

- DB 저장 실패 → 예외 catch, 경고 로그
- `PaymentQueryService` DB 장애 → 500 응답

---

# Test Requirements

- 단위 테스트: `PaymentQueryServiceTest`
  - 정상 조회, 404, 403
- 단위 테스트: `PaymentRefundServiceTest`
  - 정상 환불 → REFUNDED, 이벤트 발행
  - 이미 REFUNDED → 멱등
  - Payment 없음 → 무시
  - PENDING 상태 Payment → 예외 catch (전파 없음, 단, PaymentRefundService 레벨에서 처리)
- 단위 테스트: `OrderCancelledEventConsumerTest`
  - 정상 이벤트 → refundPayment 호출
  - 처리 중 예외 → 외부 전파 없음
- 슬라이스 테스트: `PaymentControllerTest`
  - 200, 400 (X-User-Id 누락), 403, 404
- 통합 테스트: `PaymentRefundIntegrationTest`
  - OrderCancelled 이벤트 → REFUNDED 확인

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added
- [ ] Tests passing
- [ ] Contracts updated if needed
- [ ] Specs updated first if required
- [ ] Ready for review
