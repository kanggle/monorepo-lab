# Event Contract — outbound-service Domain Events

Authoritative schemas for events **published** by `outbound-service`, and the
expected shapes of events **consumed** from `master-service` and
`inventory-service` for saga coordination and snapshot refresh.

Downstream consumers (`inventory-service`, `admin-service`) rely on the
published schemas. Changes here precede code changes (per `CLAUDE.md`
Contract Rule).

---

## Delivery Semantics

- **Publisher side**: transactional outbox (trait `transactional` T3). State
  change and outbox row written in the same DB transaction; a separate
  publisher process forwards outbox rows to Kafka. Guarantees **at-least-once**
  delivery.
- **Partition key**: `sagaId` for saga-scoped events (ensures ordered delivery
  within a saga, which is the coordination unit for `inventory-service`
  consumers); `orderId` for order-lifecycle-only events without a saga context.
  See per-event notes below for the exact key used.
- **Consumer side**: must implement **idempotent handling** keyed by `eventId`
  (trait `transactional` T8). See
  `specs/services/outbound-service/idempotency.md`.
- **No cross-topic ordering**. A consumer correlating
  `outbound.picking.requested` with `outbound.shipping.confirmed` must use
  `sagaId`, not arrival order.

---

## Global Envelope

Every event shares this outer envelope. The `payload` field carries
event-specific data.

```json
{
  "eventId": "0191d8f0-1f0e-7c40-9d13-4a2c9e3f5678",
  "eventType": "outbound.picking.requested",
  "eventVersion": 1,
  "occurredAt": "2026-04-29T10:00:00.123Z",
  "producer": "outbound-service",
  "aggregateType": "outbound_saga",
  "aggregateId": "uuid-of-saga",
  "traceId": "abc-456",
  "actorId": "user-uuid-or-system:erp-webhook",
  "payload": { /* event-specific */ }
}
```

| Field | Type | Notes |
|---|---|---|
| `eventId` | UUIDv7 string | Unique per event. Consumers dedupe on this (T8) |
| `eventType` | string | `outbound.<aggregate>.<action>` |
| `eventVersion` | int | Schema version for that `eventType`. v1 is the baseline |
| `occurredAt` | ISO-8601 UTC, ms precision | DB transaction commit time |
| `producer` | string | Always `outbound-service` |
| `aggregateType` | string | `order` \| `outbound_saga` \| `shipment` |
| `aggregateId` | UUID string | Aggregate root id — varies per event type; see each event |
| `traceId` | string | OTel trace id; propagated from REST request, webhook, or consumed Kafka event |
| `actorId` | string or null | JWT subject for REST-driven; `system:erp-webhook` for webhook-origin; `system:saga-sweeper` for sweeper-re-emitted events |
| `payload` | object | Defined per `eventType` below |

Serialization: JSON. Future Avro/Protobuf migration possible but not v1.

---

## Topic Layout

| Topic | Event type(s) | Partition key |
|---|---|---|
| `wms.outbound.order.received.v1` | `outbound.order.received` | `orderId` |
| `wms.outbound.order.cancelled.v1` | `outbound.order.cancelled` | `orderId` |
| `wms.outbound.picking.requested.v1` | `outbound.picking.requested` | `sagaId` |
| `wms.outbound.picking.cancelled.v1` | `outbound.picking.cancelled` | `sagaId` |
| `wms.outbound.picking.completed.v1` | `outbound.picking.completed` | `sagaId` |
| `wms.outbound.packing.completed.v1` | `outbound.packing.completed` | `orderId` |
| `wms.outbound.shipping.confirmed.v1` | `outbound.shipping.confirmed` | `sagaId` |

- `v1` in topic name: contract version. Breaking schema changes require a
  parallel `v2` topic with coexistence period (per
  `cross-cutting/api-versioning.md`).
- Retention: minimum 7 days. 30-day preferred for DLQ replay windows.
- Partitions: start with 6 per topic (outbound throughput and saga
  fan-out demand higher partition count than inbound).
- Dead-letter topic: `<topic>.DLT` — for consumers, not the producer.
- Cross-service contract events (`outbound.picking.requested`,
  `outbound.picking.cancelled`, `outbound.shipping.confirmed`) are marked
  with `⚠️` and require coordinated schema migration with `inventory-service`.

---

## Published Events (outbound-service → consumers)

### 1. `outbound.order.received`

Triggered when a new outbound order is created — either via the ERP webhook
background processor or a manual `POST /api/v1/outbound/orders`. Published
in the same TX as order creation.

Topic: `wms.outbound.order.received.v1`
Partition key: `orderId`
`aggregateType`: `order`
`aggregateId`: `Order.id`

```json
"payload": {
  "orderId": "uuid",
  "orderNo": "ORD-20260429-0001",
  "source": "WEBHOOK_ERP",
  "customerPartnerId": "uuid",
  "customerPartnerCode": "CUST-001",
  "warehouseId": "uuid",
  "requiredShipDate": "2026-05-02",
  "lines": [
    {
      "orderLineId": "uuid",
      "lineNo": 1,
      "skuId": "uuid",
      "skuCode": "SKU-APPLE-001",
      "lotId": "uuid-or-null",
      "qtyOrdered": 1000
    }
  ]
}
```

| Field | Type | Nullable | Notes |
|---|---|---|---|
| `orderId` | UUID | no | |
| `orderNo` | string | no | Business identifier |
| `source` | string | no | `MANUAL` \| `WEBHOOK_ERP` |
| `customerPartnerId` | UUID | no | |
| `customerPartnerCode` | string | no | Denormalized from MasterReadModel for admin dashboards |
| `warehouseId` | UUID | no | |
| `requiredShipDate` | string (YYYY-MM-DD) | yes | |
| `lines[].orderLineId` | UUID | no | |
| `lines[].lineNo` | int | no | |
| `lines[].skuId` | UUID | no | |
| `lines[].skuCode` | string | no | Denormalized |
| `lines[].lotId` | UUID | yes | Null if any-lot or no lot required |
| `lines[].qtyOrdered` | int | no | EA |

Consumer expectations:

- `admin-service`: projects into `OutboundDashboard` and `OrderAuditLog`
- `notification-service`: optional alert on high-value or time-sensitive orders

### 2. `outbound.order.cancelled`

Triggered when an order is cancelled. Allowed from `PICKING`, `PICKED`,
`PACKING`, or `PACKED` status. Post-`SHIPPED` cancellation is forbidden in v1.

Topic: `wms.outbound.order.cancelled.v1`
Partition key: `orderId`
`aggregateType`: `order`
`aggregateId`: `Order.id`

```json
"payload": {
  "orderId": "uuid",
  "orderNo": "ORD-20260429-0001",
  "previousStatus": "PICKING",
  "reason": "고객 주문 취소 요청",
  "cancelledAt": "2026-04-29T11:30:00Z"
}
```

| Field | Type | Nullable | Notes |
|---|---|---|---|
| `orderId` | UUID | no | |
| `orderNo` | string | no | |
| `previousStatus` | string | no | `PICKING` \| `PICKED` \| `PACKING` \| `PACKED` |
| `reason` | string | no | |
| `cancelledAt` | ISO-8601 UTC | no | |

Consumer expectations:

- `admin-service`: updates `OutboundDashboard`, removes order from active queue.
  If `previousStatus ∈ {PICKED, PACKING, PACKED}`, flags as ops-waste event
  (physical work was done).
- `notification-service`: optional ops alert for post-pick cancellations

### 3. `outbound.picking.requested`  ⚠️ Cross-service contract

Triggered atomically with order creation or explicit picking-request creation.
This is **Saga Step 1** — the event that instructs `inventory-service` to
reserve stock for the outbound order.

Topic: `wms.outbound.picking.requested.v1`
Partition key: `sagaId`
`aggregateType`: `outbound_saga`
`aggregateId`: `OutboundSaga.saga_id`

```json
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
      "locationId": "uuid",
      "qtyToReserve": 1000
    }
  ]
}
```

| Field | Type | Nullable | Notes |
|---|---|---|---|
| `sagaId` | UUID | no | `OutboundSaga.saga_id`. Partition key. Inventory replies echo this for correlation |
| `reservationId` | UUID | no | Equals `PickingRequest.id`. This is the id inventory uses for its reservation record |
| `orderId` | UUID | no | |
| `warehouseId` | UUID | no | All lines belong to this warehouse |
| `lines[].orderLineId` | UUID | no | |
| `lines[].skuId` | UUID | no | |
| `lines[].lotId` | UUID | yes | Null if operator picks any available lot |
| `lines[].locationId` | UUID | no | Assigned picking source location (`PickingPlanner` domain service result) |
| `lines[].qtyToReserve` | int | no | EA; equals `order_line.qty_ordered` in v1 |

> **⚠️ Authoritative cross-service contract.** This event is consumed by
> `inventory-service` (`PickingRequestedConsumer`) to execute `ReserveStockUseCase`.
> Inventory replies with `inventory.reserved` (success) or emits
> `inventory.adjusted` with reason `INSUFFICIENT_STOCK` (failure). Topic name,
> `sagaId`, `reservationId`, and `lines` shape are jointly owned with
> `inventory-service`; any change requires a coordinated migration.
>
> The `reservationId` becomes the foreign-key anchor between outbound and
> inventory reservation tables.

Consumer expectations:

- `inventory-service` (`PickingRequestedConsumer`): for each line, calls
  `ReserveStockUseCase` at `(locationId, skuId, lotId, qtyToReserve)`.
  On success publishes `inventory.reserved` with `reservationId` and `sagaId`.
  On failure emits `inventory.adjusted` reason=`INSUFFICIENT_STOCK`.
- `admin-service`: updates saga step view on operator dashboard

### 4. `outbound.picking.cancelled`  ⚠️ Cross-service contract

**Compensation event.** Triggered when an order is cancelled while the saga
holds an active reservation (saga state `RESERVED`, `PICKING_CONFIRMED`, or
`PACKING_CONFIRMED`). May also be re-emitted by the saga sweeper if the saga
is stuck in `CANCELLATION_REQUESTED` for > 5 minutes.

Topic: `wms.outbound.picking.cancelled.v1`
Partition key: `sagaId`
`aggregateType`: `outbound_saga`
`aggregateId`: `OutboundSaga.saga_id`

```json
"payload": {
  "sagaId": "uuid",
  "reservationId": "uuid",
  "orderId": "uuid",
  "reason": "고객 주문 취소 요청"
}
```

| Field | Type | Nullable | Notes |
|---|---|---|---|
| `sagaId` | UUID | no | Partition key; inventory uses to locate and release the reservation |
| `reservationId` | UUID | no | Equals `PickingRequest.id`; inventory matches on this |
| `orderId` | UUID | no | |
| `reason` | string | yes | Cancellation reason from `Order.cancel()` |

> **⚠️ Authoritative cross-service contract.** This event is the **only**
> signal by which `inventory-service` releases an outbound reservation.
> `inventory-service` consumes this event to call `ReleaseReservationUseCase`.
> After release, inventory replies with `inventory.released`, which advances
> the saga to `CANCELLED`. Compensation guarantee: this event is fired
> **exactly once per cancelled saga** (sweeper re-emission is idempotent at
> the inventory consumer via T8 eventId dedupe).

Consumer expectations:

- `inventory-service` (`PickingCancelledConsumer`): calls
  `ReleaseReservationUseCase` for `reservationId`. Publishes `inventory.released`
  with `reservationId` and `sagaId` on success.
- `admin-service`: updates order view (cancellation in progress)

### 5. `outbound.picking.completed`

Triggered when the operator confirms all physical picks for the order
(`ConfirmPickingUseCase`). At this point the saga advances to
`PICKING_CONFIRMED` and the order to `PICKED`.

Topic: `wms.outbound.picking.completed.v1`
Partition key: `sagaId`
`aggregateType`: `outbound_saga`
`aggregateId`: `OutboundSaga.saga_id`

```json
"payload": {
  "sagaId": "uuid",
  "orderId": "uuid",
  "pickingConfirmationId": "uuid",
  "confirmedBy": "user-uuid",
  "confirmedAt": "2026-04-29T12:00:00Z",
  "lines": [
    {
      "orderLineId": "uuid",
      "skuId": "uuid",
      "lotId": "uuid-or-null",
      "actualLocationId": "uuid",
      "qtyConfirmed": 1000
    }
  ]
}
```

| Field | Type | Nullable | Notes |
|---|---|---|---|
| `sagaId` | UUID | no | |
| `orderId` | UUID | no | |
| `pickingConfirmationId` | UUID | no | `PickingConfirmation.id` |
| `confirmedBy` | string | no | Actor id of warehouse operator |
| `confirmedAt` | ISO-8601 UTC | no | |
| `lines[].orderLineId` | UUID | no | |
| `lines[].skuId` | UUID | no | |
| `lines[].lotId` | UUID | yes | Actual lot picked; may differ from planned if operator substituted |
| `lines[].actualLocationId` | UUID | no | Where goods were actually picked from |
| `lines[].qtyConfirmed` | int | no | EA |

Consumer expectations:

- `admin-service`: updates operator task board (picking complete); projects
  per-order picking accuracy metrics
- `notification-service`: optional alert on lot substitutions
  (`lines[].lotId` differs from order line's `lotId`)

### 6. `outbound.packing.completed`

Triggered when all `PackingUnit` records for the order are `SEALED` and the
sum of `PackingUnitLine.qty` equals all order line quantities. Order
transitions to `PACKED`, saga to `PACKING_CONFIRMED`.

Topic: `wms.outbound.packing.completed.v1`
Partition key: `orderId`
`aggregateType`: `order`
`aggregateId`: `Order.id`

```json
"payload": {
  "orderId": "uuid",
  "orderNo": "ORD-20260429-0001",
  "warehouseId": "uuid",
  "completedAt": "2026-04-29T14:00:00Z",
  "packingUnits": [
    {
      "packingUnitId": "uuid",
      "cartonNo": "BOX-001",
      "packingType": "BOX",
      "weightGrams": 2500,
      "lengthMm": 400,
      "widthMm": 300,
      "heightMm": 200,
      "lines": [
        {
          "orderLineId": "uuid",
          "skuId": "uuid",
          "lotId": "uuid-or-null",
          "qty": 1000
        }
      ]
    }
  ],
  "totalCartonCount": 1,
  "totalWeightGrams": 2500
}
```

| Field | Type | Nullable | Notes |
|---|---|---|---|
| `orderId` | UUID | no | |
| `orderNo` | string | no | |
| `warehouseId` | UUID | no | |
| `completedAt` | ISO-8601 UTC | no | |
| `packingUnits[].packingUnitId` | UUID | no | |
| `packingUnits[].cartonNo` | string | no | |
| `packingUnits[].packingType` | string | no | `BOX` \| `PALLET` \| `ENVELOPE` |
| `packingUnits[].weightGrams` | int | yes | Null if operator did not record weight |
| `packingUnits[].lengthMm` | int | yes | |
| `packingUnits[].widthMm` | int | yes | |
| `packingUnits[].heightMm` | int | yes | |
| `packingUnits[].lines[].orderLineId` | UUID | no | |
| `packingUnits[].lines[].skuId` | UUID | no | |
| `packingUnits[].lines[].lotId` | UUID | yes | |
| `packingUnits[].lines[].qty` | int | no | EA packed in this unit |
| `totalCartonCount` | int | no | Count of PackingUnits |
| `totalWeightGrams` | int | yes | Sum of `weightGrams`; null if any unit missing weight |

Consumer expectations:

- `admin-service`: updates order dashboard; makes packing summary available
  for the TMS handover view
- `notification-service`: optional alert indicating order is ready to ship

### 7. `outbound.shipping.confirmed`  ⚠️ Cross-service contract

Triggered when `ConfirmShippingUseCase` executes on a `PACKED` order. This
is **Saga Step 4** — the event that instructs `inventory-service` to consume
(deduct) the reserved stock. Once this event is published, the saga is in
`SHIPPED` state and cannot be rolled back in v1.

Topic: `wms.outbound.shipping.confirmed.v1`
Partition key: `sagaId`
`aggregateType`: `shipment`
`aggregateId`: `Shipment.id`

```json
"payload": {
  "sagaId": "uuid",
  "reservationId": "uuid",
  "orderId": "uuid",
  "shipmentId": "uuid",
  "shipmentNo": "SHP-20260429-0001",
  "warehouseId": "uuid",
  "shippedAt": "2026-04-29T15:00:00Z",
  "carrierCode": "CJ-LOGISTICS",
  "lines": [
    {
      "orderLineId": "uuid",
      "skuId": "uuid",
      "lotId": "uuid-or-null",
      "locationId": "uuid",
      "qtyConfirmed": 1000
    }
  ]
}
```

| Field | Type | Nullable | Notes |
|---|---|---|---|
| `sagaId` | UUID | no | Partition key; inventory uses to complete the saga step |
| `reservationId` | UUID | no | Equals `PickingRequest.id`; inventory matches on this to find the reservation to confirm |
| `orderId` | UUID | no | |
| `shipmentId` | UUID | no | `Shipment.id` |
| `shipmentNo` | string | no | Business identifier for the shipment |
| `warehouseId` | UUID | no | |
| `shippedAt` | ISO-8601 UTC | no | Wall-clock time at `ConfirmShippingUseCase` execution |
| `carrierCode` | string | yes | May be null if carrier was not specified at shipping time |
| `lines[].orderLineId` | UUID | no | |
| `lines[].skuId` | UUID | no | |
| `lines[].lotId` | UUID | yes | Actual lot that was shipped (from `PickingConfirmation`) |
| `lines[].locationId` | UUID | no | Source location that was picked (from `PickingConfirmationLine.actual_location_id`) |
| `lines[].qtyConfirmed` | int | no | EA; equals `order_line.qty_ordered` in v1 |

> **⚠️ Authoritative cross-service contract.** This event is consumed by
> `inventory-service` (`ShippingConfirmedConsumer`) to call
> `ConfirmReservationUseCase`. Inventory deducts `qtyConfirmed` from the
> reserved-on-hold quantity and marks the reservation as consumed. Inventory
> replies with `inventory.confirmed` (carrying `sagaId` and `reservationId`),
> which advances the saga to `COMPLETED`.
>
> This event is also re-emitted by the saga sweeper if the saga is stuck in
> `SHIPPED` for > 5 minutes (idempotent at the inventory consumer via T8).
>
> Once this event is published, **the saga is terminal from the outbound side**
> (stock depletion is irrevocable in v1). TMS failure after this point results
> in `SHIPPED_NOT_NOTIFIED` alert but does not affect inventory.

Consumer expectations:

- `inventory-service` (`ShippingConfirmedConsumer`): calls
  `ConfirmReservationUseCase` per line, consuming reserved quantities. Publishes
  `inventory.confirmed` with `reservationId` and `sagaId`.
- `admin-service`: marks order as shipped in dashboard; computes order
  fulfillment cycle-time metric

---

## Consumed Events (cross-service, authoritative schema in publishing service)

`outbound-service` consumes the following events. The authoritative schema
lives in the publishing service's contract; shapes are reproduced here as the
contract `outbound-service` expects.

### C1. Saga Reply Events from `inventory-service`

Topics: `wms.inventory.reserved.v1`, `wms.inventory.released.v1`,
`wms.inventory.confirmed.v1`, `wms.inventory.adjusted.v1`
Authoritative schema: `specs/contracts/events/inventory-events.md`
Consumer group: `outbound-service`
Partition key: `sagaId` (set by inventory as the Kafka partition key on reply
events — ensures ordered delivery within a saga)

| Consumed event | Saga effect | Handler |
|---|---|---|
| `inventory.reserved` | Saga `REQUESTED → RESERVED`; `Order` stays in `PICKING` | `InventoryReservedConsumer` |
| `inventory.released` | Saga `CANCELLATION_REQUESTED → CANCELLED` | `InventoryReleasedConsumer` |
| `inventory.confirmed` | Saga `SHIPPED → COMPLETED` | `InventoryConfirmedConsumer` |
| `inventory.adjusted` (reason=`INSUFFICIENT_STOCK`) | Saga `REQUESTED → RESERVE_FAILED`; `Order → BACKORDERED` | `InventoryReservedConsumer` (negative branch) |

Expected payload shape `outbound-service` reads from each reply:

**`inventory.reserved` payload** (fields outbound reads):

```json
{
  "sagaId": "uuid",
  "reservationId": "uuid",
  "warehouseId": "uuid"
}
```

**`inventory.released` payload** (fields outbound reads):

```json
{
  "sagaId": "uuid",
  "reservationId": "uuid"
}
```

**`inventory.confirmed` payload** (fields outbound reads):

```json
{
  "sagaId": "uuid",
  "reservationId": "uuid"
}
```

**`inventory.adjusted` payload** (fields outbound reads when reason=`INSUFFICIENT_STOCK`):

```json
{
  "sagaId": "uuid",
  "reservationId": "uuid",
  "reason": "INSUFFICIENT_STOCK",
  "insufficientLines": [
    {
      "skuId": "uuid",
      "lotId": "uuid-or-null",
      "locationId": "uuid",
      "qtyRequested": 1000,
      "qtyAvailable": 450
    }
  ]
}
```

`insufficientLines` is stored in `OutboundSaga.failure_reason` (serialized)
for ops visibility.

Dedupe: `outbound_event_dedupe` table — 30-day retention.
Saga-level idempotency: re-delivered `inventory.reserved` to an already-`RESERVED`
saga is a silent no-op (state machine rejects the transition).

### C2. Master Snapshot Events from `master-service`

Topics: `wms.master.warehouse.v1`, `wms.master.zone.v1`,
`wms.master.location.v1`, `wms.master.sku.v1`, `wms.master.lot.v1`,
`wms.master.partner.v1`
Authoritative schema: `specs/contracts/events/master-events.md`

Effect: upsert the corresponding snapshot in `MasterReadModel`. Ignore events
whose `master_version <= cachedVersion` (out-of-order handling).

| Consumed action | Effect |
|---|---|
| `*.created` / `*.updated` | Upsert snapshot, set `status = ACTIVE` |
| `*.deactivated` | Set snapshot `status = INACTIVE` |
| `*.reactivated` | Set snapshot `status = ACTIVE` |
| `master.lot.expired` | Set `LotSnapshot.status = EXPIRED` |
| `master.partner.deactivated` | Set `PartnerSnapshot.status = INACTIVE`; deactivated customer partners rejected on new order creation |

`outbound-service` only **reads** master snapshots from these tables. It does
not publish anything in response to consuming a master event.

---

## Schema Versioning

- `eventVersion` is monotonic per `eventType`.
- Additive changes (new optional field) stay on the same version.
- Breaking changes (renamed/removed field, type change) bump `eventVersion`
  AND publish on a new topic (e.g., `wms.outbound.picking.requested.v2`) with
  a coexistence period.
- Cross-service contract events (`outbound.picking.requested`,
  `outbound.picking.cancelled`, `outbound.shipping.confirmed`) require
  `inventory-service` to be ready for the new topic before producer cut-over.
- Deprecation deadline, producer cut-over, and topic retirement are governed
  by `cross-cutting/api-versioning.md`.

---

## Consumer Contract

Every downstream consumer MUST:

1. Dedupe on `eventId` (T8) — use the consumer's local dedupe table.
2. Tolerate at-least-once delivery.
3. Treat `payload.lines` as a current-state snapshot — never patch from partial
   field diffs.
4. On unparseable event: move to `<topic>.DLT` and alert.
5. Not assume strict ordering across different `sagaId` / `orderId` values.
6. For `outbound.picking.requested`: treat `reservationId` as the external
   key linking to the inventory reservation. Echo both `sagaId` and
   `reservationId` in reply events.
7. For `outbound.shipping.confirmed`: `lines[].qtyConfirmed` is the
   authoritative quantity to deduct. Do not cross-reference against the
   original reservation lines — use the confirmation payload as-is.

---

## Producer Guarantees (outbound-service)

- Exactly one outbox row per committed state change.
- `eventId` generated at outbox write time and stable across retries
  (sweeper re-emits the same outbox row, not a new one).
- `occurredAt` = DB transaction commit time.
- `traceId` propagates from HTTP request, webhook ingest, or consumed Kafka
  event (OTel context propagation).
- Publisher retries on Kafka failure with exponential backoff; outbox row
  marked `published_at` only after broker ACK.
- Publisher metrics:
  - `outbound.outbox.pending.count`
  - `outbound.outbox.lag.seconds`
  - `outbound.outbox.publish.failure.total`

---

## Saga Sweeper Re-emission

The saga sweeper (60-second background job) re-emits outbox events for sagas
stuck in transitional states. Re-emitted events carry the **original** `eventId`
from the outbox row, making re-emission fully idempotent at the consumer.

| Stuck saga state | Threshold | Event re-emitted |
|---|---|---|
| `REQUESTED` | > 5 min | `outbound.picking.requested` |
| `CANCELLATION_REQUESTED` | > 5 min | `outbound.picking.cancelled` |
| `SHIPPED` | > 5 min | `outbound.shipping.confirmed` |

`actorId` in the re-emitted envelope: `system:saga-sweeper`.

---

## Not In v1

- No command-shaped events (e.g., `outbound.order.cancellation-requested`) —
  v1 emits fact events only.
- No compaction-keyed topics — append-only with time retention.
- No Avro / Protobuf encoding — v1 is JSON.
- No `outbound.packing.unit.created` or `outbound.packing.unit.sealed` events
  — packing unit lifecycle is internal; downstream cares only about
  `outbound.packing.completed`.
- No `outbound.order.backordered` separate event — the backordered state is
  implicit from the absence of `outbound.picking.completed` / `order.cancelled`
  and the presence of `RESERVE_FAILED` in the saga state (visible via
  `GET /orders/{id}/saga`). Admin-service reads saga state via REST polling or
  via the `outbound.picking.requested` topic's `RESERVE_FAILED` saga transition
  signal.
- No wave/batch aggregation event (Wave aggregate is v2).
- No returns / RMA event (v2).

---

## References

- `specs/services/outbound-service/architecture.md`
- `specs/services/outbound-service/domain-model.md`
- `specs/services/outbound-service/sagas/outbound-saga.md` (Open Item)
- `specs/services/outbound-service/state-machines/saga-status.md` (Open Item)
- `specs/contracts/http/outbound-service-api.md` — REST endpoints
- `specs/contracts/webhooks/erp-order-webhook.md` — webhook contract
- `specs/contracts/events/inbound-events.md` — sibling event contract (pattern reference)
- `specs/contracts/events/inventory-events.md` — consumed events (authoritative in inventory-service)
- `specs/contracts/events/master-events.md` — consumed events (authoritative in master-service)
- `platform/event-driven-policy.md`
- `rules/traits/transactional.md` — T3 (outbox), T8 (eventId dedupe)
- `rules/domains/wms.md` — Outbound bounded context, W4, W5
