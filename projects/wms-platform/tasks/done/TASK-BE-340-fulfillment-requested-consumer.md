# Task ID

TASK-BE-340

# Title

ADR-MONO-022 §D7 ② (wms) — `FulfillmentRequestedConsumer` (ecommerce → wms order intake) + additive `shipTo` on the outbound Order + additive `orderNo` echo on `outbound.shipping.confirmed`.

# Status

done

# Owner

claude (Opus 4.8) — wms outbound-service implementation (Hexagonal, event-consumer). Mirrors the existing `InventoryReservedConsumer` + ERP-webhook order-intake pattern. Adds no commerce logic (ADR-022 §1.4).

# Task Tags

- event
- code

---

# Dependency Markers

- **선행**: TASK-MONO-194 (contracts). Producer = TASK-BE-341 (ecommerce); but additive + graceful-degradation ⇒ can ship independently.
- **맥락**: `ecommerce-fulfillment-subscriptions.md` (consumer contract) + `outbound-events.md` (additive fields).

# Goal

`outbound-service` consumes `ecommerce.fulfillment.requested.v1`, resolves codes→uuids via `MasterReadModelPort`, and calls the existing `ReceiveOrderUseCase` with `source=FULFILLMENT_ECOMMERCE` + `shipTo`. `outbound.order.received` carries `shipTo`; `outbound.shipping.confirmed` carries `orderNo`.

# Scope

## In Scope (outbound-service)
1. **`FulfillmentRequestedConsumer`** (`adapter/in/messaging/consumer/`) — mirror `InventoryReservedConsumer`: `@KafkaListener(topics=ecommerce.fulfillment.requested.v1, groupId=outbound-service)`, `@Transactional`, parse via `EventEnvelopeParser`, dedupe via `EventDedupePort`, resolve `customerPartnerCode`/`warehouseCode`/`skuCode`(+`lotNo`) → uuids (`findPartnerByCode`/`findWarehouseByCode`/`findSkuByCode`/`findLotBySkuAndLotNo`), build `ReceiveOrderCommand`, call `ReceiveOrderUseCase.receive(...)`. Unresolved/inactive master → throw non-retryable → DLT.
2. **`ReceiveOrderCommand`** — additive optional `ShipToAddress shipTo` (recipientName/address/phone).
3. **`Order` domain + `OrderEntity`** — additive nullable `shipTo` (3 columns `ship_to_name`/`ship_to_address`/`ship_to_phone`). New Flyway `V15__order_shipto_address.sql`.
4. **`OrderSource` enum** — additive `FULFILLMENT_ECOMMERCE`.
5. **`OrderReceivedEvent`** — additive `shipTo`; populate in `ReceiveOrderService.emitOrderReceivedAndPickingRequested`.
6. **`ShippingConfirmedEvent`** — additive `String orderNo`; populate in `ConfirmShippingService.emitShippingOutbox` (read from Order). Same for `outbound.order.cancelled` already carries `orderNo` — verify.
7. **Seed** (integration/demo): `ECOMMERCE-STORE` partner + default warehouse + mapped SKUs ACTIVE in master snapshots.
8. **Config**: `outbound.kafka.topics.fulfillment-requested: ecommerce.fulfillment.requested.v1`.

## Out of Scope
- ecommerce side (TASK-BE-341). Multi-warehouse routing. Auto-cancel/refund saga.

# Acceptance Criteria

- AC-1: Consumer creates an outbound Order + saga from a valid fulfillment event (Testcontainers IT, `OutboundServiceIntegrationBase`): assert `outbound_order` row (`source=FULFILLMENT_ECOMMERCE`, `order_no`=incoming, `ship_to_*` populated) + `outbound_outbox` `outbound.order.received` (with `shipTo`) + `outbound.picking.requested`.
- AC-2: Idempotency — duplicate `eventId` → no second order (dedupe); duplicate `orderNo` → `existsByOrderNo` no-op.
- AC-3: Unresolved/inactive partner|warehouse|sku → non-retryable → DLT (unit test on the consumer dispatch).
- AC-4: `outbound.shipping.confirmed` payload now includes `orderNo` (= Order.orderNo); existing inventory/admin consumers unaffected (additive). Regression: existing outbound IT still green.
- AC-5: ERP-webhook + manual order paths unchanged (`shipTo=null`, source unchanged). `:projects:wms-platform:apps:outbound-service:test` green; `integrationTest` for the new IT green.

# Related Specs

- `specs/contracts/events/ecommerce-fulfillment-subscriptions.md`, `outbound-events.md`, `specs/contracts/webhooks/erp-order-webhook.md` (pattern), `specs/services/outbound-service/architecture.md`.

# Related Contracts

- Consumes `ecommerce.fulfillment.requested.v1`; emits additive fields on `wms.outbound.order.received.v1` / `wms.outbound.shipping.confirmed.v1`.

# Edge Cases

- Envelope is ecommerce-produced but **wms camelCase shape** (ACL) → `EventEnvelopeParser` works unchanged.
- `shipTo=null` allowed (B2B fallback). `lotNo=null` → any-lot (existing behavior).
- Backorder: reserve-failure path already moves Order → `BACKORDERED` + `outbound.order.cancelled` (carries `orderNo`) — no new code, verify it fires.

# Failure Scenarios

- Silent drop of unmapped SKU → forbidden; must DLT + alert (AC-3).
- Non-additive enum/field change breaking inventory/admin consumers → forbidden (AC-4 regression).
