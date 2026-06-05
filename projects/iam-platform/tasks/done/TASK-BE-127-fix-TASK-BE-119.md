# Task ID

TASK-BE-125

# Title

fix(TASK-BE-119): QueryExceptionHandler 슬라이스 테스트 — IllegalArgumentException 커버리지 누락

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

TASK-BE-119에서 `QueryExceptionHandler`가 `CommonGlobalExceptionHandler`를 상속하도록 수정되었으나, 태스크의 Test Requirements에서 명시한 **`IllegalArgumentException` 처리 검증 테스트가 누락**되어 있다.

현재 `LoginHistoryQueryControllerTest`에는 `MissingServletRequestParameterException` 케이스(`missingAccountIdReturns400`)는 존재하지만, `IllegalArgumentException` 케이스가 없다.

또한 `SuspiciousEventQueryController`에 대한 슬라이스 테스트 자체가 존재하지 않아 해당 컨트롤러의 예외 처리 동작이 검증되지 않는다.

---

# Scope

## In Scope

- `LoginHistoryQueryControllerTest`에 `IllegalArgumentException` 처리 검증 테스트 케이스 추가
  - 핸들러: `handleIllegalArgument` (상속된 공통 핸들러)
  - 기대 응답: HTTP 400, `{"code": "VALIDATION_ERROR", "message": "<예외 메시지>", "timestamp": "..."}`
- `SuspiciousEventQueryControllerTest` 슬라이스 테스트 신규 생성
  - 최소 커버리지: `MissingServletRequestParameterException`, `IllegalArgumentException`, 정상 응답(200)

## Out of Scope

- 프로덕션 코드 변경 없음
- 다른 서비스의 테스트 변경 없음

---

# Acceptance Criteria

- [ ] `LoginHistoryQueryControllerTest`에 `IllegalArgumentException` → 400 `VALIDATION_ERROR` 검증 테스트가 존재한다
- [ ] `SuspiciousEventQueryControllerTest`가 생성되고 `MissingServletRequestParameterException`, `IllegalArgumentException` 케이스를 검증한다
- [ ] 모든 신규 테스트 메서드 이름이 `{scenario}_{condition}_{expectedResult}` 패턴을 따른다
- [ ] `./gradlew :apps:security-service:test`가 통과한다

---

# Related Specs

- `platform/testing-strategy.md`
- `platform/error-handling.md`
- `specs/services/security-service/architecture.md` (Testing Expectations — Query slice)

# Related Skills

- `.claude/skills/backend/testing-backend/SKILL.md`
- `.claude/skills/backend/exception-handling/SKILL.md`

---

# Related Contracts

없음 — 프로덕션 코드 및 에러 응답 구조 변경 없음

---

# Target Service

- `apps/security-service`

---

# Architecture

- 테스트 계층: Controller Slice (`@WebMvcTest`)
- `QueryExceptionHandler`는 `@Import`로 슬라이스 테스트에 포함

---

# Edge Cases

- `IllegalArgumentException`의 message가 null인 경우 → `null`이 응답 body의 `message` 필드에 포함되지 않도록 확인 (현재 `handleIllegalArgument`는 `e.getMessage()` 그대로 사용하므로 null 전달 시 `null` 문자열 반환 가능)

---

# Failure Scenarios

- `@WebMvcTest`에 `QueryExceptionHandler`를 `@Import`하지 않으면 공통 핸들러가 활성화되지 않아 테스트가 500을 반환할 수 있음 → 반드시 `@Import({QueryExceptionHandler.class, InternalAuthFilter.class})` 포함

---

# Test Requirements

- `LoginHistoryQueryControllerTest` — `IllegalArgumentException` 케이스 추가 (단위 슬라이스)
- `SuspiciousEventQueryControllerTest` — 신규 생성, 최소 3개 케이스: `MissingServletRequestParameterException`, `IllegalArgumentException`, 인증된 정상 요청 200
