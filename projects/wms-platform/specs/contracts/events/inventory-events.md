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
| `wms.inventory.alert.v1` | `inventory.low-stock-detected` | `locationId` |

- `v1` in the topic name: contract version. Breaking schema changes require a parallel `v2`
  topic with coexistence period (per `cross-cutting/api-versioning.md`).
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

### C2. `outbound.picking.requested`

Topic: `wms.outbound.picking.requested.v1`
Authoritative schema: `specs/contracts/events/outbound-events.md`

Expected shape:

```json
{
  "eventId": "uuid",
  "eventType": "outbound.picking.requested",
  "payload": {
    "pickingRequestId": "uuid",
    "warehouseId": "uuid",
    "lines": [
      {
        "locationId": "uuid",
        "skuId": "uuid",
        "lotId": "uuid-or-null",
        "quantity": 5
      }
    ],
    "ttlSeconds": 86400
  }
}
```

Effect: calls `ReserveStockUseCase` which creates a `Reservation` for all lines atomically.
Publishes `inventory.reserved`. On failure, the event goes to DLT; outbound-service saga
sweeper re-emits if no `inventory.reserved` received within 5 minutes.

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

### C4. `outbound.shipping.confirmed`

Topic: `wms.outbound.shipping.confirmed.v1`
Authoritative schema: `specs/contracts/events/outbound-events.md`

Expected shape:

```json
{
  "eventId": "uuid",
  "eventType": "outbound.shipping.confirmed",
  "payload": {
    "pickingRequestId": "uuid",
    "warehouseId": "uuid",
    "lines": [
      {
        "reservationLineId": "uuid",
        "shippedQuantity": 5
      }
    ]
  }
}
```

Effect: calls `ConfirmReservationUseCase`. Each `shippedQuantity` must equal `ReservationLine.quantity`
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
  `cross-cutting/api-versioning.md`.

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
- `specs/contracts/events/inbound-events.md` (consumed: inbound.putaway.completed — Open Item)
- `specs/contracts/events/outbound-events.md` (consumed: outbound.picking.* — Open Item)
- `platform/event-driven-policy.md`
- `messaging/outbox-pattern/SKILL.md`
- `messaging/idempotent-consumer/SKILL.md`
- `rules/traits/transactional.md` (T3, T8)
- `rules/domains/wms.md` (W1, W2, W4, W5)
