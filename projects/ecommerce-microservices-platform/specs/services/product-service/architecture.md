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

## Change Rule
Any architectural change to this service must be documented here first before implementation.
