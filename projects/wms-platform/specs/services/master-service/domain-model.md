# master-service — Domain Model

Domain model specification for `master-service`. Owned entities, fields, relationships,
invariants, and state transitions.

Read this after `specs/services/master-service/architecture.md`.

---

## Scope

Six aggregates, each owning its own invariants:

1. **Warehouse** — 창고 루트
2. **Zone** — 창고 내 구역
3. **Location** — 물리적 저장 위치
4. **SKU** — 재고 식별 최소 단위
5. **Partner** — 거래처 (공급자 / 고객)
6. **Lot** — SKU의 제조일·유효기한 구분 단위

All aggregates share a common shape (see below).

---

## Common Aggregate Shape

Every aggregate carries these fields:

| Field | Type | Notes |
|---|---|---|
| `id` | UUID | Internal surrogate key |
| `{entity}_code` | String | Business identifier, unique per scope (see per-entity) |
| `name` | String | Human-readable name |
| `status` | enum `ACTIVE` / `INACTIVE` | Soft deactivation (W6); hard delete forbidden in v1 |
| `version` | Long | Optimistic lock (trait T5) |
| `created_at` | Instant | |
| `created_by` | String | Actor id from JWT |
| `updated_at` | Instant | |
| `updated_by` | String | Actor id from JWT |

No `deleted_at` column. Deactivation sets `status=INACTIVE`. Downstream services
treat `INACTIVE` as "read-only reference; do not allow new usage".

---

## State Machine (Common)

```
   [create]
      |
      v
  ACTIVE ----[deactivate]----> INACTIVE
      ^------[reactivate]--------|
```

- **create** → status `ACTIVE`
- **deactivate** → `ACTIVE` → `INACTIVE`, only if referential integrity passes (see per-aggregate rules)
- **reactivate** → `INACTIVE` → `ACTIVE`, always permitted in v1

Direct `UPDATE status` is forbidden (trait `transactional` T4). State change goes
through domain method `deactivate()` / `reactivate()` on the aggregate.

---

## 1. Warehouse

### Purpose

Top-level container. Everything in WMS is scoped under a warehouse.

### Fields

| Field | Type | Nullable | Notes |
|---|---|---|---|
| `id` | UUID | no | |
| `warehouse_code` | String (10) | no | Globally unique. Pattern: `WH\d{2,3}` (e.g., `WH01`). Immutable after create |
| `name` | String (100) | no | |
| `address` | String (200) | yes | Free-text |
| `timezone` | String (40) | no | IANA tz id, e.g., `Asia/Seoul`. Operational time-of-day reference |
| `status` | enum | no | |
| (common timestamp/version fields) | | | |

### Invariants

- `warehouse_code` unique across the system
- `warehouse_code` is immutable after creation (rename `name` instead)
- Deactivation blocked if any active `Zone` references this warehouse (local check)

### Relationships

- **One Warehouse : many Zones** (1:N)
- Referenced by downstream: `inventory-service`, `inbound-service`, `outbound-service`

---

## 2. Zone

### Purpose

Logical grouping within a warehouse. Typical uses: temperature range (상온/냉장/냉동),
purpose (벌크/소량/반품), picking strategy.

### Fields

| Field | Type | Nullable | Notes |
|---|---|---|---|
| `id` | UUID | no | |
| `warehouse_id` | UUID (FK) | no | Parent Warehouse |
| `zone_code` | String (20) | no | Unique within the warehouse. Pattern: `Z-[A-Z0-9]+` (e.g., `Z-A`) |
| `name` | String (100) | no | |
| `zone_type` | enum `AMBIENT` / `CHILLED` / `FROZEN` / `RETURNS` / `BULK` / `PICK` | no | v1 fixed enum; extensible |
| `status` | enum | no | |
| (common fields) | | | |

### Invariants

- `(warehouse_id, zone_code)` unique
- `warehouse_id` immutable after creation
- Deactivation blocked if any active `Location` references this zone (local check)
- Parent `Warehouse` must be `ACTIVE` at zone creation time

### Relationships

- **One Warehouse : many Zones**
- **One Zone : many Locations**

---

## 3. Location

### Purpose

Physical storage position where inventory sits. Hierarchical but stored flat with
a single globally unique code.

### Fields

| Field | Type | Nullable | Notes |
|---|---|---|---|
| `id` | UUID | no | |
| `warehouse_id` | UUID (FK) | no | Denormalized for fast scoping |
| `zone_id` | UUID (FK) | no | Parent Zone |
| `location_code` | String (40) | no | **Globally unique** (W3). Pattern: `{WH}-{ZONE}-{AISLE}-{RACK}-{LEVEL}-{BIN}` e.g., `WH01-A-01-02-03` |
| `aisle` | String (10) | yes | Hierarchy component |
| `rack` | String (10) | yes | |
| `level` | String (10) | yes | |
| `bin` | String (10) | yes | |
| `location_type` | enum `STORAGE` / `STAGING_INBOUND` / `STAGING_OUTBOUND` / `DAMAGED` / `QUARANTINE` | no | v1 fixed enum |
| `capacity_units` | Integer | yes | Maximum EA this location can hold (null = unlimited) |
| `status` | enum | no | |
| (common fields) | | | |

### Invariants (wms.md W3, W6)

- `location_code` unique **across the entire system** (not just per warehouse) — W3
- Parent `Zone` must belong to `warehouse_id` (internal consistency)
- Parent `Zone` must be `ACTIVE` at location creation time
- `warehouse_id`, `zone_id`, `location_code` are **immutable** after creation
  (rationale: downstream services cache these; renaming breaks references)
- Deactivation blocked if any inventory references this location — v1 performs
  **local check only**, which always passes. Cross-service check via
  `location.deactivation.requested` event pattern deferred to v2. This is a known
  simplification (see `architecture.md` Open Items)
- Hierarchy fields (aisle/rack/level/bin) are advisory — uniqueness is enforced
  on `location_code` alone

### Relationships

- **One Zone : many Locations**
- Referenced by downstream `inventory-service` (inventory rows key on `location_id`)

---

## 4. SKU

### Purpose

Stock Keeping Unit. The minimum identifier for inventory tracking. Same physical
product in different color / size → different SKU.

### Fields

| Field | Type | Nullable | Notes |
|---|---|---|---|
| `id` | UUID | no | |
| `sku_code` | String (40) | no | Globally unique. Case-insensitive, stored uppercase. Immutable after create |
| `name` | String (200) | no | |
| `description` | String (1000) | yes | |
| `barcode` | String (40) | yes | EAN/UPC. If present, must be unique |
| `base_uom` | enum `EA` / `BOX` / `PLT` / `KG` / `L` | no | Fixed v1 enum |
| `tracking_type` | enum `NONE` / `LOT` | no | `LOT` = Lot-tracked (FIFO/FEFO). `SERIAL` deferred to v2 |
| `weight_grams` | Integer | yes | Advisory, for ops planning |
| `volume_ml` | Integer | yes | Advisory |
| `hazard_class` | String (20) | yes | Dangerous goods flag; operational |
| `shelf_life_days` | Integer | yes | Required iff `tracking_type = LOT` — drives default expiry |
| `status` | enum | no | |
| (common fields) | | | |

### Invariants

- `sku_code` globally unique, case-insensitive
- `sku_code` immutable after creation
- `barcode` unique if non-null (allows SKUs without barcode)
- If `tracking_type = LOT`, `shelf_life_days` should be set (warning — not hard error in v1)
- Changing `tracking_type` from `NONE` to `LOT` is forbidden once Lots or inventory
  exist for the SKU (v2: supported via migration workflow)
- `base_uom` is immutable after creation (downstream uses this to interpret quantities)
- Deactivation blocked if any `Lot` records exist under this SKU in `ACTIVE` status —
  local check. Cross-service inventory check deferred to v2

### Relationships

- **One SKU : many Lots** (only when `tracking_type = LOT`)
- Referenced by inbound / outbound / inventory services

---

## 5. Partner

### Purpose

External counterparty. Supplier for inbound (ASN), customer for outbound (order).

### Fields

| Field | Type | Nullable | Notes |
|---|---|---|---|
| `id` | UUID | no | |
| `partner_code` | String (20) | no | Globally unique. Immutable after create |
| `name` | String (200) | no | |
| `partner_type` | enum `SUPPLIER` / `CUSTOMER` / `BOTH` | no | |
| `business_number` | String (20) | yes | 사업자등록번호 or equivalent; operational reference only |
| `contact_name` | String (100) | yes | Operational contact (not consumer PII) |
| `contact_email` | String (200) | yes | Operational contact |
| `contact_phone` | String (30) | yes | Operational contact |
| `address` | String (300) | yes | |
| `status` | enum | no | |
| (common fields) | | | |

### Invariants

- `partner_code` globally unique
- `partner_code` immutable after creation
- Deactivation blocked if any `ACTIVE` Lots list this partner as supplier
  (v1: no such link; v2 may add `lot.supplier_partner_id`) — so v1 deactivation is
  essentially unrestricted at master-service level

### Relationships

- v1: no hard FKs from other master aggregates. ASN (inbound-service) references
  `partner_id` but is in another service's DB

### Privacy

Per `PROJECT.md` `data_sensitivity: internal`, Partner contacts are **operational
B2B data, not consumer PII**. Standard internal-data handling applies.

---

## 6. Lot

### Purpose

Identifies a specific manufactured batch of a SKU. Drives FEFO (First-Expiry-First-Out)
picking downstream.

### Fields

| Field | Type | Nullable | Notes |
|---|---|---|---|
| `id` | UUID | no | |
| `sku_id` | UUID (FK) | no | Parent SKU. SKU must have `tracking_type = LOT` |
| `lot_no` | String (40) | no | Unique **per SKU**. Typically vendor-assigned. Immutable after create |
| `manufactured_date` | LocalDate | yes | |
| `expiry_date` | LocalDate | yes | |
| `supplier_partner_id` | UUID (FK) | yes | Origin supplier; optional in v1 |
| `status` | enum `ACTIVE` / `INACTIVE` / `EXPIRED` | no | Additional state `EXPIRED` (see state machine below) |
| (common fields) | | | |

### Invariants

- `(sku_id, lot_no)` unique
- Parent SKU must exist and have `tracking_type = LOT`
- Parent SKU must be `ACTIVE` at lot creation time
- If both dates present, `expiry_date >= manufactured_date`
- `sku_id` and `lot_no` are immutable after creation
- Transition to `EXPIRED` is permitted at any time by a scheduled domain job when
  `expiry_date < today`. Once `EXPIRED`, cannot return to `ACTIVE`.

### Lot-Specific State Machine

```
        [create]
           |
           v
        ACTIVE ----[deactivate]----> INACTIVE
           |                              ^
       [expire]                     [reactivate]
           |                              |
           v                              |
       EXPIRED (terminal) <---[deactivate from EXPIRED]---
```

- `EXPIRED` is terminal for reactivation. Can still be deactivated to hide from listings.
- `EXPIRED` is set by a scheduled job, not by direct user action.

### Relationships

- **One SKU : many Lots** (only for `tracking_type = LOT` SKUs)
- v1 no hard FK to Partner; `supplier_partner_id` is a soft reference

### v1 Scope Limit

- Lot **balance** (how much stock of this lot in what location) is NOT in master-service.
  `inventory-service` owns that. master-service only owns Lot identity.
- Lot **allocation** during outbound picking is also in inventory / outbound services.

---

## Entity Relationship Diagram

```
Warehouse  1 ────── N  Zone  1 ────── N  Location
                                           │
                                           │ referenced by
                                           ▼
                               (inventory-service, not here)

SKU  1 ────── N  Lot
 │                │
 │                │ soft ref
 │                ▼
 │           Partner (supplier_partner_id, optional)
 │
 └─ referenced by (inventory / inbound / outbound)

Partner (standalone in master-service; referenced by inbound / outbound)
```

No cross-aggregate FKs except the parent-child hierarchy above. Partner has no
hard FK from any other master aggregate in v1.

---

## Aggregate Boundaries

Each of the six is its own aggregate root. Mutations cross aggregate boundaries
**only via events**, never within a single transaction (trait `transactional` T2).

Example: deactivating a Warehouse does **not** cascade-deactivate its Zones within
the same transaction. Instead, deactivation is blocked while active children exist
(local check). Cascade logic (if ever needed) would be modeled as a saga.

---

## Reference Data Snapshot (v1 Seed)

On first deployment, seed these minimum records to enable end-to-end testing:

- 1 Warehouse: `WH01` (`Asia/Seoul`)
- 3 Zones under `WH01`: `Z-A` (AMBIENT), `Z-B` (CHILLED), `Z-Q` (QUARANTINE)
- 9 Locations: 3 per zone, e.g., `WH01-A-01-01-01`, `WH01-A-01-01-02`, `WH01-A-01-01-03`
- 2 SKUs: one `tracking_type=NONE`, one `tracking_type=LOT`
- 2 Partners: one `SUPPLIER`, one `CUSTOMER`
- 1 Lot on the LOT-tracked SKU

Seeding strategy: Flyway migration `V99__seed_dev_data.sql`, only active under
Spring profile `dev` or `standalone`.

---

## Forbidden Patterns (in code)

- ❌ JPA entity used as domain model (Hexagonal rule)
- ❌ `UPDATE master_*` SQL bypassing aggregate domain methods (trait T4)
- ❌ Hard delete of Warehouse / Zone / Location / SKU / Partner / Lot in v1
- ❌ Cross-aggregate write in one transaction (except parent creation + event via outbox,
  which is single-aggregate)
- ❌ Changing immutable fields (`*_code`, `warehouse_id`, `zone_id`, `sku_id`, `base_uom`,
  `tracking_type` in general case)

---

## Open Items

- `specs/services/master-service/state-machines/lot-status.md` — Lot state machine
  diagram in its own file (optional; diagram above may suffice)
- `specs/services/master-service/idempotency.md` — idempotency key storage strategy
- `platform/error-handling.md` — register new codes `SKU_CODE_DUPLICATE`,
  `REFERENCE_INTEGRITY_VIOLATION`, `LOT_NO_DUPLICATE`, `LOT_EXPIRED`

---

## References

- `architecture.md` (this directory)
- `rules/domains/wms.md` — W1–W6 and domain terminology
- `rules/traits/transactional.md` — T1–T8
- `specs/contracts/http/master-service-api.md` — endpoint shapes
- `specs/contracts/events/master-events.md` — event payloads
