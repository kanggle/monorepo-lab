# admin-service — Database Design

Physical schema reflection for `admin_db`. Flyway migrations under
`apps/admin-service/src/main/resources/db/migration/` are the canonical
source-of-truth; this document consolidates them into a single spec
artifact for review-time reasoning. When a new migration lands (`V3+`,
beyond V99's dev seed), this file must be updated in the same commit
(per the retrospective contract introduced by TASK-BE-157).

**Target engine**: PostgreSQL 14+ (production). Partial unique indexes
with expression-based predicates (`LOWER(email)`, `COALESCE(...)`),
JSONB columns, sentinel-UUID composite-PK patterns, and `TIMESTAMPTZ`
semantics are PostgreSQL native; portability to other engines is out of
scope for v1.

**Authoritative reference**: [`domain-model.md`](domain-model.md) for the
domain meaning of each table, [`architecture.md`](architecture.md)
§ Read-Model Projection Pattern for the LWW upsert mechanics.

**Architecture context**: admin-service is the wms portfolio's
**Layered architecture exception** (per
[`architecture.md`](architecture.md) § Architecture Style Rationale) —
the schema reflects this with **21 tables = portfolio maximum** divided
into 6 write-side aggregates (V1) and 15 read-model projections (V2).
The write-side carries the Layered-exception Hexagonal-lite ports;
the read-side is consumed-only by projectors with no domain logic at
all.

---

## Schema Overview

```
   WRITE-SIDE (V1 = 6 tables, operator authority)
   ┌──────────────────┐   ┌────────────────────────┐   ┌─────────────┐
   │ admin_user       │ ◀▶│ admin_user_role_       │◀──│ admin_role  │
   │ (email partial   │   │   assignment           │   │ (perm JSONB)│
   │   unique LOWER)  │   │ (status partial unique │   │ (is_builtin)│
   └──────────────────┘   │  COALESCE sentinel)    │   └─────────────┘
                          └────────────────────────┘
   ┌──────────────────┐   ┌──────────────────────────────┐
   │ admin_setting    │   │ admin_outbox (T3, wms-shape) │
   │ (composite PK    │   │   + admin_event_dedupe (T8,  │
   │  sentinel UUID)  │   │     4-outcome incl LATE)     │
   └──────────────────┘   └──────────────────────────────┘

   READ-MODEL (V2 = 15 tables, projection-only, no FK between rows)
   ┌─────────────────────────────────────────────────────────────┐
   │  Master Reference Projections (master.*.v1 source)          │
   │  warehouse / zone / location / sku / lot / partner — *_ref  │
   └─────────────────────────────────────────────────────────────┘

   ┌──────────────────────────────┐  ┌──────────────────────────────┐
   │ admin_asn_summary            │  │ admin_order_summary          │
   │ admin_inspection_summary 1:1 │  │ admin_shipment_summary       │
   │ (inbound.* projection)       │  │ (outbound.* projection)      │
   └──────────────────────────────┘  └──────────────────────────────┘

   ┌──────────────────────────────────────────────────────────────┐
   │ admin_inventory_snapshot   (inventory.* projection, primary  │
   │   PK (location, sku, lot=sentinel)   dashboard table)        │
   └──────────────────────────────────────────────────────────────┘

   ┌──────────────────────────────┐  ┌──────────────────────────────┐
   │ admin_adjustment_audit       │  │ admin_alert_log              │
   │ (append-only, PK=eventId)    │  │ (append-only + ack mutation) │
   └──────────────────────────────┘  └──────────────────────────────┘

   ┌──────────────────────────────┐  ┌──────────────────────────────┐
   │ admin_throughput_inbound_    │  │ admin_throughput_outbound_   │
   │   daily (date+wh PK)         │  │   daily (date+wh PK)         │
   └──────────────────────────────┘  └──────────────────────────────┘

   V99 = data-only (4 built-in role + bootstrap user + seed settings,
         dev/standalone profile-gated)
```

Total: **21 tables** across 2 schema migrations + 1 data-only migration
(V1=194, V2=398, V99=139 line — 731 total). No trigger functions —
admin uses **PK-as-dedupe natural patterns** (admin_adjustment_audit /
admin_alert_log use the source `eventId` as PK so duplicate insert
silently fails) instead of the V5/V7-style trigger pattern used by
inventory / inbound / outbound.

---

## 1. admin_user (V1, domain-model § 1)

Operator identity. Email is case-insensitive at the DB layer.

```sql
CREATE TABLE admin_user (
    id                      UUID            PRIMARY KEY,
    user_code               VARCHAR(40)     NOT NULL UNIQUE,
    email                   VARCHAR(200)    NOT NULL,
    name                    VARCHAR(200)    NOT NULL,
    phone                   VARCHAR(30)     NULL,
    status                  VARCHAR(16)     NOT NULL,
    default_warehouse_id    UUID            NULL,
    version                 BIGINT          NOT NULL DEFAULT 0,
    created_at              TIMESTAMPTZ     NOT NULL,
    created_by              VARCHAR(120)    NULL,
    updated_at              TIMESTAMPTZ     NOT NULL,
    updated_by              VARCHAR(120)    NULL,
    CONSTRAINT ck_admin_user_status
        CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

CREATE UNIQUE INDEX uq_admin_user_email_ci
    ON admin_user (LOWER(email));

CREATE INDEX idx_admin_user_status
    ON admin_user (status);
```

**`uq_admin_user_email_ci` — case-insensitive partial-expression
unique**: the index uses `LOWER(email)` to enforce uniqueness regardless
of casing. The application lowercases on write, but the index defends
against direct DB inserts or migration paths that bypass the controller.

**`user_code UNIQUE`**: immutable after creation by domain rule (the
application enforces; SQL only enforces unique).

**`default_warehouse_id` soft-FK**: no SQL FK because the warehouse
identity lives in admin_warehouse_ref (V2 projection, not authoritative)
or in the master-service. The admin domain only validates this via
read-model lookup before persisting.

---

## 2. admin_role (V1, domain-model § 2)

Operator role definitions. Permissions are a JSONB array of strings.

```sql
CREATE TABLE admin_role (
    id                  UUID            PRIMARY KEY,
    role_code           VARCHAR(40)     NOT NULL UNIQUE,
    name                VARCHAR(100)    NOT NULL,
    description         VARCHAR(500)    NULL,
    permissions_json    JSONB           NOT NULL,
    status              VARCHAR(16)     NOT NULL,
    is_builtin          BOOLEAN         NOT NULL DEFAULT FALSE,
    version             BIGINT          NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ     NOT NULL,
    created_by          VARCHAR(120)    NULL,
    updated_at          TIMESTAMPTZ     NOT NULL,
    updated_by          VARCHAR(120)    NULL,
    CONSTRAINT ck_admin_role_status
        CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

CREATE INDEX idx_admin_role_status
    ON admin_role (status);
```

**`permissions_json JSONB`** maps to a JPA entity carrying
`@JdbcTypeCode(SqlTypes.JSON)`. `JsonbColumnRegressionGuardTest`
enforces this at build time (see V1 inline header — same regression
guard as scm-service / notification).

**`is_builtin` flag**: marks the four seeded `WMS_*` roles (see § 14
V99 seed below). The application rejects deactivation / deletion of
`is_builtin=TRUE` rows with `ROLE_BUILTIN_IMMUTABLE`.

---

## 3. admin_user_role_assignment (V1, domain-model § 3)

Many-to-many between users and roles, optionally warehouse-scoped.
Uses the **sentinel-UUID COALESCE pattern** to make a partial-unique
index work with nullable `warehouse_id`.

```sql
CREATE TABLE admin_user_role_assignment (
    id              UUID            PRIMARY KEY,
    user_id         UUID            NOT NULL,
    role_id         UUID            NOT NULL,
    warehouse_id    UUID            NULL,
    granted_at      TIMESTAMPTZ     NOT NULL,
    granted_by      VARCHAR(120)    NOT NULL,
    revoked_at      TIMESTAMPTZ     NULL,
    revoked_by      VARCHAR(120)    NULL,
    status          VARCHAR(16)     NOT NULL,
    version         BIGINT          NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ     NOT NULL,
    updated_at      TIMESTAMPTZ     NOT NULL,
    CONSTRAINT ck_admin_assignment_status
        CHECK (status IN ('ACTIVE', 'REVOKED')),
    CONSTRAINT fk_admin_assignment_user
        FOREIGN KEY (user_id) REFERENCES admin_user (id),
    CONSTRAINT fk_admin_assignment_role
        FOREIGN KEY (role_id) REFERENCES admin_role (id)
);

CREATE UNIQUE INDEX uq_admin_assignment_active
    ON admin_user_role_assignment (
        user_id,
        role_id,
        COALESCE(warehouse_id, '00000000-0000-0000-0000-000000000000'::uuid)
    )
    WHERE status = 'ACTIVE';

CREATE INDEX idx_admin_assignment_user ON admin_user_role_assignment (user_id);
CREATE INDEX idx_admin_assignment_role ON admin_user_role_assignment (role_id);
```

**Sentinel-UUID COALESCE in partial unique**: PostgreSQL treats `NULL`
as not-equal-to-NULL, so a naive `UNIQUE(user_id, role_id, warehouse_id)`
would allow unlimited rows where `warehouse_id IS NULL` for the same
`(user, role)` pair. Mapping nulls to the sentinel `00000000-...` via
`COALESCE` collapses them into a single bucket, enforcing the
"one ACTIVE assignment per (user, role, warehouse-or-global)" invariant.

**`WHERE status='ACTIVE'`**: REVOKED rows accumulate without colliding
with new ACTIVE assignments — the partial filter scopes uniqueness to
active rows only.

---

## 4. admin_setting (V1, domain-model § 4)

Operational settings. Composite PK + sentinel UUID + scope CHECK.

```sql
CREATE TABLE admin_setting (
    key             VARCHAR(100)    NOT NULL,
    warehouse_id    UUID            NOT NULL,
    scope           VARCHAR(16)     NOT NULL,
    value_json      JSONB           NOT NULL,
    schema_json     JSONB           NOT NULL,
    description     VARCHAR(500)    NULL,
    version         BIGINT          NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ     NOT NULL,
    created_by      VARCHAR(120)    NULL,
    updated_at      TIMESTAMPTZ     NOT NULL,
    updated_by      VARCHAR(120)    NULL,
    PRIMARY KEY (key, warehouse_id),
    CONSTRAINT ck_admin_setting_scope
        CHECK (scope IN ('GLOBAL', 'WAREHOUSE')),
    CONSTRAINT ck_admin_setting_warehouse_required
        CHECK (
            (scope = 'GLOBAL' AND warehouse_id = '00000000-0000-0000-0000-000000000000'::uuid) OR
            (scope = 'WAREHOUSE' AND warehouse_id <> '00000000-0000-0000-0000-000000000000'::uuid)
        )
);

CREATE INDEX idx_admin_setting_scope ON admin_setting (scope);
```

**Sentinel UUID for GLOBAL scope** (second instance of the sentinel
pattern): a composite PK requires both columns NOT NULL. GLOBAL-scope
settings store `00000000-...` in `warehouse_id`. The
`ck_admin_setting_warehouse_required` CHECK pins the relationship —
GLOBAL must equal sentinel; WAREHOUSE must not.

**`value_json` / `schema_json` JSONB**: `value_json` carries the
runtime value; `schema_json` documents the expected shape (JSON Schema
fragment) for self-describing settings. JPA entities use
`@JdbcTypeCode(SqlTypes.JSON)`.

---

## 5. admin_outbox (V1, transactional T3)

Standard wms-specific outbox shape — UUID PK + JSONB payload +
`partition_key` + `attempt_count`. Matches inventory / notification /
inbound / outbound outboxes.

```sql
CREATE TABLE admin_outbox (
    id              UUID            PRIMARY KEY,
    aggregate_type  VARCHAR(40)     NOT NULL,
    aggregate_id    VARCHAR(120)    NOT NULL,
    event_type      VARCHAR(60)     NOT NULL,
    event_version   VARCHAR(10)     NOT NULL DEFAULT 'v1',
    payload         JSONB           NOT NULL,
    partition_key   VARCHAR(120)    NOT NULL,
    created_at      TIMESTAMPTZ     NOT NULL,
    published_at    TIMESTAMPTZ     NULL,
    attempt_count   INT             NOT NULL DEFAULT 0
);

CREATE INDEX idx_admin_outbox_pending
    ON admin_outbox (created_at)
    WHERE published_at IS NULL;

CREATE INDEX idx_admin_outbox_aggregate
    ON admin_outbox (aggregate_type, aggregate_id);
```

`aggregate_id VARCHAR(120)` (wider than other services' `UUID`
column) accommodates non-UUID aggregate ids (e.g., a setting `key`).
This is the only divergence from sibling outbox shapes.

**No append-only trigger**: admin chose PK-natural-dedupe for audit
tables (§ 12 below) instead of the trigger pattern. The outbox itself
is mutable on the `published_at` column by the publisher (same as other
services) and not protected against DELETE by a trigger — production
hardens via role grants (ops procedure documentation).

---

## 6. admin_event_dedupe (V1, transactional T8 + LWW context)

Consumer-side dedupe. The **4-outcome enum** distinguishes admin's LWW
projection from other services' 3-outcome dedupe.

```sql
CREATE TABLE admin_event_dedupe (
    event_id        UUID            PRIMARY KEY,
    event_type      VARCHAR(60)     NOT NULL,
    processed_at    TIMESTAMPTZ     NOT NULL DEFAULT now(),
    outcome         VARCHAR(30)     NOT NULL,
    CONSTRAINT ck_admin_dedupe_outcome
        CHECK (outcome IN ('APPLIED', 'IGNORED_DUPLICATE',
                           'IGNORED_DUPLICATE_LATE', 'FAILED'))
);

CREATE INDEX idx_admin_event_dedupe_processed_at
    ON admin_event_dedupe (processed_at);
```

| Outcome | Meaning |
|---|---|
| `APPLIED` | Event was projected to the read-model |
| `IGNORED_DUPLICATE` | Same `event_id` already in `admin_event_dedupe` (Kafka re-poll) |
| **`IGNORED_DUPLICATE_LATE`** | Event arrived after a newer event was already projected — `last_event_at` LWW guard rejected it. **Unique to admin** |
| `FAILED` | Projection threw; row recorded for replay diagnosis |

**`IGNORED_DUPLICATE_LATE` is the LWW signal**: when out-of-order
delivery causes a stale event to arrive after a newer one (e.g., Kafka
partition rebalance + slow consumer), the LWW upsert (see § 7 below)
rejects it via the `WHERE last_event_at < incoming` clause. The dedupe
table records the outcome as `LATE` so replay tooling can distinguish
"normal duplicate" from "out-of-order stale".

---

## 7. Read-Model Projection Pattern (V2 entire migration)

V2 creates **15 read-model tables**. The pattern is identical across
all of them and is the architectural backbone of admin-service:

### 7.1 LWW upsert formula

Every projection write follows the same template:

```sql
INSERT INTO <table> (id, ..., last_event_at, version)
VALUES (:id, ..., :incomingEventAt, 1)
ON CONFLICT (id) DO UPDATE
    SET ... = EXCLUDED....,
        last_event_at = EXCLUDED.last_event_at,
        version = <table>.version + 1
    WHERE <table>.last_event_at < EXCLUDED.last_event_at;
```

- `last_event_at` is the source event's `occurred_at` (NOT the consumer's
  wall-clock).
- The `WHERE` clause rejects updates from stale events — the row keeps
  the newer state.
- `version` increments on every successful write for JPA optimistic-lock
  (admin's own writers do not concurrent-write read-model rows in v1,
  but the column is in place for v2 admin-mutation scenarios).

### 7.2 No FK constraints between read-model tables

Read-model tables intentionally carry **no FK constraints** to each
other. Rationale:

- **Replayable**: a single-table truncate + re-project must succeed
  without cascade chaos. A `admin_inventory_snapshot` row may reference
  a `sku_id` that has not yet been projected from `master.sku.*` — the
  domain layer reads both and tolerates the temporary mismatch.
- **Cross-row consistency belongs upstream**: master-service publishes
  the canonical reference; admin's projection just tracks the
  latest-known state. SQL FKs would couple replay order to publish
  order, which is operationally hostile.

### 7.3 Append-only via PK = eventId (audit tables, § 12)

`admin_adjustment_audit` and `admin_alert_log` use the source `eventId`
as the PK. A duplicate insert silently fails on PK conflict — an
INSERT-only natural-dedupe pattern that avoids the trigger machinery
used elsewhere (inventory V5 / inbound V7 / outbound V8). Application
role grants exclude UPDATE / DELETE on these tables in production (ops
procedure documentation, not Flyway), with the exception of the alert
acknowledge path (§ 12).

### 7.4 18 source topics

V2 projects from **18 source topics** catalogued in
[`../../contracts/events/admin-events.md`](../../contracts/events/admin-events.md)
§ Consumed Events. Each table consumes 1-N topics.

---

## 8. Master Reference Projections (V2 § 1-6)

Six `*_ref` tables projecting `master.{warehouse,zone,location,sku,lot,partner}.v1`.

```sql
-- admin_warehouse_ref (V2 § 1)
CREATE TABLE admin_warehouse_ref (
    id              UUID            PRIMARY KEY,
    warehouse_code  VARCHAR(40)     NOT NULL,
    name            VARCHAR(200)    NOT NULL,
    timezone        VARCHAR(40)     NULL,
    status          VARCHAR(16)     NOT NULL,
    last_event_at   TIMESTAMPTZ     NOT NULL,
    version         BIGINT          NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX uq_admin_warehouse_ref_code ON admin_warehouse_ref (warehouse_code);
CREATE INDEX idx_admin_warehouse_ref_last_event_at ON admin_warehouse_ref (last_event_at DESC);

-- admin_zone_ref (V2 § 2) — analogous shape: id PK, warehouse_id, zone_code, name,
-- zone_type, status, last_event_at, version + indexes by warehouse + last_event_at.

-- admin_location_ref (V2 § 3) — id PK, location_code, warehouse_id, zone_id,
-- location_type, status, last_event_at, version + 4 indexes
-- (warehouse + zone + code + last_event_at).

-- admin_sku_ref (V2 § 4) — id PK, sku_code (UNIQUE), name, base_uom, tracking_type,
-- status, last_event_at, version.

-- admin_lot_ref (V2 § 5) — id PK, sku_id, lot_no, expiry_date, status,
-- last_event_at, version + 3 indexes (sku + lot_no + last_event_at).

-- admin_partner_ref (V2 § 6) — id PK, partner_code (UNIQUE), name, partner_type,
-- status, last_event_at, version.
-- See V2 source for byte-identical column lists.
```

**Shape pattern**: every `*_ref` table carries `last_event_at` +
`version` for the LWW upsert formula (§ 7.1). Natural-key columns
(`warehouse_code`, `sku_code`, `partner_code`) have a separate
`UNIQUE` index where applicable.

**Subset projection** — admin does not store the full master payload
(e.g., warehouse `address`, sku `barcode`, `hazard_class`). The
projection trims to dashboard-relevant fields; if admin needs the
full record, it fetches from master-service via REST.

---

## 9. ASN + Inspection Summary (V2 § 7-8)

Inbound-side aggregation summaries.

```sql
CREATE TABLE admin_asn_summary (
    asn_id                  UUID            PRIMARY KEY,
    asn_no                  VARCHAR(80)     NOT NULL,
    warehouse_id            UUID            NOT NULL,
    supplier_partner_id     UUID            NULL,
    supplier_name           VARCHAR(200)    NULL,
    status                  VARCHAR(40)     NOT NULL,
    source                  VARCHAR(40)     NULL,
    expected_arrive_date    DATE            NULL,
    line_count              INTEGER         NOT NULL DEFAULT 0,
    received_at             TIMESTAMPTZ     NULL,
    closed_at               TIMESTAMPTZ     NULL,
    last_event_at           TIMESTAMPTZ     NOT NULL,
    version                 BIGINT          NOT NULL DEFAULT 0
);

CREATE INDEX idx_admin_asn_summary_warehouse   ON admin_asn_summary (warehouse_id);
CREATE INDEX idx_admin_asn_summary_status      ON admin_asn_summary (status);
CREATE INDEX idx_admin_asn_summary_received_at ON admin_asn_summary (received_at DESC);
CREATE INDEX idx_admin_asn_summary_supplier    ON admin_asn_summary (supplier_partner_id);

CREATE TABLE admin_inspection_summary (
    asn_id                      UUID            PRIMARY KEY,
    warehouse_id                UUID            NOT NULL,
    inspection_completed_at     TIMESTAMPTZ     NOT NULL,
    inspector_id                VARCHAR(120)    NULL,
    total_lines                 INTEGER         NOT NULL DEFAULT 0,
    discrepancy_count           INTEGER         NOT NULL DEFAULT 0,
    total_qty_expected          INTEGER         NOT NULL DEFAULT 0,
    total_qty_passed            INTEGER         NOT NULL DEFAULT 0,
    total_qty_damaged           INTEGER         NOT NULL DEFAULT 0,
    total_qty_short             INTEGER         NOT NULL DEFAULT 0,
    last_event_at               TIMESTAMPTZ     NOT NULL,
    version                     BIGINT          NOT NULL DEFAULT 0
);

CREATE INDEX idx_admin_inspection_summary_warehouse ON admin_inspection_summary (warehouse_id);
```

**Denormalised `line_count` + `supplier_name`**: admin trades storage
for query latency — dashboard queries don't need to JOIN. The denorm
is refreshed by the projection on every inbound event that touches the
ASN.

**`admin_inspection_summary` 1:1 with ASN**: PK is `asn_id` (not its
own id). One inspection per ASN — matches the inbound-service
aggregate cardinality.

**3-bucket qty roll-up** (`expected` / `passed` / `damaged` / `short`):
admin keeps the per-ASN totals so a warehouse-level "today's pass rate"
KPI is a simple `SUM(passed) / SUM(expected)` query.

---

## 10. Order + Shipment Summary (V2 § 9-10)

Outbound-side aggregation. Mirror the inbound pair pattern.

```sql
CREATE TABLE admin_order_summary (
    order_id                UUID            PRIMARY KEY,
    order_no                VARCHAR(80)     NOT NULL,
    warehouse_id            UUID            NOT NULL,
    customer_partner_id     UUID            NULL,
    customer_name           VARCHAR(200)    NULL,
    status                  VARCHAR(40)     NOT NULL,
    source                  VARCHAR(40)     NULL,
    required_ship_date      DATE            NULL,
    line_count              INTEGER         NOT NULL DEFAULT 0,
    saga_state              VARCHAR(40)     NULL,
    received_at             TIMESTAMPTZ     NULL,
    shipped_at              TIMESTAMPTZ     NULL,
    last_event_at           TIMESTAMPTZ     NOT NULL,
    version                 BIGINT          NOT NULL DEFAULT 0
);

CREATE INDEX idx_admin_order_summary_warehouse   ON admin_order_summary (warehouse_id);
CREATE INDEX idx_admin_order_summary_status      ON admin_order_summary (status);
CREATE INDEX idx_admin_order_summary_received_at ON admin_order_summary (received_at DESC);
CREATE INDEX idx_admin_order_summary_customer    ON admin_order_summary (customer_partner_id);

CREATE TABLE admin_shipment_summary (
    shipment_id     UUID            PRIMARY KEY,
    order_id        UUID            NOT NULL,
    order_no        VARCHAR(80)     NULL,
    warehouse_id    UUID            NOT NULL,
    shipment_no     VARCHAR(80)     NULL,
    carrier_code    VARCHAR(40)     NULL,
    tracking_no     VARCHAR(120)    NULL,
    shipped_at      TIMESTAMPTZ     NOT NULL,
    total_qty       INTEGER         NOT NULL DEFAULT 0,
    last_event_at   TIMESTAMPTZ     NOT NULL,
    version         BIGINT          NOT NULL DEFAULT 0
);

CREATE INDEX idx_admin_shipment_summary_order      ON admin_shipment_summary (order_id);
CREATE INDEX idx_admin_shipment_summary_warehouse  ON admin_shipment_summary (warehouse_id);
CREATE INDEX idx_admin_shipment_summary_shipped_at ON admin_shipment_summary (shipped_at DESC);
```

**`saga_state` denormalised on order_summary**: admin tracks the
OutboundSaga state on the order row for dashboards that show
"stuck orders". The saga itself lives in outbound-service's
`outbound_saga` table — admin projects the state via the saga's
state-change events into this denormalised column.

---

## 11. admin_inventory_snapshot (V2 § 11) — primary dashboard table

The single largest read-model table by query traffic. Composite PK
with **sentinel UUID for non-LOT-tracked lot_id** (third instance of
the sentinel pattern, mirrors V1 admin_setting).

```sql
CREATE TABLE admin_inventory_snapshot (
    location_id         UUID            NOT NULL,
    sku_id              UUID            NOT NULL,
    lot_id              UUID            NOT NULL,
    warehouse_id        UUID            NOT NULL,
    location_code       VARCHAR(80)     NULL,
    sku_code            VARCHAR(40)     NULL,
    lot_no              VARCHAR(80)     NULL,
    available_qty       INTEGER         NOT NULL DEFAULT 0,
    reserved_qty        INTEGER         NOT NULL DEFAULT 0,
    damaged_qty         INTEGER         NOT NULL DEFAULT 0,
    on_hand_qty         INTEGER         NOT NULL DEFAULT 0,
    low_stock_flag      BOOLEAN         NOT NULL DEFAULT FALSE,
    last_adjusted_at    TIMESTAMPTZ     NULL,
    last_event_at       TIMESTAMPTZ     NOT NULL,
    version             BIGINT          NOT NULL DEFAULT 0,
    PRIMARY KEY (location_id, sku_id, lot_id)
);

CREATE INDEX idx_admin_inventory_snapshot_warehouse
    ON admin_inventory_snapshot (warehouse_id);

CREATE INDEX idx_admin_inventory_snapshot_low_stock
    ON admin_inventory_snapshot (warehouse_id)
    WHERE low_stock_flag = TRUE;

CREATE INDEX idx_admin_inventory_snapshot_last_event_at
    ON admin_inventory_snapshot (last_event_at DESC);
```

**Composite PK `(location_id, sku_id, lot_id)`** with **lot_id NOT NULL +
sentinel**: non-LOT SKUs store `00000000-...` in `lot_id`. The
adapter translates the sentinel back to NULL at the domain boundary,
so the read API exposes `lot_id` as nullable while the DB layer enjoys
a NOT NULL composite key (simpler JPA composite-id, simpler index
locality).

**4 quantity columns** (`available` + `reserved` + `damaged` +
`on_hand`): all four are projected separately so the dashboard
queries can choose the relevant bucket without an arithmetic step.
`on_hand_qty` is a denormalisation of `available + reserved` so
"total on-hand" queries are a single column read.

**`low_stock_flag` + partial index `WHERE low_stock_flag=TRUE`**: the
"warehouses with low-stock alerts" dashboard query scans only the
flagged rows — the partial index size scales with the small fraction
of rows that are actually low-stock at any moment, not with total
SKU x location count.

---

## 12. Append-Only Audit Tables (V2 § 12-13)

Two tables use the **PK = source eventId** natural-dedupe pattern.

### 12.1 admin_adjustment_audit

```sql
CREATE TABLE admin_adjustment_audit (
    id              UUID            PRIMARY KEY,
    location_id     UUID            NOT NULL,
    sku_id          UUID            NOT NULL,
    lot_id          UUID            NULL,
    warehouse_id    UUID            NOT NULL,
    bucket          VARCHAR(40)     NOT NULL,
    delta           INTEGER         NOT NULL,
    reason_code     VARCHAR(60)     NULL,
    reason_note     VARCHAR(500)    NULL,
    actor_id        VARCHAR(120)    NULL,
    occurred_at     TIMESTAMPTZ     NOT NULL,
    projected_at    TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_admin_adjustment_audit_warehouse   ON admin_adjustment_audit (warehouse_id);
CREATE INDEX idx_admin_adjustment_audit_occurred_at ON admin_adjustment_audit (occurred_at DESC);
CREATE INDEX idx_admin_adjustment_audit_sku         ON admin_adjustment_audit (sku_id);
CREATE INDEX idx_admin_adjustment_audit_location    ON admin_adjustment_audit (location_id);
```

**`id UUID PRIMARY KEY` = source eventId**: the projector inserts the
event row using the source `eventId` as the PK. A duplicate Kafka
re-delivery collides on the PK and silently fails — no need for an
upstream `SELECT` round-trip. This is the **append-only-via-PK**
pattern, distinct from the V5/V7-style trigger pattern used
elsewhere.

`occurred_at` is the event's wall-clock; `projected_at` is the
consumer's wall-clock. Difference between the two is the projection
lag, useful in metrics.

### 12.2 admin_alert_log — conditional mutability

```sql
CREATE TABLE admin_alert_log (
    id                  UUID            PRIMARY KEY,
    alert_type          VARCHAR(40)     NOT NULL,
    warehouse_id        UUID            NULL,
    location_id         UUID            NULL,
    sku_id              UUID            NULL,
    lot_id              UUID            NULL,
    threshold_qty       INTEGER         NULL,
    actual_qty          INTEGER         NULL,
    detected_at         TIMESTAMPTZ     NOT NULL,
    acknowledged_at     TIMESTAMPTZ     NULL,
    acknowledged_by     VARCHAR(120)    NULL,
    projected_at        TIMESTAMPTZ     NOT NULL DEFAULT now(),
    CONSTRAINT ck_admin_alert_log_type
        CHECK (alert_type IN ('LOW_STOCK', 'ANOMALY'))
);

CREATE INDEX idx_admin_alert_log_warehouse   ON admin_alert_log (warehouse_id);
CREATE INDEX idx_admin_alert_log_detected_at ON admin_alert_log (detected_at DESC);

CREATE INDEX idx_admin_alert_log_unacked
    ON admin_alert_log (warehouse_id, detected_at DESC)
    WHERE acknowledged_at IS NULL;
```

**`acknowledged_at` / `acknowledged_by` are the sole mutable columns**.
Per
[`architecture.md`](architecture.md) § 1.6 Justification, this is the
**conditional mutability exception** — the application layer can
`UPDATE admin_alert_log SET acknowledged_at = NOW(), acknowledged_by =
:user WHERE id = :id`, and only that path. All other columns are
write-once at projection time.

In production, role grants restrict UPDATE to those two columns; in
dev, the application code is the only enforcement (no trigger).

**Partial unacked index** (`WHERE acknowledged_at IS NULL`): the
"open alerts" dashboard scans only the unacknowledged rows. As
operators ack alerts, the rows leave the partial index hot-path
naturally.

---

## 13. Daily Throughput Counters (V2 § 14-15)

Two per-day per-warehouse roll-up counter tables. Composite PK on
`(date, warehouse_id)`.

```sql
CREATE TABLE admin_throughput_inbound_daily (
    date            DATE            NOT NULL,
    warehouse_id    UUID            NOT NULL,
    putaway_count   INTEGER         NOT NULL DEFAULT 0,
    qty_received    INTEGER         NOT NULL DEFAULT 0,
    last_event_at   TIMESTAMPTZ     NOT NULL,
    version         BIGINT          NOT NULL DEFAULT 0,
    PRIMARY KEY (date, warehouse_id)
);

CREATE INDEX idx_admin_throughput_inbound_daily_warehouse
    ON admin_throughput_inbound_daily (warehouse_id);

CREATE TABLE admin_throughput_outbound_daily (
    date            DATE            NOT NULL,
    warehouse_id    UUID            NOT NULL,
    shipment_count  INTEGER         NOT NULL DEFAULT 0,
    qty_shipped     INTEGER         NOT NULL DEFAULT 0,
    last_event_at   TIMESTAMPTZ     NOT NULL,
    version         BIGINT          NOT NULL DEFAULT 0,
    PRIMARY KEY (date, warehouse_id)
);

CREATE INDEX idx_admin_throughput_outbound_daily_warehouse
    ON admin_throughput_outbound_daily (warehouse_id);
```

**Atomic counter update**: every `inbound.putaway.completed` event
fires an UPSERT:

```sql
INSERT INTO admin_throughput_inbound_daily (date, warehouse_id, putaway_count, qty_received, last_event_at)
VALUES (:eventDate, :warehouseId, 1, :qty, :eventAt)
ON CONFLICT (date, warehouse_id) DO UPDATE
    SET putaway_count = admin_throughput_inbound_daily.putaway_count + 1,
        qty_received  = admin_throughput_inbound_daily.qty_received  + EXCLUDED.qty_received,
        last_event_at = GREATEST(admin_throughput_inbound_daily.last_event_at, EXCLUDED.last_event_at);
```

The counter is monotone — adjustments are not subtracted; instead a
correcting event fires its own row in the audit log. Replays are
idempotent because admin_event_dedupe gates the projector before
the counter UPSERT.

---

## 14. V99 Dev Seed (data-only)

V99 is **data-only** (139 line of INSERTs, no DDL) and **profile-gated**
— Flyway's location filter excludes it from prod migration runs. The
seed provides:

| Seed type | Rows | Stable UUIDs |
|---|---|---|
| Built-in roles | 4 (`WMS_VIEWER` / `WMS_OPERATOR` / `WMS_SUPERVISOR` / `WMS_ADMIN`) | `11111111-...` / `22222222-...` / `33333333-...` / `44444444-...` |
| Bootstrap admin user | 1 (`admin@wms.local`, assigned WMS_ADMIN) | well-known dev id |
| Seed settings | 7 (low-stock threshold + reservation TTL + saga retry budget + etc.) | scope=GLOBAL, sentinel warehouse_id |

**Stable UUIDs** are intentional — e2e fixtures, dev tools, and
cross-environment scripts can refer to roles by id without round-tripping
through the DB.

**Production deploy**: prod runs a **separate manual provisioning
procedure** (or per-env migration outside the V99 location) to create
built-in roles + an initial admin user. V99 is gated out via Flyway
callback / location filter at the platform level.

See [`domain-model.md`](domain-model.md) § Reference Data Snapshot for
the full seed catalog.

---

## 15. Indexing Strategy Summary

| Table | Index | Type | Purpose |
|---|---|---|---|
| `admin_user` | `admin_user_pkey` | PK | row lookup |
| `admin_user` | `admin_user_user_code_key` | unique | global user_code |
| `admin_user` | `uq_admin_user_email_ci` | partial expression unique | case-insensitive email |
| `admin_user` | `idx_admin_user_status` | btree | status filter |
| `admin_role` | `admin_role_pkey` | PK | row lookup |
| `admin_role` | `admin_role_role_code_key` | unique | global role_code |
| `admin_role` | `idx_admin_role_status` | btree | status filter |
| `admin_user_role_assignment` | `admin_user_role_assignment_pkey` | PK | row lookup |
| `admin_user_role_assignment` | `uq_admin_assignment_active` | partial unique expression | one ACTIVE per (user, role, warehouse) |
| `admin_user_role_assignment` | `idx_admin_assignment_user` | btree | per-user enumeration |
| `admin_user_role_assignment` | `idx_admin_assignment_role` | btree | per-role enumeration |
| `admin_setting` | `admin_setting_pkey` | PK compound | (key, warehouse_id) |
| `admin_setting` | `idx_admin_setting_scope` | btree | scope filter |
| `admin_outbox` | `admin_outbox_pkey` | PK | row lookup |
| `admin_outbox` | `idx_admin_outbox_pending` | partial (`published_at NULL`) | publisher FIFO scan |
| `admin_outbox` | `idx_admin_outbox_aggregate` | btree | aggregate-scoped lookup |
| `admin_event_dedupe` | `admin_event_dedupe_pkey` | PK | dedupe by event_id |
| `admin_event_dedupe` | `idx_admin_event_dedupe_processed_at` | btree | retention sweeper |
| `admin_warehouse_ref` | `admin_warehouse_ref_pkey` | PK | upsert target |
| `admin_warehouse_ref` | `uq_admin_warehouse_ref_code` | unique | natural-key |
| `admin_warehouse_ref` | `idx_admin_warehouse_ref_last_event_at` | btree | recently-changed query |
| `admin_zone_ref` | `admin_zone_ref_pkey` | PK | upsert target |
| `admin_zone_ref` | `idx_admin_zone_ref_warehouse` | btree | warehouse-scoped |
| `admin_zone_ref` | `idx_admin_zone_ref_last_event_at` | btree | recently-changed query |
| `admin_location_ref` | `admin_location_ref_pkey` | PK | upsert target |
| `admin_location_ref` | `idx_admin_location_ref_warehouse` | btree | warehouse-scoped |
| `admin_location_ref` | `idx_admin_location_ref_zone` | btree | zone-scoped |
| `admin_location_ref` | `idx_admin_location_ref_code` | btree | natural-key lookup |
| `admin_location_ref` | `idx_admin_location_ref_last_event_at` | btree | recently-changed query |
| `admin_sku_ref` | `admin_sku_ref_pkey` | PK | upsert target |
| `admin_sku_ref` | `uq_admin_sku_ref_code` | unique | natural-key |
| `admin_sku_ref` | `idx_admin_sku_ref_last_event_at` | btree | recently-changed query |
| `admin_lot_ref` | `admin_lot_ref_pkey` | PK | upsert target |
| `admin_lot_ref` | `idx_admin_lot_ref_sku` | btree | per-SKU enumeration |
| `admin_lot_ref` | `idx_admin_lot_ref_lot_no` | btree | natural-key lookup |
| `admin_lot_ref` | `idx_admin_lot_ref_last_event_at` | btree | recently-changed query |
| `admin_partner_ref` | `admin_partner_ref_pkey` | PK | upsert target |
| `admin_partner_ref` | `uq_admin_partner_ref_code` | unique | natural-key |
| `admin_partner_ref` | `idx_admin_partner_ref_last_event_at` | btree | recently-changed query |
| `admin_asn_summary` | `admin_asn_summary_pkey` (asn_id) | PK | row lookup |
| `admin_asn_summary` | `idx_admin_asn_summary_warehouse` | btree | warehouse drill-down |
| `admin_asn_summary` | `idx_admin_asn_summary_status` | btree | status filter |
| `admin_asn_summary` | `idx_admin_asn_summary_received_at` | btree | recency sort |
| `admin_asn_summary` | `idx_admin_asn_summary_supplier` | btree | supplier drill-down |
| `admin_inspection_summary` | `admin_inspection_summary_pkey` (asn_id) | PK | 1:1 with ASN |
| `admin_inspection_summary` | `idx_admin_inspection_summary_warehouse` | btree | warehouse drill-down |
| `admin_order_summary` | `admin_order_summary_pkey` (order_id) | PK | row lookup |
| `admin_order_summary` | `idx_admin_order_summary_warehouse` | btree | warehouse drill-down |
| `admin_order_summary` | `idx_admin_order_summary_status` | btree | status filter |
| `admin_order_summary` | `idx_admin_order_summary_received_at` | btree | recency sort |
| `admin_order_summary` | `idx_admin_order_summary_customer` | btree | customer drill-down |
| `admin_shipment_summary` | `admin_shipment_summary_pkey` (shipment_id) | PK | row lookup |
| `admin_shipment_summary` | `idx_admin_shipment_summary_order` | btree | per-order audit |
| `admin_shipment_summary` | `idx_admin_shipment_summary_warehouse` | btree | warehouse drill-down |
| `admin_shipment_summary` | `idx_admin_shipment_summary_shipped_at` | btree | recency sort |
| `admin_inventory_snapshot` | `admin_inventory_snapshot_pkey` | PK compound 3-col | (location, sku, lot=sentinel) |
| `admin_inventory_snapshot` | `idx_admin_inventory_snapshot_warehouse` | btree | warehouse drill-down |
| `admin_inventory_snapshot` | `idx_admin_inventory_snapshot_low_stock` | partial (`low_stock_flag=TRUE`) | dashboard hot path |
| `admin_inventory_snapshot` | `idx_admin_inventory_snapshot_last_event_at` | btree | recently-changed query |
| `admin_adjustment_audit` | `admin_adjustment_audit_pkey` (id = eventId) | PK natural dedupe | append-only |
| `admin_adjustment_audit` | `idx_admin_adjustment_audit_warehouse` | btree | warehouse drill-down |
| `admin_adjustment_audit` | `idx_admin_adjustment_audit_occurred_at` | btree | recency sort |
| `admin_adjustment_audit` | `idx_admin_adjustment_audit_sku` | btree | per-SKU audit |
| `admin_adjustment_audit` | `idx_admin_adjustment_audit_location` | btree | per-location audit |
| `admin_alert_log` | `admin_alert_log_pkey` (id = eventId) | PK natural dedupe | append-only + ack mutation |
| `admin_alert_log` | `idx_admin_alert_log_warehouse` | btree | warehouse drill-down |
| `admin_alert_log` | `idx_admin_alert_log_detected_at` | btree | recency sort |
| `admin_alert_log` | `idx_admin_alert_log_unacked` | partial (`acknowledged_at NULL`) | open-alerts dashboard hot path |
| `admin_throughput_inbound_daily` | `admin_throughput_inbound_daily_pkey` | PK compound | (date, warehouse_id) |
| `admin_throughput_inbound_daily` | `idx_admin_throughput_inbound_daily_warehouse` | btree | warehouse drill-down |
| `admin_throughput_outbound_daily` | `admin_throughput_outbound_daily_pkey` | PK compound | (date, warehouse_id) |
| `admin_throughput_outbound_daily` | `idx_admin_throughput_outbound_daily_warehouse` | btree | warehouse drill-down |

**Partial indexes (5)**: outbox pending / inventory low_stock /
alert_log unacked / user_role assignment active / user email
case-insensitive. Each filters to the dashboard hot path; the rest of
the rows live outside the index footprint.

---

## 16. Migration History

| Version | File | Line | Class | Scope |
|---|---|---|---|---|
| V1 | `V1__init.sql` | 194 | init schema | 6 write-side tables (user / role / assignment / setting / outbox / event_dedupe) |
| V2 | `V2__init_readmodel.sql` | 398 | init schema | 15 read-model projection tables (6 ref + 4 summary + inventory_snapshot + 2 audit + 2 daily counter) |
| V99 | `V99__seed_dev_data.sql` | 139 | dev seed (data-only) | 4 built-in roles + bootstrap admin user + 7 seed settings, profile-gated |

When `V3+` lands (next prod migration; V99 stays at the dev seed
position), this document must be updated in the same commit (per the
retrospective contract introduced by TASK-BE-157).

---

## References

- [`domain-model.md`](domain-model.md) — domain meaning of each table (canonical)
- [`architecture.md`](architecture.md) — § Architecture Style Rationale (Layered CQRS), § Read-Model Projection Pattern, § Persistence, § Open Items, § 1.6 Justification (alert ack conditional mutability)
- [`idempotency.md`](idempotency.md) — § 2 Kafka 30d eventId dedupe + § 2.5/2.6 LWW upsert mechanics
- [`external-integrations.md`](external-integrations.md) — zero-state (BE-159)
- [`../../contracts/events/admin-events.md`](../../contracts/events/admin-events.md) — 18 consumed source topics
- [`../inventory-service/database-design.md`](../inventory-service/database-design.md) — sibling (BE-157, primary template)
- [`../notification-service/database-design.md`](../notification-service/database-design.md) — sibling (BE-160)
- [`../master-service/database-design.md`](../master-service/database-design.md) — sibling (BE-161)
- [`../inbound-service/database-design.md`](../inbound-service/database-design.md) — sibling (BE-162)
- [`../outbound-service/database-design.md`](../outbound-service/database-design.md) — sibling (BE-163)
- `../../../apps/admin-service/src/main/resources/db/migration/V1__init.sql`
- `../../../apps/admin-service/src/main/resources/db/migration/V2__init_readmodel.sql`
- `../../../apps/admin-service/src/main/resources/db/migration/V99__seed_dev_data.sql`
- `../../../../../rules/domains/wms.md` — Admin / Operations bounded context
- `../../../../../rules/traits/transactional.md` — T1, T3, T4, T5, T8
- `../../../../../platform/architecture.md` — system-level architecture
