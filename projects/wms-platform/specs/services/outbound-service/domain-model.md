# outbound-service — Domain Model

Domain model specification for `outbound-service`. Owned aggregates, fields,
relationships, invariants, and state transitions.

Read this after `specs/services/outbound-service/architecture.md`. The outbound
saga, TMS integration pattern, webhook reception, and Hexagonal layout are
declared there and only restated here as far as needed to reason about the model.

---

## Scope

Seven owned aggregates plus infrastructure-supporting records:

**Aggregates (owned by this service)**

1. **Order** — root of the outbound workflow; carries the order lifecycle
2. **PickingRequest** — saga step 1; reservation instruction to `inventory-service`
3. **PickingConfirmation** — physical pick confirmation by operator
4. **PackingUnit** — packing record (box / pallet)
5. **Shipment** — final shipment record; TMS handover
6. **OutboundSaga** — saga state tracker; orchestrates reserve → pick → pack →
   ship across `outbound-service` and `inventory-service`

**Infrastructure-supporting records**

7. **OutboundOutbox** — transactional outbox row (T3)
8. **EventDedupe** — Kafka consumer-side dedupe (T8)
9. **ErpOrderWebhookInbox** — ERP push ingest buffer (same pattern as inbound-service)
10. **ErpOrderWebhookDedupe** — webhook replay-protection
11. **TmsRequestDedupe** — TMS API idempotency tracking
12. **MasterReadModel** — local cache of Location / SKU / Lot / Partner snapshots

---

## Common Aggregate Shape

Every aggregate row carries:

| Field | Type | Notes |
|---|---|---|
| `id` | UUID | Surrogate PK |
| `version` | Long | Optimistic lock (T5). `SELECT FOR UPDATE` forbidden |
| `created_at` | Instant | UTC |
| `created_by` | String | JWT subject or `system:erp-webhook` |
| `updated_at` | Instant | UTC |
| `updated_by` | String | |

Outbox / EventDedupe / Webhook records are append-only ledgers with no `version`
or `updated_*`. DB role grants revoke `UPDATE` / `DELETE` on those tables.

---

## 1. Order (Aggregate Root)

### Purpose

The business anchor of the outbound lifecycle. Represents a customer-issued
shipping order received from ERP or entered manually by ops.

### Fields

| Field | Type | Nullable | Notes |
|---|---|---|---|
| `id` | UUID | no | |
| `order_no` | String (40) | no | Business identifier. **Globally unique**. Pattern: `ORD-{YYYYMMDD}-{seq}`. Auto-generated for manual; ERP-assigned for webhook |
| `source` | enum `MANUAL` / `WEBHOOK_ERP` | no | Immutable after creation |
| `customer_partner_id` | UUID | no | FK-by-id to master Partner. Must be `ACTIVE` with `partner_type = CUSTOMER or BOTH` at creation |
| `warehouse_id` | UUID | no | Fulfillment warehouse; immutable after creation |
| `required_ship_date` | LocalDate | yes | Customer's requested shipping date |
| `notes` | String (1000) | yes | |
| `status` | enum | no | See state machine below |
| (common version / timestamp fields) | | | |

### OrderLine (child of Order)

| Field | Type | Nullable | Notes |
|---|---|---|---|
| `id` | UUID | no | |
| `order_id` | UUID (FK) | no | Parent Order |
| `line_no` | Integer (>0) | no | 1-indexed; unique within Order |
| `sku_id` | UUID | no | Must be `ACTIVE` per MasterReadModel at creation |
| `lot_id` | UUID | yes | Required iff SKU is LOT-tracked and lot is explicitly requested. Null = any available lot (FEFO selection by operator / inventory in v2) |
| `qty_ordered` | Integer (>0) | no | EA |

OrderLine is immutable after `PICKING` status is entered on the Order.

### Domain Methods (T4)

| Method | Allowed from | To |
|---|---|---|
| `startPicking()` | `RECEIVED` | `PICKING` |
| `completePicking()` | `PICKING` | `PICKED` |
| `startPacking()` | `PICKED` | `PACKING` |
| `completePacking()` | `PACKING` | `PACKED` |
| `confirmShipping()` | `PACKED` | `SHIPPED` |
| `cancel(reason)` | `RECEIVED`, `PICKING`, `PICKED`, `PACKING`, `PACKED` | `CANCELLED` |
| `backorder(reason)` | `RECEIVED` | `BACKORDERED` |

`STATE_TRANSITION_INVALID` for any other transition. `SHIPPED`, `CANCELLED`, and
`BACKORDERED` are terminal — no further mutations.

### Order State Machine

```
  [receive (create)]
         |
         v
     RECEIVED ──────────────────────────────────────[cancel]──> CANCELLED (terminal)
         |                                                |
   [startPicking]                                 (any state before SHIPPED)
         |
         v
     PICKING ──────────────────────────────────────[cancel]──> CANCELLED
         |
   [completePicking]
         |
         v
      PICKED ───────────────────────────────────────[cancel]──> CANCELLED
         |
   [startPacking]
         |
         v
     PACKING ──────────────────────────────────────[cancel]──> CANCELLED
         |
  [completePacking]
         |
         v
      PACKED ────────────────────────────────────────[cancel]──> CANCELLED
         |
  [confirmShipping]
         |
         v
    SHIPPED (terminal)

   RECEIVED ──[backorder]──> BACKORDERED (terminal; reserve-failed path)
```

`CANCELLED` and `BACKORDERED` are both terminal. Post-`SHIPPED` cancellation
is **forbidden** in v1 (`ORDER_ALREADY_SHIPPED`); returns modeled as RMA
inbound in v2.

### Invariants

- `order_no` unique across the system; immutable after creation.
- `customer_partner_id` must resolve to `ACTIVE` Partner with
  `partner_type = CUSTOMER or BOTH` at creation (`PARTNER_INVALID_TYPE`).
- At least one OrderLine required.
- All OrderLines' `sku_id` must be `ACTIVE` per MasterReadModel (`SKU_INACTIVE`).
- LOT-tracked SKU lines with explicit `lot_id` must resolve to an `ACTIVE`, non-`EXPIRED`
  Lot per MasterReadModel.
- All OrderLines must belong to the same `warehouse_id` (cross-warehouse forbidden
  in v1 — `WAREHOUSE_MISMATCH`).
- OrderLines are immutable after `PICKING` status is entered.

---

## 2. PickingRequest

### Purpose

Saga step 1: the outbound-service's request to `inventory-service` to reserve
stock. Published as `outbound.picking.requested` event. One PickingRequest per
Order.

### Fields

| Field | Type | Nullable | Notes |
|---|---|---|---|
| `id` | UUID | no | Also the `reservationId` referenced in inventory |
| `order_id` | UUID (FK) | no | 1:1 with Order |
| `saga_id` | UUID | no | Cross-reference to OutboundSaga. Sent in the event so inventory correlates replies |
| `warehouse_id` | UUID | no | Denormalized |
| `status` | enum `PENDING` / `SUBMITTED` / `RESERVE_FAILED` | no | Updated by saga |
| (common version / timestamp fields) | | | |

### PickingRequestLine (child of PickingRequest)

| Field | Type | Nullable | Notes |
|---|---|---|---|
| `id` | UUID | no | |
| `picking_request_id` | UUID (FK) | no | |
| `order_line_id` | UUID (FK) | no | The OrderLine being filled |
| `sku_id` | UUID | no | Denormalized |
| `lot_id` | UUID | yes | Denormalized |
| `location_id` | UUID | no | Picking source location (assigned by `PickingPlanner` domain service at `RequestPickingUseCase` time) |
| `qty_to_pick` | Integer (>0) | no | EA; must ≤ `order_line.qty_ordered` |

### Invariants

- One PickingRequest per Order; `order_id` has a unique constraint.
- `picking_request.id` == the `reservation_id` carried in
  `outbound.picking.requested` event and echoed back in `inventory.reserved`
  / `inventory.released` / `inventory.confirmed` events.
- `qty_to_pick` ≤ `order_line.qty_ordered` for each line (`PICKING_QUANTITY_EXCEEDED`).
- All picking source `location_id`s must be in the same `warehouse_id` (W1, single-warehouse).
- Status transitions are driven solely by the `OutboundSagaCoordinator`; direct
  writes forbidden (T4).

---

## 3. PickingConfirmation

### Purpose

Operator-submitted confirmation that physical picks were executed. One record per
order (confirming all lines at once in v1; per-line confirmation is v2).

### Fields

| Field | Type | Nullable | Notes |
|---|---|---|---|
| `id` | UUID | no | |
| `picking_request_id` | UUID (FK) | no | 1:1 with PickingRequest |
| `order_id` | UUID | no | Denormalized |
| `confirmed_by` | String | no | Actor id of the warehouse operator |
| `confirmed_at` | Instant | no | |
| `notes` | String (500) | yes | |

### PickingConfirmationLine (child of PickingConfirmation)

| Field | Type | Nullable | Notes |
|---|---|---|---|
| `id` | UUID | no | |
| `picking_confirmation_id` | UUID (FK) | no | |
| `order_line_id` | UUID (FK) | no | |
| `sku_id` | UUID | no | Denormalized |
| `lot_id` | UUID | yes | Actual lot picked (may differ if operator substituted) |
| `actual_location_id` | UUID | no | Where goods were actually picked from |
| `qty_confirmed` | Integer (>0) | no | EA |

### Invariants

- `qty_confirmed` must equal `order_line.qty_ordered` for each line in v1
  (no short-pick; short-pick → cancel + new order in v2).
- `actual_location_id` should match `picking_request_line.location_id`; operator
  override is permitted but logged (`notes`). Warning only in v1.
- For LOT-tracked SKUs, `lot_id` must be provided (`LOT_REQUIRED`).
- PickingConfirmation is append-only (immutable after creation).

---

## 4. PackingUnit

### Purpose

Records the physical packing of picked goods into a shipping unit (box, pallet,
envelope). One Order may have multiple PackingUnits.

### Fields

| Field | Type | Nullable | Notes |
|---|---|---|---|
| `id` | UUID | no | |
| `order_id` | UUID (FK) | no | |
| `carton_no` | String (40) | no | Unique within the Order; operator-assigned or auto-generated |
| `packing_type` | enum `BOX` / `PALLET` / `ENVELOPE` | no | |
| `weight_grams` | Integer | yes | For TMS handover |
| `length_mm` / `width_mm` / `height_mm` | Integer | yes | Dimensions for TMS |
| `notes` | String (500) | yes | |
| `status` | enum `OPEN` / `SEALED` | no | Sealed when packing is finalized |
| (common version / timestamp fields) | | | |

### PackingUnitLine (child of PackingUnit)

| Field | Type | Nullable | Notes |
|---|---|---|---|
| `id` | UUID | no | |
| `packing_unit_id` | UUID (FK) | no | |
| `order_line_id` | UUID (FK) | no | |
| `sku_id` | UUID | no | Denormalized |
| `lot_id` | UUID | yes | |
| `qty` | Integer (>0) | no | EA packed in this unit |

### Invariants

- Sum of `PackingUnitLine.qty` across all units for an `order_line_id` must equal
  `order_line.qty_ordered` before the Order can transition to `completePacking()`
  (`PACKING_INCOMPLETE`).
- `carton_no` unique within the Order; not globally unique.
- PackingUnit can only be created / modified while Order is in `PACKING` status.
- `SEALED` PackingUnit is immutable; lines cannot be added/removed.

---

## 5. Shipment

### Purpose

The final shipping record. Created when `ConfirmShippingUseCase` is executed on
a `PACKED` order. Triggers:
1. `outbound.shipping.confirmed` outbox event → `inventory-service` consumes →
   `confirm(reserved)` on all reserved quantities.
2. TMS HTTP notification via `ShipmentNotificationPort`.

### Fields

| Field | Type | Nullable | Notes |
|---|---|---|---|
| `id` | UUID | no | |
| `order_id` | UUID (FK) | no | 1:1 with Order in v1 |
| `shipment_no` | String (40) | no | Auto-generated. **Globally unique** |
| `carrier_code` | String (40) | yes | Carrier identifier for TMS (may be assigned after TMS notification) |
| `tracking_no` | String (100) | yes | Carrier-assigned tracking number; populated from TMS ack |
| `shipped_at` | Instant | no | Wall-clock at `ConfirmShippingUseCase` execution |
| `tms_status` | enum `PENDING` / `NOTIFIED` / `NOTIFY_FAILED` | no | Tracks TMS notification outcome |
| `tms_notified_at` | Instant | yes | Set when TMS ack received |
| `tms_request_id` | UUID | yes | The `request_id` stored in `TmsRequestDedupe` |
| (common version / timestamp fields) | | | |

### Invariants

- One Shipment per Order in v1; `order_id` unique.
- `shipment_no` globally unique; immutable after creation.
- `Shipment` is the anchor for the `outbound.shipping.confirmed` outbox event.
  The event carries `reservation_id` (= `picking_request.id`) and per-line
  confirmed quantities so inventory-service can `confirm()` each line.
- `tms_status = NOTIFY_FAILED` → Saga advances to `SHIPPED_NOT_NOTIFIED` (alert);
  stock is already consumed. Manual retry endpoint `POST /shipments/{id}/retry-tms-notify`
  re-attempts TMS notification without changing stock state.
- Shipment is immutable (except `tms_status` / `tms_notified_at` / `tracking_no`
  updates from TMS response handling).

---

## 6. OutboundSaga

### Purpose

The saga-state aggregate. Tracks the choreographed sequence: Order → Reserve →
Pick → Pack → Ship, plus compensation paths. Persisted in `outbound_saga` table.
Orchestrated by `OutboundSagaCoordinator` (application layer) which reacts to
inbound Kafka events.

### Fields

| Field | Type | Nullable | Notes |
|---|---|---|---|
| `saga_id` | UUID | no | PK; also used as Kafka `sagaId` header for partition routing |
| `order_id` | UUID | no | 1:1 with Order; unique |
| `state` | enum | no | See saga state machine below |
| `failure_reason` | String (500) | yes | Populated on `RESERVE_FAILED` or `SHIPPED_NOT_NOTIFIED` |
| `last_transition_at` | Instant | no | Monotonically increasing; used by sweeper |
| `started_at` | Instant | no | |
| (common version / timestamp fields) | | | |

### Saga State Machine

```
      [create: startPicking → emit picking.requested]
                       |
                       v
                  REQUESTED ──[reserve_failed event]──> RESERVE_FAILED (terminal)
                       |
             [inventory.reserved consumed]
                       |
                       v
                  RESERVED ──[cancel REST call]──> CANCELLATION_REQUESTED
                       |                                   |
             [confirmPicking REST]               [inventory.released consumed]
                       |                                   |
                       v                                   v
              PICKING_CONFIRMED ──[cancel]──> CANCELLATION_REQUESTED
                       |
             [confirmPacking REST]
                       |
                       v
              PACKING_CONFIRMED ──[cancel]──> CANCELLATION_REQUESTED
                       |
         [confirmShipping REST → emit shipping.confirmed → TMS call]
                       |
                       v
                   SHIPPED ──[tms_notify_failed]──> SHIPPED_NOT_NOTIFIED (alert)
                       |
          [inventory.confirmed consumed]
                       |
                       v
                 COMPLETED (terminal)

         CANCELLATION_REQUESTED ──[inventory.released consumed]──> CANCELLED (terminal)
```

- `RESERVE_FAILED`, `CANCELLED`, `COMPLETED` are terminal.
- `SHIPPED_NOT_NOTIFIED` is a **non-terminal alert state** — saga stays here until
  manual TMS retry succeeds, at which point it advances to `COMPLETED`.
- Direct `UPDATE outbound_saga SET state = ?` is forbidden (T4). Only
  `OutboundSagaCoordinator.apply(event)` transitions state.

### Saga Idempotency (T8 extended)

Every saga state transition is conditioned on the **current state**. Re-delivered
events for an already-applied state are silent no-ops (state machine rejects
the transition without error). This extends the base eventId dedupe to cover
the saga level:

```
Re-delivered inventory.reserved when saga is already RESERVED → no-op
Re-delivered inventory.confirmed when saga is already COMPLETED → no-op
```

### Saga Sweeper

Background job (every 60 s) finds sagas stuck in transitional states and re-emits
the driving event (idempotent at the consumer due to T8):

| Stuck state | Threshold | Action |
|---|---|---|
| `REQUESTED` | > 5 min | Re-emit `outbound.picking.requested` |
| `CANCELLATION_REQUESTED` | > 5 min | Re-emit `outbound.picking.cancelled` |
| `SHIPPED` | > 5 min | Re-emit `outbound.shipping.confirmed` |

### Invariants

- One saga per Order; `order_id` unique on `outbound_saga`.
- `saga_id` is placed as the Kafka partition key for all inventory reply events,
  ensuring ordered delivery within a saga.
- State machine is the only path to `state` mutation (T4).
- Saga creation is atomic with the first outbox row (`outbound.picking.requested`)
  and `Order.startPicking()` — all in one `@Transactional`.

---

## 7. OutboundOutbox (infrastructure)

Per T3. Same pattern as siblings.

| Field | Type | Notes |
|---|---|---|
| `id` | UUID | PK |
| `aggregate_type` | String (40) | `ORDER` / `OUTBOUND_SAGA` / `SHIPMENT` |
| `aggregate_id` | UUID | |
| `event_type` | String (60) | `outbound.order.received` / `.cancelled` / `outbound.picking.requested` / `.cancelled` / `.completed` / `outbound.packing.completed` / `outbound.shipping.confirmed` |
| `event_version` | String (10) | `v1` |
| `payload` | JSONB | Per `outbound-events.md` |
| `partition_key` | String (60) | `saga_id` for saga events; `order_id` for order lifecycle events |
| `created_at` | Instant | |
| `published_at` | Instant | Nullable until published |

---

## 8. EventDedupe (infrastructure)

Per T8.

| Field | Type | Notes |
|---|---|---|
| `event_id` | UUID | PK |
| `event_type` | String (60) | |
| `processed_at` | Instant | |
| `outcome` | enum `APPLIED` / `IGNORED_DUPLICATE` / `FAILED` | |

Retention: 30 days.

---

## 9. ErpOrderWebhookInbox / ErpOrderWebhookDedupe (infrastructure)

Identical pattern to `inbound-service` ASN webhook:

**ErpOrderWebhookInbox**: `(event_id PK, raw_payload, signature, received_at, status, processed_at, failure_reason)`

**ErpOrderWebhookDedupe**: `(event_id PK, received_at)` — 7-day retention.

Background processor: `PENDING → APPLIED` under `ReceiveOrderUseCase` which creates
Order + OutboundSaga + first outbox row atomically.

---

## 10. TmsRequestDedupe (infrastructure)

Per `integration-heavy` rule I4 — idempotency toward TMS.

| Field | Type | Notes |
|---|---|---|
| `request_id` | UUID | PK; = `shipment.id`, used as `Idempotency-Key` to TMS |
| `sent_at` | Instant | Time of first successful HTTP dispatch |
| `response_snapshot` | JSONB | TMS ack payload (or error body on failure) |

If TMS vendor honours the idempotency key, re-sends are absorbed. If not,
this table acts as a local gate: only one successful send per `request_id`.

---

## 11. MasterReadModel (local cache; read-only from this service's POV)

Same snapshot pattern as siblings. Populated by master `*` consumers.

| Snapshot | Key fields | Critical for |
|---|---|---|
| `SkuSnapshot(id, sku_code, tracking_type, status, master_version)` | `id` | Order / picking validation (`SKU_INACTIVE`, `LOT_REQUIRED`) |
| `LotSnapshot(id, sku_id, lot_no, expiry_date, status, master_version)` | `id` | Lot-specific order lines |
| `PartnerSnapshot(id, partner_code, partner_type, status, master_version)` | `id` | Order customer validation (`PARTNER_INVALID_TYPE`) |
| `LocationSnapshot(id, location_code, warehouse_id, status, master_version)` | `id` | Picking source location validation |
| `WarehouseSnapshot(id, warehouse_code, status, master_version)` | `id` | Existence / status check |

`master_version` — out-of-order events with older version dropped. Eventual
consistency only; W6 is local-only in v1.

---

## Entity Relationship Diagram

```
   Partner (master, via snapshot) ──────── customer ref ──────> Order 1──N OrderLine
                                                                   │
                                                                   │ 1:1
                                                                   ▼
                                                           OutboundSaga (saga state)

   Order 1──────────────────────────────────────────────────1 PickingRequest
                                                                 │ 1:N
                                                                 ▼
                                                         PickingRequestLine

   Order 1──────────────────────────────────────────────────1 PickingConfirmation
                                                                 │ 1:N
                                                                 ▼
                                                      PickingConfirmationLine

   Order 1──────────────────────────────────────────────────N PackingUnit
                                                                 │ 1:N
                                                                 ▼
                                                           PackingUnitLine

   Order 1──────────────────────────────────────────────────1 Shipment

   PickingRequest ──────────────────────── id → Reservation (inventory-service, by id)
```

---

## Aggregate Boundaries

| Aggregate root | Owns | Cross-aggregate via |
|---|---|---|
| Order | OrderLines, order status | events (`outbound.*`); saga is its own aggregate |
| PickingRequest | PickingRequestLines | events (`outbound.picking.requested`, saga coordination) |
| PickingConfirmation | PickingConfirmationLines | `Order.completePicking()` called in same use-case TX |
| PackingUnit | PackingUnitLines | `Order.completePacking()` called when all units sealed |
| Shipment | TMS status | `outbound.shipping.confirmed` event; fires once, then TMS call |
| OutboundSaga | saga state | Events from `inventory-service` advance the saga; saga triggers `Order` state transitions via the coordinator |

Single-use-case multi-aggregate writes permitted:

- **`ConfirmShippingUseCase`**: Order.confirmShipping() + create Shipment + write
  Outbox → all in one TX (W1, W5).
- **`RequestPickingUseCase`**: Order.startPicking() + create PickingRequest +
  create OutboundSaga + write Outbox → one TX.
- All other use-cases touch exactly one aggregate.

---

## Forbidden Patterns (in code)

- ❌ JPA entity used as domain model — Hexagonal rule
- ❌ Direct `UPDATE order SET status = ?` / `UPDATE outbound_saga SET state = ?`
  bypassing domain methods (T4)
- ❌ Cancellation of Order in `SHIPPED` status (`ORDER_ALREADY_SHIPPED`)
- ❌ Distributed transaction with `inventory-service` (T2). Coordination via
  saga events only
- ❌ `SELECT FOR UPDATE` on any aggregate row (T5)
- ❌ Creating a second PickingRequest per Order (unique constraint)
- ❌ Modifying OrderLines after `PICKING` status is entered
- ❌ Partial-shipment confirms in v1 — all lines must be confirmed at once
- ❌ Writing MasterReadModel from REST or use-case paths (consumer-only write)
- ❌ Hard delete of any aggregate row in v1

---

## Reference Data Snapshot (v1 Seed)

`outbound-service` has no business seed data at deployment. Dev / standalone
profile seeds:

- 1 Order in `SHIPPED` status for smoke-test query coverage.
- MasterReadModel populated by replaying `master.*` from offset 0.

Flyway `V99__seed_dev_data.sql`, profile `dev` or `standalone`.

---

## Open Items

- `specs/services/outbound-service/state-machines/order-status.md` — Order state
  machine standalone diagram
- `specs/services/outbound-service/state-machines/saga-status.md` — saga state
  machine standalone diagram
- `specs/services/outbound-service/sagas/outbound-saga.md` — full saga document
  per `transactional` Required Artifact 2
- `specs/services/outbound-service/workflows/outbound-flow.md` — per
  `rules/domains/wms.md` Required Artifact 4
- `specs/services/outbound-service/idempotency.md` — REST + webhook + event +
  saga-level strategy
- `specs/services/outbound-service/external-integrations.md` — TMS + ERP
  catalog (timeouts, circuit, retry, secrets)
- `platform/error-handling.md` — register `STATE_TRANSITION_INVALID` (global),
  `WAREHOUSE_MISMATCH` (shared with inbound), `PARTNER_INVALID_TYPE` (shared),
  `LOT_REQUIRED` (shared), `PICKING_INCOMPLETE` (new for outbound)

---

## References

- `architecture.md` (this directory)
- `rules/domains/wms.md` — Outbound bounded context, W1, W2, W4, W5, W6 and
  Standard Error Codes
- `rules/traits/transactional.md` — T2 (no dist TX), T3 (outbox), T4 (no direct
  status), T5 (optimistic lock), T6 (compensation), T7 (saga), T8 (eventId dedupe)
- `rules/traits/integration-heavy.md` — I1–I4, I7–I9 (TMS adapter); I6 (webhook)
- `specs/services/inventory-service/architecture.md` — saga counterpart;
  consumer of `outbound.picking.requested` / `.cancelled` / `shipping.confirmed`
- `specs/services/inbound-service/architecture.md` — webhook pattern reference
- `specs/contracts/http/outbound-service-api.md` — REST endpoint shapes (Open Item)
- `specs/contracts/events/outbound-events.md` — published event payloads (Open Item)
- `specs/contracts/webhooks/erp-order-webhook.md` — ERP webhook contract (Open Item)
