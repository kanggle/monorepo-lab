# Task ID

TASK-BE-126

# Title

[Security P0] product-service AdminProductController / AdminProductImageController admin role check 누락 수정

# Status

done

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

`AdminProductController`와 `AdminProductImageController`의 모든 핸들러에 admin role 검증을 추가한다.

현재 두 컨트롤러는 `X-User-Role` 헤더를 전혀 검사하지 않아, 일반 유저(`ROLE_USER`)가
상품 생성·수정·삭제·재고 조정·이미지 업로드·이미지 삭제를 모두 수행할 수 있다.
`AdminOrderController`·`AdminUserController`·`TemplateController`가 공통으로 사용하는
`validateAdminRole(userRole)` 헬퍼가 이미 존재하므로, 동일 패턴으로 두 컨트롤러에 적용한다.

---

# Scope

## In Scope

- `AdminProductController` 의 모든 핸들러에 `@RequestHeader("X-User-Role") String userRole` 파라미터 추가 및 `validateAdminRole(userRole)` 호출
- `AdminProductImageController` 의 모든 핸들러에 동일 패턴 적용
- 각 핸들러에 대한 단위/통합 테스트: 일반 유저 요청 시 403 반환 검증

## Out of Scope

- Spring Security `SecurityFilterChain` 도입 (패턴 통일은 별도 리팩터링 태스크)
- 다른 서비스의 admin 컨트롤러 검토

---

# Acceptance Criteria

- [ ] `POST /api/admin/products` — `ROLE_USER`로 요청 시 403 반환
- [ ] `PATCH /api/admin/products/{productId}` — `ROLE_USER`로 요청 시 403 반환
- [ ] `DELETE /api/admin/products/{productId}` — `ROLE_USER`로 요청 시 403 반환
- [ ] `PATCH /api/admin/products/{productId}/stock` — `ROLE_USER`로 요청 시 403 반환
- [ ] `POST /api/admin/products/{productId}/images/upload-url` — `ROLE_USER`로 요청 시 403 반환
- [ ] `POST /api/admin/products/{productId}/images` — `ROLE_USER`로 요청 시 403 반환
- [ ] `PATCH /api/admin/products/{productId}/images/{imageId}` — `ROLE_USER`로 요청 시 403 반환
- [ ] `DELETE /api/admin/products/{productId}/images/{imageId}` — `ROLE_USER`로 요청 시 403 반환
- [ ] `ROLE_ADMIN`으로 요청 시 기존 비즈니스 로직이 정상 실행된다
- [ ] 기존 통합 테스트가 전부 통과한다

---

# Related Specs

- `specs/platform/error-handling.md` (403 에러 코드 및 형식)
- `specs/platform/architecture.md`
- `specs/platform/coding-rules.md`
- `specs/services/product-service/architecture.md`

---

# Related Skills

- `.claude/skills/backend/spring-boot-service.md`

---

# Related Contracts

- `specs/contracts/http/product-api.md` — 변경 없음 (기존 403 응답 명세 확인)

---

# Target Service

- `product-service`

---

# Architecture

기존 패턴 참조:

```java
// AdminOrderController 예시
@GetMapping
public ResponseEntity<?> listOrders(
    @RequestHeader("X-User-Role") String userRole, ...) {
    validateAdminRole(userRole);
    ...
}
```

`AdminProductController`와 `AdminProductImageController`의 모든 `@GetMapping`/`@PostMapping`/`@PatchMapping`/`@DeleteMapping` 핸들러에 동일하게 적용한다.

---

# Edge Cases

- `X-User-Role` 헤더가 아예 없는 경우 → Spring MVC가 `MissingRequestHeaderException` → 기존 GlobalExceptionHandler가 400 반환 (게이트웨이를 통과한 요청에는 항상 존재)
- `X-User-Role: ADMIN` (대소문자) → `validateAdminRole`의 기존 비교 방식에 맞게 처리

---

# Failure Scenarios

- `validateAdminRole` 내부에서 예외를 던지면 GlobalExceptionHandler가 처리하여 403 응답 반환 — 기존 동작 유지

---

# Test Requirements

- **단위 테스트**: `AdminProductController`, `AdminProductImageController` — `ROLE_USER` 요청 시 403, `ROLE_ADMIN` 요청 시 정상 응답
- **통합 테스트**: 각 엔드포인트에 대해 인증된 일반 유저 요청 → 403 검증

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added
- [ ] Tests passing
- [ ] Contracts updated if needed
- [ ] Specs updated first if required
- [ ] Ready for review
