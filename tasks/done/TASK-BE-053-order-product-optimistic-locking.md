# Task ID

TASK-BE-053

# Title

order-service, product-service 동시성 제어 — Optimistic Locking 적용

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

order-service의 Order 애그리거트와 product-service의 재고 조정에 Optimistic Locking(@Version)을 적용하여 동시 요청 시 데이터 정합성을 보장한다.

현재 상태: 동시에 결제 확인 + 취소 요청이 들어오면 마지막 쓰기가 이전 상태를 덮어쓰는 Lost Update 문제가 존재한다. product-service의 재고 차감도 동일한 문제를 가진다.

---

# Scope

## In Scope

- Order 엔티티에 `@Version` 필드 추가 및 마이그레이션
- ProductVariantJpaEntity의 기존 `@Version` 필드를 활용한 재고 조정 잠금 적용
- OptimisticLockException 발생 시 적절한 예외 처리 (409 Conflict 반환)
- 동시성 테스트 추가

## Out of Scope

- Pessimistic Locking 전략
- 분산 락 (Redis/ZooKeeper 기반)
- user-service 주소 기본값 동시성 (별도 태스크)

---

# Acceptance Criteria

- [ ] Order 엔티티에 `@Version` 필드가 추가되고 DB 마이그레이션이 적용된다
- [ ] 동시 주문 상태 변경 시 OptimisticLockException이 발생하고 409 Conflict를 반환한다
- [ ] product-service 재고 조정 시 동시 요청에 대해 OptimisticLockException이 발생한다
- [ ] 동시성 시나리오를 검증하는 통합 테스트가 추가된다
- [ ] 기존 단위 테스트가 모두 통과한다

---

# Related Specs

- `specs/services/order-service/architecture.md`
- `specs/services/product-service/architecture.md`
- `specs/platform/testing-strategy.md`

# Related Skills

- `.claude/skills/backend/architecture/ddd.md`
- `.claude/skills/backend/testing-backend.md`

---

# Related Contracts

- `specs/contracts/http/order-api.md`
- `specs/contracts/http/product-api.md`

---

# Target Service

- `order-service`
- `product-service`

---

# Architecture

Follow:

- `specs/services/order-service/architecture.md`
- `specs/services/product-service/architecture.md`

---

# Implementation Notes

- Order JPA 엔티티에 `@Version private Long version;` 추가
- Flyway 마이그레이션으로 `version` 컬럼 추가 (DEFAULT 0)
- ProductVariantJpaEntity에 이미 `@Version` 필드 존재 — InventoryRepositoryAdapter에서 이를 활용하도록 수정
- GlobalExceptionHandler에 OptimisticLockingFailureException → 409 매핑 추가
- 이벤트 컨슈머(PaymentCompleted, StockChanged 등)에서 OptimisticLockException 발생 시 Kafka 재시도에 의존

---

# Edge Cases

- 결제 확인과 취소가 동시에 도달하는 경우
- 동일 상품에 대한 동시 재고 차감 요청
- 이벤트 컨슈머 재시도 시 이미 상태가 변경된 경우 (멱등성 확인 필요)

---

# Failure Scenarios

- OptimisticLockException 발생 시 HTTP 요청은 409 반환, Kafka 컨슈머는 재시도
- 버전 충돌 후 재시도 시에도 비즈니스 규칙 위반이면 적절한 예외 반환
- 마이그레이션 실패 시 롤백 가능해야 함

---

# Test Requirements

- Order 동시 상태 변경 통합 테스트 (CountDownLatch 활용)
- 재고 동시 차감 통합 테스트
- OptimisticLockException → 409 응답 단위 테스트
- 기존 테스트 회귀 확인

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added
- [ ] Tests passing
- [ ] Contracts updated if needed
- [ ] Specs updated first if required
- [ ] Ready for review
