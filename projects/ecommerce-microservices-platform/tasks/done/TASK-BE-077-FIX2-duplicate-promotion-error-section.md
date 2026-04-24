# Task ID

TASK-BE-077-FIX2-duplicate-promotion-error-section

# Title

error-handling.md Promotion 섹션 중복 제거

# Status

review

# Owner

backend

# Task Tags

- docs
- spec

---

# Goal

`specs/platform/error-handling.md`에 Promotion 에러 코드 섹션이 중복으로 등록되어 있다. 첫 번째 섹션(125번째 줄 근처)과 두 번째 섹션(177번째 줄 근처)이 존재하며, `INVALID_PROMOTION_REQUEST`의 설명도 두 섹션 간에 서로 다르다.

- 첫 번째 섹션: `Promotion request is invalid (missing or invalid fields)`
- 두 번째 섹션: `Promotion request field is invalid (bad status filter, invalid date format)`

중복 섹션을 제거하고 단일 Promotion 섹션만 남겨야 한다.

---

# Scope

## In Scope

- `specs/platform/error-handling.md`에서 중복된 Promotion 섹션 중 하나를 제거
- 남기는 섹션의 `INVALID_PROMOTION_REQUEST` 설명을 두 의미를 모두 포괄하도록 통합
- 섹션 위치는 기존 서비스별 에러 코드 섹션들과의 순서를 유지 (Shipping 섹션 앞에 위치)

## Out of Scope

- GlobalExceptionHandler 코드 변경
- 컨트롤러 테스트 변경
- 새 에러 코드 추가

---

# Acceptance Criteria

- [x] `specs/platform/error-handling.md`에 Promotion 섹션이 정확히 1개만 존재함
- [x] 남은 섹션에 10개 에러 코드가 모두 등록됨
- [x] `INVALID_PROMOTION_REQUEST`의 설명이 두 용도(missing/invalid fields, bad status filter, invalid date format)를 모두 포괄함
- [x] Rules 섹션 앞에 중복 섹션이 없음

---

# Related Specs

- `specs/platform/error-handling.md`

# Related Contracts

- `specs/contracts/http/promotion-api.md`

---

# Edge Cases

- 두 섹션 중 어느 것을 기준으로 통합할지 선택할 때, promotion-api.md 계약과 GlobalExceptionHandler 구현 코드 양쪽을 모두 만족해야 함

---

# Failure Scenarios

- 중복 섹션이 남아 있으면 에러 코드 레지스트리의 신뢰성이 저하됨
