# Task ID

TASK-BE-129

# Title

[Security P1] search-service SearchAdminController POST /api/search/admin/reindex admin role check 누락 수정

# Status

ready

# Owner

backend

# Task Tags

- code
- security
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

`SearchAdminController.reindex()` 핸들러에 admin role 검증을 추가한다.

현재 `POST /api/search/admin/reindex`는 gateway를 통해 인증된 사용자라면 누구든 호출할 수 있다.
admin role 검증이 없으므로 일반 유저(`ROLE_USER`)가 전체 상품 재인덱싱(수천 건 DB 조회 + ES 쓰기)을
트리거하여 DoS를 유발할 수 있다.

`AdminProductController`·`AdminOrderController` 등이 공통으로 사용하는
`validateAdminRole(userRole)` 헬퍼 패턴을 적용한다.

현재 컨트롤러 주석에는 "Gateway에서 차단 필요"라고 명시돼 있으나,
실제로 RouteService는 `/api/search/**` GET만 public으로 처리하고,
POST는 인증만 요구할 뿐 역할을 검사하지 않으므로 서비스 레이어에서 보완이 필수이다.

---

# Scope

## In Scope

- `SearchAdminController.reindex()` 핸들러에
  `@RequestHeader(value = "X-User-Role", required = false) String userRole` 파라미터 추가
- 핸들러 첫 번째 줄에서 `validateAdminRole(userRole)` 호출
- `validateAdminRole(String userRole)` private 메서드 추가
  (`"ADMIN".equalsIgnoreCase(userRole)`가 false이면 `AccessDeniedException` throw)
- GlobalExceptionHandler가 없으므로 search-service에 `AccessDeniedException` → 403 매핑 확인
- 단위 테스트: `SearchAdminControllerTest`
  - `ROLE_USER` 요청 → 403 / ACCESS_DENIED
  - 헤더 미포함 → 403 / ACCESS_DENIED
  - `ROLE_ADMIN` 요청 → 200 (기존 성공 케이스 헤더 추가)

## Out of Scope

- gateway RouteService에서 `/api/search/admin/**` 경로 차단 (별도 TASK)
- PromotionController / ShippingController 서비스 레이어 → 컨트롤러 레이어 이동 (별도 TASK)

---

# Acceptance Criteria

- [ ] `POST /api/search/admin/reindex` — `ROLE_USER`로 요청 시 403 반환
- [ ] `POST /api/search/admin/reindex` — `X-User-Role` 헤더 미포함 시 403 반환
- [ ] `POST /api/search/admin/reindex` — `ROLE_ADMIN`으로 요청 시 200 반환
- [ ] `./gradlew :projects:ecommerce-microservices-platform:apps:search-service:test` 전체 통과

---

# Related Specs

- `specs/platform/error-handling.md` (403 에러 코드 및 형식)
- `specs/platform/architecture.md`
- `specs/services/search-service/architecture.md`

---

# Related Skills

- `.claude/skills/backend/spring-boot-service.md`

---

# Related Contracts

- 변경 없음 (기존 403 응답 명세 확인)

---

# Target Service

- `search-service`

---

# Architecture

기존 패턴 참조:

```java
// AdminProductController 예시
@PostMapping
public ResponseEntity<?> createProduct(
    @RequestHeader(value = "X-User-Role", required = false) String userRole, ...) {
    validateAdminRole(userRole);
    ...
}

private void validateAdminRole(String userRole) {
    if (!"ADMIN".equalsIgnoreCase(userRole)) {
        throw new AccessDeniedException();
    }
}
```

`SearchAdminController.reindex()`에 동일하게 적용한다.

search-service의 `GlobalExceptionHandler`가 `AccessDeniedException`을 403으로 처리하는지 확인 후,
누락 시 추가한다.

---

# Edge Cases

- `X-User-Role` 헤더가 없는 경우 → `required = false` → null → `validateAdminRole(null)` → 403
- `X-User-Role: admin` (소문자) → `equalsIgnoreCase` 처리로 ADMIN과 동일하게 인정 (기존 패턴 따름)

---

# Failure Scenarios

- `validateAdminRole` 내부에서 예외를 던지면 GlobalExceptionHandler가 처리하여 403 응답 반환

---

# Test Requirements

- **단위 테스트**: `SearchAdminControllerTest`
  - `ROLE_USER` → 403 / ACCESS_DENIED
  - 헤더 미포함 → 403 / ACCESS_DENIED
  - `ROLE_ADMIN` + 정상 파라미터 → 200

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added
- [ ] Tests passing
- [ ] Contracts updated if needed
- [ ] Specs updated first if required
- [ ] Ready for review
