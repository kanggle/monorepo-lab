# Task ID

TASK-BE-030-fix2-register-saas-error-codes

# Title

admin-service — platform/error-handling.md에 saas 도메인 에러 코드 등록

# Status

ready

# Owner

backend

# Task Tags

- spec
- docs

# depends_on

- TASK-BE-030-fix-downstream-4xx-and-idempotency-race

---

# Goal

TASK-BE-030-fix 리뷰에서 발견된 Critical 1건을 해소한다. `platform/error-handling.md`에 saas 도메인 섹션이 없어, admin-service가 이미 사용 중인 에러 코드 4건이 미등록 상태이다. `platform/error-handling.md`의 Change Rule("Error codes must be registered in this document before use")에 위반된다.

---

# Scope

## In Scope

- `platform/error-handling.md`에 `## Admin Operations [domain: saas]` 섹션 추가.
- 등록 대상 코드:
  | Code | HTTP | Description |
  |---|---|---|
  | `BATCH_SIZE_EXCEEDED` | 422 | accountIds가 배치 상한(100)을 초과 |
  | `IDEMPOTENCY_KEY_CONFLICT` | 409 | 동일 Idempotency-Key로 다른 payload 재전송 |
  | `AUDIT_FAILURE` | 500 | 감사 row 기록 실패; 명령 중단 |
  | `ACCOUNT_NOT_FOUND` | 404 | 대상 계정 미존재 (admin 경로 전용) |
  | `STATE_TRANSITION_INVALID` | 422 | 현재 상태에서 허용되지 않는 상태 전이 (admin 경로) |
- `rules/domains/saas.md`의 Standard Error Codes 섹션에 위 코드 cross-reference 추가.

## Out of Scope

- 코드 구현 변경 없음 (spec-only fix)

---

# Acceptance Criteria

- [ ] `platform/error-handling.md`에 `[domain: saas]` 태그가 붙은 Admin Operations 섹션 존재
- [ ] 5개 코드 모두 등록됨
- [ ] `rules/domains/saas.md`에 cross-reference 추가됨
- [ ] 기존 ecommerce 섹션 변경 없음

---

# Related Specs

- `platform/error-handling.md` (Change Rule)
- `rules/domains/saas.md`
- `specs/contracts/http/admin-api.md`

# Related Contracts

- `specs/contracts/http/admin-api.md`

---

# Target Service

- `platform/` (spec 파일만)

---

# Edge Cases

- `ACCOUNT_NOT_FOUND`는 account-api.md에도 이미 쓰이므로 admin 경로 전용 맥락임을 description에 명시

---

# Failure Scenarios

- 없음 (spec 파일 편집만)

---

# Test Requirements

- 없음 (spec-only)

---

# Definition of Done

- [ ] 구현 완료
- [ ] Ready for review
