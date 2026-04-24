# Task ID

TASK-R-06

# Title

promotion-service CouponRestoreNotAllowedException 핸들러 추가 및 에러코드 등록

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

promotion-service의 CouponRestoreNotAllowedException이 GlobalExceptionHandler에서 처리되지 않아 500 Internal Server Error로 반환되고 있다. 이는 error-handling.md 정책 위반이다.

GlobalExceptionHandler에 해당 예외 핸들러를 추가하고, error-handling.md에 에러코드를 등록하여 적절한 HTTP 상태 코드와 에러 응답을 반환하도록 수정한다.

---

# Scope

## In Scope

- CouponRestoreNotAllowedException용 GlobalExceptionHandler 핸들러 추가
- error-handling.md에 에러코드 등록 (COUPON_RESTORE_NOT_ALLOWED)
- 적절한 HTTP 상태 코드 매핑 (422 Unprocessable Entity)
- 표준 에러 응답 형식 준수

## Out of Scope

- 다른 서비스의 예외 핸들러 점검
- CouponRestoreNotAllowedException 발생 로직 자체 변경
- 기존 에러코드 변경

---

# Acceptance Criteria

- [ ] CouponRestoreNotAllowedException 발생 시 422 상태 코드와 COUPON_RESTORE_NOT_ALLOWED 에러코드가 반환된다
- [ ] 500 Internal Server Error가 더 이상 반환되지 않는다
- [ ] 에러 응답이 표준 형식(code, message, timestamp)을 따른다
- [ ] error-handling.md Promotion 섹션에 COUPON_RESTORE_NOT_ALLOWED 에러코드가 등록되어 있다
- [ ] GlobalExceptionHandler에 핸들러 메서드가 추가되어 있다

---

# Related Specs

- `specs/platform/error-handling.md`
- `specs/services/promotion-service/architecture.md`
- `specs/platform/coding-rules.md`

# Related Skills

- `.claude/skills/backend/architecture/ddd.md`
- `.claude/skills/backend/testing-backend.md`

---

# Related Contracts

- `specs/contracts/http/promotion-api.md`

---

# Target Service

- `promotion-service`

---

# Architecture

Follow:

- `specs/services/promotion-service/architecture.md`

---

# Implementation Notes

- error-handling.md 변경이 스펙 변경이므로 구현 전에 먼저 스펙을 업데이트한다
- error-handling.md Rules: "Error codes must be registered in this document before use."
- 422 Unprocessable Entity는 비즈니스 규칙 위반에 해당하는 상태 코드이다
- 쿠폰 복원이 허용되지 않는 상황(이미 사용되지 않은 쿠폰 복원 시도 등)에 대한 명확한 메시지 제공

---

# Edge Cases

- CouponRestoreNotAllowedException에 메시지가 없는 경우 기본 메시지 제공
- 동시 복원 요청 시 한쪽은 성공하고 다른 쪽은 해당 예외 발생

---

# Failure Scenarios

- 핸들러 등록 누락: 기존과 동일하게 500 반환 (이를 방지하기 위해 테스트 필수)
- 에러코드 미등록: 스펙 우선 업데이트로 방지

---

# Test Requirements

- 슬라이스 테스트: CouponRestoreNotAllowedException 발생 시 422 상태 코드와 COUPON_RESTORE_NOT_ALLOWED 코드 반환 검증 (@WebMvcTest)
- 단위 테스트: GlobalExceptionHandler가 해당 예외를 올바른 응답으로 변환하는지 검증

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added
- [ ] Tests passing
- [ ] Contracts updated if needed
- [ ] Specs updated first if required (error-handling.md 에러코드 등록)
- [ ] Ready for review
