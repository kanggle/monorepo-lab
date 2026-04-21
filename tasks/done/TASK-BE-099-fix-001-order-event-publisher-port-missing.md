# Task ID

TASK-BE-099-fix-001

# Title

order-service OrderEventPublisher 포트 인터페이스 및 KafkaOrderEventPublisher 구현체 누락 수정

# Status

review

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

TASK-BE-099 리뷰에서 발견된 누락 항목을 수정한다.

TASK-BE-099의 스코프에 명시된 `OrderEventPublisher` 포트 인터페이스와 `KafkaOrderEventPublisher` 인프라 구현체가 구현되지 않았다. 현재 `OrderPlacementService`, `OrderCancellationService`, `UserWithdrawalOrderService`가 Spring의 `ApplicationEventPublisher`를 직접 사용하고 있으며, 이를 포트 인터페이스로 전환해야 한다.

---

# Scope

## In Scope

- `application/port/` 패키지에 `OrderEventPublisher` 포트 인터페이스 정의
  - 메서드: `publishOrderPlaced(OrderPlacedEvent event)`, `publishOrderCancelled(OrderCancelledEvent event)`
- `infrastructure/event/` 패키지에 `KafkaOrderEventPublisher` 구현체 작성
  - 기존 `ApplicationEventPublisher` → Spring 내부 이벤트 발행 로직 유지하되 포트 뒤로 캡슐화
- `OrderPlacementService`, `OrderCancellationService`, `UserWithdrawalOrderService`에서 `ApplicationEventPublisher` 의존을 `OrderEventPublisher` 포트로 교체
- 기존 서비스 테스트 수정 (포트 인터페이스 mock 사용)
- `KafkaOrderEventPublisher` 구현체 단위 테스트 추가

## Out of Scope

- `OrderMetricsPort` 관련 변경 (이미 완료됨)
- `OrderConfirmationService` 변경 (이벤트 발행 없음)
- Transactional Outbox 패턴 도입 (별도 태스크)
- 도메인 모델 변경

---

# Acceptance Criteria

- [ ] `application/port/` 패키지에 `OrderEventPublisher` 포트 인터페이스가 존재한다
- [ ] `infrastructure/event/` 패키지에 `KafkaOrderEventPublisher` 구현체가 존재한다
- [ ] `OrderPlacementService`가 `OrderEventPublisher` 포트만 의존한다 (`ApplicationEventPublisher` import 제거)
- [ ] `OrderCancellationService`가 `OrderEventPublisher` 포트만 의존한다 (`ApplicationEventPublisher` import 제거)
- [ ] `UserWithdrawalOrderService`가 `OrderEventPublisher` 포트만 의존한다 (`ApplicationEventPublisher` import 제거)
- [ ] application 패키지의 어떤 클래스도 Spring `ApplicationEventPublisher`를 직접 import하지 않는다
- [ ] `KafkaOrderEventPublisher` 구현체의 단위 테스트가 존재한다
- [ ] 기존 서비스 테스트가 모두 통과한다

---

# Related Specs

- `specs/services/order-service/architecture.md`
- `specs/platform/architecture-decision-rule.md`

# Related Skills

- `.claude/skills/backend/ddd-port-adapter.md`

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

DDD 의존성 규칙: presentation → application → domain ← infrastructure

---

# Implementation Notes

- `OrderEventPublisher` 인터페이스는 `application/port/` 패키지에 위치한다.
- 포트 인터페이스 메서드는 도메인 용어로 명명한다 (예: `publishOrderPlaced`, `publishOrderCancelled`).
- `KafkaOrderEventPublisher` 구현체는 내부적으로 Spring `ApplicationEventPublisher`를 사용하여 기존 이벤트 핸들링 체인(`OrderEventKafkaHandler`)을 유지한다.
- 기존 `OrderEventKafkaHandler`의 동작은 변경하지 않는다.

---

# Edge Cases

- 포트 구현체가 빈 등록되지 않는 경우 — 애플리케이션 시작 실패
- 포트 인터페이스 변경 시 구현체 동기화 누락

---

# Failure Scenarios

- 포트 인터페이스와 구현체 시그니처 불일치 시 컴파일 에러
- 빈 와이어링 실패 시 컨텍스트 로드 실패

---

# Test Requirements

- application 서비스 단위 테스트: `OrderEventPublisher` 포트 인터페이스 mock으로 대체
- infrastructure 구현체 단위 테스트: `KafkaOrderEventPublisher`가 `ApplicationEventPublisher`에 올바르게 위임하는지 확인

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added
- [ ] Tests passing
- [ ] application 패키지에서 `ApplicationEventPublisher` import 제거 확인
- [ ] Ready for review
