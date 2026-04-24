# Task ID

TASK-BE-016

# Title

product-service 부트스트랩 — 프로젝트 구조, DB, 도메인 모델

# Status

review

# Owner

backend

# Task Tags

- code

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

product-service의 기반 구조를 구축한다. 프로젝트 골격, DB 스키마, 핵심 도메인 모델을 완성하여 이후 기능 구현 태스크들이 이 위에서 시작할 수 있도록 한다.

이 태스크 완료 후: product-service가 기동되고, DB 마이그레이션이 완료되며, 도메인 모델이 정의된다.

---

# Scope

## In Scope

- Gradle 멀티모듈 설정 (apps/product-service)
- application.yml 기본 설정 (DB, Redis, Kafka placeholder)
- Flyway 마이그레이션: `products`, `product_variants`, `categories` 테이블 생성
- 도메인 모델 구현:
  - Aggregate: `Product`, `Inventory`
  - Entity: `ProductVariant`, `Category`
  - Value Object: `Price`, `StockQuantity`, `ProductStatus`
  - Repository 인터페이스: `ProductRepository`, `InventoryRepository`, `CategoryRepository`
- Infrastructure: JPA 엔티티 및 Spring Data 레포지토리
- 기본 헬스체크 (`GET /actuator/health`)

## Out of Scope

- API 엔드포인트 구현 (TASK-BE-017~019)
- 이벤트 발행 (TASK-BE-017~019)
- 카테고리 관리 API

---

# Acceptance Criteria

- [ ] `apps/product-service` 모듈이 빌드된다
- [ ] `products` 테이블: `id (UUID PK)`, `name`, `description`, `price`, `status`, `category_id`, `created_at`, `updated_at`
- [ ] `product_variants` 테이블: `id (UUID PK)`, `product_id (FK)`, `option_name`, `stock`, `additional_price`
- [ ] `categories` 테이블: `id (UUID PK)`, `name`, `parent_id (nullable)`
- [ ] `Product` 애그리게이트가 불변 규칙을 도메인 레이어에서 강제한다 (stock은 음수 불가)
- [ ] `ProductStatus` 값 객체: `ON_SALE`, `SOLD_OUT`, `HIDDEN`
- [ ] `ProductRepository`, `InventoryRepository` 인터페이스가 도메인 레이어에 위치한다
- [ ] JPA 구현체가 인프라 레이어에 위치한다
- [ ] 서비스가 기동되고 `/actuator/health`가 200을 반환한다
- [ ] Testcontainers로 DB 연동 통합 테스트가 통과한다

---

# Related Specs

- `specs/services/product-service/architecture.md`
- `specs/services/product-service/overview.md`
- `specs/platform/coding-rules.md`
- `specs/platform/naming-conventions.md`
- `specs/platform/testing-strategy.md`

# Related Skills

- `.claude/skills/backend/springboot-api.md`
- `.claude/skills/backend/testing-backend.md`
- `.claude/skills/backend/implementation-workflow.md`

---

# Related Contracts

- `specs/contracts/http/product-api.md`

---

# Target Service

- `product-service`

---

# Architecture

Follow:

- `specs/services/product-service/architecture.md`

계층 배치:
- Domain: `Product`, `Inventory`, `ProductVariant`, `Category`, `Price`, `StockQuantity`, `ProductStatus`, Repository 인터페이스
- Application: (TASK-BE-017부터)
- Infrastructure: JPA 엔티티, Spring Data 레포지토리 구현체, Flyway 마이그레이션
- Interface: (TASK-BE-017부터)

---

# Implementation Notes

### 도메인 불변 규칙

```java
// StockQuantity 값 객체 — 음수 방지
public record StockQuantity(int value) {
    public StockQuantity {
        if (value < 0) throw new IllegalArgumentException("Stock cannot be negative");
    }
}

// Product 애그리게이트 — 최소 1개 variant 강제
public void addVariant(ProductVariant variant) {
    // variant null 체크
}
```

### DDD 구조

auth-service(Layered)와 달리 DDD-style 사용. 도메인 레이어에 비즈니스 규칙 집중.

---

# Edge Cases

- `categories` 테이블의 `parent_id`가 null인 경우 → 최상위 카테고리
- `product_variants` 없이 상품 생성 시도 → 도메인 레이어에서 예외
- stock 값이 Integer 범위를 초과하는 경우 → DB 컬럼 INT 범위 내로 제한

---

# Failure Scenarios

- Flyway 마이그레이션 실패 → 서비스 기동 차단 (Flyway 기본 동작)
- DB 연결 실패 → 헬스체크 DOWN 반환
- JPA 엔티티와 도메인 모델 매핑 불일치 → 단위 테스트에서 조기 발견

---

# Test Requirements

- 단위 테스트: `ProductTest` — 도메인 불변 규칙 검증 (stock 음수, variant 없는 상품 등)
- 단위 테스트: `StockQuantityTest`, `PriceTest` — VO 생성 및 유효성
- 통합 테스트: `ProductRepositoryTest` — Testcontainers PostgreSQL로 CRUD 검증

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added
- [ ] Tests passing
- [ ] Contracts updated if needed
- [ ] Specs updated first if required
- [ ] Ready for review
