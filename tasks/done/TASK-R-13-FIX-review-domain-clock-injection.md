# Task ID

TASK-R-13-FIX

# Title

[FIX] review-service ReviewEvent 도메인 이벤트 Clock 주입 (TASK-R-13 미완료)

# Status

review

# Owner

backend

# Task Tags

- fix
- refactor
- domain

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

TASK-R-13에서 `Review` 도메인 모델은 Clock 주입으로 변경되었으나, `ReviewEvent` 도메인 이벤트 클래스가 누락되었다. `ReviewEvent.of()` 메서드(도메인/event 패키지)에서 `Instant.now()`를 직접 호출하고 있어 TASK-R-13의 수락 기준("Review 도메인에서 Instant.now() 직접 호출이 없다")을 위반한다.

`ReviewEvent` 생성 시 Clock을 주입받아 `Instant.now(clock)`을 사용하도록 수정한다.

---

# Scope

## In Scope

- `ReviewEvent.of()` 정적 팩토리 메서드에서 Clock을 파라미터로 받도록 변경
- `ReviewEvent` 생성 시 `Instant.now()` 대신 `Instant.now(clock)` 사용
- `ReviewEvent`를 생성하는 호출 측(application service 또는 domain service)에서 Clock을 전달하도록 수정
- 관련 테스트에서 고정 Clock 사용

## Out of Scope

- `Review` 도메인 모델 Clock 주입 로직 변경 (이미 완료)
- 다른 서비스 변경
- 이벤트 페이로드 필드 변경

---

# Acceptance Criteria

- [ ] `ReviewEvent` 도메인에서 `Instant.now()` 직접 호출이 없다
- [ ] `ReviewEvent` 생성 시 Clock이 주입되어 `Instant.now(clock)`을 사용한다
- [ ] `ReviewEvent`를 생성하는 호출 측에서 Clock을 전달한다
- [ ] 테스트에서 고정 Clock을 사용하여 `occurredAt` 시간을 검증할 수 있다
- [ ] 기존 이벤트 기능이 정상 동작한다

---

# Related Specs

- `specs/services/review-service/architecture.md`

---

# Related Contracts

- `specs/contracts/events/review-events.md`

---

# Edge Cases

- `ReviewEvent`의 정적 팩토리 메서드(`created`, `updated`, `deleted`) 모두 수정 필요
- Clock 파라미터 추가 시 기존 호출 측 코드를 모두 수정해야 함

---

# Failure Scenarios

- 일부 팩토리 메서드에 Clock 누락으로 컴파일 오류 -> 전체 검색 후 수정
- 테스트에서 Clock 미주입으로 실제 시간 의존 -> 고정 Clock 사용 검증

---

# Test Requirements

- `ReviewEvent` 단위 테스트: 고정 Clock으로 `occurredAt` 검증
- `ReviewApplicationService` 단위 테스트: Clock 주입 경로 확인

---

# Definition of Done

- [x] Implementation completed
- [x] Tests added
- [x] Tests passing
- [x] Contracts updated if needed
- [x] Specs updated first if required
- [x] Ready for review
