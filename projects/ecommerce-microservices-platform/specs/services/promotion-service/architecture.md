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

- Pattern: Transactional Outbox (**v2** — shared `AbstractOutboxPublisher`, ADR-MONO-004 § 5; migrated from v1 in TASK-BE-444)
- Table: `promotion_outbox` (v2 shape — `event_id UUID` PK, `occurred_at`, `retries`/`last_error`; mirrors the wms `master_outbox` reference). The v1 `outbox` table is retained but unused (kept present-but-empty so the still-EntityScanned lib `OutboxJpaEntity` validates under `ddl-auto=validate`).
- Write path: `SpringPromotionEventPublisher` (implements `PromotionEventPublisher`) persists a `PromotionOutboxEntity` row directly; the row `event_id` reuses the event envelope's own `event_id`, and the payload is the byte-identical `objectMapper.writeValueAsString(event)` (wire-preserving).
- Relay: `PromotionOutboxPublisher extends AbstractOutboxPublisher<PromotionOutboxEntity>` — `@Scheduled` poll, exponential backoff (1s→…→30s), `eventId`/`eventType` Kafka headers, `MicrometerOutboxMetrics("promotion")` (+ preserved `promotion.outbox.pending.count` gauge). Runs unconditionally (no `@Profile`, matching the v1 scheduler).
- Topic 매핑 (preserved verbatim — note **no** `.v1` suffix):
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

## Multi-Tenancy & Marketplace (ADR-MONO-030)

> (Originally introduced in TASK-BE-368.) 모델 SoT = [specs/features/multi-tenancy-and-marketplace.md](../../features/multi-tenancy-and-marketplace.md)

promotion-service adopts the platform's `multi-tenant` trait
([`rules/traits/multi-tenant.md`](../../../../../rules/traits/multi-tenant.md) M1-M7),
inheriting the outer-axis tenant-isolation pattern proven in product-service /
order-service (TASK-BE-357) and user-service (TASK-BE-367). The `seller_id` inner
axis does **not** apply — promotion/coupon are tenant-scoped operator entities, not
seller-attributed catalog data.

- **M1 — row-level `tenant_id`**: `promotions` and `coupons` carry
  `tenant_id VARCHAR(64) NOT NULL`, stamped once at insert and immutable
  (`updatable=false`). `outbox` / `processed_events` stay column-free — tenant
  rides on the event envelope (M5). Lives in the persistence + event layers only;
  the clean `Promotion` / `Coupon` domain aggregates are unchanged.
- **M2 — 3-layer isolation**: (1) the gateway entitlement-trust gate + `X-Tenant-Id`
  header are owned by **gateway-service** (TASK-BE-357), reused; (2)
  `TenantContextFilter` (`HIGHEST_PRECEDENCE`) binds the header into a request-scoped
  framework-free `TenantContext` ThreadLocal; (3) every operator/consumer repository
  read filters `WHERE tenant_id = currentTenant()` (incl. the `findByIdForUpdate`
  pessimistic-lock paths) and every write stamps it.
- **M3 — 404-over-403**: a cross-tenant single-resource read or write
  (`GET /api/promotions/{id}`, update/delete/issue, coupon apply for another
  tenant's row) resolves to empty → **404** (existence hidden), never 403.
- **M5 — async propagation (outbox)**: `CouponUsed` / `CouponExpired` outbox
  envelopes carry `tenant_id` (see
  [promotion-events.md](../../contracts/events/promotion-events.md)). The consumed
  `OrderCancelled` envelope's `tenant_id` (optional until order-service migrates) is
  bound to the context for the coupon-restore; absent → default tenant.
- **System/batch paths stay tenant-agnostic** (mirrors order-service
  `findByIdAcrossTenants`): the coupon-expiration sweep (`findExpiredIssuedCoupons`)
  and the `OrderCancelled` recovery (`findByOrderIdAndStatus`, `orderId` globally
  unique) operate across tenants by unique id, preserve the mutated row's
  `tenant_id`, and stamp the published event with that row's tenant.
- **M6 — cross-tenant-leak regression IT**: `MultiTenantIsolationIntegrationTest`
  (Testcontainers PostgreSQL) proves tenant A's promotions/coupons are invisible to
  a tenant B context (list exclusion + 404 single-read) and that a missing
  `X-Tenant-Id` resolves to the default tenant.
- **net-zero / standalone (D8)**: the V6 migration backfills all pre-existing rows
  to the default tenant `'ecommerce'`; an unset context resolves to that default —
  single-store behavior byte-identical. Multi-tenancy is additive; **fail-closed is
  prohibited**.

> The operator-plane reads (`/api/promotions`) are now tenant-scoped — the backend
> prerequisite (ADR-MONO-031 Phase 3a) that gates the platform-console
> promotions-area absorption (Phase 3b, full CRUD).

## Testing Expectations
Required emphasis:
- aggregate and domain rule tests
- application service tests
- repository integration tests
- event publishing tests
- concurrency tests (coupon quantity limit)

## Change Rule
Any architectural change to this service must be documented here first before implementation.
