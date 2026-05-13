# product-service — Overview

> 1-pager: responsibilities, public surface, key invariants.

## Service identity

| Field | Value |
|---|---|
| Service name | `product-service` |
| Project | `ecommerce-microservices-platform` |
| Service Type | `rest-api` |
| Architecture Style | **DDD-style** — see [architecture.md § Architecture Style](architecture.md) |
| Stack | Java 21, Spring Boot 3.4, PostgreSQL, Kafka, `libs/java-messaging` (transactional outbox) |
| Deployable unit | `apps/product-service/` |
| Bounded Context | `Product / Inventory` |
| Persistent stores | PostgreSQL (product + variant + category + stock) + Kafka outbox table |
| Event publication | `product.product.created`, `product.product.updated`, `product.product.deleted`, `product.stock.changed` |

## Responsibilities

- Own product catalog: registration, modification, deletion of product master data.
- Manage product variants (size / color / option), categories (hierarchy), pricing, discount configuration.
- Track and adjust inventory stock levels; enforce non-negative stock domain invariant.
- Manage product status (`ON_SALE` / `SOLD_OUT` / `HIDDEN`).
- Publish product domain events to Kafka (consumed by `search-service` for index sync).

## Public surface

| Channel | Endpoint / Topic | Auth | Purpose |
|---|---|---|---|
| REST | `GET /api/products/**` | none (public) | catalog browsing (anonymous) |
| REST | `POST /api/products` | JWT + ROLE_ADMIN | product create |
| REST | `PUT /api/products/{id}` | JWT + ROLE_ADMIN | product update |
| REST | `DELETE /api/products/{id}` | JWT + ROLE_ADMIN | product delete (soft) |
| REST | `POST /api/admin/products/{id}/stock` | JWT + ROLE_ADMIN | stock adjustment |
| Kafka publish | `product.product.{created,updated,deleted}`, `product.stock.changed` | — | search-service index sync |

자세한 spec 은 [`../../contracts/http/product-api.md`](../../contracts/http/product-api.md) + [`../../contracts/events/product-events.md`](../../contracts/events/product-events.md) 참조.

## Key invariants

1. **Stock cannot go negative** — domain-enforced; 음수 stock 시도 → `IllegalStockTransition`.
2. **Product must have ≥ 1 variant** — variant 없는 product 생성 / 마지막 variant 삭제 금지.
3. **Search-service coupling = events only** — `search-service` 가 product API 직접 호출 금지 (architecture.md § Forbidden Dependencies — event-driven decoupling).
4. **Domain ↛ framework** — domain layer Spring / JPA / Kafka SDK 직접 import 금지.
5. **Repositories carry no business decision** — 분기 로직 repository 금지.

## Owned Data

- product master data + variants / options + category hierarchy + pricing / discount config + inventory stock records.

## Published Interfaces

- [`../../contracts/http/product-api.md`](../../contracts/http/product-api.md) (HTTP)
- [`../../contracts/events/product-events.md`](../../contracts/events/product-events.md) — `ProductCreated`, `ProductUpdated`, `ProductDeleted`, `StockChanged`

## Dependent Systems

- PostgreSQL — product / variant / stock persistence
- Kafka — event publication
- (no inbound dependency — product-service 가 다른 service 의 event 소비 안 함 v1)

## Out of scope (v1)

- Order processing / payment — `order-service` / `payment-service`.
- Search indexing — `search-service` 가 events 로 자기 index 유지 (product-service 는 발행만).
- User authentication — `auth-service` (deprecated) → GAP.
- Review / rating — `review-service`.
- Stock reservation / hold — v2 (order placement 시 stock 차감 동기, 아직 reservation pattern 미적용).
