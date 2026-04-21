# Task ID

TASK-BE-027

# Title

order-service StockChanged 이벤트 소비 — ORDER_RESERVED 시 PENDING → CONFIRMED 전이

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

If any section is missing or incomplete, this task must not be implemented.

---

# Goal

product-service가 발행하는 StockChanged 이벤트를 소비하여, `reason == ORDER_RESERVED`인 경우 해당 주문을 PENDING → CONFIRMED 상태로 전이한다.

이 태스크 완료 후: 주문 생성 후 재고 예약이 완료되면 주문이 자동으로 CONFIRMED 상태로 변경된다.

---

# Scope

## In Scope

- `Order.confirm()` 도메인 메서드 추가 — PENDING → CONFIRMED 상태 전이, 이미 CONFIRMED이면 멱등 처리
- `OrderConfirmationService` application service — orderId로 주문 조회 후 confirm() 호출
- `infrastructure/event/StockChangedEvent.java` — 인바운드 이벤트 레코드 (product-service 계약 기반)
- `infrastructure/event/StockChangedEventConsumer.java` — `@EventListener` 기반 어댑터
  - reason == ORDER_RESERVED 이고 orderId가 있을 때만 confirmOrder 호출
  - 그 외 reason (RESTOCK, ORDER_CANCELLED, ADMIN_ADJUSTMENT) 무시
- 단위 테스트 및 통합 테스트 추가

## Out of Scope

- Kafka 연동 (현재 단계: Spring ApplicationEvent 기반)
- DLQ 처리
- ORDER_CANCELLED 이벤트로 주문 상태 변경 (주문 취소는 HTTP API로 처리)
- CONFIRMED → SHIPPED → DELIVERED 전이 (별도 태스크)

---

# Acceptance Criteria

- [ ] `Order.confirm()` 호출 시 PENDING → CONFIRMED로 상태가 변경된다
- [ ] `Order.confirm()` 호출 시 이미 CONFIRMED이면 아무 변화 없이 정상 반환된다 (멱등)
- [ ] `Order.confirm()` 호출 시 PENDING이 아니고 CONFIRMED도 아닌 상태이면 예외가 발생한다
- [ ] StockChanged 이벤트 소비 시 reason == ORDER_RESERVED이고 orderId가 있으면 해당 주문이 CONFIRMED된다
- [ ] reason이 ORDER_RESERVED가 아닌 경우 이벤트를 무시한다
- [ ] orderId가 null이거나 blank인 경우 경고 로그만 남기고 무시한다
- [ ] 존재하지 않는 orderId가 포함된 경우 경고 로그만 남기고 무시한다 (예외 전파 금지)
- [ ] 이미 CONFIRMED된 주문에 대해 중복 이벤트가 오면 안전하게 멱등 처리된다
- [ ] `OrderConfirmationService`가 application 레이어에 위치한다
- [ ] `StockChangedEventConsumer`가 infrastructure/event 레이어에 위치한다
- [ ] 단위 테스트 및 통합 테스트가 추가된다
- [ ] 기존 모든 테스트가 통과한다

---

# Related Specs

- `specs/services/order-service/architecture.md`
- `specs/services/order-service/overview.md`
- `specs/platform/event-driven-policy.md`
- `specs/platform/testing-strategy.md`

# Related Contracts

- `specs/contracts/events/product-events.md` (StockChanged 이벤트, orderId 필드 추가됨)

---

# Target Service

- `order-service`

---

# Architecture

DDD-style 계층 배치:
- Domain: `Order.confirm()` 추가
- Application: `OrderConfirmationService`
- Infrastructure/Event (inbound adapter): `StockChangedEvent`, `StockChangedEventConsumer`

---

# Implementation Notes

### Order.confirm()

```java
public void confirm() {
    if (this.status == OrderStatus.CONFIRMED) {
        return; // 멱등: 이미 CONFIRMED이면 무시
    }
    if (this.status != OrderStatus.PENDING) {
        throw new InvalidOrderException("PENDING 상태에서만 확정할 수 있습니다: " + status);
    }
    this.status = OrderStatus.CONFIRMED;
    this.updatedAt = LocalDateTime.now();
}
```

### StockChangedEventConsumer

```java
@EventListener
public void handle(StockChangedEvent event) {
    if (!"ORDER_RESERVED".equals(event.payload().reason())) return;
    String orderId = event.payload().orderId();
    if (orderId == null || orderId.isBlank()) {
        log.warn("StockChanged ORDER_RESERVED event has no orderId, skipping");
        return;
    }
    try {
        orderConfirmationService.confirmOrder(orderId);
    } catch (Exception e) {
        log.warn("Failed to confirm order {}: {}", orderId, e.getMessage());
    }
}
```

### 멱등성

`Order.confirm()`이 CONFIRMED 상태에서 재호출되면 아무 동작 없이 반환. 동일 이벤트 중복 소비 안전.

---

# Edge Cases

- reason이 ORDER_RESERVED 이외인 StockChanged → 무시
- orderId 필드가 없거나 blank → 경고 로그 후 무시
- orderId가 존재하지 않는 주문 → 경고 로그 후 무시 (OrderNotFoundException catch)
- 이미 CONFIRMED된 주문에 중복 이벤트 → Order.confirm() 멱등 처리로 안전
- CANCELLED된 주문에 ORDER_RESERVED 이벤트 → InvalidOrderException → catch하여 경고 로그

---

# Failure Scenarios

- 이벤트 소비 중 OrderRepository 접근 실패 → 예외 catch, 경고 로그 (현재 단계 허용)
- 이벤트 payload 역직렬화 실패 → Spring이 EventListener 호출 자체를 하지 않음 (런타임 타입 미스매치)

---

# Test Requirements

- 단위 테스트: `OrderConfirmTest` (OrderTest에 추가)
  - PENDING → CONFIRMED 정상 전이
  - CONFIRMED 상태에서 confirm() 호출 시 멱등 처리 (예외 없음)
  - CANCELLED 상태에서 confirm() 호출 시 예외 발생
- 단위 테스트: `OrderConfirmationServiceTest`
  - 정상 확정
  - 존재하지 않는 orderId → OrderNotFoundException
- 단위 테스트: `StockChangedEventConsumerTest`
  - ORDER_RESERVED + orderId 있음 → confirmOrder 호출됨
  - ORDER_RESERVED + orderId 없음 → confirmOrder 미호출
  - RESTOCK reason → confirmOrder 미호출
  - confirmOrder 예외 발생 시 → 예외 전파 없음
- 통합 테스트: `OrderConfirmationIntegrationTest`
  - 이벤트 발행 → CONFIRMED 상태 확인

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added
- [ ] Tests passing
- [ ] Contracts updated if needed
- [ ] Specs updated first if required
- [ ] Ready for review
