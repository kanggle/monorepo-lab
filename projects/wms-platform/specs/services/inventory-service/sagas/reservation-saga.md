# inventory-service — Reservation Saga (Participant View)

Reservation saga document — `inventory-service` side. Required artifact per
`rules/traits/transactional.md` Required Artifact 2 ("saga-별 보상 경로
명세 (per-saga compensation spec)") and `rules/domains/wms.md` Required
Artifact 5.

This document specifies how `inventory-service` participates in the
**Outbound Saga**: which events it consumes, which mutations it performs
atomically, which events it emits, the TTL expiry self-loop (the inventory
side's only saga timer), and the compensation rule. The companion
state-machine diagram lives at
[`../state-machines/reservation-status.md`](../state-machines/reservation-status.md).

The orchestrator-side view is in
[`../../outbound-service/sagas/outbound-saga.md`](../../outbound-service/sagas/outbound-saga.md);
that document is canonical for cross-service flow. This file is canonical
for inventory-side behavior only — implementation must match.

---

## 1. Saga Overview

### 1.1 Purpose

`Reservation` is the inventory-side projection of an outbound picking
request: it pins `available_qty → reserved_qty` quantities at chosen
inventory rows until either shipping confirms (reserve → consume per W4)
or cancellation releases them back. `Reservation` is the aggregate that
delivers W4 (reserve → confirm two-phase) and W5 (no decrement until
shipped) inside `inventory-service`.

### 1.2 Participation Pattern (choreographed callee)

Per `outbound-saga.md` § 1.4, the outbound saga is **choreographed**, not
orchestrated. `inventory-service` therefore:

- **Never** issues synchronous RPC to other services as part of saga
  steps.
- Reacts to inbound Kafka events from `outbound-service`
  (`outbound.picking.requested` / `outbound.picking.cancelled` /
  `outbound.shipping.confirmed`).
- Emits its own factual events (`inventory.reserved` / `.released` /
  `.confirmed`) via outbox (T3) in the **same transaction** as the
  domain mutation.
- Persists **no** saga state — `OutboundSaga` lives only in `outbound_db`.
  `Reservation.id`, `Reservation.picking_request_id`, and the `sagaId`
  carried on inbound events together correlate inventory's row state with
  the outbound saga's view; no second source-of-truth is created here.

### 1.3 Owned vs Foreign Aggregates

| Aggregate | Owner | Role in this saga |
|---|---|---|
| `Reservation` (+ `ReservationLine`) | inventory-service | Aggregate that this saga's state machine lives on |
| `Inventory` (+ `InventoryMovement`) | inventory-service | Bucket-quantity mutation target; ledger appended every step |
| `Order` / `OutboundSaga` / `PickingRequest` / `Shipment` | outbound-service | **Foreign** — never read or written directly; correlated by id only |

---

## 2. Operations (per-event)

Each operation is one Kafka consumer's `@Transactional` handler. The
transaction commits domain mutation + Outbox + `event_dedupe` row
together; the consumer ack happens after commit.

### 2.1 Reserve — trigger `outbound.picking.requested`

**Consumer**: `OutboundPickingRequestedConsumer`.

**Atomic actions inside one `@Transactional`**:

1. Insert `event_dedupe(event_id, event_type, processed_at, outcome=APPLIED)`
   — fails fast on replay (T8).
2. Insert `Reservation(picking_request_id=event.pickingRequestId,
   warehouse_id=event.warehouseId, status=RESERVED,
   expires_at=now + warehouse.reservation_ttl)`.
3. Insert N `ReservationLine`s (one per `event.lines[i]`).
4. For each line: `Inventory.reserve(qty, reservationId)` — domain method
   bumps `version`, writes **two** `InventoryMovement` rows
   (`AVAILABLE delta=-N` + `RESERVED delta=+N`, both with
   `reservation_id`, both with `reason_code=PICKING`).
5. Insert outbox row: `inventory.reserved` (`partition_key=locationId` of
   first line per `inventory-events.md` § 4).

**Emits**: `inventory.reserved`.

**Idempotency**: `event_dedupe.event_id` UNIQUE (T8) AND
`Reservation.picking_request_id` UNIQUE — a replay short-circuits at the
first INSERT and rolls back without side effect; the consumer ack
proceeds, dropping the duplicate.

**Failure — `INSUFFICIENT_STOCK`**: `Inventory.reserve(...)` throws when
`available_qty < qty`. The transaction rolls back; **no `Reservation`
row, no `Movement`, no outbox** are written. Instead, the consumer's
catch block writes an `inventory.reserve.failed{reason=INSUFFICIENT_STOCK}`
outbox row in a *separate* `REQUIRES_NEW` transaction so outbound's saga
can transition to `RESERVE_FAILED` (per `outbound-saga.md` § 3.2). No
compensation event is needed — nothing was held.

**Other failures**:
- Stale `MasterReadModel` (`LOCATION_INACTIVE` / `SKU_INACTIVE` /
  `LOT_INACTIVE` / `LOT_EXPIRED`) → same as `INSUFFICIENT_STOCK` —
  rolls back + emits `inventory.reserve.failed{reason=<code>}`.
- Postgres outage / DB OL conflict → consumer retry (per Kafka consumer
  retry policy); after retry budget exhausted → DLT (T8 + I5).

### 2.2 Release — triggers: `outbound.picking.cancelled` OR TTL OR manual

Three entry points, one domain operation. The `released_reason` enum on
`Reservation` records which one fired.

| Entry point | Reason | Caller |
|---|---|---|
| `outbound.picking.cancelled` | `CANCELLED` | `OutboundPickingCancelledConsumer` |
| TTL expiry sweep (§ 3) | `EXPIRED` | `ReleaseReservationService.releaseExpired(...)` (`@Scheduled`) |
| `INVENTORY_ADMIN` REST call | `MANUAL` | `ReleaseReservationUseCase` (REST controller) |

**Atomic actions inside one `@Transactional`**:

1. Insert `event_dedupe` (consumer-driven only; TTL/manual paths skip
   this step — they have their own idempotency: TTL by status filter,
   manual by REST `Idempotency-Key`).
2. Load `Reservation` by `picking_request_id` (consumer) or `id`
   (TTL / manual). **If already `RELEASED` or `CONFIRMED` → no-op**
   (state machine terminal-once; consumer logs and returns). § 5 below.
3. `Reservation.release(reason)` — bumps `version`, sets `status=RELEASED`,
   `released_reason=<reason>`, `released_at=now`.
4. For each line: `Inventory.release(qty, reservationId, reason)` — bumps
   `version`, writes **two** `Movement` rows (`RESERVED delta=-N` +
   `AVAILABLE delta=+N`, both with `reservation_id`, `reason_code` =
   `PICKING_CANCELLED` / `PICKING_EXPIRED` / `ADJUSTMENT_RECLASSIFY`
   depending on reason).
5. Insert outbox row: `inventory.released` (payload carries
   `reason=CANCELLED|EXPIRED|MANUAL`).

**Emits**: `inventory.released`.

**Idempotency**: dual-layer. Consumer path uses `event_dedupe`; all three
paths additionally guard via the terminal-once state-machine rule (§ 5)
— a second release attempt on a `RELEASED` row is a no-op that still
ACKs the inbound event.

### 2.3 Confirm — trigger `outbound.shipping.confirmed`

**Consumer**: `OutboundShippingConfirmedConsumer`.

**Atomic actions inside one `@Transactional`**:

1. Insert `event_dedupe`.
2. Load `Reservation` by `picking_request_id`. **If already `CONFIRMED`
   → no-op** (idempotent replay). **If `RELEASED` → throw**
   `RESERVATION_ALREADY_RELEASED` → DLT.
3. Per-line **quantity equality check** (W4 / W5): the shipped quantity
   on `event.lines[i].quantity` must equal the `ReservationLine.quantity`
   exactly. Mismatch → throw `RESERVATION_QUANTITY_MISMATCH` → DLT. v1
   no partial shipments; over-/under-ship is a domain-model violation
   from upstream and must not silently mutate. (§ 6 Edge.)
4. `Reservation.confirm()` — bumps `version`, sets `status=CONFIRMED`,
   `confirmed_at=now`.
5. For each line: `Inventory.confirm(qty, reservationId)` — bumps
   `version`, writes **one** `Movement` row (`RESERVED delta=-N`,
   `reason_code=SHIPPING_CONFIRMED`). No paired `AVAILABLE` row — the
   stock is consumed, not returned.
6. Insert outbox row: `inventory.confirmed`.

**Emits**: `inventory.confirmed`.

**Idempotency**: `event_dedupe` + terminal-once on `CONFIRMED`.

---

## 3. TTL Expiry (Category D — ADR-MONO-005 § D6)

`inventory-service` is the monorepo's **Category D reference
implementation** ([`../../../../../../docs/adr/ADR-MONO-005-saga-timeout-escalation-dead-letter-policy.md`](../../../../../../docs/adr/ADR-MONO-005-saga-timeout-escalation-dead-letter-policy.md)
§ D6) — the saga has no orchestrator-driven timer, but each
`Reservation` row carries `expires_at` and a scheduled sweep auto-releases
the row.

| Aspect | Value | Source |
|---|---|---|
| Default TTL | 24 hours, configurable per warehouse | `architecture.md § Saga Participation` (line 510) |
| Job interval | `inventory.reservation.ttl-job.interval-ms=60000` (60s) | `architecture.md § Saga / Long-running Flow` (line 523) |
| Batch size | `inventory.reservation.ttl-job.batch-size=200` | same |
| Transaction boundary | each row in its own `@Transactional` | same — one failure does not abort the batch |
| OL race | OL conflict (manual release races with sweep) → retry next tick | same |
| Terminal state | `RELEASED` (reason `EXPIRED`) | `domain-model.md § 3 Reservation § State Machine` |
| Metric | `inventory.reservation.expiry.swept.total` counter (incremented by released count per tick) | ADR-MONO-005 § D5 + `architecture.md § Saga / Long-running Flow` line 523 |

Domain caller: `ReleaseReservationService.releaseExpired(...)` (called by
the `@Scheduled` sweeper). Each row's release follows § 2.2 atomic
actions (steps 2-5; step 1 `event_dedupe` skipped — TTL is not an inbound
event).

Selection query: `WHERE status = 'RESERVED' AND expires_at < NOW()
ORDER BY expires_at ASC LIMIT 200`. `CONFIRMED` rows are excluded by the
status filter so a stale TTL never races a just-shipped row.

---

## 4. Compensation

**Single rule**: the compensation for any **non-confirmed** `Reservation`
is `Release` (§ 2.2). The `release()` domain method itself is the
compensation primitive — no separate compensating saga.

| Originating action | Compensation |
|---|---|
| `Reserve` (RESERVED row exists) | `Release(reason=CANCELLED)` via `outbound.picking.cancelled` |
| `Reserve` followed by TTL elapse | `Release(reason=EXPIRED)` via TTL sweep |
| `Reserve` rejected (INSUFFICIENT_STOCK) | none — nothing was held |
| `Confirm` (CONFIRMED terminal) | **none — irreversible**; post-ship reversal is modelled as RMA inbound in v2, not as state regression here |

The all-or-nothing reserve property (W4) means a partially-reserved
`Reservation` does not exist: either every line reserves or the
transaction rolls back. Consequently, compensation is never partial.

Cross-aggregate compensation is unnecessary: `Inventory.release(...)`
restores the `available_qty / reserved_qty` buckets atomically inside
the same transaction as `Reservation.release()`.

---

## 5. Concurrency / Idempotency Guarantees

| Layer | Mechanism | Source |
|---|---|---|
| Aggregate concurrency | `Reservation.version` (T5 optimistic lock) | `domain-model.md § Common Aggregate Shape` |
| Bucket concurrency | `Inventory.version` (T5) — version-check UPDATE; retry next tick on conflict | `domain-model.md § 1 Inventory § Invariants` |
| Event replay | `event_dedupe(event_id PK)`, 30-day retention (T8) | `domain-model.md § 7 EventDedupe` |
| Domain-level duplicate-reserve | `Reservation.picking_request_id` UNIQUE — second `outbound.picking.requested` for the same picking-request is rejected by DB constraint, rolled back, ACKed | `domain-model.md § 3 Reservation § Invariants` |
| Domain-level terminal-once | State-machine rule: `CONFIRMED` / `RELEASED` are terminal; `confirm()` / `release()` on a terminal row are no-op | [`../state-machines/reservation-status.md`](../state-machines/reservation-status.md) |
| REST idempotency (manual release) | `Idempotency-Key` header + Redis 24h TTL | `idempotency.md` |

**No cross-service synchronous RPC** in saga steps (§ 1.2). Cross-service
coordination is **only** via Kafka events on owned topics.

---

## 6. Failure Modes

| Scenario | Behaviour | Recovery |
|---|---|---|
| `INSUFFICIENT_STOCK` on Reserve | Rolls back; emits `inventory.reserve.failed{reason=INSUFFICIENT_STOCK}` in `REQUIRES_NEW` TX | outbound saga → `RESERVE_FAILED` (terminal); no compensation needed |
| Stale `MasterReadModel` (`LOCATION_INACTIVE` / `SKU_INACTIVE` / `LOT_INACTIVE` / `LOT_EXPIRED`) on Reserve | Same as `INSUFFICIENT_STOCK` — rolls back + emits `inventory.reserve.failed{reason=<code>}` | outbound saga → `RESERVE_FAILED` |
| Out-of-order: `picking.cancelled` arrives before `picking.requested` | `Reservation` lookup fails → DLT for the cancel; the requested event arrives later, succeeds → operator must manually release via `INVENTORY_ADMIN` REST OR wait for TTL | manual or TTL |
| `RESERVATION_QUANTITY_MISMATCH` on Confirm | Throws → DLT | Operator inspects the divergence; v1 has no automated reconciliation — RMA inbound (v2) is the future path |
| `RESERVATION_ALREADY_RELEASED` on Confirm | Throws → DLT | Operator investigates the race (likely a manual release fired during ship); v1 no automated path |
| Postgres outage during consume | Consumer retry (Kafka retry budget); after exhaustion → DLT; offset NOT advanced until success or DLT routing | Outage resolved → DLT consumer drains (see `notification-service/runbooks/dlt-replay.md` for the per-service playbook pattern) |
| Outbox publisher backlog | Inventory mutation succeeded but Kafka publish lagging; outbound saga visible lag | `inventory.outbox.unpublished.count` gauge alert; publisher autoscales |
| TTL sweep contends with manual release | OL conflict on `Reservation.version` → one TX rolls back, retried next tick; net result identical (RELEASED with whichever reason committed first) | self-recovering |

---

## 7. Observability

| Signal | Type | Notes |
|---|---|---|
| `inventory.reservation.reserved.total{warehouse}` | Counter | Per-warehouse reserve count |
| `inventory.reservation.released.total{reason}` | Counter | Tagged by reason (`CANCELLED` / `EXPIRED` / `MANUAL`) |
| `inventory.reservation.confirmed.total{warehouse}` | Counter | |
| `inventory.reservation.reserve.rejected.total{reason}` | Counter | `INSUFFICIENT_STOCK` / `LOCATION_INACTIVE` / etc |
| `inventory.reservation.expiry.swept.total` | Counter | Category D mandated (ADR-MONO-005 § D5); incremented by released-count per tick |
| `inventory.reservation.event-dedupe.outcome{outcome}` | Counter | `APPLIED` / `IGNORED_DUPLICATE` |

Trace spans carry attributes: `reservation.id`, `picking_request_id`,
`saga.id` (from inbound event header), `event.type`, `outcome`. Log
messages mirror those keys for cross-correlation with `outbound-service`
traces.

---

## 8. Test Matrix

Per [`../architecture.md`](../architecture.md) § Testing Requirements. The
state-machine unit tests and guard-condition tests live in
[`../state-machines/reservation-status.md`](../state-machines/reservation-status.md)
§ Test Requirements; this section covers **saga-level integration test
surfaces** — consumer round-trips, TTL expiry, idempotency, and failure modes.

The sibling outbound-saga test matrix is
[`../../outbound-service/sagas/outbound-saga.md`](../../outbound-service/sagas/outbound-saga.md)
§ 8; this document follows the same sub-section shape.

### 8.1 Unit (Domain) — `Reservation` aggregate

Covered by `reservation-status.md` § Test Requirements. Cross-reference
only; do not duplicate test cases here.

### 8.2 Application Service (port fakes)

- **Happy path — Reserve**: `OutboundPickingRequestedConsumer` receives
  `outbound.picking.requested` → `Reservation` created (`RESERVED`), N
  `ReservationLine`s, 2N `InventoryMovement` rows, one `inventory.reserved`
  outbox row — all in the same `@Transactional`.
- **Happy path — Release (CANCELLED)**: `OutboundPickingCancelledConsumer`
  receives `outbound.picking.cancelled` → `RESERVED → RELEASED`; 2N movement
  rows + one `inventory.released` (reason=`CANCELLED`) outbox row.
- **Happy path — Confirm**: `OutboundShippingConfirmedConsumer` receives
  `outbound.shipping.confirmed` → `RESERVED → CONFIRMED`; N movement rows +
  one `inventory.confirmed` outbox row.
- **Reserve rejected — `INSUFFICIENT_STOCK`**: `Inventory.reserve()` throws;
  TX rolls back; zero `Reservation` row, zero movement rows, zero outbox rows;
  `inventory.reserve.failed{reason=INSUFFICIENT_STOCK}` emitted in separate
  `REQUIRES_NEW` TX.
- **Reserve rejected — stale MasterReadModel** (`SKU_INACTIVE` / `LOT_INACTIVE`
  / `LOT_EXPIRED` / `LOCATION_INACTIVE`): same treatment as
  `INSUFFICIENT_STOCK` — rolls back + `inventory.reserve.failed{reason=<code>}`.
- **Confirm on RELEASED row**: `RESERVATION_ALREADY_RELEASED` → consumer
  throws → routed to DLT; no outbox row; `Reservation` row unchanged.
- **Quantity mismatch on Confirm**: `RESERVATION_QUANTITY_MISMATCH` → DLT;
  same no-side-effect guarantee.
- Each transition verifies outbox row is absent when the transaction rolls back
  (Kafka publish must not occur on TX rollback, per T7).

### 8.3 Consumer Idempotency (Testcontainers Kafka + Postgres)

- **`outbound.picking.requested` redelivery (same `event_id`)**: first delivery
  inserts `event_dedupe` row (outcome=`APPLIED`) + `Reservation`; second
  delivery collides on `event_dedupe` PK → rollback → consumer ACKs; exactly
  one `Reservation` row, exactly one outbox row.
- **`outbound.picking.requested` redelivery (same `picking_request_id`, fresh
  `event_id`)**: collides on `Reservation.picking_request_id` UNIQUE → rollback
  → consumer ACKs; same single-row guarantee.
- **`outbound.picking.cancelled` redelivery**: second delivery loads
  `Reservation` in `RELEASED` (terminal-once) → no-op; consumer ACKs; zero
  additional movement rows, zero additional outbox rows.
- **`outbound.shipping.confirmed` redelivery**: already `CONFIRMED` → no-op;
  consumer ACKs.
- **Out-of-order: `picking.cancelled` before `picking.requested`**:
  `Reservation` lookup fails → DLT; `picking.requested` arrives later,
  succeeds → operator must manually release via `INVENTORY_ADMIN` REST or wait
  for TTL.

### 8.4 TTL Expiry (Testcontainers Postgres + `@Scheduled`)

- **Normal TTL expiry**: insert `Reservation` with `expires_at = now - 1s`;
  trigger `ReleaseReservationService.releaseExpired()`; verify
  `status = RELEASED`, `released_reason = EXPIRED`, 2N movement rows,
  `inventory.released` (reason=`EXPIRED`) outbox row.
- **Batch isolation**: 3 rows with `expires_at < NOW()` + 1 row with
  `expires_at > NOW()`; after sweep, exactly 3 released and 1 still
  `RESERVED`.
- **OL race — TTL vs manual**: parallel TTL sweep and manual release on the
  same row; one TX commits, the other gets OL conflict → rolls back → retries →
  finds `RELEASED` → no-op; exactly one `inventory.released` emitted.
- **`CONFIRMED` rows skipped**: a `CONFIRMED` row with a past `expires_at`
  (stale TTL) must NOT be touched by the sweeper (`status` filter excludes it).
- **Metric emission**: `inventory.reservation.expiry.swept.total` counter
  increments by released-count per sweep tick (ADR-MONO-005 § D5).

### 8.5 Concurrency (Testcontainers Postgres)

- **OL conflict on `Reservation.version`**: two threads each load the same
  `RESERVED` row (version=0) and attempt `release(CANCELLED)` concurrently;
  exactly one succeeds at version=1; the other raises
  `OptimisticLockingFailureException` → consumer retry → finds `RELEASED` →
  no-op; exactly one `inventory.released` outbox row.
- **OL conflict on `Inventory.version`**: two concurrent reserves for the same
  `inventory` row; exactly one succeeds; the other rolls back; no partial
  movement rows.
- **REST manual release + TTL sweep race**: covered under § 8.4 OL race above.

### 8.6 REST Idempotency — MANUAL Release (`@WebMvcTest`)

- `POST /api/v1/inventory/reservations/{id}:release` with the same
  `Idempotency-Key` twice → second call returns cached response from Redis; no
  second domain mutation; no second outbox row.
- Missing `Idempotency-Key` header → 400 `MISSING_IDEMPOTENCY_KEY`.
- Release on non-existent `id` → 404 `RESERVATION_NOT_FOUND`.
- Release on `CONFIRMED` row → 422 `STATE_TRANSITION_INVALID`.
- Release on `RELEASED` row → 200 no-op (idempotent).
- Caller without `INVENTORY_ADMIN` role → 403 `FORBIDDEN`.

### 8.7 Failure-Mode Suite (per trait `transactional` Required Artifact 5)

- Same `Idempotency-Key` POST twice → single release, single outbox row.
- Same `event_id` delivered twice → single `Reservation`, single outbox row
  (event-dedupe table blocks replay).
- `INSUFFICIENT_STOCK` → no `Reservation` row, no movement rows; outbound
  saga receives `inventory.reserve.failed{reason=INSUFFICIENT_STOCK}` and moves to
  `RESERVE_FAILED`.
- `RESERVATION_ALREADY_RELEASED` on confirm → DLT; no side-effect in
  inventory; outbound saga ops alert.
- TTL sweep restart after a pod crash mid-batch: partially-swept rows
  remain `RESERVED`; next sweep cycle picks them up; no double-release.

---

## 9. Open Questions

No open questions for v1. All mechanics (TTL, OL retry, idempotency,
compensation) are fully specified. Potential v2 candidates:

1. **TTL extension** — `expires_at` is immutable in v1 (single-shot allocation
   lifetime). A v2 `EXTEND_TTL` operation would require a new `reserved_ttl_extended`
   event, an additional outbox contract, and a state-machine guard preventing
   extension after CONFIRMED/RELEASED.
2. **Partial confirm / partial release** — v1 is aggregate-whole only. v2 may
   introduce line-level status tracking (`ReservationLine.status`), which would
   require a new intermediate state (e.g., `PARTIALLY_CONFIRMED`).
3. **Cross-warehouse allocation** — v1 forbids mixed `warehouse_id` lines. A
   v2 multi-warehouse reservation would need a split-saga pattern (one
   child-reservation per warehouse, one parent coordinator).
4. **RMA inbound compensation** — post-ship reversal is out of scope for v1
   (`reservation-saga.md § 4 Compensation` documents the `CONFIRMED → irreversible`
   rule). v2 path: new inbound RMA saga that fires `inventory.received`
   compensations.

---

## 10. References

- [`../architecture.md`](../architecture.md) — § Saga Participation
  (v1 light) (canonical declaration of inventory's role), § Saga /
  Long-running Flow (ADR-MONO-005 Category D), § State Machines §
  Reservation lifecycle.
- [`../domain-model.md`](../domain-model.md) — § 3 Reservation
  (aggregate shape, invariants, state machine, quantity-mismatch
  handling), § 7 EventDedupe (T8 dedupe table).
- [`../state-machines/reservation-status.md`](../state-machines/reservation-status.md)
  — companion state machine diagram; this saga's transitions are
  authored there.
- [`../../outbound-service/sagas/outbound-saga.md`](../../outbound-service/sagas/outbound-saga.md)
  — orchestrator-side counterpart; the cross-service flow's source of
  truth.
- [`../../outbound-service/state-machines/saga-status.md`](../../outbound-service/state-machines/saga-status.md)
  — orchestrator-side state machine (sibling template pattern).
- [`../../../contracts/events/inventory-events.md`](../../../contracts/events/inventory-events.md)
  — § 4 `inventory.reserved`, § 5 `inventory.released`, § 6
  `inventory.confirmed` schemas.
- [`../../../contracts/events/outbound-events.md`](../../../contracts/events/outbound-events.md)
  — `outbound.picking.requested` / `.cancelled` / `outbound.shipping.confirmed`
  consumer-side schemas.
- [`../../../../../../docs/adr/ADR-MONO-005-saga-timeout-escalation-dead-letter-policy.md`](../../../../../../docs/adr/ADR-MONO-005-saga-timeout-escalation-dead-letter-policy.md)
  — § D6 (inventory TTL Category D reference implementation), § D5
  (counter contract).
- [`../../../../../../rules/traits/transactional.md`](../../../../../../rules/traits/transactional.md)
  — Required Artifact 2 (saga compensation spec), T4 (no direct status
  UPDATE), T5 (optimistic lock), T7 (saga atomicity), T8 (event dedupe).
- [`../../../../../../rules/domains/wms.md`](../../../../../../rules/domains/wms.md)
  — W4 (reserve→confirm), W5 (no decrement until shipped), Required
  Artifact 5.
