# Task ID

TASK-BE-018

# Title

product-service 상품 수정 + 삭제 API — PATCH /api/admin/products/{id}, ProductUpdated/ProductDeleted 이벤트

# Status

review

# Owner

backend

# Task Tags

- code
- api
- event

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

상품 정보 수정 및 삭제 API를 구현한다. 변경 시 `ProductUpdated`, 삭제 시 `ProductDeleted` 이벤트를 발행하여 search-service가 색인을 갱신할 수 있게 한다.

이 태스크 완료 후: 관리자가 상품 정보와 상태를 수정하고, 상품을 삭제할 수 있다.

---

# Scope

## In Scope

- `PATCH /api/admin/products/{productId}` — 상품 정보 수정 (partial update: name, description, price, status)
- `DELETE /api/admin/products/{productId}` — 상품 삭제 (soft delete 또는 hard delete)
- Application service: `UpdateProductService`, `DeleteProductService`
- `ProductUpdated` 이벤트 발행 (수정 성공 시)
- `ProductDeleted` 이벤트 발행 (삭제 성공 시)

## Out of Scope

- variant 수정/추가/삭제 (별도 태스크)
- 재고 조정 (TASK-BE-019)
- 상품 복구 API

---

# Acceptance Criteria

- [ ] `PATCH /api/admin/products/{productId}` 성공 시 200과 `{ "id": "..." }` 반환
- [ ] 부분 수정(partial update)을 지원한다 — 전달된 필드만 수정
- [ ] 수정 후 `ProductUpdated` 이벤트가 발행된다 (payload가 계약과 일치)
- [ ] `DELETE /api/admin/products/{productId}` 성공 시 204 반환
- [ ] 삭제 후 `ProductDeleted` 이벤트가 발행된다
- [ ] 삭제된 상품 조회 시 404 반환
- [ ] 존재하지 않는 productId 수정/삭제 시 404 + `{ "code": "PRODUCT_NOT_FOUND" }` 반환
- [ ] 단위 테스트 및 통합 테스트가 추가된다
- [ ] 기존 모든 테스트가 통과한다

---

# Related Specs

- `specs/services/product-service/architecture.md`
- `specs/platform/error-handling.md`
- `specs/platform/testing-strategy.md`

# Related Skills

- `.claude/skills/backend/springboot-api.md`
- `.claude/skills/backend/testing-backend.md`

---

# Related Contracts

- `specs/contracts/http/product-api.md`
- `specs/contracts/events/product-events.md`

---

# Target Service

- `product-service`

---

# Architecture

Follow:

- `specs/services/product-service/architecture.md`

계층 배치:
- Domain: `ProductUpdated`, `ProductDeleted` 이벤트 레코드
- Application: `UpdateProductService`, `DeleteProductService`, `UpdateProductCommand`
- Infrastructure: JPA 수정/삭제 처리
- Interface: `AdminProductController` 업데이트 (TASK-BE-017에서 생성된 컨트롤러 확장)

---

# Implementation Notes

### Partial Update 전략

null 필드는 수정하지 않는 방식으로 처리. `@JsonInclude(NON_NULL)` 또는 Optional 필드 사용.

### Soft Delete vs Hard Delete

`products` 테이블에 `deleted_at TIMESTAMPTZ` 컬럼 추가(soft delete) 권장. 이후 재고 이력, 이벤트 추적에 유리.

### 이벤트 발행 실패 격리

TASK-BE-017과 동일 패턴 적용.

---

# Edge Cases

- 이미 삭제된 상품을 다시 삭제 시 → 404 반환
- status를 현재 값과 동일하게 수정 시 → 정상 처리 (멱등)
- 수정 요청 body가 빈 객체 `{}` 인 경우 → 변경 없이 200 반환 (또는 400, 정책 결정)
- 재고가 있는 상품 삭제 → 허용 (재고 정합성은 order-service 책임)

---

# Failure Scenarios

- DB 수정 후 이벤트 발행 실패 → 수정은 적용됨, 이벤트 유실 로깅
- DB 수정 실패 → 500 반환, 이벤트 미발행
- 동시 수정 요청 → JPA optimistic locking 또는 last-write-wins 허용

---

# Test Requirements

- 단위 테스트: `UpdateProductServiceTest` — 수정 성공, 없는 상품, 이벤트 발행
- 단위 테스트: `DeleteProductServiceTest` — 삭제 성공, 없는 상품, 이벤트 발행
- 컨트롤러 슬라이스: `AdminProductControllerTest` — PATCH/DELETE 요청 검증
- 통합 테스트: `ProductUpdateDeleteIntegrationTest` — 수정/삭제 후 조회 결과 검증

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added
- [ ] Tests passing
- [ ] Contracts updated if needed
- [ ] Specs updated first if required
- [ ] Ready for review
