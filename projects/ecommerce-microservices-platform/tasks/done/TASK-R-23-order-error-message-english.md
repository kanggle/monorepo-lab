# Task ID

TASK-R-23

# Title

order-service 한국어 에러 메시지 -> 영어 통일

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

order-service에 @NotBlank(message = "X-User-Id 헤더는 필수입니다") 등 한국어 에러 메시지가 혼재되어 있다. 다른 서비스들은 영어 메시지를 사용하고 있으므로, 코드베이스 일관성을 위해 한국어 메시지를 영어로 통일한다.

---

# Scope

## In Scope

- order-service 내 모든 Bean Validation 어노테이션의 한국어 message 속성을 영어로 변경
- order-service 내 도메인 예외의 한국어 메시지를 영어로 변경 (해당하는 경우)
- 관련 테스트의 메시지 검증값 수정

## Out of Scope

- 다른 서비스의 메시지 변경
- i18n/메시지 번들 도입
- 에러코드 변경
- HTTP 상태코드 변경

---

# Acceptance Criteria

- [ ] order-service 내 모든 validation 어노테이션의 message가 영어이다
- [ ] order-service 내 도메인 예외 메시지가 영어이다
- [ ] 한국어 에러 메시지가 코드에 남아있지 않다
- [ ] 메시지가 다른 서비스의 영어 메시지 스타일과 일관적이다
- [ ] 기존 테스트가 모두 통과한다

---

# Related Specs

- `specs/platform/coding-rules.md`

# Related Skills

- `.claude/skills/backend/refactoring.md`

---

# Related Contracts

- 해당 없음 (에러 메시지 언어 변경, API 계약 영향 없음)

---

# Target Service

- `order-service`

---

# Architecture

Follow:

- `specs/platform/coding-rules.md`
- `specs/services/order-service/architecture.md`

---

# Implementation Notes

- 다른 서비스의 영어 메시지 패턴을 참고하여 일관된 스타일 적용
- 예시 변환:
  - "X-User-Id 헤더는 필수입니다" -> "X-User-Id header is required"
  - "주문 항목은 비어있을 수 없습니다" -> "Order items must not be empty"
- 메시지는 간결하고 명확한 영어로 작성
- error-handling.md: "human-readable description (must not contain sensitive data)"

---

# Edge Cases

- 한국어와 영어가 혼합된 메시지가 있는 경우 -> 전체를 영어로 변경
- 로그 메시지에 한국어가 있는 경우 -> 이 태스크에서는 에러 응답 메시지만 대상, 로그는 별도 검토
- 테스트에서 한국어 메시지를 직접 비교하는 경우 -> 영어 메시지로 수정

---

# Failure Scenarios

- 메시지 변경 누락으로 한국어가 남아있는 경우 -> 전체 검색으로 확인
- 테스트 메시지 검증값 수정 누락 -> 테스트 전체 실행으로 확인
- 영어 메시지가 기존 한국어 메시지의 의미를 정확히 전달하지 못하는 경우 -> 다른 서비스의 유사 메시지 참고

---

# Test Requirements

- order-service 전체 테스트 통과 확인
- 한국어 메시지를 검증하는 테스트의 기대값을 영어로 수정
- validation 에러 메시지가 영어로 반환되는지 슬라이스 테스트에서 검증

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added
- [ ] Tests passing
- [ ] Contracts updated if needed
- [ ] Specs updated first if required
- [ ] Ready for review
