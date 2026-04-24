# Task ID

TASK-R-13

# Title

review-service 도메인에 Clock 주입 패턴 적용

# Status

review

# Owner

backend

# Task Tags

- refactor

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

review-service의 Review 도메인에서 Instant.now()를 직접 호출하고 있다. 이는 시간 의존 로직의 테스트를 어렵게 만들고, 도메인 레이어의 외부 의존성을 높인다.

java.time.Clock을 주입하여 시간 생성을 제어 가능하게 변경하고, 테스트에서 고정 시간을 사용할 수 있도록 한다.

---

# Scope

## In Scope

- Review 도메인에서 Instant.now() 직접 호출을 Clock 기반으로 변경
- application service 또는 도메인 서비스에 Clock 주입
- Clock 빈 등록 (production: Clock.systemUTC(), test: 고정 Clock)
- 기존 테스트를 고정 Clock으로 개선

## Out of Scope

- 다른 서비스의 Clock 주입
- 타임존 변경
- 비즈니스 로직 변경
- 시간 관련 새로운 기능 추가

---

# Acceptance Criteria

- [ ] Review 도메인에서 Instant.now() 직접 호출이 없다
- [ ] Clock이 주입되어 시간 생성에 사용된다
- [ ] production 환경에서 Clock.systemUTC()가 사용된다
- [ ] 테스트에서 고정 Clock을 주입하여 시간 의존 로직을 검증할 수 있다
- [ ] 기존 기능이 정상 동작한다

---

# Related Specs

- `specs/services/review-service/architecture.md`

# Related Skills

- `.claude/skills/backend/architecture/ddd.md`
- `.claude/skills/backend/testing-backend.md`

---

# Related Contracts

- 없음 (내부 구현 변경만 해당)

---

# Target Service

- `review-service`

---

# Architecture

Follow:

- `specs/services/review-service/architecture.md`

Boundary Rules:
- "application layer coordinates use-cases and transaction boundaries"
- "domain layer owns review rules and rating invariants"

---

# Implementation Notes

- 도메인 엔티티에 Clock을 직접 주입하는 것은 DDD 안티패턴이므로, application service에서 Clock으로 시간을 생성하여 도메인 메서드에 전달하는 방식 권장
- 예: `review.create(rating, content, Instant.now(clock))` 또는 `review.create(rating, content, clock.instant())`
- @Configuration에서 `@Bean Clock clock() { return Clock.systemUTC(); }` 등록
- 테스트에서는 `Clock.fixed(...)` 사용

---

# Edge Cases

- Clock이 null인 경우 방어 코드 (생성자에서 검증)
- 여러 도메인 메서드에서 시간을 사용하는 경우 동일 Clock 인스턴스 사용으로 일관성 보장

---

# Failure Scenarios

- Clock 빈 미등록으로 인한 Spring context 로드 실패
- Clock 주입 누락으로 인한 NullPointerException

---

# Test Requirements

- 단위 테스트: 고정 Clock을 사용하여 Review 생성/수정 시 정확한 시간 설정 검증
- 단위 테스트: application service에 Clock이 올바르게 주입되는지 검증
- 통합 테스트: Spring context가 정상 로드되고 Clock 빈이 존재하는지 확인

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added
- [ ] Tests passing
- [ ] Contracts updated if needed
- [ ] Specs updated first if required
- [ ] Ready for review
