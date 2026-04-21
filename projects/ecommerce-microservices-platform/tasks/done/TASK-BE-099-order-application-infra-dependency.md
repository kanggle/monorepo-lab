# Task ID

TASK-BE-099

# Title

order-service Application 레이어 Infrastructure 직접 의존 제거 — OrderMetrics, KafkaTemplate 포트 인터페이스 분리

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

order-service의 application 레이어가 infrastructure 레이어를 직접 의존하는 아키텍처 위반을 수정한다.

현재 `OrderPlacementService`, `OrderCancellationService`, `OrderConfirmationService`, `UserWithdrawalOrderService`가 `infrastructure.metrics.OrderMetrics`와 `KafkaTemplate`을 직접 참조하고 있다. DDD 아키텍처 규칙상 application 레이어는 domain 레이어에만 의존해야 하며, infrastructure 의존은 포트(인터페이스)를 통해 역전되어야 한다.

---

# Scope

## In Scope

- application 레이어에 이벤트 발행 포트 인터페이스 정의 (예: `OrderEventPublisher`)
- application 레이어에 메트릭 포트 인터페이스 정의 (예: `OrderMetricsPort`)
- infrastructure 레이어에 포트 구현체 작성 (`KafkaOrderEventPublisher`, `MicrometerOrderMetrics`)
- `OrderPlacementService`, `OrderCancellationService`, `OrderConfirmationService`, `UserWithdrawalOrderService`가 포트 인터페이스만 의존하도록 변경
- 기존 테스트 수정

## Out of Scope

- 다른 서비스의 아키텍처 위반 수정 (TASK-BE-082에서 별도 처리)
- 도메인 모델 변경
- Transactional Outbox 패턴 도입 (별도 태스크)

---

# Acceptance Criteria

- [ ] application 패키지의 어떤 클래스도 `infrastructure` 패키지를 import하지 않는다
- [ ] application 패키지에 `OrderEventPublisher` 포트 인터페이스가 존재한다
- [ ] application 패키지에 `OrderMetricsPort` 포트 인터페이스가 존재한다
- [ ] infrastructure 패키지에 각 포트의 구현체가 존재한다
- [ ] 기존 기능이 동일하게 동작한다 (기존 테스트 통과)
- [ ] 새로운 포트 인터페이스에 대한 단위 테스트가 추가된다

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
- `OrderMetricsPort` 인터페이스는 `application/port/` 패키지에 위치한다.
- 포트 인터페이스 메서드는 도메인 용어로 명명한다 (예: `publishOrderPlaced`, `recordOrderPlaced`).
- infrastructure 구현체는 기존 `OrderMetrics`와 Kafka 발행 로직을 그대로 유지한다.

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

- application 서비스 단위 테스트: 포트 인터페이스 mock으로 대체
- infrastructure 구현체 단위 테스트: Kafka/Micrometer 연동 확인
- ArchUnit 또는 수동 검증: application → infrastructure import 없음 확인

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added
- [ ] Tests passing
- [ ] application 패키지에서 infrastructure import 제거 확인
- [ ] Ready for review
