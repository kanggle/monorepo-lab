# Task ID

TASK-BE-114

# Title

order-service Order 도메인 모델 userId 검증 추가

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

`Order.create()` 팩토리 메서드에서 `userId`의 null/blank 검증이 누락되어 있다. DDD 원칙상 도메인 불변식은 도메인 레이어에서 보호해야 하므로, `Order.create()`에서 `userId` 유효성 검증을 추가한다.

---

# Scope

## In Scope

- `Order.create()`에 userId null/blank 검증 추가
- `OrderPlacementService`의 중복 userId 검증 제거
- `shippingAddress` null 검증도 도메인에 추가 (누락 시)

## Out of Scope

- userId 형식 검증 (UUID 여부 등)
- 다른 도메인 모델의 검증 강화

---

# Acceptance Criteria

- [ ] `Order.create()`에서 userId가 null이면 `InvalidOrderException`이 발생한다
- [ ] `Order.create()`에서 userId가 blank이면 `InvalidOrderException`이 발생한다
- [ ] `Order.create()`에서 shippingAddress가 null이면 `InvalidOrderException`이 발생한다
- [ ] `OrderPlacementService`의 중복 userId 검증이 제거된다
- [ ] 기존 테스트가 모두 통과한다

---

# Related Specs

- `specs/services/order-service/architecture.md`
- `specs/platform/coding-rules.md`

# Related Skills

- `.claude/skills/backend/`

---

# Related Contracts

- `specs/contracts/http/order-api.md`

---

# Target Service

- `order-service`

---

# Architecture

Follow:

- `specs/services/order-service/architecture.md`

---

# Implementation Notes

- 검증 순서: userId → items → shippingAddress
- `InvalidOrderException`을 사용하여 기존 에러 핸들링과 일관성 유지
- `OrderPlacementService`에서 items 빈 리스트 검증도 이미 도메인에서 수행하므로 함께 제거 가능

---

# Edge Cases

- userId가 공백만 포함된 문자열인 경우 (blank 처리)
- reconstitute()에서는 검증 생략 (DB에서 복원하는 경우이므로)

---

# Failure Scenarios

- 잘못된 userId로 Order 생성 시도 → InvalidOrderException → 400 응답

---

# Test Requirements

- unit test: `Order.create()` userId null/blank 시 예외 발생 확인
- unit test: `Order.create()` shippingAddress null 시 예외 발생 확인

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added
- [ ] Tests passing
- [ ] Contracts updated if needed
- [ ] Specs updated first if required
- [ ] Ready for review
