# Task ID

TASK-BE-129

# Title

TASK-BE-128 후속 수정 — AdminOutboxFailureHandlerConfigTest / MembershipOutboxFailureHandlerConfigTest 메서드 레벨 @DisplayName 한국어 변환

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

---

# Goal

TASK-BE-128 리뷰에서 발견된 1가지 문제를 수정한다:

**메서드 레벨 @DisplayName이 영문(메서드명 복사)으로 작성됨**: `platform/testing-strategy.md`는 "Use `@DisplayName` with Korean descriptions for test readability"를 명시하고, `.claude/skills/backend/testing-backend/SKILL.md`도 "Use Korean display names to describe the business behavior being tested"를 요구한다. 두 테스트 파일의 메서드 레벨 `@DisplayName` 값이 메서드명을 그대로 영문으로 복사한 형태로 작성되어 있어 프로젝트 컨벤션을 위반한다. 한국어 설명으로 변환한다.

---

# Scope

## In Scope

- `apps/admin-service/src/test/java/com/example/admin/infrastructure/messaging/AdminOutboxFailureHandlerConfigTest.java` — 3개 테스트 메서드의 `@DisplayName` 을 한국어 설명으로 변환
- `apps/membership-service/src/test/java/com/example/membership/infrastructure/messaging/MembershipOutboxFailureHandlerConfigTest.java` — 3개 테스트 메서드의 `@DisplayName` 을 한국어 설명으로 변환

## Out of Scope

- 테스트 로직 변경 — 기능은 정상 동작 중이므로 `@DisplayName` 텍스트만 수정
- 클래스 레벨 `@DisplayName` — 이미 한국어로 올바르게 작성됨

---

# Acceptance Criteria

- [ ] `AdminOutboxFailureHandlerConfigTest`의 각 `@Test` 메서드의 `@DisplayName`이 한국어 비즈니스 설명으로 작성된다
- [ ] `MembershipOutboxFailureHandlerConfigTest`의 각 `@Test` 메서드의 `@DisplayName`이 한국어 비즈니스 설명으로 작성된다
- [ ] 메서드 명(3-파트 `{scenario}_{condition}_{expectedResult}` 패턴)은 변경하지 않는다
- [ ] `./gradlew :apps:admin-service:test :apps:membership-service:test` 통과

---

# Related Specs

- platform/testing-strategy.md

# Related Skills

- .claude/skills/backend/testing-backend/SKILL.md

---

# Related Contracts

없음 — 코드 변경 없음, @DisplayName 텍스트만 수정

---

# Target Service

- admin-service
- membership-service

---

# Architecture

Follow:

- 각 서비스의 specs/services/<service>/architecture.md

---

# Implementation Notes

## 변환 대상 메서드 및 예시 한국어 @DisplayName

### AdminOutboxFailureHandlerConfigTest

| 메서드명 | 현재 @DisplayName (영문) | 변환 후 @DisplayName (한국어) |
|---|---|---|
| `outboxFailureHandler_singleFailure_incrementsCounterTaggedWithEventType` | `"outboxFailureHandler_singleFailure_incrementsCounterTaggedWithEventType"` | `"단일 publish 실패 시 event_type 태그와 함께 카운터가 1 증가한다"` |
| `outboxFailureHandler_multipleEventTypes_registersSeparateCountersPerTag` | `"outboxFailureHandler_multipleEventTypes_registersSeparateCountersPerTag"` | `"여러 event_type으로 실패 시 태그별 독립 카운터가 각각 증가한다"` |
| `outboxFailureHandler_meterRegistryProvided_returnsNonNullHandler` | `"outboxFailureHandler_meterRegistryProvided_returnsNonNullHandler"` | `"MeterRegistry를 주입하면 null이 아닌 OutboxFailureHandler를 반환한다"` |

### MembershipOutboxFailureHandlerConfigTest

| 메서드명 | 현재 @DisplayName (영문) | 변환 후 @DisplayName (한국어) |
|---|---|---|
| `outboxFailureHandler_singleFailure_incrementsCounterTaggedWithEventType` | `"outboxFailureHandler_singleFailure_incrementsCounterTaggedWithEventType"` | `"단일 publish 실패 시 event_type 태그와 함께 카운터가 1 증가한다"` |
| `outboxFailureHandler_multipleEventTypes_registersSeparateCountersPerTag` | `"outboxFailureHandler_multipleEventTypes_registersSeparateCountersPerTag"` | `"여러 event_type으로 실패 시 태그별 독립 카운터가 각각 증가한다"` |
| `outboxFailureHandler_meterRegistryProvided_returnsNonNullHandler` | `"outboxFailureHandler_meterRegistryProvided_returnsNonNullHandler"` | `"MeterRegistry를 주입하면 null이 아닌 OutboxFailureHandler를 반환한다"` |

위 한국어 설명은 예시이며, 구현자가 더 적절한 표현으로 조정할 수 있다.

---

# Edge Cases

- 메서드명(`{scenario}_{condition}_{expectedResult}` 패턴)은 변경하지 않으므로 IDE 테스트 실행 경로에 영향 없음
- 클래스 레벨 `@DisplayName`은 이미 한국어이므로 수정 대상 아님

---

# Failure Scenarios

- `@DisplayName` 문자열에 한국어가 포함되지 않는 경우 → 컨벤션 위반으로 재리뷰 시 다시 지적됨

---

# Test Requirements

- 변경 후 `./gradlew :apps:admin-service:test :apps:membership-service:test` 통과 확인

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests passing
- [ ] Contracts updated if needed (해당 없음)
- [ ] Specs updated first if required (해당 없음)
- [ ] Ready for review
