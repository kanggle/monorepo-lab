# shipping-service — Architecture

This document declares the internal architecture of `shipping-service`.
All implementation tasks targeting this service must follow this declaration
and `platform/architecture-decision-rule.md`.

---

## Identity

| Field | Value |
|---|---|
| Service name | `shipping-service` |
| Project | `ecommerce-microservices-platform` |
| Service Type | `rest-api + event-consumer` (hybrid — see Service Type Composition below) |
| Architecture Style | **DDD-style Architecture** (4-layer + domain/port) |
| Domain | ecommerce |
| Primary language / stack | Java 21, Spring Boot |
| Bounded Context | Shipping (shipping aggregates / status transitions / event-driven lifecycle) |
| Deployable unit | `apps/shipping-service/` |
| Data store | PostgreSQL (owned) |
| Event publication | Kafka via outbox (shipping.* lifecycle events) |
| Event consumption | `OrderConfirmed` from `order.order.confirmed` (idempotent via `EventDeduplicationChecker`, creates Shipping records) |

### Service Type Composition

`shipping-service` is a hybrid service per
`platform/service-types/INDEX.md` § Hybrid Cases (REST service that also
consumes events). Primary type is `rest-api`; the secondary `event-consumer`
capability subscribes to `order.order.confirmed` to bootstrap Shipping
aggregates upon order confirmation. The primary type determines the spec read
order — applied rules:
[platform/service-types/rest-api.md](../../../../../platform/service-types/rest-api.md).
The secondary capability is documented under "Integration Rules" below with
topic / consumer-group / idempotency details.

---

## Why This Architecture
Shipping management involves meaningful domain concepts: shipping aggregates, status transitions with strict ordering rules, and event-driven lifecycle.

Domain invariants (e.g. status cannot go backwards, only valid transitions are allowed) require aggregate-level enforcement.

DDD-style keeps these rules in the domain layer and prevents them from leaking into infrastructure or presentation.

## Internal Structure Rule
This service uses a domain-driven internal structure.

Recommended internal areas:
- interface (presentation)
- application
- domain
- infrastructure

Key domain concepts:
- Aggregates: Shipping
- Entities: none (Shipping is the root)
- Value Objects: ShippingStatus (PREPARING, SHIPPED, IN_TRANSIT, DELIVERED), TrackingInfo
- Domain Events: ShippingStatusChanged
- Domain Services: ShippingStatusTransitionValidator
- Repositories: ShippingRepository

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
- domain layer owns shipping status rules and transition invariants
- infrastructure layer handles persistence, event publishing, and external adapters

## Domain Scope
- Shipping (order reference, status, tracking number, carrier, status history, timestamps)
- Status transition rules (PREPARING -> SHIPPED -> IN_TRANSIT -> DELIVERED, no backward transitions)

## Domain Constraints
- shipping-service must NOT own order or payment data
- Status transitions must follow the defined order (no backward or skip transitions)
- One shipping record per order
- Duplicate OrderConfirmed events must not create duplicate shipping records (idempotency)

## Outbox

- Pattern: Transactional Outbox
- Table: `outbox` (libs/java-messaging 표준 schema)
- Polling scheduler: `OutboxPollingScheduler` (libs `com.example.messaging.outbox.OutboxPollingScheduler` base 의 concrete subclass)
- Topic 매핑:
  - `ShippingStatusChanged` → `shipping.shipping.status-changed`

## Integration Rules
- HTTP behavior must follow published contracts
- Domain events must follow published event contracts
- Consumes OrderConfirmed from order-service to create shipping records
- notification-service consumes ShippingStatusChanged events
- Shared libraries may be used only under shared-library policy

## Events
- Publishes: `ShippingStatusChanged`
- Consumes: `OrderConfirmed` (order-service)

## Testing Expectations
Required emphasis:
- aggregate and domain rule tests (status transitions)
- application service tests
- repository integration tests
- event publishing and consuming tests
- idempotency tests

## Change Rule
Any architectural change to this service must be documented here first before implementation.
