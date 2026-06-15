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

## Testing Expectations
Required emphasis:
- application service tests (with mocked ports)
- outbound adapter tests against real Elasticsearch (Testcontainers)
- event consumer integration tests
- search query accuracy tests
- index sync failure and retry scenario tests

## Change Rule
Any architectural change to this service must be documented here first before implementation.
