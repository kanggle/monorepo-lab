# Task ID

TASK-BE-113

# Title

order-service 회원 탈퇴 시 주문 취소 배치 저장 최적화

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

`UserWithdrawalOrderService.cancelOrdersForWithdrawnUser()`에서 활성 주문을 하나씩 `save()`하고 이벤트를 발행한다. 주문이 많은 경우 DB round-trip과 Kafka 전송이 N번 발생하여 성능 저하가 발생할 수 있다. 배치 저장으로 최적화한다.

---

# Scope

## In Scope

- `OrderRepository`에 `saveAll()` 메서드 추가
- `UserWithdrawalOrderService`에서 배치 저장 적용
- 이벤트 발행을 일괄 수집 후 처리

## Out of Scope

- 다른 서비스의 배치 처리 최적화
- JPA batch insert/update 설정 변경 (Hibernate 설정)

---

# Acceptance Criteria

- [ ] `OrderRepository`에 `saveAll(List<Order>)` 메서드가 추가된다
- [ ] `OrderRepositoryImpl`에서 `jpaRepository.saveAll()`을 사용하여 배치 저장한다
- [ ] `UserWithdrawalOrderService`에서 모든 주문 취소 후 한 번에 저장한다
- [ ] 이벤트는 저장 완료 후 일괄 발행된다
- [ ] 기존 동작과 결과가 동일하다 (각 주문별 메트릭, 이벤트 발행)
- [ ] 기존 테스트가 모두 통과한다

---

# Related Specs

- `specs/services/order-service/architecture.md`
- `specs/platform/event-driven-policy.md`

# Related Skills

- `.claude/skills/backend/`

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

- `OrderRepository` 인터페이스에 `List<Order> saveAll(List<Order> orders)` 추가
- 취소 처리: 먼저 모든 주문 cancel() 호출 → saveAll() → 이벤트 일괄 발행
- 메트릭은 각 주문별로 기록 (집계 정확성 유지)
- 단일 트랜잭션 내에서 처리

---

# Edge Cases

- 활성 주문이 0건인 경우 (기존 로직대로 조기 리턴)
- 활성 주문이 매우 많은 경우 (JPA flush 시 메모리 사용량 증가 가능)
- 배치 저장 중 일부 주문에서 낙관적 락 충돌 발생

---

# Failure Scenarios

- saveAll() 중 OptimisticLockingFailureException 발생 → 전체 트랜잭션 롤백
- 이벤트 발행 실패 → 메트릭 기록 후 로그 (outbox 패턴 적용 시 해결)

---

# Test Requirements

- unit test: 배치 저장 로직 및 이벤트 일괄 발행 검증
- integration test: 다수 주문 취소 시 전체 처리 확인

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added
- [ ] Tests passing
- [ ] Contracts updated if needed
- [ ] Specs updated first if required
- [ ] Ready for review
