# master-service — Database Design

Physical schema reflection for `master_db`. Flyway migrations under
`apps/master-service/src/main/resources/db/migration/` are the canonical
source-of-truth; this document consolidates them into a single spec
artifact for review-time reasoning. When a new migration lands
(`V8+`), this file must be updated in the same commit (per the
retrospective contract introduced by TASK-BE-157).

**Target engine**: PostgreSQL 14+ (production). Partial unique indexes,
`TIMESTAMPTZ` semantics, and the `CHECK (col = UPPER(col))` SKU casing
guard are PostgreSQL native; portability to other engines is out of scope
for v1. The Flyway scripts also include H2 2.x-compatible syntax for
slice tests.

**Authoritative reference**: [`domain-model.md`](domain-model.md) for the
domain meaning of each table.

---

## Schema Overview

```
                                           ┌─────────────┐
                                           │  warehouses │ (V1)
                                           └──────┬──────┘
                                                  │ FK
                              ┌───────────────────┼────────────────┐
                              ▼                   ▼                ▼
                          ┌────────┐         ┌──────────┐    ┌────────┐
                          │ zones  │ (V3)    │locations │(V4)│  …W3   │
                          └───┬────┘         └──────────┘    └────────┘
                              │ FK ◀──── globally unique location_code
                              └─────────────────────────────────────────┐
                                                                        ▼
                          ┌────────┐                              (location FK
                          │  skus  │ (V5)                          to zones+
                          └───┬────┘                               warehouses)
                              │ FK
                              ▼
                          ┌────────┐
                          │  lots  │ (V6)        ┌──────────┐
                          │  ─────  │            │ partners │ (V7)
                          │ supplier_partner_id  └──────────┘  (independent)
                          │ (no FK — v2 promotion)
                          └────────┘

                          ┌──────────────┐       ┌────────────┐  ┌──────────────────┐
                          │ master_outbox│ (V8)  │  outbox    │  │ processed_events │
                          │ UUID PK, v2  │       │ (V2, stub) │  │ (V2, stub)       │
                          └──────────────┘       └────────────┘  └──────────────────┘
                          (live publisher)       (retained, unused — EntityScan/validate)
```

Total: 9 tables across 8 migrations (V1=32, V2=28, V3=43, V4=53, V5=52,
V6=49, V7=45, V8=master_outbox). **TASK-BE-438** added V8 `master_outbox`
(v2 `AbstractOutboxPublisher`: UUID PK + TEXT payload + `occurred_at` +
`retries`/`last_error`). The original V2 `outbox`/`processed_events` tables
are retained-but-unused (the lib EntityScan + `ddl-auto=validate` require
them) — see § 2 below.

---

## 1. Warehouse Aggregate Root (V1, domain-model § 1)

Aggregate root for warehouse identity. One row per logical warehouse.

```sql
CREATE TABLE warehouses (
    id              UUID         PRIMARY KEY,
    warehouse_code  VARCHAR(10)  NOT NULL,
    name            VARCHAR(100) NOT NULL,
    address         VARCHAR(200),
    timezone        VARCHAR(40)  NOT NULL,
    status          VARCHAR(20)  NOT NULL,
    version         BIGINT       NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ  NOT NULL,
    created_by      VARCHAR(100) NOT NULL,
    updated_at      TIMESTAMPTZ  NOT NULL,
    updated_by      VARCHAR(100) NOT NULL,
    CONSTRAINT uq_warehouses_warehouse_code UNIQUE (warehouse_code),
    CONSTRAINT ck_warehouses_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

CREATE INDEX idx_warehouses_status_updated_at
    ON warehouses (status, updated_at DESC);
```

**Soft deactivation pattern**: `status IN ('ACTIVE', 'INACTIVE')` — no
hard delete in v1 per [`domain-model.md`](domain-model.md) § Common
Aggregate Shape. The `INACTIVE` state is the deletion analog; W6
referential integrity guards (zone / location / lot point at warehouse)
fire at the application layer before the deactivation succeeds.

`idx_warehouses_status_updated_at` supports the default REST list query
(status filter + recent-first sort, per
[`../../contracts/http/master-service-api.md`](../../contracts/http/master-service-api.md)
§ Warehouse list).

---

## 2. Outbox (V8 `master_outbox`, v2 AbstractOutboxPublisher) + retained V2 stubs

master-service publishes via the shared **v2** outbox stack
(`AbstractOutboxPublisher<MasterOutboxEntity>`, `libs/java-messaging`,
ADR-MONO-004 § 5) since **TASK-BE-438**. The live table is `master_outbox`
(V8). The original V2 library tables (`outbox`, `processed_events`) are
**retained but unused** — see "Retained V2 stubs" below.

### Live table: `master_outbox` (V8)

UUID-keyed, occurrence-ordered, with the v2 publisher's retry/last-error
columns. Schema matches `OutboxRowEntity` (the `@MappedSuperclass`
`MasterOutboxEntity` extends).

```sql
CREATE TABLE master_outbox (
    event_id       UUID         PRIMARY KEY,
    event_type     VARCHAR(100) NOT NULL,
    aggregate_type VARCHAR(60)  NOT NULL,
    aggregate_id   VARCHAR(60)  NOT NULL,
    partition_key  VARCHAR(60),
    payload        TEXT         NOT NULL,
    occurred_at    TIMESTAMP    NOT NULL,
    published_at   TIMESTAMP,
    retries        INT          NOT NULL DEFAULT 0,
    last_error     TEXT
);

-- Pending poll: WHERE published_at IS NULL ORDER BY occurred_at ASC.
CREATE INDEX idx_master_outbox_pending
    ON master_outbox (occurred_at)
    WHERE published_at IS NULL;
```

- `event_id` — UUIDv7, generated by `EventEnvelopeSerializer` at write time
  and reused as both the row PK and the envelope `eventId` (so the Kafka
  `eventId` record header matches the payload id; consumers dedupe on one id).
- `occurred_at` — the **domain** event timestamp (`DomainEvent.occurredAt()`),
  not the insert time; the publisher's `master.outbox.lag.seconds` metric is
  `publish − occurred`.
- `partition_key` — left `null` by the write path; the publisher falls back to
  `aggregate_id` as the Kafka record key (per-aggregate ordering per
  `contracts/events/master-events.md` § Topic Layout).
- `retries` / `last_error` — populated by the v2 backoff loop on transient
  Kafka failures.

The partial index keeps only the unpublished tail indexed for the FIFO drain.

### Retained V2 stubs: `outbox` + `processed_events` (DO NOT DROP)

`libs/java-messaging` force-registers `OutboxJpaEntity` (table `outbox`) **and**
`ProcessedEventJpaEntity` (table `processed_events`) in its EntityScan
(`OutboxJpaConfig`, imported via the retained `OutboxAutoConfiguration`). With
`hibernate.ddl-auto=validate`, **both tables must remain present** or the
context fails to boot. V8 therefore leaves them in place:

```sql
-- V2__init_outbox.sql (still applied; rows no longer produced or polled)
CREATE TABLE outbox (
    id              BIGSERIAL    PRIMARY KEY,
    aggregate_type  VARCHAR(100) NOT NULL,
    aggregate_id    VARCHAR(100) NOT NULL,
    event_type      VARCHAR(100) NOT NULL,
    payload         TEXT         NOT NULL,
    created_at      TIMESTAMP    NOT NULL,
    published_at    TIMESTAMP,
    status          VARCHAR(20)  NOT NULL,
    CONSTRAINT ck_outbox_status CHECK (status IN ('PENDING', 'PUBLISHED', 'FAILED'))
);
CREATE INDEX idx_outbox_status_created_at ON outbox (status, created_at);

CREATE TABLE processed_events (
    event_id      VARCHAR(100) PRIMARY KEY,
    event_type    VARCHAR(100) NOT NULL,
    processed_at  TIMESTAMP    NOT NULL
);
```

- The v1 `outbox` table is now **write-free and poll-free** — no scheduler
  drives the lib v1 publisher anymore. `processed_events` was always a
  producer-only stub (master never consumes).
- **Cutover disposition (F1):** any unpublished rows sitting in the v1 `outbox`
  at deploy time are **abandoned** (not migrated): master events are
  low-volume operator-driven mutations, re-derivable in the demo / fed-e2e / CI
  usage this monorepo targets. A later cleanup task can drop both stub tables
  once the lib stops force-scanning the v1 entities (would also let master drop
  the retained `OutboxAutoConfiguration`).

---

## 3. Zone (V3, domain-model § 2)

Zones nest under warehouses. Compound unique within parent warehouse —
two warehouses may carry the same `zone_code` independently.

```sql
CREATE TABLE zones (
    id              UUID         PRIMARY KEY,
    warehouse_id    UUID         NOT NULL,
    zone_code       VARCHAR(20)  NOT NULL,
    name            VARCHAR(100) NOT NULL,
    zone_type       VARCHAR(20)  NOT NULL,
    status          VARCHAR(20)  NOT NULL,
    version         BIGINT       NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ  NOT NULL,
    created_by      VARCHAR(100) NOT NULL,
    updated_at      TIMESTAMPTZ  NOT NULL,
    updated_by      VARCHAR(100) NOT NULL,
    CONSTRAINT fk_zones_warehouse
        FOREIGN KEY (warehouse_id) REFERENCES warehouses (id),
    CONSTRAINT uq_zones_warehouse_code
        UNIQUE (warehouse_id, zone_code),
    CONSTRAINT ck_zones_status
        CHECK (status IN ('ACTIVE', 'INACTIVE')),
    CONSTRAINT ck_zones_zone_type
        CHECK (zone_type IN ('AMBIENT', 'CHILLED', 'FROZEN', 'RETURNS', 'BULK', 'PICK'))
);

CREATE INDEX idx_zones_warehouse_status_updated_at
    ON zones (warehouse_id, status, updated_at DESC);
```

**Local FK to warehouses**: acceptable because both aggregates live in
the same `master_db`. Cross-aggregate referential checks at the
application layer (e.g., "cannot delete warehouse with active zones")
still fire — the SQL FK is a structural backstop.

**zone_type enum**: 6 values mirroring [`ZoneType.java`](../../../apps/master-service/src/main/java/com/wms/master/domain/model/ZoneType.java). Promoting a new
value requires a Flyway migration that adds the enum literal here +
domain enum + REST schema update (3-leg coordination).

---

## 4. Location (V4, domain-model § 3 — W3 anchor)

Locations nest under warehouse + zone. **`location_code` is GLOBALLY
unique** (W3) — not scoped to warehouse or zone. This is the
distinguishing W3 invariant for the master domain.

```sql
CREATE TABLE locations (
    id              UUID         PRIMARY KEY,
    warehouse_id    UUID         NOT NULL,
    zone_id         UUID         NOT NULL,
    location_code   VARCHAR(40)  NOT NULL,
    aisle           VARCHAR(10),
    rack            VARCHAR(10),
    level           VARCHAR(10),
    bin             VARCHAR(10),
    location_type   VARCHAR(20)  NOT NULL,
    capacity_units  INTEGER,
    status          VARCHAR(20)  NOT NULL,
    version         BIGINT       NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ  NOT NULL,
    created_by      VARCHAR(100) NOT NULL,
    updated_at      TIMESTAMPTZ  NOT NULL,
    updated_by      VARCHAR(100) NOT NULL,
    CONSTRAINT fk_locations_warehouse
        FOREIGN KEY (warehouse_id) REFERENCES warehouses (id),
    CONSTRAINT fk_locations_zone
        FOREIGN KEY (zone_id) REFERENCES zones (id),
    CONSTRAINT uq_locations_location_code
        UNIQUE (location_code),
    CONSTRAINT ck_locations_status
        CHECK (status IN ('ACTIVE', 'INACTIVE')),
    CONSTRAINT ck_locations_location_type
        CHECK (location_type IN (
            'STORAGE', 'STAGING_INBOUND', 'STAGING_OUTBOUND', 'DAMAGED', 'QUARANTINE')),
    CONSTRAINT ck_locations_capacity_units_positive
        CHECK (capacity_units IS NULL OR capacity_units >= 1)
);

CREATE INDEX idx_locations_warehouse_status
    ON locations (warehouse_id, status);
CREATE INDEX idx_locations_zone_status
    ON locations (zone_id, status);
```

**W3 — globally unique location_code**: the load-bearing invariant of the
master domain. Two warehouses cannot reuse the same `location_code` —
this is the property that lets sibling services (inventory / inbound /
outbound) refer to a location by code alone without disambiguating
warehouse context.

**Denormalized `warehouse_id`**: stored on Location for fast warehouse-
scoped queries (avoids JOIN through zones). The
parent-zone-matches-warehouse consistency is enforced at the application
layer; if a mismatched request lands, it surfaces as `ZONE_NOT_FOUND`
(leak-safe — does not reveal the conflicting warehouse id).

**Dual index** (`warehouse_id+status` and `zone_id+status`): list queries
filter by either parent scope; both access patterns are supported with
their own partial-equality index.

---

## 5. SKU (V5, domain-model § 4)

SKUs are independent aggregates (no parent ref). Independently mutable.

```sql
CREATE TABLE skus (
    id              UUID         PRIMARY KEY,
    sku_code        VARCHAR(40)  NOT NULL,
    name            VARCHAR(200) NOT NULL,
    description     VARCHAR(1000),
    barcode         VARCHAR(40),
    base_uom        VARCHAR(10)  NOT NULL,
    tracking_type   VARCHAR(10)  NOT NULL,
    weight_grams    INTEGER,
    volume_ml       INTEGER,
    hazard_class    VARCHAR(20),
    shelf_life_days INTEGER,
    status          VARCHAR(20)  NOT NULL,
    version         BIGINT       NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ  NOT NULL,
    created_by      VARCHAR(100) NOT NULL,
    updated_at      TIMESTAMPTZ  NOT NULL,
    updated_by      VARCHAR(100) NOT NULL,
    CONSTRAINT uq_skus_sku_code UNIQUE (sku_code),
    CONSTRAINT ck_skus_sku_code_uppercase CHECK (sku_code = UPPER(sku_code)),
    CONSTRAINT ck_skus_status CHECK (status IN ('ACTIVE', 'INACTIVE')),
    CONSTRAINT ck_skus_base_uom CHECK (base_uom IN ('EA', 'BOX', 'PLT', 'KG', 'L')),
    CONSTRAINT ck_skus_tracking_type CHECK (tracking_type IN ('NONE', 'LOT')),
    CONSTRAINT ck_skus_weight_grams_nonneg CHECK (weight_grams IS NULL OR weight_grams >= 0),
    CONSTRAINT ck_skus_volume_ml_nonneg CHECK (volume_ml IS NULL OR volume_ml >= 0),
    CONSTRAINT ck_skus_shelf_life_days_nonneg CHECK (shelf_life_days IS NULL OR shelf_life_days >= 0)
);

CREATE UNIQUE INDEX uq_skus_barcode ON skus (barcode) WHERE barcode IS NOT NULL;

CREATE INDEX idx_skus_status_updated_at
    ON skus (status, updated_at DESC);
```

**`ck_skus_sku_code_uppercase`**: enforces case-insensitive SKU identity
at the DB layer. The REST contract
([`master-service-api.md`](../../contracts/http/master-service-api.md)
§ 4.1) lowercases on read and stores uppercase on write; the DB
constraint backstops any path that bypasses the controller (direct SQL,
out-of-band imports). Unusual SQL pattern — flagged as load-bearing
because lookup mismatches would silently corrupt inbound / outbound
service references.

**Partial unique barcode** (`WHERE barcode IS NOT NULL`): SKUs without a
scannable barcode coexist freely (NULL ≠ NULL), but two SKUs cannot share
the same non-null barcode. JPA `@UniqueConstraint` cannot express the
WHERE filter, so the constraint is DB-only — no entity-level annotation.

**`tracking_type`** binds to inventory: `LOT` SKUs require lot identity
(see § 6 Lot below); `NONE` SKUs skip the lot dimension entirely.

---

## 6. Lot (V6, domain-model § 6)

Lots belong to LOT-tracked SKUs. Parent SKU `tracking_type='LOT'`
invariant enforced in domain code (the SQL FK is structural only and does
not validate parent tracking_type).

```sql
CREATE TABLE lots (
    id                  UUID         PRIMARY KEY,
    sku_id              UUID         NOT NULL REFERENCES skus(id),
    lot_no              VARCHAR(40)  NOT NULL CHECK (char_length(trim(lot_no)) > 0),
    manufactured_date   DATE,
    expiry_date         DATE,
    supplier_partner_id UUID,
    status              VARCHAR(16)  NOT NULL CHECK (status IN ('ACTIVE', 'INACTIVE', 'EXPIRED')),
    version             BIGINT       NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ  NOT NULL,
    created_by          VARCHAR(255) NOT NULL,
    updated_at          TIMESTAMPTZ  NOT NULL,
    updated_by          VARCHAR(255) NOT NULL,
    CONSTRAINT uq_lots_sku_lotno UNIQUE (sku_id, lot_no),
    CONSTRAINT ck_lots_date_pair CHECK (
        manufactured_date IS NULL OR expiry_date IS NULL OR expiry_date >= manufactured_date
    )
);

CREATE INDEX idx_lots_sku_status ON lots (sku_id, status);

CREATE INDEX idx_lots_expiry_active ON lots (expiry_date) WHERE status = 'ACTIVE';
```

**Compound unique `(sku_id, lot_no)`**: per-parent-SKU uniqueness only —
two SKUs may carry the same `lot_no` (independent supplier batches).
Global lot uniqueness is not a master domain invariant.

**`supplier_partner_id` intentional no-FK**: the column references a
Partner aggregate (V7) but **carries no SQL FK**. Rationale (V6 inline
comment L15-18):

- Partner aggregate has its own lifecycle (independent INSERT order).
- v1 performs only soft validation in the domain layer — a Lot can be
  created referencing a Partner whose row hasn't yet replicated to a
  read-model.
- When the Partner aggregate ships hard validation (`BE-005` follow-up),
  a cleanup migration that promotes this to a FK is expected.

**`idx_lots_expiry_active`** (`WHERE status='ACTIVE'`): partial index for
the daily expiration batch — the scheduler scans `ACTIVE + expiry_date <
today` rows; the partial filter keeps the index size minimal as
historical EXPIRED / INACTIVE rows accumulate.

**`ck_lots_date_pair`**: `expiry_date >= manufactured_date` when both
are present. Either can be NULL (production date unknown / no shelf
life).

---

## 7. Partner (V7, domain-model § 5)

Partners are independent aggregates (no parent ref). v1 = SUPPLIER /
CUSTOMER / BOTH — type axis only, no hierarchy.

```sql
CREATE TABLE partners (
    id              UUID         PRIMARY KEY,
    partner_code    VARCHAR(20)  NOT NULL,
    name            VARCHAR(200) NOT NULL,
    partner_type    VARCHAR(10)  NOT NULL,
    business_number VARCHAR(20),
    contact_name    VARCHAR(100),
    contact_email   VARCHAR(200),
    contact_phone   VARCHAR(30),
    address         VARCHAR(300),
    status          VARCHAR(20)  NOT NULL,
    version         BIGINT       NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ  NOT NULL,
    created_by      VARCHAR(100) NOT NULL,
    updated_at      TIMESTAMPTZ  NOT NULL,
    updated_by      VARCHAR(100) NOT NULL,
    CONSTRAINT uq_partners_partner_code UNIQUE (partner_code),
    CONSTRAINT ck_partners_status CHECK (status IN ('ACTIVE', 'INACTIVE')),
    CONSTRAINT ck_partners_partner_type CHECK (partner_type IN ('SUPPLIER', 'CUSTOMER', 'BOTH'))
);

CREATE INDEX idx_partners_partner_type ON partners (partner_type);
CREATE INDEX idx_partners_status_updated_at
    ON partners (status, updated_at DESC);
```

**Internal B2B contact data**: `contact_email` / `contact_phone` are
operational B2B contact channels (PROJECT.md `data_sensitivity:
internal`) — not consumer PII. No additional privacy treatment
(encryption / masking) is applied at this layer.

**Dual index**: by `partner_type` (drill-down suppliers vs customers)
and by `(status, updated_at)` (default list query).

---

## 8. Indexing Strategy Summary

| Table | Index | Type | Purpose |
|---|---|---|---|
| `warehouses` | `warehouses_pkey` | PK | row lookup |
| `warehouses` | `uq_warehouses_warehouse_code` | unique | global warehouse_code |
| `warehouses` | `idx_warehouses_status_updated_at` | btree | list query default sort |
| `master_outbox` | `master_outbox_pkey` | PK (UUID `event_id`) | row lookup / mark-published |
| `master_outbox` | `idx_master_outbox_pending` | partial (`published_at IS NULL`) | v2 publisher FIFO drain |
| `outbox` | `outbox_pkey` | PK (BIGSERIAL) | (retained stub — unused since V8) |
| `outbox` | `idx_outbox_status_created_at` | btree | (retained stub — unused since V8) |
| `processed_events` | `processed_events_pkey` | PK (event_id) | (stub — never written) |
| `zones` | `zones_pkey` | PK | row lookup |
| `zones` | `uq_zones_warehouse_code` | unique compound | per-warehouse zone_code |
| `zones` | `idx_zones_warehouse_status_updated_at` | btree | scoped list query |
| `locations` | `locations_pkey` | PK | row lookup |
| `locations` | `uq_locations_location_code` | unique | **W3 — globally unique** |
| `locations` | `idx_locations_warehouse_status` | btree | warehouse-scoped query |
| `locations` | `idx_locations_zone_status` | btree | zone-scoped query |
| `skus` | `skus_pkey` | PK | row lookup |
| `skus` | `uq_skus_sku_code` | unique | global SKU code (uppercase) |
| `skus` | `uq_skus_barcode` | partial unique (`barcode NOT NULL`) | barcode uniqueness |
| `skus` | `idx_skus_status_updated_at` | btree | list query default sort |
| `lots` | `lots_pkey` | PK | row lookup |
| `lots` | `uq_lots_sku_lotno` | unique compound | per-SKU lot_no |
| `lots` | `idx_lots_sku_status` | btree | per-SKU list + SKU deactivate guard |
| `lots` | `idx_lots_expiry_active` | partial (`status='ACTIVE'`) | daily expiry batch |
| `partners` | `partners_pkey` | PK | row lookup |
| `partners` | `uq_partners_partner_code` | unique | global partner_code |
| `partners` | `idx_partners_partner_type` | btree | type drill-down |
| `partners` | `idx_partners_status_updated_at` | btree | list query default sort |

---

## 9. Migration History

| Version | File | Line | Scope |
|---|---|---|---|
| V1 | `V1__init_warehouse.sql` | 32 | warehouses |
| V2 | `V2__init_outbox.sql` | 28 | outbox + processed_events (libs/java-messaging) |
| V3 | `V3__init_zone.sql` | 43 | zones (FK warehouse, compound unique) |
| V4 | `V4__init_location.sql` | 53 | locations (FK warehouse+zone, W3 global unique) |
| V5 | `V5__init_sku.sql` | 52 | skus (UPPERCASE check, partial unique barcode) |
| V6 | `V6__init_lot.sql` | 49 | lots (FK sku, partial expiry index, no-FK supplier) |
| V7 | `V7__init_partner.sql` | 45 | partners (3-value type enum) |
| V8 | `V8__master_outbox_v2.sql` | — | `master_outbox` (outbox v2, AbstractOutboxPublisher) — TASK-BE-438 |

When `V9+` lands, this document must be updated in the same commit (per
the retrospective contract introduced by TASK-BE-157).

---

## References

- [`domain-model.md`](domain-model.md) — domain meaning of each table (canonical reference)
- [`architecture.md`](architecture.md) — § Persistence, § Open Items
- [`idempotency.md`](idempotency.md) — REST + outbox dedupe
- [`external-integrations.md`](external-integrations.md) — zero-state (BE-159)
- [`../../contracts/http/master-service-api.md`](../../contracts/http/master-service-api.md) — REST list query defaults
- [`../../contracts/events/master-events.md`](../../contracts/events/master-events.md) — 6 aggregate snapshot event schemas
- [`../inventory-service/database-design.md`](../inventory-service/database-design.md) — sibling reference (BE-157, primary template)
- [`../notification-service/database-design.md`](../notification-service/database-design.md) — sibling reference (BE-160)
- `../../../apps/master-service/src/main/resources/db/migration/V1__init_warehouse.sql`
- `../../../apps/master-service/src/main/resources/db/migration/V2__init_outbox.sql`
- `../../../apps/master-service/src/main/resources/db/migration/V3__init_zone.sql`
- `../../../apps/master-service/src/main/resources/db/migration/V4__init_location.sql`
- `../../../apps/master-service/src/main/resources/db/migration/V5__init_sku.sql`
- `../../../apps/master-service/src/main/resources/db/migration/V6__init_lot.sql`
- `../../../apps/master-service/src/main/resources/db/migration/V7__init_partner.sql`
- `../../../apps/master-service/src/main/resources/db/migration/V8__master_outbox_v2.sql`
- `../../../../../rules/domains/wms.md` — W1-W6 (especially W3 location uniqueness)
- `../../../../../rules/traits/transactional.md` — T1, T3, T8 (outbox + dedupe)
- `../../../../../platform/architecture.md` — system-level architecture
