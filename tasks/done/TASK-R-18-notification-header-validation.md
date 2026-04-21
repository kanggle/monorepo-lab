# Task ID

TASK-R-18

# Title

notification-service 컨트롤러 @NotBlank 추가 및 MissingRequestHeaderException 핸들러 추가

# Status

review

# Owner

backend

# Task Tags

- refactor
- api

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

notification-service 컨트롤러의 X-User-Id 헤더 파라미터에 @NotBlank 어노테이션이 누락되어 있고, GlobalExceptionHandler에 MissingRequestHeaderException 핸들러가 없다. 빈 문자열이나 헤더 누락 시 적절한 에러 응답을 반환하도록 수정한다.

---

# Scope

## In Scope

- notification-service 컨트롤러의 @RequestHeader("X-User-Id") 파라미터에 @NotBlank 추가
- notification-service GlobalExceptionHandler에 MissingRequestHeaderException 핸들러 추가 (401 UNAUTHORIZED 반환)
- ConstraintViolationException 핸들러 추가 또는 확인 (@NotBlank 위반 시 처리)
- 관련 테스트 추가/수정

## Out of Scope

- 다른 서비스의 헤더 검증 변경
- notification-service의 비즈니스 로직 변경
- 인증 미들웨어/필터 추가

---

# Acceptance Criteria

- [ ] notification-service 컨트롤러의 X-User-Id 파라미터에 @NotBlank가 적용되어 있다
- [ ] X-User-Id 헤더 누락 시 401 UNAUTHORIZED를 반환한다
- [ ] X-User-Id 헤더가 빈 문자열일 때 적절한 에러 응답을 반환한다
- [ ] 에러 응답이 표준 ErrorResponse 포맷(code, message, timestamp)을 따른다
- [ ] 기존 테스트가 모두 통과한다

---

# Related Specs

- `specs/platform/error-handling.md`
- `specs/contracts/http/notification-api.md`

# Related Skills

- `.claude/skills/backend/refactoring.md`

---

# Related Contracts

- `specs/contracts/http/notification-api.md` (X-User-Id 헤더 요구사항 확인)

---

# Target Service

- `notification-service`

---

# Architecture

Follow:

- `specs/platform/error-handling.md`
- `specs/services/notification-service/architecture.md`

---

# Implementation Notes

- MissingRequestHeaderException 핸들러: X-User-Id 헤더 누락은 인증 실패로 처리하여 401 UNAUTHORIZED 반환
- @NotBlank 추가 시 컨트롤러 클래스에 @Validated 어노테이션이 필요할 수 있음
- ConstraintViolationException 핸들러: @NotBlank 위반 시 VALIDATION_ERROR (400) 반환
- 다른 서비스(order-service, payment-service 등)의 패턴을 참고

---

# Edge Cases

- X-User-Id 헤더가 공백 문자만 포함된 경우 -> @NotBlank로 처리
- X-User-Id 외 다른 @RequestHeader가 있는 경우 -> MissingRequestHeaderException 핸들러에서 헤더명에 따라 분기 처리 검토
- @Validated가 이미 적용되어 있는 경우 -> 중복 추가하지 않음

---

# Failure Scenarios

- @Validated 누락으로 @NotBlank가 동작하지 않음 -> 슬라이스 테스트로 빈 문자열 케이스 검증
- MissingRequestHeaderException이 Spring 기본 핸들러에 의해 처리되는 경우 -> GlobalExceptionHandler에서 명시적으로 처리
- 기존 테스트에서 헤더 없이 요청하는 케이스 수정 필요 -> 테스트 확인

---

# Test Requirements

- GlobalExceptionHandler 슬라이스 테스트: X-User-Id 헤더 누락 시 401 UNAUTHORIZED 반환 검증
- 컨트롤러 슬라이스 테스트: X-User-Id 빈 문자열 시 에러 응답 검증
- 기존 컨트롤러 슬라이스 테스트 통과 확인

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added
- [ ] Tests passing
- [ ] Contracts updated if needed
- [ ] Specs updated first if required
- [ ] Ready for review
