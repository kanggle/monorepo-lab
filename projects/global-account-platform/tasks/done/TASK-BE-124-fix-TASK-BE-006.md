# Task ID

TASK-BE-124

# Title

TASK-BE-006 후속 수정 — PiiMaskingUtilsTest @DisplayName 한글화 및 테스트 메서드 네이밍 규칙 준수

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

TASK-BE-006 리뷰에서 발견된 두 가지 경미한 규칙 위반을 수정한다:

1. `libs/java-security/src/test/java/com/gap/security/pii/PiiMaskingUtilsTest.java`의 `@DisplayName` 문자열이 영어로 작성되어 있어 `platform/testing-strategy.md`("Use `@DisplayName` with Korean descriptions for test readability") 위반.
2. 일부 테스트 메서드 이름이 `{scenario}_{condition}_{expectedResult}` 3-파트 패턴을 따르지 않아 `platform/testing-strategy.md` 네이밍 규칙을 완전히 충족하지 못함.

---

# Scope

## In Scope

- `libs/java-security/src/test/java/com/gap/security/pii/PiiMaskingUtilsTest.java`
  - 모든 `@DisplayName` 값을 한글 설명으로 교체
  - 테스트 메서드 이름을 `{대상메서드}_{조건}_{기대결과}` 3-파트 패턴으로 정비

## Out of Scope

- 기존 마스킹 로직 변경 없음
- 다른 테스트 파일 변경 없음
- `platform/testing-strategy.md` 이외의 규칙 변경 없음

---

# Acceptance Criteria

- [x] `PiiMaskingUtilsTest`의 모든 `@DisplayName`이 한글로 작성된다
- [x] 모든 테스트 메서드 이름이 `{method}_{condition}_{expectedResult}` 패턴(또는 동등한 설명적 3-파트)을 따른다
- [x] 테스트 통과 수 및 커버리지 변경 없음
- [x] `:libs:java-security:test` BUILD SUCCESSFUL

---

# Related Specs

- `platform/testing-strategy.md` (§ Naming Conventions, § Rules)

---

# Related Contracts

없음 — 테스트 네이밍 수정만

---

# Edge Cases

- `@ParameterizedTest`에는 `@DisplayName`이 CSV 케이스별로 자동 생성되므로 `@DisplayName` 어노테이션 자체의 문자열만 한글로 변경
- 메서드 이름은 Java 식별자 규칙(camelCase)을 유지하면서 3-파트 의미를 표현

---

# Failure Scenarios

- 한글 `@DisplayName`으로 변경 후 빌드 깨짐 → 없어야 함(Java는 UTF-8 소스 지원)
- 메서드 이름 변경 후 테스트 수가 줄어드는 경우 → AC에서 차단

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added (N/A — 기존 테스트 수정)
- [ ] Tests passing
- [ ] Contracts updated if needed (N/A)
- [ ] Specs updated first if required (N/A)
- [ ] Ready for review
