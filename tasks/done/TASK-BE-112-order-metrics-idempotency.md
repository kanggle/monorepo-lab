# Task ID

TASK-BE-112

# Title

order-service 상태 변경 메트릭 멱등성 보장

# Status

done

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

If any section is missing or incomplete, this task must not be implemented.

---

# Goal

`OrderConfirmationService.confirmOrder()`에서 `Order.confirm()`이 이미 CONFIRMED 상태일 때 조기 리턴(멱등 처리)하지만, 메트릭(`recordOrderConfirmed`, `recordStatusTransition`)은 실제 상태 변경 여부와 무관하게 항상 호출된다. 중복 이벤트로 인해 메트릭이 부풀려지는 문제를 해결한다.

---

# Scope

## In Scope

- `Order.confirm()`이 실제 상태 변경 여부를 반환하도록 수정
- `OrderConfirmationService`에서 상태가 실제로 변경된 경우에만 메트릭 기록
- 동일한 패턴이 필요한 다른 서비스 메서드에도 적용 검토

## Out of Scope

- 메트릭 수집 인프라 변경
- Grafana 대시보드 수정

---

# Acceptance Criteria

- [x] `Order.confirm()`이 boolean을 반환한다 (true: 상태 변경됨, false: 이미 CONFIRMED)
- [x] `OrderConfirmationService`에서 `confirm()` 반환값이 true일 때만 메트릭을 기록한다
- [x] 이미 CONFIRMED인 주문에 대해 `confirmOrder()` 호출 시 메트릭이 증가하지 않는다
- [x] 기존 테스트가 모두 통과한다

---

# Related Specs

- `specs/services/order-service/observability.md`
- `specs/services/order-service/architecture.md`

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

- `Order.confirm()` 반환 타입을 `void` → `boolean`으로 변경
- 이미 CONFIRMED인 경우 `return false`, 실제 변경 시 `return true`
- 도메인 모델의 의미를 유지하면서 상태 변경 결과를 전달

---

# Edge Cases

- `confirm()`이 `false`를 반환하는 경우에도 `orderRepository.save()`는 호출해도 무방 (변경 없으므로 JPA dirty check에서 UPDATE 미발생)
- 동시에 같은 주문에 대해 confirmOrder가 호출되는 경우 (낙관적 락으로 보호)

---

# Failure Scenarios

- 메트릭 기록 자체의 실패는 비즈니스 로직에 영향 없음 (기존과 동일)

---

# Test Requirements

- unit test: `Order.confirm()` 반환값 검증 (PENDING→CONFIRMED: true, CONFIRMED→CONFIRMED: false)
- unit test: `OrderConfirmationService`에서 메트릭 호출 조건 검증

---

# Definition of Done

- [x] Implementation completed
- [x] Tests added
- [x] Tests passing
- [x] Contracts updated if needed
- [x] Specs updated first if required
- [x] Ready for review
