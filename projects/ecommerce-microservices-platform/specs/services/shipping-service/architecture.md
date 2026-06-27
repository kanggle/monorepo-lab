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
| Event publication | Kafka via outbox: `shipping.shipping.status-changed` (ShippingStatusChanged); `ecommerce.fulfillment.requested.v1` (FulfillmentRequested, ADR-022 forward leg, via outbox `OutboxPollingScheduler`) |
| Event consumption | `OrderConfirmed` from `order.order.confirmed` (idempotent via `EventDeduplicationChecker`, creates Shipping records); `wms.outbound.shipping.confirmed.v1` (WmsShippingConfirmedConsumer, group `shipping-service-wms`, ADR-022 return leg); `wms.outbound.order.cancelled.v1` (WmsOutboundCancelledConsumer, group `shipping-service-wms`, ADR-022 return leg) |

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

- Pattern: Transactional Outbox (**v2** — shared `AbstractOutboxPublisher`, ADR-MONO-004 § 5; migrated from v1 in TASK-BE-446)
- Table: `shipping_outbox` (v2 shape — `event_id UUID` PK, `occurred_at`, `retries`/`last_error`; mirrors `master_outbox`/`promotion_outbox`). The v1 `outbox` table is retained but unused (kept so the still-EntityScanned lib `OutboxJpaEntity` validates under `ddl-auto=validate`).
- Write path: `SpringShippingEventPublisher` (implements `ShippingEventPublisher`) persists a `ShippingOutboxEntity` row directly. The two structured envelopes reuse their own `event_id` as the row PK; the forward fulfillment leg stores the opaque pre-serialized `messageJson` verbatim with a fresh UUID PK (the wms consumer dedupes on the payload `eventId`, not the Kafka header — wire-safe).
- Relay: `ShippingOutboxPublisher extends AbstractOutboxPublisher<ShippingOutboxEntity>` — `@Scheduled` poll, backoff, `eventId`/`eventType` headers, `MicrometerOutboxMetrics("shipping")` (+ preserved `shipping.outbox.pending.count` gauge). Runs unconditionally (no `@Profile`).
- Topic 매핑 (preserved verbatim — **mixed conventions**: status-changed has no `.v1`, the two fulfillment legs do):
  - `ShippingStatusChanged` → `shipping.shipping.status-changed`
  - `FulfillmentRequested` → `ecommerce.fulfillment.requested.v1`
  - `ManualShipConfirmRequested` → `ecommerce.shipping.manual-confirm-requested.v1`

## Integration Rules
- HTTP behavior must follow published contracts
- Domain events must follow published event contracts
- Consumes OrderConfirmed from order-service to create shipping records
- notification-service consumes ShippingStatusChanged events
- Shared libraries may be used only under shared-library policy

## Events
- Publishes: `ShippingStatusChanged` (`shipping.shipping.status-changed`); `FulfillmentRequested` (`ecommerce.fulfillment.requested.v1`, ADR-022 forward leg, via outbox)
- Consumes: `OrderConfirmed` (order-service, `order.order.confirmed`); `wms.outbound.shipping.confirmed.v1` (WmsShippingConfirmedConsumer, group `shipping-service-wms`); `wms.outbound.order.cancelled.v1` (WmsOutboundCancelledConsumer, group `shipping-service-wms`)

## Testing Expectations
Required emphasis:
- aggregate and domain rule tests (status transitions)
- application service tests
- repository integration tests
- event publishing and consuming tests
- idempotency tests

## Multi-Tenancy & Marketplace (ADR-MONO-030)

> 모델 SoT = [specs/features/multi-tenancy-and-marketplace.md](../../features/multi-tenancy-and-marketplace.md) (ADR-MONO-030). 본 섹션은 shipping-service 적용분만 선언한다.

shipping-service adopts the platform's `multi-tenant` trait
([`rules/traits/multi-tenant.md`](../../../../../rules/traits/multi-tenant.md) M1-M7),
inheriting the outer-axis tenant-isolation pattern proven in product-service /
order-service (TASK-BE-357), user-service (TASK-BE-367), and promotion-service
(TASK-BE-368). The `seller_id` inner axis does **not** apply — shipping aggregates
are tenant-scoped operational data, not seller-attributed catalog data.

- **M1 — row-level `tenant_id`**: `shipping` records carry `tenant_id VARCHAR(64) NOT NULL`,
  stamped at insert and immutable (`updatable=false`). V7 migration backfills all
  pre-existing rows to the default tenant `'ecommerce'`.
- **M2 — 3-layer isolation**: (1) gateway entitlement-trust gate + `X-Tenant-Id` header
  injection owned by **gateway-service** (TASK-BE-357), reused; (2) `TenantContextFilter`
  (`HIGHEST_PRECEDENCE`) binds the header into a request-scoped `TenantContext` ThreadLocal;
  (3) every repository read filters `WHERE tenant_id = currentTenant()` and every write
  stamps it.
- **M3 — 404-over-403**: cross-tenant single-resource read resolves to empty → **404**
  (existence hidden), never 403.
- **M5 — async propagation**: `ShippingStatusChanged` and `FulfillmentRequested` outbox
  envelopes carry `tenant_id`. The consumed `OrderConfirmed` envelope's `tenant_id` is
  bound to the context for shipping record creation; absent → default tenant.
- **M6 — cross-tenant-leak regression IT**: cross-tenant isolation IT proves tenant A's
  shipping records are invisible to a tenant B context.
- **net-zero / standalone (D8)**: V7 migration backfills all pre-existing rows to the
  default tenant `'ecommerce'`; an unset context resolves to that default — single-store
  behavior byte-identical. Multi-tenancy is additive; **fail-closed is prohibited**.

## ADR-MONO-022 Fulfillment Integration

shipping-service implements **both legs** of the ecommerce ↔ wms order-fulfillment
integration ([ADR-MONO-022](../../../../../docs/adr/ADR-MONO-022-ecommerce-wms-fulfillment-integration.md)):

**Forward leg (ecommerce → wms)**: on `OrderConfirmed`, after creating the Shipping
record, shipping-service publishes `ecommerce.fulfillment.requested.v1`
(`FulfillmentRequested`) via the transactional outbox (`OutboxPollingScheduler`).
This event carries the `orderId`, `orderNo`, fulfillment items, and delivery address,
triggering wms to create an outbound order.

**Return leg (wms → ecommerce)** — consumer group `shipping-service-wms`:
- `wms.outbound.shipping.confirmed.v1` → `WmsShippingConfirmedConsumer`: advances
  the Shipping record `PREPARING → SHIPPED` with `trackingNumber = shipmentNo` and
  `carrier = carrierCode`. The existing `ShippingStatusChanged` then drives the
  order-service `Order → SHIPPED`.
- `wms.outbound.order.cancelled.v1` → `WmsOutboundCancelledConsumer`: backorder/cancel
  ops alert; Shipping stays `PREPARING`-flagged. (No Shipping row typically exists yet
  at backorder time.)

SoT for the cross-service event contract:
[specs/contracts/events/wms-shipment-subscriptions.md](../../contracts/events/wms-shipment-subscriptions.md).

## Change Rule
Any architectural change to this service must be documented here first before implementation.
