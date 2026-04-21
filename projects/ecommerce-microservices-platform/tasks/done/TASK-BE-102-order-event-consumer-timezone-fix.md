# Task ID

TASK-BE-102

# Title

order-service 이벤트 컨슈머 ZoneId.systemDefault() 제거 — UTC 고정 시간대 전환

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

`PaymentCompletedEventConsumer`와 `PaymentRefundedEventConsumer`에서 ISO 8601 타임스탬프를 `LocalDateTime`으로 변환할 때 `ZoneId.systemDefault()`를 사용하고 있어, 배포 환경에 따라 시간이 달라질 수 있는 문제를 수정한다.

`ZoneOffset.UTC`로 변경하여 모든 환경에서 동일한 시간 변환을 보장한다.

---

# Scope

## In Scope

- `PaymentCompletedEventConsumer.parsePaidAt()` — `ZoneId.systemDefault()` → `ZoneOffset.UTC`
- `PaymentRefundedEventConsumer.parseRefundedAt()` — `ZoneId.systemDefault()` → `ZoneOffset.UTC`
- 기존 테스트 수정

## Out of Scope

- 다른 서비스의 시간대 처리 변경
- 도메인 모델의 시간 타입 변경 (LocalDateTime → Instant 등)
- DB 저장 시간대 변경

---

# Acceptance Criteria

- [ ] `PaymentCompletedEventConsumer`에서 `ZoneId.systemDefault()`가 사용되지 않는다
- [ ] `PaymentRefundedEventConsumer`에서 `ZoneId.systemDefault()`가 사용되지 않는다
- [ ] UTC 기준으로 ISO 8601 문자열이 `LocalDateTime`으로 변환된다
- [ ] 기존 테스트가 수정되어 통과한다

---

# Related Specs

- `specs/services/order-service/architecture.md`

# Related Skills

- `.claude/skills/backend/event-consumer.md`

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

- `LocalDateTime.ofInstant(Instant.parse(str), ZoneOffset.UTC)` 형태로 변경한다.
- 프로젝트 전반적으로 시간은 UTC 기준으로 저장/처리하는 것이 일반적이므로, 이 변경이 기존 데이터와 호환되는지 확인한다.

---

# Edge Cases

- 이벤트 타임스탬프에 시간대 오프셋이 포함된 경우 (예: `+09:00`) — `Instant.parse`가 처리
- 이벤트 타임스탬프가 Z(UTC) 접미사인 경우 — 정상 처리

---

# Failure Scenarios

- 파싱 불가능한 타임스탬프 형식 — 기존 `IllegalArgumentException` 처리 유지

---

# Test Requirements

- 단위 테스트: UTC 오프셋이 포함된 타임스탬프 변환 확인
- 단위 테스트: 다양한 ISO 8601 형식 처리 확인

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added
- [ ] Tests passing
- [ ] Ready for review
