# Task ID

TASK-R-14

# Title

Validation 에러코드 VALIDATION_ERROR로 통일

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

shipping-service, order-service, review-service의 MethodArgumentNotValidException / ConstraintViolationException 핸들러가 INVALID_XXX_REQUEST 에러코드를 반환하고 있다. error-handling.md 정책에 따르면 validation 에러는 VALIDATION_ERROR 코드를 사용해야 한다. 해당 서비스들의 GlobalExceptionHandler를 수정하여 validation 에러코드를 VALIDATION_ERROR로 통일한다.

---

# Scope

## In Scope

- shipping-service GlobalExceptionHandler의 MethodArgumentNotValidException/ConstraintViolationException 핸들러에서 에러코드를 VALIDATION_ERROR로 변경
- order-service GlobalExceptionHandler의 MethodArgumentNotValidException/ConstraintViolationException 핸들러에서 에러코드를 VALIDATION_ERROR로 변경
- review-service GlobalExceptionHandler의 MethodArgumentNotValidException/ConstraintViolationException 핸들러에서 에러코드를 VALIDATION_ERROR로 변경
- 기존 테스트의 에러코드 검증 값 수정

## Out of Scope

- 비즈니스 규칙 위반 에러코드 변경 (도메인 고유 코드는 유지)
- 다른 서비스의 validation 에러코드 변경 (이미 올바른 서비스)
- GlobalExceptionHandler의 구조 변경
- 에러 응답 포맷 변경

---

# Acceptance Criteria

- [ ] shipping-service의 MethodArgumentNotValidException 핸들러가 VALIDATION_ERROR 코드를 반환한다
- [ ] shipping-service의 ConstraintViolationException 핸들러가 VALIDATION_ERROR 코드를 반환한다
- [ ] order-service의 MethodArgumentNotValidException 핸들러가 VALIDATION_ERROR 코드를 반환한다
- [ ] order-service의 ConstraintViolationException 핸들러가 VALIDATION_ERROR 코드를 반환한다
- [ ] review-service의 MethodArgumentNotValidException 핸들러가 VALIDATION_ERROR 코드를 반환한다
- [ ] review-service의 ConstraintViolationException 핸들러가 VALIDATION_ERROR 코드를 반환한다
- [ ] HTTP 상태 코드는 400 Bad Request로 유지된다
- [ ] 기존 테스트가 모두 통과한다
- [ ] 에러 응답 JSON 포맷이 변경되지 않았다

---

# Related Specs

- `specs/platform/error-handling.md`

# Related Skills

- `.claude/skills/backend/refactoring.md`

---

# Related Contracts

- 해당 없음 (에러코드 변경이지만 error-handling.md 정책 준수를 위한 수정이며, API 계약의 에러 응답 포맷은 동일하게 유지)

---

# Target Service

- `shipping-service`
- `order-service`
- `review-service`

---

# Architecture

Follow:

- `specs/platform/error-handling.md`
- 각 서비스의 `specs/services/<service>/architecture.md`

---

# Implementation Notes

- error-handling.md 정책: "Validation errors must use VALIDATION_ERROR and include the first failing field message."
- INVALID_SHIPPING_REQUEST, INVALID_ORDER_REQUEST, INVALID_REVIEW_REQUEST는 비즈니스 validation 에러에만 사용하고, Bean Validation 에러(MethodArgumentNotValidException, ConstraintViolationException)는 VALIDATION_ERROR를 사용해야 한다.
- 메시지 포맷은 기존 패턴을 유지하되, findFirst() 패턴으로 첫 번째 실패 필드 메시지만 반환한다.

---

# Edge Cases

- 일부 컨트롤러 슬라이스 테스트가 INVALID_XXX_REQUEST 코드를 검증하고 있는 경우 -> VALIDATION_ERROR로 수정
- 비즈니스 validation과 Bean Validation이 같은 핸들러에서 처리되는 경우 -> Bean Validation만 VALIDATION_ERROR로 변경, 비즈니스 규칙 위반은 도메인 고유 코드 유지
- ConstraintViolationException 핸들러가 없는 서비스 -> 추가 불필요, 있는 핸들러만 수정

---

# Failure Scenarios

- 에러코드 변경 후 기존 테스트 실패 -> 테스트의 기대값을 VALIDATION_ERROR로 수정
- 비즈니스 에러코드까지 잘못 변경 -> 비즈니스 규칙 위반 핸들러는 변경 대상이 아님을 확인
- 프론트엔드가 특정 에러코드에 의존하는 경우 -> error-handling.md가 source of truth이므로 정책대로 수정

---

# Test Requirements

- 각 서비스의 GlobalExceptionHandler 슬라이스 테스트에서 validation 에러 시 VALIDATION_ERROR 코드 반환 검증
- 기존 컨트롤러 슬라이스 테스트 통과 확인

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added
- [ ] Tests passing
- [ ] Contracts updated if needed
- [ ] Specs updated first if required
- [ ] Ready for review
