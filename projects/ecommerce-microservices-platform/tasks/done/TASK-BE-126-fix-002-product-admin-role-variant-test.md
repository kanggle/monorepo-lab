# Task ID

TASK-BE-126-fix-002

# Title

TASK-BE-126-fix-001 리뷰 수정 — register / addVariant / updateVariant / deleteVariant role 검증 테스트 누락 추가

# Status

done

# Owner

backend

# Task Tags

- code
- test
- security

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

TASK-BE-126-fix-001 리뷰에서 발견된 누락 테스트를 추가한다.

`AdminProductController`의 `register`(POST), `addVariant`(POST), `updateVariant`(PATCH), `deleteVariant`(DELETE)
4개 핸들러에 대한 role 검증 케이스(`ROLE_USER → 403`, 헤더 미포함 → 403)가 없다.
`AdminProductImageController`의 `listImages`(GET)도 role check가 적용되어 있으나 검증 케이스가 없다.

---

# Scope

## In Scope

- `AdminProductControllerTest` — register, addVariant, updateVariant, deleteVariant 각각에 대해
  - `ROLE_USER` 헤더 → 403 / ACCESS_DENIED
  - 헤더 미포함 → 403 / ACCESS_DENIED
  총 8개 테스트 메서드 추가
- `AdminProductImageControllerTest` — listImages에 대해
  - `ROLE_USER` 헤더 → 403 / ACCESS_DENIED
  - 헤더 미포함 → 403 / ACCESS_DENIED
  총 2개 테스트 메서드 추가 (`get` import 포함)

## Out of Scope

- 컨트롤러 구현 코드 변경
- 기존 success/error 케이스 변경
- variant 성공 시나리오 신규 추가

---

# Acceptance Criteria

- [ ] `./gradlew :apps:product-service:test` 전체 통과
- [ ] `register_userRole_returns403` / `register_missingRoleHeader_returns403` 추가
- [ ] `addVariant_userRole_returns403` / `addVariant_missingRoleHeader_returns403` 추가
- [ ] `updateVariant_userRole_returns403` / `updateVariant_missingRoleHeader_returns403` 추가
- [ ] `deleteVariant_userRole_returns403` / `deleteVariant_missingRoleHeader_returns403` 추가
- [ ] `listImages_userRole_returns403` / `listImages_missingRoleHeader_returns403` 추가
- [ ] 모든 role check 테스트에서 `jsonPath("$.code").value("ACCESS_DENIED")` 검증 포함

---

# Related Specs

- `specs/platform/error-handling.md`
- `specs/platform/testing-strategy.md`

---

# Related Contracts

- 변경 없음

---

# Target Service

- `product-service`

---

# Architecture

`@Valid` 검증은 메서드 바디 실행 전에 일어나므로, POST/PATCH 엔드포인트 role 검증 테스트에는
Bean Validation을 통과할 수 있는 유효한 요청 바디를 제공해야 403을 얻을 수 있다.

- `register`: `{ "name": "테스트 상품", "price": 10000, "variants": [{ "optionName": "기본", "stock": 10, "additionalPrice": 0 }] }`
- `addVariant`: `{ "optionName": "옵션A", "stock": 10, "additionalPrice": 0 }`
- `updateVariant`: `{ "optionName": "옵션A", "additionalPrice": 0 }`
- `deleteVariant` / `listImages`: 바디 불필요

---

# Edge Cases

- `required = false`로 선언된 헤더이므로 미포함 → null → validateAdminRole(null) → 403 (MissingRequestHeaderException 아님)
- POST register, addVariant: @Valid가 먼저 실행되므로 유효한 바디 필수

---

# Failure Scenarios

- 해당 없음

---

# Test Requirements

- `AdminProductControllerTest`: register/addVariant/updateVariant/deleteVariant role 검증 8개
- `AdminProductImageControllerTest`: listImages role 검증 2개

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests passing (`./gradlew :apps:product-service:test`)
- [ ] Contracts updated if needed
- [ ] Specs updated if required
- [ ] Ready for review
