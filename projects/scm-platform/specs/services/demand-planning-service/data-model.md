# demand-planning-service — Data Model

PostgreSQL schema `scm_demand_planning` (Flyway `db/migration/demand-planning`).
All tables carry `tenant_id` (always `"scm"` in v1) with an index prefix.
Per ADR-MONO-027 D3/D4/D6.

## Tables

### `reorder_policy` — the scm-owned reorder decision (D4)

Distinct from the wms alert threshold. The scm reorder point/quantity per SKU.

| Column | Type | Null | Notes |
|---|---|---|---|
| `sku_code` | VARCHAR | no | Join key (shared SKU coding with procurement + wms alert). |
| `reorder_point` | INT | no | Reorder when `availableQty <= reorder_point`. |
| `safety_stock` | INT | no | v1 informational; reserved for v2 forecasting. |
| `reorder_qty` | INT | no | Quantity to suggest (per raise). |
| `tenant_id` | VARCHAR | no | `"scm"`. |
| `version` | INT | no | Optimistic lock. |
| `updated_at` | TIMESTAMPTZ | no | |

- PK / UNIQUE `(tenant_id, sku_code)`.
- If no policy row exists for a SKU, fall back to `sku_supplier_map.default_order_qty`
  and treat `reorder_point` as the wms alert's own threshold (degraded — a policy
  row is the intended source).

### `sku_supplier_map` — minimal SKU→supplier mapping (D3)

Deliberate minimal stand-in for the v2-deferred `supplier-service`.

| Column | Type | Null | Notes |
|---|---|---|---|
| `sku_code` | VARCHAR | no | Join key. |
| `supplier_id` | UUID | no | **FK-free** cross-service reference (procurement convention — no DB FK). |
| `default_order_qty` | INT | no | Fallback reorder quantity when no `reorder_policy.reorder_qty`. |
| `lead_time_days` | INT | no | Informational (expected arrival horizon). |
| `currency` | CHAR(3) | no | ISO 4217 for the PO. |
| `tenant_id` | VARCHAR | no | `"scm"`. |

- PK / UNIQUE `(tenant_id, sku_code)`.
- Unmapped `sku_code` at alert time → non-retryable DLT + ops alert (fail-closed,
  S2). Cannot reorder from an unknown supplier.
- Migrates to `supplier-service` at v2 — the `supplier_id` reference is
  forward-compatible (same value, resolved against the supplier master later).

### `reorder_suggestion` — the aggregate

| Column | Type | Null | Notes |
|---|---|---|---|
| `id` | UUID v7 | no | PK. |
| `sku_code` | VARCHAR | no | |
| `warehouse_id` | UUID | no | From the alert `locationId`/warehouse — the suggestion dimension. |
| `supplier_id` | UUID | no | Resolved from `sku_supplier_map` at raise time. |
| `suggested_qty` | INT | no | From `reorder_policy.reorder_qty` (fallback `default_order_qty`). |
| `status` | VARCHAR | no | `SUGGESTED` / `APPROVED` / `MATERIALIZED` / `DISMISSED`. |
| `source` | VARCHAR | no | `ALERT` / `BATCH`. |
| `trigger_event_id` | UUID | yes | The wms alert `eventId` (null for `BATCH` source). |
| `trigger_available_qty` | INT | yes | `availableQty` at raise time (provenance). |
| `materialized_po_id` | UUID | yes | Soft link to the procurement DRAFT PO (set on MATERIALIZED). **Not an FK.** |
| `tenant_id` | VARCHAR | no | `"scm"`. |
| `version` | INT | no | Optimistic lock (concurrent approve guard). |
| `created_at` / `updated_at` | TIMESTAMPTZ | no | |

Indexes:
- `(tenant_id, status)` — operator list filtering.
- **Partial-unique open-suggestion guard (D6)**:
  `UNIQUE (tenant_id, sku_code, warehouse_id) WHERE status IN ('SUGGESTED','APPROVED')`.
  A `MATERIALIZED` or `DISMISSED` suggestion does **not** block a future
  re-suggestion when stock drops again — the guard spans only the two non-terminal
  states.

#### Status machine

```
SUGGESTED ──approve──> APPROVED ──materialize(DRAFT PO created)──> MATERIALIZED
    │                     │
    └──dismiss──┐         └──dismiss──┐
                ▼                     ▼
             DISMISSED            DISMISSED
```

- `SUGGESTED → APPROVED`: operator approve (REST). Resolves `sku_supplier_map`;
  unmapped → `SKU_SUPPLIER_UNMAPPED` (422), stays `SUGGESTED`.
- `APPROVED → MATERIALIZED`: procurement DRAFT PO created (D5); set
  `materialized_po_id`. Idempotent on `sourceSuggestionId` (re-approve → same PO).
- `* → DISMISSED`: operator dismiss; releases the open-guard.
- `MATERIALIZED`/`DISMISSED` are terminal.

### `processed_events` — consumer idempotency (T8)

| Column | Type | Null | Notes |
|---|---|---|---|
| `event_id` | UUID | no | PK. The wms alert envelope `eventId`. |
| `tenant_id` | VARCHAR | no | `"scm"`. |
| `processed_at` | TIMESTAMPTZ | no | |
| `source_topic` | VARCHAR | no | `wms.inventory.alert.v1`. |

- Duplicate `event_id` → skip without mutation.

## Cross-service references (no FK)

- `supplier_id` → scm supplier identity (procurement / future supplier-service). FK-free.
- `materialized_po_id` → procurement PO. FK-free soft link.
- `sku_code` / `warehouse_id` → wms-originated identifiers carried in the alert. No FK.

This follows procurement's existing FK-free cross-service convention (S5 boundary).

## Notes

- v2 forecasting (moving-average / seasonality) extends `reorder_policy` — the
  table is the seam; no schema migration is presumed here.
- `safety_stock` is carried in v1 but only consumed by v2 logic.
