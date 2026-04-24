# Task ID

TASK-R-22

# Title

review-service validation 메시지 findFirst() 패턴으로 변경

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

review-service의 GlobalExceptionHandler에서 MethodArgumentNotValidException/ConstraintViolationException 처리 시 Collectors.joining으로 모든 에러 메시지를 합치고 있다. error-handling.md 정책에 따르면 "include the first failing field message"이므로 findFirst() 패턴으로 변경하여 첫 번째 실패 필드 메시지만 반환하도록 수정한다.

---

# Scope

## In Scope

- review-service GlobalExceptionHandler의 MethodArgumentNotValidException 핸들러에서 Collectors.joining -> findFirst() 변경
- review-service GlobalExceptionHandler의 ConstraintViolationException 핸들러에서 동일하게 변경 (해당하는 경우)
- 관련 테스트 수정

## Out of Scope

- 다른 서비스의 validation 메시지 처리 변경
- 에러코드 변경
- HTTP 상태코드 변경
- validation 규칙 자체 변경

---

# Acceptance Criteria

- [ ] review-service의 MethodArgumentNotValidException 핸들러가 첫 번째 실패 필드 메시지만 반환한다
- [ ] review-service의 ConstraintViolationException 핸들러가 첫 번째 실패 필드 메시지만 반환한다 (해당 핸들러 존재 시)
- [ ] findFirst() 또는 동등한 패턴을 사용하여 단일 메시지를 추출한다
- [ ] 다른 서비스와 동일한 메시지 반환 패턴을 사용한다
- [ ] 기존 테스트가 모두 통과한다

---

# Related Specs

- `specs/platform/error-handling.md`

# Related Skills

- `.claude/skills/backend/refactoring.md`

---

# Related Contracts

- 해당 없음 (에러 메시지 포맷 변경이지만 정책 준수를 위한 수정)

---

# Target Service

- `review-service`

---

# Architecture

Follow:

- `specs/platform/error-handling.md`
- `specs/services/review-service/architecture.md`

---

# Implementation Notes

- error-handling.md: "Validation errors must use VALIDATION_ERROR and include the first failing field message."
- 다른 서비스의 패턴 참고: `errors.getFieldErrors().stream().findFirst().map(FieldError::getDefaultMessage).orElse("Validation failed")`
- Collectors.joining(", ") -> findFirst() + orElse 패턴으로 변경

---

# Edge Cases

- validation 에러가 없는 경우 (이론적으로 발생하지 않으나) -> orElse로 기본 메시지 제공
- 필드 에러가 아닌 글로벌 에러만 있는 경우 -> getFieldErrors()가 빈 리스트일 수 있으므로 getGlobalErrors()도 확인

---

# Failure Scenarios

- findFirst()로 변경 후 기존 테스트가 여러 에러 메시지를 기대하는 경우 -> 첫 번째 메시지만 검증하도록 수정
- 메시지 순서가 비결정적인 경우 -> 어떤 메시지가 반환되든 유효한 에러 메시지인지만 확인

---

# Test Requirements

- GlobalExceptionHandler 슬라이스 테스트: validation 에러 시 단일 메시지만 반환되는지 검증
- 복수 필드 에러 발생 시에도 단일 메시지 반환 검증
- 기존 컨트롤러 슬라이스 테스트 통과 확인

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added
- [ ] Tests passing
- [ ] Contracts updated if needed
- [ ] Specs updated first if required
- [ ] Ready for review
