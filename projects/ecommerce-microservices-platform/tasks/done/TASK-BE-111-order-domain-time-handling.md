# Task ID

TASK-BE-111

# Title

order-service 도메인 모델 시간 처리 개선 (Clock 주입 및 Instant 전환)

# Status

ready

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

현재 `Order` 도메인 모델에서 `LocalDateTime.now()`를 직접 호출하여 타임존 정보 없이 시간을 기록하고 있다. 스펙에서 요구하는 ISO 8601 UTC를 준수하고 테스트 가능성을 높이기 위해 `Clock`을 주입하거나 도메인 모델의 시간 타입을 `Instant`로 전환한다.

---

# Scope

## In Scope

- `Order` 도메인 모델의 `createdAt`, `updatedAt` 타입을 `LocalDateTime`에서 `Instant`로 변경
- `paidAt`, `refundedAt` 타입도 `Instant`로 변경
- JPA 엔티티 매핑 업데이트
- 이벤트 생성 시 `Instant` 기반으로 ISO 8601 UTC 문자열 변환
- 기존 테스트 업데이트

## Out of Scope

- DB 마이그레이션으로 기존 데이터 변환 (기존 데이터는 UTC로 간주)
- 다른 서비스의 시간 처리 개선

---

# Acceptance Criteria

- [ ] `Order` 도메인 모델의 시간 필드가 `Instant` 타입이다
- [ ] `Order.create()`에서 `Instant.now(clock)`을 사용한다
- [ ] `confirm()`, `cancel()`, `markPaymentCompleted()`, `markRefunded()`에서 시간 업데이트가 `Instant` 기반이다
- [ ] JPA 엔티티에서 `Instant` ↔ DB timestamp 매핑이 정상 동작한다
- [ ] `OrderCancelledEvent`의 `cancelledAt` 변환이 타임존 안전하다
- [ ] 테스트에서 `Clock.fixed()`를 주입하여 시간 제어가 가능하다
- [ ] 기존 테스트가 모두 통과한다

---

# Related Specs

- `specs/platform/coding-rules.md`
- `specs/platform/observability.md`
- `specs/services/order-service/architecture.md`

# Related Skills

- `.claude/skills/backend/`

---

# Related Contracts

- `specs/contracts/events/order-events.md`
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

- `Clock`은 application 레이어에서 서비스를 통해 도메인에 전달하거나, 도메인 팩토리 메서드의 파라미터로 전달
- 도메인 레이어에 `Clock` 의존성이 직접 들어가지 않도록 주의 (프레임워크 의존 금지)
- JPA에서 `Instant`는 `@Column(columnDefinition = "TIMESTAMP")` 매핑
- Flyway 마이그레이션은 컬럼 타입 변경 불필요 (PostgreSQL timestamp는 Instant와 호환)

---

# Edge Cases

- 기존 DB에 저장된 `LocalDateTime` 값과 새 `Instant` 값의 호환
- 서버 타임존이 UTC가 아닌 환경에서의 동작
- `OrderCancelledEvent` 생성 시 `updatedAt`이 이미 `Instant`이므로 변환 불필요

---

# Failure Scenarios

- JPA `Instant` 매핑 오류 → 애플리케이션 시작 실패 (테스트에서 사전 검증)
- 기존 데이터와 타입 불일치 → PostgreSQL의 자동 변환으로 처리

---

# Test Requirements

- unit test: `Order.create()`, `confirm()`, `cancel()` 등에서 `Clock.fixed()` 사용 시간 검증
- unit test: 이벤트 생성 시 ISO 8601 UTC 형식 확인
- integration test: JPA 엔티티 저장/조회 시 시간 정합성 확인

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added
- [ ] Tests passing
- [ ] Contracts updated if needed
- [ ] Specs updated first if required
- [ ] Ready for review
