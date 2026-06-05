# Task ID

TASK-BE-137

# Title

membership-service — CancelSubscriptionUseCaseTest / ExpireSubscriptionUseCaseTest 추가 (fix TASK-BE-136)

# Status

ready

# Owner

backend

# Task Tags

- refactor
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

---

# Goal

TASK-BE-136 리뷰에서 발견된 결함: `CancelSubscriptionUseCase`와 `ExpireSubscriptionUseCase`가 `SubscriptionStatusHistoryRecorder`로 교체됐으나 두 UseCase에 대한 단위 테스트(`CancelSubscriptionUseCaseTest`, `ExpireSubscriptionUseCaseTest`)가 존재하지 않는다.

TASK-BE-136 Acceptance Criteria 항목 "기존 `ActivateSubscriptionUseCaseTest`, `CancelSubscriptionUseCaseTest`, `ExpireSubscriptionUseCaseTest`가 모두 통과한다"를 충족하기 위해 두 UseCase 단위 테스트를 신규 생성한다.

---

# Scope

## In Scope

- 신규 파일: `apps/membership-service/src/test/java/com/example/membership/application/CancelSubscriptionUseCaseTest.java`
  - Mock: `SubscriptionRepository`, `SubscriptionStatusHistoryRecorder`, `MembershipEventPublisher`
  - 시나리오: 정상 해지, 구독 미발견(SubscriptionNotFoundException), 타인 구독 해지 시도(SubscriptionPermissionDeniedException), ACTIVE 아닌 구독 해지 시도(SubscriptionNotActiveException)
  - Korean `@DisplayName` 필수, 3-part 메서드 이름 필수
  - `historyRecorder.recordTransition(...)` 호출 검증 포함

- 신규 파일: `apps/membership-service/src/test/java/com/example/membership/application/ExpireSubscriptionUseCaseTest.java`
  - Mock: `SubscriptionRepository`, `SubscriptionStatusHistoryRecorder`, `MembershipEventPublisher`
  - 시나리오: 정상 만료, ACTIVE 아닌 구독 스킵(이벤트·히스토리 미발행), 구독 미발견(SubscriptionNotFoundException)
  - Korean `@DisplayName` 필수, 3-part 메서드 이름 필수
  - `historyRecorder.recordTransition(...)` 호출 검증 포함

## Out of Scope

- 프로덕션 코드 변경 없음
- `SubscriptionStatusHistoryRecorder` 자체 변경 없음
- 기타 UseCase 테스트 변경 없음

---

# Acceptance Criteria

- [ ] `CancelSubscriptionUseCaseTest.java`가 `com.example.membership.application` 패키지에 추가된다
  - [ ] `historyRecorder`가 mock으로 주입된다 (`@Mock SubscriptionStatusHistoryRecorder`)
  - [ ] 정상 해지 시 `historyRecorder.recordTransition(s, from, CANCELLED, "USER_CANCEL", "USER", ...)` 1회 호출 검증
  - [ ] 정상 해지 시 `eventPublisher.publishCancelled(s)` 1회 호출 검증
  - [ ] 구독 미발견 시 `SubscriptionNotFoundException` 던짐 검증
  - [ ] 타인 구독 해지 시 `SubscriptionPermissionDeniedException` 던짐 검증
  - [ ] ACTIVE 아닌 구독 해지 시 `SubscriptionNotActiveException` 던짐 검증
- [ ] `ExpireSubscriptionUseCaseTest.java`가 `com.example.membership.application` 패키지에 추가된다
  - [ ] `historyRecorder`가 mock으로 주입된다 (`@Mock SubscriptionStatusHistoryRecorder`)
  - [ ] ACTIVE 구독 정상 만료 시 `historyRecorder.recordTransition(s, from, EXPIRED, "SCHEDULED_EXPIRE", "SYSTEM", ...)` 1회 호출 검증
  - [ ] ACTIVE 구독 정상 만료 시 `eventPublisher.publishExpired(s)` 1회 호출 검증
  - [ ] 비ACTIVE 구독은 히스토리·이벤트 미발행 검증 (skip)
  - [ ] 구독 미발견 시 `SubscriptionNotFoundException` 던짐 검증
- [ ] Korean `@DisplayName` 사용, 3-part 메서드 이름 (`{scenario}_{condition}_{expectedResult}`) 준수
- [ ] `./gradlew :apps:membership-service:test` 통과

---

# Related Specs

- `specs/services/membership-service/architecture.md`
- `specs/services/membership-service/overview.md`
- `platform/testing-strategy.md`

# Related Skills

- `.claude/skills/backend/refactoring/SKILL.md`
- `.claude/skills/backend/testing-backend/SKILL.md`

---

# Related Contracts

없음 — 행위 변경 없음.

---

# Target Service

- `membership-service`

---

# Architecture

Follow:

- `specs/services/membership-service/architecture.md`
- 단위 테스트 — Spring context 미기동, Mockito mock 의존성만 사용

---

# Edge Cases

- `from = s.getStatus()`은 `cancel()` / `expire()` 호출 전에 캡처됨 — 각 UseCase의 기존 코드 구조 그대로.
- `ExpireSubscriptionUseCase`: ACTIVE 아닌 구독에 대해 early return — `historyRecorder.recordTransition()` 및 `eventPublisher.publishExpired()` 미호출 검증.

---

# Failure Scenarios

- `historyRepository.append` / `recordTransition` 실패 → 예외 전파 (기존 동작과 동일). 이 시나리오는 `SubscriptionStatusHistoryRecorderTest`에서 이미 검증됨.

---

# Test Requirements

- `CancelSubscriptionUseCaseTest`: Mockito unit test, 4개 이상 시나리오, Korean @DisplayName, 3-part method names
- `ExpireSubscriptionUseCaseTest`: Mockito unit test, 3개 이상 시나리오, Korean @DisplayName, 3-part method names
- `@MockitoSettings(strictness = Strictness.STRICT_STUBS)` 적용 (기존 테스트와 동일 패턴)
- 기존 `ActivateSubscriptionUseCaseTest`, `SubscriptionStatusHistoryRecorderTest` 회귀 통과 확인

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests passing
- [ ] Contracts updated if needed (해당 없음)
- [ ] Specs updated first if required (해당 없음)
- [ ] Ready for review
