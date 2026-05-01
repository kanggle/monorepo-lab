# Task ID

TASK-BE-130

# Title

[Security P2] ReviewController sort 파라미터 허용 값 화이트리스트 검증 추가

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

`ReviewController.getProductReviews()`의 `sort` 파라미터를 허용 필드 화이트리스트로 검증한다.

현재 `sort` 파라미터의 필드 부분(`createdAt,desc`의 앞부분)이 무검증으로
`ReviewRepositoryAdapter.parseSort()`에 JPA 정렬 필드로 전달된다. 유효하지 않은
필드명을 전달하면 JPA 예외가 발생해 500 응답이 반환된다(DoS 위험).
유효 필드 외의 값은 400으로 거부하여 서비스 안정성을 확보한다.

---

# Scope

## In Scope

- `ReviewController.getProductReviews()` 에 `validateSortParam()` 헬퍼 추가
  - 허용 필드: `createdAt`, `rating`
  - 허용 외 필드 → `IllegalArgumentException` → `GlobalExceptionHandler` → 400 `INVALID_REVIEW_REQUEST`
- `ReviewControllerTest` 에 테스트 케이스 1건 추가 (invalid sort → 400)
- `ReviewRepositoryAdapter.parseSort()` 에 동일 화이트리스트 검증 추가 (defense-in-depth)

## Out of Scope

- 다른 서비스의 sort 파라미터 검토
- sort 방향(`asc`/`desc`) 검증 (현재 unknown → DESC 기본값으로 안전하게 처리됨)

---

# Acceptance Criteria

- [x] `GET /api/reviews/products/{productId}?sort=invalidField` → 400, code = `INVALID_REVIEW_REQUEST`
- [x] `GET /api/reviews/products/{productId}?sort=createdAt,desc` → 200 정상 응답
- [x] `GET /api/reviews/products/{productId}?sort=rating,asc` → 200 정상 응답
- [x] sort 파라미터 미포함 → 200 정상 응답 (기존 defaultValue 동작 유지)

---

# Related Specs

- `specs/platform/error-handling.md` (400 에러 코드 및 형식)
- `specs/services/review-service/architecture.md`

---

# Related Skills

- `.claude/skills/backend/spring-boot-service.md`

---

# Related Contracts

- `specs/contracts/http/review-api.md` — 변경 없음 (400 응답은 기존 명세 내 포함)

---

# Target Service

- `review-service`

---

# Architecture

허용 필드 상수와 검증 헬퍼를 컨트롤러에 추가:

```java
private static final Set<String> ALLOWED_SORT_FIELDS = Set.of("createdAt", "rating");

private void validateSortParam(String sort) {
    String field = sort.split(",")[0].trim();
    if (!ALLOWED_SORT_FIELDS.contains(field)) {
        throw new IllegalArgumentException("Invalid sort field: " + field);
    }
}
```

`GlobalExceptionHandler`의 기존 `handleIllegalArgument` → 400 `INVALID_REVIEW_REQUEST` 경로를 그대로 활용한다.
`ReviewRepositoryAdapter.parseSort()`에도 동일 whitelist 검증 추가 (defense-in-depth).

---

# Edge Cases

- `sort=` (빈 문자열) → Spring MVC가 `defaultValue = "createdAt,desc"` 적용 → 200 (정상 처리)
- `sort=createdAt` (방향 생략) → 필드 검증 통과, 방향은 기본 DESC
- `sort=createdAt,desc` → 정상
- `sort=rating,asc` → 정상
- `sort=DROP TABLE,desc` → 400

---

# Failure Scenarios

- `IllegalArgumentException` 발생 → `GlobalExceptionHandler.handleIllegalArgument()` → 400 `INVALID_REVIEW_REQUEST`

---

# Test Requirements

- **단위 테스트**: `ReviewControllerTest` — 허용 외 sort 필드 → 400, code = `INVALID_REVIEW_REQUEST`

---

# Definition of Done

- [x] Implementation completed
- [x] Tests added
- [x] Tests passing
- [x] Contracts updated if needed
- [x] Specs updated first if required
- [x] Ready for review
