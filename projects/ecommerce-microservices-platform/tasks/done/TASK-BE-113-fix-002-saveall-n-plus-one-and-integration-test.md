# Task ID

TASK-BE-113-fix-002

# Title

TASK-BE-113-fix-001 리뷰 수정 — saveAll() N+1 쿼리 제거 및 회원 탈퇴 배치 저장 통합 테스트 추가

# Status

done

# Owner

backend

# Task Tags

- code
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

TASK-BE-113-fix-001 리뷰에서 발견된 두 가지 문제를 수정한다.

1. **`OrderRepositoryImpl.saveAll()` N+1 SELECT 쿼리 문제**: 현재 구현은 version이 있는 주문마다 `jpaRepository.findById()` 를 개별 호출하여 N번의 SELECT가 발생한다. 회원 탈퇴 시 처리되는 주문은 모두 기존 주문(version != null)이므로 실제로는 항상 N번의 SELECT가 발생한다. `jpaRepository.findAllById()` 로 한 번에 조회하는 방식으로 개선해야 한다.

2. **회원 탈퇴 배치 저장 통합 테스트 누락**: TASK-BE-113-fix-001 Scope에서 명시한 통합 테스트(`UserWithdrawalOrderServiceIntegrationTest` 또는 동등한 수준)가 구현되지 않았다. `saveAll()` 경로를 실제 DB와 함께 검증하는 통합 테스트를 추가해야 한다.

---

# Scope

## In Scope

- `OrderRepositoryImpl.saveAll()` 구현 개선
  - 기존: 루프 내 개별 `jpaRepository.findById()` 호출 (N번 SELECT)
  - 변경: `jpaRepository.findAllById(orderIds)` 로 한 번에 조회 후 Map 매핑 (1번 SELECT)
  - version == null인 신규 주문과 version != null인 기존 주문을 분리하여 처리하는 로직은 유지
- `UserWithdrawalOrderServiceIntegrationTest` 추가 (또는 `OrderRepositoryImplIntegrationTest`에 `saveAll()` 테스트 추가)
  - 실제 PostgreSQL(Testcontainers)에서 `saveAll()` 배치 처리 검증
  - N건의 주문이 1번의 SELECT(findAllById) + 배치 UPDATE로 처리되는 것 검증
  - 회원 탈퇴 취소 시나리오 전체 흐름 통합 검증

## Out of Scope

- JPA batch insert/update 설정 변경 (Hibernate `hibernate.jdbc.batch_size` 등)
- `UserWithdrawalOrderService` 로직 변경
- 이벤트 발행 방식 변경
- 다른 서비스의 배치 처리 최적화

---

# Acceptance Criteria

- [ ] `OrderRepositoryImpl.saveAll()` 에서 루프 내 개별 `jpaRepository.findById()` 호출이 제거된다
- [ ] `jpaRepository.findAllById(orderIds)` 로 한 번에 조회하여 Map으로 매핑하는 방식으로 교체된다
- [ ] version == null인 신규 주문은 `mapper.toEntity()` 로 신규 엔티티 생성 경로를 유지한다
- [ ] version != null인 기존 주문은 Map에서 찾은 JPA 엔티티에 `updateFrom()` 을 호출하는 방식으로 처리된다
- [ ] 대상 주문 중 DB에 존재하지 않는 주문 ID가 있을 경우 `IllegalStateException` 을 던진다
- [ ] 회원 탈퇴 배치 저장 통합 테스트가 추가된다 (Testcontainers PostgreSQL 사용)
- [ ] 통합 테스트에서 N건의 주문 배치 취소 후 DB 상태가 모두 CANCELLED로 변경됨을 검증한다
- [ ] 기존 단위 테스트가 모두 통과한다

---

# Related Specs

- `specs/services/order-service/architecture.md`
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

```java
// Before (현재 구현 — 변경 전)
@Override
public List<Order> saveAll(List<Order> orders) {
    List<OrderJpaEntity> entities = new java.util.ArrayList<>(orders.size());
    for (Order order : orders) {
        if (order.getVersion() == null) {
            entities.add(mapper.toEntity(order));
        } else {
            OrderJpaEntity existing = jpaRepository.findById(order.getOrderId())  // N번 SELECT
                    .orElseThrow(...);
            existing.updateFrom(order);
            entities.add(existing);
        }
    }
    List<OrderJpaEntity> savedEntities = jpaRepository.saveAll(entities);
    return savedEntities.stream().map(mapper::toDomain).toList();
}

// After (목표 구현)
@Override
public List<Order> saveAll(List<Order> orders) {
    List<String> existingIds = orders.stream()
            .filter(o -> o.getVersion() != null)
            .map(Order::getOrderId)
            .toList();

    Map<String, OrderJpaEntity> existingMap = existingIds.isEmpty()
            ? Map.of()
            : jpaRepository.findAllById(existingIds).stream()
                    .collect(Collectors.toMap(OrderJpaEntity::getOrderId, e -> e));  // 1번 SELECT

    List<OrderJpaEntity> entities = new ArrayList<>(orders.size());
    for (Order order : orders) {
        if (order.getVersion() == null) {
            entities.add(mapper.toEntity(order));
        } else {
            OrderJpaEntity existing = existingMap.get(order.getOrderId());
            if (existing == null) {
                throw new IllegalStateException("Order not found for update: " + order.getOrderId());
            }
            existing.updateFrom(order);
            entities.add(existing);
        }
    }
    List<OrderJpaEntity> savedEntities = jpaRepository.saveAll(entities);
    return savedEntities.stream().map(mapper::toDomain).toList();
}
```

통합 테스트 추가 위치:
- `OrderRepositoryImplIntegrationTest` 에 `saveAll_multipleExistingOrders_executesOneSelectAndBatchUpdate()` 테스트 추가 또는
- 별도 `UserWithdrawalOrderServiceIntegrationTest` 생성

---

# Edge Cases

- 주문 목록이 비어 있는 경우 — 빈 리스트 반환
- 전부 신규 주문(version == null)인 경우 — `findAllById` 호출 생략
- 혼합(신규 + 기존) 주문이 포함된 경우 — 각각 올바른 경로로 처리
- 대상 주문 중 DB에 존재하지 않는 주문 ID가 포함된 경우 — `IllegalStateException` 발생

---

# Failure Scenarios

- `findAllById` 결과에 일부 주문 ID가 없는 경우 → `IllegalStateException` 발생, 전체 트랜잭션 롤백
- `jpaRepository.saveAll()` 중 낙관적 락 충돌 발생 → `JpaOptimisticLockingFailureException`, 전체 트랜잭션 롤백

---

# Test Requirements

- unit test: 기존 `UserWithdrawalOrderServiceTest` 모두 통과 유지
- integration test: `OrderRepositoryImplIntegrationTest` 또는 `UserWithdrawalOrderServiceIntegrationTest`
  - N건의 기존 주문에 대해 `saveAll()` 호출 시 SELECT 1회(또는 0회, 신규 주문이 없는 경우) 발생 검증
  - 모든 주문의 상태가 DB에 올바르게 반영됨을 검증

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added/updated
- [ ] Tests passing
- [ ] Contracts updated if needed
- [ ] Specs updated first if required
- [ ] Ready for review
