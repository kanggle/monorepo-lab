# procurement-service — Data Model

Schema source of truth: `apps/procurement-service/src/main/resources/db/migration/procurement/V1__init.sql` (Flyway baseline).

All tables carry `tenant_id VARCHAR(64) NOT NULL`. Indexes prefix `tenant_id`
to support a future multi-org extension without schema migration. Cross-tenant
queries are structurally impossible — every repository method embeds `tenant_id`
in `WHERE`.

> **Authoring note**: Created in coordination with [TASK-SCM-BE-006](../../../tasks/done/)
> (architecture.md retroactive) and [TASK-SCM-BE-007](../../../tasks/done/)
> (INT-001b schema fixes backfill).

---

## Tables

### suppliers

v1 internal master (v2 migrates to `supplier-service`).

| Column | Type | Notes |
|---|---|---|
| id | VARCHAR(36) PK | UUID v7 |
| tenant_id | VARCHAR(64) NOT NULL | always `scm` in v1 |
| name | VARCHAR(200) NOT NULL | |
| status | VARCHAR(20) NOT NULL | CHECK ∈ {`ACTIVE`, `INACTIVE`, `CONTRACT_EXPIRED`} |
| contract_started_at | TIMESTAMPTZ | nullable |
| contract_expires_at | TIMESTAMPTZ | nullable |
| contact_info | JSONB | nullable; mapped via `@JdbcTypeCode(SqlTypes.JSON)` on the JPA entity (see § Hibernate ↔ PostgreSQL JSONB note below) |
| created_at | TIMESTAMPTZ NOT NULL | |
| updated_at | TIMESTAMPTZ NOT NULL | |
| version | BIGINT NOT NULL DEFAULT 0 | optimistic lock counter (T7) |

Index: `idx_suppliers_tenant_status (tenant_id, status)`.

**Hibernate ↔ PostgreSQL JSONB note** (TASK-SCM-BE-002d fix):
`Supplier.contactInfoJson` is a `String` JPA field mapped to PostgreSQL `jsonb`.
Without `@JdbcTypeCode(SqlTypes.JSON)`, Hibernate 6 raises `42804
datatype_mismatch` at insert/update time. The annotation forces the JSON SQL
type code so the JDBC driver emits the correct PG cast.

```java
@JdbcTypeCode(SqlTypes.JSON)
@Column(name = "contact_info", columnDefinition = "jsonb")
private String contactInfoJson;
```

Sibling occurrence: `apps/inventory-visibility-service/.../jpa/InventoryNodeJpaEntity.contactInfo`
(TASK-SCM-INT-001b cycle 2 fix). When adding a new JSONB column anywhere in
scm-platform, copy this annotation pair as a unit.

---

### supplier_credentials

Column-level AES-GCM encryption per S6 (`SupplierCredentialsEncryptor`).

| Column | Type | Notes |
|---|---|---|
| supplier_id | VARCHAR(36) PK | FK → `suppliers.id` |
| tenant_id | VARCHAR(64) NOT NULL | denormalized for tenant-scoped queries |
| credential_type | VARCHAR(40) NOT NULL | e.g., `API_KEY`, `EDI_USER_PASS`, `SFTP_KEY` |
| encrypted_payload | BYTEA NOT NULL | layout: `[12-byte IV][ciphertext][16-byte AES-GCM tag]` |
| encryption_key_id | VARCHAR(40) NOT NULL | always `"v1"` in v1; vault-rotated key ids in v2 |
| rotated_at | TIMESTAMPTZ NOT NULL | |

FK: `fk_supplier_credentials_supplier (supplier_id) → suppliers (id)`.

---

### purchase_orders

Aggregate root for the PO lifecycle. State machine is enforced by
`PoStatusMachine` — see [`architecture.md`](./architecture.md) § PO State Machine.

| Column | Type | Notes |
|---|---|---|
| id | VARCHAR(36) PK | UUID v7 |
| tenant_id | VARCHAR(64) NOT NULL | |
| po_number | VARCHAR(40) NOT NULL | format = `"PO-" + UUID-v7-rand_b-tail-8-uppercase`; see § po_number format below |
| supplier_id | VARCHAR(36) NOT NULL | references `suppliers.id` (no FK declared — supplier may be in v2 supplier-service) |
| buyer_account_id | VARCHAR(36) NOT NULL | GAP `sub` claim of the actor who drafted the PO |
| status | VARCHAR(30) NOT NULL | CHECK ∈ {`DRAFT`, `SUBMITTED`, `ACKNOWLEDGED`, `CONFIRMED`, `PARTIALLY_RECEIVED`, `RECEIVED`, `SETTLED`, `CLOSED`, `CANCELED`} |
| total_amount | NUMERIC(18,2) NOT NULL | sum of `line.quantity × line.unit_price` |
| currency | VARCHAR(3) NOT NULL | ISO 4217 |
| submitted_at | TIMESTAMPTZ | nullable |
| acknowledged_at | TIMESTAMPTZ | nullable |
| confirmed_at | TIMESTAMPTZ | nullable |
| canceled_at | TIMESTAMPTZ | nullable |
| cancellation_reason | VARCHAR(200) | nullable |
| created_at | TIMESTAMPTZ NOT NULL | |
| updated_at | TIMESTAMPTZ NOT NULL | |
| version | BIGINT NOT NULL DEFAULT 0 | optimistic lock counter |

Constraints:
- UNIQUE `uq_purchase_orders_tenant_po_number (tenant_id, po_number)`
- CHECK `ck_purchase_orders_status` (enum above)

Indexes:
- `idx_po_tenant_status_created (tenant_id, status, created_at DESC)` — paginated search by status
- `idx_po_tenant_supplier_status (tenant_id, supplier_id, status)` — supplier filter

**`po_number` format** (TASK-SCM-INT-001b cycle 2 root cause fix, PR #262):

`po_number = "PO-" + uuidV7.substring(uuidV7.length() - 8).uppercase()`.

The suffix is the **last 8 hex chars of UUID v7** — equivalent to `substring(28)`
on the dash-included 36-char form. RFC 9562 § 5.7 specifies UUID v7's first
8 hex chars are a millisecond-resolution timestamp; two PO drafts in the same
millisecond (e.g., a buyer batching POs in a tight loop) share the prefix and
collide on the `(tenant_id, po_number)` UNIQUE constraint with PostgreSQL
`23505 unique_violation`. The fix uses the trailing 8 chars, which fall inside
the **`rand_b` field** (62 bits of pure random per RFC 9562 § 5.7) — collision
probability ≈ 1/(16^8) ≈ 2.3 × 10⁻¹⁰ per draft, statistically irrelevant under
realistic batch volumes.

```java
// PurchaseOrderApplicationService.draft
String poId = UuidV7.randomString();           // 36-char dash-form
String poNumber = "PO-" + poId.substring(poId.length() - 8).toUpperCase();
```

Original (broken) form used the **first 8 chars** of the UUID, which IS the
ms timestamp prefix and collided as soon as draft TPS exceeded one PO per ms.
Symptom in TASK-SCM-INT-001b cycle 2: `DataIntegrityViolationException`
intermittently on parallel-test fixtures.

---

### purchase_order_lines

Child entity of `PurchaseOrder`. Loaded/saved via dedicated JPA repository
(aggregate-internal collection on the domain side is `@Transient` to avoid
Hibernate lazy-init footguns).

| Column | Type | Notes |
|---|---|---|
| id | VARCHAR(36) PK | UUID v7 |
| po_id | VARCHAR(36) NOT NULL | FK → `purchase_orders.id` |
| tenant_id | VARCHAR(64) NOT NULL | denormalized |
| line_no | INT NOT NULL | unique within PO |
| sku | VARCHAR(100) NOT NULL | |
| supplier_sku | VARCHAR(100) | nullable |
| quantity | NUMERIC(18,4) NOT NULL | CHECK > 0 |
| unit_price | NUMERIC(18,2) NOT NULL | CHECK ≥ 0 |
| received_quantity | NUMERIC(18,4) NOT NULL DEFAULT 0 | accumulates ASN-applied amounts |

Constraints:
- FK `fk_pol_po (po_id) → purchase_orders (id)`
- UNIQUE `uq_pol_po_line (po_id, line_no)`
- CHECK `ck_pol_quantity_positive (quantity > 0)`
- CHECK `ck_pol_unit_price_non_negative (unit_price >= 0)`

Index: `idx_pol_tenant_po (tenant_id, po_id)`.

---

### po_status_history

Append-only audit trail (S7) for PO state transitions.

| Column | Type | Notes |
|---|---|---|
| id | BIGSERIAL PK | |
| po_id | VARCHAR(36) NOT NULL | references `purchase_orders.id` |
| tenant_id | VARCHAR(64) NOT NULL | |
| from_status | VARCHAR(30) NOT NULL | |
| to_status | VARCHAR(30) NOT NULL | |
| actor_account_id | VARCHAR(36) | nullable for SUPPLIER / SYSTEM transitions |
| actor_type | VARCHAR(20) NOT NULL | CHECK ∈ {`BUYER`, `OPERATOR`, `SUPPLIER`, `SYSTEM`} |
| reason | VARCHAR(200) | optional context (e.g., supplier ack ref, cancel reason) |
| occurred_at | TIMESTAMPTZ NOT NULL | |

Index: `idx_psh_tenant_po_occurred (tenant_id, po_id, occurred_at DESC)`.

Append-only — no UPDATE / DELETE in any code path.

---

### advance_shipment_notices

Supplier-issued ASN aggregate root.

| Column | Type | Notes |
|---|---|---|
| id | VARCHAR(36) PK | UUID v7 |
| tenant_id | VARCHAR(64) NOT NULL | |
| po_id | VARCHAR(36) NOT NULL | FK → `purchase_orders.id` |
| supplier_asn_ref | VARCHAR(100) NOT NULL | supplier's natural id; S2 idempotency key (see UNIQUE below) |
| expected_arrival_at | TIMESTAMPTZ NOT NULL | |
| received_at | TIMESTAMPTZ | nullable until ASN is applied |
| created_at | TIMESTAMPTZ NOT NULL | |
| version | BIGINT NOT NULL DEFAULT 0 | optimistic lock counter |

Constraints:
- FK `fk_asn_po (po_id) → purchase_orders (id)`
- UNIQUE `uq_asn_tenant_supplier_ref (tenant_id, supplier_asn_ref)` — **S2
  idempotency**: the same supplier_asn_ref under the same tenant is a
  duplicate webhook delivery; the application service short-circuits and
  returns the existing ASN.

Index: `idx_asn_tenant_po_expected (tenant_id, po_id, expected_arrival_at)`.

---

### asn_lines

| Column | Type | Notes |
|---|---|---|
| id | VARCHAR(36) PK | UUID v7 |
| asn_id | VARCHAR(36) NOT NULL | FK → `advance_shipment_notices.id` |
| tenant_id | VARCHAR(64) NOT NULL | |
| po_line_id | VARCHAR(36) NOT NULL | FK → `purchase_order_lines.id` |
| quantity_shipped | NUMERIC(18,4) NOT NULL | CHECK > 0 |
| quantity_received | NUMERIC(18,4) | nullable until inventory-side confirmation |

Constraints:
- FK `fk_asn_line_asn (asn_id) → advance_shipment_notices (id)`
- FK `fk_asn_line_pol (po_line_id) → purchase_order_lines (id)`
- CHECK `ck_asn_line_qty_positive (quantity_shipped > 0)`

Indexes:
- `idx_asn_lines_asn (asn_id)`
- `idx_asn_lines_tenant_pol (tenant_id, po_line_id)`

---

### audit_log

Cross-aggregate application-level audit trail (S7). Each command boundary
in `PurchaseOrderApplicationService` writes one row inside the same
transaction as the state change.

| Column | Type | Notes |
|---|---|---|
| id | BIGSERIAL PK | |
| tenant_id | VARCHAR(64) NOT NULL | |
| aggregate_type | VARCHAR(40) NOT NULL | `purchase_order`, `asn`, `supplier`, … |
| aggregate_id | VARCHAR(36) NOT NULL | |
| action | VARCHAR(40) NOT NULL | `DRAFT`, `SUBMIT`, `ACKNOWLEDGE`, `CONFIRM`, `CANCEL`, `RECEIVE`, … |
| actor_account_id | VARCHAR(36) | nullable for SUPPLIER / SYSTEM |
| actor_type | VARCHAR(20) NOT NULL | `BUYER` / `OPERATOR` / `SUPPLIER` / `SYSTEM` |
| before_state | JSONB | nullable on creation |
| after_state | JSONB | nullable on deletion (no v1 case) |
| occurred_at | TIMESTAMPTZ NOT NULL | |

Indexes:
- `idx_audit_aggregate_occurred (aggregate_type, aggregate_id, occurred_at DESC)` — forensic per-aggregate timeline
- `idx_audit_tenant_occurred (tenant_id, occurred_at DESC)` — tenant-scoped recent activity

Append-only — no UPDATE / DELETE.

---

### outbox

Schema mirrors `libs/java-messaging` `OutboxJpaEntity` exactly. Field names and
types MUST stay in sync with the library.

| Column | Type | Notes |
|---|---|---|
| id | BIGSERIAL PK | |
| aggregate_type | VARCHAR(100) NOT NULL | `purchase_order`, `asn` |
| aggregate_id | VARCHAR(255) NOT NULL | PO id or ASN id |
| event_type | VARCHAR(100) NOT NULL | matches `ProcurementEventPublisher.EVENT_*` constants |
| payload | TEXT NOT NULL | JSON-serialized envelope produced by `BaseEventPublisher` |
| created_at | TIMESTAMP NOT NULL | (no TZ — matches library schema) |
| published_at | TIMESTAMP | populated by polling scheduler on success |
| status | VARCHAR(20) NOT NULL | CHECK ∈ {`PENDING`, `PUBLISHED`, `FAILED`} |

Index: `idx_outbox_status_created_at (status, created_at)` — polling-loop scan target.

---

### processed_events

Schema mirrors `libs/java-messaging` `ProcessedEventJpaEntity` exactly. T8
inbound dedupe table (currently unused by procurement-service in v1 because
no Kafka consumers; the table exists for cross-service consistency and v2
ASN consumer addition).

| Column | Type | Notes |
|---|---|---|
| event_id | VARCHAR(100) PK | UUID v7 from inbound envelope |
| event_type | VARCHAR(100) NOT NULL | |
| processed_at | TIMESTAMP NOT NULL | |

---

### idempotency_keys

S2 idempotency cache fallback (Redis is the primary, this is the persistent
backstop when Redis is offline — Failure Mode #4 fail-CLOSED).

| Column | Type | Notes |
|---|---|---|
| idempotency_key | VARCHAR(80) NOT NULL | client-supplied header value |
| endpoint | VARCHAR(120) NOT NULL | e.g., `POST /api/procurement/po` |
| tenant_id | VARCHAR(64) NOT NULL | |
| payload_hash | VARCHAR(64) NOT NULL | SHA-256 of canonical request body |
| response_status | INT NOT NULL | replayed HTTP status |
| response_body | TEXT | replayed body, nullable |
| created_at | TIMESTAMPTZ NOT NULL | |
| expires_at | TIMESTAMPTZ NOT NULL | TTL purge target (no v1 cleanup job) |

Composite PK: `(idempotency_key, endpoint, tenant_id)`.
Index: `idx_idempotency_expires (expires_at)`.

---

## tenant_id Policy

All tables carry `tenant_id VARCHAR(64) NOT NULL`. Repository methods
**always** accept and embed `tenant_id` in `WHERE` — see § Multi-tenancy in
[`architecture.md`](./architecture.md). v1 always `scm`. The 3-layer
defense-in-depth (gateway → JWT validator → `TenantClaimEnforcer` filter)
ensures cross-tenant tokens never reach repository code.

---

## References

- `apps/procurement-service/src/main/resources/db/migration/procurement/V1__init.sql`
- [`architecture.md`](./architecture.md) — service-level invariants
- `rules/domains/scm.md` § Mandatory Rules (S1, S2, S6, S7)
- `rules/traits/transactional.md` (T1, T3, T7, T8)
- TASK-SCM-BE-002 — bootstrap PR #239 (initial migration)
- TASK-SCM-BE-002d — `Supplier.contactInfoJson` `@JdbcTypeCode` fix
- TASK-SCM-INT-001b cycle 2 — `poNumber` rand_b tail fix + `InventoryNode.contactInfo`
  `@JdbcTypeCode` (sibling fix in inventory-visibility-service)
- TASK-SCM-BE-006 — architecture.md retroactive
- TASK-SCM-BE-007 — this data-model.md authoring + INT-001b backfill
- RFC 9562 § 5.7 UUID Version 7 (rand_b field)
