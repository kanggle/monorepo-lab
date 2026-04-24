# Task ID

TASK-R-21

# Title

review-service GlobalExceptionHandler ResponseEntity 패턴 통일

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

review-service의 GlobalExceptionHandler만 @ResponseStatus 어노테이션과 직접 ErrorResponse 반환 방식을 사용하고 있다. 다른 서비스들은 ResponseEntity<ErrorResponse> 패턴을 사용한다. 코드 일관성을 위해 review-service도 ResponseEntity<ErrorResponse> 패턴으로 통일한다.

---

# Scope

## In Scope

- review-service GlobalExceptionHandler의 모든 핸들러 메서드에서 @ResponseStatus 어노테이션 제거
- 반환 타입을 ErrorResponse에서 ResponseEntity<ErrorResponse>로 변경
- ResponseEntity.status(HttpStatus.XXX).body(errorResponse) 패턴 적용
- 관련 테스트 수정

## Out of Scope

- 에러코드 변경
- 에러 메시지 변경
- HTTP 상태코드 변경
- 다른 서비스의 GlobalExceptionHandler 변경

---

# Acceptance Criteria

- [ ] review-service GlobalExceptionHandler의 모든 핸들러가 ResponseEntity<ErrorResponse>를 반환한다
- [ ] @ResponseStatus 어노테이션이 모든 핸들러에서 제거되었다
- [ ] HTTP 상태코드가 기존과 동일하게 유지된다
- [ ] 에러 응답 JSON 포맷이 변경되지 않았다
- [ ] 기존 테스트가 모두 통과한다

---

# Related Specs

- `specs/platform/error-handling.md`

# Related Skills

- `.claude/skills/backend/refactoring.md`

---

# Related Contracts

- 해당 없음 (내부 리팩토링, API 응답 포맷 변경 없음)

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

- 다른 서비스(order-service, payment-service 등)의 GlobalExceptionHandler를 참고하여 동일한 ResponseEntity 패턴 적용
- @ResponseStatus + 직접 반환 -> ResponseEntity.status(HttpStatus.XXX).body(new ErrorResponse(...)) 변환
- 예시:
  - 변경 전: @ResponseStatus(HttpStatus.BAD_REQUEST) public ErrorResponse handleXxx(...)
  - 변경 후: public ResponseEntity<ErrorResponse> handleXxx(...)

---

# Edge Cases

- @ResponseStatus와 ResponseEntity를 동시에 사용하면 충돌 -> @ResponseStatus 반드시 제거 확인
- 핸들러 메서드가 void를 반환하는 경우 -> ResponseEntity로 변환

---

# Failure Scenarios

- @ResponseStatus 제거 후 HTTP 상태코드가 200으로 변경됨 -> ResponseEntity에 올바른 상태코드 설정 확인
- 테스트에서 반환 타입을 직접 검증하는 경우 수정 필요 -> 슬라이스 테스트는 HTTP 응답으로 검증하므로 영향 제한적

---

# Test Requirements

- review-service GlobalExceptionHandler 슬라이스 테스트: 모든 핸들러의 HTTP 상태코드와 에러 응답 검증
- 기존 컨트롤러 슬라이스 테스트 통과 확인

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added
- [ ] Tests passing
- [ ] Contracts updated if needed
- [ ] Specs updated first if required
- [ ] Ready for review
