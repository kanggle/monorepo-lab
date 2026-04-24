# Task ID

TASK-R-21-FIX

# Title

[FIX] review-service GlobalExceptionHandler ResponseEntity 반환 방식 전환 (TASK-R-21 미완료)

# Status

review

# Owner

backend

# Task Tags

- fix
- refactor
- error-handling

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

TASK-R-21의 구현이 완료되지 않았다. `review-service`의 `GlobalExceptionHandler` 전체 핸들러 메서드(9개)가 여전히 `@ResponseStatus` 어노테이션과 함께 `ErrorResponse`를 직접 반환하고 있다. 다른 서비스와 동일하게 `ResponseEntity<ErrorResponse>`를 반환하고 `@ResponseStatus`를 제거해야 한다.

---

# Scope

## In Scope

- `review-service` `GlobalExceptionHandler`의 모든 핸들러 메서드를 `ResponseEntity<ErrorResponse>` 반환으로 변경
- 모든 핸들러에서 `@ResponseStatus` 어노테이션 제거
- HTTP 상태 코드는 `ResponseEntity.status(HttpStatus.XXX)` 방식으로 설정
- 관련 테스트 수정 (반환 타입 변경에 따른 테스트 업데이트)

## Out of Scope

- 에러 코드 변경
- HTTP 상태 코드 변경 (기존 상태 코드 유지)
- 에러 메시지 변경
- 다른 서비스 변경

---

# Acceptance Criteria

- [ ] `GlobalExceptionHandler`의 모든 핸들러 메서드가 `ResponseEntity<ErrorResponse>`를 반환한다
- [ ] `@ResponseStatus` 어노테이션이 모든 핸들러에서 제거되었다
- [ ] HTTP 상태코드가 기존과 동일하게 유지된다 (400, 401, 403, 404, 409, 422, 500)
- [ ] 에러 응답 JSON 포맷이 변경되지 않았다
- [ ] 기존 테스트가 모두 통과한다

---

# Related Specs

- `specs/platform/error-handling.md`

---

# Related Contracts

- 해당 없음 (내부 리팩토링, HTTP 응답 포맷 변경 없음)

---

# Edge Cases

- 테스트에서 `@ResponseStatus`에 의존하는 경우 -> `ResponseEntity` 반환으로 변경 후 테스트도 수정
- `@RestControllerAdvice`와 `ResponseEntity` 조합의 Spring 동작 확인 필요

---

# Failure Scenarios

- 일부 핸들러 수정 누락으로 `@ResponseStatus` + `ResponseEntity` 혼용 -> 전체 핸들러 검토
- 테스트에서 기대 상태 코드 불일치 -> 기존 상태 코드와 동일하게 유지 확인

---

# Test Requirements

- `GlobalExceptionHandler` 슬라이스 테스트 (각 예외별 HTTP 상태 코드 및 응답 JSON 검증)

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added
- [ ] Tests passing
- [ ] Contracts updated if needed
- [ ] Specs updated first if required
- [ ] Ready for review
