# Task ID

TASK-BE-106

# Title

order-service OrderCancelledEvent cancelledAt 시각 불일치 수정 — 도메인 취소 시각 사용

# Status

review

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

`OrderCancelledEvent.of()`에서 `cancelledAt`에 `Instant.now()`를 사용하고 있어, 도메인의 `Order.cancel()`에서 설정한 실제 취소 시각(`updatedAt`)과 불일치하는 문제를 수정한다.

이벤트의 `cancelledAt`은 주문이 실제로 취소된 시점을 나타내야 하므로, `Order` 도메인 객체의 취소 시각을 전달받아 사용해야 한다.

---

# Scope

## In Scope

- `OrderCancelledEvent.of()` 메서드가 `Order` 도메인의 취소 시각을 인자로 받도록 수정
- `OrderCancellationService`, `UserWithdrawalOrderService`에서 이벤트 생성 시 도메인 시각 전달
- `occurred_at`과 `cancelledAt`의 의미 구분 명확화

## Out of Scope

- `Order` 도메인의 `LocalDateTime` → `Instant` 전환 (TASK-BE-102에서 다룸)
- `OrderPlacedEvent`의 시각 처리 (이미 도메인 시각 사용 여부 확인 필요)

---

# Acceptance Criteria

- [ ] `OrderCancelledEvent`의 `cancelledAt`이 `Order` 도메인의 취소 시각과 일치한다
- [ ] `occurred_at`은 이벤트 발생 시각(Instant.now())으로 유지된다
- [ ] `cancelledAt`은 ISO 8601 형식의 문자열이다
- [ ] 기존 테스트가 수정되고 새 테스트가 추가된다

---

# Related Specs

- `specs/services/order-service/architecture.md`

# Related Skills

- `.claude/skills/backend/domain-event.md`

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

- `OrderCancelledEvent.of(Order order)` 시그니처에서 `order.getUpdatedAt()`을 사용하여 `cancelledAt`을 설정한다.
- `Order.cancel()` 호출 후 `updatedAt`이 취소 시각으로 설정되어 있는지 확인한다.
- `LocalDateTime` → `Instant` 변환이 필요한 경우, UTC 기준으로 변환한다 (TASK-BE-102 완료 전까지 임시 처리).

---

# Edge Cases

- `Order.cancel()` 후 `updatedAt`이 null인 경우 — `cancel()` 메서드에서 반드시 설정되므로 발생하지 않음
- `LocalDateTime`과 `Instant` 간 시간대 변환 차이 — UTC 기준 일관 처리

---

# Failure Scenarios

- 도메인 시각과 이벤트 시각의 미세한 차이로 인한 이벤트 순서 역전 — `occurred_at`으로 이벤트 순서를 판단하므로 영향 없음

---

# Test Requirements

- 단위 테스트: `OrderCancelledEvent.of()` 호출 시 `cancelledAt`이 도메인 취소 시각과 일치하는지 확인
- 단위 테스트: `occurred_at`과 `cancelledAt`이 서로 다른 값일 수 있음을 확인
- 직렬화 테스트: 이벤트 JSON에 올바른 `cancelledAt` 값이 포함되는지 확인

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added
- [ ] Tests passing
- [ ] Ready for review
