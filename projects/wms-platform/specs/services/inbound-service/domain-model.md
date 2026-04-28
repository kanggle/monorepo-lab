# inbound-service — Domain Model

Domain model specification for `inbound-service`. Owned aggregates, fields,
relationships, invariants, and state transitions.

Read this after `specs/services/inbound-service/architecture.md`. The ASN
state machine, webhook reception pattern, and Hexagonal layout are declared
there and only restated here as far as needed to reason about the model.

---

## Scope

Four owned aggregates plus infrastructure-supporting records:

**Aggregates (owned by this service)**

1. **Asn** — root aggregate for the inbound receiving workflow; carries the full
   state machine
2. **Inspection** — inspection results per ASN; records pass / damage / short per
   line, and discrepancies
3. **PutawayInstruction** — planned destination assignments per line; confirmed
   per line by operator
4. **PutawayConfirmation** — per-line physical confirmation record; child of
   `PutawayInstruction`

**Infrastructure-supporting records**

5. **InboundOutbox** — transactional outbox row (T3)
6. **EventDedupe** — Kafka consumer-side dedupe table (T8)
7. **ErpWebhookInbox** — ingest buffer for ERP push (decouples reception from
   domain processing)
8. **ErpWebhookDedupe** — replay-protection for webhook event ids
9. **MasterReadModel** — local cache of Location / SKU / Lot / Partner snapshots

---

## Common Aggregate Shape

Every aggregate row carries:

| Field | Type | Notes |
|---|---|---|
| `id` | UUID | Surrogate PK |
| `version` | Long | Optimistic lock (T5). `SELECT FOR UPDATE` forbidden |
| `created_at` | Instant | UTC |
| `created_by` | String | JWT subject or `system:erp-webhook` |
| `updated_at` | Instant | UTC |
| `updated_by` | String | |

Outbox / EventDedupe / Webhook records are append-only ledgers. They do **not**
carry `version` or `updated_*`. DB-level role grants revoke `UPDATE` / `DELETE`
on these tables for the application role.

---

## 1. Asn (Aggregate Root)

### Purpose

The primary aggregate of this service. Represents an Advance Shipment Notice —
a supplier-issued pre-arrival of goods. Drives the full inbound lifecycle from
reception through putaway to close.

### Fields

| Field | Type | Nullable | Notes |
|---|---|---|---|
| `id` | UUID | no | |
| `asn_no` | String (40) | no | Business identifier. **Globally unique**. Pattern: `ASN-{YYYYMMDD}-{seq}`. Auto-generated for manual entry; ERP-assigned for webhook origin |
| `source` | enum `MANUAL` / `WEBHOOK_ERP` | no | Immutable after creation |
| `supplier_partner_id` | UUID | no | FK-by-id to master Partner. Must be `ACTIVE` and `partner_type` = `SUPPLIER` or `BOTH` at creation time |
| `warehouse_id` | UUID | no | Destination warehouse; immutable after creation |
| `expected_arrive_date` | LocalDate | yes | Supplier's stated arrival date |
| `notes` | String (1000) | yes | Free-text operational notes |
| `status` | enum | no | See state machine below |
| (common version / timestamp fields) | | | |

### AsnLine (child of Asn)

| Field | Type | Nullable | Notes |
|---|---|---|---|
| `id` | UUID | no | |
| `asn_id` | UUID (FK) | no | Parent ASN |
| `line_no` | Integer (>0) | no | 1-indexed. Unique within ASN |
| `sku_id` | UUID | no | FK-by-id to master SKU. Must be `ACTIVE` at ASN creation |
| `lot_id` | UUID | yes | FK-by-id to master Lot snapshot. Required iff SKU is LOT-tracked and lot is known at receiving-notice time; may be null if lot is assigned at inspection |
| `expected_qty` | Integer (>0) | no | EA |

AsnLine is immutable after the ASN transitions past `CREATED` (once inspection
starts, expected quantities may not be revised — discrepancies are recorded in
Inspection instead).

### Domain Methods (T4 — direct status UPDATE forbidden)

| Method | Allowed from | To |
|---|---|---|
| `startInspection()` | `CREATED` | `INSPECTING` |
| `completeInspection()` | `INSPECTING` | `INSPECTED` |
| `instructPutaway()` | `INSPECTED` | `IN_PUTAWAY` |
| `completePutaway()` | `IN_PUTAWAY` | `PUTAWAY_DONE` |
| `close()` | `PUTAWAY_DONE` | `CLOSED` |
| `cancel(reason)` | `CREATED`, `INSPECTING` | `CANCELLED` |

`STATE_TRANSITION_INVALID` thrown for any other transition.

### State Machine

```
  [receive (create)]
         |
         v
     CREATED ───[cancel]─────────────────────────────────────> CANCELLED (terminal)
         |
   [startInspection]
         |
         v
   INSPECTING ──[cancel]──────────────────────────────────────> CANCELLED (terminal)
         |
 [completeInspection]
         |
         v
   INSPECTED
         |
   [instructPutaway]
         |
         v
   IN_PUTAWAY
         |
  [completePutaway (all lines done)]
         |
         v
  PUTAWAY_DONE
         |
      [close]
         |
         v
     CLOSED (terminal)
```

- `CANCELLED` is terminal. Once any putaway has started (`IN_PUTAWAY` or beyond),
  cancellation is **forbidden** in v1 (`ASN_ALREADY_CLOSED`).
- `CLOSED` is terminal. No further mutations.

### Invariants

- `asn_no` unique across the system; assigned at creation, immutable.
- `supplier_partner_id` must resolve to a `ACTIVE` master Partner with
  `partner_type = SUPPLIER or BOTH` at creation time (validated via
  MasterReadModel); soft ref, no DB FK.
- At least one AsnLine is required.
- All AsnLines must have `ACTIVE` SKU status (per MasterReadModel) at ASN
  creation time.
- LOT-tracked SKUs may have `lot_id = null` at ASN creation (lot revealed at
  inspection dock); the invariant is enforced at inspection time.
- `warehouse_id` immutable after creation.
- AsnLines are immutable after `INSPECTING` state is entered.
- Transition to `INSPECTED` requires that **no Inspection discrepancy is
  unresolved** — see Inspection invariants (`INSPECTION_INCOMPLETE`).

### Relationships

- 1 Asn : N AsnLines
- 1 Asn : 0..1 Inspection (one per receiving event; v1 supports exactly one)
- 1 Asn : 0..1 PutawayInstruction
- Soft ref to master Partner, Location (via warehouse_id), SKU, Lot

---

## 2. Inspection

### Purpose

Records the physical inspection result for each ASN line at the receiving dock.
`qty_passed + qty_damaged + qty_short` may differ from `expected_qty` — the
difference is captured in `InspectionDiscrepancy`. Discrepancies do not
automatically block flow; an ops user may acknowledge them to unblock.

### Fields (Inspection root)

| Field | Type | Nullable | Notes |
|---|---|---|---|
| `id` | UUID | no | |
| `asn_id` | UUID (FK) | no | Parent ASN. 1:1 in v1 |
| `inspector_id` | String | no | Actor id of the inspecting operator |
| `completed_at` | Instant | yes | Set when `RecordInspectionUseCase` finalises the inspection |
| `notes` | String (1000) | yes | |
| (common version / timestamp fields) | | | |

### InspectionLine (child of Inspection)

| Field | Type | Nullable | Notes |
|---|---|---|---|
| `id` | UUID | no | |
| `inspection_id` | UUID (FK) | no | Parent Inspection |
| `asn_line_id` | UUID (FK) | no | The AsnLine being inspected |
| `sku_id` | UUID | no | Denormalized from AsnLine |
| `lot_id` | UUID | yes | May differ from AsnLine.lot_id if lot is first revealed at dock; must match an `ACTIVE` Lot in MasterReadModel |
| `lot_no` | String (40) | yes | Human-entered lot number when `lot_id` is null (e.g., batch not yet in master); carries forward to putaway |
| `qty_passed` | Integer (≥0) | no | Accepted for putaway |
| `qty_damaged` | Integer (≥0) | no | Physically damaged; directed to DAMAGED location |
| `qty_short` | Integer (≥0) | no | Did not arrive |

`qty_passed + qty_damaged + qty_short ≤ expected_qty` (any overcount is an
`INSPECTION_QUANTITY_MISMATCH`; exactly matching or under-counting is a
discrepancy, recorded in `InspectionDiscrepancy`).

### InspectionDiscrepancy (child of Inspection)

| Field | Type | Nullable | Notes |
|---|---|---|---|
| `id` | UUID | no | |
| `inspection_id` | UUID (FK) | no | |
| `asn_line_id` | UUID (FK) | no | |
| `discrepancy_type` | enum `QUANTITY_MISMATCH` / `LOT_MISMATCH` / `DAMAGE_EXCESS` | no | |
| `expected_qty` | Integer | no | From AsnLine |
| `actual_total_qty` | Integer | no | `passed + damaged + short` |
| `variance` | Integer | no | `actual_total_qty - expected_qty` (signed) |
| `acknowledged` | Boolean | no | Default `false`. Operator must set `true` to unblock ASN close |
| `acknowledged_by` | String | yes | |
| `acknowledged_at` | Instant | yes | |
| `notes` | String (500) | yes | Reason for acknowledgement |

### Invariants

- One InspectionLine per AsnLine (1:1 mapping, exactly).
- `qty_passed + qty_damaged + qty_short ≤ expected_qty` (hard error:
  `INSPECTION_QUANTITY_MISMATCH`).
- If sum is less than `expected_qty`, a `QUANTITY_MISMATCH` discrepancy is
  automatically created when the inspection is recorded.
- For LOT-tracked SKUs, `lot_id` must resolve to a valid, `ACTIVE` Lot per
  MasterReadModel, OR `lot_no` must be provided (lot reconciled ops-side; a
  background job may later link it to master). v1: `lot_id` null + `lot_no`
  provided is accepted. Error: `LOT_REQUIRED` if both null for a LOT-tracked SKU.
- ASN cannot transition to `INSPECTED` while any `InspectionDiscrepancy.acknowledged = false`
  (`INSPECTION_INCOMPLETE`).
- Once the Inspection is linked to an ASN in `IN_PUTAWAY` or later, the
  InspectionLine quantities are immutable.

### Relationships

- N : 1 to Asn (one Inspection per ASN in v1)
- 1 : N to InspectionLine (one per AsnLine)
- 1 : N to InspectionDiscrepancy (0..N, one per mismatched line)

---

## 3. PutawayInstruction

### Purpose

Planned destination assignments for each inspection-passed quantity. Created by
`InstructPutawayUseCase` after the ASN reaches `INSPECTED`. One
PutawayInstruction per ASN, with one line per destination-assignment.

### Fields (PutawayInstruction root)

| Field | Type | Nullable | Notes |
|---|---|---|---|
| `id` | UUID | no | |
| `asn_id` | UUID (FK) | no | 1:1 with ASN |
| `warehouse_id` | UUID | no | Denormalized |
| `planned_by` | String | no | Actor id of planning operator (or `system:auto-planner` in v2) |
| `status` | enum `PENDING` / `IN_PROGRESS` / `COMPLETED` / `PARTIALLY_COMPLETED` | no | See transition below |
| (common version / timestamp fields) | | | |

### PutawayLine (child of PutawayInstruction)

| Field | Type | Nullable | Notes |
|---|---|---|---|
| `id` | UUID | no | |
| `putaway_instruction_id` | UUID (FK) | no | Parent |
| `asn_line_id` | UUID (FK) | no | Source AsnLine (for traceability) |
| `sku_id` | UUID | no | Denormalized |
| `lot_id` | UUID | yes | Resolved lot (from InspectionLine, if any) |
| `lot_no` | String (40) | yes | Carried from InspectionLine if lot_id is null |
| `destination_location_id` | UUID | no | Planned target Location. Must be `ACTIVE` per MasterReadModel |
| `qty_to_putaway` | Integer (>0) | no | EA. ≤ `InspectionLine.qty_passed` for that line |
| `status` | enum `PENDING` / `CONFIRMED` / `SKIPPED` | no | See below |

### PutawayLine Status

```
PENDING ──[confirm]──> CONFIRMED (terminal)
PENDING ──[skip]────> SKIPPED (terminal; ops decision)
```

`CONFIRMED` → a `PutawayConfirmation` child record is created.
Once all lines are `CONFIRMED` or `SKIPPED` → `PutawayInstruction` → `COMPLETED`
(if any `SKIPPED` → `PARTIALLY_COMPLETED`).
`COMPLETED` / `PARTIALLY_COMPLETED` triggers `Asn.completePutaway()`.

### Invariants

- `PutawayInstruction` is 1:1 with ASN; cannot be created before ASN reaches
  `INSPECTED`.
- Sum of `qty_to_putaway` across lines for a given `(asn_line_id)` ≤
  `InspectionLine.qty_passed` for that line. Remainder is unallocated (creates
  a `SKIPPED` line or a partial instruction).
- `destination_location_id` must resolve to an `ACTIVE` Location in the same
  `warehouse_id` per MasterReadModel (`LOCATION_INACTIVE` / `WAREHOUSE_MISMATCH`).
- `warehouse_id` on PutawayInstruction must match the ASN's `warehouse_id`.
- `destination_location_id` for a normal SKU should be `location_type = STORAGE`;
  `DAMAGED` destination is allowed for damaged-qty putaway. v1: no hard constraint,
  advisory warning only.

### Relationships

- N : 1 to Asn
- 1 : N to PutawayLine
- Each PutawayLine : 0..1 PutawayConfirmation

---

## 4. PutawayConfirmation

### Purpose

The per-line physical confirmation record — written when an operator (or future
scanner adapter) confirms that a given quantity was placed at a location. This
record drives the `inbound.putaway.completed` event that credits stock in
`inventory-service`.

One `PutawayConfirmation` per confirmed `PutawayLine`.

### Fields

| Field | Type | Nullable | Notes |
|---|---|---|---|
| `id` | UUID | no | |
| `putaway_instruction_id` | UUID | no | Parent |
| `putaway_line_id` | UUID (FK) | no | The PutawayLine being confirmed |
| `sku_id` | UUID | no | Denormalized for event payload |
| `lot_id` | UUID | yes | Denormalized |
| `planned_location_id` | UUID | no | What was planned |
| `actual_location_id` | UUID | no | Where operator actually placed goods. May differ from planned (ops correction). Must be `ACTIVE` in MasterReadModel |
| `qty_confirmed` | Integer (>0) | no | EA. Must equal `putaway_line.qty_to_putaway` in v1 (partial confirmation is v2) |
| `confirmed_by` | String | no | Actor id |
| `confirmed_at` | Instant | no | |

PutawayConfirmation is **append-only**. No updates after creation.

### Event Output

Confirming the last pending PutawayLine fires `inbound.putaway.completed`
(via outbox) with:

```json
{
  "eventId": "<uuid>",
  "asnId": "<uuid>",
  "warehouseId": "<uuid>",
  "lines": [
    {
      "skuId": "<uuid>",
      "lotId": "<uuid|null>",
      "locationId": "<actual_location_id>",
      "qtyReceived": <qty_confirmed>
    }
  ]
}
```

This is the **cross-service contract** consumed by `inventory-service` to call
`ReceiveStockUseCase`. The event carries all lines of the instruction in one
envelope.

### Invariants

- `qty_confirmed == putaway_line.qty_to_putaway` (v1: no partial confirms).
- `actual_location_id` must be `ACTIVE` per MasterReadModel at confirmation time
  (`LOCATION_INACTIVE`).
- `actual_location_id.warehouse_id` must equal the instruction's `warehouse_id`
  (`WAREHOUSE_MISMATCH`).
- Duplicate confirmation attempt (same `putaway_line_id` confirmed twice) returns
  the cached idempotent result (idempotency-key on the confirming request).

---

## 5. InboundOutbox (infrastructure)

Per T3. Same single-table outbox pattern as siblings.

| Field | Type | Notes |
|---|---|---|
| `id` | UUID | PK |
| `aggregate_type` | String (40) | `ASN` / `INSPECTION` / `PUTAWAY_INSTRUCTION` |
| `aggregate_id` | UUID | The aggregate root's id |
| `event_type` | String (60) | `inbound.asn.received` / `.cancelled` / `inbound.inspection.completed` / `inbound.putaway.instructed` / `inbound.putaway.completed` / `inbound.asn.closed` |
| `event_version` | String (10) | `v1` |
| `payload` | JSONB | Event payload per `inbound-events.md` |
| `partition_key` | String (60) | `asn_id` |
| `created_at` | Instant | Same as enclosing TX commit |
| `published_at` | Instant | Set by Kafka publisher on success |

---

## 6. EventDedupe (infrastructure)

Per T8. For master-data Kafka consumers only (inbound-service does not consume
other business events).

| Field | Type | Notes |
|---|---|---|
| `event_id` | UUID | PK; from inbound Kafka header |
| `event_type` | String (60) | |
| `processed_at` | Instant | |
| `outcome` | enum `APPLIED` / `IGNORED_DUPLICATE` / `FAILED` | |

Retention: 30 days. No `version` / `updated_*`.

---

## 7. ErpWebhookInbox (infrastructure)

Decouples ERP webhook ingestion from domain processing. Webhook controller
persists the raw payload here immediately (fast ACK to ERP), a background
processor moves it to the domain.

| Field | Type | Notes |
|---|---|---|
| `event_id` | String (40) | PK; from `X-Erp-Event-Id` header |
| `raw_payload` | JSONB | Original body verbatim |
| `signature` | String (100) | Stored for audit; already verified |
| `received_at` | Instant | |
| `status` | enum `PENDING` / `APPLIED` / `FAILED` | Updated by background processor |
| `processed_at` | Instant | Nullable until processed |
| `failure_reason` | String (500) | Nullable; populated on `FAILED` |

---

## 8. ErpWebhookDedupe (infrastructure)

Replay-protection for ERP webhook event ids. Separate from `EventDedupe`
(different source, different lifecycle).

| Field | Type | Notes |
|---|---|---|
| `event_id` | String (40) | PK |
| `received_at` | Instant | |

Retention: 7 days (shorter than Kafka consumer dedupe; ERP retries window is
shorter).

---

## 9. MasterReadModel (local cache; read-only from this service's POV)

Populated by the `MasterLocationConsumer`, `MasterSkuConsumer`,
`MasterPartnerConsumer` from `master.*` events. Never written by REST or use-case
paths.

| Snapshot | Key fields | Critical for |
|---|---|---|
| `LocationSnapshot(id, code, warehouse_id, status, location_type, master_version)` | `id` | Putaway destination validation (`LOCATION_INACTIVE`, `WAREHOUSE_MISMATCH`) |
| `SkuSnapshot(id, sku_code, tracking_type, status, master_version)` | `id` | ASN creation validation, lot-tracking check (`SKU_INACTIVE`) |
| `LotSnapshot(id, sku_id, lot_no, expiry_date, status, master_version)` | `id` | Inspection lot resolution (`LOT_REQUIRED`) |
| `PartnerSnapshot(id, partner_code, partner_type, status, master_version)` | `id` | ASN supplier validation (`PARTNER_INVALID_TYPE`) |
| `WarehouseSnapshot(id, warehouse_code, status, master_version)` | `id` | Existence check |

`master_version`: out-of-order events with older version are dropped. Eventually
consistent — stale snapshot may briefly allow a deactivated resource through; W6
cross-service integrity is local-only in v1 (per master-service design).

---

## Entity Relationship Diagram

```
 Partner ──────────────────────┐
 (master, via snapshot)        │ supplier ref
                               ▼
Warehouse (master, via snap) ──> Asn 1 ───── N ──> AsnLine
                                  │                    │
                                  │ 1:1                │ 1:1
                                  ▼                    ▼
                              Inspection    InspectionLine
                                  │ 1:N
                                  ▼
                          InspectionDiscrepancy (0..N per Inspection)

                              Asn 1:1 PutawayInstruction
                                          │ 1:N
                                          ▼
                                     PutawayLine
                                          │ 1:0..1
                                          ▼
                                  PutawayConfirmation
                                   (append-only; fires putaway.completed event)

Location (master, via snap) ─── referenced by PutawayLine / PutawayConfirmation
SKU / Lot (master, via snap) ── referenced by AsnLine / InspectionLine / PutawayLine / PutawayConfirmation
```

---

## Aggregate Boundaries

| Aggregate root | Owns | Cross-aggregate via |
|---|---|---|
| Asn | AsnLines, ASN status lifecycle | events (`inbound.*`); Inspection and PutawayInstruction are separate aggregates that hold a FK back to Asn |
| Inspection | InspectionLines, InspectionDiscrepancies | none in v1 — created in response to `Asn.startInspection()` use-case, separate TX |
| PutawayInstruction | PutawayLines | events; triggers `Asn.completePutaway()` use-case when fully done (same service, one TX) |
| PutawayConfirmation | one per line; append-only | `inbound.putaway.completed` event payload; inventory-service acts on it |

A single `ConfirmPutawayUseCase` invocation: loads PutawayInstruction + Line,
creates PutawayConfirmation, updates Line status, possibly updates Instruction
status, possibly calls `Asn.completePutaway()` — **all in one `@Transactional`
boundary** (W1: no partial state). One outbox row per use-case call; if it
fires the final `putaway.completed` event, that row carries the full set of
confirmed lines.

---

## Forbidden Patterns (in code)

- ❌ JPA entity used as domain model — Hexagonal rule
- ❌ Direct `UPDATE asn SET status = ?` without domain method (T4)
- ❌ Cancellation of ASN in `IN_PUTAWAY` or later (`ASN_ALREADY_CLOSED`)
- ❌ Modifying AsnLine quantities after `INSPECTING` is entered
- ❌ Inspection line sums exceeding expected_qty (hard `INSPECTION_QUANTITY_MISMATCH`)
- ❌ Advancing ASN to `INSPECTED` while unacknowledged discrepancies exist
  (`INSPECTION_INCOMPLETE`)
- ❌ Putaway to a deactivated or wrong-warehouse Location (`LOCATION_INACTIVE`,
  `WAREHOUSE_MISMATCH`)
- ❌ Writing MasterReadModel from REST or use-case code paths (consumer-only write)
- ❌ Webhook domain logic in `ErpAsnWebhookController` — controller only validates
  signature / timestamp and writes to inbox; domain processing happens in the
  background processor
- ❌ Hard delete of any Asn / Inspection / PutawayInstruction row in v1

---

## Reference Data Snapshot (v1 Seed)

`inbound-service` contains no business seed data at first deployment. Dev /
standalone profile seeds:

- 1 ASN in `CLOSED` status, tied to master-service's seeded Supplier partner
  and seeded Location, for smoke-testing queries.
- MasterReadModel populated by replaying `master.*` topics from offset 0.

Strategy: Flyway `V99__seed_dev_data.sql`, active only under profile `dev` or
`standalone`.

---

## Open Items

- `specs/services/inbound-service/state-machines/asn-status.md` — ASN state
  machine diagram (standalone file; referenced in `architecture.md`)
- `specs/services/inbound-service/workflows/inbound-flow.md` — full lifecycle
  narrative (required artifact per `rules/domains/wms.md` §3)
- `specs/services/inbound-service/idempotency.md` — REST + webhook dual-layer
  dedupe strategy (architecture.md Open Items)
- `specs/services/inbound-service/external-integrations.md` — ERP webhook
  catalog (per `integration-heavy` Required Artifact 1)
- `platform/error-handling.md` — register codes not yet in catalog:
  `WAREHOUSE_MISMATCH`, `PUTAWAY_QUANTITY_EXCEEDED`, `PARTNER_INVALID_TYPE`,
  `LOT_REQUIRED`, `INSPECTION_INCOMPLETE`, `WEBHOOK_SIGNATURE_INVALID`,
  `WEBHOOK_REPLAY_DETECTED`, `WEBHOOK_TIMESTAMP_INVALID`

---

## References

- `architecture.md` (this directory)
- `rules/domains/wms.md` — Inbound bounded context, W1, W2, W6 and Standard
  Error Codes
- `rules/traits/transactional.md` — T1 (idempotency), T3 (outbox), T4 (no
  direct status), T5 (optimistic lock), T8 (eventId dedupe)
- `rules/traits/integration-heavy.md` — I1–I6, I10 (webhook reception pattern)
- `specs/services/master-service/domain-model.md` — Partner / Location / SKU /
  Lot identity; this service caches snapshots only
- `specs/services/inventory-service/architecture.md` — consumer of
  `inbound.putaway.completed` event; cross-service contract
- `specs/contracts/http/inbound-service-api.md` — REST endpoint shapes (Open Item)
- `specs/contracts/events/inbound-events.md` — published event payloads (Open Item)
- `specs/contracts/webhooks/erp-asn-webhook.md` — ERP webhook contract (Open Item)
