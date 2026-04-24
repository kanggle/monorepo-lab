# Task ID

TASK-BE-019

# Title

product-service 재고 조정 API — PATCH /api/admin/products/{id}/stock, StockChanged 이벤트

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

관리자가 상품 variant의 재고를 조정할 수 있는 API를 구현한다. 조정 시 `StockChanged` 이벤트를 발행하여 search-service와 order-service가 재고 변화를 수신할 수 있게 한다.

이 태스크 완료 후: 재고 조정 API가 동작하고, 조정 결과가 이벤트로 발행된다.

---

# Scope

## In Scope

- `PATCH /api/admin/products/{productId}/stock` — 재고 조정
- Application service: `AdjustStockService`
- `StockChanged` 이벤트 발행 (조정 성공 시, 증감 방향 무관)
- `reason` 값: `RESTOCK`, `ADMIN_ADJUSTMENT` (관리자 조정 범위)
- 재고 조정 후 `ProductStatus` 자동 변경 (stock=0이면 `SOLD_OUT`)

## Out of Scope

- `ORDER_RESERVED`, `ORDER_CANCELLED` reason (order-service 연동 시 구현)
- 재고 이력 조회 API
- 재고 예약/해제 API

---

# Acceptance Criteria

- [ ] `PATCH /api/admin/products/{productId}/stock` 성공 시 200과 `{ "variantId": "...", "currentStock": ... }` 반환
- [ ] 조정 후 DB의 재고가 갱신된다
- [ ] 조정 성공 시 `StockChanged` 이벤트가 발행된다 (`previousStock`, `currentStock`, `delta`, `reason` 포함, 계약과 일치)
- [ ] 재고 조정 후 stock이 0이 되면 해당 상품의 status가 `SOLD_OUT`으로 변경된다
- [ ] RESTOCK으로 stock이 0 초과가 되면 `SOLD_OUT` 상품이 `ON_SALE`로 변경된다
- [ ] 재고 조정 결과가 음수가 되는 경우 400 + 적절한 에러 코드 반환
- [ ] 존재하지 않는 productId 또는 variantId 요청 시 404 반환
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
- Domain: `StockChanged` 이벤트 레코드, `StockQuantity` VO에 조정 로직, `Inventory` 애그리게이트에 불변 규칙
- Application: `AdjustStockService`, `AdjustStockCommand`
- Infrastructure: JPA 재고 갱신
- Interface: `AdminProductController` 확장 (TASK-BE-017에서 생성)

---

# Implementation Notes

### 재고 조정 로직

`Inventory` 애그리게이트에서 재고 변경 및 불변 규칙 강제:

```java
public void adjustStock(String variantId, int delta) {
    // delta 적용 후 음수면 예외
    // stock == 0이면 상품 status SOLD_OUT 처리
}
```

### StockChanged 이벤트

`previousStock`과 `currentStock`을 모두 포함해야 한다. `delta`는 양수(증가) 또는 음수(감소) 모두 가능.

### 이벤트 발행 실패 격리

TASK-BE-017과 동일 패턴 적용.

---

# Edge Cases

- `quantity`가 0인 요청 → 400 반환 (변경 없는 조정은 의미 없음)
- 재고를 현재 재고보다 많이 감소 시 → `StockQuantity` VO에서 예외 → 400
- `HIDDEN` 상태 상품의 재고 조정 → 허용 (상태와 무관하게 재고 조정 가능)
- 동시 재고 조정 요청 → 낙관적 락 또는 DB 레벨 원자적 연산으로 처리

---

# Failure Scenarios

- DB 갱신 후 이벤트 발행 실패 → 재고는 반영됨, 이벤트 유실 로깅
- DB 갱신 실패 → 500 반환, 이벤트 미발행
- 동시 조정으로 재고 음수 방지 실패 → `StockQuantity` VO 검증에서 최후 방어

---

# Test Requirements

- 단위 테스트: `AdjustStockServiceTest` — 증가/감소 성공, 음수 방지, SOLD_OUT 전환, 이벤트 발행
- 단위 테스트: `InventoryTest` — 재고 불변 규칙 (음수 방지, status 자동 전환)
- 컨트롤러 슬라이스: `AdminProductControllerTest` — stock PATCH 요청 검증
- 통합 테스트: `StockAdjustmentIntegrationTest` — 재고 조정 후 DB 반영 + 이벤트 발행 검증

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added
- [ ] Tests passing
- [ ] Contracts updated if needed
- [ ] Specs updated first if required
- [ ] Ready for review
