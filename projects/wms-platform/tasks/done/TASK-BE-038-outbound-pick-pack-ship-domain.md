# TASK-BE-038 — outbound-service: PickingRequest / PackingUnit / Shipment domain + REST

## Goal

Implement the remaining **pick → pack → ship** aggregates and operations for `outbound-service`:

- `PickingRequest` + `PickingRequestLine` domain model + JPA + persistence adapter
- `PickingConfirmation` + `PickingConfirmationLine` domain model + JPA + persistence adapter
- `PackingUnit` + `PackingUnitLine` domain model + JPA + persistence adapter
- `Shipment` domain model + JPA + persistence adapter
- `ConfirmPickingUseCase`, `CreatePackingUnitUseCase`, `SealPackingUnitUseCase`, `ConfirmPackingUseCase`, `ConfirmShippingUseCase`
- REST: `PickingController`, `PackingController`, `ShippingController`
- Domain events: `outbound.picking.completed`, `outbound.packing.completed`, `outbound.shipping.confirmed`
- Schema alignment V11 migration (fix V3/V4 gaps)
- TMS notification wiring via `ShipmentNotificationPort` (`StubTmsClientAdapter`)
- Unit tests for all of the above

Depends on TASK-BE-037 (Order aggregate, OutboundSaga, OutboxPublisher, saga consumers for reserved/released).

## Scope

**Target service**: `outbound-service` (`projects/wms-platform/apps/outbound-service/`)

**In scope:**
- `domain/model/`: `PickingRequest`, `PickingRequestLine`, `PickingConfirmation`, `PickingConfirmationLine`, `PackingUnit`, `PackingUnitLine`, `Shipment`
- `domain/event/`: `PickingCompleted`, `PickingCancelled` (compensation path), `PackingCompleted`, `ShippingConfirmed`
- `application/port/in/`: `ConfirmPickingUseCase`, `CreatePackingUnitUseCase`, `SealPackingUnitUseCase`, `ConfirmPackingUseCase`, `ConfirmShippingUseCase`
- `application/port/out/`: `PickingPersistencePort`, `PackingPersistencePort`, `ShipmentPersistencePort`
- `application/command/`: `ConfirmPickingCommand`, `CreatePackingUnitCommand`, `SealPackingUnitCommand`, `ConfirmPackingCommand`, `ConfirmShippingCommand`
- `application/service/`: `ConfirmPickingService`, `PackingService`, `ConfirmShippingService`
- `adapter/in/rest/controller/`: `PickingController`, `PackingController`, `ShippingController`
- `adapter/in/rest/dto/request/`: per controller (confirm-picking, create-packing-unit, seal-packing-unit, confirm-shipping)
- `adapter/in/rest/dto/response/`: `PickingRequestResponse`, `PackingUnitResponse`, `ShipmentResponse`
- `adapter/out/persistence/entity/`: `PickingRequestEntity`, `PickingRequestLineEntity`, `PickingConfirmationEntity`, `PickingConfirmationLineEntity`, `PackingUnitEntity`, `PackingUnitLineEntity`, `ShipmentEntity`
- `adapter/out/persistence/repository/`: one per entity
- `adapter/out/persistence/mapper/`: one per aggregate
- `adapter/out/persistence/adapter/`: `PickingPersistenceAdapter`, `PackingPersistenceAdapter`, `ShipmentPersistenceAdapter`
- `EventEnvelopeSerializer`: extend for new 4 event types
- `resources/db/migration/V11__picking_pack_ship_schema_align.sql`: ADD COLUMN IF NOT EXISTS to fix V3/V4 gaps
- Idempotency filter wiring for new mutating endpoints

**Out of scope:**
- `InboundService` changes
- Multi-warehouse picking (v2)
- TMS quote / carrier rating (v2)
- Per-line picking confirmation (v2)
- RMA inbound (v2)

## Acceptance Criteria

**AC-01** `PickingRequest` aggregate has all domain-model.md §2 fields. `PickingRequestLine` has all §2.PickingRequestLine fields. One PickingRequest per Order enforced via unique constraint on `order_id`.

**AC-02** `PickingConfirmation` aggregate has all domain-model.md §3 fields. `qty_confirmed` per line must equal `order_line.qty_ordered` (`PICKING_INCOMPLETE` if not).

**AC-03** `PackingUnit` has all domain-model.md §4 fields including `carton_no`, `packing_type` (BOX/PALLET/ENVELOPE), `weight_grams`, dimensions, `status` (OPEN/SEALED). `SEALED` PackingUnit is immutable.

**AC-04** `Shipment` has all domain-model.md §5 fields including `shipment_no` (globally unique, auto-generated), `tms_status` (PENDING/NOTIFIED/NOTIFY_FAILED), `tms_notified_at`, `tms_request_id`.

**AC-05** `ConfirmPickingService`:
  - Validates Order is `PICKING`
  - Creates `PickingConfirmation` + `PickingConfirmationLine` rows
  - Calls `Order.completePicking()` → `PICKED`; `OutboundSaga` transitions `RESERVED → PICKING_CONFIRMED`
  - Writes `outbound.picking.completed` outbox row in same TX
  - Role: `OUTBOUND_WRITE`

**AC-06** `PackingService`:
  - `createPackingUnit`: Order must be `PACKING`; creates `PackingUnit` (status=OPEN)
  - `sealPackingUnit`: seals OPEN PackingUnit; validates `PackingUnitLine.qty` sum per `order_line_id` covers `order_line.qty_ordered` before sealing (`PACKING_INCOMPLETE`)
  - `ConfirmPackingUseCase`: all PackingUnits for the order must be SEALED; calls `Order.completePacking()` → `PACKED`; `OutboundSaga` → `PACKING_CONFIRMED`; writes `outbound.packing.completed` outbox row
  - Role: `OUTBOUND_WRITE`

**AC-07** `ConfirmShippingService`:
  - Order must be `PACKED`
  - Creates `Shipment` (auto-generates `shipment_no = SHIP-{YYYYMMDD}-{seq}`)
  - Calls `Order.confirmShipping()` → `SHIPPED`; `OutboundSaga` → `SHIPPED`
  - Writes `outbound.shipping.confirmed` outbox row in same TX (contains `reservationId` + per-line confirmed quantities for inventory-service to consume)
  - **Post-commit** (ApplicationEvent / `@TransactionalEventListener(AFTER_COMMIT)`): calls `ShipmentNotificationPort.notify(shipment)` (→ `StubTmsClientAdapter`); on success sets `tms_status=NOTIFIED`; on failure sets `tms_status=NOTIFY_FAILED`, saga advances to `SHIPPED_NOT_NOTIFIED`
  - Role: `OUTBOUND_WRITE`

**AC-08** REST endpoints:
  - `POST /api/v1/outbound/orders/{id}/picking/confirm` → 200 `PickingRequestResponse`
  - `POST /api/v1/outbound/orders/{id}/packing/units` → 201 `PackingUnitResponse`
  - `PATCH /api/v1/outbound/orders/{id}/packing/units/{unitId}/seal` → 200
  - `POST /api/v1/outbound/orders/{id}/packing/confirm` → 200
  - `POST /api/v1/outbound/orders/{id}/shipping/confirm` → 201 `ShipmentResponse`
  All mutating endpoints require `Idempotency-Key` header.

**AC-09** V11 migration adds missing columns to V3/V4 tables via `ADD COLUMN IF NOT EXISTS`:
  - `picking_request`: `saga_id UUID`
  - `picking_confirmation`: `order_id UUID`, `notes VARCHAR(500)`, `confirmed_by VARCHAR(100)` (if absent)
  - `packing_unit`: `carton_no VARCHAR(40)`, `packing_type VARCHAR(20)`, `weight_grams INT`, `length_mm INT`, `width_mm INT`, `height_mm INT`, `status VARCHAR(20)`, `version BIGINT`, `updated_at TIMESTAMPTZ`
  - `shipment`: `shipment_no VARCHAR(40)`, `tms_status VARCHAR(30)`, `tms_notified_at TIMESTAMPTZ`, `tms_request_id UUID`, `version BIGINT`, `updated_at TIMESTAMPTZ`

**AC-10** All new domain aggregates, services, and controllers have unit tests using port fakes (no Testcontainers). Build passes (`./gradlew :apps:outbound-service:test`).

**AC-11** `GlobalExceptionHandler` extended with new error codes: `PICKING_INCOMPLETE` (422), `PACKING_INCOMPLETE` (422), `PACKING_UNIT_NOT_FOUND` (404), `SHIPMENT_NOT_FOUND` (404).

## Related Specs

- `projects/wms-platform/specs/services/outbound-service/architecture.md`
- `projects/wms-platform/specs/services/outbound-service/domain-model.md` §2 (PickingRequest), §3 (PickingConfirmation), §4 (PackingUnit), §5 (Shipment)
- `projects/wms-platform/specs/services/outbound-service/sagas/outbound-saga.md` §saga steps for PICKING_CONFIRMED, PACKING_CONFIRMED, SHIPPED
- `projects/wms-platform/specs/services/outbound-service/state-machines/saga-status.md`
- `projects/wms-platform/specs/services/outbound-service/external-integrations.md` (TMS adapter I1–I4)
- `projects/wms-platform/specs/services/outbound-service/idempotency.md`

## Related Contracts

- `projects/wms-platform/specs/contracts/http/outbound-service-api.md` §2 (ConfirmPicking), §3 (Packing), §4 (Shipping), §5 (Cancel after pick)
- `projects/wms-platform/specs/contracts/events/outbound-events.md` §5 (picking.completed), §6 (packing.completed), §7 (shipping.confirmed)

## Edge Cases

- **PackingUnit created but Order not in PACKING status**: `STATE_TRANSITION_INVALID` (422).
- **Sum of PackingUnitLine.qty < order_line.qty_ordered at ConfirmPacking**: `PACKING_INCOMPLETE` (422).
- **ConfirmShipping TMS failure**: saga advances to `SHIPPED_NOT_NOTIFIED`; stock is already consumed (shipping.confirmed outbox was written in same TX). Manual retry via `POST /shipments/{id}/retry-tms-notify` (stub in v1).
- **Idempotent re-submit of ConfirmPicking**: same `Idempotency-Key` → cached 200 response replayed.
- **PickingConfirmation qty mismatch**: `qty_confirmed != qty_ordered` for any line → `PICKING_INCOMPLETE` (422).
- **LOT-tracked SKU without lot_id in PickingConfirmationLine**: `LOT_REQUIRED` (422).
- **Shipment.shipment_no collision** (extremely unlikely): unique constraint on DB; `DataIntegrityViolationException` → retry with new seq.

## Failure Scenarios

- **ConfirmShippingService TX rollback**: shipping.confirmed outbox row not written; Order stays `PACKED`; Shipment not persisted. Caller may retry with same `Idempotency-Key`.
- **TMS notification call fails** (post-commit): `tms_status=NOTIFY_FAILED`; saga to `SHIPPED_NOT_NOTIFIED`; stock already consumed — manual TMS retry endpoint required. Failure counter incremented.
- **ConfirmPickingService optimistic lock conflict**: `CONFLICT` (409); caller should GET fresh Order and retry.
- **PackingUnit seal attempted on SEALED unit**: idempotent — if already sealed, return current state (no-op via Idempotency-Key cache).
