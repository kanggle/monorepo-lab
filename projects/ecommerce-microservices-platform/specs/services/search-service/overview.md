# search-service — Overview

> 1-pager: responsibilities, public surface, key invariants.

## Service identity

| Field | Value |
|---|---|
| Service name | `search-service` |
| Project | `ecommerce-microservices-platform` |
| Service Type | `rest-api` |
| Architecture Style | **Hexagonal** — domain + application + adapter, see [architecture.md § Architecture Style](architecture.md) |
| Stack | Java 21, Spring Boot 3.4, **Elasticsearch** (no PostgreSQL — search index only), Kafka |
| Deployable unit | `apps/search-service/` |
| Bounded Context | `Search / Product Index` |
| Persistent stores | Elasticsearch (denormalized search index — derived, not authoritative) + search configuration |
| Event publication | none |

## Responsibilities

- Maintain a **denormalized search index** in Elasticsearch by consuming `product-service` + `review-service` events.
- Provide product search query API — keyword, filter, sort, pagination, faceted filter.
- Manage index lifecycle — create / update / delete documents on event consumption.
- Own search ranking and relevance configuration (per `architecture.md § Architecture Style Rationale`).
- Consume review events to keep product rating field updated in search index.

## Public surface

| Channel | Endpoint / Topic | Auth | Purpose |
|---|---|---|---|
| REST | `GET /api/search?q=&filters=&sort=&page=` | none (public) | product search query |
| REST | `GET /api/search/autocomplete?q=` | none (public) | autocomplete suggestions |
| REST | `GET /api/search/facets` | none (public) | facet aggregations (category / price range) |
| Kafka consume | `product.product.{created,updated,deleted}`, `product.stock.changed` | — | product index sync |
| Kafka consume | `review.review.{created,updated,deleted}` | — | rating index sync |

자세한 spec 은 [`../../contracts/http/search-api.md`](../../contracts/http/search-api.md) 참조.

## Key invariants

1. **Domain ↛ Elasticsearch SDK** — domain layer 는 Elasticsearch RestHighLevelClient / Query DSL 직접 import 금지.
2. **Application ↛ Elasticsearch Query DSL** — application service 는 search abstraction (`SearchPort`) 만 호출; ES-specific 표현 부재 (architecture.md § Forbidden Dependencies).
3. **Adapter ↛ business policy** — ranking / filter 결정은 application; adapter 는 ES 호출 + 변환만.
4. **`product-service` is SoT** — search index 는 derived view 만; index ↔ DB drift 발견 시 product-service 가 win, `batch-worker` 가 consistency check 수행.
5. **Index schema versioning** — schema 변경 시 versioned migration strategy (`product-v1` → `product-v2` rolling reindex), 무중단 재인덱싱.

## Owned Data

- search index data (derived from product-service / review-service events; PostgreSQL 미사용).
- search configuration and ranking rules (analyzers / synonyms / weights).

## Published Interfaces

- [`../../contracts/http/search-api.md`](../../contracts/http/search-api.md) (HTTP, query only)

## Dependent Systems

- Elasticsearch — primary store (search index)
- Kafka — event consumption
- `product-service` (events SoT)
- `review-service` (events for rating sync)
- `batch-worker` (index consistency check, HTTP read-only)

## Out of scope (v1)

- Product data ownership — `product-service`.
- Order / payment processing — `order-service` / `payment-service`.
- User authentication — `auth-service` (deprecated) → IAM.
- Personalized search ranking (사용자별 맞춤) — v2 (recommendation 도입 시).
- Search analytics (query log analysis) — v2.
