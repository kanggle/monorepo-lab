# Task ID

TASK-BE-113-fix-001

# Title

TASK-BE-113 리뷰 수정 — order-service 회원 탈퇴 시 주문 취소 배치 저장 실제 구현

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

TASK-BE-113 리뷰에서 발견된 문제를 수정한다. TASK-BE-113의 핵심 목표인 배치 저장 최적화가 구현되지 않았다.

구체적으로:
1. `OrderRepository` 인터페이스에 `saveAll()` 메서드가 추가되지 않았다.
2. `OrderRepositoryImpl`에 `saveAll()` 구현이 없다.
3. `UserWithdrawalOrderService`에서 for 루프 내 개별 `save()` + 즉시 이벤트 발행 패턴이 그대로 유지되어 있다. "취소 처리 → `saveAll()` → 이벤트 일괄 발행" 흐름으로 변경되지 않았다.
4. 테스트가 `saveAll()` 호출을 검증하지 않고 여전히 개별 `save()` 2회를 검증한다.

---

# Scope

## In Scope

- `OrderRepository` 인터페이스에 `List<Order> saveAll(List<Order> orders)` 메서드 추가
- `OrderRepositoryImpl`에 `saveAll()` 구현 — `jpaRepository.saveAll()` 사용
- `UserWithdrawalOrderService.cancelOrdersForWithdrawnUser()` 로직 변경
  - 모든 주문 `cancel()` 호출
  - `saveAll()`로 한 번에 저장
  - 저장 완료 후 이벤트 일괄 발행
  - 메트릭은 각 주문별로 기록 유지
- 단위 테스트 수정 — `saveAll()` 호출 검증으로 업데이트
- 통합 테스트 추가 (`UserWithdrawalOrderServiceIntegrationTest` 또는 동등한 수준의 검증)

## Out of Scope

- 다른 서비스의 배치 처리 최적화
- JPA batch insert/update 설정 변경 (Hibernate 설정)
- TransactionalEventListener 전환 (TASK-BE-100 범위)

---

# Acceptance Criteria

- [ ] `OrderRepository` 인터페이스에 `saveAll(List<Order> orders)` 메서드가 추가된다
- [ ] `OrderRepositoryImpl`에서 `jpaRepository.saveAll()`을 사용하여 배치 저장한다
- [ ] `UserWithdrawalOrderService`에서 모든 주문 취소 후 `saveAll()`로 한 번에 저장한다
- [ ] 이벤트는 저장 완료 후 일괄 발행된다 (for 루프 밖에서 수집 후 발행)
- [ ] 메트릭은 각 주문별로 기록된다 (집계 정확성 유지)
- [ ] 단위 테스트가 `saveAll()` 호출 1회를 검증한다 (기존 `save()` 2회 검증에서 변경)
- [ ] 활성 주문이 없는 경우 조기 리턴 동작이 유지된다
- [ ] 기존 테스트가 모두 통과한다

---

# Related Specs

- `specs/services/order-service/architecture.md`
- `specs/platform/event-driven-policy.md`
- `specs/platform/testing-strategy.md`

# Related Skills

- `.claude/skills/backend/architecture/ddd.md`
- `.claude/skills/backend/testing-backend.md`

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

변경 흐름:

```
// Before (현재 구현 — 변경 전)
for (Order order : activeOrders) {
    order.cancel(clock);
    orderRepository.save(order);           // N번 개별 저장
    orderMetrics.recordOrderCancelled(...);
    orderEventPublisher.publishOrderCancelled(...);  // N번 즉시 발행
}

// After (목표 구현)
List<OrderCancelledEvent> events = new ArrayList<>();
for (Order order : activeOrders) {
    String previousStatus = order.getStatus().name();
    order.cancel(clock);
    orderMetrics.recordOrderCancelled("user_withdrawn");
    orderMetrics.recordStatusTransition(previousStatus, order.getStatus().name());
    events.add(OrderCancelledEvent.of(...));
}
orderRepository.saveAll(activeOrders);    // 1번 배치 저장
events.forEach(orderEventPublisher::publishOrderCancelled);  // 일괄 발행
```

`OrderRepository` 인터페이스 추가 메서드:
```java
List<Order> saveAll(List<Order> orders);
```

`OrderRepositoryImpl` 구현:
- 기존 `save()` 메서드의 update 패턴(version 기반 분기)을 `saveAll()`에서도 동일하게 처리하거나,
- 회원 탈퇴 취소 시 모든 주문은 update 대상임을 고려하여 적절하게 구현

---

# Edge Cases

- 활성 주문이 0건인 경우 — 조기 리턴 유지
- 활성 주문이 매우 많은 경우 — JPA flush 시 메모리 사용량 증가 가능 (현재 태스크 범위에서는 허용)
- `saveAll()` 중 일부 주문에서 낙관적 락 충돌 발생 — 전체 트랜잭션 롤백

---

# Failure Scenarios

- `saveAll()` 중 `OptimisticLockingFailureException` 발생 → 전체 트랜잭션 롤백
- 이벤트 발행 실패 → 메트릭 기록 후 로그 (outbox 패턴 적용 시 해결 — 별도 태스크 범위)

---

# Test Requirements

- unit test: `UserWithdrawalOrderServiceTest` — `saveAll()` 1회 호출 검증, 이벤트 일괄 발행 검증
- 기존 테스트 시나리오 (noActiveOrders, idempotent, recordsMetrics) 모두 유지 및 패스

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added/updated
- [ ] Tests passing
- [ ] Contracts updated if needed
- [ ] Specs updated first if required
- [ ] Ready for review
