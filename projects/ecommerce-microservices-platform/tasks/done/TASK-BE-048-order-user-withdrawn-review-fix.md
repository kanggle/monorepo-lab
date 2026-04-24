# Task ID

TASK-BE-048

# Title

TASK-BE-045 리뷰 수정 — 예외 처리 패턴 통일, 로그 언어 일관성

# Status

review

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

TASK-BE-045 리뷰에서 발견된 이슈를 수정한다.

주요 수정 사항:
1. `UserWithdrawnEventConsumer`의 예외 처리 패턴을 다른 컨슈머와 통일 — 현재 `throw e`로 예외를 전파하나, DLQ 설정(TASK-BE-046)이 적용되면 `DefaultErrorHandler`가 재시도 + DLQ 라우팅을 담당하므로, 비즈니스 예외는 전파하고 로그 레벨만 조정
2. `UserWithdrawalOrderService`의 로그 메시지를 영문으로 통일 — 다른 컨슈머/서비스의 영문 로그와 일관성 유지

---

# Scope

## In Scope

- `UserWithdrawnEventConsumer` — 예외 처리 패턴을 다른 컨슈머(StockChanged, PaymentCompleted, PaymentRefunded)와 통일
- `UserWithdrawalOrderService` — 한국어 로그 메시지를 영문으로 변경

## Out of Scope

- 비즈니스 로직 변경
- DLQ 설정 (TASK-BE-046에서 처리)
- 새로운 API 엔드포인트 추가

---

# Acceptance Criteria

- [ ] `UserWithdrawnEventConsumer`의 예외 처리가 다른 컨슈머와 동일한 패턴을 따른다
- [ ] `UserWithdrawalOrderService`의 로그 메시지가 영문으로 통일된다
- [ ] 기존 비즈니스 로직 동작은 변경되지 않는다
- [ ] 테스트가 수정되고 전체 테스트가 통과한다

---

# Related Specs

- `specs/platform/event-driven-policy.md`
- `specs/services/order-service/architecture.md`
- `specs/platform/testing-strategy.md`

# Related Skills

- `.claude/skills/backend/springboot-api.md`
- `.claude/skills/backend/testing-backend.md`
- `.claude/skills/backend/implementation-workflow.md`

---

# Related Contracts

- `specs/contracts/events/user-events.md` (UserWithdrawn — 소비)
- `specs/contracts/events/order-events.md` (OrderCancelled — 발행)

---

# Target Service

- `order-service`

---

# Architecture

Follow:

- `specs/services/order-service/architecture.md`

수정 대상 계층:
- Infrastructure: `UserWithdrawnEventConsumer` — 예외 처리 패턴 조정
- Application: `UserWithdrawalOrderService` — 로그 메시지 영문 통일

---

# Implementation Notes

### 1. 예외 처리 패턴 통일

TASK-BE-046에서 `DefaultErrorHandler`가 추가되면, 컨슈머의 비즈니스 예외는 전파되어 재시도 + DLQ 라우팅된다. 따라서 `UserWithdrawnEventConsumer`의 `handle()` 메서드에서 `catch (Exception e) { throw e; }` 블록을 제거하고, 다른 컨슈머와 동일하게 비즈니스 예외를 자연스럽게 전파하는 패턴으로 변경한다.

### 2. 로그 메시지 영문 통일

현재 (한국어):
```java
log.info("탈퇴 사용자의 활성 주문 없음: userId={}", userId);
log.info("탈퇴 사용자 활성 주문 일괄 취소 완료: userId={}, cancelledCount={}", userId, activeOrders.size());
```

변경 후 (영문):
```java
log.info("No active orders for withdrawn user: userId={}", userId);
log.info("Cancelled active orders for withdrawn user: userId={}, cancelledCount={}", userId, activeOrders.size());
```

---

# Edge Cases

- 예외 처리 패턴 변경 후 기존 동작 유지 확인
- 로그 메시지 변경이 모니터링/알림에 영향 없음 확인 (현재 로그 기반 알림 없음)

---

# Failure Scenarios

- 패턴 변경 시 의도치 않은 예외 누락 → 테스트로 검증

---

# Test Requirements

- 단위 테스트: `UserWithdrawnEventConsumer` — 예외 전파 동작 검증
- 기존 테스트 전체 통과

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added
- [ ] Tests passing
- [ ] Contracts updated if needed
- [ ] Specs updated first if required
- [ ] Ready for review
