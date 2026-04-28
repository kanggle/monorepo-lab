# inventory-service — Domain Model

Domain model specification for `inventory-service`. Owned aggregates, fields,
relationships, invariants, and state transitions.

Read this after `specs/services/inventory-service/architecture.md`. Quantity-bucket
semantics (`available` / `reserved` / `damaged`), the reservation lifecycle, and
the Hexagonal layout are declared there and only restated here as far as needed
to reason about the model.

---

## Scope

Five owned aggregates plus three infrastructure-supporting record types:

**Aggregates (owned by this service)**

1. **Inventory** — quantity buckets at a `(location, sku, [lot])` keying
2. **InventoryMovement** — append-only ledger of every quantity change (W2)
3. **Reservation** — outbound picking allocation; state-machined (W4 / W5)
4. **StockAdjustment** — reason-recorded manual correction
5. **StockTransfer** — atomic two-row move between locations within one warehouse

**Infrastructure-supporting records (not domain aggregates)**

6. **InventoryOutbox** — transactional outbox row (T3)
7. **EventDedupe** — consumer-side eventId dedupe table (T8)
8. **MasterReadModel** — local cache of Location / SKU / Lot snapshots fed by
   `master.*` events (read-only from this service's POV; never written by REST or use-case)

Each owned aggregate enforces its own invariants. Cross-aggregate consistency
goes through events, never inside one transaction (trait `transactional` T2) —
**except** where a single use-case mutates multiple Inventory rows atomically
(transfer source + target). That case is one logical mutation expressed as two
Inventory rows + two Movement rows + one Outbox row, all within one
`@Transactional` boundary, and is explicitly justified by W1.

---

## Common Aggregate Shape

Every aggregate row carries:

| Field | Type | Notes |
|---|---|---|
| `id` | UUID | Internal surrogate key |
| `version` | Long | Optimistic lock (trait T5). Pessimistic `SELECT FOR UPDATE` is forbidden |
| `created_at` | Instant | UTC |
| `created_by` | String | Actor id from JWT (or `system:<consumer-name>` for event-driven creates) |
| `updated_at` | Instant | UTC |
| `updated_by` | String | Same source as `created_by` |

Movement / Outbox / EventDedupe are append-only ledgers. They do **not** carry
`version` or `updated_*` columns — once written, never modified. DB-level role
grants (Flyway) revoke `UPDATE` / `DELETE` on those tables for the application
role.

---

## 1. Inventory

### Purpose

The single system of record for "how much of `(location, sku, [lot])` is here,
and how is it allocated". Every other aggregate in this service mutates Inventory
exactly through the domain methods declared below.

### Identity & Keying

Logical key (business identity): `(location_id, sku_id, lot_id)`.

- `location_id` — references `master.location.id` (snapshot in `MasterReadModel`)
- `sku_id` — references `master.sku.id`
- `lot_id` — required iff the SKU's `tracking_type = LOT`; `NULL` otherwise

The combination must be unique; this is the row's natural key. Surrogate `id`
exists only to give Movement / Adjustment / Transfer rows a stable FK target
that survives quantity changes.

### Fields

| Field | Type | Nullable | Notes |
|---|---|---|---|
| `id` | UUID | no | Surrogate PK |
| `warehouse_id` | UUID | no | Denormalized from Location for fast scoping and the same-warehouse transfer check |
| `location_id` | UUID | no | FK-by-id to master Location (no DB FK; cross-service) |
| `sku_id` | UUID | no | FK-by-id to master SKU |
| `lot_id` | UUID | yes | FK-by-id to master Lot. Required iff SKU is LOT-tracked |
| `available_qty` | Integer (≥0) | no | Free for new picking allocation |
| `reserved_qty` | Integer (≥0) | no | Allocated to an active Reservation, not yet shipped |
| `damaged_qty` | Integer (≥0) | no | Quarantined / unsellable; visible to ops, not pickable |
| `last_movement_at` | Instant | no | Hot-path query helper; equals max(`movement.occurred_at`) for this row |
| (common version / timestamp fields) | | | |

Quantities are **integer EA**. Fractional units (KG, L) are converted to base UOM
upstream by master-service / inbound-service before reaching here. v1 hard-rejects
non-integer quantities at the API layer (`VALIDATION_ERROR`).

`on_hand = available_qty + reserved_qty + damaged_qty` is **derived**, not stored.

### Domain Methods (the only legitimate mutators)

All methods bump `version`, append exactly one `InventoryMovement` row, and (for
the use-cases that publish events) write exactly one `InventoryOutbox` row, all
within the calling use-case's single `@Transactional` boundary.

| Method | Pre-condition | Post-condition |
|---|---|---|
| `receive(qty, reason=PUTAWAY)` | `qty > 0` | `available += qty` |
| `reserve(qty, reservationId)` | `available >= qty` | `available -= qty`; `reserved += qty` |
| `release(qty, reservationId, reason)` | `reserved >= qty` for that reservation | `reserved -= qty`; `available += qty` |
| `confirm(qty, reservationId)` | `reserved >= qty` for that reservation | `reserved -= qty` (terminal consume) |
| `adjust(delta, reason, bucket)` | resulting `bucket >= 0` | `bucket += delta` (delta may be negative) |
| `transferOut(qty)` | `available >= qty` | `available -= qty` |
| `transferIn(qty)` | always | `available += qty` |
| `markDamaged(qty)` | `available >= qty` | `available -= qty`; `damaged += qty` |
| `writeOffDamaged(qty, reason)` | `damaged >= qty`, `INVENTORY_ADMIN` role | `damaged -= qty` |

Domain methods are pure functions on the in-memory aggregate. The use-case is
responsible for: loading the aggregate (with version), invoking the method,
appending Movement, writing Outbox, then saving with version-check UPDATE.

### Invariants (W1, W2, W4, W5; trait T2, T4, T5)

- All three buckets are non-negative integers (structural). Any operation that
  would drive a bucket below zero throws `INSUFFICIENT_STOCK` before the SQL
  UPDATE runs.
- `available_qty + reserved_qty + damaged_qty >= 0` (trivially follows from the
  above; documented for completeness).
- If the row's SKU is LOT-tracked, `lot_id` must be non-null. If not LOT-tracked,
  `lot_id` must be null. Mixed populations are forbidden; the partial-unique
  constraint on `(location_id, sku_id, lot_id)` is enforced at the DB level
  treating NULL as a distinguishable value (composite-key equality with NULL
  handled via expression index — see `database-design.md`, deferred).
- `warehouse_id` must equal the Location's `warehouse_id` per local read-model.
  Validated on creation; not re-validated on every mutation (the row is
  immutable on this field).
- `location_id`, `sku_id`, `lot_id`, `warehouse_id` are **immutable** after row
  creation. A SKU moving location → release + re-receive at the new row.
- Direct SQL `UPDATE inventory SET available_qty = ?` outside the domain method
  / use-case path is forbidden (W1, T4). Enforced by code review + the fact that
  Movement is the only legitimate writer of bucket changes.
- A row is "alive" while any bucket > 0. Empty rows are kept (not deleted) for
  ~30 days to keep Movement history joinable; archived after that. (Operational
  detail; not enforced by domain.)
- v1 simplification: no cross-warehouse atomic transfers. `transferOut` /
  `transferIn` pairs require equal `warehouse_id`. Cross-warehouse moves are
  modeled as outbound + inbound in the saga.

### Relationships

- One Inventory : many `InventoryMovement` (1:N, append-only ledger)
- One Inventory : many `Reservation` lines (via `ReservationLine.inventory_id`),
  but `reserved_qty` on Inventory is the authoritative aggregate
- Soft references (no hard FK across services): `location_id` → master Location,
  `sku_id` → master SKU, `lot_id` → master Lot, validated against MasterReadModel

---

## 2. InventoryMovement

### Purpose

W2 — append-only ledger of every quantity change. The audit-of-record. Every
domain method on Inventory writes exactly one Movement row in the same
transaction.

### Fields

| Field | Type | Nullable | Notes |
|---|---|---|---|
| `id` | UUID | no | PK |
| `inventory_id` | UUID (FK) | no | The Inventory row whose buckets changed |
| `movement_type` | enum | no | See enum below |
| `bucket` | enum `AVAILABLE` / `RESERVED` / `DAMAGED` | no | Which bucket changed |
| `delta` | Integer | no | Signed; e.g. reserve writes `-N` to AVAILABLE and `+N` to RESERVED → two rows |
| `qty_before` | Integer (≥0) | no | Bucket value before this movement |
| `qty_after` | Integer (≥0) | no | Bucket value after; `qty_after = qty_before + delta` (structural) |
| `reason_code` | enum | no | See `reason_code` catalog below |
| `reason_note` | String (500) | yes | Free-text supplemental reason. Required when `reason_code = ADJUSTMENT_*` |
| `reservation_id` | UUID | yes | Set for RESERVE / RELEASE / CONFIRM; null otherwise |
| `transfer_id` | UUID | yes | Set for both legs of a TRANSFER; null otherwise |
| `adjustment_id` | UUID | yes | Set for ADJUSTMENT; null otherwise |
| `source_event_id` | UUID | yes | Set when triggered by a consumed Kafka event (`putaway.completed`, `picking.requested`, `shipping.confirmed`); null for REST-driven movements |
| `actor_id` | String | no | JWT subject or `system:<consumer-name>` |
| `occurred_at` | Instant | no | Equals the enclosing transaction's commit time |
| `created_at` | Instant | no | Same as `occurred_at` in v1 (kept distinct for future time-skew investigation) |

No `version`, no `updated_*`. **Append-only.**

### Enums

`movement_type`:

```
RECEIVE       — putaway-completed event added stock
RESERVE       — outbound picking allocated
RELEASE       — reservation released (cancel / expire)
CONFIRM       — shipping confirmed; reserved → consumed
ADJUSTMENT    — manual reason-coded delta
TRANSFER_OUT  — transfer source leg
TRANSFER_IN   — transfer target leg
DAMAGE_MARK   — available → damaged bucket move
DAMAGE_WRITE_OFF — damaged decremented (admin)
```

`reason_code` — closed v1 catalog (extensible via Flyway-managed enum table; not
a Java enum, to allow ops-time additions without a redeploy):

```
PUTAWAY                 # RECEIVE
PICKING                 # RESERVE / CONFIRM
PICKING_CANCELLED       # RELEASE
PICKING_EXPIRED         # RELEASE (TTL job)
SHIPPING_CONFIRMED      # CONFIRM
ADJUSTMENT_CYCLE_COUNT  # ADJUSTMENT (실사)
ADJUSTMENT_DAMAGE       # ADJUSTMENT or DAMAGE_MARK
ADJUSTMENT_LOSS         # ADJUSTMENT
ADJUSTMENT_FOUND        # ADJUSTMENT (positive)
ADJUSTMENT_RECLASSIFY   # ADJUSTMENT (between buckets — paired rows)
TRANSFER_INTERNAL       # TRANSFER_OUT / TRANSFER_IN
DAMAGE_WRITE_OFF        # DAMAGE_WRITE_OFF
```

### Invariants (W2)

- `qty_after = qty_before + delta` (structural; checked in domain factory before insert).
- `qty_after >= 0` (mirrors the bucket non-negative invariant).
- `(inventory_id, occurred_at, movement_type, bucket)` rows form a deterministic
  reconstruction of bucket value at any point in time. This is the audit
  property; we never trust Inventory's bucket columns alone for compliance reads
  — replay Movement instead.
- DB role: only `INSERT` / `SELECT` granted to the application role on
  `inventory_movement`. `UPDATE` and `DELETE` are revoked (Flyway role grants).
  Architectural enforcement of W2.
- Exactly one Movement row per bucket-change. A reserve (which moves
  `AVAILABLE → RESERVED`) writes **two** Movement rows — one with
  `bucket=AVAILABLE delta=-N` and one with `bucket=RESERVED delta=+N`, both
  carrying the same `reservation_id` and the same `occurred_at`.

### Relationships

- N : 1 to Inventory (parent row)
- 1 : 0..1 to Reservation (via `reservation_id`)
- 1 : 0..1 to StockTransfer (via `transfer_id`; expect 2 rows per transfer)
- 1 : 0..1 to StockAdjustment (via `adjustment_id`)

---

## 3. Reservation

### Purpose

Represents one outbound picking request's allocation against inventory.
Implements W4 (reserve → confirm two-phase) and W5 (no decrement until shipped).

A Reservation is created in response to `outbound.picking.requested` and is
either **confirmed** by `outbound.shipping.confirmed`, **released** by
`outbound.picking.cancelled`, or **expired** by the TTL job.

### Aggregate Shape

Reservation is the aggregate root; ReservationLine is its part.

#### Reservation (root)

| Field | Type | Nullable | Notes |
|---|---|---|---|
| `id` | UUID | no | PK |
| `picking_request_id` | UUID | no | Outbound's picking-request id; **unique** — at most one Reservation per picking request |
| `warehouse_id` | UUID | no | Denormalized for scope; all lines must share it |
| `status` | enum `RESERVED` / `CONFIRMED` / `RELEASED` | no | See state machine below |
| `expires_at` | Instant | no | TTL — auto-released when passed (default 24h, configurable per warehouse) |
| `released_reason` | enum `CANCELLED` / `EXPIRED` / `MANUAL` / null | yes | Set when `status = RELEASED` |
| `confirmed_at` | Instant | yes | Set when `status = CONFIRMED` |
| `released_at` | Instant | yes | Set when `status = RELEASED` |
| (common version / timestamp fields) | | | |

#### ReservationLine

| Field | Type | Nullable | Notes |
|---|---|---|---|
| `id` | UUID | no | PK |
| `reservation_id` | UUID (FK) | no | Parent Reservation |
| `inventory_id` | UUID | no | The exact Inventory row this line draws from |
| `location_id` | UUID | no | Denormalized for query speed; equals `inventory.location_id` |
| `sku_id` | UUID | no | Same |
| `lot_id` | UUID | yes | Same; null for non-LOT SKUs |
| `quantity` | Integer (>0) | no | Allocated EA |

ReservationLine has no version of its own — the Reservation aggregate's version
covers the whole graph. State transitions on lines (partial confirm) are NOT
supported in v1; the aggregate moves as a whole.

### State Machine

```
              [reserve (initial create)]
                       |
                       v
                   RESERVED ----[release]----> RELEASED (terminal)
                       |
                  [confirm]
                       |
                       v
                   CONFIRMED (terminal)
```

- `RESERVED → CONFIRMED`: triggered by `outbound.shipping.confirmed`. Atomic with
  Inventory `confirm` calls on every line.
- `RESERVED → RELEASED`: triggered by `outbound.picking.cancelled` (manual op),
  the TTL job (timeout), or `INVENTORY_ADMIN` manual release. Atomic with
  Inventory `release` calls on every line.
- `CONFIRMED` and `RELEASED` are terminal. No reactivation, ever. Restoring
  stock after a confirmed shipment is modeled as a new RECEIVE + outbound
  cancellation event chain — never as state regression here.

Direct status update bypassing `confirm()` / `release()` is forbidden (T4).

### Invariants

- `picking_request_id` is unique across all Reservations regardless of status —
  a cancelled-then-recreated picking request must use a new `picking_request_id`
  upstream. Enforces idempotency of `outbound.picking.requested` consumption.
- `expires_at > created_at` at creation time.
- All lines share the same `warehouse_id` (cross-warehouse picking forbidden in v1).
- For each line, `inventory.reserved_qty >= quantity` must hold for the lifetime
  of the Reservation while in `RESERVED` state. Enforced by:
  - On create: Inventory.`reserve(qty, reservationId)` increments `reserved_qty`.
  - On confirm: Inventory.`confirm(qty, reservationId)` decrements `reserved_qty`.
  - On release: Inventory.`release(qty, reservationId, reason)` decrements
    `reserved_qty` and increments `available_qty`.
- A line's `(inventory_id, reservation_id)` pair is unique — same picking
  request cannot have duplicate lines on the same Inventory row (collapse them
  upstream).
- Once `CONFIRMED` or `RELEASED`, no further mutations on lines are permitted.
- TTL (`expires_at`) cannot be extended in v1 (single-shot allocation lifetime).

### Quantity-mismatch Handling

`outbound.shipping.confirmed` carries the actually-shipped quantity per line.
That quantity must equal the reserved quantity exactly (`RESERVATION_QUANTITY_MISMATCH`).
v1 does not support partial shipments — short-shipment is modeled as
release + new picking request upstream. (See `architecture.md` saga section.)

### Relationships

- 1 : N to ReservationLine
- N : 1 to picking-request (in `outbound-service`, by id; soft reference)
- Each ReservationLine : 1 Inventory row (whose `reserved_qty` it contributes to)
- Each transition writes 2N+ Movement rows (N=line count) and one Outbox event

---

## 4. StockAdjustment

### Purpose

A reason-recorded manual correction of an Inventory row, performed by an
operator with `INVENTORY_WRITE` role. Adjustments are how 실사 (cycle count)
discrepancies, found-stock, lost-stock, and bucket reclassifications get into
the system without bypassing W2.

### Fields

| Field | Type | Nullable | Notes |
|---|---|---|---|
| `id` | UUID | no | PK |
| `inventory_id` | UUID (FK) | no | The single row being adjusted |
| `bucket` | enum `AVAILABLE` / `RESERVED` / `DAMAGED` | no | Which bucket the delta lands on |
| `delta` | Integer (≠0) | no | Signed |
| `reason_code` | enum (see Movement reason_code catalog, `ADJUSTMENT_*` subset) | no | Required (`ADJUSTMENT_REASON_REQUIRED` if missing) |
| `reason_note` | String (500) | no | **Required** for adjustments — the operational requirement is that adjustments always carry an explanation |
| `actor_id` | String | no | JWT subject of the operator |
| `idempotency_key` | String | yes | Deduped via the REST idempotency store; not a domain field strictly, but persisted for audit |
| (common version / timestamp fields) | | | |

### Bucket-reclassify Adjustments (paired)

Moving between buckets on the same Inventory row (e.g., AVAILABLE → DAMAGED for
discovered damage) uses **two** StockAdjustment rows linked by a shared
`reason_code = ADJUSTMENT_RECLASSIFY` and a shared `reason_note` referencing the
peer adjustment's id. Both rows + both Movement rows commit in one
`@Transactional` use-case. v1 does NOT introduce a separate "ReclassifyAdjustment"
aggregate — the existing pair is sufficient.

### Invariants

- `delta != 0`.
- `reason_code` ∈ `ADJUSTMENT_*` subset.
- `reason_note` non-empty (length ≥ 3, after trim).
- The resulting bucket value must be `>= 0` (`INSUFFICIENT_STOCK` if a negative
  delta would underflow).
- Adjustments are immutable once persisted. To "correct an adjustment", create a
  reverse adjustment with `reason_code = ADJUSTMENT_RECLASSIFY` referencing the
  original.
- One Adjustment ↔ one Movement row (or two for reclassify pair) — exactly the
  movements needed to reflect the bucket change. Both rows' `reason_code` /
  `reason_note` mirror the Adjustment's.

### Relationships

- N : 1 to Inventory
- 1 : 1..2 to InventoryMovement (1 normally; 2 for the reclassify pair)
- For reclassify: 1 : 1 to peer StockAdjustment (soft, via `reason_note` payload)

---

## 5. StockTransfer

### Purpose

Atomic move between two locations within one warehouse. The single use-case
that mutates **two** Inventory rows in one `@Transactional` boundary, justified
by W1.

### Fields

| Field | Type | Nullable | Notes |
|---|---|---|---|
| `id` | UUID | no | PK |
| `warehouse_id` | UUID | no | Both endpoints share this |
| `source_location_id` | UUID | no | |
| `target_location_id` | UUID | no | |
| `sku_id` | UUID | no | |
| `lot_id` | UUID | yes | If SKU is LOT-tracked |
| `quantity` | Integer (>0) | no | EA moved |
| `reason_code` | enum `TRANSFER_INTERNAL` / `REPLENISHMENT` / `CONSOLIDATION` | no | v1 fixed enum |
| `reason_note` | String (500) | yes | Optional |
| `actor_id` | String | no | JWT subject |
| `idempotency_key` | String | yes | Persisted for audit |
| (common version / timestamp fields) | | | |

### Invariants (W1)

- `source_location_id != target_location_id` (`TRANSFER_SAME_LOCATION` otherwise).
- `quantity > 0`.
- The two endpoints share `warehouse_id` (looked up from MasterReadModel)
  (`TRANSFER_CROSS_WAREHOUSE` — to register; or surfaces as `VALIDATION_ERROR`
  in v1 if cross-warehouse codes are deferred).
- Source Inventory row's `available_qty >= quantity` at the version-checked
  UPDATE time. If not, `INSUFFICIENT_STOCK` and full rollback.
- The two Inventory rows are loaded and updated in deterministic order
  (`ORDER BY location_id ASC` or by PK) to avoid deadlocks under concurrent
  reciprocal transfers.
- Target Inventory row is created if missing: `(target_location, sku, lot)`
  may have no prior stock, so the use-case **upserts** the target row in the
  same transaction with `available_qty = quantity` and version 0.
- Exactly one StockTransfer ↔ exactly two Movement rows (`TRANSFER_OUT` from
  source, `TRANSFER_IN` to target), all sharing `transfer_id = transfer.id`,
  all in one transaction.
- Transfers are immutable once persisted. Reversing a transfer is a new transfer
  in the opposite direction.

### Relationships

- N : 1 source Inventory, N : 1 target Inventory (no DB FK from Transfer to
  Inventory — Movement rows carry the link)
- 1 : 2 to InventoryMovement (one per leg)

---

## 6. InventoryOutbox (infrastructure)

Per `transactional` T3. Same single-table outbox pattern used by `master-service`.

| Field | Type | Nullable | Notes |
|---|---|---|---|
| `id` | UUID | no | PK |
| `aggregate_type` | String (40) | no | `INVENTORY` / `RESERVATION` / `STOCK_ADJUSTMENT` / `STOCK_TRANSFER` |
| `aggregate_id` | UUID | no | The aggregate root's id |
| `event_type` | String (60) | no | e.g., `inventory.adjusted` / `inventory.transferred` / `inventory.reserved` / `inventory.released` / `inventory.confirmed` / `inventory.received` / `inventory.low-stock-detected` |
| `event_version` | String (10) | no | `v1` |
| `payload` | JSONB | no | Event payload per `inventory-events.md` |
| `partition_key` | String (60) | no | `location_id` (mutations) or `sku_id` (master refreshes) — picked at write-time |
| `created_at` | Instant | no | Same as enclosing TX commit |
| `published_at` | Instant | yes | Set when Kafka publisher confirms publish |

Append-only from the application role. The publisher process updates `published_at`
with a separate, narrowly-scoped DB role. (Documented in `database-design.md`,
deferred.)

---

## 7. EventDedupe (infrastructure)

Per `transactional` T8. Already declared in `architecture.md`; restated here for
completeness:

| Field | Type | Nullable | Notes |
|---|---|---|---|
| `event_id` | UUID | no | PK; from inbound event header |
| `event_type` | String (60) | no | For observability |
| `processed_at` | Instant | no | |
| `outcome` | enum `APPLIED` / `IGNORED_DUPLICATE` / `FAILED` | no | |

Retention: 30 days, cron purge. No `version` / `updated_*`.

---

## 8. MasterReadModel (local cache; **read-only from this service's POV**)

Populated by the `MasterLocationConsumer` and `MasterSkuConsumer` from
`master.*` events. Never written by REST handlers or use-cases. Stored as
denormalized snapshots — the only write path is `upsert by id` from the
consumer.

### LocationSnapshot

| Field | Type | Nullable | Notes |
|---|---|---|---|
| `id` | UUID | no | PK; equals master Location id |
| `location_code` | String (40) | no | For display / log |
| `warehouse_id` | UUID | no | For same-warehouse transfer check |
| `zone_id` | UUID | no | |
| `location_type` | enum | no | mirrors master |
| `status` | enum `ACTIVE` / `INACTIVE` | no | If `INACTIVE`, new mutations on this location → `LOCATION_INACTIVE` |
| `cached_at` | Instant | no | When the most recent `master.location.*` event was processed |
| `master_version` | Long | no | The `version` field from the master Location at event time; used to ignore out-of-order older events |

### SkuSnapshot

| Field | Type | Nullable | Notes |
|---|---|---|---|
| `id` | UUID | no | PK; equals master SKU id |
| `sku_code` | String (40) | no | |
| `tracking_type` | enum `NONE` / `LOT` | no | Drives whether `lot_id` is required on Inventory rows for this SKU |
| `base_uom` | enum | no | For display only — quantities are normalized upstream |
| `status` | enum | no | `INACTIVE` → `SKU_INACTIVE` on new mutations |
| `cached_at` | Instant | no | |
| `master_version` | Long | no | |

### LotSnapshot

| Field | Type | Nullable | Notes |
|---|---|---|---|
| `id` | UUID | no | PK; equals master Lot id |
| `sku_id` | UUID | no | |
| `lot_no` | String (40) | no | |
| `expiry_date` | LocalDate | yes | If past today, `status` should be `EXPIRED` |
| `status` | enum `ACTIVE` / `INACTIVE` / `EXPIRED` | no | `EXPIRED` / `INACTIVE` → reject new mutations |
| `cached_at` | Instant | no | |
| `master_version` | Long | no | |

### Read-model Invariants

- Snapshots are eventually consistent. A reservation that succeeded on a stale
  snapshot of a since-deactivated Location is **not** retroactively rolled back —
  the deactivation event will fail closed for **future** mutations, and ops
  resolve the existing stock manually (W6 cross-service check is local-only in
  v1; see `architecture.md` and `master-service/domain-model.md`).
- The consumer drops events whose `master_version` is `<=` the cached
  `master_version` (out-of-order delivery handling).
- The snapshot tables are never read inside a domain mutation transaction in a
  way that would create cross-aggregate write coupling — the use-case reads the
  snapshot once at the start of the transaction to validate, then proceeds.

---

## Entity Relationship Diagram

```
               (master-service, by id; via MasterReadModel cache)
                         Location  SKU  Lot
                            │      │    │
                            ▼      ▼    ▼
                      ┌──────────────────────┐
                      │      Inventory       │  ← aggregate root
                      │ (loc, sku, lot) key  │
                      │  available/reserved/ │
                      │       damaged        │
                      └──────────┬───────────┘
                                 │ 1 : N (append-only)
                                 ▼
                       InventoryMovement (W2 ledger)
                                 ▲
                                 │ 1 movement / leg
                ┌────────────────┼────────────────┐
                │                │                │
        StockAdjustment   Reservation         StockTransfer
        (1 row, 1 mvmt;     │                  (1 row, 2 mvmts:
         pair for           │                   TRANSFER_OUT/IN
         reclassify)        │ 1:N
                            ▼
                    ReservationLine  ── each line ties to one Inventory row
                                       and contributes to its reserved_qty
```

Outbox / EventDedupe / MasterReadModel are infrastructure; not on the diagram.

---

## Aggregate Boundaries

| Aggregate root | Owns | Cross-aggregate via |
|---|---|---|
| Inventory | bucket quantities for one (location, sku, lot) row | events (`inventory.*`); Movement is its child ledger |
| Reservation | status, lines, TTL | events (`inventory.reserved` / `.released` / `.confirmed`); its lines mutate Inventory rows but the use-case (not the aggregate) coordinates that |
| StockAdjustment | the adjustment record | events (`inventory.adjusted`); its Movement row mirrors it |
| StockTransfer | the transfer record | events (`inventory.transferred`); its 2 Movement rows mirror it |
| InventoryMovement | a single immutable ledger row | none — child of Inventory; never published as event itself |

A use-case is permitted to touch multiple aggregates in **one transaction** only
when the underlying domain operation is logically atomic (W1). The two cases
in this service:

- **StockTransfer** (Inventory source + Inventory target + 2 Movements + Outbox)
- **Reservation create / confirm / release** (Reservation + N Inventory rows +
  2N Movements + Outbox)

Every other use-case touches exactly one Inventory row + its Movement +
optionally Outbox.

---

## State Machines (Cross-reference)

- **Reservation lifecycle**: declared in `architecture.md` § State Machines and
  re-stated in §3 above. The standalone diagram lives at
  `specs/services/inventory-service/state-machines/reservation-status.md`
  (Open Item from `architecture.md`).
- **Inventory row**: no enum status. Its state is its quantity buckets; an
  empty row is idle, archived after ~30 days.
- **Movement / Adjustment / Transfer**: terminal-on-create. No state machine.
- **MasterReadModel snapshots**: mirror the master aggregate's state machine
  passively. Not state-machined here.

---

## Forbidden Patterns (in code)

- ❌ JPA entity used as domain model — Hexagonal rule
- ❌ Direct `UPDATE inventory SET available_qty / reserved_qty / damaged_qty`
  outside the domain method path (W1, T4)
- ❌ `INSERT` / `UPDATE` / `DELETE` on `inventory_movement` outside the
  Movement-write code path; in particular, **`UPDATE` and `DELETE` are
  revoked at the DB role level** (W2)
- ❌ `SELECT ... FOR UPDATE` on Inventory rows (T5; use optimistic lock + retry)
- ❌ Reservation status updated bypassing `confirm()` / `release()` domain methods
- ❌ Adjustment without `reason_note` (`ADJUSTMENT_REASON_REQUIRED`)
- ❌ Transfer with `source_location_id == target_location_id` (`TRANSFER_SAME_LOCATION`)
- ❌ Transfer across warehouses (v1 simplification — `VALIDATION_ERROR`)
- ❌ Quantity mutation of a row whose Location / SKU / Lot snapshot is
  `INACTIVE` / `EXPIRED` (`LOCATION_INACTIVE` / `SKU_INACTIVE` / `LOT_INACTIVE`
  / `LOT_EXPIRED`)
- ❌ Writing MasterReadModel from REST or use-case code paths (consumer-only)
- ❌ Hard delete of any aggregate row in v1 (Reservation / Adjustment / Transfer
  are immutable; Inventory rows are archived not deleted)
- ❌ Mutating Reservation lines after `CONFIRMED` / `RELEASED` terminal state
- ❌ Cross-aggregate write in one transaction outside the two W1-justified
  cases (Transfer; Reservation lifecycle)

---

## Reference Data Snapshot (v1 Seed)

`inventory-service` does not seed business data on first deployment — it is
an empty store until consumed events from `master-service` (read-model) and
`inbound-service` (RECEIVE) populate it.

Dev / standalone profile may seed:

- 5 Inventory rows under master-service's seeded Locations / SKUs (e.g.,
  `WH01-A-01-01-01` × seeded SKU `SKU-001` × `available_qty=100`)
- 0 Reservations, 0 Adjustments, 0 Transfers (empty ledgers)
- MasterReadModel populated either by replaying `master.*` topics from the
  beginning **or** via a Flyway-managed dev seed; v1 picks the replay approach
  to avoid drift.

Strategy: Flyway migration `V99__seed_dev_data.sql`, only active under Spring
profile `dev` or `standalone`.

---

## Open Items

- `specs/services/inventory-service/database-design.md` — physical schema
  (tables, indexes, partial-unique on `(location_id, sku_id, lot_id)` with
  NULL-aware key, role grants enforcing W2 append-only)
- `specs/services/inventory-service/state-machines/reservation-status.md` —
  diagram in its own file (referenced in `architecture.md` Open Items)
- `specs/services/inventory-service/sagas/reservation-saga.md` — saga
  participation detail (referenced in `architecture.md`)
- `specs/services/inventory-service/idempotency.md` — REST + event dedupe
  strategy (referenced in `architecture.md`)
- `platform/error-handling.md` — register codes referenced here that are not
  yet in the catalog: `RESERVATION_NOT_FOUND`, `RESERVATION_QUANTITY_MISMATCH`,
  `LOCATION_INACTIVE`, `SKU_INACTIVE`, `LOT_INACTIVE`, `LOT_EXPIRED`,
  `TRANSFER_CROSS_WAREHOUSE` (or fold into `VALIDATION_ERROR`)

---

## References

- `architecture.md` (this directory)
- `rules/domains/wms.md` — W1 (transactional protection), W2 (append-only
  history), W4 (reserve→confirm), W5 (no decrement until shipped), W6
  (master ref-integrity), and Standard Error Codes
- `rules/traits/transactional.md` — T2, T3 (outbox), T4 (no direct status
  update), T5 (optimistic lock only), T8 (eventId dedupe)
- `rules/traits/integration-heavy.md` — I3 (retry), I5 (DLQ), I8 (internal
  model translation between events and domain)
- `specs/services/master-service/domain-model.md` — Location / SKU / Lot
  identity owned upstream; this service caches snapshots only
- `specs/contracts/http/inventory-service-api.md` — REST endpoint shapes
  (Open Item; cross-link)
- `specs/contracts/events/inventory-events.md` — published event payloads
  (Open Item; cross-link)
