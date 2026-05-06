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
| contact_info | JSONB | nullable |
| created_at | TIMESTAMPTZ NOT NULL | |
| updated_at | TIMESTAMPTZ NOT NULL | |

Unique: `(tenant_id, node_external_id)`.
Index: `(tenant_id, node_type, status)`.

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
