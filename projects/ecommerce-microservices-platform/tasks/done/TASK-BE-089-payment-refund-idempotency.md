# Task ID

TASK-BE-089

# Title

payment-service 환불 멱등성 보장 — 중복 환불 요청 방지

# Status

ready

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

payment-service의 `Payment.refund()` 메서드에 멱등성을 추가한다.

현재 상태:
- `Payment.complete()`는 이미 완료 상태이면 early return하여 멱등성이 보장됨
- `Payment.refund()`는 멱등성 체크 없이 매번 상태를 변경 → 중복 `OrderCancelled` 이벤트 수신 시 이중 환불 위험

수정 후: `refund()` 호출 시 이미 `REFUNDED` 상태이면 중복 처리 없이 조기 반환한다.

---

# Scope

## In Scope

- `Payment.refund()` 메서드에 멱등성 체크 추가
- 이미 환불된 결제에 대한 로깅
- 단위 테스트 추가

## Out of Scope

- 이벤트 수준 중복 제거 (eventId 기반)
- 부분 환불 기능
- 결제 금액 검증

---

# Acceptance Criteria

- [ ] `Payment.refund()` 호출 시 이미 `REFUNDED` 상태이면 예외 없이 조기 반환한다
- [ ] 중복 환불 시도는 로그로 기록된다
- [ ] `PaymentRefunded` 이벤트가 중복 발행되지 않는다
- [ ] 정상 환불 흐름은 기존과 동일하게 동작한다
- [ ] 단위 테스트가 중복 환불 시나리오를 검증한다

---

# Related Specs

- `specs/services/payment-service/architecture.md`
- `specs/platform/event-driven-policy.md`

# Related Skills

- `.claude/skills/backend/architecture/ddd.md`
- `.claude/skills/backend/testing-backend.md`

---

# Related Contracts

- `specs/contracts/events/payment-events.md`

---

# Target Service

- `payment-service`

---

# Architecture

Follow:

- `specs/services/payment-service/architecture.md`

---

# Implementation Notes

- `complete()` 메서드의 멱등성 패턴을 참고하여 동일하게 적용
- 상태 체크: `if (this.status == PaymentStatus.REFUNDED) return;`
- 중복 호출 시 warn 레벨 로그 추가
- 이벤트 발행은 상태 변경 후에만 실행되도록 보장

---

# Edge Cases

- COMPLETED → REFUNDED → REFUNDED (중복 호출)
- PENDING 상태에서 refund() 호출 시 처리
- 동시에 두 개의 환불 요청이 도달하는 경우

---

# Failure Scenarios

- 환불 상태 저장 후 이벤트 발행 실패 시 (기존 이벤트 발행 복원력에 의존)
- 동시 환불 요청 시 optimistic lock 충돌

---

# Test Requirements

- 중복 refund() 호출 시 멱등성 단위 테스트
- 정상 환불 흐름 회귀 테스트
- PENDING 상태에서 refund() 호출 예외 테스트

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added
- [ ] Tests passing
- [ ] Contracts updated if needed
- [ ] Specs updated first if required
- [ ] Ready for review
