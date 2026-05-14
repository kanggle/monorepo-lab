# inventory-service — Database Design

Physical schema reflection for `inventory_service_db`. Required by
`domain-model.md § Open Items` — Flyway migrations under
`apps/inventory-service/src/main/resources/db/migration/` are the canonical
source-of-truth; this document consolidates them into a single spec
artifact for review-time reasoning. When a new migration lands (`V6+`),
this file must be updated in the same commit.

**Target engine**: PostgreSQL 14+ (production). NULL-aware partial-unique
indexes, `JSONB` payloads, and `TIMESTAMPTZ` semantics are PostgreSQL
native; portability to other engines is out of scope for v1.

**Authoritative reference**: [`domain-model.md`](domain-model.md) for the
domain meaning of each table.

---

## Schema Overview

```
                ┌──────────────────────────────┐
                │   master read-model (V4)     │
                │   location_snapshot          │
                │   sku_snapshot               │
                │   lot_snapshot               │
                └──────────────────────────────┘
                              ▲ (consumer-fed, never written from REST)

       ┌──────────────────────────────────────────────────┐
       │   inventory (V1)  ─── version, partial-unique    │
       │       │                                          │
       │       └─→ inventory_movement (V1, append-only)   │
       │                  │ ▲                             │
       │                  │ trigger + REVOKE (V5)         │
       │                  ▼                               │
       │            stock_adjustment (V3)                 │
       │            stock_transfer (V3)                   │
       │                                                  │
       │       ┌─→ reservation (V2)  ─── status FSM       │
       │       │       │                                  │
       │       │       └─→ reservation_line (V2, CASCADE) │
       └──────────────────────────────────────────────────┘
                       │
                       ▼
              inventory_outbox (V1, T3 outbox)
              inventory_event_dedupe (V1, T8)
```

Total: 11 tables + 2 triggers + 1 PL/pgSQL function across 5 migrations
(V1=156, V2=60, V3=69, V4=58, V5=55 line). All schema invariants here are
enforced by the DB; domain code may add layered checks, but the DB layer
is the last line.

---

## 1. Inventory Aggregate (V1, domain-model § 1)

Aggregate root for stock state. One row per `(location, sku, lot?)` triplet.

```sql
CREATE TABLE inventory (
    id                 UUID         PRIMARY KEY,
    warehouse_id       UUID         NOT NULL,
    location_id        UUID         NOT NULL,
    sku_id             UUID         NOT NULL,
    lot_id             UUID,
    available_qty      INTEGER      NOT NULL DEFAULT 0,
    reserved_qty       INTEGER      NOT NULL DEFAULT 0,
    damaged_qty        INTEGER      NOT NULL DEFAULT 0,
    last_movement_at   TIMESTAMPTZ  NOT NULL,
    version            BIGINT       NOT NULL DEFAULT 0,
    created_at         TIMESTAMPTZ  NOT NULL,
    created_by         VARCHAR(100) NOT NULL,
    updated_at         TIMESTAMPTZ  NOT NULL,
    updated_by         VARCHAR(100) NOT NULL,
    CONSTRAINT ck_inventory_buckets_nonneg
        CHECK (available_qty >= 0 AND reserved_qty >= 0 AND damaged_qty >= 0)
);
```

**Optimistic locking**: `version BIGINT DEFAULT 0` is JPA's `@Version`
column. Every aggregate mutation increments the version; concurrent updates
on the same row fail with `OptimisticLockingFailureException`.

**Non-negative invariant**: `ck_inventory_buckets_nonneg` enforces W1
(buckets never go negative) at the schema layer. Domain code performs the
same check pre-flight, but the DB constraint is the absolute backstop —
even malformed JPA queries cannot drive any bucket below zero.

### 1.1 Indexes

```sql
CREATE UNIQUE INDEX uq_inventory_loc_sku_lot
    ON inventory (location_id, sku_id, lot_id)
    WHERE lot_id IS NOT NULL;

CREATE UNIQUE INDEX uq_inventory_loc_sku_no_lot
    ON inventory (location_id, sku_id)
    WHERE lot_id IS NULL;

CREATE INDEX idx_inventory_warehouse_sku
    ON inventory (warehouse_id, sku_id);

CREATE INDEX idx_inventory_sku
    ON inventory (sku_id);
```

**NULL-aware natural key** (`uq_inventory_loc_sku_lot` +
`uq_inventory_loc_sku_no_lot`): PostgreSQL treats `NULL != NULL`, so a
single index on `(location_id, sku_id, lot_id)` would allow unlimited rows
with `lot_id IS NULL` for the same `(location, sku)` — breaking the
"one row per logical key" invariant for non-LOT-tracked SKUs. The two
partial-unique indexes split the constraint by lot-presence:
- LOT-tracked SKUs: `(location_id, sku_id, lot_id)` unique where `lot_id IS NOT NULL`.
- non-LOT-tracked SKUs: `(location_id, sku_id)` unique where `lot_id IS NULL`.

This avoids the alternative "NULL sentinel UUID" hack and keeps semantic
clarity at the SQL layer.

---

## 2. InventoryMovement Append-Only Ledger (V1 + V5, domain-model § 2)

Every bucket delta produces exactly one movement row. Append-only enforced
at trigger + role-grant level (see § 9 Append-Only Enforcement Strategy).

```sql
CREATE TABLE inventory_movement (
    id                UUID         PRIMARY KEY,
    inventory_id      UUID         NOT NULL REFERENCES inventory (id),
    movement_type     VARCHAR(30)  NOT NULL,
    bucket            VARCHAR(20)  NOT NULL,
    delta             INTEGER      NOT NULL,
    qty_before        INTEGER      NOT NULL,
    qty_after         INTEGER      NOT NULL,
    reason_code       VARCHAR(40)  NOT NULL,
    reason_note       VARCHAR(500),
    reservation_id    UUID,
    transfer_id       UUID,
    adjustment_id     UUID,
    source_event_id   UUID,
    actor_id          VARCHAR(100) NOT NULL,
    occurred_at       TIMESTAMPTZ  NOT NULL,
    created_at        TIMESTAMPTZ  NOT NULL,
    CONSTRAINT ck_movement_bucket
        CHECK (bucket IN ('AVAILABLE', 'RESERVED', 'DAMAGED')),
    CONSTRAINT ck_movement_type
        CHECK (movement_type IN (
            'RECEIVE', 'RESERVE', 'RELEASE', 'CONFIRM', 'ADJUSTMENT',
            'TRANSFER_OUT', 'TRANSFER_IN', 'DAMAGE_MARK', 'DAMAGE_WRITE_OFF'
        )),
    CONSTRAINT ck_movement_after_nonneg
        CHECK (qty_before >= 0 AND qty_after >= 0),
    CONSTRAINT ck_movement_after_consistent
        CHECK (qty_after = qty_before + delta)
);
```

**`ck_movement_after_consistent`** is the load-bearing invariant: the row
itself proves the math. `qty_after = qty_before + delta` is enforced at the
SQL layer — domain code cannot ship a corrupt ledger entry even by mistake.

**Optional FK columns** (`reservation_id`, `transfer_id`, `adjustment_id`,
`source_event_id`) link a movement to its originating aggregate or event.
Each is NULL unless that particular type fired the mutation. Partial
indexes (§ 2.1) only cover the non-null subset.

### 2.1 Indexes

```sql
CREATE INDEX idx_movement_inventory_occurred
    ON inventory_movement (inventory_id, occurred_at DESC);

CREATE INDEX idx_movement_occurred
    ON inventory_movement (occurred_at DESC);

CREATE INDEX idx_movement_reservation
    ON inventory_movement (reservation_id)
    WHERE reservation_id IS NOT NULL;

CREATE INDEX idx_movement_transfer
    ON inventory_movement (transfer_id)
    WHERE transfer_id IS NOT NULL;

CREATE INDEX idx_movement_adjustment
    ON inventory_movement (adjustment_id)
    WHERE adjustment_id IS NOT NULL;

CREATE INDEX idx_movement_source_event
    ON inventory_movement (source_event_id)
    WHERE source_event_id IS NOT NULL;
```

Hot paths: per-inventory history (`idx_movement_inventory_occurred`) and
time-window queries with optional `occurredAfter` filter
(`idx_movement_occurred`). The four partial FK indexes serve auditing
queries that follow a specific reservation / transfer / adjustment / event
lineage.

---

## 3. InventoryOutbox (V1, transactional T3)

Transactional outbox for Kafka publication. Domain writes a row in the same
TX as the inventory mutation; a separate publisher polls pending rows and
ships them to Kafka.

```sql
CREATE TABLE inventory_outbox (
    id              UUID         PRIMARY KEY,
    aggregate_type  VARCHAR(40)  NOT NULL,
    aggregate_id    UUID         NOT NULL,
    event_type      VARCHAR(60)  NOT NULL,
    event_version   VARCHAR(10)  NOT NULL DEFAULT 'v1',
    payload         JSONB        NOT NULL,
    partition_key   VARCHAR(60)  NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL,
    published_at    TIMESTAMPTZ
);

CREATE INDEX idx_inventory_outbox_pending
    ON inventory_outbox (created_at)
    WHERE published_at IS NULL;

CREATE INDEX idx_inventory_outbox_aggregate
    ON inventory_outbox (aggregate_type, aggregate_id);
```

**Pending-publisher index** (`WHERE published_at IS NULL`) keeps the
publisher's FIFO scan cheap regardless of total outbox row count — the
index only stores unpublished rows, which empties to near-zero in steady
state.

`payload` is `JSONB` (not `JSON`) so the publisher can introspect event
shape if needed without re-parsing. `partition_key` is set per-event
(typically `inventory_id` or `reservation_id`) to guarantee per-aggregate
in-order delivery downstream.

---

## 4. EventDedupe (V1, transactional T8)

Consumer-side dedupe by `event_id`. Primary-key conflict is the dedupe
signal — INSERT-or-skip pattern with no extra SELECT round-trip.

```sql
CREATE TABLE inventory_event_dedupe (
    event_id      UUID         PRIMARY KEY,
    event_type    VARCHAR(60)  NOT NULL,
    processed_at  TIMESTAMPTZ  NOT NULL,
    outcome       VARCHAR(20)  NOT NULL,
    CONSTRAINT ck_dedupe_outcome
        CHECK (outcome IN ('APPLIED', 'IGNORED_DUPLICATE', 'FAILED'))
);

CREATE INDEX idx_dedupe_processed_at
    ON inventory_event_dedupe (processed_at);
```

`idx_dedupe_processed_at` powers the retention sweeper (30-day window
per [`idempotency.md`](idempotency.md) § Event Dedupe).

---

## 5. Reservation Aggregate (V2, domain-model § 3)

Reservation root + `reservation_line` children (one line per inventory row
consumed). State machine RESERVED → CONFIRMED or RESERVED → RELEASED, both
terminal.

```sql
CREATE TABLE reservation (
    id                  UUID         PRIMARY KEY,
    picking_request_id  UUID         NOT NULL,
    warehouse_id        UUID         NOT NULL,
    status              VARCHAR(20)  NOT NULL,
    expires_at          TIMESTAMPTZ  NOT NULL,
    released_reason     VARCHAR(20),
    confirmed_at        TIMESTAMPTZ,
    released_at         TIMESTAMPTZ,
    version             BIGINT       NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ  NOT NULL,
    created_by          VARCHAR(100) NOT NULL,
    updated_at          TIMESTAMPTZ  NOT NULL,
    updated_by          VARCHAR(100) NOT NULL,
    CONSTRAINT uq_reservation_picking_request_id UNIQUE (picking_request_id),
    CONSTRAINT ck_reservation_status
        CHECK (status IN ('RESERVED', 'CONFIRMED', 'RELEASED')),
    CONSTRAINT ck_reservation_released_reason
        CHECK (released_reason IS NULL OR released_reason IN ('CANCELLED', 'EXPIRED', 'MANUAL'))
);
```

**`uq_reservation_picking_request_id`** enforces per-picking-request
idempotency at the DB layer, alongside the REST `Idempotency-Key` store.
A repeated picking request collides on the DB constraint regardless of
whether Redis dedupe was bypassed.

State-machine constraint (`ck_reservation_status` + per-transition rules in
[`state-machines/reservation-status.md`](state-machines/reservation-status.md)):
the SQL constraint catches malformed out-of-band INSERTs; domain code
enforces the transition rules (RESERVED → CONFIRMED requires non-null
`confirmed_at`, etc.).

```sql
CREATE TABLE reservation_line (
    id              UUID         PRIMARY KEY,
    reservation_id  UUID         NOT NULL REFERENCES reservation (id) ON DELETE CASCADE,
    inventory_id    UUID         NOT NULL,
    location_id     UUID         NOT NULL,
    sku_id          UUID         NOT NULL,
    lot_id          UUID,
    quantity        INTEGER      NOT NULL,
    CONSTRAINT ck_reservation_line_quantity_positive
        CHECK (quantity > 0),
    CONSTRAINT uq_reservation_line_inventory
        UNIQUE (reservation_id, inventory_id)
);
```

`ON DELETE CASCADE` is operationally safe because reservation deletion is
only used by test fixtures and dev-only seed cleanup — production
reservations transition through state, never deleted.

### 5.1 Indexes

```sql
CREATE INDEX idx_reservation_status_expires_at
    ON reservation (status, expires_at)
    WHERE status = 'RESERVED';

CREATE INDEX idx_reservation_warehouse_status
    ON reservation (warehouse_id, status);

CREATE INDEX idx_reservation_line_reservation
    ON reservation_line (reservation_id);

CREATE INDEX idx_reservation_line_inventory
    ON reservation_line (inventory_id);
```

The **expiry sweeper** (per [`sagas/reservation-saga.md`](sagas/reservation-saga.md)
§ TTL Expiry) scans `idx_reservation_status_expires_at` — only ACTIVE
(`RESERVED`) reservations are indexed, so the scan stays small even as the
historical reservation table grows.

---

## 6. StockAdjustment + StockTransfer (V3, domain-model § 4 + § 5)

Both aggregates are **immutable post-create**. The domain method writes
exactly one (adjustment + 1-2 movements) or one (transfer + exactly 2
movements) in the same transaction; no UPDATE / DELETE path exists in
application code.

```sql
CREATE TABLE stock_adjustment (
    id              UUID         PRIMARY KEY,
    inventory_id    UUID         NOT NULL REFERENCES inventory (id),
    bucket          VARCHAR(20)  NOT NULL,
    delta           INTEGER      NOT NULL,
    reason_code     VARCHAR(40)  NOT NULL,
    reason_note     VARCHAR(500) NOT NULL,
    actor_id        VARCHAR(100) NOT NULL,
    idempotency_key VARCHAR(100),
    version         BIGINT       NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ  NOT NULL,
    created_by      VARCHAR(100) NOT NULL,
    updated_at      TIMESTAMPTZ  NOT NULL,
    updated_by      VARCHAR(100) NOT NULL,
    CONSTRAINT ck_adjustment_bucket
        CHECK (bucket IN ('AVAILABLE', 'RESERVED', 'DAMAGED')),
    CONSTRAINT ck_adjustment_delta_nonzero
        CHECK (delta <> 0),
    CONSTRAINT ck_adjustment_reason_note_length
        CHECK (length(trim(reason_note)) >= 3)
);

CREATE INDEX idx_adjustment_inventory_created
    ON stock_adjustment (inventory_id, created_at DESC);

CREATE INDEX idx_adjustment_created
    ON stock_adjustment (created_at DESC);
```

`reason_note` is `NOT NULL` here (unlike on `inventory_movement`) — every
manual adjustment must carry an operator-supplied note ≥ 3 chars trimmed
(`ck_adjustment_reason_note_length`). The length check is a soft signal
("don't accept whitespace-only"); audits rely on the note being human.

```sql
CREATE TABLE stock_transfer (
    id                   UUID         PRIMARY KEY,
    warehouse_id         UUID         NOT NULL,
    source_location_id   UUID         NOT NULL,
    target_location_id   UUID         NOT NULL,
    sku_id               UUID         NOT NULL,
    lot_id               UUID,
    quantity             INTEGER      NOT NULL,
    reason_code          VARCHAR(40)  NOT NULL,
    reason_note          VARCHAR(500),
    actor_id             VARCHAR(100) NOT NULL,
    idempotency_key      VARCHAR(100),
    version              BIGINT       NOT NULL DEFAULT 0,
    created_at           TIMESTAMPTZ  NOT NULL,
    created_by           VARCHAR(100) NOT NULL,
    updated_at           TIMESTAMPTZ  NOT NULL,
    updated_by           VARCHAR(100) NOT NULL,
    CONSTRAINT ck_transfer_quantity_positive
        CHECK (quantity > 0),
    CONSTRAINT ck_transfer_distinct_locations
        CHECK (source_location_id <> target_location_id),
    CONSTRAINT ck_transfer_reason_code
        CHECK (reason_code IN ('TRANSFER_INTERNAL', 'REPLENISHMENT', 'CONSOLIDATION'))
);

CREATE INDEX idx_transfer_warehouse_created
    ON stock_transfer (warehouse_id, created_at DESC);

CREATE INDEX idx_transfer_source_location
    ON stock_transfer (source_location_id, created_at DESC);

CREATE INDEX idx_transfer_target_location
    ON stock_transfer (target_location_id, created_at DESC);
```

`ck_transfer_distinct_locations` is the SQL backstop for W4
(within-warehouse transfer only crosses _locations_, never produces a
self-loop). The domain layer rejects same-source-and-target requests
before this constraint fires, but the constraint protects against any
out-of-band INSERT path. Cross-warehouse transfer is folded into
`VALIDATION_ERROR` at the domain layer (v1 simplification per § 5
StockTransfer invariants in [`domain-model.md`](domain-model.md)).

---

## 7. Master Read Model (V4, domain-model § 8)

Local snapshot tables fed by `master.*` Kafka topics. Inventory's REST and
use-case paths only READ these tables — only the master-snapshot consumers
write. Out-of-order delivery is handled at upsert time by comparing
`master_version`.

```sql
CREATE TABLE location_snapshot (
    id              UUID         PRIMARY KEY,
    location_code   VARCHAR(40)  NOT NULL,
    warehouse_id    UUID         NOT NULL,
    zone_id         UUID         NOT NULL,
    location_type   VARCHAR(20)  NOT NULL,
    status          VARCHAR(20)  NOT NULL,
    cached_at       TIMESTAMPTZ  NOT NULL,
    master_version  BIGINT       NOT NULL,
    CONSTRAINT ck_location_snapshot_status
        CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

CREATE INDEX idx_location_snapshot_warehouse
    ON location_snapshot (warehouse_id);

CREATE INDEX idx_location_snapshot_code
    ON location_snapshot (location_code);

CREATE TABLE sku_snapshot (
    id              UUID         PRIMARY KEY,
    sku_code        VARCHAR(40)  NOT NULL,
    tracking_type   VARCHAR(10)  NOT NULL,
    base_uom        VARCHAR(10)  NOT NULL,
    status          VARCHAR(20)  NOT NULL,
    cached_at       TIMESTAMPTZ  NOT NULL,
    master_version  BIGINT       NOT NULL,
    CONSTRAINT ck_sku_snapshot_tracking_type
        CHECK (tracking_type IN ('NONE', 'LOT')),
    CONSTRAINT ck_sku_snapshot_status
        CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

CREATE INDEX idx_sku_snapshot_code
    ON sku_snapshot (sku_code);

CREATE TABLE lot_snapshot (
    id              UUID         PRIMARY KEY,
    sku_id          UUID         NOT NULL,
    lot_no          VARCHAR(40)  NOT NULL,
    expiry_date     DATE,
    status          VARCHAR(20)  NOT NULL,
    cached_at       TIMESTAMPTZ  NOT NULL,
    master_version  BIGINT       NOT NULL,
    CONSTRAINT ck_lot_snapshot_status
        CHECK (status IN ('ACTIVE', 'INACTIVE', 'EXPIRED'))
);

CREATE INDEX idx_lot_snapshot_sku
    ON lot_snapshot (sku_id);
```

**No FK to a remote master schema** — these are local replicas keyed by
the same UUID as the master service uses. The consumer chooses the upsert
target by primary key; out-of-order delivery is rejected when the incoming
`master_version` is ≤ the stored one.

---

## 8. Append-Only Enforcement Strategy (V5, W2)

`inventory_movement` is the only table protected against UPDATE/DELETE
beyond the SQL `INSERT`. Two-layer defense:

### 8.1 Trigger (always enforced)

```sql
CREATE OR REPLACE FUNCTION inventory_movement_reject_modification()
    RETURNS TRIGGER
    LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION
        'inventory_movement is append-only (W2): % rejected',
        TG_OP
        USING ERRCODE = '23514';
END;
$$;

CREATE TRIGGER trg_inventory_movement_no_update
    BEFORE UPDATE ON inventory_movement
    FOR EACH ROW
    EXECUTE FUNCTION inventory_movement_reject_modification();

CREATE TRIGGER trg_inventory_movement_no_delete
    BEFORE DELETE ON inventory_movement
    FOR EACH ROW
    EXECUTE FUNCTION inventory_movement_reject_modification();
```

The trigger fires for every non-superuser regardless of GRANT/REVOKE
state. PostgreSQL table owners bypass ACL-level permission checks, so a
REVOKE alone is a no-op whenever the application role also owns the table
(the case in the local docker-compose setup). The trigger is the absolute
backstop.

Superusers bypass triggers via `session_replication_role = replica` — the
Testcontainers W2 enforcement test explicitly does NOT set this, and runs
under a non-superuser connection so the trigger fires.

### 8.2 REVOKE (production hint)

```sql
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = current_user) THEN
        EXECUTE format(
            'REVOKE UPDATE, DELETE ON inventory_movement FROM %I',
            current_user);
    END IF;
EXCEPTION WHEN OTHERS THEN
    -- Owner self-revoke is a no-op in PostgreSQL; tolerated for migration idempotence
    NULL;
END $$;
```

Effective in **production** where the DBA separates an `inventory_owner`
DDL role from a runtime application role — the runtime role has no
UPDATE/DELETE grant, so even if a future trigger bypass is added (e.g.
maintenance window with `session_replication_role = replica`) the role
itself cannot issue the command. Documented as best-practice for
deployment.

In local docker-compose, the application role IS the table owner →
self-revoke is a no-op (the block tolerates this so the migration stays
idempotent across environments).

---

## 9. Indexing Strategy Summary

| Table | Index | Type | Purpose |
|---|---|---|---|
| `inventory` | `inventory_pkey` | PK | row lookup |
| `inventory` | `uq_inventory_loc_sku_lot` | partial unique (`lot_id NOT NULL`) | LOT-tracked natural key |
| `inventory` | `uq_inventory_loc_sku_no_lot` | partial unique (`lot_id NULL`) | non-LOT natural key |
| `inventory` | `idx_inventory_warehouse_sku` | btree | warehouse-scoped sku query |
| `inventory` | `idx_inventory_sku` | btree | cross-warehouse sku totals |
| `inventory_movement` | `inventory_movement_pkey` | PK | row lookup |
| `inventory_movement` | `idx_movement_inventory_occurred` | btree | per-inventory history (DESC) |
| `inventory_movement` | `idx_movement_occurred` | btree | time-window queries (DESC) |
| `inventory_movement` | `idx_movement_reservation` | partial (`reservation_id NOT NULL`) | per-reservation lineage |
| `inventory_movement` | `idx_movement_transfer` | partial | per-transfer lineage |
| `inventory_movement` | `idx_movement_adjustment` | partial | per-adjustment lineage |
| `inventory_movement` | `idx_movement_source_event` | partial | per-source-event lineage |
| `inventory_outbox` | `inventory_outbox_pkey` | PK | row lookup |
| `inventory_outbox` | `idx_inventory_outbox_pending` | partial (`published_at NULL`) | publisher FIFO scan |
| `inventory_outbox` | `idx_inventory_outbox_aggregate` | btree | aggregate-scoped lookup |
| `inventory_event_dedupe` | `inventory_event_dedupe_pkey` | PK | dedupe by event_id |
| `inventory_event_dedupe` | `idx_dedupe_processed_at` | btree | retention sweeper |
| `reservation` | `reservation_pkey` | PK | row lookup |
| `reservation` | `uq_reservation_picking_request_id` | unique | per-picking-request idempotency |
| `reservation` | `idx_reservation_status_expires_at` | partial (`status='RESERVED'`) | expiry sweeper |
| `reservation` | `idx_reservation_warehouse_status` | btree | warehouse-scoped state query |
| `reservation_line` | `reservation_line_pkey` | PK | row lookup |
| `reservation_line` | `uq_reservation_line_inventory` | unique | one line per `(reservation, inventory)` |
| `reservation_line` | `idx_reservation_line_reservation` | btree | FK lookup |
| `reservation_line` | `idx_reservation_line_inventory` | btree | reverse FK lookup |
| `stock_adjustment` | `stock_adjustment_pkey` | PK | row lookup |
| `stock_adjustment` | `idx_adjustment_inventory_created` | btree | per-inventory audit history |
| `stock_adjustment` | `idx_adjustment_created` | btree | time-window audit query |
| `stock_transfer` | `stock_transfer_pkey` | PK | row lookup |
| `stock_transfer` | `idx_transfer_warehouse_created` | btree | warehouse-scoped audit history |
| `stock_transfer` | `idx_transfer_source_location` | btree | source-side audit query |
| `stock_transfer` | `idx_transfer_target_location` | btree | target-side audit query |
| `location_snapshot` | `location_snapshot_pkey` | PK | upsert target |
| `location_snapshot` | `idx_location_snapshot_warehouse` | btree | warehouse-scoped lookup |
| `location_snapshot` | `idx_location_snapshot_code` | btree | natural-key lookup |
| `sku_snapshot` | `sku_snapshot_pkey` | PK | upsert target |
| `sku_snapshot` | `idx_sku_snapshot_code` | btree | natural-key lookup |
| `lot_snapshot` | `lot_snapshot_pkey` | PK | upsert target |
| `lot_snapshot` | `idx_lot_snapshot_sku` | btree | per-SKU lot enumeration |

---

## 10. Migration History

| Version | File | Line | Scope |
|---|---|---|---|
| V1 | `V1__init_inventory_tables.sql` | 156 | inventory + inventory_movement + inventory_outbox + inventory_event_dedupe |
| V2 | `V2__init_reservation_tables.sql` | 60 | reservation + reservation_line |
| V3 | `V3__init_adjustment_transfer_tables.sql` | 69 | stock_adjustment + stock_transfer |
| V4 | `V4__init_master_readmodel.sql` | 58 | location_snapshot + sku_snapshot + lot_snapshot |
| V5 | `V5__role_grants.sql` | 55 | append-only trigger + REVOKE block |

When `V6+` lands, this document must be updated in the same commit (per
the retrospective contract introduced by TASK-BE-157).

---

## References

- [`domain-model.md`](domain-model.md) — domain meaning of each table (canonical reference)
- [`architecture.md`](architecture.md) — § Architecture Style (Hexagonal), § Dependencies
- [`idempotency.md`](idempotency.md) — REST + event dedupe (Outbox + EventDedupe usage)
- [`sagas/reservation-saga.md`](sagas/reservation-saga.md) — TTL expiry sweeper
- [`state-machines/reservation-status.md`](state-machines/reservation-status.md) — reservation state transitions
- `../../../apps/inventory-service/src/main/resources/db/migration/V1__init_inventory_tables.sql`
- `../../../apps/inventory-service/src/main/resources/db/migration/V2__init_reservation_tables.sql`
- `../../../apps/inventory-service/src/main/resources/db/migration/V3__init_adjustment_transfer_tables.sql`
- `../../../apps/inventory-service/src/main/resources/db/migration/V4__init_master_readmodel.sql`
- `../../../apps/inventory-service/src/main/resources/db/migration/V5__role_grants.sql`
- `../../../../../rules/domains/wms.md` — W1 (Inventory bounded context), W2 (append-only Movement), W4 (transfer locations)
- `../../../../../rules/traits/transactional.md` — T3 (outbox), T8 (event dedupe)
- `../../../../../platform/architecture.md` — system-level architecture
