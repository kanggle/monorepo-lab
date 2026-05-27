# promotion-service — Architecture

This document declares the internal architecture of `promotion-service`.
All implementation tasks targeting this service must follow this declaration
and `platform/architecture-decision-rule.md`.

---

## Identity

| Field | Value |
|---|---|
| Service name | `promotion-service` |
| Project | `ecommerce-microservices-platform` |
| Service Type | `rest-api + event-consumer` (hybrid — see Service Type Composition below) |
| Architecture Style | **DDD-style Architecture** (4-layer + domain/port) |
| Domain | ecommerce |
| Primary language / stack | Java 21, Spring Boot |
| Bounded Context | Promotion / coupon (aggregates / lifecycle / discount rules / usage constraints) |
| Deployable unit | `apps/promotion-service/` |
| Data store | PostgreSQL (owned) |
| Event publication | Kafka via outbox (promotion.* / coupon.* lifecycle events) |
| Event consumption | `OrderCancelled` from `order.order.cancelled` (restores USED coupons back to ISSUED state) |

### Service Type Composition

`promotion-service` is a hybrid service per
`platform/service-types/INDEX.md` § Hybrid Cases (REST service that also
consumes events). Primary type is `rest-api`; the secondary `event-consumer`
capability subscribes to `order.order.cancelled` to roll back coupon usage
state when an order is cancelled. The primary type determines the spec read
order — applied rules:
[platform/service-types/rest-api.md](../../../../../platform/service-types/rest-api.md).
The secondary capability is documented under "Events" below with topic /
consumer-group details.

---

## Why This Architecture
Promotion and coupon management involves meaningful domain concepts: promotion aggregates, coupon lifecycle, discount rules, and usage constraints.

Domain invariants (e.g. coupon cannot be used twice, coupon quantity cannot go negative, expired coupons cannot be applied) require aggregate-level enforcement.

DDD-style keeps these rules in the domain layer and prevents them from leaking into infrastructure or presentation.

## Internal Structure Rule
This service uses a domain-driven internal structure.

Recommended internal areas:
- interface (presentation)
- application
- domain
- infrastructure

Key domain concepts:
- Aggregates: Promotion, Coupon
- Entities: CouponIssuance
- Value Objects: DiscountType (FIXED, PERCENTAGE), PromotionPeriod, CouponStatus (ISSUED, USED, EXPIRED)
- Domain Events: CouponUsed, CouponExpired
- Domain Services: DiscountCalculator
- Repositories: PromotionRepository, CouponRepository

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
- domain layer owns promotion and coupon rules and invariants
- infrastructure layer handles persistence, event publishing, and external adapters

## Domain Scope
- Promotion (name, description, discount type, discount value, period, max issuance count)
- Coupon (code, promotion reference, user reference, status, issued/used/expired timestamps)
- Discount calculation (fixed amount, percentage with max cap)

## Domain Constraints
- promotion-service must NOT own order or payment logic
- A coupon can only be used once per issuance
- Coupon quantity must not go negative
- Expired coupons cannot be applied
- Discount amount must not exceed order total

## Outbox

- Pattern: Transactional Outbox
- Table: `outbox` (libs/java-messaging 표준 schema)
- Polling scheduler: `OutboxPollingScheduler` (libs `com.example.messaging.outbox.OutboxPollingScheduler` base 의 concrete subclass)
- Topic 매핑:
  - `CouponUsed` → `promotion.coupon.used`
  - `CouponExpired` → `promotion.coupon.expired`

## Integration Rules
- HTTP behavior must follow published contracts
- Domain events must follow published event contracts
- order-service communicates coupon application via synchronous HTTP call to promotion-service
- Shared libraries may be used only under shared-library policy

## Events
- Publishes: `CouponUsed`, `CouponExpired`
- Consumes: `OrderCancelled` (from order-service, restores USED coupons to ISSUED)

## Testing Expectations
Required emphasis:
- aggregate and domain rule tests
- application service tests
- repository integration tests
- event publishing tests
- concurrency tests (coupon quantity limit)

## Change Rule
Any architectural change to this service must be documented here first before implementation.
