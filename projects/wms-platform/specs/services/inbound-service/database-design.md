# inbound-service — Database Design

Physical schema reflection for `inbound_db`. Flyway migrations under
`apps/inbound-service/src/main/resources/db/migration/` are the canonical
source-of-truth; this document consolidates them into a single spec
artifact for review-time reasoning. When a new migration lands (`V9+`),
this file must be updated in the same commit (per the retrospective
contract introduced by TASK-BE-157).

**Target engine**: PostgreSQL 14+ (production). Partial indexes, JSONB
columns, `TIMESTAMPTZ` semantics, and PL/pgSQL triggers are PostgreSQL
native; portability to other engines is out of scope for v1.

**Authoritative reference**: [`domain-model.md`](domain-model.md) for the
domain meaning of each table.

---

## Schema Overview

```
   ┌─────────────────────────────────────────────────────┐
   │  Master Read Model (V1, consumer-fed, never written │
   │  from REST or use-case paths)                       │
   │  warehouse_snapshot / zone_snapshot /               │
   │  location_snapshot / sku_snapshot /                 │
   │  lot_snapshot / partner_snapshot                    │
   └─────────────────────────────────────────────────────┘

   ┌──────────┐ 1:1 ┌────────────┐ 1:1 ┌────────────────────┐
   │   asn    │ ◀── │ inspection │ ──▶ │ inspection_line    │
   │  (V2)    │     │   (V3)     │     │ + discrepancy      │
   └────┬─────┘     └────────────┘     └────────────────────┘
        │ 1:N (cascade)
        ▼
   ┌──────────┐ 1:1 ┌──────────────────────┐
   │ asn_line │ ◀── │ putaway_instruction  │
   └──────────┘     │ + line + confirmation│  ← append-only (V7 trigger)
                    │  (V4)                │
                    └──────────────────────┘

   ┌────────────────────┐  ┌────────────────────┐
   │ inbound_outbox     │  │ inbound_event_     │  ← V7 triggers
   │ (V5, T3)           │  │ dedupe (V5, T8)    │     append-only
   └────────────────────┘  └────────────────────┘

   ┌────────────────────┐  ┌────────────────────┐
   │ erp_webhook_inbox  │  │ erp_webhook_dedupe │  ← V7 trigger
   │ (V6, FSM)          │  │ (V6, append-only)  │     append-only
   └────────────────────┘  └────────────────────┘

   ┌────────────────────┐
   │ asn_no_sequence    │  (V8, per-day atomic UPSERT)
   └────────────────────┘
```

Total: **18 tables + 4 trigger functions** across 8 migrations
(V1=124, V2=52, V3=62, V4=64, V5=51, V6=44, V7=152, V8=13 line). This is
the largest schema in the wms portfolio so far — driven by the
multi-aggregate domain (ASN + Inspection + Putaway) plus the 6 master
read-model snapshots plus the ERP webhook ingest buffer.

---

## 1. Master Read Model (V1, domain-model § 9)

Local snapshot tables fed by `master.*` Kafka topics. Inbound's REST and
use-case paths only READ these tables — only the master-snapshot
consumers write. Out-of-order delivery is handled at upsert time by
comparing `master_version`.

```sql
CREATE TABLE warehouse_snapshot (
    id              UUID         PRIMARY KEY,
    warehouse_code  VARCHAR(20)  NOT NULL,
    status          VARCHAR(20)  NOT NULL,
    cached_at       TIMESTAMPTZ  NOT NULL,
    master_version  BIGINT       NOT NULL,
    CONSTRAINT ck_warehouse_snapshot_status
        CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

CREATE TABLE zone_snapshot (
    id              UUID         PRIMARY KEY,
    warehouse_id    UUID         NOT NULL,
    zone_code       VARCHAR(20)  NOT NULL,
    zone_type       VARCHAR(20)  NOT NULL,
    status          VARCHAR(20)  NOT NULL,
    cached_at       TIMESTAMPTZ  NOT NULL,
    master_version  BIGINT       NOT NULL,
    CONSTRAINT ck_zone_snapshot_status
        CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

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

CREATE TABLE sku_snapshot (
    id              UUID         PRIMARY KEY,
    sku_code        VARCHAR(40)  NOT NULL,
    tracking_type   VARCHAR(10)  NOT NULL,
    status          VARCHAR(20)  NOT NULL,
    cached_at       TIMESTAMPTZ  NOT NULL,
    master_version  BIGINT       NOT NULL,
    CONSTRAINT ck_sku_snapshot_tracking_type
        CHECK (tracking_type IN ('NONE', 'LOT')),
    CONSTRAINT ck_sku_snapshot_status
        CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

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

CREATE TABLE partner_snapshot (
    id              UUID         PRIMARY KEY,
    partner_code    VARCHAR(40)  NOT NULL,
    partner_type    VARCHAR(20)  NOT NULL,
    status          VARCHAR(20)  NOT NULL,
    cached_at       TIMESTAMPTZ  NOT NULL,
    master_version  BIGINT       NOT NULL,
    CONSTRAINT ck_partner_snapshot_partner_type
        CHECK (partner_type IN ('SUPPLIER', 'CARRIER', 'CUSTOMER', 'BOTH')),
    CONSTRAINT ck_partner_snapshot_status
        CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

CREATE INDEX idx_warehouse_snapshot_code ON warehouse_snapshot (warehouse_code);
CREATE INDEX idx_zone_snapshot_warehouse ON zone_snapshot (warehouse_id);
CREATE INDEX idx_zone_snapshot_code      ON zone_snapshot (zone_code);
CREATE INDEX idx_location_snapshot_warehouse ON location_snapshot (warehouse_id);
CREATE INDEX idx_location_snapshot_code      ON location_snapshot (location_code);
CREATE INDEX idx_sku_snapshot_code           ON sku_snapshot (sku_code);
CREATE INDEX idx_lot_snapshot_sku            ON lot_snapshot (sku_id);
CREATE INDEX idx_partner_snapshot_code       ON partner_snapshot (partner_code);
```

**No FK to a remote master schema** — these are local replicas keyed by
the same UUID as the master service uses. The consumer chooses the
upsert target by primary key; out-of-order delivery is rejected when the
incoming `master_version` is ≤ the stored one.

**`partner_snapshot` 4-enum divergence**: inbound recognises **4**
partner types (`SUPPLIER`, **`CARRIER`**, `CUSTOMER`, `BOTH`), whereas
the master-service `partners` table (`master-service/database-design.md`
§ 7) uses **3** types (`SUPPLIER`, `CUSTOMER`, `BOTH`). The `CARRIER`
addition in the snapshot reflects inbound's operational need to classify
transport vendors directly — when the master publishes a partner event
without `CARRIER` (current v1 source), the consumer code is responsible
for ignoring or projecting partner type appropriately. v1 source-of-truth
is master's 3-enum; inbound's 4-enum is read-model accommodation for v2
master expansion.

**No `idempotency_key` / `created_by` audit columns**: read-model rows
are consumer-fed, not authored by operators; the source `eventId` lives
in `inbound_event_dedupe` (§ 5) and links via the consumer's per-event
log line.

---

## 2. ASN Aggregate (V2, domain-model § 1)

ASN root + `asn_line` children. State machine
`CREATED → INSPECTING → INSPECTED → IN_PUTAWAY → PUTAWAY_DONE → CLOSED`
plus `CANCELLED` as a parallel terminal — see
[`state-machines/asn-status.md`](state-machines/asn-status.md) for the
full transition rules.

```sql
CREATE TABLE asn (
    id                    UUID         PRIMARY KEY,
    asn_no                VARCHAR(40)  NOT NULL,
    source                VARCHAR(20)  NOT NULL,
    supplier_partner_id   UUID         NOT NULL,
    warehouse_id          UUID         NOT NULL,
    expected_arrive_date  DATE,
    notes                 VARCHAR(1000),
    status                VARCHAR(20)  NOT NULL,
    version               BIGINT       NOT NULL DEFAULT 0,
    created_at            TIMESTAMPTZ  NOT NULL,
    created_by            VARCHAR(100) NOT NULL,
    updated_at            TIMESTAMPTZ  NOT NULL,
    updated_by            VARCHAR(100) NOT NULL,
    CONSTRAINT uq_asn_no UNIQUE (asn_no),
    CONSTRAINT ck_asn_source
        CHECK (source IN ('MANUAL', 'WEBHOOK_ERP')),
    CONSTRAINT ck_asn_status
        CHECK (status IN (
            'CREATED', 'INSPECTING', 'INSPECTED',
            'IN_PUTAWAY', 'PUTAWAY_DONE', 'CLOSED', 'CANCELLED'
        ))
);

CREATE INDEX idx_asn_warehouse ON asn (warehouse_id);
CREATE INDEX idx_asn_supplier  ON asn (supplier_partner_id);
CREATE INDEX idx_asn_status    ON asn (status);

CREATE TABLE asn_line (
    id            UUID         PRIMARY KEY,
    asn_id        UUID         NOT NULL REFERENCES asn (id) ON DELETE CASCADE,
    line_no       INTEGER      NOT NULL,
    sku_id        UUID         NOT NULL,
    lot_id        UUID,
    expected_qty  INTEGER      NOT NULL,
    CONSTRAINT uq_asn_line_no UNIQUE (asn_id, line_no),
    CONSTRAINT ck_asn_line_no_positive CHECK (line_no >= 1),
    CONSTRAINT ck_asn_line_qty_positive CHECK (expected_qty > 0)
);

CREATE INDEX idx_asn_line_asn ON asn_line (asn_id);
CREATE INDEX idx_asn_line_sku ON asn_line (sku_id);
```

**`source` enum**: distinguishes whether the ASN was created via REST
(`MANUAL`) or accepted from an ERP webhook (`WEBHOOK_ERP`). This drives
provenance display in the operator UI and audit reports.

**`asn_no` globally unique** — auto-generated by `nextAsnNo()` via
V8's `asn_no_sequence` (see § 8). Format `<YYYYMMDD>-<seq>`.

**`asn_line` FK CASCADE**: when an ASN is deleted (test fixtures / dev
seed cleanup only — production ASNs transition through state, never
hard-deleted), its lines are removed too. The `(asn_id, line_no)` compound
unique enforces stable line numbering per ASN.

State-machine SQL constraint (`ck_asn_status`) catches out-of-band
INSERTs; domain code enforces the per-transition rules.

---

## 3. Inspection Aggregate (V3, domain-model § 2)

1:1 with ASN — every ASN has at most one Inspection. Inspection has
many lines (one per ASN line) plus zero-or-more discrepancies.

```sql
CREATE TABLE inspection (
    id            UUID         PRIMARY KEY,
    asn_id        UUID         NOT NULL UNIQUE REFERENCES asn (id) ON DELETE CASCADE,
    inspector_id  VARCHAR(100) NOT NULL,
    completed_at  TIMESTAMPTZ,
    notes         VARCHAR(1000),
    version       BIGINT       NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ  NOT NULL,
    created_by    VARCHAR(100) NOT NULL,
    updated_at    TIMESTAMPTZ  NOT NULL,
    updated_by    VARCHAR(100) NOT NULL
);

CREATE INDEX idx_inspection_asn ON inspection (asn_id);

CREATE TABLE inspection_line (
    id              UUID         PRIMARY KEY,
    inspection_id   UUID         NOT NULL REFERENCES inspection (id) ON DELETE CASCADE,
    asn_line_id     UUID         NOT NULL REFERENCES asn_line (id),
    sku_id          UUID         NOT NULL,
    lot_id          UUID,
    lot_no          VARCHAR(40),
    qty_passed      INTEGER      NOT NULL DEFAULT 0,
    qty_damaged     INTEGER      NOT NULL DEFAULT 0,
    qty_short       INTEGER      NOT NULL DEFAULT 0,
    CONSTRAINT uq_inspection_line_asn_line UNIQUE (inspection_id, asn_line_id),
    CONSTRAINT ck_inspection_line_qty_nonneg
        CHECK (qty_passed >= 0 AND qty_damaged >= 0 AND qty_short >= 0)
);

CREATE INDEX idx_inspection_line_inspection ON inspection_line (inspection_id);
CREATE INDEX idx_inspection_line_asn_line   ON inspection_line (asn_line_id);

CREATE TABLE inspection_discrepancy (
    id                UUID         PRIMARY KEY,
    inspection_id     UUID         NOT NULL REFERENCES inspection (id) ON DELETE CASCADE,
    asn_line_id       UUID         NOT NULL REFERENCES asn_line (id),
    discrepancy_type  VARCHAR(40)  NOT NULL,
    expected_qty      INTEGER      NOT NULL,
    actual_total_qty  INTEGER      NOT NULL,
    variance          INTEGER      NOT NULL,
    acknowledged      BOOLEAN      NOT NULL DEFAULT FALSE,
    acknowledged_by   VARCHAR(100),
    acknowledged_at   TIMESTAMPTZ,
    notes             VARCHAR(500),
    CONSTRAINT ck_inspection_discrepancy_type
        CHECK (discrepancy_type IN ('QUANTITY_MISMATCH', 'LOT_MISMATCH', 'DAMAGE_EXCESS'))
);

CREATE INDEX idx_inspection_discrepancy_inspection
    ON inspection_discrepancy (inspection_id);

CREATE INDEX idx_inspection_discrepancy_unacked
    ON inspection_discrepancy (inspection_id)
    WHERE acknowledged = FALSE;
```

**`asn_id UNIQUE`** on `inspection`: enforces 1:1 at the DB layer.
Domain code does the same precondition check; the SQL constraint is the
absolute backstop against out-of-band creation.

**`qty_passed + qty_damaged + qty_short` 3-bucket**: per-line outcome
counts. Sum across all three buckets must equal `asn_line.expected_qty`
(or surface as a discrepancy row); this arithmetic invariant lives in
the domain layer — not enforced at SQL level because it's a
*cross-row* assertion that triggers would have to walk.

**`idx_inspection_discrepancy_unacked`** (`WHERE acknowledged=FALSE`):
partial index for the operator dashboard's "open discrepancies" query —
acknowledged rows accumulate and rarely re-queried, so excluding them
keeps the hot path index lean.

---

## 4. Putaway Aggregate (V4, domain-model § 3 + § 4)

Putaway instruction + lines + immutable confirmations. The
`putaway_confirmation` table is append-only — see § 7 V7 trigger.

```sql
CREATE TABLE putaway_instruction (
    id            UUID         PRIMARY KEY,
    asn_id        UUID         NOT NULL UNIQUE REFERENCES asn (id) ON DELETE CASCADE,
    warehouse_id  UUID         NOT NULL,
    planned_by    VARCHAR(100) NOT NULL,
    status        VARCHAR(40)  NOT NULL,
    version       BIGINT       NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ  NOT NULL,
    created_by    VARCHAR(100) NOT NULL,
    updated_at    TIMESTAMPTZ  NOT NULL,
    updated_by    VARCHAR(100) NOT NULL,
    CONSTRAINT ck_putaway_instruction_status
        CHECK (status IN ('PENDING', 'IN_PROGRESS', 'COMPLETED', 'PARTIALLY_COMPLETED'))
);

CREATE INDEX idx_putaway_instruction_asn       ON putaway_instruction (asn_id);
CREATE INDEX idx_putaway_instruction_warehouse ON putaway_instruction (warehouse_id);

CREATE TABLE putaway_line (
    id                       UUID         PRIMARY KEY,
    putaway_instruction_id   UUID         NOT NULL REFERENCES putaway_instruction (id) ON DELETE CASCADE,
    asn_line_id              UUID         NOT NULL REFERENCES asn_line (id),
    sku_id                   UUID         NOT NULL,
    lot_id                   UUID,
    lot_no                   VARCHAR(40),
    destination_location_id  UUID         NOT NULL,
    qty_to_putaway           INTEGER      NOT NULL,
    status                   VARCHAR(20)  NOT NULL,
    CONSTRAINT ck_putaway_line_qty_positive CHECK (qty_to_putaway > 0),
    CONSTRAINT ck_putaway_line_status
        CHECK (status IN ('PENDING', 'CONFIRMED', 'SKIPPED'))
);

CREATE INDEX idx_putaway_line_instruction ON putaway_line (putaway_instruction_id);
CREATE INDEX idx_putaway_line_asn_line    ON putaway_line (asn_line_id);
CREATE INDEX idx_putaway_line_destination ON putaway_line (destination_location_id);

CREATE TABLE putaway_confirmation (
    id                       UUID         PRIMARY KEY,
    putaway_instruction_id   UUID         NOT NULL REFERENCES putaway_instruction (id),
    putaway_line_id          UUID         NOT NULL UNIQUE REFERENCES putaway_line (id),
    sku_id                   UUID         NOT NULL,
    lot_id                   UUID,
    planned_location_id      UUID         NOT NULL,
    actual_location_id       UUID         NOT NULL,
    qty_confirmed            INTEGER      NOT NULL,
    confirmed_by             VARCHAR(100) NOT NULL,
    confirmed_at             TIMESTAMPTZ  NOT NULL,
    CONSTRAINT ck_putaway_confirmation_qty_positive CHECK (qty_confirmed > 0)
);

CREATE INDEX idx_putaway_confirmation_instruction ON putaway_confirmation (putaway_instruction_id);
CREATE INDEX idx_putaway_confirmation_line        ON putaway_confirmation (putaway_line_id);
CREATE INDEX idx_putaway_confirmation_actual_loc  ON putaway_confirmation (actual_location_id);
```

**`putaway_line_id UNIQUE`** on `putaway_confirmation`: enforces 1:1
between line and its confirmation. A line can only be confirmed once;
the SQL UNIQUE blocks even a corrupted INSERT path.

**`planned_location_id` vs `actual_location_id`**: confirmation captures
*both* — the planner's intent (`planned`) and the operator's physical
outcome (`actual`). Mismatch is recorded but not blocked at SQL level
(operators may legitimately deposit to a different location); the
divergence is auditable via `(planned_location_id != actual_location_id)`
queries.

**Append-only** (V7 trigger): the table accepts INSERT only — operators
never edit or delete a confirmation. See § 7 Append-Only Enforcement
Strategy for the trigger pattern.

---

## 5. Outbox + EventDedupe (V5, transactional T3 + T8)

```sql
CREATE TABLE inbound_outbox (
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

CREATE INDEX idx_inbound_outbox_pending
    ON inbound_outbox (created_at)
    WHERE published_at IS NULL;

CREATE INDEX idx_inbound_outbox_aggregate
    ON inbound_outbox (aggregate_type, aggregate_id);

CREATE TABLE inbound_event_dedupe (
    event_id      UUID         PRIMARY KEY,
    event_type    VARCHAR(60)  NOT NULL,
    processed_at  TIMESTAMPTZ  NOT NULL,
    outcome       VARCHAR(20)  NOT NULL,
    CONSTRAINT ck_inbound_dedupe_outcome
        CHECK (outcome IN ('APPLIED', 'IGNORED_DUPLICATE', 'FAILED'))
);

CREATE INDEX idx_inbound_dedupe_processed_at
    ON inbound_event_dedupe (processed_at);
```

**`inbound_outbox` = wms-specific shape**: UUID PK + JSONB payload +
`partition_key` column — matches inventory / notification outbox shape,
**diverges from master's libs/java-messaging shared shape** (BIGSERIAL +
TEXT + status enum, see
[`../master-service/database-design.md`](../master-service/database-design.md)
§ 2). The wms-specific shape was adopted to support per-event partition
routing (downstream consumer cross-correlation) and JSONB queryability —
features the libs base did not provide at the time inbound was written.

**`partition_key`**: set per-event (typically `asn_id` for ASN events,
`inspection_id` for inspection events). Guarantees per-aggregate
in-order Kafka delivery for cross-service correlation.

**Pending-publisher index** (`WHERE published_at IS NULL`): keeps the
publisher's FIFO scan cheap regardless of total outbox row count.

**Both tables are append-only** (V7 trigger):
- `inbound_event_dedupe` blocks UPDATE + DELETE entirely.
- `inbound_outbox` blocks DELETE only — the publisher needs UPDATE on
  `published_at` to stamp publish-success (see § 7).

---

## 6. ERP Webhook Inbox + Dedupe (V6)

Two-table pattern for ERP webhook ingest: a mutable inbox that
decouples the synchronous webhook handler from the background processor,
and an append-only dedupe table for replay protection by
`X-Erp-Event-Id`.

```sql
CREATE TABLE erp_webhook_inbox (
    event_id        VARCHAR(80)  PRIMARY KEY,
    raw_payload     JSONB        NOT NULL,
    signature       VARCHAR(100) NOT NULL,
    source          VARCHAR(40)  NOT NULL,
    received_at     TIMESTAMPTZ  NOT NULL,
    status          VARCHAR(20)  NOT NULL,
    processed_at    TIMESTAMPTZ,
    failure_reason  VARCHAR(500),
    CONSTRAINT ck_erp_webhook_inbox_status
        CHECK (status IN ('PENDING', 'APPLIED', 'FAILED'))
);

CREATE INDEX idx_erp_webhook_inbox_pending
    ON erp_webhook_inbox (received_at)
    WHERE status = 'PENDING';

CREATE INDEX idx_erp_webhook_inbox_failed
    ON erp_webhook_inbox (received_at)
    WHERE status = 'FAILED';

CREATE TABLE erp_webhook_dedupe (
    event_id     VARCHAR(80)  PRIMARY KEY,
    received_at  TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_erp_webhook_dedupe_received
    ON erp_webhook_dedupe (received_at);
```

**`erp_webhook_inbox` is mutable** — status transitions
`PENDING → APPLIED | FAILED` performed by the background processor.
The two partial indexes (`pending` and `failed`) support the processor's
hot paths: pick next batch + re-process failures (operator-triggered).

**`erp_webhook_dedupe` is append-only** (V7 trigger) — records every
`X-Erp-Event-Id` received. A duplicate `event_id` arriving via ERP retry
short-circuits at the dedupe insert (PK conflict) before any inbox row
is written.

**`event_id VARCHAR(80)`** — wider than the UUID columns elsewhere in
the schema because ERP-supplied event IDs may include vendor-prefixed
strings (e.g., `erp-prod:abc-123-def`).

---

## 7. Append-Only Enforcement Strategy (V7, W2)

Four trigger functions enforce the W2 append-only invariant on four
tables. Two-layer defense identical in pattern to inventory V5 (see
[`../inventory-service/database-design.md`](../inventory-service/database-design.md)
§ 8), extended here for inbound's larger append-only surface area.

```sql
CREATE OR REPLACE FUNCTION inbound_event_dedupe_reject_modification()
    RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION
        'inbound_event_dedupe is append-only (W2): % rejected', TG_OP
        USING ERRCODE = '23514';
END; $$;

CREATE TRIGGER trg_inbound_event_dedupe_no_update
    BEFORE UPDATE ON inbound_event_dedupe
    FOR EACH ROW
    EXECUTE FUNCTION inbound_event_dedupe_reject_modification();

CREATE TRIGGER trg_inbound_event_dedupe_no_delete
    BEFORE DELETE ON inbound_event_dedupe
    FOR EACH ROW
    EXECUTE FUNCTION inbound_event_dedupe_reject_modification();

-- erp_webhook_dedupe: same 2-trigger pattern (UPDATE + DELETE block)
-- putaway_confirmation: same 2-trigger pattern (UPDATE + DELETE block)
-- (function bodies and CREATE TRIGGER statements omitted for brevity;
--  see V7 source file for byte-identical SQL)

-- inbound_outbox: DELETE-only block (UPDATE permitted for published_at stamp)
CREATE OR REPLACE FUNCTION inbound_outbox_reject_delete()
    RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION
        'inbound_outbox is append-only (W2): DELETE rejected'
        USING ERRCODE = '23514';
END; $$;

CREATE TRIGGER trg_inbound_outbox_no_delete
    BEFORE DELETE ON inbound_outbox
    FOR EACH ROW
    EXECUTE FUNCTION inbound_outbox_reject_delete();
```

| Protected table | Operations blocked | Rationale |
|---|---|---|
| `inbound_event_dedupe` | UPDATE + DELETE | T8 contract — once an event is processed, its outcome is permanent |
| `erp_webhook_dedupe` | UPDATE + DELETE | Webhook replay protection — once an `event_id` is seen, it's seen forever |
| `putaway_confirmation` | UPDATE + DELETE | Operator action audit — physical putaway cannot be retroactively edited |
| `inbound_outbox` | **DELETE only** (UPDATE permitted) | Publisher needs UPDATE for `published_at` stamp |

**`inbound_outbox` DELETE-only is the unique outbox pattern**: most
append-only tables block both UPDATE and DELETE, but the outbox
publisher must mark rows as published. The trigger lets UPDATE through
for outbox alone.

**REVOKE backstop** (V7 DO block at end):

```sql
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = current_user) THEN
        EXECUTE format('REVOKE UPDATE, DELETE ON inbound_event_dedupe FROM %I', current_user);
        EXECUTE format('REVOKE UPDATE, DELETE ON erp_webhook_dedupe FROM %I', current_user);
        EXECUTE format('REVOKE UPDATE, DELETE ON putaway_confirmation FROM %I', current_user);
        EXECUTE format('REVOKE DELETE ON inbound_outbox FROM %I', current_user);
    END IF;
EXCEPTION WHEN OTHERS THEN
    NULL;  -- Owner self-revoke is a no-op; tolerated for migration idempotence
END $$;
```

Effective in production where the DBA separates an `inbound_owner` DDL
role from a runtime application role. In local docker-compose, the
application role IS the table owner → self-revoke is a no-op; the
trigger is the only defense. This matches inventory V5's documented
posture.

---

## 8. ASN Number Sequence (V8)

Per-day atomic sequence for `asn_no` generation. Avoids a global
sequence (which would couple ASN numbers to instance-wide write rate)
and instead resets per calendar day — operators see `YYYYMMDD-NNN`
numbers that align with their daily batch routine.

```sql
CREATE TABLE asn_no_sequence (
    date_key   VARCHAR(8)   PRIMARY KEY,
    last_seq   BIGINT       NOT NULL DEFAULT 1,
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
```

**Atomic UPSERT pattern** (consumed by `AsnPersistenceAdapter.nextAsnNo()`):

```sql
INSERT INTO asn_no_sequence (date_key, last_seq)
VALUES ('20260514', 1)
ON CONFLICT (date_key)
DO UPDATE SET last_seq = asn_no_sequence.last_seq + 1,
              updated_at = now()
RETURNING last_seq;
```

Single statement — race-free under concurrent writes. Postgres row-level
locking guarantees atomicity; no separate `SELECT FOR UPDATE` + `UPDATE`
roundtrip is needed.

**`date_key VARCHAR(8)`**: stores `YYYYMMDD` as text rather than a DATE
column to make the natural-key form trivially indexable as PK and to
match the wire format of the resulting `asn_no`.

---

## 9. Indexing Strategy Summary

| Table | Index | Type | Purpose |
|---|---|---|---|
| `warehouse_snapshot` | `warehouse_snapshot_pkey` | PK | upsert target |
| `warehouse_snapshot` | `idx_warehouse_snapshot_code` | btree | natural-key lookup |
| `zone_snapshot` | `zone_snapshot_pkey` | PK | upsert target |
| `zone_snapshot` | `idx_zone_snapshot_warehouse` | btree | warehouse-scoped |
| `zone_snapshot` | `idx_zone_snapshot_code` | btree | natural-key lookup |
| `location_snapshot` | `location_snapshot_pkey` | PK | upsert target |
| `location_snapshot` | `idx_location_snapshot_warehouse` | btree | warehouse-scoped |
| `location_snapshot` | `idx_location_snapshot_code` | btree | natural-key lookup |
| `sku_snapshot` | `sku_snapshot_pkey` | PK | upsert target |
| `sku_snapshot` | `idx_sku_snapshot_code` | btree | natural-key lookup |
| `lot_snapshot` | `lot_snapshot_pkey` | PK | upsert target |
| `lot_snapshot` | `idx_lot_snapshot_sku` | btree | per-SKU enumeration |
| `partner_snapshot` | `partner_snapshot_pkey` | PK | upsert target |
| `partner_snapshot` | `idx_partner_snapshot_code` | btree | natural-key lookup |
| `asn` | `asn_pkey` | PK | row lookup |
| `asn` | `uq_asn_no` | unique | global asn_no |
| `asn` | `idx_asn_warehouse` | btree | warehouse-scoped list |
| `asn` | `idx_asn_supplier` | btree | supplier-scoped audit |
| `asn` | `idx_asn_status` | btree | status filter |
| `asn_line` | `asn_line_pkey` | PK | row lookup |
| `asn_line` | `uq_asn_line_no` | unique compound | per-ASN line number |
| `asn_line` | `idx_asn_line_asn` | btree | FK lookup |
| `asn_line` | `idx_asn_line_sku` | btree | per-SKU history |
| `inspection` | `inspection_pkey` | PK | row lookup |
| `inspection` | (`asn_id` UNIQUE) | unique | 1:1 with ASN |
| `inspection` | `idx_inspection_asn` | btree | FK lookup |
| `inspection_line` | `inspection_line_pkey` | PK | row lookup |
| `inspection_line` | `uq_inspection_line_asn_line` | unique compound | one line per ASN line |
| `inspection_line` | `idx_inspection_line_inspection` | btree | FK lookup |
| `inspection_line` | `idx_inspection_line_asn_line` | btree | reverse FK lookup |
| `inspection_discrepancy` | `inspection_discrepancy_pkey` | PK | row lookup |
| `inspection_discrepancy` | `idx_inspection_discrepancy_inspection` | btree | FK lookup |
| `inspection_discrepancy` | `idx_inspection_discrepancy_unacked` | partial (`acknowledged=FALSE`) | operator dashboard hot path |
| `putaway_instruction` | `putaway_instruction_pkey` | PK | row lookup |
| `putaway_instruction` | (`asn_id` UNIQUE) | unique | 1:1 with ASN |
| `putaway_instruction` | `idx_putaway_instruction_asn` | btree | FK lookup |
| `putaway_instruction` | `idx_putaway_instruction_warehouse` | btree | warehouse-scoped |
| `putaway_line` | `putaway_line_pkey` | PK | row lookup |
| `putaway_line` | `idx_putaway_line_instruction` | btree | FK lookup |
| `putaway_line` | `idx_putaway_line_asn_line` | btree | reverse FK lookup |
| `putaway_line` | `idx_putaway_line_destination` | btree | location-scoped audit |
| `putaway_confirmation` | `putaway_confirmation_pkey` | PK | row lookup |
| `putaway_confirmation` | (`putaway_line_id` UNIQUE) | unique | 1:1 with line |
| `putaway_confirmation` | `idx_putaway_confirmation_instruction` | btree | FK lookup |
| `putaway_confirmation` | `idx_putaway_confirmation_line` | btree | reverse FK lookup |
| `putaway_confirmation` | `idx_putaway_confirmation_actual_loc` | btree | location-scoped audit |
| `inbound_outbox` | `inbound_outbox_pkey` | PK | row lookup |
| `inbound_outbox` | `idx_inbound_outbox_pending` | partial (`published_at NULL`) | publisher FIFO scan |
| `inbound_outbox` | `idx_inbound_outbox_aggregate` | btree | aggregate-scoped lookup |
| `inbound_event_dedupe` | `inbound_event_dedupe_pkey` | PK | dedupe by event_id |
| `inbound_event_dedupe` | `idx_inbound_dedupe_processed_at` | btree | 30-day retention sweeper |
| `erp_webhook_inbox` | `erp_webhook_inbox_pkey` (event_id) | PK | dedupe + lookup |
| `erp_webhook_inbox` | `idx_erp_webhook_inbox_pending` | partial (`status='PENDING'`) | processor hot path |
| `erp_webhook_inbox` | `idx_erp_webhook_inbox_failed` | partial (`status='FAILED'`) | re-process hot path |
| `erp_webhook_dedupe` | `erp_webhook_dedupe_pkey` (event_id) | PK | replay protection |
| `erp_webhook_dedupe` | `idx_erp_webhook_dedupe_received` | btree | retention sweeper |
| `asn_no_sequence` | `asn_no_sequence_pkey` (date_key) | PK | atomic UPSERT target |

**5 partial indexes** (highlighted): outbox pending / inspection
discrepancy unacked / webhook inbox pending / webhook inbox failed /
(implicit via PK) — the partial filter keeps hot-path scans tight as
historical rows accumulate.

---

## 10. Migration History

| Version | File | Line | Scope |
|---|---|---|---|
| V1 | `V1__init_master_readmodel.sql` | 124 | 6 master snapshot tables (warehouse / zone / location / sku / lot / partner with 4-enum) |
| V2 | `V2__init_asn_tables.sql` | 52 | asn + asn_line (7-status state machine + MANUAL/WEBHOOK_ERP source) |
| V3 | `V3__init_inspection_tables.sql` | 62 | inspection (1:1 ASN) + inspection_line (3 qty bucket) + inspection_discrepancy (3-enum + partial unacked index) |
| V4 | `V4__init_putaway_tables.sql` | 64 | putaway_instruction + putaway_line + putaway_confirmation (append-only) |
| V5 | `V5__init_outbox_dedupe.sql` | 51 | inbound_outbox (wms-specific UUID+JSONB+partition_key) + inbound_event_dedupe (T8 3-outcome) |
| V6 | `V6__init_webhook_inbox.sql` | 44 | erp_webhook_inbox (FSM + dual partial index) + erp_webhook_dedupe (append-only) |
| V7 | `V7__role_grants.sql` | 152 | 4 trigger functions (W2 append-only — 3 full-block + 1 DELETE-only outbox) + REVOKE block |
| V8 | `V8__init_asn_no_sequence.sql` | 13 | asn_no_sequence (per-day atomic UPSERT for asn_no generation) |

When `V9+` lands, this document must be updated in the same commit (per
the retrospective contract introduced by TASK-BE-157).

---

## References

- [`domain-model.md`](domain-model.md) — domain meaning of each table (canonical reference)
- [`architecture.md`](architecture.md) — § Persistence, § Open Items, § Security
- [`idempotency.md`](idempotency.md) — REST + outbox + webhook dedupe
- [`external-integrations.md`](external-integrations.md) — ERP webhook + Kafka catalog
- [`state-machines/asn-status.md`](state-machines/asn-status.md) — 7-state ASN transitions
- [`../inventory-service/database-design.md`](../inventory-service/database-design.md) — sibling primary template (BE-157, V5 trigger pattern source)
- [`../notification-service/database-design.md`](../notification-service/database-design.md) — sibling (BE-160)
- [`../master-service/database-design.md`](../master-service/database-design.md) — sibling (BE-161, V2 libs outbox shape divergence reference)
- `../../../apps/inbound-service/src/main/resources/db/migration/V1__init_master_readmodel.sql`
- `../../../apps/inbound-service/src/main/resources/db/migration/V2__init_asn_tables.sql`
- `../../../apps/inbound-service/src/main/resources/db/migration/V3__init_inspection_tables.sql`
- `../../../apps/inbound-service/src/main/resources/db/migration/V4__init_putaway_tables.sql`
- `../../../apps/inbound-service/src/main/resources/db/migration/V5__init_outbox_dedupe.sql`
- `../../../apps/inbound-service/src/main/resources/db/migration/V6__init_webhook_inbox.sql`
- `../../../apps/inbound-service/src/main/resources/db/migration/V7__role_grants.sql`
- `../../../apps/inbound-service/src/main/resources/db/migration/V8__init_asn_no_sequence.sql`
- `../../../../../rules/domains/wms.md` — W2 append-only invariant
- `../../../../../rules/traits/transactional.md` — T3 (outbox), T8 (event dedupe)
- `../../../../../rules/traits/integration-heavy.md` — I5 (DLQ) + I6 (webhook signature)
- `../../../../../platform/architecture.md` — system-level architecture
