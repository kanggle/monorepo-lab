# TASK-BE-431 — Fix the outbound→inventory `picking.requested` contract (reservation leg)

**Status:** done
**Type:** TASK-BE
**Analysis model:** Opus 4.8 / **Recommended impl model:** Opus 4.8 (cross-service contract + allocation design)

> Done: merged via PR #1933 (squash `1191e10a8`). Live-verified in the federation demo — inventory reserved (stock 100→98) and the saga advanced to RESERVED.

## Goal

The WMS-internal reservation leg `outbound.picking.requested` → inventory-service is
**broken**: the producer and consumer disagree on the wire shape, so a real event NPEs at
the consumer and a fulfillment order can never reach `RESERVED` (picking stays blocked).
This was never caught because the ecommerce e2e host-synthesises the wms boundary and the
inventory ITs synthesise the consumer's *assumed* shape — the two services were never run
together against a shared broker until the ADR-MONO-022 fulfillment loop was wired live.

Producer ([outbound] `EventEnvelopeSerializer.pickingRequestedPayload`):
- top-level: `sagaId`, `reservationId`, `orderId`, `warehouseId`
- per line: `orderLineId`, `skuId`, `lotId`, `locationId` (**null in v1** — location is bound at picking), `qtyToReserve`

Consumer ([inventory] `PickingRequestedConsumer.buildCommand`) currently reads:
- top-level: `pickingRequestId` (absent → NPE), `warehouseId`
- per line: `inventoryId` (absent → NPE), `quantity`

## Design decision (allocation policy v1)

The producer (outbound-service) **cannot** know inventory-service's private `inventory`
row PK (`inventoryId`); it only knows domain identity (`skuId`, `warehouseId`, optional
`lotId`/`locationId`). Therefore the **consumer resolves the inventory row(s)** from the
natural key:

- top-level: read `reservationId` as the `pickingRequestId` (outbound declares
  `reservationId` = `PickingRequest.id`; they are 1:1).
- per line `(skuId, lotId, locationId?, qtyToReserve)`:
  - `locationId` present → resolve the single row via `InventoryRepository.findByKey(locationId, skuId, lotId)`.
  - `locationId` null (v1 norm) → resolve candidate rows for `(warehouseId, skuId, lotId)`
    with `available_qty > 0`, and **allocate `qtyToReserve` across them, greatest
    `available_qty` first** (deterministic; single-row in the common case). Spanning
    multiple rows only when one row is insufficient.
  - total available < `qtyToReserve` → existing shortfall path emits
    `inventory.reserve.failed` → outbound auto-backorder (unchanged).

`ReserveStockCommand.Line(inventoryId, quantity)` is **unchanged** — the consumer does the
resolution/allocation and may emit multiple command lines from one event line. Minimal
blast radius: `ReserveStockService`, `Reservation`, and all published events stay as-is.

## Scope

- inventory-service:
  - `InventoryRepository` (+impl, +JPA): add `List<Inventory> findAvailableByWarehouseSkuLot(UUID warehouseId, UUID skuId, UUID lotId)` ordered by `available_qty DESC, id ASC` (deterministic), `available_qty > 0`. `lotId` null → match rows with `lot_id IS NULL`.
  - `PickingRequestedConsumer.buildCommand`: read the real wire (`reservationId`, per-line `skuId`/`lotId`/`locationId`/`qtyToReserve`); resolve + allocate to `ReserveStockCommand.Line(inventoryId, qty)`; on total shortfall, still build a command that the existing `signalShortfall` path turns into `inventory.reserve.failed` (do not pre-empt that path — let `doReserve` emit it so the saga backorders).
- spec: `projects/wms-platform/specs/contracts/events/inventory-events.md` — add a **Consumed Events** entry for `outbound.picking.requested` documenting the authoritative consumed shape (matching the producer SoT in `outbound-events.md §3`) + the resolution/allocation policy above. Reconcile any stale `§C2` `inventoryId`/`pickingRequestId` description.

## Acceptance Criteria

- AC-1: A real `outbound.picking.requested.v1` envelope produced by outbound's
  `EventEnvelopeSerializer` (top `reservationId`, line `skuId`+`locationId:null`+`qtyToReserve`),
  fed to `PickingRequestedConsumer`, creates a `Reservation` and emits `inventory.reserved`
  (no NPE). **Add this as a regression test that uses the real producer serializer output**
  (the guard that was missing).
- AC-2: `locationId` present → resolves that specific row.
- AC-3: `locationId` null, single stock row → reserves it. Multi-row → allocates greatest-available first.
- AC-4: total available < requested → `inventory.reserve.failed` emitted (backorder), no partial mutation.
- AC-5: idempotent replay (same `reservationId`/`eventId`) → no duplicate reservation.
- AC-6: `:inventory-service:test` GREEN; existing consumer tests updated to the real wire.

## Related Specs / Contracts

- `projects/wms-platform/specs/contracts/events/outbound-events.md` §3 (producer SoT — do NOT change)
- `projects/wms-platform/specs/contracts/events/inventory-events.md` (consumed shape + policy — update)
- ADR-MONO-022 (ecommerce↔wms fulfillment integration)

## Edge Cases

- `lotId` null vs present (partial unique indexes `uq_inventory_loc_sku_lot` / `..._no_lot`).
- Multiple inventory rows for one SKU across locations (allocation spanning rows).
- Concurrent reserves on the same row → existing optimistic-lock retry (unchanged).

## Failure Scenarios

- No stock row at all for `(warehouse, sku, lot)` → treat as zero available → shortfall → `reserve.failed` (not NPE / not InventoryNotFoundException-to-DLT).
