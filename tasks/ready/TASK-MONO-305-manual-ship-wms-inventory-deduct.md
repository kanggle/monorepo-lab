# TASK-MONO-305 — Operator-gated manual ship-confirm propagates to WMS inventory deduction

- Status: ready
- Type: cross-project feature (ecommerce shipping-service ↔ wms outbound-service ↔ platform-console)
- ADR: ADR-MONO-022 §D4 (extension — operator-gated manual reverse-confirm)
- Analysis model: Opus 4.8 / Impl: Opus (event-driven cross-project saga wiring)

## Goal

When an operator manually marks an ecommerce Shipping `SHIPPED` (entering tracking
number/carrier by hand, not via the WMS auto-loop), let the operator **optionally**
elect to also deduct WMS physical inventory for that order. Today the manual path
(`PUT /api/shippings/{id}/status`) flips only the ecommerce Shipping/Order and emits
no signal to WMS, so a WMS-routed order's reservation stays `RESERVED` forever
(phantom reserved stock). This task closes that gap **without touching the inventory
data model** by routing the operator's election through the WMS outbound-service,
which already owns the `orderNo → order → saga → reservation` mapping and the
existing `shipping.confirmed → inventory deduction` path.

## Scope

**ecommerce shipping-service** (`projects/ecommerce-microservices-platform/apps/shipping-service`)
- `Shipping` aggregate gains a `wmsRouted` boolean (set true when the fulfillment
  forward-leg event was actually published for that order). New Flyway `V8` column
  `wms_routed BOOLEAN NOT NULL DEFAULT FALSE`.
- `OrderConfirmedEventConsumer.publishFulfillmentRequested` returns whether it
  published; `createShipping` records `wmsRouted` accordingly.
- `UpdateShippingStatusRequest`/`Command` gain `deductWmsInventory` (boolean, default
  false). `ShippingCommandService.updateStatus`: when the target is `SHIPPED` AND
  `deductWmsInventory` AND the row is `wmsRouted`, publish a new outbox event
  `ecommerce.shipping.manual-confirm-requested.v1` (wms camelCase envelope) carrying
  `orderNo (= orderId)`, `carrierCode`, `trackingNo`.
- `ShippingResponse` exposes `wmsRouted` so the console can gate the toggle.

**wms outbound-service** (`projects/wms-platform/apps/outbound-service`)
- New `ManualShipConfirmConsumer` (`@Profile("!standalone")`) subscribing the new
  topic. Layered idempotency mirrors `FulfillmentRequestedConsumer`:
  `EventDedupePort` (eventId) outer + saga terminal-state guard inner.
- Resolve `orderNo → Order` via `OrderJpaRepository.findByOrderNo`. If absent →
  no-op (order never routed through WMS). Load saga by orderId:
  - `PACKING_CONFIRMED` → call existing `ConfirmShippingUseCase.confirm` (carrierCode
    from the event) → emits `wms.outbound.shipping.confirmed.v1` → inventory deducts.
  - already `SHIPPED`/`COMPLETED` → no-op (already deducted).
  - any earlier state → no-op + ops WARN log (not yet physically picked/packed —
    deduction is not physically valid; no silent forced skip).

**platform-console** (`projects/platform-console/apps/console-web`)
- `ShipFormDialog` adds a "WMS 재고 차감" checkbox, shown only when the shipping row
  is `wmsRouted`. Wire `deductWmsInventory` through `shipping-types.ts`,
  `shippings-api.ts`, and the BFF proxy. `order-types`/list types surface `wmsRouted`.

**inventory-service** — unchanged.

## Acceptance Criteria

1. A WMS-routed order whose saga is `PACKING_CONFIRMED`: operator manual-ships with
   the toggle ON → outbound consumes the event → `ConfirmShippingUseCase` runs →
   `wms.outbound.shipping.confirmed.v1` published → inventory reservation deducted
   (RESERVED → consumed). Order/Shipping already SHIPPED (operator path); the
   return-leg `markShippedByOrderId` is an idempotent no-op (no loop).
2. Toggle OFF (or absent) → no WMS event; behaviour identical to today.
3. Non-WMS-routed order → toggle not shown in console; even if the event were sent,
   outbound `findByOrderNo` misses → safe no-op.
4. Saga not yet `PACKING_CONFIRMED` → WARN log, no deduction, no exception.
5. Duplicate delivery of the same event → `EventDedupePort` IGNORED_DUPLICATE; already
   SHIPPED saga → terminal no-op. Inventory deducted exactly once.
6. `deductWmsInventory` defaults false everywhere (back-compatible; existing callers
   and the `markShippedByOrderId` return-leg unaffected).

## Related Specs
- `docs/adr/ADR-MONO-022-ecommerce-wms-fulfillment-integration.md` §D4 (extended)
- `projects/wms-platform/specs/services/outbound-service/state-machines/saga-status.md`

## Related Contracts
- `projects/ecommerce-microservices-platform/specs/contracts/events/shipping-events.md`
  (new produced event `ecommerce.shipping.manual-confirm-requested.v1`)
- `projects/ecommerce-microservices-platform/specs/contracts/http/shipping-api.md`
  (PUT status body gains `deductWmsInventory`; ShippingResponse gains `wmsRouted`)
- `projects/wms-platform/specs/contracts/events/ecommerce-fulfillment-subscriptions.md`
  (new consumed event)

## Edge Cases
- orderId == orderNo (FulfillmentAcl invariant) — the cross-domain correlation key.
- carrierCode taken from operator input; if blank, outbound falls back to its default
  carrier resolution (do not block the deduction on a missing carrier).
- Event envelope follows the **wms camelCase** convention (eventId/eventType/
  occurredAt/aggregateType/aggregateId/tenantId/payload) so `EventEnvelopeParser` is
  reused unchanged (same ACL pattern as FulfillmentAcl).

## Failure Scenarios
- Unparseable envelope / missing orderNo → IllegalArgumentException → DLT (non-retryable).
- `findByOrderNo` miss → no-op (debug log), NOT an error (non-WMS order is legitimate).
- `ConfirmShippingUseCase` optimistic-lock / state race → standard outbound handling;
  dedupe + terminal-state guard make re-delivery safe.
