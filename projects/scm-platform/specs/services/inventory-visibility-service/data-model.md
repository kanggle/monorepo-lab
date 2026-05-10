# inventory-visibility-service — Data Model

## Tables

### inventory_nodes

| Column | Type | Notes |
|---|---|---|
| id | VARCHAR(36) PK | UUID |
| tenant_id | VARCHAR(64) NOT NULL | always `scm` in v1 |
| node_type | VARCHAR(30) NOT NULL | WMS_WAREHOUSE / SUPPLIER / THIRD_PARTY_LOGISTICS / IN_TRANSIT |
| node_external_id | VARCHAR(100) NOT NULL | external system identifier (e.g., wms warehouseId) |
| name | VARCHAR(200) | empty for auto-registered nodes |
| status | VARCHAR(20) NOT NULL | ACTIVE / SUSPENDED / DECOMMISSIONED |
| contact_info | JSONB | nullable; mapped via `@JdbcTypeCode(SqlTypes.JSON)` on the JPA entity (see § Hibernate ↔ PostgreSQL JSONB note below) |
| created_at | TIMESTAMPTZ NOT NULL | |
| updated_at | TIMESTAMPTZ NOT NULL | |

Unique: `(tenant_id, node_external_id)`.
Index: `(tenant_id, node_type, status)`.

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
