# Task ID

TASK-BE-070

# Title

OrderPlacedEventConsumer 누락 테스트 보완 — null payload, null 필드 경로

# Status

done

# Owner

backend

# Task Tags

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

---

# Goal

TASK-BE-065 리뷰에서 발견된 Minor 이슈 수정. OrderPlacedEventConsumer의 방어 검증 로직 중 테스트가 누락된 경로에 대한 테스트를 추가한다.

---

# Scope

## In Scope

- payment-service OrderPlacedEventConsumerTest: null payload 이벤트 수신 시 skip 테스트 추가
- payment-service OrderPlacedEventConsumerTest: orderId가 null인 이벤트 수신 시 skip 테스트 추가
- payment-service OrderPlacedEventConsumerTest: userId가 null인 이벤트 수신 시 skip 테스트 추가

## Out of Scope

- 프로덕션 코드 변경 (검증 로직은 이미 구현됨)
- 다른 서비스의 테스트 보완

---

# Acceptance Criteria

- [ ] null payload 이벤트 수신 시 processPayment를 호출하지 않는 테스트가 존재한다
- [ ] orderId가 null인 이벤트 수신 시 processPayment를 호출하지 않는 테스트가 존재한다
- [ ] userId가 null인 이벤트 수신 시 processPayment를 호출하지 않는 테스트가 존재한다

---

# Related Specs

- `specs/platform/testing-strategy.md`

# Related Contracts

_(없음)_

---

# Edge Cases

- payload 자체가 null인 경우
- payload 내부 필드 일부만 null인 경우 (orderId만, userId만)

---

# Failure Scenarios

- 테스트 추가 시 기존 테스트가 깨지는 경우 → 기존 테스트와 독립적으로 작성

---

# Test Requirements

- OrderPlacedEventConsumerTest에 3개 테스트 메서드 추가
- `@DisplayName`에 한국어 설명 포함
