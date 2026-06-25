# TASK-BE-437 — Fix the outbound→inventory `shipping.confirmed` contract (return / deduct leg)

**Status:** ready
**Type:** TASK-BE
**Analysis model:** Opus 4.8 / **Recommended impl model:** Opus 4.8 (cross-service contract design)

## Goal

The WMS-internal **return leg** `outbound.shipping.confirmed` → inventory-service is **broken**
in exactly the same way [TASK-BE-431](../done/TASK-BE-431-outbound-inventory-picking-requested-contract.md)
fixed the forward (reservation) leg — the producer and consumer disagree on the wire shape, so a
real ship-confirm event NPEs at the inventory consumer and **the reserved stock is never
deducted** (the reservation stays `RESERVED` forever). BE-431 corrected the forward leg but left
this return-leg sibling un-fixed.

This is invisible to the existing suites for the same reason BE-431 was: the inventory ITs feed
the consumer's *assumed* shape, and the ecommerce return-leg consumer on the same topic only
reads `orderNo` (so the ecommerce order still reaches `SHIPPED`). It only surfaced when the real
outbound `ConfirmShippingUseCase` published against a shared broker during the live ADR-MONO-022
fulfillment loop: `ecommerce` order/shipping went `SHIPPED`, the outbound saga went `SHIPPED`, but
inventory stayed `RESERVED` (stock not decremented), the inventory consumer looping NPE→retry→skip.

Producer ([outbound] `EventEnvelopeSerializer.shippingConfirmedPayload`, authoritative schema
`specs/contracts/events/outbound-events.md` §7):
- top-level: `sagaId`, `reservationId` (= `PickingRequest.id`), `orderId`, `orderNo`, `shipmentId`, `shipmentNo`, `warehouseId`, `shippedAt`, `carrierCode`
- per line: `orderLineId`, `skuId`, `lotId`, `locationId`, `qtyConfirmed`

Consumer ([inventory] `ShippingConfirmedConsumer.applyConfirm`) previously read:
- top-level: `pickingRequestId` (absent → **NPE**)
- per line: `reservationLineId` (absent → **NPE**), `shippedQuantity` (absent; producer sends `qtyConfirmed`)

## Design decision

Mirror the BE-431 resolution: the **producer is SoT** (outbound-events.md §7, matches the
serializer byte-for-byte) and the **consumer + its contract mirror (inventory-events.md §C4) are
corrected** to the producer shape:

- top-level: read `reservationId` and use it as the `pickingRequestId`
  (`ReservationRepository.findByPickingRequestId`) — outbound declares `reservationId` =
  `PickingRequest.id`, 1:1 with inventory's `pickingRequestId`.
- per line `(skuId, lotId, qtyConfirmed)`: the producer carries domain identity only and cannot
  know inventory's private `ReservationLine` PK, so the consumer **resolves each shipped line to
  its owning `ReservationLine` by `(skuId, lotId)`** and builds
  `ConfirmReservationCommand.Line(reservationLineId, qtyConfirmed)`.
- A shipped line with no matching reservation line is a **hard error** (`IllegalArgumentException`)
  — a genuine data inconsistency, not a transient (no silent no-op, no DLT loop).

No producer change. No inventory data-model change. No new events.

## Scope

- `inventory-service`: `ShippingConfirmedConsumer.applyConfirm` (read `reservationId` +
  `qtyConfirmed`, map lines by `(skuId, lotId)` → `reservationLineId`).
- Contract `specs/contracts/events/inventory-events.md` §C4 — replace the stale shape with the
  producer-mirrored shape + the BE-437 correction note (mirrors the BE-431 §C2 edit).
- New unit test `ShippingConfirmedConsumerTest` driving the **real** producer wire.

Out of scope: outbound producer, ecommerce return-leg consumer (reads `orderNo`, already correct),
partial-shipment semantics (v1 = `qtyConfirmed` equals `ReservationLine.quantity` exactly).

## Acceptance Criteria

- AC-1: A real producer-shape `outbound.shipping.confirmed` (top `reservationId`, line
  `skuId`/`lotId`/`qtyConfirmed`) confirms the reservation: `ConfirmReservationUseCase` is called
  with `reservationId` and a line carrying the **mapped** `reservationLineId` + `qtyConfirmed`.
- AC-2: An already-terminal (`CONFIRMED`/`RELEASED`) reservation is a no-op (terminal guard).
- AC-3: An unknown `reservationId` is ignored (no throw, no confirm).
- AC-4: A shipped line whose `(skuId, lotId)` matches no reservation line → `IllegalArgumentException`.
- AC-5: Duplicate `eventId` is deduped (confirm runs once).
- AC-6: `inventory-events.md` §C4 mirrors `outbound-events.md` §7 byte-for-byte.
- `:inventory-service:test` green.

## Related Specs

- `projects/wms-platform/specs/services/inventory-service/` (reservation lifecycle W5: shipped → consumed)
- ADR-MONO-022 (ecommerce↔wms fulfillment loop)

## Related Contracts

- `projects/wms-platform/specs/contracts/events/outbound-events.md` §7 (producer SoT) — unchanged
- `projects/wms-platform/specs/contracts/events/inventory-events.md` §C4 (consumer mirror) — corrected

## Edge Cases

- `lotId` null vs present — matched via `Objects.equals` on `(skuId, lotId)`.
- Multiple lines per event — each mapped independently.
- `reservationId` == reservation's `pickingRequestId` (1:1) — resolution key.

## Failure Scenarios

- Reservation not found → WARN + no-op (late/duplicate after release, or wrong shard).
- Line with no matching reservation line → hard `IllegalArgumentException` (surfaces a real
  producer/consumer drift rather than silently under-deducting).
- Confirm race (`StateTransitionInvalidException`) → WARN, absorbed (terminal already reached).
