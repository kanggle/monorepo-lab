# Task ID

TASK-BE-086

# Title

order-service 컨트롤러 수동 null 검증 제거 — @Valid + DTO 검증 전환

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

If any section is missing or incomplete, this task must not be implemented.

---

# Goal

order-service의 `OrderController`에서 `userId` 등 요청 파라미터를 `if (userId == null || userId.isBlank())` 패턴으로 수동 검증하고 있다. 다른 서비스들은 Spring Validation(`@Valid`, `@NotBlank`, `@Validated`)을 사용하여 선언적으로 검증한다. order-service도 동일한 패턴으로 전환하여 서비스 간 일관성을 확보한다.

---

# Scope

## In Scope

- `OrderController`의 수동 null/blank 검증을 `@NotBlank`, `@Valid` 등 Bean Validation 애노테이션으로 전환
- 필요 시 요청 DTO에 검증 애노테이션 추가
- `@Validated` 클래스 레벨 애노테이션 적용 (PathVariable, RequestParam 검증용)
- GlobalExceptionHandler에서 ConstraintViolationException 처리 확인

## Out of Scope

- 비즈니스 검증 로직 변경 (도메인 계층 검증은 유지)
- 다른 서비스의 검증 개선 (TASK-BE-065에서 처리)
- 새로운 API 엔드포인트 추가

---

# Acceptance Criteria

- [ ] `OrderController`에 수동 null/blank 검증 코드(`if (... == null || ...isBlank())`)가 없다
- [ ] `@Validated`가 `OrderController` 클래스에 적용되었다
- [ ] 요청 파라미터에 `@NotBlank` 등 Bean Validation 애노테이션이 적용되었다
- [ ] 유효하지 않은 입력 시 400 Bad Request가 반환된다
- [ ] 에러 응답 포맷이 다른 서비스와 동일하다
- [ ] 모든 기존 테스트가 통과한다

---

# Related Specs

- `specs/services/order-service/architecture.md`
- `specs/platform/error-handling.md`
- `specs/platform/coding-rules.md`

# Related Skills

- `.claude/skills/backend/validation.md`
- `.claude/skills/backend/exception-handling.md`

---

# Related Contracts

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

- auth-service의 `AuthController` + `@Valid @RequestBody` 패턴을 참고한다.
- search-service의 `@Validated` 클래스 레벨 + `@NotBlank` 파라미터 레벨 패턴을 참고한다.
- `@RequestHeader("X-User-Id")` 파라미터에 `@NotBlank`를 적용하려면 클래스에 `@Validated`가 필요하다.
- ConstraintViolationException → 400 응답 변환이 GlobalExceptionHandler에 있는지 확인한다.

---

# Edge Cases

- X-User-Id 헤더가 아예 없는 경우 → MissingRequestHeaderException (Spring 기본 처리)
- X-User-Id 헤더가 빈 문자열인 경우 → @NotBlank로 ConstraintViolationException
- PathVariable orderId가 빈 값인 경우 → 라우팅 자체가 매칭되지 않음

---

# Failure Scenarios

- ConstraintViolationException 핸들러가 없어 500 에러 반환
- 기존 수동 검증의 에러 응답 포맷과 Bean Validation의 에러 응답 포맷이 달라 클라이언트 호환성 깨짐
- @NotBlank가 null과 blank를 모두 거부하지만 기존 로직은 null만 거부한 경우 동작 변경

---

# Test Requirements

- 유효하지 않은 입력에 대한 400 응답 테스트 (null, blank, 빈 헤더)
- 유효한 입력에 대한 정상 동작 테스트
- 에러 응답 포맷 검증 테스트

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added
- [ ] Tests passing
- [ ] Contracts updated if needed
- [ ] Specs updated first if required
- [ ] Ready for review
