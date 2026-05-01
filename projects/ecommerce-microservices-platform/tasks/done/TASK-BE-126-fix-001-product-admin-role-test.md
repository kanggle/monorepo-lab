# Task ID

TASK-BE-126-fix-001

# Title

TASK-BE-126 리뷰 수정 — 기존 테스트 X-User-Role 헤더 누락 및 role 검증 신규 테스트 추가

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

TASK-BE-126 리뷰에서 발견된 테스트 이슈를 수정한다.

`AdminProductController`와 `AdminProductImageController`에 `validateAdminRole()` 이 추가됐지만,
기존 테스트가 `X-User-Role` 헤더 없이 호출하여 전부 403으로 깨지며,
ROLE_USER → 403 신규 검증 케이스가 한 건도 없다.

---

# Scope

## In Scope

- `AdminProductControllerTest` — 모든 기존 success/error 케이스에 `.header("X-User-Role", "ADMIN")` 추가
- `AdminProductImageControllerTest` — 동일
- `ProductControllerTest` 내 `/api/admin/products` 호출 케이스 — 헤더 추가
- `ProductApiContractTest` 내 admin 경로 케이스 — 헤더 추가
- 두 컨트롤러의 각 엔드포인트에 role 검증 신규 케이스 추가:
  - `ROLE_USER` 헤더 → 403/ACCESS_DENIED
  - 헤더 미포함 → 403/ACCESS_DENIED (required=false이므로 null → validateAdminRole에서 403)
- `AdminOrderControllerTest` 패턴을 참고 기준으로 사용

## Out of Scope

- 컨트롤러 구현 코드 변경 (이미 완료)
- variants 엔드포인트 이외 범위 확장

---

# Acceptance Criteria

- [ ] `./gradlew :apps:product-service:test` 전체 통과
- [ ] `AdminProductControllerTest` — 모든 기존 케이스가 ADMIN 헤더 포함 후 통과
- [ ] `AdminProductImageControllerTest` — 모든 기존 케이스가 ADMIN 헤더 포함 후 통과
- [ ] `ProductControllerTest` 및 `ProductApiContractTest` — admin 경로 케이스 통과
- [ ] `AdminProductControllerTest` — 각 엔드포인트에 대해 ROLE_USER → 403, 헤더 미포함 → 403 케이스 존재
- [ ] `AdminProductImageControllerTest` — 동일

---

# Related Specs

- `specs/platform/error-handling.md`
- `specs/platform/testing-strategy.md`
- `specs/services/product-service/architecture.md`

---

# Related Contracts

- 변경 없음

---

# Target Service

- `product-service`

---

# Architecture

`AdminOrderControllerTest` 패턴 참조:

```java
// ROLE_USER → 403
mockMvc.perform(post("/api/admin/products")
    .header("X-User-Role", "USER")
    .contentType(MediaType.APPLICATION_JSON)
    .content("{}"))
    .andExpect(status().isForbidden())
    .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

// 헤더 미포함 → 403
mockMvc.perform(post("/api/admin/products")
    .contentType(MediaType.APPLICATION_JSON)
    .content("{}"))
    .andExpect(status().isForbidden())
    .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
```

---

# Edge Cases

- `required = false` 로 선언됐으므로 헤더 미포함 → null → `validateAdminRole(null)` → 403 (MissingRequestHeaderException 아님)
- 기존 400 기대 케이스(validation error)는 role check가 먼저 실행되어 403이 되므로, 헤더 추가 후 원래 기대 상태코드(400)가 복원돼야 함

---

# Failure Scenarios

- 해당 없음

---

# Test Requirements

- `AdminProductControllerTest`: 기존 케이스 헤더 추가 + role 검증 신규 케이스
- `AdminProductImageControllerTest`: 동일
- `ProductControllerTest`, `ProductApiContractTest`: admin 경로 헤더 추가

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests passing (`./gradlew :apps:product-service:test`)
- [ ] Contracts updated if needed
- [ ] Specs updated if required
- [ ] Ready for review
