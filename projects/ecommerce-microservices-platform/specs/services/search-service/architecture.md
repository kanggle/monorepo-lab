# search-service — Architecture

This document declares the internal architecture of `search-service`.
All implementation tasks targeting this service must follow this declaration
and `platform/architecture-decision-rule.md`.

---

## Identity

| Field | Value |
|---|---|
| Service name | `search-service` |
| Project | `ecommerce-microservices-platform` |
| Service Type | `rest-api + event-consumer` (hybrid — see Service Type Composition below) |
| Architecture Style | **Hexagonal Architecture** (Ports & Adapters) |
| Domain | ecommerce |
| Primary language / stack | Java 21, Spring Boot |
| Bounded Context | Search / discovery (Elasticsearch index + query) |
| Deployable unit | `apps/search-service/` |
| Data store | Elasticsearch (primary index) + PostgreSQL (dedupe state) |
| Event publication | none |
| Event consumption | `ProductCreated` / `ProductUpdated` / `ProductDeleted` / `ProductImagesUpdated` / `StockChanged` from `product.product.*` topics (index sync via index-sync skill) |

### Service Type Composition

`search-service` is a hybrid service per
`platform/service-types/INDEX.md` § Hybrid Cases (REST service that also
consumes events). Primary type is `rest-api` for the query API surface; the
secondary `event-consumer` capability subscribes to product / catalog source
topics to keep the Elasticsearch index in sync (index-sync skill). Hexagonal
으로 Elasticsearch adapter 격리. The primary type determines the spec read
order — applied rules:
[platform/service-types/rest-api.md](../../../../../platform/service-types/rest-api.md).
The secondary capability is documented under "Events" below with topic /
consumer-group details.

---

## Why This Architecture
This service depends critically on Elasticsearch as an external system.

Business logic (relevance rules, filter mapping, index document structure) must remain independent from the specific Elasticsearch client version or SDK.

Hexagonal architecture isolates the search engine behind outbound ports, making the service testable and replaceable.

## Internal Structure Rule
This service uses a ports-and-adapters structure.

Recommended internal areas:
- inbound adapters (HTTP query handlers, event consumers)
- application
- domain
- outbound ports
- outbound adapters (Elasticsearch adapter, event consumer adapter)

Business logic must remain independent from Elasticsearch SDK details.

## Allowed Dependencies
- inbound adapters -> application
- application -> domain
- application -> ports
- outbound adapters -> ports
- outbound adapters -> Elasticsearch SDK or messaging infrastructure

## Forbidden Dependencies
- domain must not depend on Elasticsearch SDK
- application must not reference Elasticsearch query DSL directly
- adapters must not own search ranking or filter business rules
- HTTP handlers must not bypass application services

## Boundary Rules
- inbound HTTP adapter translates search requests into application queries
- inbound event consumer adapter translates product events into index commands
- application layer coordinates search and indexing use-cases through ports
- domain contains search relevance rules, filter logic, and index document structure
- outbound adapter implements Elasticsearch operations behind ports

## Integration Rules
- product-service events must be consumed through published event contracts
- Search query HTTP API must follow published HTTP contracts
- Elasticsearch dependency must be isolated behind outbound ports
- Index schema changes must be managed through a versioned migration strategy

## Events

search-service is a **pure consumer** — it publishes no events.

### Consumed Topics

All consumers use consumer group `search-service` (verified from `@KafkaListener` annotations in `apps/search-service`):

| Event | Topic | Consumer class | Purpose |
|---|---|---|---|
| `ProductCreated` | `product.product.created` | `ProductCreatedConsumer` | Index new product document in Elasticsearch |
| `ProductUpdated` | `product.product.updated` | `ProductUpdatedConsumer` | Update existing product document |
| `ProductDeleted` | `product.product.deleted` | `ProductDeletedConsumer` | Remove product document from index |
| `StockChanged` | `product.product.stock-changed` | `StockChangedConsumer` | Update stock availability in index |
| `ProductImagesUpdated` | `product.product.images-updated` | `ProductImagesUpdatedConsumer` | Update image URLs in indexed document |

Contract: [`specs/contracts/events/product-events.md`](../../contracts/events/product-events.md).

## Multi-Tenancy

**Isolation shape**: ES index-field + mandatory query filter (TASK-BE-404, ADR-MONO-030 Step 4 facet c).

This differs from the relational `tenant_id`-column services (product/order/payment/etc.) in
that there is **no Flyway migration and no SQL column**. Isolation is achieved entirely at the
Elasticsearch document and query layer:

| Layer | Implementation |
|---|---|
| Document field | `tenantId` keyword field on every `SearchDocument`; populated from the inbound product event's `tenant_id` envelope field (M5 propagation — product-service stamps it since TASK-BE-357). |
| Query filter | Every read path in `ElasticsearchQueryAdapter.buildQuery()` adds a mandatory `term { tenantId: TenantContext.currentTenant() }` filter clause inside `bool.filter`. A document in tenant A is never returned under a tenant B context. |
| Context injection | `TenantContextFilter` (servlet filter, `Ordered.HIGHEST_PRECEDENCE`) reads the gateway-injected `X-Tenant-Id` header into `TenantContext` (ThreadLocal) for the duration of each request, then clears it in `finally`. |
| Default / D8 net-zero | `X-Tenant-Id` absent → `TenantContext.currentTenant()` returns `"ecommerce"` → query is scoped to the default tenant → single-store behaviour preserved. |

### Index mapping

`tenantId` is a `keyword` field declared in `IndexInitializer.INDEX_SPEC_JSON`. Adding it is
**additive** (no mapping conflict). The `IndexInitializer.hasCurrentSpec()` check includes a
`tenantId` key presence gate: if an existing index lacks the field the initializer will
delete-and-recreate it (non-destructive from a code standpoint; data is repopulated via
`POST /api/search/admin/reindex`).

### Pre-existing documents (demo / migration)

Documents indexed before TASK-BE-404 lack `tenantId`. These are coalesced to the default
tenant `"ecommerce"` at read time in `ElasticsearchFieldMapper.toSearchDocument()`. They
will be restamped on the next product event re-sync. No destructive bulk reindex is required
for demo / development purposes.

## Testing Expectations
Required emphasis:
- application service tests (with mocked ports)
- outbound adapter tests against real Elasticsearch (Testcontainers)
- event consumer integration tests
- search query accuracy tests
- index sync failure and retry scenario tests
- `TenantContext` + `TenantContextFilter` unit tests
- consumer tenant_id propagation unit tests (no live ES)
- `ElasticsearchQueryAdapter` query-construction unit tests asserting mandatory `tenant_id` filter

## Change Rule
Any architectural change to this service must be documented here first before implementation.
