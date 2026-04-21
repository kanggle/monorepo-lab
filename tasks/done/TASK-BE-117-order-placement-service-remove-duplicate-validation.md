# Task ID

TASK-BE-117

# Title

order-service OrderPlacementService 중복 검증 제거

# Status

review

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

`OrderPlacementService.placeOrder()`에서 `userId`와 `items` 유효성 검증을 수행하고 있으나, 이는 `Order.create()` 도메인 메서드에서 이미 수행하는 검증과 중복이다. DDD 구조에서는 도메인 레이어의 불변식 검증을 신뢰하므로 서비스 레이어의 중복 검증을 제거한다.

TASK-BE-114(userId 검증 추가)가 완료된 후 진행해야 한다.

---

# Scope

## In Scope

- `OrderPlacementService.placeOrder()`에서 userId, items 중복 검증 코드 제거
- 도메인 `Order.create()`의 검증에 위임

## Out of Scope

- 도메인 모델의 검증 로직 변경
- 컨트롤러 레이어의 Bean Validation 변경

---

# Acceptance Criteria

- [ ] `OrderPlacementService.placeOrder()`에서 userId null/blank 검증 코드가 제거된다
- [ ] `OrderPlacementService.placeOrder()`에서 items null/empty 검증 코드가 제거된다
- [ ] 잘못된 입력 시 `Order.create()`에서 `InvalidOrderException`이 발생하여 동일한 에러 응답이 반환된다
- [ ] 기존 테스트가 모두 통과한다

---

# Related Specs

- `specs/services/order-service/architecture.md`

# Related Skills

- `.claude/skills/backend/`

---

# Related Contracts

- N/A

---

# Target Service

- `order-service`

---

# Architecture

Follow:

- `specs/services/order-service/architecture.md`

---

# Implementation Notes

- 이 태스크는 TASK-BE-114 완료 후 진행 (userId 검증이 도메인에 먼저 추가되어야 함)
- 제거 대상 코드: `OrderPlacementService.placeOrder()` 30~35줄의 if 블록 2개
- 에러 응답은 기존과 동일 (`InvalidOrderException` → `INVALID_ORDER_REQUEST` 400)

---

# Edge Cases

- 없음 (단순 중복 코드 제거)

---

# Failure Scenarios

- 없음 (도메인 검증이 동일한 예외를 발생시킴)

---

# Test Requirements

- 기존 테스트 통과 확인
- unit test: 서비스에서 검증 없이 도메인으로 위임됨을 확인

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added
- [ ] Tests passing
- [ ] Contracts updated if needed
- [ ] Specs updated first if required
- [ ] Ready for review
