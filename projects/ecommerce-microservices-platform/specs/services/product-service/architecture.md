# product-service — Architecture

This document declares the internal architecture of `product-service`.
All implementation tasks targeting this service must follow this declaration
and `platform/architecture-decision-rule.md`.

---

## Identity

| Field | Value |
|---|---|
| Service name | `product-service` |
| Project | `ecommerce-microservices-platform` |
| Service Type | `rest-api` (single — see Service Type Composition below) |
| Architecture Style | **DDD-style Architecture** (4-layer + domain/port) |
| Domain | ecommerce |
| Primary language / stack | Java 21, Spring Boot |
| Bounded Context | Product catalog (aggregates / variants / inventory / pricing / lifecycle) |
| Deployable unit | `apps/product-service/` |
| Data store | PostgreSQL (owned) + S3 (product image storage via ProductImageBucketResolver port, TASK-BE-143) |
| Event publication | Kafka via outbox (product.* lifecycle events) |
| Event consumption | none (single-type rest-api) |

### Service Type Composition

`product-service` is a single-type `rest-api` service per
`platform/service-types/INDEX.md`. Product catalog 도메인 — aggregates,
variants, inventory, pricing rules, lifecycle. S3 image storage 는
ProductImageBucketResolver domain port 경유 (TASK-BE-143 cherry-pick). 적용되는
규칙:
[platform/service-types/rest-api.md](../../../../../platform/service-types/rest-api.md).

---

## Why This Architecture
Product management involves meaningful domain concepts: product aggregates, variants, inventory, pricing rules, and lifecycle states.

Domain invariants (e.g. stock cannot go negative, a product must have at least one variant) require aggregate-level enforcement.

DDD-style keeps these rules in the domain layer and prevents them from leaking into infrastructure or presentation.

## Internal Structure Rule
This service uses a domain-driven internal structure.

Recommended internal areas:
- interface (presentation)
- application
- domain
- infrastructure

Key domain concepts:
- Aggregates: Product, Inventory
- Entities: ProductVariant, Category
- Value Objects: Price, StockQuantity, ProductStatus
- Domain Events: ProductCreated, ProductUpdated, StockChanged
- Repositories: ProductRepository, InventoryRepository

## Allowed Dependencies
- interface -> application
- application -> domain
- infrastructure -> domain
- infrastructure -> application ports

## Forbidden Dependencies
- domain must not depend on framework or persistence details
- application must not contain domain rules that belong in aggregates
- controllers must not bypass application services
- repositories must not contain business decisions

## Boundary Rules
- interface layer handles HTTP mapping and request validation entry
- application layer coordinates use-cases and transaction boundaries
- domain layer owns product and inventory rules and invariants
- infrastructure layer handles persistence, event publishing, and external adapters

## Outbox

- Pattern: **Direct Kafka publish** (not `libs/java-messaging` transactional outbox — `java-messaging` is not a dependency of product-service).
- Publisher: `KafkaProductEventPublisher` (`infrastructure/event`) — implements `ProductEventPublisher` domain port. Active on all profiles except `standalone`.
- Topic 매핑 (`KafkaProductEventPublisher`):
  - `ProductCreated` → `product.product.created`
  - `ProductUpdated` → `product.product.updated`
  - `ProductDeleted` → `product.product.deleted`
  - `StockChanged` → `product.product.stock-changed`
  - `ProductImagesUpdated` → `product.product.images-updated`
- Publish failures are counted via `ProductMetrics.incrementEventPublishFailure(eventType)` and logged at ERROR level; no retry or dead-letter queue is wired in v1.
- **Note**: the Identity table entry "Event publication | Kafka via outbox" reflects the intended target pattern; the v1 implementation is direct publish without a transactional outbox table. Adding the outbox is a forward-declared improvement.

## Integration Rules
- HTTP behavior must follow published contracts
- Product domain events must follow published event contracts
- search-service consumes product events — do not couple directly to search-service
- Shared libraries may be used only under shared-library policy

## Testing Expectations
Required emphasis:
- aggregate and domain rule tests
- application service tests
- repository integration tests
- event publishing tests
- stock boundary and invariant tests

## Multi-Tenancy & Marketplace (ADR-MONO-030)

> 모델 SoT = [specs/features/multi-tenancy-and-marketplace.md](../../features/multi-tenancy-and-marketplace.md) (ADR-MONO-030 Step 1). 본 섹션은 product-service 적용분만 선언한다. 컬럼/마이그레이션/게이트/셀러 구현 = Step 2(바깥 축)·Step 3(안쪽 축).

- **바깥 축 (tenant, M1-M7)**: `Product` / `ProductVariant` / `Inventory` 등 **모든 영속 aggregate/entity 에 `tenant_id` (NOT NULL)**. 모든 read 는 `WHERE tenant_id = <요청 컨텍스트>` (gateway `X-Tenant-Id`), write 는 컨텍스트 `tenant_id` 주입. cross-tenant 조회 = 404 (M3). 참조 = scm/erp `tenant_id` 컬럼.
- **안쪽 축 (seller)**: 상품 **소유권 = `(tenant_id, seller_id)`**. `seller_id` 는 product 등록(OPERATOR 표면)에서 토큰/스코프로 주입; 소비자 조회 표면엔 읽기 전용 표시. 셀러-스코프 read = ADR-025 `org_scope` 형태(net-zero: claim 부재/`'*'`=무필터).
- **이벤트**: `ProductCreated`/`StockChanged` 등 봉투에 `tenant_id` 전파(M5), 페이로드에 `seller_id`. (계약 편집 = Step 2/3.)
- **degradation (D8)**: default-tenant + default-seller 시드 → 단일 스토어 단일 셀러 동작 byte-identical; `tenant_id` claim 부재(standalone) → default tenant.
- **회귀 (M6)**: cross-tenant leak IT 필수 — 테넌트 A 상품이 B 토큰으로 안 보임.
- **PROJECT.md `multi-tenant` trait**: Step 2(코드)와 함께 추가 (ADR-030 §D7 타이밍 — 슬라이스 전 추가 시 미마이그 서비스 M1 미스분류).

## Change Rule
Any architectural change to this service must be documented here first before implementation.
