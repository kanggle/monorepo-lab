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
| Service Type | `rest-api` (primary) + `event-consumer` (reservation saga, IAM lifecycle, WMS reconciliation) — dual-type, see Service Type Composition |
| Architecture Style | **DDD-style Architecture** (4-layer + domain/port) |
| Domain | ecommerce |
| Primary language / stack | Java 21, Spring Boot |
| Bounded Context | Product catalog (aggregates / variants / inventory / pricing / lifecycle) |
| Deployable unit | `apps/product-service/` |
| Data store | PostgreSQL (owned) + S3 (product image storage via ProductImageBucketResolver port, TASK-BE-143) |
| Event publication | Kafka via outbox (product.* lifecycle events) |
| Event consumption | `OrderPlaced` / `OrderCancelled` from `order.order.*` and `PaymentCompleted` from `payment.payment.completed` (consumer group `product-service-reservation`) — the payment-driven stock-reservation + backorder saga (TASK-BE-428). See "Stock reservation saga" below. |

### Service Type Composition

`product-service` combines two service types in one deployable unit:

- `rest-api` (**primary**) — the product catalog surface: aggregates, variants,
  inventory, pricing rules, lifecycle. S3 image storage 는
  ProductImageBucketResolver domain port 경유 (TASK-BE-143 cherry-pick).
- `event-consumer` — **six** `@KafkaListener` classes across three consumer groups:
  - `product-service-reservation` — the reservation saga:
    `order.order.placed` · `order.order.cancelled` · `payment.payment.completed`
  - `product-service-iam` — `account.status.changed` (a locked seller-operator
    account suspends the matching `Seller`, ADR-MONO-042 D4-C)
  - `product-service-wms` — `wms.inventory.adjusted.v1` ·
    `wms.inventory.received.v1` · `wms.master.sku.v1`

**This file declared `rest-api` (single) until TASK-MONO-372**, while six consumers
had been running for months. That was not cosmetic: `platform/entrypoint.md` loads
**exactly one** service-type rule file from this cell, so `event-consumer.md` — its
idempotency, retry/DLQ and version-branching MUSTs — was never in the rule set for
any task touching those consumers. HARDSTOP-10 checks that a Service Type is
*declared*, never that it is *true*; `scripts/check-service-type-drift.sh` now does.

적용되는 규칙:
[platform/service-types/rest-api.md](../../../../../platform/service-types/rest-api.md)
(primary) 와
[platform/service-types/event-consumer.md](../../../../../platform/service-types/event-consumer.md)
(consumer path).

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

### Stock reservation saga (TASK-BE-428)

product-service owns the **order-time sellability gate** (ADR-MONO-022 §D4): it
reserves (decrements) variant stock when an order is paid, and is the producer of
the `StockChanged(ORDER_RESERVED)` event the order-service confirm saga waits for.

- **Aggregate**: `StockReservation` (`order_id` unique, `tenant_id`, `status`
  `NEW|RESERVED|BACKORDERED|RELEASED`, `payment_received` flag, `@Version`) with
  child `StockReservationLine` (`variant_id`, `product_id`, `quantity`). New tables
  `stock_reservations` / `stock_reservation_lines` (Flyway, main + h2).
- **Convergence (order-independent)**: `OrderPlaced` fills the lines; `PaymentCompleted`
  sets `payment_received`. Whichever arrives first creates the row; a single
  all-or-nothing reserve fires exactly once when both inputs are present and
  `status=NEW`. (The two topics have no cross-topic ordering guarantee.)
- **Reserve (all-or-nothing)**: in one transaction, verify every line has enough
  stock, then decrement each (`Inventory.decrease`, optimistic-locked, no-negative
  invariant). Success → `RESERVED` + one `StockChanged(ORDER_RESERVED, orderId)` per
  line. Any shortage → **no decrement**, `BACKORDERED` + `OrderReservationFailed`.
- **Restock retry (FIFO)**: any positive stock increase (`AdjustStockService` admin
  restock, `WmsInventoryReconciliationService` positive delta) calls
  `ReservationRetryService.onStockIncreased(variantId)`, which re-attempts
  `BACKORDERED` reservations holding a line on that variant in `created_at` order.
- **Release (compensation)**: `OrderCancelled` → reservation `RELEASED`; if it was
  `RESERVED`, restore each line's stock + emit `StockChanged(ORDER_CANCELLED, orderId)`;
  if `BACKORDERED`/`NEW`, release without stock change (so a later restock never
  reserves for a cancelled order).
- **Idempotency**: the three reservation-saga consumers dedupe on `event_id`; per-order
  convergence and per-reservation transactions isolate concurrent retries/cancels.
  The WMS reconciliation consumers (`wms.inventory.*`, `wms.master.sku.v1`) dedupe the
  same way. `AccountStatusChangedSellerConsumer` **does not** — it is idempotent by
  construction: it acts only on `LOCKED`, and suspending an already-`SUSPENDED` seller
  returns without side effect, so a redelivery is a no-op. That is a valid strategy, but
  it is a *different* one, and it went unreviewed against `event-consumer.md § Idempotency`
  for as long as this file declared the service `rest-api` (single) — see TASK-MONO-372.

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
- **셀러 라이프사이클 + 실 IAM provisioning (ADR-MONO-042, Step 4 facet f)**: `Seller` 애그리거트가 실제 운영자 principal 을 갖는다. 온보딩 시 product-service 가 account-service 내부 EP(`POST /internal/tenants/{t}/accounts` 역할 `SELLER` + `identities:resolveOrCreate`)를 client_credentials JWT 로 호출해 **실 IAM seller-operator 계정 + born-unified identity**(ADR-036 재사용)를 발급한다. 상태기계: `register → PENDING_PROVISIONING`; provisioning 성공 → `ACTIVE`(account_id/identity_id 저장); 운영자 `suspend → SUSPENDED`(계정 lock)/`close → CLOSED`(계정 deactivate). **fail-soft(D3)**: IAM 미가용 시 셀러는 PENDING_PROVISIONING 으로 남고 온보딩은 절대 막히지 않음(재-provision 가능). **authz net-zero(D6)**: 런타임 seller-scope 경로(`SellerScopeContextFilter` → ADR-025 axis-2)는 불변 — 신뢰 claim 이 실 계정으로 backing 될 뿐. 계약 = [specs/contracts/http/internal/product-to-account.md](../../contracts/http/internal/product-to-account.md).
- **이벤트**: `ProductCreated`/`StockChanged` 등 봉투에 `tenant_id` 전파(M5), 페이로드에 `seller_id`. (계약 편집 = Step 2/3.)
- **degradation (D8)**: default-tenant + default-seller 시드 → 단일 스토어 단일 셀러 동작 byte-identical; default seller 는 ACTIVE 로 태어나 IAM provisioning 하지 않음(standalone 단일 스토어 anchor). legacy 셀러(ADR-042 이전)는 ACTIVE + null account/identity 로 backfill — 동작 불변. `tenant_id` claim 부재(standalone) → default tenant.
- **회귀 (M6)**: cross-tenant leak IT 필수 — 테넌트 A 상품이 B 토큰으로 안 보임.
- **M7 unbounded-query cap (TASK-BE-405)**: 모든 list endpoint(`GET /api/products`, `GET /api/admin/products`, `GET /api/admin/sellers`)는 **max page size = 100**(`MAX_PAGE_SIZE`) — `size` 가 100 을 초과하면 100 으로 clamp(`Math.min`); 정상 size 는 그대로 전달(backward-compatible). `LIMIT`-less / 과도 list 를 통한 단일 테넌트의 cross-tenant DBMS 자원 고갈 차단(M7 line 86). per-tenant API rate limit 은 gateway-edge(M2 layer-1, gateway-service/architecture.md § Per-tenant Rate Limit).
- **PROJECT.md `multi-tenant` trait**: Step 2(코드)와 함께 추가 (ADR-030 §D7 타이밍 — 슬라이스 전 추가 시 미마이그 서비스 M1 미스분류).

## Change Rule
Any architectural change to this service must be documented here first before implementation.
