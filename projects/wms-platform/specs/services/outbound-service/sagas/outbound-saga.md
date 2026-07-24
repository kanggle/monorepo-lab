# outbound-service — Outbound Saga

Full saga document. Required artifact per `rules/traits/transactional.md`
Required Artifact 2 ("saga-별 보상 경로 명세 (per-saga compensation
spec)") and `rules/domains/wms.md` Required Artifact 5.

This document specifies the **Outbound Saga** end-to-end: participants,
steps, compensation paths, recovery, concurrency, failure taxonomy, and
observability. It is the canonical reference; implementation must match.

For diagrams and per-transition rules, see
[`../state-machines/saga-status.md`](../state-machines/saga-status.md).
For the workflow narrative, see
[`../workflows/outbound-flow.md`](../workflows/outbound-flow.md).

---

## 1. Saga Overview

### 1.1 Purpose

Order fulfilment requires coordinated state changes across **two
services**:

- `outbound-service` — owns Order, PickingRequest, PickingConfirmation,
  PackingUnit, Shipment, OutboundSaga
- `inventory-service` — owns Inventory rows, Reservations, InventoryMovements

A single distributed transaction across two services is forbidden (per
trait `transactional` rule T2). The Outbound Saga is the **choreographed
coordination protocol** that satisfies T6 (compensation required) and T7
(saga has explicit state machine + persistence + concurrency model).

### 1.2 Scope

| Concern | In scope | Out of scope (v1) |
|---|---|---|
| Order reception | ✅ | — |
| Reserve / release / consume stock | ✅ (orchestrated via events) | — |
| Operator picking confirmation | ✅ (REST) | per-line confirmation (v2) |
| Operator packing | ✅ (REST) | — |
| Operator shipping confirmation | ✅ (REST) | — |
| Carrier dispatch | Relocated to scm `logistics-service` (consumes `outbound.shipping.confirmed`, ADR-MONO-053 §D8) | rating / tracking (logistics-service) |
| Compensation: pre-ship cancellation | ✅ (release reserved stock) | — |
| Compensation: post-ship reversal | ❌ | RMA inbound (v2) |
| Multi-warehouse split-saga | ❌ | v2 |
| Wave / batch sagas (multi-order) | ❌ | v2 |

### 1.3 Participants

| Service | Role | Owned aggregates |
|---|---|---|
| `outbound-service` | **Orchestrator (state-keeper)** | Order, OutboundSaga, PickingRequest, PickingConfirmation, PackingUnit, Shipment |
| `inventory-service` | **Choreographed participant** | Inventory, Reservation, InventoryMovement |
| scm `logistics-service` | **Downstream event consumer** — dispatches carrier off `outbound.shipping.confirmed` (ADR-MONO-053 §D8); not part of the outbound saga | Dispatch, Shipment leg |

Outbound is the **only** service that reads/writes `outbound_saga`; the
saga state lives entirely in outbound's owned database. Inventory carries
the `sagaId` in its events solely for correlation (it never persists
saga state).

### 1.4 Choreographed (not Orchestrated)

Despite outbound being the "state-keeper", the protocol is
**choreographed** — each participant reacts to events; no service issues
synchronous RPC commands at saga steps. Concretely:

- outbound publishes `outbound.picking.requested` and **waits** for
  `inventory.reserved` (does not call inventory directly).
- outbound publishes `outbound.shipping.confirmed` and **waits** for
  `inventory.confirmed`.
- outbound publishes `outbound.picking.cancelled` and **waits** for
  `inventory.released`.

The saga aggregate is the durable record of "where are we" — `state`
column on `outbound_saga` plus `version` for optimistic concurrency.

---

## 2. Saga Steps

### 2.1 Step 1 — REQUESTED → emit `outbound.picking.requested`

**Trigger**: `ReceiveOrderUseCase` (REST manual or webhook background
processor).

**Atomic actions inside one `@Transactional`**:

1. Insert `Order` (status=`RECEIVED`).
2. Order.startPicking() → status=`PICKING`.
3. Insert `OrderLine`s.
4. `PickingPlanner` (domain service) computes per-line `location_id` from
   MasterReadModel.
5. Insert `PickingRequest` (status=`PENDING`) and `PickingRequestLine`s
   (`picking_request.id` doubles as the inventory-side `reservation_id`).
6. Insert `OutboundSaga` (state=`REQUESTED`, version=0).
7. Insert outbox row: `outbound.order.received`
   (partition_key=`order_id`).
8. Insert outbox row: `outbound.picking.requested`
   (partition_key=`saga_id`, payload includes `sagaId`,
   `reservationId=picking_request.id`, lines).

**Outcome**:
- Saga state: `REQUESTED`
- Order state: `PICKING`
- Awaiting Kafka reply: `inventory.reserved`

**Failure modes**:
- Validation failure (partner inactive, SKU inactive, warehouse mismatch,
  lot required) → 4xx response (REST) or `erp_order_webhook_inbox.status =
  FAILED` (webhook).
- DB write failure → TX rolls back; nothing emitted; caller gets 5xx.
- Crash after TX commit but before broker ACK → outbox publisher will
  re-publish on next tick (idempotent at inventory consumer via dedupe).

### 2.2 Step 2 — Saga: `inventory.reserved` consumed → RESERVED

**Trigger**: `InventoryReservedConsumer` polls Kafka and receives
`inventory.reserved` (partition_key=`sagaId`).

**Inside the consumer's `@Transactional`**:

1. `EventDedupePort` inserts `outbound_event_dedupe(event_id, ...)`.
   Conflict → no-op (return).
2. Load `OutboundSaga` by `sagaId`.
3. `OutboundSaga.apply(InventoryReservedEvent)`:
   - If state == `REQUESTED`: transition to `RESERVED`, version++.
   - If state ∈ {`RESERVED`, `PICKING_CONFIRMED`, ...}: silent no-op
     (already-applied — see [`../idempotency.md`](../idempotency.md) §4).
   - If state == `RESERVE_FAILED` or `CANCELLED`: log WARN, throw (DLT
     route).
4. Update `PickingRequest.status = SUBMITTED`.
5. Commit.

**Outcome**:
- Saga state: `RESERVED`
- Order state: `PICKING` (unchanged)
- Awaiting REST: `confirmPicking`

**Failure modes**:
- `OptimisticLockingFailureException`: retry; usually saga has advanced
  (REST cancel race) — saga-guard absorbs on retry.
- DB unavailable: retry per Spring Kafka backoff; DLT after exhaustion;
  saga sweeper re-emits `outbound.picking.requested` after 5min,
  inventory replays `inventory.reserved`.

### 2.3 Step 3 (REST) — Confirm Picking → PICKING_CONFIRMED

**Trigger**: `ConfirmPickingUseCase` (REST `POST
/picking-requests/{id}/confirmations`).

**Pre-conditions**: Saga state == `RESERVED`. PickingRequest exists.

**Atomic actions inside one `@Transactional`**:

1. Idempotency check (Redis).
2. Load PickingRequest, OutboundSaga.
3. Validate per-line: `qty_confirmed == order_line.qty_ordered` (no
   short-pick in v1); `lot_id` provided for LOT-tracked SKUs;
   `actual_location_id` is ACTIVE.
4. Insert `PickingConfirmation` + `PickingConfirmationLine`s.
5. `Order.completePicking()` → status=`PICKED`.
6. `OutboundSaga.transitionTo(PICKING_CONFIRMED)`, version++.
7. Outbox: `outbound.picking.completed` (partition_key=`saga_id`).
8. Commit.

**Outcome**:
- Saga state: `PICKING_CONFIRMED`
- Order state: `PICKED`
- Awaiting REST: `createPackingUnit` / `sealPackingUnit`

**Notes**: `outbound.picking.completed` is **operational** — admin-service
consumes it for KPI projections. Inventory does NOT consume it (reserved
stock stays reserved until `outbound.shipping.confirmed`).

### 2.4 Step 4 (REST, multi-call) — Pack → PACKING_CONFIRMED

**Trigger**: One or more `CreatePackingUnitUseCase` (`POST
/orders/{id}/packing-units`) followed by `SealPackingUnitUseCase` (`PATCH
/packing-units/{id}` with `status: SEALED`).

**Pre-conditions**: Saga state == `PICKING_CONFIRMED`. Order in `PICKED`
or `PACKING`.

**Atomic actions** (only the **last** seal that completes packing
transitions the saga):

1. Idempotency check (Redis).
2. Load PackingUnit, Order, OutboundSaga.
3. Validate qty per line; `packing_unit.status == OPEN`.
4. PackingUnit.seal() → SEALED.
5. Check completeness: `sum(packing_unit_line.qty) == order_line.qty_ordered`
   for every line, and every PackingUnit for this order is SEALED.
6. **If incomplete**: commit with no saga/order transition, no outbox row.
7. **If complete**:
   - `Order.completePacking()` → status=`PACKED`.
   - `OutboundSaga.transitionTo(PACKING_CONFIRMED)`, version++.
   - Outbox: `outbound.packing.completed` (partition_key=`saga_id`).
8. Commit.

**Outcome (when complete)**:
- Saga state: `PACKING_CONFIRMED`
- Order state: `PACKED`
- Awaiting REST: `confirmShipping`

### 2.5 Step 5 (REST) — Confirm Shipping → SHIPPED

**Trigger**: `ConfirmShippingUseCase` (REST `POST
/orders/{id}/shipments`).

**Pre-conditions**: Saga state == `PACKING_CONFIRMED`. Order in `PACKED`.

**Atomic actions inside one `@Transactional`**:

1. Idempotency check (Redis).
2. Load Order, OutboundSaga.
3. `Order.confirmShipping(actorId)` → status=`SHIPPED`.
4. Insert `Shipment` (shipment_no auto-generated).
5. `OutboundSaga.transitionTo(SHIPPED)`, version++.
6. Outbox: `outbound.shipping.confirmed` (partition_key=`saga_id`,
   payload includes `reservationId`, per-line confirmed quantities).
7. Commit.

**After commit (separate concerns)**:
- Outbox publisher emits `outbound.shipping.confirmed` to Kafka.
  inventory-service consumes → `confirm()` reservation → emits
  `inventory.confirmed`.
- The scm `logistics-service` also consumes `outbound.shipping.confirmed` and
  dispatches the carrier (ADR-MONO-053 §D8) — outside this saga.

**Outcome**:
- Saga state: `SHIPPED`
- Order state: `SHIPPED`
- Awaiting Kafka reply: `inventory.confirmed`

### 2.6 Step 6 — Saga: `inventory.confirmed` consumed → COMPLETED

**Trigger**: `InventoryConfirmedConsumer` consumes `inventory.confirmed`.

**Inside the consumer's `@Transactional`**:

1. `EventDedupePort` insert. Conflict → no-op.
2. Load `OutboundSaga`.
3. `OutboundSaga.apply(InventoryConfirmedEvent)`:
   - If state == `SHIPPED`: transition to `COMPLETED`, version++.
   - If state == `COMPLETED`: silent no-op.
   - Other states: log WARN, throw → DLT.
4. Commit.

**Outcome**:
- Saga state: `COMPLETED` (terminal)
- Order state: `SHIPPED` (unchanged)
- Stock fully consumed (inventory side)

`inventory.confirmed` is the **business completion** signal for the saga —
carrier dispatch (owned downstream by the scm `logistics-service`) is
independent and never blocks or advances the outbound saga.

---

## 3. Compensation Paths

Per trait `transactional` rule T6: every step must declare its
compensation. The Outbound Saga has the following compensation matrix.

### 3.1 Compensation Matrix

| Step | Failure trigger | Compensation action | End state |
|---|---|---|---|
| Step 1 (REQUESTED) | Validation fails (partner inactive, SKU inactive, etc.) before commit | TX rollback; nothing emitted | No saga created; caller gets 4xx |
| Step 2 (REQUESTED → RESERVED) | Inventory reports `INSUFFICIENT_STOCK` (via `inventory.reserve.failed`) | None needed (all-or-nothing reserve — no resources held) | Saga: `RESERVE_FAILED`. Order: `BACKORDERED`. Outbox: `outbound.order.cancelled` (reason=BACKORDERED) |
| Step 2 / Step 3 (RESERVED, PICKING_CONFIRMED) | Operator-initiated cancel | Emit `outbound.picking.cancelled`; await `inventory.released` | Saga: `CANCELLATION_REQUESTED → CANCELLED`. Order: `CANCELLED` (immediately at cancel time) |
| Step 4 (PACKING_CONFIRMED) | Operator-initiated cancel | Same as above — picked-and-packed reservations are still releasable | Same |
| Step 6 (SHIPPED → COMPLETED) | Post-ship cancellation | **Forbidden in v1** (`ORDER_ALREADY_SHIPPED`). v2: creates an inbound RMA flow, distinct lifecycle | n/a |

> Carrier dispatch failures are **not** an outbound-saga compensation concern —
> dispatch is owned by the scm `logistics-service` (its own retry / recovery),
> off the `outbound.shipping.confirmed` event (ADR-MONO-053 §D8).

### 3.2 Pre-Pick Cancellation (Saga in REQUESTED, no reservation yet)

A subtle case: the operator cancels **before** `inventory.reserved`
arrives. There is no reservation in inventory-service yet.

```
T0: Saga = REQUESTED, version V
T1: REST cancel arrives. Loads saga at V.
T2: Cancel from REQUESTED is legal. Atomic actions:
     - Order.cancel() → CANCELLED
     - Saga.transitionTo(CANCELLED)  (DIRECTLY to terminal, NOT via CANCELLATION_REQUESTED)
     - Outbox: outbound.order.cancelled
     - NO outbox: outbound.picking.cancelled
       (because no reservation was created)
T3: Commit at V+1.
```

**Subsequent inventory.reserved arrival**: When inventory eventually
emits `inventory.reserved`, outbound's consumer loads the saga (now
`CANCELLED`) and the state-machine guard treats it as a genuinely
impossible transition → log WARN, throw → DLT after retries. Ops
investigates: this means inventory created a reservation that outbound
never asked to cancel. The runbook action is **manual release in
inventory-service** (admin endpoint).

> The race between "REST cancel" and "inventory.reserved" is the most
> intricate corner case. It is documented in
> [`../state-machines/saga-status.md`](../state-machines/saga-status.md)
> § Concurrency and tested in the failure-mode suite.

### 3.3 Mid-Flow Cancellation (Saga in RESERVED, PICKING_CONFIRMED, PACKING_CONFIRMED)

```
T0: Saga = <transitional>, version V
T1: REST cancel arrives. Loads saga at V.
T2: Cancel is legal. Atomic actions:
     - Order.cancel() → CANCELLED
     - Saga.transitionTo(CANCELLATION_REQUESTED)
     - Outbox: outbound.order.cancelled
     - Outbox: outbound.picking.cancelled (carries reservation_id, sagaId)
T3: Commit at V+1.
T4: Async — outbox publisher emits both.
T5: inventory-service consumes outbound.picking.cancelled → ReleaseStockUseCase → emits inventory.released.
T6: outbound consumes inventory.released → Saga.transitionTo(CANCELLED).
```

**Sweeper safety**: If `inventory.released` is lost, the saga sweeper
detects `CANCELLATION_REQUESTED > 5 min` and re-emits
`outbound.picking.cancelled`. inventory-service's dedupe absorbs (or
release is genuinely re-applied — release of a `RELEASED` reservation is
naturally idempotent on inventory's side).

### 3.4 Compensation Guarantees (per T6)

| Guarantee | Mechanism |
|---|---|
| Compensation event fired exactly once per cancelled saga | Atomic with cancel use-case: outbox row is committed in the same TX as `Saga.transitionTo(CANCELLATION_REQUESTED)`. The state guard prevents a second cancel from re-emitting. |
| Compensation handler idempotent | inventory-service's `ReleaseStockUseCase` is idempotent (release of an already-released reservation is a no-op). The saga consumer is idempotent (state-machine guard). |
| Compensation eventually completes even if Kafka loses a message | Saga sweeper re-emits after 5 min. |
| No double-compensation | (a) Outbox row uniqueness ensures one emission per cancel; (b) state-machine guard rejects re-cancel from `CANCELLATION_REQUESTED` or `CANCELLED`. |
| Compensation cannot leak resources | Inventory's `Reservation` row tracks state (`PENDING` / `RELEASED` / `CONFIRMED`); release is bounded by the row's lifecycle. |

---

## 4. Saga Recovery (Sweeper)

### 4.1 Purpose

Per trait `transactional` rule T7 and `architecture.md` § Saga Sweeper, a
background job recovers sagas stuck in transitional states beyond a time
threshold. This is the **failure-recovery loop** that makes the saga
eventually consistent even if a Kafka message is lost or a consumer pod
crashes.

### 4.2 Job Specification

- **Schedule**: every 60 seconds (Spring `@Scheduled`).
- **Concurrency**: `FOR UPDATE SKIP LOCKED` to coordinate across pods.
- **Batch size**: max 100 sagas per tick (configurable
  `outbound.saga.sweeper.batch-size`).
- **Threshold**: 5 minutes since `last_transition_at` (configurable
  `outbound.saga.sweeper.threshold-seconds`).
- **Cap**: 5 re-emissions per saga (configurable
  `outbound.saga.sweeper.max-attempts`, default `5`). After cap → saga
  transitions to terminal `STUCK_RECOVERY_FAILED` and emits
  `outbound.alert.saga.recovery.exhausted` v1 alert event (see
  [state-machines/saga-status.md](../state-machines/saga-status.md) +
  [contracts/events/admin-events.md](../../../contracts/events/admin-events.md)
  §A1 for the alert schema). Ops investigates.

### 4.3 Recovery Actions

| Stuck state | Threshold | Action |
|---|---|---|
| `REQUESTED` | > 5 min | Insert fresh outbox row `outbound.picking.requested` (new envelope eventId, same payload). Increment `Saga.sweeper_attempts`. |
| `CANCELLATION_REQUESTED` | > 5 min | Insert fresh outbox row `outbound.picking.cancelled`. Increment counter. |
| `SHIPPED` | > 5 min AND no `inventory.confirmed` dedupe row found for any of this saga's prior `inventory.confirmed` events | Insert fresh outbox row `outbound.shipping.confirmed`. Increment counter. |

### 4.4 Re-Emission Safety

Re-emission produces a **fresh** Kafka envelope `eventId`. The downstream
defenses are:

1. **inventory-service dedupe** (`inventory_event_dedupe`, 30-day TTL):
   absorbs the same business action when within window.
2. **inventory-service domain idempotency**: `Reservation` UNIQUE on
   `reservation_id` (= our `picking_request.id`). Second reserve with the
   same reservation_id sees existing row, treats as already-applied.
3. **outbound saga state-machine guard** (this service): when inventory
   replies (with possibly-fresh eventId), saga is already `RESERVED` /
   `CANCELLED` / `COMPLETED` → already-applied no-op.

The four-layer defense (REST Redis + webhook DB dedupe + Kafka eventId
dedupe + saga state guard) is the load-bearing correctness boundary; see
[`../idempotency.md`](../idempotency.md).

### 4.5 Stuck Sagas

When a saga exceeds `max-attempts` re-emissions, the sweeper transitions
the saga to terminal state `STUCK_RECOVERY_FAILED` (see
[state-machines/saga-status.md](../state-machines/saga-status.md)),
populates `failure_reason`, increments
`outbound.saga.sweeper.exhausted.count{from_state}`, and emits
`outbound.alert.saga.recovery.exhausted` v1 alert event into the outbox
in the same transaction (T3 atomicity). Ops investigates via the alert
event + the per-saga runbook.

---

## 5. Concurrency Handling

### 5.1 Optimistic Locking

`OutboundSaga.version` is bumped on every transition. Concurrent
transitions on the same saga raise `OptimisticLockingFailureException` on
the second commit. The saga aggregate is loaded fresh on each retry.

Per trait T5: pessimistic locks (`SELECT FOR UPDATE` on saga rows) are
**forbidden**. The sole exception is the saga sweeper's `FOR UPDATE SKIP
LOCKED` for inter-pod coordination — but this locks rows the sweeper is
about to update, not rows being mutated by other code paths.

### 5.2 Saga-Level Idempotency (4th Layer)

See [`../idempotency.md`](../idempotency.md) §4 for the full treatment.
Summary:

`OutboundSaga.apply(event)` checks the (current state, event kind) pair:

- **Legal**: advance state, version++.
- **Already-applied**: silent no-op (log INFO).
- **Genuinely impossible**: log WARN, throw `StateTransitionInvalidException`
  → DLT (Kafka) or 422 (REST).

This layer protects against:
- Sweeper re-emissions producing fresh eventIds that bypass dedupe.
- 30-day eventId-dedupe TTL expiry + operator DLT replay.
- Cross-topic eventId collisions (theoretical — UUID collision).

### 5.3 Intra-Saga Race: Concurrent REST + Kafka

The most common race: REST cancel arriving simultaneously with
`inventory.reserved`. Detailed in
[`../state-machines/saga-status.md`](../state-machines/saga-status.md) §
Concurrency. Resolution mechanism:

1. Both load saga at version V.
2. First commit succeeds at V+1.
3. Second commit raises `OptimisticLockingFailureException`:
   - REST loser → 409 `CONFLICT`. Caller refetches, re-evaluates.
   - Kafka loser → message retried; on retry, saga state has advanced;
     state-machine guard absorbs as no-op (or genuinely impossible — DLT).

### 5.4 Inter-Saga Independence

Two different sagas (different `saga_id`) never block each other. Kafka
partition key `sagaId` ensures replies for different sagas land on
different partitions and can be consumed in parallel by different consumer
pods.

---

## 6. Failure Taxonomy

Failure modes the saga is expected to encounter, with end states and
operator actions.

| Failure | End state | Operator action |
|---|---|---|
| **RESERVE_FAILED** | Saga: `RESERVE_FAILED` (terminal). Order: `BACKORDERED` (terminal). | Customer notified (via `outbound.order.cancelled` reason=BACKORDERED → notification-service); ops creates new order or contacts customer. |
| **CANCELLED (pre-pick)** | Saga: `CANCELLED` (terminal, no compensation needed). Order: `CANCELLED`. | None — clean cancellation. |
| **CANCELLED (mid-flow, post-reserve)** | Saga: `CANCELLED` (after compensation). Order: `CANCELLED`. | None — compensation completed asynchronously. |
| **STUCK_RECOVERY_FAILED (sweeper exhausted)** | Saga: `STUCK_RECOVERY_FAILED` (terminal). `failure_reason` = "sweeper_max_attempts_exceeded". `outbound.saga.sweeper.exhausted.count{from_state}` increments. `outbound.alert.saga.recovery.exhausted` v1 alert event emitted into outbox in same TX. | Investigate Kafka health, inventory-service health. Manual remediation per runbook (admin endpoint `POST /sagas/{id}:force-fail` planned in v2; v1: direct DB inspection + targeted re-emission). |
| **DLT (genuinely impossible saga transition)** | Message routed to `<topic>.DLT`; saga unchanged. | Investigate the originating event. Common cause: protocol violation (inventory emitted a reply that doesn't match outbound's saga state — usually a bug, never a steady-state condition). |

### 6.1 Carrier Dispatch (relocated — ADR-MONO-053 §D8)

The former `SHIPPED_NOT_NOTIFIED` alert state and TMS retry loop were retired
in TASK-BE-560. Carrier dispatch is owned by the scm `logistics-service`, which
consumes `outbound.shipping.confirmed` and runs its own retry / circuit /
recovery (`:retry` on the dispatch). A dispatch failure never appears in the
outbound saga — the saga's terminal states are `COMPLETED`, `CANCELLED`,
`RESERVE_FAILED`, and `STUCK_RECOVERY_FAILED` only.

---

## 7. Observability

### 7.1 Saga-Specific Metrics

Per [`../architecture.md`](../architecture.md) § Observability:

| Metric | Type | Description |
|---|---|---|
| `outbound.saga.active.count` | gauge | Currently in-progress sagas (state ∉ terminal set) |
| `outbound.saga.state.transitions{from, to}` | counter | Transition counter, labelled by from/to state |
| `outbound.saga.completed.duration.seconds` | histogram | Receipt-to-COMPLETED latency, p50/p95/p99 |
| `outbound.saga.failed.count{reason=reserve_failed | stuck_recovery_failed}` | counter | Terminal failures by reason |
| `outbound.saga.compensation.fired.count` | counter | `outbound.picking.cancelled` outbox emissions |
| `outbound.saga.event.already_applied.count{saga_state, event_kind}` | counter | Saga-guard no-op count |
| `outbound.saga.event.invalid_transition.count{saga_state, event_kind}` | counter | Genuinely impossible transitions (alert at >0) |
| `outbound.saga.sweeper.run.count` | counter | Sweeper scheduler tick count |
| `outbound.saga.sweeper.recovery.fired{from_state}` | counter | Sweeper re-emission count per stuck state (`REQUESTED|CANCELLATION_REQUESTED|SHIPPED`) |
| `outbound.saga.sweeper.exhausted.count{from_state}` | counter | Saga transitions to `STUCK_RECOVERY_FAILED` (cap exceeded), tagged by originating state |

### 7.2 Logs (structured JSON)

| Event key | Level | Fields |
|---|---|---|
| `saga_created` | INFO | `sagaId, orderId, source` |
| `saga_state_transition` | INFO | `sagaId, from, to, trigger, version` |
| `saga_event_already_applied` | INFO | `sagaId, sagaState, eventKind, eventId` |
| `saga_event_invalid_transition` | WARN | `sagaId, sagaState, eventKind, eventId` |
| `saga_sweeper_reemission` | INFO | `sagaId, state, attempt` |
| `saga_stuck` | ERROR | `sagaId, state, attempts, failureReason` |
| `saga_completed` | INFO | `sagaId, durationMs` |
| `saga_compensation_fired` | INFO | `sagaId, originalState` |
| `saga_reserve_failed` | WARN | `sagaId, reason` |

### 7.3 Tracing (OpenTelemetry)

Each saga gets a logical trace:

- Root span: `outbound.saga` with attribute `saga.id`.
- Child spans per transition step: `outbound.saga.step.<state>`.
- Cross-service propagation: `traceparent` carried through Kafka headers,
  picked up by inventory-service consumers, returned via
  `inventory.*` events. Outbound consumers re-attach the span.

This produces a multi-service trace from `ReceiveOrderUseCase` to
`InventoryConfirmedConsumer` showing every hop.

---

## 8. Test Matrix

Per [`../architecture.md`](../architecture.md) § Testing Requirements,
the saga has the following test surfaces.

### 8.1 Unit (Domain) — `OutboundSaga` aggregate

- Every legal transition (table in §2 above)
- Every `STATE_TRANSITION_INVALID` case (cross-product minus legal)
- Every already-applied pair returns silent no-op (table in
  [`../state-machines/saga-status.md`](../state-machines/saga-status.md)
  § Re-Delivery Behavior)
- `version` increments on every successful transition
- `failure_reason` populated on `RESERVE_FAILED` / `STUCK_RECOVERY_FAILED`

### 8.2 Application Service (port fakes)

- **Happy path** end-to-end: REQUESTED → RESERVED → PICKING_CONFIRMED →
  PACKING_CONFIRMED → SHIPPED → COMPLETED
- **Reserve-failed branch**: REQUESTED → RESERVE_FAILED; Order →
  BACKORDERED; single outbox row (`outbound.order.cancelled`,
  reason=BACKORDERED)
- **Pre-pick cancel** (REQUESTED): direct → CANCELLED; one outbox row
  (`outbound.order.cancelled`); NO `outbound.picking.cancelled`
- **Mid-flow cancel** (RESERVED): → CANCELLATION_REQUESTED → CANCELLED;
  two outbox rows (`outbound.order.cancelled`,
  `outbound.picking.cancelled`); after consumed `inventory.released` →
  CANCELLED
- **Shipping confirm**: `confirmShipping` → Saga SHIPPED + Shipment created +
  `outbound.shipping.confirmed` outbox row; `inventory.confirmed` → Saga COMPLETED
- Each transition writes outbox row in same TX as state change (verify
  via test that Kafka publish does not occur on TX rollback)

### 8.3 Persistence Adapter (Testcontainers Postgres)

- All `SagaPersistencePort` repo methods (find by sagaId, find by orderId,
  save, findStuck, sweeperBatch with FOR UPDATE SKIP LOCKED)
- Optimistic-lock conflict on `outbound_saga.version`: parallel
  transition attempts → exactly one succeeds, one raises
- Saga sweeper batch query under concurrent pods: each saga claimed by
  exactly one pod

### 8.4 REST Controllers (`@WebMvcTest`)

- All saga-affecting endpoints: `/orders` (create), `:cancel`,
  `/picking-requests/{id}/confirmations`, `/orders/{id}/packing-units`
  (POST + PATCH seal), `/orders/{id}/shipments`
- Idempotency-Key behavior on each (covered in
  [`../idempotency.md`](../idempotency.md))
- Cancel-from-SHIPPED → 422 `ORDER_ALREADY_SHIPPED`
- Confirm-shipping-from-PICKING → 422 `STATE_TRANSITION_INVALID`

### 8.5 Consumers (Testcontainers Kafka)

- `InventoryReservedConsumer`: happy path, redelivery (idempotent
  transition), poison → DLT
- `InventoryReleasedConsumer`: happy path (CANCELLATION_REQUESTED →
  CANCELLED), redelivery, out-of-order arrival
- `InventoryConfirmedConsumer`: happy path, redelivery
- `InventoryReserveFailedConsumer`:
  REQUESTED → RESERVE_FAILED
- Out-of-order: `inventory.confirmed` arriving before saga is `SHIPPED`
  → DLT (genuinely impossible)
- Sweeper re-emit safety: feed `inventory.reserved` twice with **fresh**
  eventIds (bypassing dedupe) — saga state, version, and outbox row count
  are unchanged after second delivery (state-machine guard absorbs)

### 8.6 Saga Sweeper (Testcontainers Kafka + Postgres)

- Sagas in `REQUESTED` past threshold → fresh outbox row written
- Sagas in `CANCELLATION_REQUESTED` past threshold → fresh outbox row
- Sagas in `SHIPPED` past threshold without `inventory.confirmed` →
  fresh outbox row
- Re-emitted event arrives at consumer → saga absorbs as no-op (state-machine guard)
- Sweeper attempts cap: after `max-attempts`, saga moves to STUCK,
  metric fires
- Concurrent sweepers (2 pods): each saga claimed by exactly one
  (`FOR UPDATE SKIP LOCKED` test)

### 8.7 Failure-Mode Suite (per trait `transactional` Required Artifact 5)

- Same `Idempotency-Key` POST twice → identical result, single saga
- Same webhook event-id → single saga
- Reserve fails → saga `RESERVE_FAILED`, order `BACKORDERED`, no double
  compensation
- Pre-pick cancel → release event fired exactly once, idempotent on
  redelivery
- Saga restart from any non-terminal state via background sweeper
- Race: REST cancel + `inventory.reserved` simultaneous → exactly one
  resolution path; no double compensation

### 8.8 Contract Tests

- All published event schemas in `outbound-events.md`
- All consumed event schemas (`inventory.*`) match inventory-service's
  outbox per `inventory-events.md`
- Webhook contract per `erp-order-webhook.md`

---

## 9. Open Questions

These are intentionally deferred to the implementation phase or follow-up
specs:

1. **Force-fail admin endpoint** — `POST /sagas/{id}:force-fail` for
   ops to terminate STUCK sagas. v1: direct DB; v2: REST endpoint.
2. **Sweeper attempt cap value** — resolved: shipped at `5`
   (`outbound.saga.sweeper.max-attempts`, see §4.2 / §4.5); may be tuned
   based on prod observation.
3. **Wave / batch saga model** — multi-order sagas (Wave aggregate) are
   v2; this saga document is per-order only.
4. **Returns / RMA inbound saga** — distinct from Outbound Saga;
   creates an inbound RMA flow. v2.

---

## 10. References

- [`../architecture.md`](../architecture.md) § Outbound Saga, § Saga
  Sweeper, § Carrier Dispatch (relocated), § Testing Requirements
- [`../domain-model.md`](../domain-model.md) §6 OutboundSaga
- [`../state-machines/saga-status.md`](../state-machines/saga-status.md)
  — formal saga state machine
- [`../state-machines/order-status.md`](../state-machines/order-status.md)
  — parallel Order state machine
- [`../workflows/outbound-flow.md`](../workflows/outbound-flow.md) —
  narrative workflow
- [`../idempotency.md`](../idempotency.md) §4 Saga-Level Idempotency
- [`../external-integrations.md`](../external-integrations.md) §2 — carrier
  dispatch relocation (ADR-MONO-053 §D8)
- `specs/services/inventory-service/architecture.md` — counterpart
  participant; consumer of `outbound.picking.requested` /
  `outbound.picking.cancelled` / `outbound.shipping.confirmed`; emitter
  of `inventory.reserved` / `.released` / `.confirmed` / `.adjusted`
- `specs/contracts/events/outbound-events.md` — outbox event schemas
- `specs/contracts/events/inventory-events.md` — consumed event
  schemas
- `specs/contracts/http/outbound-service-api.md` — REST endpoint
  shapes
- `rules/traits/transactional.md` — T2 (no dist TX), T3 (outbox), T4
  (no direct status), T5 (optimistic lock), T6 (compensation), T7 (saga),
  T8 (eventId dedupe)
- `rules/traits/integration-heavy.md` — I6 (ERP webhook reception)
- `rules/domains/wms.md` — Outbound bounded context, W1, W4, W5, W6
- `platform/architecture-decision-rule.md`
- `platform/error-handling.md` — `STATE_TRANSITION_INVALID`,
  `ORDER_ALREADY_SHIPPED`, `EXTERNAL_SERVICE_UNAVAILABLE`,
  `EXTERNAL_TIMEOUT`, etc.
- `messaging/outbox-pattern/SKILL.md`
- `messaging/idempotent-consumer/SKILL.md`
