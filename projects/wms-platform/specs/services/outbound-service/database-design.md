# outbound-service — Database Design

Physical schema reflection for `outbound_db`. Flyway migrations under
`apps/outbound-service/src/main/resources/db/migration/` are the canonical
source-of-truth; this document consolidates them into a single spec
artifact for review-time reasoning. When a new migration lands (`V15+`),
this file must be updated in the same commit (per the retrospective
contract introduced by TASK-BE-157).

**Target engine**: PostgreSQL 14+ (production). Partial indexes, JSONB
columns, `TIMESTAMPTZ` semantics, `gen_random_uuid()`, and PL/pgSQL
triggers are PostgreSQL native; portability to other engines is out of
scope for v1.

**Authoritative reference**: [`domain-model.md`](domain-model.md) for the
domain meaning of each table.

**Evolution character**: outbound-service has the **most schema-aligned
history** in the wms portfolio — 9 init migrations (V1-V8) followed by 6
schema_align / feature migrations (V9-V14). Each evolution preserves
zero-downtime compatibility by adding nullable columns + partial-unique
indexes for unique-when-backfilled identifiers + retaining legacy columns
until a later cleanup phase. The story below presents each table with
its init + align stages together, so the final shape is unambiguous.

---

## Schema Overview

```
   ┌─────────────────────────────────────────────────────┐
   │  Master Read Model (V1, consumer-fed)               │
   │  warehouse / zone / location / sku / lot / partner  │
   │  (partner 4-enum incl. CARRIER, same as inbound)    │
   └─────────────────────────────────────────────────────┘

   ┌─────────────────┐  1:N (FK)   ┌──────────────────────┐
   │ outbound_order  │ ─────────▶  │ outbound_order_line  │
   │ (V2 + V10 align)│             │ (V2)                 │
   └──────┬──────────┘             └──────────────────────┘
          │ saga_id (cross-aggregate ref)
          ▼
   ┌──────────────────────┐
   │  outbound_saga (V5)  │  ← UNIQUE order_id, status FSM
   │  + V14 re_emit_count │     + sweeper-candidates partial index
   └──────────────────────┘

   ┌──────────────────┐  1:1 (FK)   ┌──────────────────────────┐
   │ picking_request  │ ─────────▶  │ picking_request_line     │
   │ (V3 + V11 align) │             │ (V3)                     │
   │  UNIQUE order_id │             └──────────────────────────┘
   └──────┬───────────┘                       │
          │                                   ▼
          ▼                              ┌──────────────────────┐
   ┌──────────────────────┐              │ picking_confirmation │
   │ picking_confirmation │ ────────▶    │   _line (V3 + V11)   │
   │ (V3 + V11 align)     │              └──────────────────────┘
   └──────────────────────┘

   ┌─────────────────┐ 1:N         ┌──────────────────┐
   │ packing_unit    │ ──────────▶ │ packing_unit_line│
   │ (V4 + V11 align)│             │ (V4)             │
   │ carton_no partial unique      └──────────────────┘
   └─────────────────┘

   ┌─────────────────┐  1:1 (FK)
   │   shipment      │  ◀── UNIQUE order_id (one shipment per order, v1)
   │ (V4 + V11 + V12)│      shipment_no partial unique
   │ + tms_status FSM│      + V12 created_by (BE-040)
   └─────────────────┘

   ┌─────────────────────┐  ┌──────────────────────────┐
   │ outbound_outbox     │  │ outbound_event_dedupe    │  ← V8 triggers
   │ (V6 + V9 align)     │  │ (V6 + V9 outcome col)    │     append-only
   └─────────────────────┘  └──────────────────────────┘

   ┌────────────────────────┐  ┌──────────────────────────┐
   │ erp_order_webhook_inbox│  │ erp_order_webhook_dedupe │  ← V8 trigger
   │ (V7, status FSM)       │  │ (V7, append-only)        │
   └────────────────────────┘  └──────────────────────────┘

   ┌────────────────────────┐  ← V13 BE-049 (final shape)
   │  tms_request_dedupe    │     V4 early bootstrap superseded
   │  (request_id PK,       │
   │   response_snapshot)   │
   └────────────────────────┘
```

Total: **15 tables + 4 trigger functions** across 14 migrations
(V1=91, V2=29, V3=39, V4=40, V5=18, V6=25, V7=25, V8=87, V9=33, V10=43,
V11=93, V12=5, V13=34, V14=27 line — 589 total).

Larger than master (8 tables / 7 migrations) and smaller than inbound
(18 tables / 8 migrations) but with the highest *migration count* in
the portfolio — reflecting outbound's continuous evolution from
bootstrap to BE-049 (TMS dedupe) and BE-050 (saga sweeper).

---

## 1. Master Read Model (V1, domain-model § 11)

Local snapshot tables fed by `master.*` Kafka topics. Identical pattern
to [`../inbound-service/database-design.md`](../inbound-service/database-design.md)
§ 1 — including the **partner_snapshot 4-enum divergence**
(`SUPPLIER`, **`CARRIER`**, `CUSTOMER`, `BOTH`) vs master-service's
3-enum.

```sql
CREATE TABLE warehouse_snapshot (
    id              UUID         PRIMARY KEY,
    warehouse_code  VARCHAR(20)  NOT NULL,
    status          VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    cached_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    master_version  BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT ck_warehouse_snapshot_status
        CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

-- zone_snapshot / location_snapshot / sku_snapshot / lot_snapshot /
-- partner_snapshot follow the identical pattern. See V1 source for the
-- byte-identical column lists; the salient invariants are:
-- - zone_snapshot.zone_type VARCHAR(50)    (wider than master's 20 for v2 enum growth headroom)
-- - location_snapshot.location_type VARCHAR(50)
-- - sku_snapshot.tracking_type IN ('NONE', 'LOT')
-- - lot_snapshot.status IN ('ACTIVE','INACTIVE','EXPIRED')
-- - partner_snapshot.partner_type IN ('SUPPLIER','CARRIER','CUSTOMER','BOTH')

CREATE INDEX idx_warehouse_snapshot_code ON warehouse_snapshot (warehouse_code);
CREATE INDEX idx_zone_snapshot_warehouse ON zone_snapshot (warehouse_id);
CREATE INDEX idx_location_snapshot_warehouse ON location_snapshot (warehouse_id);
CREATE INDEX idx_sku_snapshot_code        ON sku_snapshot (sku_code);
CREATE INDEX idx_lot_snapshot_sku         ON lot_snapshot (sku_id);
CREATE INDEX idx_partner_snapshot_code    ON partner_snapshot (partner_code);
```

**Default values** (`status='ACTIVE'`, `cached_at=NOW()`, `master_version=0`):
outbound adopts the defaults pattern (inbound omits defaults). Either
pattern is valid — outbound chose defaults to tolerate ad-hoc test
inserts; inbound chose explicit columns to match the producer-side
master events more strictly.

---

## 2. Order Aggregate (V2 + V10, domain-model § 1)

Bootstrap V2 + final shape V10 schema align. The `outbound_order` table
is the most evolved in the portfolio.

### 2.1 V2 bootstrap

```sql
CREATE TABLE outbound_order (
    id                   UUID         PRIMARY KEY,
    erp_order_number     VARCHAR(100) NOT NULL UNIQUE,
    warehouse_id         UUID         NOT NULL,
    partner_id           UUID         NOT NULL,
    status               VARCHAR(30)  NOT NULL DEFAULT 'RECEIVED',
    requested_ship_date  DATE,
    saga_id              UUID,
    idempotency_key      VARCHAR(255),
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    version              BIGINT       NOT NULL DEFAULT 0
);

CREATE TABLE outbound_order_line (
    id              UUID  PRIMARY KEY,
    order_id        UUID  NOT NULL REFERENCES outbound_order(id),
    sku_id          UUID  NOT NULL,
    lot_id          UUID,
    requested_qty   INT   NOT NULL,
    line_number     INT   NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_outbound_order_status ON outbound_order(status);
CREATE INDEX idx_outbound_order_line_order_id ON outbound_order_line(order_id);
```

### 2.2 V10 schema align — final shape

The spec (`domain-model.md` § 1) renamed `erp_order_number` to `order_no`
and added `source`, `notes`, `customer_partner_id`, plus standard audit
columns (`created_by`/`updated_by`). V10 applies the delta without
dropping legacy columns (zero-downtime compatibility):

```sql
ALTER TABLE outbound_order
    ADD COLUMN IF NOT EXISTS order_no            VARCHAR(40),
    ADD COLUMN IF NOT EXISTS source              VARCHAR(20),
    ADD COLUMN IF NOT EXISTS customer_partner_id UUID,
    ADD COLUMN IF NOT EXISTS notes               VARCHAR(1000),
    ADD COLUMN IF NOT EXISTS created_by          VARCHAR(100),
    ADD COLUMN IF NOT EXISTS updated_by          VARCHAR(100);

UPDATE outbound_order SET order_no = erp_order_number WHERE order_no IS NULL;
UPDATE outbound_order SET customer_partner_id = partner_id WHERE customer_partner_id IS NULL;

CREATE UNIQUE INDEX IF NOT EXISTS idx_outbound_order_order_no_unique
    ON outbound_order(order_no);

CREATE INDEX IF NOT EXISTS idx_outbound_order_customer_partner_id
    ON outbound_order(customer_partner_id);

CREATE INDEX IF NOT EXISTS idx_outbound_order_source
    ON outbound_order(source);
```

**Zero-downtime pattern**: all `ADD COLUMN IF NOT EXISTS` are nullable
initially. The persistence adapter writes both legacy
(`erp_order_number`, `partner_id`) and new (`order_no`,
`customer_partner_id`) columns on insert so existing readers and the
new writer coexist. A future migration may drop the legacy columns once
all consumers have migrated.

**`order_no` partial unique**: PostgreSQL unique indexes exclude NULL
by default — the index applies once `order_no` is backfilled and rejects
duplicates thereafter without requiring a NOT NULL constraint mid-flight.

---

## 3. Picking Aggregate (V3 + V11, domain-model § 2 + § 3)

V3 bootstrap + V11 schema align (BE-038).

### 3.1 V3 bootstrap

```sql
CREATE TABLE picking_request (
    id            UUID  PRIMARY KEY,
    order_id      UUID  NOT NULL REFERENCES outbound_order(id),
    warehouse_id  UUID  NOT NULL,
    status        VARCHAR(30) NOT NULL DEFAULT 'REQUESTED',
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version       BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE picking_request_line (
    id                   UUID PRIMARY KEY,
    picking_request_id   UUID NOT NULL REFERENCES picking_request(id),
    order_line_id        UUID NOT NULL,
    sku_id               UUID NOT NULL,
    lot_id               UUID,
    location_id          UUID NOT NULL,
    requested_qty        INT  NOT NULL,
    picked_qty           INT  NOT NULL DEFAULT 0
);

CREATE TABLE picking_confirmation (
    id                   UUID PRIMARY KEY,
    picking_request_id   UUID NOT NULL REFERENCES picking_request(id),
    confirmed_by         VARCHAR(100),
    confirmed_at         TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE picking_confirmation_line (
    id                       UUID PRIMARY KEY,
    picking_confirmation_id  UUID NOT NULL REFERENCES picking_confirmation(id),
    picking_line_id          UUID NOT NULL,
    picked_qty               INT  NOT NULL
);

CREATE INDEX idx_picking_request_order_id ON picking_request(order_id);
CREATE INDEX idx_picking_request_status   ON picking_request(status);
```

### 3.2 V11 schema align (BE-038)

```sql
-- picking_request: saga cross-reference + 1:1 with Order
ALTER TABLE picking_request
    ADD COLUMN IF NOT EXISTS saga_id UUID;

CREATE INDEX IF NOT EXISTS idx_picking_request_saga_id
    ON picking_request(saga_id);

CREATE UNIQUE INDEX IF NOT EXISTS idx_picking_request_order_id_unique
    ON picking_request(order_id);

-- picking_confirmation: denormalised order_id + operator notes
ALTER TABLE picking_confirmation
    ADD COLUMN IF NOT EXISTS order_id UUID,
    ADD COLUMN IF NOT EXISTS notes    VARCHAR(500);

CREATE INDEX IF NOT EXISTS idx_picking_confirmation_order_id
    ON picking_confirmation(order_id);

-- picking_confirmation_line: spec field set per domain-model.md §3
ALTER TABLE picking_confirmation_line
    ADD COLUMN IF NOT EXISTS order_line_id        UUID,
    ADD COLUMN IF NOT EXISTS sku_id               UUID,
    ADD COLUMN IF NOT EXISTS lot_id               UUID,
    ADD COLUMN IF NOT EXISTS actual_location_id   UUID,
    ADD COLUMN IF NOT EXISTS qty_confirmed        INT;
```

**`saga_id` cross-reference**: PickingRequest carries the OutboundSaga
id so inventory reply events can correlate. Domain code reads
`saga_id` at the picking-confirmed step to advance the saga state.

**1:1 with Order** (`idx_picking_request_order_id_unique`): one
PickingRequest per Order in v1; if a re-issue is needed, the picking
request is regenerated with a new id rather than amended.

---

## 4. Packing + Shipping (V4 + V11 + V12, domain-model § 4 + § 5)

Three-stage evolution: V4 bootstrap → V11 schema align (BE-038) → V12
single-column add (BE-040).

### 4.1 V4 bootstrap

```sql
CREATE TABLE packing_unit (
    id              UUID PRIMARY KEY,
    order_id        UUID NOT NULL REFERENCES outbound_order(id),
    tracking_number VARCHAR(100),
    status          VARCHAR(30) NOT NULL DEFAULT 'PACKING',
    packed_at       TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE packing_unit_line (
    id              UUID PRIMARY KEY,
    packing_unit_id UUID NOT NULL REFERENCES packing_unit(id),
    order_line_id   UUID NOT NULL,
    sku_id          UUID NOT NULL,
    lot_id          UUID,
    packed_qty      INT  NOT NULL
);

CREATE TABLE shipment (
    id              UUID PRIMARY KEY,
    order_id        UUID NOT NULL REFERENCES outbound_order(id),
    carrier         VARCHAR(100),
    tracking_number VARCHAR(200),
    status          VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    shipped_at      TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_packing_unit_order_id ON packing_unit(order_id);
CREATE INDEX idx_shipment_order_id     ON shipment(order_id);
```

### 4.2 V11 schema align (BE-038)

```sql
-- packing_unit: physical attributes (BE-038)
ALTER TABLE packing_unit
    ADD COLUMN IF NOT EXISTS carton_no    VARCHAR(40),
    ADD COLUMN IF NOT EXISTS packing_type VARCHAR(20),
    ADD COLUMN IF NOT EXISTS weight_grams INT,
    ADD COLUMN IF NOT EXISTS length_mm    INT,
    ADD COLUMN IF NOT EXISTS width_mm     INT,
    ADD COLUMN IF NOT EXISTS height_mm    INT,
    ADD COLUMN IF NOT EXISTS notes        VARCHAR(500),
    ADD COLUMN IF NOT EXISTS version      BIGINT      NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS updated_at   TIMESTAMPTZ NOT NULL DEFAULT NOW();

-- carton_no unique WITHIN an order (not globally)
CREATE UNIQUE INDEX IF NOT EXISTS idx_packing_unit_carton_no
    ON packing_unit(order_id, carton_no)
    WHERE carton_no IS NOT NULL;

-- shipment: TMS integration fields + audit
ALTER TABLE shipment
    ADD COLUMN IF NOT EXISTS shipment_no      VARCHAR(40),
    ADD COLUMN IF NOT EXISTS tms_status       VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    ADD COLUMN IF NOT EXISTS tms_notified_at  TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS tms_request_id   UUID,
    ADD COLUMN IF NOT EXISTS version          BIGINT      NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW();

-- shipment_no globally unique
CREATE UNIQUE INDEX IF NOT EXISTS idx_shipment_shipment_no
    ON shipment(shipment_no)
    WHERE shipment_no IS NOT NULL;

-- One shipment per order (v1 invariant)
CREATE UNIQUE INDEX IF NOT EXISTS idx_shipment_order_id_unique
    ON shipment(order_id);
```

### 4.3 V12 (BE-040)

```sql
ALTER TABLE shipment
    ADD COLUMN IF NOT EXISTS created_by VARCHAR(100) NOT NULL DEFAULT 'system';
```

**`carton_no` partial unique (within order)**: two different orders may
carry `CARTON-001` independently; per-order namespace prevents
operator confusion when scanning across multiple orders. Partial
(`WHERE carton_no IS NOT NULL`) tolerates rows created before the
attribute is assigned.

**`shipment_no` partial unique (global)**: shipment_no is a customer-
facing identifier (appears on shipping labels) — globally unique.

**`tms_status` FSM**: starts `PENDING`, transitions to `NOTIFIED` /
`NOTIFY_FAILED` based on TMS adapter outcome (see
[`external-integrations.md § 2 TMS`](external-integrations.md) and
[`sagas/outbound-saga.md`](sagas/outbound-saga.md) § Shipped-Not-Notified).

---

## 5. OutboundSaga (V5 + V14, domain-model § 6, ADR-MONO-005 Category B)

Saga state aggregate orchestrating the full Order → Picking → Packing →
Shipping → TMS lifecycle.

### 5.1 V5 bootstrap

```sql
CREATE TABLE outbound_saga (
    id                   UUID PRIMARY KEY,
    order_id             UUID NOT NULL UNIQUE,
    status               VARCHAR(30) NOT NULL DEFAULT 'REQUESTED',
    reservation_id       UUID,
    picking_request_id   UUID,
    shipment_id          UUID,
    failure_reason       VARCHAR(500),
    created_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version              BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_outbound_saga_status   ON outbound_saga(status);
CREATE INDEX idx_outbound_saga_order_id ON outbound_saga(order_id);
```

**`order_id UNIQUE`**: one saga per Order. The saga row is the
orchestration anchor — every inventory reply / TMS callback advances
status through `REQUESTED → RESERVED → PICKING → PICKED → PACKING →
PACKED → SHIPPED → COMPLETED` (terminal), or branches to one of the
failure terminals (`RESERVE_FAILED`, `SHIPPED_NOT_NOTIFIED`,
`STUCK_RECOVERY_FAILED`).

### 5.2 V14 saga sweeper recovery (BE-050)

```sql
ALTER TABLE outbound_saga
    ADD COLUMN IF NOT EXISTS re_emit_count INT NOT NULL DEFAULT 0;

CREATE INDEX IF NOT EXISTS idx_outbound_saga_sweeper_candidates
    ON outbound_saga (status, updated_at)
    WHERE status IN ('REQUESTED', 'CANCELLATION_REQUESTED', 'SHIPPED');
```

**`re_emit_count`**: monotonically-increasing counter of saga sweeper
re-emissions. The sweeper finds stuck sagas (rows whose `status` has
not advanced past a transitional state for longer than the configured
window) and republishes the appropriate outbox event to nudge the
state forward. After `re_emit_count` reaches the cap (configured via
`outbound.saga.sweeper.max-attempts`), the saga transitions to
`STUCK_RECOVERY_FAILED` and an `outbound.alert.saga.recovery.exhausted`
event fires (admin-events.md A1).

**Partial sweeper-candidates index**: targets only the three
transitional states the sweeper scans. Terminal-state rows
(`COMPLETED`, `RESERVE_FAILED`, etc.) are excluded — they accumulate
indefinitely without polluting the sweeper's hot path.

---

## 6. Outbox + EventDedupe (V6 + V9, transactional T3 + T8)

Two-stage evolution: V6 bootstrap with publisher-implementation columns
(`status`, `retry_count`), then V9 schema_align adds the wms-specific
shape columns (`aggregate_type`, `event_version`, `partition_key`).

### 6.1 V6 bootstrap

```sql
CREATE TABLE outbound_outbox (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_id    UUID NOT NULL,
    event_type      VARCHAR(100) NOT NULL,
    payload         JSONB NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    published_at    TIMESTAMPTZ,
    retry_count     INT NOT NULL DEFAULT 0
);

CREATE TABLE outbound_event_dedupe (
    event_id      UUID PRIMARY KEY,
    event_type    VARCHAR(100) NOT NULL,
    processed_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_outbound_outbox_status
    ON outbound_outbox(status)
    WHERE status = 'PENDING';
```

### 6.2 V9 schema align (wms-specific shape completion + outcome enum)

```sql
ALTER TABLE outbound_outbox
    ADD COLUMN IF NOT EXISTS aggregate_type VARCHAR(40),
    ADD COLUMN IF NOT EXISTS event_version  VARCHAR(10),
    ADD COLUMN IF NOT EXISTS partition_key  VARCHAR(60);

ALTER TABLE outbound_event_dedupe
    ADD COLUMN IF NOT EXISTS outcome VARCHAR(30);
```

**Publisher-tracking columns** (`status`, `retry_count`): unique to
outbound's outbox shape — sibling wms outboxes (inventory / notification /
inbound) do not have them. outbound's publisher uses `status` to
distinguish `PENDING` / `PUBLISHED` / `FAILED` (separate from the
nullable `published_at` field) so an in-flight publish attempt can be
distinguished from a never-attempted row. `retry_count` is the
broker-publish retry counter (separate from V14's saga `re_emit_count`).

**wms-specific columns** (`aggregate_type`, `event_version`,
`partition_key`): match the schema used by inventory / notification /
inbound. V6 omitted them; V9 backfills the spec gap. Nullable for
existing rows — the publisher (BE-036) populates them on every new
insert.

**`outcome` enum** (`outbound_event_dedupe`): `APPLIED` /
`IGNORED_DUPLICATE` / `FAILED` per V9 inline comment. Aligns with the
sibling dedupe pattern.

**Pending-publisher index** (`WHERE status='PENDING'`): unique to
outbound — sibling outboxes use `WHERE published_at IS NULL`. Both
patterns work; the choice mirrors the V6/V9 evolution where `status`
arrived before `published_at` semantics were fully specified.

**Shape vs `master-service`**: this wms-specific shape (UUID PK + JSONB
payload + `partition_key`, plus outbound's `status` + `retry_count`
publisher columns) diverges from `master-service`'s legacy
`libs/java-messaging` shared schema (BIGSERIAL PK + TEXT payload +
single `status` enum) — see [`../master-service/database-design.md`](../master-service/database-design.md)
§ 2 for the full rationale. master's migration to the modern shape is
deferred per ADR-MONO-003 D2 cadence (≥ 2026-06-10), tracked as
TASK-MONO-049 § 6 follow-up #1.

---

## 7. ERP Order Webhook (V7)

```sql
CREATE TABLE erp_order_webhook_inbox (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id      VARCHAR(80) NOT NULL,
    source        VARCHAR(50) NOT NULL,
    payload       JSONB NOT NULL,
    status        VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    received_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    processed_at  TIMESTAMPTZ
);

CREATE TABLE erp_order_webhook_dedupe (
    event_id      VARCHAR(80) PRIMARY KEY,
    source        VARCHAR(50) NOT NULL,
    received_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_erp_order_webhook_inbox_status
    ON erp_order_webhook_inbox(status)
    WHERE status = 'PENDING';
```

Mirrors inbound's `erp_webhook_inbox` + `erp_webhook_dedupe` pattern
([`../inbound-service/database-design.md`](../inbound-service/database-design.md)
§ 6) for the order-side ERP webhook. Both tables share `VARCHAR(80)
event_id` width to accommodate ERP-prefixed identifiers.

`erp_order_webhook_dedupe` is append-only (V8 trigger). `erp_order_webhook_inbox`
remains mutable (status FSM `PENDING → APPLIED | FAILED`).

---

## 8. TMS Request Dedupe (V4 → V13, integration-heavy I4)

This table has two shape stages — V4 bootstrap (early scaffolding) and
V13 BE-049 (final production shape). V13's `CREATE TABLE IF NOT EXISTS`
is a no-op when V4 already created the table; the final ground-truth
columns are described in V13.

### 8.1 V4 bootstrap (early)

```sql
CREATE TABLE tms_request_dedupe (
    shipment_id      UUID PRIMARY KEY,
    idempotency_key  VARCHAR(255) NOT NULL,
    tms_status       VARCHAR(30)  NOT NULL DEFAULT 'PENDING',
    requested_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
```

### 8.2 V13 BE-049 final shape

```sql
CREATE TABLE IF NOT EXISTS tms_request_dedupe (
    request_id        UUID                     PRIMARY KEY,
    sent_at           TIMESTAMP WITH TIME ZONE NOT NULL,
    response_snapshot JSONB                    NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_tms_request_dedupe_sent_at
    ON tms_request_dedupe (sent_at);
```

**Final production shape** (V13): 3-column shape declared in
[`external-integrations.md § 2.7`](external-integrations.md). Each row
is keyed by `request_id` = `Shipment.id` (UUIDv7), so insert order is
monotonically increasing in time. `response_snapshot JSONB` caches the
vendor `TmsAcknowledgement` (success flag + vendor request id + tracking
no + carrier code + status).

**Vendor idempotency fallback (I4)**: if the TMS vendor honours the
`Idempotency-Key` header (= `request_id`), this table caches the
response so a re-attempt skips the network call. If the vendor regresses
and stops honouring the header, this table becomes the local ground-
truth and prevents duplicate side effects on the WMS side.

**Insert sequencing rule**: the row is inserted in a separate
`REQUIRES_NEW` transaction, **after** the saga TX commits and **after**
the TMS network call returns 2xx. A failed TMS call must NOT insert here
— that would cement a failure as "already sent".

---

## 9. Append-Only Enforcement Strategy (V8, W2)

Four trigger functions enforce the W2 invariant. Same two-layer defense
as inventory V5 / inbound V7 — extended for outbound's four protected
tables.

```sql
-- outbound_event_dedupe: UPDATE + DELETE block
CREATE OR REPLACE FUNCTION outbound_event_dedupe_reject_modification()
    RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION
        'outbound_event_dedupe is append-only (W2): % rejected', TG_OP
        USING ERRCODE = '23514';
END; $$;

CREATE TRIGGER trg_outbound_event_dedupe_no_update
    BEFORE UPDATE ON outbound_event_dedupe
    FOR EACH ROW EXECUTE FUNCTION outbound_event_dedupe_reject_modification();

CREATE TRIGGER trg_outbound_event_dedupe_no_delete
    BEFORE DELETE ON outbound_event_dedupe
    FOR EACH ROW EXECUTE FUNCTION outbound_event_dedupe_reject_modification();

-- erp_order_webhook_dedupe: same 2-trigger pattern (omitted for brevity; see V8 source)
-- tms_request_dedupe: same 2-trigger pattern (omitted for brevity; see V8 source)

-- outbound_outbox: DELETE-only block (publisher needs UPDATE for published_at)
CREATE OR REPLACE FUNCTION outbound_outbox_reject_delete()
    RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION
        'outbound_outbox is append-only (W2): DELETE rejected'
        USING ERRCODE = '23514';
END; $$;

CREATE TRIGGER trg_outbound_outbox_no_delete
    BEFORE DELETE ON outbound_outbox
    FOR EACH ROW EXECUTE FUNCTION outbound_outbox_reject_delete();
```

| Protected table | Operations blocked | Rationale |
|---|---|---|
| `outbound_event_dedupe` | UPDATE + DELETE | T8 contract — once processed, outcome is permanent |
| `erp_order_webhook_dedupe` | UPDATE + DELETE | Webhook replay protection |
| `tms_request_dedupe` | UPDATE + DELETE | Vendor acknowledgement is immutable — re-fetch from vendor if questioned |
| `outbound_outbox` | **DELETE only** (UPDATE permitted) | Publisher needs UPDATE for `published_at` stamp |

The trigger pattern is identical to inbound V7 (per
[`../inbound-service/database-design.md`](../inbound-service/database-design.md)
§ 7) and inventory V5
([`../inventory-service/database-design.md`](../inventory-service/database-design.md)
§ 8).

**REVOKE backstop** (V8 DO block):

```sql
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = current_user) THEN
        EXECUTE format('REVOKE UPDATE, DELETE ON outbound_event_dedupe FROM %I', current_user);
        EXECUTE format('REVOKE UPDATE, DELETE ON erp_order_webhook_dedupe FROM %I', current_user);
        EXECUTE format('REVOKE UPDATE, DELETE ON tms_request_dedupe FROM %I', current_user);
        EXECUTE format('REVOKE DELETE ON outbound_outbox FROM %I', current_user);
    END IF;
EXCEPTION WHEN OTHERS THEN
    NULL;
END $$;
```

Note: V8 runs BEFORE V13 (which redefines `tms_request_dedupe`). The
trigger and REVOKE attach to the V4 shape; once V13 lands, the new
columns are still subject to the same triggers — PostgreSQL triggers
follow the table, not specific columns, so the protection persists
across the V13 schema change.

---

## 10. Indexing Strategy Summary

| Table | Index | Type | Purpose |
|---|---|---|---|
| `warehouse_snapshot` | `warehouse_snapshot_pkey` | PK | upsert target |
| `warehouse_snapshot` | `idx_warehouse_snapshot_code` | btree | natural-key lookup |
| `zone_snapshot` | `zone_snapshot_pkey` | PK | upsert target |
| `zone_snapshot` | `idx_zone_snapshot_warehouse` | btree | warehouse-scoped |
| `location_snapshot` | `location_snapshot_pkey` | PK | upsert target |
| `location_snapshot` | `idx_location_snapshot_warehouse` | btree | warehouse-scoped |
| `sku_snapshot` | `sku_snapshot_pkey` | PK | upsert target |
| `sku_snapshot` | `idx_sku_snapshot_code` | btree | natural-key lookup |
| `lot_snapshot` | `lot_snapshot_pkey` | PK | upsert target |
| `lot_snapshot` | `idx_lot_snapshot_sku` | btree | per-SKU enumeration |
| `partner_snapshot` | `partner_snapshot_pkey` | PK | upsert target |
| `partner_snapshot` | `idx_partner_snapshot_code` | btree | natural-key lookup |
| `outbound_order` | `outbound_order_pkey` | PK | row lookup |
| `outbound_order` | `outbound_order_erp_order_number_key` | unique (V2 legacy) | bootstrap business key |
| `outbound_order` | `idx_outbound_order_order_no_unique` | partial unique (`order_no NOT NULL`) | post-V10 business key |
| `outbound_order` | `idx_outbound_order_status` | btree | status filter |
| `outbound_order` | `idx_outbound_order_customer_partner_id` | btree | customer drill-down |
| `outbound_order` | `idx_outbound_order_source` | btree | source filter |
| `outbound_order_line` | `outbound_order_line_pkey` | PK | row lookup |
| `outbound_order_line` | `idx_outbound_order_line_order_id` | btree | FK lookup |
| `picking_request` | `picking_request_pkey` | PK | row lookup |
| `picking_request` | `idx_picking_request_order_id_unique` | partial unique | 1:1 with Order |
| `picking_request` | `idx_picking_request_order_id` | btree | FK lookup (V3 legacy) |
| `picking_request` | `idx_picking_request_status` | btree | status filter |
| `picking_request` | `idx_picking_request_saga_id` | btree | saga correlation |
| `picking_confirmation` | `picking_confirmation_pkey` | PK | row lookup |
| `picking_confirmation` | `idx_picking_confirmation_order_id` | btree | denormalised FK |
| `packing_unit` | `packing_unit_pkey` | PK | row lookup |
| `packing_unit` | `idx_packing_unit_order_id` | btree | FK lookup |
| `packing_unit` | `idx_packing_unit_carton_no` | partial unique compound | per-order carton uniqueness |
| `shipment` | `shipment_pkey` | PK | row lookup |
| `shipment` | `idx_shipment_order_id` | btree | FK lookup (V4 legacy) |
| `shipment` | `idx_shipment_order_id_unique` | partial unique | 1:1 with Order (v1) |
| `shipment` | `idx_shipment_shipment_no` | partial unique | global shipment_no |
| `outbound_saga` | `outbound_saga_pkey` | PK | row lookup |
| `outbound_saga` | (`order_id` UNIQUE) | unique | 1:1 with Order |
| `outbound_saga` | `idx_outbound_saga_order_id` | btree | reverse lookup |
| `outbound_saga` | `idx_outbound_saga_status` | btree | status filter |
| `outbound_saga` | `idx_outbound_saga_sweeper_candidates` | partial (`status IN (3 transitional)`) | sweeper hot path |
| `outbound_outbox` | `outbound_outbox_pkey` | PK | row lookup |
| `outbound_outbox` | `idx_outbound_outbox_status` | partial (`status='PENDING'`) | publisher FIFO scan |
| `outbound_event_dedupe` | `outbound_event_dedupe_pkey` | PK | dedupe by event_id |
| `erp_order_webhook_inbox` | `erp_order_webhook_inbox_pkey` | PK | row lookup |
| `erp_order_webhook_inbox` | `idx_erp_order_webhook_inbox_status` | partial (`status='PENDING'`) | processor hot path |
| `erp_order_webhook_dedupe` | `erp_order_webhook_dedupe_pkey` | PK (event_id) | replay protection |
| `tms_request_dedupe` | `tms_request_dedupe_pkey` | PK | vendor idempotency cache |
| `tms_request_dedupe` | `idx_tms_request_dedupe_sent_at` | btree | retention sweeper |

**5 partial indexes** (highlighted): outbox pending / saga sweeper /
order_no partial unique / shipment_no partial unique / carton_no
per-order partial unique / picking_request 1:1 / shipment 1:1. Most
unique constraints on backfillable columns are partial-unique to
preserve zero-downtime migration semantics.

---

## 11. Migration History

| Version | File | Line | Class | Scope |
|---|---|---|---|---|
| V1 | `V1__init_master_readmodel.sql` | 91 | init | 6 master snapshot tables (partner_snapshot 4-enum) |
| V2 | `V2__init_order_tables.sql` | 29 | init | outbound_order + outbound_order_line bootstrap |
| V3 | `V3__init_picking_tables.sql` | 39 | init | picking_request + line + confirmation + confirmation_line |
| V4 | `V4__init_packing_shipping_tables.sql` | 40 | init | packing_unit + line + shipment + tms_request_dedupe bootstrap |
| V5 | `V5__init_saga_table.sql` | 18 | init | outbound_saga |
| V6 | `V6__init_outbox_dedupe.sql` | 25 | init | outbound_outbox + outbound_event_dedupe |
| V7 | `V7__init_webhook_inbox.sql` | 25 | init | erp_order_webhook_inbox + dedupe |
| V8 | `V8__role_grants.sql` | 87 | init | 4 trigger function (W2 append-only) + REVOKE |
| V9 | `V9__outbox_dedupe_schema_align.sql` | 33 | align | outbox aggregate_type/event_version/partition_key + dedupe outcome enum |
| V10 | `V10__order_schema_align.sql` | 43 | align | outbound_order order_no + source + customer_partner_id + audit (BE-037) |
| V11 | `V11__picking_pack_ship_schema_align.sql` | 93 | align | picking saga_id + UNIQUE + confirmation align / packing carton_no + dimensions + UNIQUE / shipment shipment_no + tms_status + UNIQUE (BE-038) |
| V12 | `V12__shipment_created_by.sql` | 5 | align | shipment.created_by (BE-040) |
| V13 | `V13__tms_request_dedupe.sql` | 34 | feature | tms_request_dedupe final shape (BE-049 vendor idempotency fallback) |
| V14 | `V14__saga_re_emit_count.sql` | 27 | feature | outbound_saga.re_emit_count + sweeper-candidates partial index (BE-050) |

**9 init + 4 align + 2 feature** — the largest schema evolution count
in the wms portfolio. When `V15+` lands, this document must be updated
in the same commit (per the retrospective contract introduced by
TASK-BE-157).

---

## References

- [`domain-model.md`](domain-model.md) — domain meaning of each table
- [`architecture.md`](architecture.md) — § Persistence, § Saga Persistence, § Open Items
- [`idempotency.md`](idempotency.md) — REST + outbox + webhook + tms dedupe strategies
- [`external-integrations.md`](external-integrations.md) — TMS marquee (I4 idempotency reference for § 8)
- [`sagas/outbound-saga.md`](sagas/outbound-saga.md) — saga state machine + sweeper recovery
- [`state-machines/saga-status.md`](state-machines/saga-status.md) — saga state transitions
- [`../inventory-service/database-design.md`](../inventory-service/database-design.md) — sibling primary template (BE-157)
- [`../notification-service/database-design.md`](../notification-service/database-design.md) — sibling (BE-160)
- [`../master-service/database-design.md`](../master-service/database-design.md) — sibling (BE-161)
- [`../inbound-service/database-design.md`](../inbound-service/database-design.md) — sibling (BE-162, master-readmodel pattern reference)
- `../../../apps/outbound-service/src/main/resources/db/migration/V1__init_master_readmodel.sql`
- `../../../apps/outbound-service/src/main/resources/db/migration/V14__saga_re_emit_count.sql`
- `../../../../../rules/domains/wms.md` — W2 append-only invariant
- `../../../../../rules/traits/transactional.md` — T3 (outbox), T4 (state machine), T8 (event dedupe)
- `../../../../../rules/traits/integration-heavy.md` — I4 (vendor idempotency)
- `../../../../../docs/adr/ADR-MONO-005-saga-timeout-escalation-dead-letter-policy.md` — Category B saga policy
- `../../../../../platform/architecture.md` — system-level architecture
