# outbound-service — Outbound Workflow

End-to-end outbound lifecycle narrative. Required artifact per
`rules/domains/wms.md` § Required Artifacts (4) "출고 워크플로".

This document complements
[`architecture.md`](../architecture.md),
[`state-machines/order-status.md`](../state-machines/order-status.md), and
[`state-machines/saga-status.md`](../state-machines/saga-status.md) by
walking the **happy path** and the most common deviations through every
service involved (master, gateway, outbound, inventory, admin, TMS,
notification).

The outbound flow differs from inbound in one crucial way: it is **a
choreographed saga** spanning `outbound-service` and `inventory-service`.
This document narrates the saga from outbound's point of view; the
inventory-side handlers are described in
[`specs/services/inventory-service/architecture.md`](../../inventory-service/architecture.md).

---

## Actors

| Actor | Role | Identifier in events |
|---|---|---|
| **ERP** | External — emits order webhooks | `system:erp-webhook` |
| **Operator** | Human — manual order entry, picking confirmation, packing, shipping confirmation | JWT `sub` (UUID) |
| **outbound-service** | This service | `outbound-service` (event `producer`) |
| **inventory-service** | Reserves / releases / confirms stock per saga step | `inventory-service` |
| **master-service** | Authoritative source for Partner / SKU / Lot / Location | `master-service` |
| **TMS** | External — receives shipment-ready notification | `system:tms` (consumer of our outbound HTTP) |
| **admin-service** | Read-only KPI projections | `admin-service` |
| **notification-service** | Optional alerting on failures (RESERVE_FAILED, SHIPPED_NOT_NOTIFIED) | `notification-service` |

---

## Phase 1 — Order Reception (Atomic Saga Start)

Two entry paths produce an Order in `RECEIVED` status, which **immediately**
transitions to `PICKING` within the same use-case as `OutboundSaga` is
created in `REQUESTED` state. Both paths terminate in
`ReceiveOrderUseCase`, the unifying entry point.

### 1a. ERP Webhook Path

```
ERP                gateway-service          outbound-service           Postgres
 │                       │                        │                        │
 │── POST /webhooks/erp/order ───────────────────▶│                        │
 │   (signed payload)    │ (HMAC-only route,      │                        │
 │                       │  no JWT, no rate-limit)│                        │
 │                       │                        │── verify HMAC ────────▶│
 │                       │                        │── verify timestamp ──▶ │
 │                       │                        │── insert erp_order_webhook_dedupe (event_id)
 │                       │                        │── insert erp_order_webhook_inbox (raw_payload, status=PENDING)
 │                       │                        │                        │
 │◀── 200 {accepted, eventId} ────────────────────│                        │
 │                       │                        │                        │

       (background processor, every 1s)
                                 │
                                 │── SELECT * FROM erp_order_webhook_inbox WHERE status=PENDING LIMIT 50
                                 │── for each: ReceiveOrderUseCase  (atomic — see §1c)
                                 │      ├─ resolve customer_partner from MasterReadModel
                                 │      ├─ create Order (status=RECEIVED → startPicking → PICKING) + OrderLines
                                 │      ├─ create OutboundSaga (state=REQUESTED)
                                 │      ├─ create PickingRequest + PickingRequestLines (PickingPlanner assigns location_id)
                                 │      ├─ write outbox: outbound.order.received
                                 │      ├─ write outbox: outbound.picking.requested
                                 │      └─ update erp_order_webhook_inbox.status=APPLIED
                                 ▼
                          Kafka: wms.outbound.order.received.v1
                          Kafka: wms.outbound.picking.requested.v1   (consumed by inventory-service)
```

**Why ack-then-process**: ERP HTTP timeout is short (~10s) and ERP
retries aggressively on timeout. Persisting raw payload + acking
immediately decouples ingestion latency from domain validation. If domain
validation fails (partner inactive, SKU inactive, warehouse mismatch), the
row moves to `status=FAILED` with `failure_reason` populated; ops sees it
on the admin dashboard.

### 1b. Manual Path

```
Operator             gateway-service          outbound-service          Postgres
 │                       │                        │                        │
 │── POST /api/v1/outbound/orders ────────────────▶│                        │
 │   (Bearer JWT, Idempotency-Key)                │                        │
 │                       │── JWT decode ─────────▶│                        │
 │                       │── X-User-Id, X-User-Role headers                │
 │                       │                        │── ReceiveOrderUseCase  │
 │                       │                        │      ├─ idempotency check (Redis)
 │                       │                        │      ├─ validate customer (MasterReadModel)
 │                       │                        │      │     - ACTIVE + partner_type ∈ {CUSTOMER, BOTH}
 │                       │                        │      │       else PARTNER_INVALID_TYPE (422)
 │                       │                        │      ├─ validate SKU(s) ACTIVE (MasterReadModel)
 │                       │                        │      │     else SKU_INACTIVE (422)
 │                       │                        │      ├─ validate same warehouse_id across all lines
 │                       │                        │      │     else WAREHOUSE_MISMATCH (422)
 │                       │                        │      ├─ validate LOT_REQUIRED for LOT-tracked SKUs with explicit lotId
 │                       │                        │      ├─ atomic write (see §1c)
 │◀── 201 {orderId, orderNo, status=PICKING, sagaId} ─│                    │
 │                       │                        │                        │

                          Kafka: wms.outbound.order.received.v1
                          Kafka: wms.outbound.picking.requested.v1
```

**Differences from webhook path**:

- Synchronous validation: invalid input returns 422 immediately to the
  operator.
- No `erp_order_webhook_inbox` row written (no separation of ingest vs.
  domain).
- `actorId = JWT.sub` (operator id), `source = MANUAL`.
- The response carries the `sagaId` so the caller can later correlate with
  saga-related queries.

### 1c. Atomicity (single `@Transactional`)

`ReceiveOrderUseCase` performs **all** of the following inside one
`@Transactional` boundary, and either all succeed or all roll back:

1. `Order` row inserted (status=`RECEIVED` initially).
2. `OrderLine` rows inserted.
3. `Order.startPicking()` invoked → status flipped to `PICKING` (T4: only
   via domain method).
4. `PickingPlanner` (domain service) computes `location_id` per line by
   consulting `MasterReadModel.LocationSnapshot` (v1: deterministic
   first-active-location-with-stock heuristic; per-line allocation
   delegated to inventory in v2).
5. `PickingRequest` + `PickingRequestLine` rows inserted.
6. `OutboundSaga` row inserted (state=`REQUESTED`, version=0).
7. Two outbox rows inserted:
   - `outbound.order.received` (partition_key=`order_id`)
   - `outbound.picking.requested` (partition_key=`saga_id`)

After commit, the outbox publisher picks up both rows and emits to Kafka.
inventory-service consumes `outbound.picking.requested` and begins the
reserve operation.

**Single TX** is the only way to satisfy T7 (saga creation atomic with
first event emission) and T3 (outbox in same TX as state change).

### 1d. Saga State After Phase 1

| Aggregate | State |
|---|---|
| Order | PICKING |
| OutboundSaga | REQUESTED |
| PickingRequest | PENDING |

---

## Phase 2 — Inventory Reserve (Async, Choreographed)

`inventory-service` consumes `outbound.picking.requested` and runs its own
`ReserveStockUseCase` over each line. On success, it emits
`inventory.reserved` per saga (one event with all lines, partition_key =
`saga_id`).

```
inventory-service                                  outbound-service
       │                                                    │
       │── consume outbound.picking.requested               │
       │     ├─ EventDedupe check                           │
       │     ├─ ReserveStockUseCase                         │
       │     │     ├─ for each line:                        │
       │     │     │   - locate stock (location, sku, lot)  │
       │     │     │   - decrement available, increment reserved
       │     │     │   - create Reservation(reservation_id = picking_request.id)
       │     │     ├─ all-or-nothing: any line short → no reservation; emit inventory.adjusted{reason=INSUFFICIENT_STOCK}
       │     │     └─ outbox: inventory.reserved (sagaId, reservation_id, lines)
       │     │
       │── emit inventory.reserved.v1 ────────────────────▶ │
       │     (partition_key = sagaId)                       │── InventoryReservedConsumer
       │                                                    │     ├─ EventDedupePort (TX-MANDATORY)
       │                                                    │     ├─ load OutboundSaga by sagaId
       │                                                    │     │     - already RESERVED? state-machine guard → no-op (silent)
       │                                                    │     │     - REQUESTED? proceed
       │                                                    │     ├─ Saga.transitionTo(RESERVED) (version++)
       │                                                    │     ├─ PickingRequest.status = SUBMITTED
       │                                                    │     └─ commit (no outbox row — REST drives next step)
       │                                                    │
       │                                                    ▼
                                                       Saga state: RESERVED
                                                       Order state: PICKING (unchanged)
```

**Out-of-stock branch**:

```
inventory-service                                  outbound-service
       │                                                    │
       │── ReserveStockUseCase fails any line               │
       │── emit inventory.adjusted.v1 (reason=INSUFFICIENT_STOCK, sagaId) ───▶│
       │                                                    │── InventoryAdjustedConsumer (filtered: reason=INSUFFICIENT_STOCK)
       │                                                    │     ├─ EventDedupePort
       │                                                    │     ├─ load OutboundSaga
       │                                                    │     ├─ Saga.transitionTo(RESERVE_FAILED, reason="…")
       │                                                    │     ├─ Order.backorder("INSUFFICIENT_STOCK")
       │                                                    │     │     status: PICKING → BACKORDERED
       │                                                    │     └─ outbox: outbound.order.cancelled (carries reason=BACKORDERED)
       │                                                    │
                                                       Saga state: RESERVE_FAILED (terminal)
                                                       Order state: BACKORDERED (terminal)
                                                       notification-service consumes → ops alert
```

**No compensation needed** in the reserve-failed branch: inventory has
not yet decremented anything (all-or-nothing reserve). The saga simply
terminates in `RESERVE_FAILED`, and the Order in `BACKORDERED`.

---

## Phase 3 — Operator Confirms Picks

The operator (or future scanner adapter) physically picks goods from
their assigned locations and submits one consolidated confirmation per
order (v1 — per-line confirmation is v2).

```
Operator                gateway-service           outbound-service          Postgres
 │                            │                          │                      │
 │── POST /api/v1/outbound/picking-requests/{id}/confirmations ──▶│
 │   (Bearer JWT, Idempotency-Key)                       │                      │
 │   {confirmedAt, lines: [                              │                      │
 │      {orderLineId, actualLocationId, lotId, qtyConfirmed}, ...]} │           │
 │                            │                          │                      │
 │                            │                          │── ConfirmPickingUseCase
 │                            │                          │     ├─ idempotency check (Redis)
 │                            │                          │     ├─ load OutboundSaga
 │                            │                          │     │     - state must be RESERVED
 │                            │                          │     │     else STATE_TRANSITION_INVALID (422)
 │                            │                          │     ├─ for each line:
 │                            │                          │     │     - qty_confirmed == order_line.qty_ordered (v1, no short-pick)
 │                            │                          │     │       else PICKING_QUANTITY_MISMATCH
 │                            │                          │     │     - LOT-tracked SKU: lotId required else LOT_REQUIRED
 │                            │                          │     │     - actual_location_id active per MasterReadModel
 │                            │                          │     ├─ create PickingConfirmation + PickingConfirmationLines
 │                            │                          │     ├─ Order.completePicking()  (PICKING → PICKED)
 │                            │                          │     ├─ Saga.transitionTo(PICKING_CONFIRMED)
 │                            │                          │     └─ outbox: outbound.picking.completed
 │◀── 201 {confirmationId, order.status=PICKED, saga.state=PICKING_CONFIRMED} ─│
                              │                          │                      │
                              Kafka: wms.outbound.picking.completed.v1
                                                         │
                                                         ▼
                                             admin-service consumes for KPI projection
                                             (inventory-service ignores — reserved stock
                                              is already accounted for)
```

**Saga state after Phase 3**:

| Aggregate | State |
|---|---|
| Order | PICKED |
| OutboundSaga | PICKING_CONFIRMED |
| PickingRequest | SUBMITTED (unchanged from Phase 2) |
| PickingConfirmation | (newly created) |

---

## Phase 4 — Operator Records Packing

The operator packs picked goods into one or more PackingUnits (boxes,
pallets, envelopes). Multiple `POST` calls create open units; a `PATCH`
seals each one. When the **last unit is sealed AND all lines are fully
packed**, the order transitions to `PACKED`.

This is the only phase with multiple REST calls before the saga
advances; the saga step fires only on the consolidating
`completePacking()`.

```
Operator                gateway-service           outbound-service          Postgres
 │                            │                          │                      │
 │── POST /api/v1/outbound/orders/{id}/packing-units ─▶ │                      │
 │   {packingType, dimensions, lines: [                  │                      │
 │      {orderLineId, lotId, qty}, ...]}                 │                      │
 │                            │                          │── CreatePackingUnitUseCase
 │                            │                          │     ├─ idempotency check (Redis)
 │                            │                          │     ├─ Order.status must be PICKED or PACKING
 │                            │                          │     │     else STATE_TRANSITION_INVALID
 │                            │                          │     ├─ if PICKED: Order.startPacking() → PACKING
 │                            │                          │     ├─ create PackingUnit (status=OPEN) + PackingUnitLines
 │                            │                          │     │     - sum(qty) per orderLineId so far ≤ order_line.qty_ordered
 │                            │                          │     │       else PACKING_QUANTITY_EXCEEDED
 │                            │                          │     └─ no outbox (intermediate)
 │◀── 201 {packingUnitId, status=OPEN} ─────────────────│                      │
 │                            │                          │                      │
 │                            (operator fills more units, repeats POST as needed)
 │                            │                          │                      │
 │── PATCH /api/v1/outbound/packing-units/{id} ───────▶│                      │
 │   {status: SEALED, weightGrams, dimensions}           │                      │
 │                            │                          │── SealPackingUnitUseCase
 │                            │                          │     ├─ packing_unit.status must be OPEN
 │                            │                          │     │     else STATE_TRANSITION_INVALID
 │                            │                          │     ├─ PackingUnit.seal()  (OPEN → SEALED)
 │                            │                          │     ├─ check completeness:
 │                            │                          │     │     for each order_line: sum(packing_unit_line.qty) == order_line.qty_ordered?
 │                            │                          │     │     and all packing_units for this order are SEALED?
 │                            │                          │     ├─ if YES (all complete):
 │                            │                          │     │     - Order.completePacking()  (PACKING → PACKED)
 │                            │                          │     │     - Saga.transitionTo(PACKING_CONFIRMED)
 │                            │                          │     │     - outbox: outbound.packing.completed
 │                            │                          │     │   else: status remains PACKING, no outbox
 │◀── 200 {status=SEALED, order.status=PACKING|PACKED} ──│                      │
                              │                          │                      │
                              Kafka: wms.outbound.packing.completed.v1 (only fired on final seal)
```

**Why packing event fires only on completion**: per
[`domain-model.md`](../domain-model.md) §4 invariants, the order can
transition to `PACKED` only when **sum(packed) == sum(ordered)** for
every line and every PackingUnit is `SEALED`. The outbox event
`outbound.packing.completed` is the **final state snapshot**, fired in
the same TX as the order transition. admin-service consumes it for KPI
calculation; inventory-service ignores it (stock is still reserved, not
yet consumed).

**Saga state after Phase 4 completes**:

| Aggregate | State |
|---|---|
| Order | PACKED |
| OutboundSaga | PACKING_CONFIRMED |
| PackingUnit (n records) | all SEALED |

---

## Phase 5 — Operator Confirms Shipping (Saga Step 4 + TMS)

The operator confirms the shipment is ready to leave the warehouse. This
is the most consequential step — it fires the saga's final outbound
event and triggers TMS notification.

### 5a. Synchronous Path (REST + atomic TX)

```
Operator                gateway-service           outbound-service          Postgres
 │                            │                          │                      │
 │── POST /api/v1/outbound/orders/{id}/shipments ─────▶│                      │
 │   (Bearer JWT, Idempotency-Key)                      │                      │
 │   {carrierCode (optional), shippedAt}                │                      │
 │                            │                          │── ConfirmShippingUseCase
 │                            │                          │     ├─ idempotency check (Redis)
 │                            │                          │     ├─ Order.status must be PACKED
 │                            │                          │     │     else STATE_TRANSITION_INVALID (422)
 │                            │                          │     │   (if SHIPPED already: ORDER_ALREADY_SHIPPED)
 │                            │                          │     ├─ load OutboundSaga (state must be PACKING_CONFIRMED)
 │                            │                          │     ├─ Order.confirmShipping()  (PACKED → SHIPPED)
 │                            │                          │     ├─ create Shipment (tms_status=PENDING)
 │                            │                          │     ├─ Saga.transitionTo(SHIPPED)
 │                            │                          │     └─ outbox: outbound.shipping.confirmed
 │◀── 201 {shipmentId, shipmentNo, tms_status=PENDING, order.status=SHIPPED} ─│
                              │                          │                      │
                              Kafka: wms.outbound.shipping.confirmed.v1
                                                         │
                                                         │  (consumed by inventory-service — see §5b)
                                                         │  (separately: TMS push — see §5c)
```

### 5b. Inventory Confirm (Async)

```
inventory-service                                outbound-service
       │                                                  │
       │── consume outbound.shipping.confirmed            │
       │     ├─ EventDedupe check                         │
       │     ├─ ConfirmStockUseCase                       │
       │     │     ├─ for each line: decrement reserved, no available change (already deducted on reserve)
       │     │     ├─ Reservation.confirm() (PENDING → CONFIRMED)
       │     │     └─ outbox: inventory.confirmed (sagaId)
       │     │
       │── emit inventory.confirmed.v1 ─────────────────▶ │
       │     (partition_key = sagaId)                     │── InventoryConfirmedConsumer
       │                                                  │     ├─ EventDedupePort
       │                                                  │     ├─ load OutboundSaga
       │                                                  │     │     - already COMPLETED? state-machine guard → no-op
       │                                                  │     │     - SHIPPED? proceed
       │                                                  │     │     - SHIPPED_NOT_NOTIFIED? still proceed (TMS retry can re-progress)
       │                                                  │     ├─ Saga.transitionTo(COMPLETED)
       │                                                  │     └─ commit
       │                                                  ▼
                                                   Saga state: COMPLETED (terminal)
```

### 5c. TMS Push (Async — after main TX commit)

```
After ConfirmShippingUseCase TX commits:
  AfterCommit hook (or scheduled poller for SHIPPED shipments with tms_status=PENDING)

outbound-service                                  TMS
       │                                                  │
       │── TmsClientAdapter.notify(shipment)              │
       │     ├─ check tms_request_dedupe (skip if cached snapshot exists)
       │     ├─ POST {tms-base}/shipments                 │
       │     │     headers:                               │
       │     │       Idempotency-Key: {shipment.id}       │
       │     │       X-Tms-Api-Key: <secret>              │
       │     │     body: TmsShipmentRequest(carrier, dimensions, ...) ─▶│
       │     │                                            │
       │     │ ◀── 200 {tms_shipment_id, carrier_code, tracking_number, status=ACCEPTED} ──│
       │     ├─ INSERT tms_request_dedupe (REQUIRES_NEW)  │
       │     ├─ Shipment.recordTmsAck(...)                │
       │     │     - tms_status: PENDING → NOTIFIED       │
       │     │     - tms_notified_at = now()              │
       │     │     - tracking_no, carrier_code populated  │
       │     │
       │     [success path ends; Saga.transitionTo(COMPLETED) already happened or will happen via §5b]
```

**Failure path** (after retry / circuit / bulkhead exhaustion — see
[`external-integrations.md`](../external-integrations.md) §2):

```
       │
       │     ├─ retries exhausted (timeout / 5xx / circuit open)
       │     ├─ Shipment.recordTmsFailure(reason)
       │     │     - tms_status: PENDING → NOTIFY_FAILED
       │     │     - failure_reason populated
       │     ├─ Saga.transitionTo(SHIPPED_NOT_NOTIFIED, reason="…")
       │     ├─ outbox: (no event — internal alert only)
       │     └─ metric outbound.tms.notify_failed.count++
                                                          │
                                                   notification-service alert fires
                                                   ops investigates via runbook
```

**Stock state**: At this point, `inventory.confirmed` may already have
been processed (Saga went `SHIPPED → COMPLETED → ... ` then back? No —
saga state-machine forbids that). What actually happens:

- If `inventory.confirmed` arrived **before** TMS exhaustion: saga is
  already `COMPLETED`. The TMS handler still updates the Shipment row
  (independent aggregate). No saga regression — `COMPLETED` is terminal,
  and the TMS-failure side-channel records the issue on the Shipment.
- If `inventory.confirmed` arrives **during** TMS retry: saga moves
  `SHIPPED → COMPLETED`. TMS exhaustion afterward only flips
  `Shipment.tms_status=NOTIFY_FAILED` and emits the alert; the saga
  stays `COMPLETED`.
- If TMS exhaustion happens **before** `inventory.confirmed`: saga moves
  `SHIPPED → SHIPPED_NOT_NOTIFIED`. When `inventory.confirmed` later
  arrives, the saga state-machine checks: from
  `SHIPPED_NOT_NOTIFIED` + `inventory.confirmed`, the saga transitions
  straight to `COMPLETED` (the alert is a soft state, not blocking).

> **Subtlety**: `SHIPPED_NOT_NOTIFIED` is a non-terminal alert state.
> Both `inventory.confirmed` (which advances saga to `COMPLETED`) and a
> manual `:retry-tms-notify` call (which only updates Shipment, not
> saga) progress the system. See
> [`state-machines/saga-status.md`](../state-machines/saga-status.md)
> for the full transition table.

### 5d. Manual TMS Retry

For sagas in `SHIPPED_NOT_NOTIFIED`:

```
Operator (OUTBOUND_ADMIN)        outbound-service
 │                                       │
 │── POST /api/v1/outbound/shipments/{id}:retry-tms-notify ─▶│
 │                                       │── RetryTmsNotifyUseCase
 │                                       │     ├─ idempotency check (Redis)
 │                                       │     ├─ Shipment.tms_status must be NOTIFY_FAILED
 │                                       │     │     (NOTIFIED → return cached ack, no TMS call)
 │                                       │     ├─ TmsClientAdapter.notify(shipment) (same Idempotency-Key)
 │                                       │     ├─ on success:
 │                                       │     │     - Shipment.recordTmsAck(...)  → tms_status=NOTIFIED
 │                                       │     │     - if Saga.state == SHIPPED_NOT_NOTIFIED:
 │                                       │     │         Saga.transitionTo(COMPLETED)  (sweeper / direct)
 │                                       │     └─ on failure: stays NOTIFY_FAILED, alert continues
 │◀── 200 {shipment.tms_status=NOTIFIED, tracking_no} ─────│
```

---

## Phase 6 — Cancellation (Off-Path, Compensation Saga)

Cancellation is allowed at any non-terminal Order status **before**
`SHIPPED`. After `SHIPPED`, it's forbidden in v1 (`ORDER_ALREADY_SHIPPED`)
— returns / RMA is v2 (creates an inbound RMA, distinct lifecycle).

The cancellation flow is itself a mini-saga: outbound-service must
release the inventory reservation, which is done by emitting
`outbound.picking.cancelled` and waiting for `inventory.released`.

### 6a. Cancellation Triggered (REST)

```
Operator (OUTBOUND_ADMIN)                gateway-service           outbound-service
 │                                              │                        │
 │── POST /api/v1/outbound/orders/{id}:cancel ─▶│                        │
 │   {reason: "고객 취소 통보"}                  │                        │
 │                                              │                        │── CancelOrderUseCase
 │                                              │                        │     ├─ idempotency check (Redis)
 │                                              │                        │     ├─ Order.status ∈ {RECEIVED, PICKING, PICKED, PACKING, PACKED}
 │                                              │                        │     │     else ORDER_ALREADY_SHIPPED (422 if SHIPPED)
 │                                              │                        │     │     else STATE_TRANSITION_INVALID (if CANCELLED/BACKORDERED)
 │                                              │                        │     ├─ Order.cancel(reason)  (* → CANCELLED)
 │                                              │                        │     ├─ load OutboundSaga
 │                                              │                        │     │     - if state ∈ {RESERVED, PICKING_CONFIRMED, PACKING_CONFIRMED}:
 │                                              │                        │     │         Saga.transitionTo(CANCELLATION_REQUESTED)
 │                                              │                        │     │         outbox: outbound.picking.cancelled (sagaId, reservation_id)
 │                                              │                        │     │     - if state == REQUESTED:
 │                                              │                        │     │         (no inventory reservation yet → no compensation needed)
 │                                              │                        │     │         Saga.transitionTo(CANCELLED) directly
 │                                              │                        │     │         outbox: (none for inventory)
 │                                              │                        │     └─ outbox: outbound.order.cancelled
 │◀── 200 {order.status=CANCELLED, saga.state=CANCELLATION_REQUESTED|CANCELLED} ─│
                                                                          │
                                                Kafka: wms.outbound.picking.cancelled.v1 (consumed by inventory)
                                                Kafka: wms.outbound.order.cancelled.v1
```

### 6b. Inventory Releases (Async)

```
inventory-service                                outbound-service
       │                                                  │
       │── consume outbound.picking.cancelled             │
       │     ├─ EventDedupe                               │
       │     ├─ ReleaseStockUseCase                       │
       │     │     ├─ for each reserved line: increment available, decrement reserved
       │     │     ├─ Reservation.release() (PENDING → RELEASED)
       │     │     └─ outbox: inventory.released (sagaId)
       │     │
       │── emit inventory.released.v1 ──────────────────▶ │
       │     (partition_key = sagaId)                     │── InventoryReleasedConsumer
       │                                                  │     ├─ EventDedupePort
       │                                                  │     ├─ load OutboundSaga
       │                                                  │     │     - already CANCELLED? state-machine guard → no-op
       │                                                  │     │     - CANCELLATION_REQUESTED? proceed
       │                                                  │     ├─ Saga.transitionTo(CANCELLED)
       │                                                  │     └─ commit
       │                                                  ▼
                                                   Saga state: CANCELLED (terminal)
```

### 6c. Compensation Guarantees

| Failure Point | Compensation Path | Guarantee |
|---|---|---|
| Reserve fails (insufficient stock) | No compensation needed (all-or-nothing reserve) | Saga → `RESERVE_FAILED`; Order → `BACKORDERED` |
| Pre-pick cancellation (Saga in `RESERVED`) | Emit `outbound.picking.cancelled` → inventory releases → saga `CANCELLED` | Released exactly once per saga (sweeper re-emits if `CANCELLATION_REQUESTED` > 5 min) |
| Pick-stage cancellation (Saga in `PICKING_CONFIRMED`) | Same path — picked-but-still-reserved stock is releasable | Same |
| Post-pick / pre-ship cancellation (Saga in `PACKING_CONFIRMED`) | Same path | Same |
| Post-ship cancellation | **Forbidden in v1** (`ORDER_ALREADY_SHIPPED`) | Returns / RMA is v2 |
| TMS notify fails after retries | Saga stays `SHIPPED_NOT_NOTIFIED`; manual retry via `:retry-tms-notify` | Stock already consumed — TMS retry is the only recovery |

---

## Phase 7 — Saga Sweeper (Recovery Loop)

Background job runs every 60 seconds and finds sagas stuck in
transitional states beyond a threshold. Re-emits the driver event
(idempotent at the consumer due to T8 + saga state-machine guard).

```
SagaSweeperJob (every 60s):

  SELECT * FROM outbound_saga
   WHERE state = 'REQUESTED' AND last_transition_at < now() - interval '5 minutes'
   FOR UPDATE SKIP LOCKED;
       → for each: write fresh outbox row outbound.picking.requested (new eventId)

  SELECT * FROM outbound_saga
   WHERE state = 'CANCELLATION_REQUESTED' AND last_transition_at < now() - interval '5 minutes';
       → for each: write fresh outbox row outbound.picking.cancelled

  SELECT * FROM outbound_saga
   WHERE state = 'SHIPPED' AND last_transition_at < now() - interval '5 minutes'
       AND NOT EXISTS (SELECT 1 FROM outbound_event_dedupe ed
                        WHERE ed.event_type = 'inventory.confirmed'
                          AND ed.outcome = 'APPLIED'
                          AND ed.event_id IN (... events for this sagaId ...));
       → for each: write fresh outbox row outbound.shipping.confirmed
```

**Why sweeper is safe**:

1. Each re-emission carries a **fresh** Kafka envelope `eventId`. Inventory's
   own dedupe sees a new id and processes (or no-ops via inventory's own
   reservation-id UNIQUE constraint).
2. When inventory replies (with possibly-fresh eventId), outbound's
   `EventDedupePort` checks first; if dedupe row exists from prior delivery
   → no-op. If dedupe row missing (TTL expired) → saga state-machine guard
   absorbs as no-op when state is already advanced.

The sweeper is the **failure-recovery loop** that makes the saga
eventually consistent even if a Kafka message is lost. Combined with
consumer-side dedupe and saga-level idempotency, re-emission is safe.

---

## Failure Modes (per `rules/traits/transactional` Required Artifact 5)

| Scenario | Behavior |
|---|---|
| Webhook signature invalid | 401, no row written to inbox or dedupe |
| Webhook event-id duplicate | 200 `{status: ignored_duplicate}`, single domain effect |
| Operator submits manual order with same `orderNo` as a webhook-pending row | 1st commits via webhook background; 2nd hits `order_no` UNIQUE → 409 `ORDER_NO_DUPLICATE` |
| Two operators race on same picking confirmation | 1st commits; 2nd hits `OptimisticLockingFailureException` on PickingRequest → 409 `CONFLICT`. Caller refetches; Saga state already advanced; PickingRequest already SUBMITTED → second sees `STATE_TRANSITION_INVALID` |
| Inventory `inventory.reserved` arrives twice (broker redelivery, same eventId) | EventDedupe absorbs second; saga unchanged |
| Inventory `inventory.reserved` arrives with **fresh** eventId for already-RESERVED saga (sweeper re-emit) | State-machine guard: silent no-op |
| Reserve fails (insufficient stock) | Saga → `RESERVE_FAILED`; Order → `BACKORDERED`; outbox: `outbound.order.cancelled` (reason=BACKORDERED). Single emission |
| Pre-pick cancel | `outbound.picking.cancelled` fired exactly once. Inventory releases, emits `inventory.released`. Saga → `CANCELLED` |
| TMS timeout / 5xx | Retried 3 times exp-backoff; on exhaustion saga → `SHIPPED_NOT_NOTIFIED`, alert. Stock already consumed |
| TMS 4xx (validation) | No retry; saga → `SHIPPED_NOT_NOTIFIED` immediately, `failure_reason=TMS_VALIDATION_REJECTED` |
| `inventory.confirmed` arrives before saga is `SHIPPED` (impossible — but log-and-drop) | State-machine guard logs WARN `saga_event_invalid_transition`, message goes to DLT after retries; ops investigates |
| inventory-service down when `outbound.shipping.confirmed` published | Event sits on Kafka topic; inventory-service catches up on restart. Outbox already delivered — outbound TX committed. Saga sweeper safe-net at 5min |
| outbound-service crashes after Order TX commits but before TMS push | After-commit hook re-runs on next pod start (scheduled poller picks up SHIPPED + tms_status=PENDING shipments) |
| ERP webhook arrives after operator manually creates same Order | First commit wins (`order_no` UNIQUE); second errors. Webhook inbox row marked FAILED with reason; ops investigates |

---

## Sequence — Happy Path Cycle Time Reference

For SLO baseline (admin-service `outbound.order.cycle.time.seconds` p50
target):

| Step | Typical duration |
|---|---|
| Order received → inventory.reserved | ~2–10s (Kafka + inventory work) |
| Reserved → operator confirms picks | 15–60 min (operator walking aisles) |
| Picks confirmed → packing complete | 5–20 min |
| Packing complete → shipping confirmed | 1–5 min |
| Shipping confirmed → TMS notified | ~1–3s (TMS p99 <12s) |
| Shipping confirmed → inventory.confirmed | ~2–10s |
| **End-to-end (excluding human time)** | typically ~20s of compute / IO; rest is human labor |
| **End-to-end (with operator time)** | typically 30–90 min |

Outbound-service contributes ~5s of compute / IO; the rest is human time
+ async event hops.

---

## Workflow Invariants (cross-phase)

| Invariant | Phase | Source |
|---|---|---|
| Every Order has a unique `order_no` | reception | `domain-model.md` §1 |
| Every Order has at most one OutboundSaga (`order_id` UNIQUE) | reception | `domain-model.md` §6 |
| OrderLines immutable from `PICKING` onwards | reception | `domain-model.md` §1 |
| Saga state advances monotonically through state machine (T4) | every | `state-machines/saga-status.md` |
| `qty_to_pick` ≤ `order_line.qty_ordered` per line | reception | `domain-model.md` §2 |
| `qty_confirmed` == `order_line.qty_ordered` per line (v1, no short-pick) | confirmation | `domain-model.md` §3 |
| Sum of `packing_unit_line.qty` == `order_line.qty_ordered` per line at PACKED | packing | `domain-model.md` §4 |
| One Shipment per Order (`order_id` UNIQUE) | shipping | `domain-model.md` §5 |
| Cancellation forbidden after `SHIPPED` (`ORDER_ALREADY_SHIPPED`) | any | `state-machines/order-status.md` |
| One outbox row per use-case `@Transactional` (or N rows when atomically required, e.g., Phase 1) | every | `architecture.md` § Persistence |
| Saga creation atomic with first outbox emission | reception | T7 |

---

## Out of Scope (v1)

- Returns / RMA outbound flow (creates an inbound RMA — distinct
  lifecycle, v2)
- Cross-warehouse split-picking (multi-warehouse Order — `WAREHOUSE_MISMATCH` blocks)
- FEFO auto-allocation by inventory (v1: order line specifies lot or null)
- Wave / batch picking (v2: `Wave` aggregate)
- Carrier rating / TMS quote API (v2)
- Per-line picking confirmation (v1: one consolidated confirmation per order)
- Reverse picking (un-confirming a PickingConfirmation)
- Partial shipping (v1: all-or-nothing per Order)
- Scanner / RFID adapter for picking
- ERP outbound order ack push (v2)

---

## References

- [`../architecture.md`](../architecture.md) § Outbound Saga, § Outbound
  Workflow, § TMS Integration
- [`../domain-model.md`](../domain-model.md) — entity field details
- [`../state-machines/order-status.md`](../state-machines/order-status.md)
  — formal Order transition table
- [`../state-machines/saga-status.md`](../state-machines/saga-status.md)
  — formal saga transition table
- [`../sagas/outbound-saga.md`](../sagas/outbound-saga.md) — saga document
  per trait `transactional` Required Artifact 2
- [`../external-integrations.md`](../external-integrations.md) — TMS,
  ERP, Kafka, Postgres, Redis policies
- [`../idempotency.md`](../idempotency.md) — REST + webhook + Kafka +
  saga-level dedupe strategies
- `specs/contracts/http/outbound-service-api.md` — REST endpoint shapes
  (Open Item)
- `specs/contracts/webhooks/erp-order-webhook.md` — webhook payload +
  headers (Open Item)
- `specs/contracts/events/outbound-events.md` — outbox event schemas
  (Open Item)
- `specs/services/inventory-service/architecture.md` — counterpart
  consumer for picking.requested / .cancelled / shipping.confirmed
- `specs/services/inbound-service/workflows/inbound-flow.md` — sibling
  reference (no saga)
- `rules/domains/wms.md` — Outbound bounded context, W1, W4, W5, W6
- `rules/traits/transactional.md` — T1, T2, T3, T4, T5, T6, T7, T8
- `rules/traits/integration-heavy.md` — I1–I4, I6, I9, I10
