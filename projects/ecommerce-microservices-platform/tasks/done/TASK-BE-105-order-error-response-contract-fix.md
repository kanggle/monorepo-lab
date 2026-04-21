# Task ID

TASK-BE-105

# Title

order-service ErrorResponse 컨트랙트 위반 수정 — timestamp 필드 제거 및 IllegalArgumentException 핸들러 추가

# Status

review

# Owner

backend

# Task Tags

- code
- api
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

order-service의 `ErrorResponse`에 API 컨트랙트에 정의되지 않은 `timestamp` 필드가 포함되어 있는 컨트랙트 위반을 수정한다. 스펙은 `{"code": "string", "message": "string"}` 형식만 정의하고 있다.

또한 `GlobalExceptionHandler`에 `IllegalArgumentException` 핸들러가 누락되어, `OrderItem`/`ShippingAddress` 생성자에서 발생하는 유효성 검증 실패 시 400 대신 500이 반환되는 문제를 함께 수정한다.

---

# Scope

## In Scope

- `ErrorResponse` record에서 `timestamp` 필드 제거
- `GlobalExceptionHandler`에 `IllegalArgumentException` 핸들러 추가 (400 Bad Request 반환)
- 기존 `GlobalExceptionHandler` 테스트 수정 및 새 테스트 추가

## Out of Scope

- 다른 서비스의 ErrorResponse 수정
- 에러 응답에 timestamp를 추가하기 위한 스펙 변경 제안

---

# Acceptance Criteria

- [ ] `ErrorResponse`가 `code`와 `message` 필드만 포함한다
- [ ] 모든 에러 응답이 `{"code": "string", "message": "string"}` 형식을 따른다
- [ ] `IllegalArgumentException` 발생 시 400 Bad Request와 `INVALID_ORDER_REQUEST` 코드가 반환된다
- [ ] 기존 예외 핸들러들의 응답 형식이 변경된 `ErrorResponse`를 사용한다
- [ ] 테스트가 변경 사항을 커버한다

---

# Related Specs

- `specs/services/order-service/architecture.md`

# Related Skills

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

- `ErrorResponse` record에서 `timestamp` 필드와 관련 로직을 제거한다.
- 기존 `GlobalExceptionHandler`의 모든 핸들러에서 `ErrorResponse` 생성 시 timestamp 인자를 제거한다.
- `IllegalArgumentException` 핸들러는 `@ExceptionHandler(IllegalArgumentException.class)`로 추가하고, `INVALID_ORDER_REQUEST` 에러 코드와 예외 메시지를 반환한다.

---

# Edge Cases

- `IllegalArgumentException`이 도메인 검증 외 다른 곳에서도 발생할 수 있음 — 메시지를 그대로 전달하되 민감 정보 노출 여부 확인
- `MethodArgumentNotValidException`과 `IllegalArgumentException`의 중복 처리 가능성 — `@Valid` 검증은 별도 핸들러로 처리

---

# Failure Scenarios

- `ErrorResponse` 필드 변경 시 기존 클라이언트가 `timestamp` 필드에 의존하고 있는 경우 — 컨트랙트에 없으므로 의존해서는 안 됨

---

# Test Requirements

- 단위 테스트: `ErrorResponse`가 `code`와 `message`만 포함하는지 확인
- 단위 테스트: `IllegalArgumentException` 발생 시 400 반환 확인
- 단위 테스트: 기존 예외 핸들러들이 변경된 `ErrorResponse` 형식으로 응답하는지 확인

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added
- [ ] Tests passing
- [ ] Contracts updated if needed
- [ ] Ready for review
