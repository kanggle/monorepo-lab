# review-service — Architecture

This document declares the internal architecture of `review-service`.
All implementation tasks targeting this service must follow this declaration
and `platform/architecture-decision-rule.md`.

---

## Identity

| Field | Value |
|---|---|
| Service name | `review-service` |
| Project | `ecommerce-microservices-platform` |
| Service Type | `rest-api` (single — see Service Type Composition below) |
| Architecture Style | **DDD-style Architecture** (4-layer + domain/port) |
| Domain | ecommerce |
| Primary language / stack | Java 21, Spring Boot |
| Bounded Context | Product review (aggregates / rating calculations / purchase verification) |
| Deployable unit | `apps/review-service/` |
| Data store | PostgreSQL (owned) |
| Event publication | Kafka via outbox (review.* lifecycle events) |
| Event consumption | none (single-type rest-api) |

### Service Type Composition

`review-service` is a single-type `rest-api` service per
`platform/service-types/INDEX.md`. Product review 도메인 — review aggregates,
rating calculations, purchase verification constraints. 적용되는 규칙:
[platform/service-types/rest-api.md](../../../../../platform/service-types/rest-api.md).

---

## Why This Architecture
Review and rating management involves meaningful domain concepts: review aggregates, rating calculations, and purchase verification constraints.

Domain invariants (e.g. one review per user per product, rating must be 1-5, only purchasers can write reviews) require aggregate-level enforcement.

DDD-style keeps these rules in the domain layer and prevents them from leaking into infrastructure or presentation.

## Internal Structure Rule
This service uses a domain-driven internal structure.

Recommended internal areas:
- interface (presentation)
- application
- domain
- infrastructure

Key domain concepts:
- Aggregates: Review
- Entities: none (Review is the root)
- Value Objects: Rating (1-5), ReviewStatus (ACTIVE, DELETED)
- Domain Events: ReviewCreated, ReviewUpdated, ReviewDeleted
- Domain Services: AverageRatingCalculator
- Repositories: ReviewRepository

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
- domain layer owns review rules and rating invariants
- infrastructure layer handles persistence, event publishing, and external adapters

## Domain Scope
- Review (user reference, product reference, rating, title, content, status, timestamps)
- Average rating per product (materialized view or cache)

## Domain Constraints
- review-service must NOT own product or order data
- One review per user per product (unique constraint)
- Rating must be between 1 and 5
- Only users who purchased the product can write reviews (verified via order-service)
- Deleted reviews are soft-deleted (status change)

## Outbox

- Pattern: Transactional Outbox
- Table: `outbox` (libs/java-messaging 표준 schema)
- Polling scheduler: `ReviewOutboxPollingScheduler` (libs `com.example.messaging.outbox.OutboxPollingScheduler` base 의 concrete subclass)
- Topic 매핑:
  - `ReviewCreated` → `review.review.created`
  - `ReviewUpdated` → `review.review.updated`
  - `ReviewDeleted` → `review.review.deleted`

## Integration Rules
- HTTP behavior must follow published contracts
- Domain events must follow published event contracts
- Purchase verification calls order-service via synchronous HTTP
- search-service consumes review events for rating index updates
- Shared libraries may be used only under shared-library policy

## Events
- Publishes: `ReviewCreated`, `ReviewUpdated`, `ReviewDeleted`
- Consumes: none

## Testing Expectations
Required emphasis:
- aggregate and domain rule tests
- application service tests
- repository integration tests
- event publishing tests
- purchase verification integration tests

## Multi-Tenancy

Adopted as of TASK-BE-403 (ADR-MONO-030 Step 4 facet c). All M1-M7 rules from
`rules/traits/multi-tenant.md` apply; see the table below for applicability:

| Rule | Description | Status |
|---|---|---|
| M1 | Row-level `tenant_id VARCHAR NOT NULL` on `reviews` table | Applied — V5 migration |
| M2 | 3-layer isolation: gateway entitlement-trust → `X-Tenant-Id` context → `WHERE tenant_id` | Applied — `TenantContextFilter` + tenant-scoped JPA queries |
| M3 | 404-over-403 on cross-tenant access | Applied — `findActiveById` / `existsByUserIdAndProductId` scope by `TenantContext.currentTenant()` |
| M4 | Enumeration prevention | Applied — all list/summary queries scoped by `tenant_id` |
| M5 | Async event envelope carries `tenant_id` | Applied — `ReviewEvent` record + `ReviewEventMessage` DTO include `tenant_id` |
| M6 | Cross-tenant leak regression IT | Covered by `ReviewIntegrationTest` (Testcontainers, CI-only) |
| M7 | Per-tenant quota | N/A — out of scope (ADR-MONO-030 §3.4 Step 4 backlog) |

**Default tenant (D8 net-zero):** `TenantContext.currentTenant()` returns `'ecommerce'` when no
`X-Tenant-Id` header is present (standalone deployment without platform IAM). All pre-existing rows
were backfilled to `tenant_id = 'ecommerce'` via V5 migration. The service behaves
byte-identically to its pre-multi-tenant state in single-store mode.

## Change Rule
Any architectural change to this service must be documented here first before implementation.
