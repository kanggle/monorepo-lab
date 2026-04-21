# Task ID

TASK-R-16

# Title

order-service MissingRequestHeaderException -> 401 UNAUTHORIZED 수정

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

order-service에서 X-User-Id 헤더 누락 시 400 INVALID_ORDER_REQUEST를 반환하고 있다. X-User-Id 헤더는 인증된 사용자 식별을 위한 필수 헤더이므로, 누락 시 인증 실패로 판단하여 401 UNAUTHORIZED를 반환하도록 수정한다.

---

# Scope

## In Scope

- order-service GlobalExceptionHandler의 MissingRequestHeaderException 핸들러 수정: HTTP 400 -> 401, INVALID_ORDER_REQUEST -> UNAUTHORIZED
- 관련 테스트 수정

## Out of Scope

- 다른 서비스의 MissingRequestHeaderException 처리 변경
- X-User-Id 헤더의 값 검증 로직 변경
- 인증 미들웨어/필터 추가

---

# Acceptance Criteria

- [ ] order-service에서 X-User-Id 헤더 누락 시 HTTP 401을 반환한다
- [ ] 에러코드가 UNAUTHORIZED이다
- [ ] 에러 메시지가 헤더 누락을 명확히 설명한다
- [ ] 에러 응답이 표준 ErrorResponse 포맷(code, message, timestamp)을 따른다
- [ ] 기존 테스트가 모두 통과한다

---

# Related Specs

- `specs/platform/error-handling.md`

# Related Skills

- `.claude/skills/backend/refactoring.md`

---

# Related Contracts

- 해당 없음 (에러 응답의 HTTP 상태코드와 에러코드 수정이며, error-handling.md 정책 준수를 위한 변경)

---

# Target Service

- `order-service`

---

# Architecture

Follow:

- `specs/platform/error-handling.md`
- `specs/services/order-service/architecture.md`

---

# Implementation Notes

- error-handling.md: "Authentication failure (invalid credentials, missing token) -> 401 Unauthorized"
- X-User-Id 헤더는 API Gateway에서 인증 후 전달하는 사용자 식별 정보이므로, 누락은 인증 실패에 해당
- error-handling.md 표준 코드: UNAUTHORIZED (401) - "Access token missing or invalid"

---

# Edge Cases

- X-User-Id 외 다른 헤더가 MissingRequestHeaderException을 발생시키는 경우 -> 헤더 이름을 확인하여 X-User-Id인 경우에만 401 처리, 다른 헤더는 400 유지
- MissingRequestHeaderException 핸들러가 여러 헤더를 공통으로 처리하는 경우 -> 헤더별 분기 처리 검토

---

# Failure Scenarios

- 401 변경 후 프론트엔드가 로그인 페이지로 리다이렉트하는 경우 -> API Gateway 레벨에서 이미 인증 처리하므로 영향 제한적
- 테스트에서 400을 기대하는 코드 수정 누락 -> 테스트 전체 실행으로 확인

---

# Test Requirements

- GlobalExceptionHandler 슬라이스 테스트: X-User-Id 헤더 누락 시 401 UNAUTHORIZED 반환 검증
- 기존 컨트롤러 슬라이스 테스트 통과 확인

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added
- [ ] Tests passing
- [ ] Contracts updated if needed
- [ ] Specs updated first if required
- [ ] Ready for review
