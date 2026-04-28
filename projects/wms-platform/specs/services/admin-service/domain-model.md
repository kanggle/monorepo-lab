# admin-service — Domain Model

Domain model specification for `admin-service`. Owned aggregates, read-model
projection tables, and infrastructure records.

Read this after `specs/services/admin-service/architecture.md`. The CQRS
read-model pattern, Layered architecture exception, and projection strategy
are declared there and only restated here as far as needed to reason about
the model.

---

## Scope

`admin-service` owns two kinds of data:

### Write-side (small; standard CRUD with domain validation)

1. **User** — operator account profile and status
2. **Role** — role definition + permission set
3. **UserRoleAssignment** — user ↔ role binding scoped per warehouse
4. **Setting** — global / per-warehouse runtime configuration

### Read-side (large; CQRS projection tables)

5. **MasterRef tables** — denormalised warehouse / zone / location / SKU / lot /
   partner lookup data
6. **AsnSummary** — inbound ASN lifecycle summary
7. **InspectionSummary** — per-ASN inspection result summary
8. **OrderSummary** — outbound order lifecycle summary
9. **ShipmentSummary** — per-shipment record for throughput tracking
10. **InventorySnapshot** — per-(location, sku, lot) real-time stock view
11. **AdjustmentAudit** — inventory adjustment event log
12. **AlertLog** — low-stock and anomaly alert history
13. **ThroughputDaily** — daily inbound / outbound throughput counters

### Infrastructure

14. **AdminOutbox** — transactional outbox for user / role / settings mutations
15. **EventDedupe** — consumer-side dedupe table (T8)

---

## Architecture Note

`admin-service` uses **Layered** architecture (deliberate exception; declared in
`architecture.md § Architecture Style`). Domain types are simple POJOs. Read-model
entities are JPA entities used directly in query responses. There is no Hexagonal
port layer.

---

## Common Shape (Write-side aggregates)

| Field | Type | Notes |
|---|---|---|
| `id` | UUID | Surrogate PK |
| `version` | Long | Optimistic lock (T5) |
| `created_at` | Instant | UTC |
| `created_by` | String | JWT subject |
| `updated_at` | Instant | |
| `updated_by` | String | |

Read-model rows carry `last_event_at` (the source event's timestamp for
last-write-wins logic) and `version` (for concurrent projection conflict
detection), but no `created_by` / `updated_by`.

---

## 1. User

### Purpose

Operator identity record. Does **not** store credentials or tokens — those live
in the external identity provider (Keycloak / Cognito). `admin-service` stores
the profile data that `gateway-service` uses to populate JWT claims.

### Fields

| Field | Type | Nullable | Notes |
|---|---|---|---|
| `id` | UUID | no | |
| `user_code` | String (40) | no | Globally unique. Immutable after creation. Pattern: `USR-{seq}` or operator-assigned |
| `email` | String (200) | no | Globally unique; case-insensitive, stored lowercase |
| `name` | String (200) | no | Display name |
| `phone` | String (30) | yes | Operational contact |
| `status` | enum `ACTIVE` / `INACTIVE` | no | See state machine below |
| `default_warehouse_id` | UUID | yes | Soft ref to master Warehouse (via snapshot); sets default scope in UI |
| (common version / timestamp fields) | | | |

### User State Machine

```
    [create]
       |
       v
   ACTIVE ──[deactivate]──> INACTIVE
       ^──[reactivate]──────────|
```

- `ACTIVE` ↔ `INACTIVE` only (T4; `STATE_TRANSITION_INVALID` otherwise).
- Deactivation blocked if user has any `ACTIVE` `UserRoleAssignment` without
  the `force` flag (`USER_HAS_ACTIVE_ASSIGNMENTS`). With `force` flag
  (`WMS_SUPERADMIN` role required), assignments are cascade-revoked.

### Invariants

- `user_code` globally unique; immutable after creation.
- `email` globally unique (case-insensitive); `USER_EMAIL_DUPLICATE` on conflict.
- `email` is operational PII — logged fields must mask it (TLS, at-rest encryption
  per platform standard; see architecture.md § PII Handling).

---

## 2. Role

### Purpose

Named permission bundle. Assigned to users per warehouse scope
(or globally with `null` warehouse).

### Fields

| Field | Type | Nullable | Notes |
|---|---|---|---|
| `id` | UUID | no | |
| `role_code` | String (40) | no | Globally unique. Immutable after creation. Examples: `WMS_VIEWER`, `WMS_OPERATOR`, `WMS_ADMIN`, `WMS_SUPERADMIN` |
| `name` | String (100) | no | Human-readable |
| `description` | String (500) | yes | |
| `permissions_json` | JSONB | no | Array of permission strings, e.g. `["INVENTORY_READ","INVENTORY_WRITE"]`. Schema validated at write time |
| `status` | enum `ACTIVE` / `INACTIVE` | no | |
| (common version / timestamp fields) | | | |

### Invariants

- `role_code` globally unique; immutable after creation; `ROLE_CODE_DUPLICATE`.
- `permissions_json` must be a valid JSON array of known permission strings.
  Unknown strings → `SETTING_VALIDATION_ERROR` (reused; specific code TBD).
- Deactivation blocked if any `ACTIVE` `UserRoleAssignment` references this role
  (`ROLE_IN_USE`). `WMS_SUPERADMIN` with `force` flag cascades revocations.
- The four built-in roles (`WMS_VIEWER`, `WMS_OPERATOR`, `WMS_ADMIN`,
  `WMS_SUPERADMIN`) are seeded on first deployment and cannot be deleted (only
  their `permissions_json` may be updated via ops procedure, not via API in v1).

---

## 3. UserRoleAssignment

### Purpose

Grants a User a Role. Optionally scoped to a specific warehouse (`warehouse_id`
null = global scope). A user may hold multiple assignments.

### Fields

| Field | Type | Nullable | Notes |
|---|---|---|---|
| `id` | UUID | no | |
| `user_id` | UUID (FK) | no | |
| `role_id` | UUID (FK) | no | |
| `warehouse_id` | UUID | yes | Null = global scope. Soft ref to master Warehouse |
| `granted_at` | Instant | no | |
| `granted_by` | String | no | Actor id |
| `revoked_at` | Instant | yes | Set when `status = REVOKED` |
| `revoked_by` | String | yes | |
| `status` | enum `ACTIVE` / `REVOKED` | no | |
| (common version / timestamp fields) | | | |

### Invariants

- `(user_id, role_id, warehouse_id)` unique among `ACTIVE` assignments (null
  `warehouse_id` treated as singleton in the unique check). Duplicate grant
  returns the existing active assignment idempotently.
- Parent User and Role must both be `ACTIVE` at grant time.
- `REVOKED` is terminal — cannot be re-activated; create a new assignment instead.
- Revocation is the only mutation permitted after creation (setting
  `status = REVOKED`, `revoked_at`, `revoked_by`).

---

## 4. Setting

### Purpose

Runtime configuration values that services may read at startup or react to via
`admin.settings.changed` events. Examples: reservation TTL, low-stock threshold
per SKU category, notification routing.

### Fields

| Field | Type | Nullable | Notes |
|---|---|---|---|
| `key` | String (100) | no | PK. Dot-namespaced. Examples: `inventory.reservation.ttl_hours`, `inventory.low_stock.threshold_qty`, `inbound.putaway.auto_close_delay_hours` |
| `scope` | enum `GLOBAL` / `WAREHOUSE` | no | |
| `warehouse_id` | UUID | yes | Required iff `scope = WAREHOUSE`; null for `GLOBAL` |
| `value_json` | JSONB | no | The setting value. Schema validated against `schema_json` at write time |
| `schema_json` | JSONB | no | JSON Schema draft-07 fragment declaring allowed types and ranges. Seeded with each setting key |
| `description` | String (500) | yes | Human-readable explanation |
| `version` | Long | no | Optimistic lock |
| `updated_at` | Instant | no | |
| `updated_by` | String | no | |

`(key, warehouse_id)` is the composite unique key (null `warehouse_id` allowed
for `GLOBAL` keys).

### Invariants

- `value_json` must satisfy `schema_json` at write time (`SETTING_VALIDATION_ERROR`).
- Changing a setting fires `admin.settings.changed` event via outbox in the same
  TX (T3). Consuming services may reload the value.
- `key` and `scope` are immutable after creation. `warehouse_id` is immutable.
- Settings with `scope = WAREHOUSE` and null `warehouse_id` are rejected.
- Deleting a setting key is forbidden in v1 (deactivation not applicable here —
  settings are always readable).

### Seed Settings (v1)

| Key | Scope | Default |
|---|---|---|
| `inventory.reservation.ttl_hours` | GLOBAL | `24` |
| `inventory.low_stock.default_threshold_qty` | GLOBAL | `10` |
| `inbound.asn.auto_close_delay_hours` | GLOBAL | `48` |
| `outbound.saga.sweeper_interval_seconds` | GLOBAL | `60` |

---

## Read-Model Tables

All read-model tables are projections of other services' events. They are:

- **Eventually consistent** — may lag by seconds under normal load.
- **Last-write-wins** — each row carries `last_event_at`; projection handlers
  drop events older than the stored `last_event_at`.
- **Idempotent** — duplicate event re-processing produces the same row state
  (insert-or-update semantics; combined with EventDedupe for safety).
- **Replayable** — truncate + re-consume from offset 0 restores exact same state.

**Never** written by REST controllers or application services (projection consumers
only).

---

## 5. MasterRef Tables

One table per master-service aggregate; contain denormalised display fields for
join-free dashboard queries.

### WarehouseRef

| Field | Type |
|---|---|
| `id` | UUID (PK) |
| `warehouse_code` | String(10) |
| `name` | String(100) |
| `timezone` | String(40) |
| `status` | enum |
| `last_event_at` | Instant |
| `version` | Long |

### ZoneRef

| Field | Type |
|---|---|
| `id` | UUID (PK) |
| `warehouse_id` | UUID |
| `zone_code` | String(20) |
| `name` | String(100) |
| `zone_type` | enum |
| `status` | enum |
| `last_event_at` | Instant |
| `version` | Long |

### LocationRef

| Field | Type |
|---|---|
| `id` | UUID (PK) |
| `location_code` | String(40) |
| `warehouse_id` | UUID |
| `zone_id` | UUID |
| `location_type` | enum |
| `status` | enum |
| `last_event_at` | Instant |
| `version` | Long |

### SkuRef

| Field | Type |
|---|---|
| `id` | UUID (PK) |
| `sku_code` | String(40) |
| `name` | String(200) |
| `base_uom` | enum |
| `tracking_type` | enum |
| `status` | enum |
| `last_event_at` | Instant |
| `version` | Long |

### LotRef

| Field | Type |
|---|---|
| `id` | UUID (PK) |
| `sku_id` | UUID |
| `lot_no` | String(40) |
| `expiry_date` | LocalDate |
| `status` | enum |
| `last_event_at` | Instant |
| `version` | Long |

### PartnerRef

| Field | Type |
|---|---|
| `id` | UUID (PK) |
| `partner_code` | String(20) |
| `name` | String(200) |
| `partner_type` | enum |
| `status` | enum |
| `last_event_at` | Instant |
| `version` | Long |

---

## 6. AsnSummary

Projected from `wms.inbound.*` events. One row per ASN.

| Field | Type | Notes |
|---|---|---|
| `asn_id` | UUID (PK) | |
| `asn_no` | String(40) | |
| `warehouse_id` | UUID | |
| `supplier_partner_id` | UUID | |
| `supplier_name` | String(200) | Denormalized from PartnerRef at projection time |
| `status` | enum | Mirrors ASN status |
| `source` | enum `MANUAL` / `WEBHOOK_ERP` | |
| `expected_arrive_date` | LocalDate | Nullable |
| `line_count` | Integer | From ASN received event |
| `received_at` | Instant | Timestamp of `inbound.asn.received` event |
| `closed_at` | Instant | Nullable; set on `inbound.asn.closed` |
| `last_event_at` | Instant | For last-write-wins |
| `version` | Long | |

---

## 7. InspectionSummary

Projected from `inbound.inspection.completed`. One row per ASN (1:1).

| Field | Type | Notes |
|---|---|---|
| `asn_id` | UUID (PK) | |
| `warehouse_id` | UUID | |
| `inspection_completed_at` | Instant | |
| `inspector_id` | String | |
| `total_lines` | Integer | |
| `discrepancy_count` | Integer | Lines with at least one discrepancy |
| `total_qty_expected` | Integer | Sum across all lines |
| `total_qty_passed` | Integer | |
| `total_qty_damaged` | Integer | |
| `total_qty_short` | Integer | |
| `last_event_at` | Instant | |
| `version` | Long | |

---

## 8. OrderSummary

Projected from `wms.outbound.*` events. One row per Order.

| Field | Type | Notes |
|---|---|---|
| `order_id` | UUID (PK) | |
| `order_no` | String(40) | |
| `warehouse_id` | UUID | |
| `customer_partner_id` | UUID | |
| `customer_name` | String(200) | Denormalized |
| `status` | enum | Mirrors Order status |
| `source` | enum | |
| `required_ship_date` | LocalDate | Nullable |
| `line_count` | Integer | |
| `saga_state` | enum | Latest saga state from `OutboundSaga` |
| `received_at` | Instant | |
| `shipped_at` | Instant | Nullable |
| `last_event_at` | Instant | |
| `version` | Long | |

---

## 9. ShipmentSummary

Projected from `outbound.shipping.confirmed`. One row per Shipment.

| Field | Type | Notes |
|---|---|---|
| `shipment_id` | UUID (PK) | |
| `order_id` | UUID | |
| `order_no` | String(40) | Denormalized |
| `warehouse_id` | UUID | |
| `shipment_no` | String(40) | |
| `carrier_code` | String(40) | Nullable |
| `tracking_no` | String(100) | Nullable |
| `shipped_at` | Instant | |
| `total_qty` | Integer | Sum of all shipped line quantities |
| `last_event_at` | Instant | |
| `version` | Long | |

---

## 10. InventorySnapshot

Projected from `wms.inventory.*` events. One row per `(location_id, sku_id, lot_id)`.
This is the **primary dashboard data source** for real-time stock visibility.

| Field | Type | Notes |
|---|---|---|
| `location_id` | UUID | Composite PK part |
| `sku_id` | UUID | Composite PK part |
| `lot_id` | UUID | Composite PK part; null-aware |
| `warehouse_id` | UUID | Denormalized from LocationRef |
| `location_code` | String(40) | Denormalized |
| `sku_code` | String(40) | Denormalized |
| `lot_no` | String(40) | Nullable; denormalized |
| `available_qty` | Integer | |
| `reserved_qty` | Integer | |
| `damaged_qty` | Integer | |
| `on_hand_qty` | Integer | Stored (= available + reserved + damaged); updated at projection time |
| `low_stock_flag` | Boolean | Set when `available_qty ≤ threshold` (from Settings); updated on each event |
| `last_adjusted_at` | Instant | Timestamp of last mutation event |
| `last_event_at` | Instant | For last-write-wins |
| `version` | Long | |

`low_stock_flag` is recomputed at projection time using the current Setting
`inventory.low_stock.default_threshold_qty`. It is a read-model convenience
field — not authoritative. The authoritative low-stock alert is in `AlertLog`.

---

## 11. AdjustmentAudit

Projected from `inventory.adjusted` events. Append-only log; used for the
adjustment history dashboard.

| Field | Type | Notes |
|---|---|---|
| `id` | UUID (PK) | = `eventId` from the inventory event |
| `location_id` | UUID | |
| `sku_id` | UUID | |
| `lot_id` | UUID | Nullable |
| `warehouse_id` | UUID | Denormalized |
| `bucket` | enum | Which bucket changed |
| `delta` | Integer | Signed |
| `reason_code` | enum | |
| `reason_note` | String(500) | |
| `actor_id` | String | |
| `occurred_at` | Instant | From the source event |
| `projected_at` | Instant | When this row was written by the projection |

No `version` / `updated_*` — append-only. DB role grants revoke `UPDATE` /
`DELETE` for the application role.

---

## 12. AlertLog

Projected from `inventory.low-stock-detected` events. Used by the alert
dashboard.

| Field | Type | Notes |
|---|---|---|
| `id` | UUID (PK) | = `eventId` |
| `alert_type` | enum `LOW_STOCK` / `ANOMALY` | v1 only `LOW_STOCK` |
| `warehouse_id` | UUID | |
| `location_id` | UUID | |
| `sku_id` | UUID | |
| `lot_id` | UUID | Nullable |
| `threshold_qty` | Integer | The threshold that was configured |
| `actual_qty` | Integer | Available qty at detection time |
| `detected_at` | Instant | From event |
| `acknowledged_at` | Instant | Nullable; set by ops via admin API |
| `acknowledged_by` | String | Nullable |
| `projected_at` | Instant | |

Append-only from projection; `acknowledged_at` / `acknowledged_by` are updated
by the ops acknowledgement endpoint (the **only** write path on this table from
the application layer).

---

## 13. ThroughputDaily

Two tables for daily KPI counters. Updated by projection consumers; never set
by REST.

### ThroughputInboundDaily

Incremented by `inbound.putaway.completed` events.

| Field | Type | Notes |
|---|---|---|
| `date` | LocalDate | PK part |
| `warehouse_id` | UUID | PK part |
| `putaway_count` | Integer | Number of putaway completions |
| `qty_received` | Integer | Sum of qty across all lines in putaway events |
| `last_event_at` | Instant | |
| `version` | Long | |

### ThroughputOutboundDaily

Incremented by `outbound.shipping.confirmed` events.

| Field | Type | Notes |
|---|---|---|
| `date` | LocalDate | PK part |
| `warehouse_id` | UUID | PK part |
| `shipment_count` | Integer | |
| `qty_shipped` | Integer | Sum across all shipped lines |
| `last_event_at` | Instant | |
| `version` | Long | |

Projection handlers use `INSERT ... ON CONFLICT (date, warehouse_id) DO UPDATE`
(PostgreSQL upsert) to increment counters atomically. EventDedupe ensures
each event increments exactly once.

---

## 14. AdminOutbox (infrastructure)

Per T3. Only fired for User / Role / Assignment / Setting mutations.

| Field | Type | Notes |
|---|---|---|
| `id` | UUID | PK |
| `aggregate_type` | String(40) | `USER` / `ROLE` / `ASSIGNMENT` / `SETTING` |
| `aggregate_id` | String | UUID for user/role/assignment; `key` for setting |
| `event_type` | String(60) | `admin.user.created` / `.updated` / `.deactivated` / `admin.role.*` / `admin.assignment.granted` / `.revoked` / `admin.settings.changed` |
| `event_version` | String(10) | `v1` |
| `payload` | JSONB | Per `admin-events.md` |
| `partition_key` | String(60) | Aggregate id |
| `created_at` | Instant | |
| `published_at` | Instant | Nullable |

---

## 15. EventDedupe (infrastructure)

Per T8.

| Field | Type | Notes |
|---|---|---|
| `event_id` | UUID | PK; from inbound event header |
| `event_type` | String(60) | |
| `processed_at` | Instant | |
| `outcome` | enum `APPLIED` / `IGNORED_DUPLICATE` / `FAILED` | |

Retention: 30 days.

---

## Entity Relationship Diagram (Write-side)

```
User 1──────────────────N UserRoleAssignment N──────────────────1 Role

Setting (standalone; no FK to User/Role)
```

Read-side tables are standalone projections with no FK constraints between
themselves (intentionally — projections are independent and separately
replayable). Cross-row consistency is enforced in the source services, not here.

---

## Aggregate Boundaries (Write-side)

| Aggregate | Owns | Cross-aggregate via |
|---|---|---|
| User | status, profile | `admin.user.*` events; `UserRoleAssignment` is its own aggregate (not a child of User) |
| Role | permissions_json, status | `admin.role.*` events |
| UserRoleAssignment | grant/revoke state | References User and Role by FK; deactivation of parent User/Role cascades via service logic, not DB cascade |
| Setting | value_json | `admin.settings.changed` event; consuming services react asynchronously |

Read-model tables are **not aggregates** — they are projection state, managed
by `*ProjectionService` classes with no domain invariants beyond last-write-wins
and dedupe.

---

## Forbidden Patterns (in code)

- ❌ Writing read-model tables from REST controllers or application write-path
  code — projection consumers only
- ❌ Writing `InventorySnapshot` / `AdjustmentAudit` / `ThroughputDaily` from
  any code path other than the projection service
- ❌ Hard delete of any User / Role / Assignment row in v1 (`INACTIVE` / `REVOKED`
  are the terminal states)
- ❌ Deleting Setting keys in v1
- ❌ DB `UPDATE` / `DELETE` on `admin_adjustment_audit` or `admin_alert_log`
  projection rows (except `acknowledged_at` / `acknowledged_by` on `AlertLog`)
- ❌ Importing inventory / inbound / outbound domain classes into `admin-service`
  — data flows in via events only
- ❌ Outbox bypassed for user / role / settings mutations (T3)
- ❌ Cascading writes across aggregates in a single transaction except the
  `force`-flag deactivation path (User deactivation + cascade Assignment revocations)

---

## Reference Data Snapshot (v1 Seed)

Flyway `V99__seed_dev_data.sql`, profile `dev` or `standalone`:

- 4 built-in Roles: `WMS_VIEWER`, `WMS_OPERATOR`, `WMS_ADMIN`, `WMS_SUPERADMIN`
  with default `permissions_json` as declared in architecture.md
- 1 seed User `admin@wms.internal` with `WMS_SUPERADMIN` global assignment
- Default Settings per the seed table in §4
- Read-model tables empty at seed time; populated by replaying `master.*`,
  `inbound.*`, `outbound.*`, `inventory.*` topics from offset 0

---

## Open Items

- `specs/services/admin-service/idempotency.md` — REST + event-dedupe strategy
- `specs/services/admin-service/runbooks/read-model-rebuild.md` — manual replay
  procedure (architecture.md Open Items §5)
- `specs/contracts/http/admin-service-api.md` — REST endpoints (dashboards, user,
  role, settings)
- `specs/contracts/events/admin-events.md` — published event schemas
- `platform/error-handling.md` — register: `USER_EMAIL_DUPLICATE`,
  `ROLE_CODE_DUPLICATE`, `USER_HAS_ACTIVE_ASSIGNMENTS`, `ROLE_IN_USE`,
  `SETTING_VALIDATION_ERROR`
- `PROJECT.md § Overrides` — declare Layered exception for admin-service
  (architecture.md Open Items §8)

---

## References

- `architecture.md` (this directory)
- `rules/domains/wms.md` — Admin / Operations bounded context
- `rules/traits/transactional.md` — T1, T3, T5, T8 (mutation write paths)
- `platform/service-types/rest-api.md`
- `platform/service-types/event-consumer.md`
- `specs/services/master-service/domain-model.md` — source of MasterRef projections
- `specs/services/inventory-service/domain-model.md` — source of InventorySnapshot /
  AdjustmentAudit projections
- `specs/services/inbound-service/domain-model.md` — source of AsnSummary /
  InspectionSummary projections
- `specs/services/outbound-service/domain-model.md` — source of OrderSummary /
  ShipmentSummary projections
- `specs/contracts/events/admin-events.md` — published event payloads (Open Item)
