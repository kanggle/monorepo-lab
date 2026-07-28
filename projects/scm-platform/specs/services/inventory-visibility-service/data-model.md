# inventory-visibility-service — Data Model

## Tables

### inventory_nodes

| Column | Type | Notes |
|---|---|---|
| id | VARCHAR(36) PK | UUID |
| tenant_id | VARCHAR(64) NOT NULL | always `scm` in v1 |
| node_type | VARCHAR(30) NOT NULL | WMS_WAREHOUSE / SUPPLIER / THIRD_PARTY_LOGISTICS / IN_TRANSIT |
| node_external_id | VARCHAR(100) NOT NULL | external system identifier (e.g., wms warehouseId) |
| name | VARCHAR(200) | empty for auto-registered `WMS_WAREHOUSE` nodes (enriched later); **named at registration** for explicitly-registered `THIRD_PARTY_LOGISTICS` nodes (ADR-MONO-054 §D2 / TASK-SCM-BE-046) — a 3PL relationship is known by name when onboarded |
| status | VARCHAR(20) NOT NULL | ACTIVE / SUSPENDED / DECOMMISSIONED |
| contact_info | JSONB | nullable; mapped via `@JdbcTypeCode(SqlTypes.JSON)` on the JPA entity (see § Hibernate ↔ PostgreSQL JSONB note below) |
| created_at | TIMESTAMPTZ NOT NULL | |
| updated_at | TIMESTAMPTZ NOT NULL | |

Unique: `(tenant_id, node_external_id)`.
Index: `(tenant_id, node_type, status)`.

**Registration model** (see [`architecture.md § Node Registration Model`](architecture.md#node-registration-model-adr-mono-054-d2--task-scm-be-046)):
`WMS_WAREHOUSE` is auto-registered from wms events; `THIRD_PARTY_LOGISTICS`
is **explicitly registered** (`POST /api/inventory-visibility/nodes`,
ADR-MONO-054 §D2 / TASK-SCM-BE-046) — a 3PL relationship is an onboarding
fact, not an event side-effect. A registered 3PL node is **observed
read-only, never operated** (ADR-054 §D4 / ADR-050 §D4); `SUPPLIER` /
`IN_TRANSIT` remain declared with no active registration path in v1.

**Hibernate ↔ PostgreSQL JSONB note** (TASK-SCM-INT-001b cycle 2 fix, PR #262):
`InventoryNodeJpaEntity.contactInfo` is a `String` column mapped to PostgreSQL
`jsonb`. Without `@JdbcTypeCode(SqlTypes.JSON)`, Hibernate 6 binds the
parameter as `bytea`/`varchar` and PostgreSQL raises `42804 datatype_mismatch`
("column "contact_info" is of type jsonb but expression is of type
character varying"). The annotation forces Hibernate to use the JSON SQL
type code so the JDBC driver emits the correct PG cast.

```java
@JdbcTypeCode(SqlTypes.JSON)
@Column(name = "contact_info", columnDefinition = "jsonb")
private String contactInfo;
```

This is a **recurring monorepo pattern** for any `String` JPA field stored as
PostgreSQL `jsonb`. Sibling occurrence: `apps/procurement-service/.../domain/
supplier/Supplier.contactInfoJson` (TASK-SCM-BE-002d fix). When introducing a
new JSONB column, copy this annotation pair as a unit.

References:
- RFC 9562 §5.7 UUID v7 (note: rand_b suffix consumed by procurement-service
  `poNumber` — see procurement-service/data-model.md)
- Hibernate User Guide § 2.4 "Basic value types — JSON"
- TASK-SCM-INT-001b cycle 2 (PR #262) — root cause analysis

### inventory_snapshots

| Column | Type | Notes |
|---|---|---|
| id | VARCHAR(36) PK | UUID |
| node_id | VARCHAR(36) NOT NULL FK→inventory_nodes | |
| sku | VARCHAR(100) NOT NULL | |
| quantity | NUMERIC(18,3) NOT NULL | |
| tenant_id | VARCHAR(64) NOT NULL | |
| last_event_id | VARCHAR(36) NOT NULL | UUID v7 of last applied wms event |
| last_event_at | TIMESTAMPTZ | |
| version | INT NOT NULL | optimistic lock counter |
| updated_at | TIMESTAMPTZ NOT NULL | |

Unique: `(node_id, sku, tenant_id)`.
Indexes: `(tenant_id, sku)`, `(node_id, updated_at DESC)`, `(tenant_id, updated_at DESC)`.

**S5 note**: this table is an eventually-consistent read-model. `last_event_at` is the authoritative freshness indicator. Callers must check staleness before trusting quantity values for PO decisions.

**3PL observation note** (ADR-MONO-054 §D4 / TASK-SCM-BE-047): the table carries
**no node-type column** — a row for a `THIRD_PARTY_LOGISTICS` node is
structurally identical to a `WMS_WAREHOUSE` row. The two differ only in how
`quantity`/`last_event_id`/`last_event_at`/`version` are **written**: wms rows
are written by `applyDelta` (incremental, from `wms.inventory.*` events); a 3PL
row is written by `applyQuantity` (**absolute set**, from an operator-pushed
observation via `POST /nodes/{nodeId}/observed-stock`) — each observation
*replaces* the stored quantity rather than accumulating against it. Staleness
applies identically once a 3PL node has been observed at least once (see
`node_staleness` below).

### node_staleness

| Column | Type | Notes |
|---|---|---|
| node_id | VARCHAR(36) PK FK→inventory_nodes | |
| tenant_id | VARCHAR(64) NOT NULL | |
| last_event_at | TIMESTAMPTZ | null if no events ever received |
| last_event_id | VARCHAR(36) | |
| staleness_status | VARCHAR(20) NOT NULL | FRESH / STALE / UNREACHABLE |
| last_checked_at | TIMESTAMPTZ | set by staleness detection batch |

Index: `(tenant_id, staleness_status)`.

**3PL observation note** (TASK-SCM-BE-047): the only path that creates a row
for a `THIRD_PARTY_LOGISTICS` node's `node_id` is the first
`applyThirdPartyObservedStock` call — `POST /nodes` (TASK-SCM-BE-046, node
registration) does **not** seed one. A registered-but-never-observed 3PL node
therefore has no `node_staleness` row and is absent from the staleness sweep
until its first observation, at which point it joins the FRESH/STALE/UNREACHABLE
lifecycle exactly like a wms node.

### inbound_expectations

The scm-internal **3PL inbound-expectation sink** (ADR-MONO-055 §D4 / TASK-SCM-BE-049).
A lightweight, read-model-shaped record of stock we expect to arrive at a
`THIRD_PARTY_LOGISTICS` node, materialized from
`scm.procurement.inbound-expected.third-party.v1` (published by
`procurement-service` when a 3PL-addressed replenishment PO is confirmed). It is
**not** a state machine — it is a projection with a two-value `status`
(`OPEN` → `SATISFIED`) reconciled by 3PL observation (TASK-SCM-BE-047).

| Column | Type | Notes |
|---|---|---|
| id | VARCHAR(36) PK | UUID |
| tenant_id | VARCHAR(64) NOT NULL | always `scm` in v1 |
| node_id | VARCHAR(36) NOT NULL FK→inventory_nodes | the addressed 3PL node |
| sku | VARCHAR(100) NOT NULL | |
| expected_quantity | NUMERIC(18,3) NOT NULL | ordered quantity for this line |
| source_po_id | VARCHAR(36) NOT NULL | the procurement PO aggregate id |
| source_po_number | VARCHAR(40) NOT NULL | the PO business reference (dedupe dimension) |
| expected_at | DATE | nullable — `confirmedAt + lead_time_days`, absent when lead time unknown |
| status | VARCHAR(20) NOT NULL | `OPEN` / `SATISFIED` (default `OPEN`) |
| created_at | TIMESTAMPTZ NOT NULL | |
| updated_at | TIMESTAMPTZ NOT NULL | |
| satisfied_at | TIMESTAMPTZ | set when reconciled to `SATISFIED` |

Unique: `(tenant_id, source_po_number, sku, node_id)` — **idempotency on the PO
reference** (ADR-MONO-055 §D4): a re-confirmed / replayed PO does not
double-record; a duplicate insert is caught and treated as a no-op.
Indexes: `(node_id, sku, status)` (reconciliation lookup), `(tenant_id, status)`
(open-expectation operational visibility).

**Reconciliation** (TASK-SCM-BE-047 observation feed): when
`applyThirdPartyObservedStock` records an absolute observed quantity for
`(node_id, sku)`, every `OPEN` expectation for that pair whose
`expected_quantity ≤ observed quantity` is marked `SATISFIED` (v1 is **binary**
— a partial observation below the expected quantity leaves the expectation
`OPEN`, an aging operational signal, not silently dropped). No bespoke scheduler:
reconciliation rides the existing observation path.

**3PL node absent / wrong type** (fail-closed): the consumer resolves the node by
`node_id` before inserting; an absent, non-`THIRD_PARTY_LOGISTICS`, or
cross-tenant node yields a clear error routed to the topic DLT — **no orphan
expectation** is created (ADR-MONO-055 §D4 / TASK-SCM-BE-049 Edge Case).

### event_dedupe

| Column | Type | Notes |
|---|---|---|
| event_id | VARCHAR(36) PK | UUID v7 from wms envelope |
| tenant_id | VARCHAR(64) NOT NULL | |
| processed_at | TIMESTAMPTZ NOT NULL | |
| source_topic | VARCHAR(200) NOT NULL | |

Index: `(tenant_id, processed_at)`.
Purpose: T8 idempotency — duplicate event_id lookups hit this table before any mutation.

### shedlock

Standard ShedLock schema (batch-heavy trait). Used by `StalenessDetectionScheduler`.

## tenant_id Policy

All tables carry `tenant_id` as a non-nullable column. v1 always `scm`. Key indexes prefix `tenant_id` to support future multi-org extension without schema migration.
