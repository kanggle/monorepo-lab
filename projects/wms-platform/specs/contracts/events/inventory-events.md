# Event Contract — inventory-service Domain Events

Authoritative schemas for events **published** by `inventory-service`, and the expected
shapes of events **consumed** from `inbound-service` and `outbound-service`.

Consumers (`outbound-service`, `admin-service`, `notification-service`) rely on the
published schemas. Changes here precede code changes (per `CLAUDE.md` Contract Rule).

---

## Delivery Semantics

- **Publisher side**: transactional outbox (trait `transactional` T3).
  State change and outbox row written in the same DB transaction; a separate publisher
  process forwards outbox rows to Kafka. Guarantees **at-least-once** delivery.
  Partition key: `locationId` for all inventory mutations (ensures ordered delivery
  per location, which is the contention unit).
- **Consumer side**: must implement **idempotent handling** keyed by `eventId`
  (trait `transactional` T8). See `specs/services/inventory-service/idempotency.md`.
- **No cross-topic ordering.** A consumer correlating `inventory.reserved` with
  `inventory.confirmed` must use `pickingRequestId` / `reservationId`, not arrival order.

---

## Global Envelope

Every event shares this outer envelope. The `payload` field carries event-specific data.

```json
{
  "eventId": "0191d8f0-1f0e-7c40-9d13-4a2c9e3f1234",
  "eventType": "inventory.received",
  "eventVersion": 1,
  "occurredAt": "2026-04-20T10:00:00.123Z",
  "producer": "inventory-service",
  "aggregateType": "inventory",
  "aggregateId": "uuid-of-inventory-row",
  "traceId": "abc-123",
  "actorId": "user-uuid-or-system:putaway-consumer",
  "payload": { /* event-specific */ }
}
```

| Field | Type | Notes |
|---|---|---|
| `eventId` | UUIDv7 string | Unique per event. Consumers dedupe on this (T8) |
| `eventType` | string | `inventory.<action>` or `inventory.<subtype>.<action>` |
| `eventVersion` | int | Schema version for that `eventType`. v1 is the baseline |
| `occurredAt` | ISO-8601 UTC, ms precision | DB transaction commit time |
| `producer` | string | Always `inventory-service` |
| `aggregateType` | string | `inventory | reservation | stock_adjustment | stock_transfer | alert` |
| `aggregateId` | UUID string | Aggregate root id (e.g., Inventory row id, Reservation id) |
| `traceId` | string | OTel trace id; propagated from REST request or consumed Kafka event |
| `actorId` | string or null | JWT subject for REST-driven; `system:<consumer-name>` for event-driven; null if unavailable |
| `payload` | object | Defined per `eventType` below |

Serialization: JSON. Future Avro/Protobuf migration possible but not v1.

---

## Topic Layout

| Topic | Event type(s) | Partition key |
|---|---|---|
| `wms.inventory.received.v1` | `inventory.received` | `locationId` |
| `wms.inventory.adjusted.v1` | `inventory.adjusted` | `locationId` |
| `wms.inventory.transferred.v1` | `inventory.transferred` | `sourceLocationId` |
| `wms.inventory.reserved.v1` | `inventory.reserved` | `locationId` of first line |
| `wms.inventory.released.v1` | `inventory.released` | `locationId` of first line |
| `wms.inventory.confirmed.v1` | `inventory.confirmed` | `locationId` of first line |
| `wms.inventory.reserve.failed.v1` | `inventory.reserve.failed` | `pickingRequestId` |
| `wms.inventory.alert.v1` | `inventory.low-stock-detected` | `locationId` |

- `v1` in the topic name: contract version. Breaking schema changes require a parallel `v2`
  topic with coexistence period (per `.claude/skills/cross-cutting/api-versioning/SKILL.md`).
- Retention: minimum 7 days. 30-day preferred for DLQ replay windows.
- Partitions: start with 6 per topic (inventory is higher throughput than master).
- Dead-letter topic: `<topic>.DLT` — for consumers, not the producer.

---

## Published Events (inventory-service → consumers)

### 1. `inventory.received`

Triggered when the `PutawayCompletedConsumer` processes `inbound.putaway.completed`.
One event per ASN putaway batch (one event covers all lines of the putaway).

Topic: `wms.inventory.received.v1`
`aggregateType`: `inventory`
`aggregateId`: the first affected Inventory row id (multi-line putaways emit one event; see `lines`)

```json
"payload": {
  "warehouseId": "uuid",
  "sourceEventId": "uuid-of-inbound.putaway.completed-event",
  "asnId": "uuid",
  "lines": [
    {
      "inventoryId": "uuid",
      "locationId": "uuid",
      "locationCode": "WH01-A-01-01-01",
      "skuId": "uuid",
      "lotId": "uuid-or-null",
      "qtyReceived": 50,
      "availableQtyAfter": 50
    }
  ]
}
```

Consumer expectations:

- `notification-service`: triggers low-stock re-evaluation (not applicable here — stock increased)
- `admin-service`: projects received quantities into `InboundSummary` and `InventorySnapshot`

### 2. `inventory.adjusted`

Triggered on manual adjustment (REST: `POST /adjustments`, `mark-damaged`, `write-off-damaged`).

Topic: `wms.inventory.adjusted.v1`
`aggregateType`: `stock_adjustment`
`aggregateId`: `StockAdjustment.id`

```json
"payload": {
  "adjustmentId": "uuid",
  "inventoryId": "uuid",
  "locationId": "uuid",
  "skuId": "uuid",
  "lotId": "uuid-or-null",
  "bucket": "AVAILABLE",
  "delta": -5,
  "reasonCode": "ADJUSTMENT_LOSS",
  "reasonNote": "실사 결과 5개 분실 확인",
  "movementType": "ADJUSTMENT",
  "inventory": {
    "availableQty": 75,
    "reservedQty": 20,
    "damagedQty": 0,
    "onHandQty": 95,
    "version": 6
  }
}
```

`movementType` values for this event: `ADJUSTMENT | DAMAGE_MARK | DAMAGE_WRITE_OFF`

Consumer expectations:

- `admin-service`: projects into `AdjustmentAudit` and `InventorySnapshot`
- `notification-service`: may alert on large negative adjustments (threshold in `admin.settings`)

Also fires `inventory.low-stock-detected` (on `wms.inventory.alert.v1`) when the mutation
reduces `availableQty` below the configured threshold. See §7.

### 3. `inventory.transferred`

Triggered on successful stock transfer between two locations.

Topic: `wms.inventory.transferred.v1`
`aggregateType`: `stock_transfer`
`aggregateId`: `StockTransfer.id`

```json
"payload": {
  "transferId": "uuid",
  "warehouseId": "uuid",
  "skuId": "uuid",
  "lotId": "uuid-or-null",
  "quantity": 10,
  "reasonCode": "TRANSFER_INTERNAL",
  "source": {
    "locationId": "uuid",
    "locationCode": "WH01-A-01-01-01",
    "inventoryId": "uuid",
    "availableQtyAfter": 70
  },
  "target": {
    "locationId": "uuid",
    "locationCode": "WH01-B-02-01-01",
    "inventoryId": "uuid",
    "availableQtyAfter": 10,
    "wasCreated": false
  }
}
```

`target.wasCreated`: `true` if the target Inventory row was upserted (first stock at that location).

Consumer expectations:

- `admin-service`: projects into `InventorySnapshot` for both locations

### 4. `inventory.reserved`

Triggered when `outbound-service` calls `POST /reservations` (W4 reserve phase).

Topic: `wms.inventory.reserved.v1`
`aggregateType`: `reservation`
`aggregateId`: `Reservation.id`

```json
"payload": {
  "reservationId": "uuid",
  "pickingRequestId": "uuid",
  "warehouseId": "uuid",
  "expiresAt": "2026-04-21T10:00:00Z",
  "lines": [
    {
      "reservationLineId": "uuid",
      "inventoryId": "uuid",
      "locationId": "uuid",
      "skuId": "uuid",
      "lotId": "uuid-or-null",
      "quantity": 5,
      "availableQtyAfter": 75,
      "reservedQtyAfter": 25
    }
  ]
}
```

Consumer expectations:

- `outbound-service`: validates reservation was created successfully (correlation by
  `pickingRequestId`); updates `OutboundSaga` state machine
- `admin-service`: increments active reservation count in `InventorySnapshot`

### 4a. `inventory.reserve.failed`  ⚠️ Cross-service contract

Triggered when `PickingRequestedConsumer` processes an
`outbound.picking.requested` and the requested quantity exceeds availability for
one or more lines (the reserve is **not** attempted — availability is pre-checked
so no partial mutation occurs). The negative counterpart of `inventory.reserved`.
Added by TASK-MONO-196 (ADR-MONO-022 §D4) — the dedicated reservation-failure
signal that drives the outbound auto-backorder. It is **not** overloaded onto
`inventory.adjusted` (that topic is a stock-mutation event consumed cross-project
by scm `inventory-visibility-service`).

Topic: `wms.inventory.reserve.failed.v1`
`aggregateType`: `reservation`
`aggregateId`: `pickingRequestId` (no `Reservation` is created on failure)
Partition key: `pickingRequestId`

```json
"payload": {
  "pickingRequestId": "uuid",
  "reason": "INSUFFICIENT_STOCK",
  "insufficientLines": [
    {
      "inventoryId": "uuid",
      "skuId": "uuid",
      "lotId": "uuid-or-null",
      "locationId": "uuid",
      "qtyRequested": 1000,
      "qtyAvailable": 450
    }
  ]
}
```

Consumer expectations:

- `outbound-service` (`InventoryReserveFailedConsumer`): resolves `sagaId` from
  `pickingRequestId`, advances `OutboundSaga REQUESTED → RESERVE_FAILED`, sets
  `Order → BACKORDERED`, and emits `outbound.order.cancelled`
  (reason=`INSUFFICIENT_STOCK`) for the cross-project backorder signal.

### 5. `inventory.released`

Triggered when a reservation is released: cancellation, TTL expiry, or manual release.

Topic: `wms.inventory.released.v1`
`aggregateType`: `reservation`
`aggregateId`: `Reservation.id`

```json
"payload": {
  "reservationId": "uuid",
  "pickingRequestId": "uuid",
  "warehouseId": "uuid",
  "releasedReason": "CANCELLED",
  "lines": [
    {
      "reservationLineId": "uuid",
      "inventoryId": "uuid",
      "locationId": "uuid",
      "skuId": "uuid",
      "lotId": "uuid-or-null",
      "quantity": 5,
      "availableQtyAfter": 80,
      "reservedQtyAfter": 20
    }
  ],
  "releasedAt": "2026-04-20T12:00:00Z"
}
```

`releasedReason`: `CANCELLED | EXPIRED | MANUAL`

`EXPIRED` releases are initiated by the TTL scheduler job; `actorId` = `system:reservation-ttl-job`.

Consumer expectations:

- `outbound-service`: updates `OutboundSaga` → triggers cancellation path if reason is `CANCELLED`
  or `EXPIRED`; advances saga if part of planned release flow
- `admin-service`: updates `InventorySnapshot`, decrements active reservations

### 6. `inventory.confirmed`

Triggered when `outbound-service` calls `POST /reservations/{id}/confirm` after shipping
(W5 final decrement — stock consumed).

Topic: `wms.inventory.confirmed.v1`
`aggregateType`: `reservation`
`aggregateId`: `Reservation.id`

```json
"payload": {
  "reservationId": "uuid",
  "pickingRequestId": "uuid",
  "warehouseId": "uuid",
  "lines": [
    {
      "reservationLineId": "uuid",
      "inventoryId": "uuid",
      "locationId": "uuid",
      "skuId": "uuid",
      "lotId": "uuid-or-null",
      "quantity": 5,
      "reservedQtyAfter": 20
    }
  ],
  "confirmedAt": "2026-04-20T14:00:00Z"
}
```

Note: `availableQty` is not included here because confirmation only decrements `RESERVED`
— `AVAILABLE` is unchanged. The full current state is obtainable via `GET /inventory/{id}`.

Consumer expectations:

- `outbound-service`: advances `OutboundSaga` to `COMPLETED`
- `admin-service`: projects into `InventorySnapshot`, increments `ThroughputOutboundDaily`

### 7. `inventory.low-stock-detected`

Triggered when any mutation reduces `availableQty` below the warehouse-configured threshold.
Fired at most once per `(inventoryId, threshold-crossing)` event (no re-fire until stock
rises above threshold and drops again). Threshold configuration is in `admin-service` settings.

Topic: `wms.inventory.alert.v1`
`aggregateType`: `alert`
`aggregateId`: Inventory row id

```json
"payload": {
  "inventoryId": "uuid",
  "locationId": "uuid",
  "locationCode": "WH01-A-01-01-01",
  "skuId": "uuid",
  "skuCode": "SKU-APPLE-001",
  "lotId": "uuid-or-null",
  "availableQty": 5,
  "threshold": 10,
  "triggeringEventType": "inventory.reserved",
  "triggeringEventId": "uuid"
}
```

Consumer expectations:

- `notification-service`: sends low-stock alert to configured operators
- `admin-service`: projects into `AlertLog`
- `scm-platform demand-planning-service` (cross-project, ADR-MONO-027): consumes for replenishment reorder-suggestion decisioning. Subscription contract: scm `replenishment-subscriptions.md` (group `scm-demand-planning-v1`). No schema/payload change — read-only consumer.

---

## Consumed Events (cross-service, authoritative schema in publishing service)

`inventory-service` consumes the following events. The authoritative schema lives
in the publishing service's contract; shapes are reproduced here as the contract
`inventory-service` expects (a consumer-driven contract).

### C1. `inbound.putaway.completed`

Topic: `wms.inbound.putaway.completed.v1`
Authoritative schema: `specs/contracts/events/inbound-events.md`

Expected shape (what inventory-service parses):

```json
{
  "eventId": "uuid",
  "eventType": "inbound.putaway.completed",
  "payload": {
    "asnId": "uuid",
    "warehouseId": "uuid",
    "lines": [
      {
        "skuId": "uuid",
        "lotId": "uuid-or-null",
        "locationId": "uuid",
        "qtyReceived": 50
      }
    ]
  }
}
```

Effect: for each line, calls `ReceiveStockUseCase` → `Inventory.receive(qty)` at
`(locationId, skuId, lotId)`, creating the row if absent. Publishes `inventory.received`.

### C2. `outbound.picking.requested`  ⚠️ Cross-service contract (TASK-BE-431)

Topic: `wms.outbound.picking.requested.v1`
Authoritative schema: `specs/contracts/events/outbound-events.md` §3 (producer SoT — owned
jointly with this consumer).

**Authoritative consumed shape** (mirrors the producer byte-for-byte — the previous
`pickingRequestId` / per-line `inventoryId`+`quantity` description was stale and NPE'd at
the consumer; corrected by TASK-BE-431):

```json
{
  "eventId": "uuid",
  "eventType": "outbound.picking.requested",
  "payload": {
    "sagaId": "uuid",
    "reservationId": "uuid",
    "orderId": "uuid",
    "warehouseId": "uuid",
    "lines": [
      {
        "orderLineId": "uuid",
        "skuId": "uuid",
        "lotId": "uuid-or-null",
        "locationId": "uuid-or-null",
        "qtyToReserve": 5
      }
    ]
  }
}
```

| Field | Type | Nullable | Notes |
|---|---|---|---|
| `reservationId` | UUID | no | Equals outbound `PickingRequest.id`; used as inventory's `pickingRequestId` (1:1). Also the dedupe/idempotency key for the reservation aggregate |
| `warehouseId` | UUID | no | All lines belong to this warehouse |
| `lines[].skuId` | UUID | no | |
| `lines[].lotId` | UUID | yes | Null = any available lot (matches rows with `lot_id IS NULL`) |
| `lines[].locationId` | UUID | yes | **Null in v1** — the picking source location is bound later at picking, so the consumer resolves the row(s) by `(warehouseId, skuId, lotId)` |
| `lines[].qtyToReserve` | int | no | EA |

> ⚠️ The producer does **not** send `inventoryId`. It cannot know inventory-service's
> private `inventory` row PK — it only knows domain identity. The consumer
> (`PickingRequestedConsumer`) **resolves and allocates** the concrete row(s).

**Resolution + allocation policy** (`PickingRequestedConsumer.resolve`):

- Top-level: read `reservationId`; use it as the `pickingRequestId` for the
  `ReserveStockCommand` and the reservation aggregate.
- Per line `(skuId, lotId, locationId?, qtyToReserve)`:
  - `locationId` **present** → resolve the single row at `(locationId, skuId, lotId)` via
    `InventoryRepository.findByKey` (treated as no-candidate when its `available_qty == 0`).
  - `locationId` **null** (v1 norm) → resolve candidate rows for
    `(warehouseId, skuId, lotId)` with `available_qty > 0` via
    `InventoryRepository.findAvailableByWarehouseSkuLot`, ordered `available_qty DESC, id ASC`
    (deterministic). Allocate `qtyToReserve` **greatest-available first**, spanning multiple
    rows only when one row is insufficient — one `ReserveStockCommand.Line(inventoryId, qty)`
    per row drawn from.
- **Shortfall (incl. zero stock).** If any line's total available across resolved rows is
  `< qtyToReserve`, the **whole event** fails with no mutation: `inventory.reserve.failed`
  (§4a, reason `INSUFFICIENT_STOCK`) is emitted → outbound auto-backorder. When at least one
  row resolved, `ReserveStockService.doReserve`'s pre-check emits it; when **no** row resolved
  at all, `ReserveStockUseCase.signalReserveFailed` emits it directly from the natural-key
  identity (the failure line carries a `null` `inventoryId` hint). Either path: **no NPE, no
  DLT loop, the saga backorders.**

Effect: on full coverage, calls `ReserveStockUseCase.reserveForPickingEvent` → creates one
`Reservation` for all lines atomically and publishes `inventory.reserved`. Idempotent on
`eventId` (dedupe table) and on `reservationId` (existing reservation short-circuits).

### C3. `outbound.picking.cancelled`

Topic: `wms.outbound.picking.cancelled.v1`
Authoritative schema: `specs/contracts/events/outbound-events.md`

Expected shape:

```json
{
  "eventId": "uuid",
  "eventType": "outbound.picking.cancelled",
  "payload": {
    "pickingRequestId": "uuid",
    "warehouseId": "uuid"
  }
}
```

Effect: calls `ReleaseReservationUseCase` with `reason = CANCELLED`.
Publishes `inventory.released`.

### C4. `outbound.shipping.confirmed`  ⚠️ Cross-service contract (TASK-BE-437)

Topic: `wms.outbound.shipping.confirmed.v1`
Authoritative schema: `specs/contracts/events/outbound-events.md` §7 (producer SoT — owned
jointly with this consumer).

**Authoritative consumed shape** (mirrors the producer byte-for-byte — the previous
`pickingRequestId` / per-line `reservationLineId`+`shippedQuantity` description was stale and
NPE'd at the consumer on every real event; corrected by TASK-BE-437, the return-leg sibling of
the BE-431 forward-leg fix):

```json
{
  "eventId": "uuid",
  "eventType": "outbound.shipping.confirmed",
  "payload": {
    "sagaId": "uuid",
    "reservationId": "uuid",
    "orderId": "uuid",
    "orderNo": "ORD-...",
    "shipmentId": "uuid",
    "shipmentNo": "SHP-...",
    "warehouseId": "uuid",
    "shippedAt": "2026-04-29T15:00:00Z",
    "carrierCode": "CJ-LOGISTICS",
    "lines": [
      {
        "orderLineId": "uuid",
        "skuId": "uuid",
        "lotId": "uuid-or-null",
        "locationId": "uuid",
        "qtyConfirmed": 5
      }
    ]
  }
}
```

| Field | Type | Nullable | Notes |
|---|---|---|---|
| `reservationId` | UUID | no | Equals outbound `PickingRequest.id`; used as inventory's `pickingRequestId` (1:1) to find the reservation to confirm. The producer does **not** send `pickingRequestId` |
| `lines[].skuId` | UUID | no | Natural key (with `lotId`) the consumer maps to the owning `ReservationLine` to obtain its private `reservationLineId` — the producer cannot know inventory's row PK |
| `lines[].lotId` | UUID | yes | |
| `lines[].qtyConfirmed` | int | no | EA. The producer field is `qtyConfirmed`, **not** `shippedQuantity` |
| `orderNo` / `orderId` / `sagaId` / `shipment*` / `warehouseId` / `shippedAt` / `carrierCode` | — | — | Additive / unused by inventory; ignored |

> ⚠️ The producer carries domain identity only; the consumer
> (`ShippingConfirmedConsumer`) reads top-level `reservationId` as the `pickingRequestId`
> and **resolves each shipped line to its `ReservationLine` by `(skuId, lotId)`** to build the
> `ConfirmReservationCommand`. A line with no matching reservation line is a hard error (not a
> silent no-op).

Effect: calls `ConfirmReservationUseCase`. Each `qtyConfirmed` must equal `ReservationLine.quantity`
exactly (v1 no partial shipments). Publishes `inventory.confirmed`.

### C5. `master.location.*` and `master.sku.*`

Topics: `wms.master.location.v1`, `wms.master.sku.v1`, `wms.master.lot.v1`
Authoritative schema: `specs/contracts/events/master-events.md`

Effect: upsert the corresponding `LocationSnapshot`, `SkuSnapshot`, `LotSnapshot` in `MasterReadModel`.
Ignore events whose `master_version <= cachedVersion` (out-of-order handling).

Consumed actions:
- `master.location.created` / `.updated` → upsert LocationSnapshot
- `master.location.deactivated` → set LocationSnapshot.status = INACTIVE
- `master.location.reactivated` → set LocationSnapshot.status = ACTIVE
- `master.sku.created` / `.updated` → upsert SkuSnapshot
- `master.sku.deactivated` → set SkuSnapshot.status = INACTIVE
- `master.lot.created` / `.updated` → upsert LotSnapshot
- `master.lot.deactivated` → set LotSnapshot.status = INACTIVE
- `master.lot.expired` → set LotSnapshot.status = EXPIRED

---

## Schema Versioning

- `eventVersion` is monotonic per `eventType`.
- Additive changes (new optional field) stay on the same version.
- Breaking changes (renamed/removed field, type change) bump `eventVersion` AND
  publish on a new topic (e.g., `wms.inventory.reserved.v2`) with a coexistence period.
- Deprecation deadline, producer cut-over, and topic retirement are governed by
  `.claude/skills/cross-cutting/api-versioning/SKILL.md`.

---

## Consumer Contract

Every downstream consumer MUST:

1. Dedupe on `eventId` (T8) — use the `inventory_event_dedupe` table or Redis dedupe store
2. Tolerate at-least-once delivery
3. Treat `payload.inventory` / `payload.lines` as current state snapshots — never patch from
   partial field diffs
4. On unparseable event: move to `<topic>.DLT` and alert (`messaging/consumer-retry-dlq/SKILL.md`)
5. Not assume strict ordering across different `aggregateId` values

---

## Producer Guarantees (inventory-service)

- Exactly one outbox row per committed state change
- `eventId` generated at outbox write time and stable across retries
- `occurredAt` = DB transaction commit time
- `traceId` propagates from HTTP request or consumed Kafka event (OTel context propagation)
- Publisher retries on Kafka failure with exponential backoff; outbox row deleted only after
  broker ACK
- Publisher metrics: `inventory.outbox.pending.count`, `inventory.outbox.lag.seconds`,
  `inventory.outbox.publish.failure.total`

---

## Not In v1

- No command-shaped events (e.g., `inventory.reservation-requested`) — v1 only emits fact events
- No compaction-keyed topics — append-only with time retention
- No Avro / Protobuf encoding — v1 is JSON
- No `inventory.movement.recorded` event — movements are internal ledger rows; consumers get
  the aggregate-level events (`received`, `adjusted`, etc.) which carry enough state

---

## References

- `specs/services/inventory-service/architecture.md`
- `specs/services/inventory-service/domain-model.md`
- `specs/contracts/http/inventory-service-api.md`
- `specs/contracts/events/master-events.md` (consumed: master.*)
- `specs/contracts/events/inbound-events.md` (consumed: inbound.putaway.completed)
- `specs/contracts/events/outbound-events.md` (consumed: outbound.picking.*)
- `platform/event-driven-policy.md`
- `messaging/outbox-pattern/SKILL.md`
- `messaging/idempotent-consumer/SKILL.md`
- `rules/traits/transactional.md` (T3, T8)
- `rules/domains/wms.md` (W1, W2, W4, W5)
