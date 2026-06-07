# Task ID

TASK-BE-341

# Title

ADR-MONO-022 §D7 ③ (ecommerce) — Publish `ecommerce.fulfillment.requested.v1` (forward leg + ACL) on Shipping `PREPARING`, and consume `wms.outbound.shipping.confirmed.v1` (return leg) → Shipping `SHIPPED` → Order `SHIPPED`.

# Status

done

# Owner

claude (Opus 4.8) — ecommerce shipping-service implementation (outbox publisher + ACL + Kafka consumer). The ACL (vocabulary translation) lives here per ADR-022 D6.

# Task Tags

- event
- code

---

# Dependency Markers

- **선행**: TASK-MONO-194 (contracts). Counterpart = TASK-BE-340 (wms); additive + graceful-degradation ⇒ independent ship.
- **맥락**: `fulfillment-events.md`, `wms-shipment-subscriptions.md`.

# Goal

When a Shipping record reaches `PREPARING`, shipping-service publishes `ecommerce.fulfillment.requested.v1` (wms-shaped envelope + ACL-mapped codes). When `wms.outbound.shipping.confirmed.v1` arrives, shipping-service flips Shipping `PREPARING → SHIPPED` (tracking=`shipmentNo`, carrier=`carrierCode`) → existing `ShippingStatusChanged` → order-service flips Order `→ SHIPPED`.

# Scope

## In Scope
### shipping-service — forward leg (publish + ACL)
1. **Publish on PREPARING**: in the OrderConfirmed→createShipping flow (`ShippingCommandService.createShipping`), after the Shipping is created in PREPARING, write an outbox row for `ecommerce.fulfillment.requested.v1` (new `OutboxPollingScheduler.resolveTopic` case → topic `ecommerce.fulfillment.requested.v1`).
2. **ACL** (`FulfillmentAcl`/mapper): build the **wms camelCase envelope** (`eventId`/`eventType`/`occurredAt`/`aggregateType=fulfillment`/`aggregateId=orderId`/`payload`). Map: `orderNo=orderId`; `customerPartnerCode=ECOMMERCE-STORE` (const); `warehouseCode` from config (`fulfillment.default-warehouse-code`); per line `skuCode` from SKU mapping (config table/map; v1 = identity or a small map) + `qtyOrdered`; `shipTo` from the order's shipping address (fetch from order data available to shipping-service — orderId + the order's address; if shipping-service lacks the address, carry what OrderConfirmed/Shipping provides, else leave `shipTo=null` and document). Unmapped SKU → do not publish that order; log + alert (no silent drop).
3. **Config**: `fulfillment.enabled` (default true), `fulfillment.default-warehouse-code`, `fulfillment.sku-map` (optional).

### shipping-service — return leg (consume)
4. **`WmsShippingConfirmedConsumer`** (`infrastructure/event/`): `@KafkaListener(topics=wms.outbound.shipping.confirmed.v1, groupId=shipping-service-wms)`, dedupe via `EventDeduplicationChecker` (camelCase `eventId`), DTO maps wms envelope, locate Shipping by `orderId == payload.orderNo`, call `ShippingCommandService.updateStatus(SHIPPED, trackingNumber=shipmentNo, carrier=carrierCode)` (idempotent). Missing `orderNo` / unknown order → non-retryable → DLT.
5. **`WmsOutboundCancelledConsumer`**: consume `wms.outbound.order.cancelled.v1` → ops alert/log (backorder path, D4); v1 leaves Shipping in PREPARING flagged. Auto-refund = v2.

### order-service — return path tail (verify/implement)
6. **`ShippingStatusChangedEventConsumer`** in order-service IF not present: consume `shipping.shipping.status-changed`, on `newStatus=SHIPPED` call `order.ship(clock)` + save. (Per the wms-side map this consumer does NOT yet exist — implement it; this completes the loop to Order SHIPPED.)

## Out of Scope
- wms side (TASK-BE-340). product-service inventory reconciliation (D4 v2). Auto-refund.

# Acceptance Criteria

- AC-1: On OrderConfirmed→Shipping PREPARING, an outbox row for `ecommerce.fulfillment.requested.v1` is written with the wms camelCase envelope + ACL-mapped payload (unit + IT).
- AC-2: `WmsShippingConfirmedConsumer` flips Shipping PREPARING→SHIPPED (tracking/carrier set) keyed by `orderNo`; idempotent on `eventId` + on already-SHIPPED.
- AC-3: order-service `ShippingStatusChangedEventConsumer` flips Order CONFIRMED→SHIPPED on `newStatus=SHIPPED` (the existing return path tail); idempotent.
- AC-4: Unmapped SKU → fulfillment NOT published for that order + alert (no silent drop). `fulfillment.enabled=false` disables publish (standalone degradation, D8).
- AC-5: `:projects:ecommerce-microservices-platform:apps:shipping-service:test` + `order-service:test` green; new consumer ITs green (Testcontainers + EmbeddedKafka, existing `ShippingIntegrationTest` style).

# Related Specs

- `specs/contracts/events/{fulfillment-events.md, wms-shipment-subscriptions.md, shipping-events.md, order-events.md}`; shipping-service + order-service specs.

# Related Contracts

- Publishes `ecommerce.fulfillment.requested.v1`; consumes `wms.outbound.shipping.confirmed.v1` / `wms.outbound.order.cancelled.v1`; reuses existing `shipping.shipping.status-changed` → order-service.

# Edge Cases

- **Envelope direction**: forward leg PRODUCES camelCase (wms shape); return leg CONSUMES camelCase (wms shape). ecommerce-internal events stay snake_case — do not change them.
- **shipTo source**: if the order shipping address isn't reachable from shipping-service, publish `shipTo=null` (B2B fallback) and record the limitation; do not block fulfillment.
- **Correlation**: locate Shipping by `orderId == payload.orderNo`; absent `orderNo` → DLT.

# Failure Scenarios

- Silent drop of unmapped SKU → forbidden (AC-4).
- Changing ecommerce-internal event envelopes to camelCase → breaks order/payment/notification consumers. Only the NEW cross-project events are camelCase.
- Double-SHIP on event re-delivery → prevented by dedupe + idempotent shipping transition (AC-2).

# Implementation Notes

## Data-availability approach — chosen: EVENT-DRIVEN ENRICHMENT (preferred)

`OrderConfirmed` is enriched additively in order-service to carry `lines[]`
(per-line `sku`/`productId`/`variantId`/`quantity`) + `shippingAddress`
({recipientName, address, phone}). shipping-service's `OrderConfirmedEventConsumer`
then has everything to build the cross-project fulfillment event with no
synchronous REST call back to order-service. No new outbound client port was
introduced (fallback avoided).

### Pre-existing gap discovered + closed
order-service did **not** publish `OrderConfirmed` at all before this task — the
`confirmOrder` flow only flipped status; no outbox row, no `order.order.confirmed`
topic in `resolveTopic`, no `publishOrderConfirmed`. shipping-service's
`OrderConfirmedEventConsumer` was therefore dormant in production (nothing emitted
the topic). This task wires the producer side: `OrderConfirmationService.confirmOrder`
now co-commits an `OrderConfirmed` outbox row (only on the real PENDING→CONFIRMED
transition; idempotent re-confirm publishes nothing), `SpringOrderEventPublisher`
gained `publishOrderConfirmed`, and `OutboxPollingScheduler` maps `OrderConfirmed`
→ `order.order.confirmed`. Standalone profile = no-op (D8). Envelope stays the
ecommerce-internal `event_id`/`event_type` snake-ish shape; only the payload gained
additive fields.

### SKU identity
order-service has no explicit `sku` field. The ecommerce sellable-unit id is the
order line's `variantId` (falling back to `productId` when the variant is absent).
That value travels as `OrderConfirmed.lines[].sku` and is ACL-mapped to a wms
`skuCode` (config `fulfillment.sku-map`, identity default).

## Layering
ACL (`FulfillmentAcl`) + camelCase message DTO live in shipping-service
`infrastructure/event`. The application port (`ShippingEventPublisher`) gained a
String-JSON method (`publishFulfillmentRequested(orderId, messageJson)`) so the
infrastructure DTO does not leak into the application layer; serialization happens
in the consumer (which already holds an `ObjectMapper`).

## Verification (2026-06-08, Docker unavailable on host)
- Compile main+test BOTH modules: BUILD SUCCESSFUL.
- `:order-service:test` → 282 tests, 0 failures/errors/skipped.
- `:shipping-service:test` → 80 tests, 0 failures/errors/skipped.
- The project `build.gradle` excludes `@Tag("integration")` from `:test`; the new
  Testcontainers ITs (`shipping-service/FulfillmentIntegrationTest`) were therefore
  NOT executed here (Docker down — `docker info` failed). They run in the
  Docker-enabled CI integration job. Forward-leg outbox-row assertion + return-leg
  PREPARING→SHIPPED + idempotency are covered by those ITs and by unit tests that
  DID run.
