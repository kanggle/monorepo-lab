# Task ID

TASK-R-17

# Title

order-service UnauthorizedOrderAccessException 에러코드 UNAUTHORIZED -> ACCESS_DENIED 수정

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

order-service에서 다른 사용자의 주문에 접근할 때 UnauthorizedOrderAccessException을 던지며 UNAUTHORIZED(401) 에러코드를 반환하고 있다. 이는 "인증 실패"가 아니라 "권한 부족"이므로, error-handling.md 정책에 맞게 403 ACCESS_DENIED로 수정한다.

---

# Scope

## In Scope

- order-service GlobalExceptionHandler의 UnauthorizedOrderAccessException 핸들러 수정: HTTP 401 -> 403, UNAUTHORIZED -> ACCESS_DENIED
- 관련 테스트 수정

## Out of Scope

- UnauthorizedOrderAccessException 클래스명 변경 (도메인 예외 이름은 유지)
- 다른 서비스의 권한 검사 로직 변경
- 새로운 권한 검사 로직 추가

---

# Acceptance Criteria

- [ ] order-service에서 다른 사용자의 주문 접근 시 HTTP 403을 반환한다
- [ ] 에러코드가 ACCESS_DENIED이다
- [ ] 에러 메시지가 권한 부족을 명확히 설명한다
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

- error-handling.md: "Authorization failure (insufficient permission) -> 403 Forbidden"
- error-handling.md 표준 코드: ACCESS_DENIED (403) - "Insufficient permissions to access resource"
- UNAUTHORIZED (401)는 "Access token missing or invalid"에만 사용
- 다른 사용자의 주문에 접근하는 것은 인증된 사용자의 권한 부족 문제이므로 403이 적절

---

# Edge Cases

- UnauthorizedOrderAccessException이 여러 곳에서 사용되는 경우 -> 모든 사용처에서 동일하게 403 ACCESS_DENIED로 처리
- 예외 클래스명에 "Unauthorized"가 포함되어 있어 혼동 가능 -> 클래스명 변경은 이 태스크 범위 밖, 핸들러의 응답만 수정

---

# Failure Scenarios

- 테스트에서 401을 기대하는 코드 수정 누락 -> 테스트 전체 실행으로 확인
- 프론트엔드가 401을 기반으로 리다이렉트 처리하는 경우 -> 403으로 변경 시 올바른 권한 부족 UI 표시 가능

---

# Test Requirements

- GlobalExceptionHandler 슬라이스 테스트: UnauthorizedOrderAccessException 발생 시 403 ACCESS_DENIED 반환 검증
- 기존 컨트롤러 슬라이스 테스트 통과 확인
- 주문 조회/취소 시 다른 사용자 접근 테스트에서 403 검증

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added
- [ ] Tests passing
- [ ] Contracts updated if needed
- [ ] Specs updated first if required
- [ ] Ready for review
