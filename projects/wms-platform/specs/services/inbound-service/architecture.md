# inbound-service — Architecture

This document declares the internal architecture of `inbound-service`.
All implementation tasks targeting this service must follow this declaration
and `platform/architecture-decision-rule.md`.

---

## Identity

| Field | Value |
|---|---|
| Service name | `inbound-service` |
| Service Type | `rest-api` (primary). Webhook receiver also rides on the REST surface |
| Architecture Style | **Hexagonal (Ports & Adapters)** |
| Primary language / stack | Java 21, Spring Boot |
| Bounded Context | **Inbound** (per `rules/domains/wms.md`) |
| Deployable unit | `apps/inbound-service/` |
| Data store | PostgreSQL (owned, not shared) |
| Event publication | Kafka via outbox (per trait `transactional`, rule T3) |
| Event consumption | Kafka with eventId-based dedupe (master snapshots only) |

---

## Responsibility

`inbound-service` owns the **inbound (receiving) workflow** end-to-end:

- **ASN (Advance Shipment Notice)** — supplier-issued notice of incoming goods.
  Received via ERP webhook or manual entry by ops staff.
- **Inspection** — quantity / quality / damage check of received goods at the
  receiving dock. Records mismatches.
- **Putaway** — instructed move of inspected goods to a destination location.
  On confirmation, fires the event that drives `inventory-service` to credit stock.

It is the **single system of record** for ASN identity, inspection results, and
putaway instructions. It does **not** own inventory quantities.

---

## Out of Scope

`inbound-service` does NOT own:

- Inventory quantities (owned by `inventory-service`)
- Order / picking / packing / shipping (owned by `outbound-service`)
- Master data identity (owned by `master-service`)
- ERP integration logic for outbound orders (owned by `outbound-service` adapter)
- Multi-leg cross-warehouse receiving (v1 scope: single warehouse per ASN)

Returns (RMA inbound) are deferred to v2 — they would create a second ASN
sub-type with a different validation flow.

---

## Architecture Style: Hexagonal

### Rationale

- WMS traits (`transactional`, `integration-heavy`) demand sharp separation between
  domain rules (ASN state machine, W2 movement-history append-only on putaway) and
  infrastructure (JPA, Kafka, ERP webhook adapter, scanner adapter).
- The **external integration surface is large**: ERP webhooks (incoming), Kafka
  outbound, future scanner / RFID adapters (incoming). Hexagonal forces all of
  these into adapter ports — domain stays clean.
- Uniform with `master-service`, `inventory-service`, `outbound-service`.

### Trade-off Accepted

- Boilerplate cost ~2× over Layered. Accepted because:
  1. ASN lifecycle has multiple state transitions (CREATED → INSPECTING → PUTAWAY
     → CLOSED) that benefit from explicit domain modelling (T4).
  2. Webhook signature / replay protection logic (I6) is purely adapter concern;
     keeping it out of domain is non-negotiable.

### Package Structure

Follow `.claude/skills/backend/architecture/hexagonal/SKILL.md` exactly.

```
com.wms.inbound/
├── adapter/
│   ├── in/
│   │   ├── rest/
│   │   │   ├── controller/      # AsnController, InspectionController, PutawayController
│   │   │   └── dto/{request,response}/
│   │   ├── webhook/
│   │   │   └── erp/             # ErpAsnWebhookController + signature/replay validators
│   │   └── messaging/
│   │       └── consumer/        # MasterLocationConsumer, MasterSkuConsumer, MasterPartnerConsumer
│   └── out/
│       ├── persistence/
│       │   ├── entity/          # JPA entities — package-private
│       │   ├── repository/
│       │   ├── mapper/
│       │   └── adapter/
│       ├── event/
│       │   ├── outbox/
│       │   └── publisher/
│       └── masterref/
│           └── readmodel/       # Local read-model store for master snapshots
├── application/
│   ├── port/
│   │   ├── in/                  # ReceiveAsnUseCase, RecordInspectionUseCase,
│   │   │                        #   InstructPutawayUseCase, ConfirmPutawayUseCase,
│   │   │                        #   CloseAsnUseCase, QueryAsnUseCase
│   │   └── out/                 # AsnPersistencePort, InspectionPersistencePort,
│   │                            #   PutawayPersistencePort, InboundEventPort,
│   │                            #   MasterReadModelPort, IdempotencyStorePort,
│   │                            #   EventDedupePort
│   ├── service/                 # Use-case implementations (@Service, @Transactional)
│   ├── command/                 # Input records
│   └── result/                  # Output records
├── domain/
│   ├── model/                   # Pure POJOs: Asn, AsnLine, Inspection, InspectionDiscrepancy,
│   │                            #   PutawayInstruction, PutawayConfirmation
│   ├── event/                   # AsnReceived, InspectionCompleted, PutawayInstructed,
│   │                            #   PutawayCompleted, AsnClosed
│   └── service/                 # Domain services (AsnStateMachine, InspectionReconciler,
│                                #   PutawayInstructionPlanner)
└── config/
```

### Layer Rules

Same five rules as `master-service` and `inventory-service`. Specifics for inbound:

1. Domain layer has no framework dependency. ASN state transitions are domain
   methods (`startInspection()`, `recordInspection()`, `instructPutaway()`,
   `confirmPutaway()`, `close()`).
2. Application layer's `@Transactional` boundary lives at the use-case service.
   Each command results in: load aggregate → mutate → persist → write outbox —
   one TX.
3. Webhook signature / replay validation is **adapter-only**. Domain receives a
   validated `ReceiveAsnCommand` only.
4. Inbound port grouping by lifecycle phase (receive / inspect / putaway / close).
5. Mappers package-private inside persistence adapter.

---

## Dependencies (Inbound)

| Caller | Contract | Purpose |
|---|---|---|
| `gateway-service` | `specs/contracts/http/inbound-service-api.md` | External UI — manual ASN entry, inspection recording, putaway confirmation |
| **External ERP** | `specs/contracts/webhooks/erp-asn-webhook.md` | Push of new ASNs (signed webhook) |
| `admin-service` | `inbound-service-api.md` | Read-only KPI / dashboards |
| `master-service` | Events on `wms.master.*.v1` topics | Master snapshot refresh |

`inbound-service` does NOT receive synchronous calls from `inventory-service` or
`outbound-service` in v1.

---

## Dependencies (Outbound)

v1 outbound dependencies:

- **PostgreSQL** — owned DB
- **Kafka** — event publication (via outbox), event consumption for master snapshots
- **Redis** — idempotency-key store and event-dedupe cache
- **External ERP** (webhook callback for ASN receipt acknowledgement) — optional, v1
  may skip and rely on poll-based reconciliation; documented in
  `external-integrations.md`

Future scanner / RFID integration adds a `ScannerInputPort` (inbound adapter) — not v1.

---

## Event Publication

All inbound state changes publish events via the **transactional outbox pattern**
(trait `transactional`, rule T3):

| Event | Topic | Trigger |
|---|---|---|
| `inbound.asn.received` | `wms.inbound.asn.received.v1` | New ASN created (webhook or manual) |
| `inbound.asn.cancelled` | `wms.inbound.asn.cancelled.v1` | ASN cancelled before inspection |
| `inbound.inspection.completed` | `wms.inbound.inspection.completed.v1` | Inspection finalised; carries discrepancy summary |
| `inbound.putaway.instructed` | `wms.inbound.putaway.instructed.v1` | Putaway instruction issued (operational signal) |
| `inbound.putaway.completed` | `wms.inbound.putaway.completed.v1` | **Critical**: drives `inventory-service` stock receipt |
| `inbound.asn.closed` | `wms.inbound.asn.closed.v1` | ASN finalised; no further mutation |

Full schemas: `specs/contracts/events/inbound-events.md` (Open Items).

> **Cross-service contract**: `inventory-service` consumes
> `wms.inbound.putaway.completed.v1` and credits `available` stock at the
> destination location for the SKU/Lot quantities listed. Topic name and event
> shape are jointly owned and any change requires an updated contract version
> (rolling consumers, etc.).

---

## Event Consumption

Per `service-types/event-consumer.md` and trait `transactional` rule T8:

| Subscribed Event | Source Topic | Effect |
|---|---|---|
| `master.warehouse.*` | `wms.master.warehouse.v1` | Refresh local read-model (cache name + status) |
| `master.zone.*` | `wms.master.zone.v1` | Same |
| `master.location.*` | `wms.master.location.v1` | Same; deactivated locations rejected as putaway destinations |
| `master.sku.*` | `wms.master.sku.v1` | Same; deactivated SKUs rejected on new ASN |
| `master.partner.*` | `wms.master.partner.v1` | Same; deactivated partners rejected as ASN supplier |

Same dedupe + DLQ pattern as `inventory-service`:

- `inbound_event_dedupe(event_id PK, event_type, processed_at, outcome)` — 30 days
- DLT topics with retry (3 attempts, exponential backoff + jitter — I3)
- Partition key = aggregate id (e.g., `location_id`, `sku_id`)

---

## Webhook Reception (integration-heavy)

ERP push of new ASNs flows through `ErpAsnWebhookController`:

### Validation (per `integration-heavy` rule I6)

1. **Signature verification** — HMAC-SHA256 over raw request body, secret loaded
   from configured Secret Manager. Reject with 401 on mismatch.
2. **Timestamp window** — `X-Erp-Timestamp` header within ±5 minutes of server
   time. Reject with 401 outside window (anti-replay).
3. **Replay-id dedupe** — `X-Erp-Event-Id` upserted into `erp_webhook_dedupe`
   table; duplicate → 200 OK with `{status: ignored_duplicate}` (idempotent
   replies are safe).
4. **Schema validation** — JSON schema against `webhooks/erp-asn-webhook.md`.
5. **Authorisation** — webhook source IP optionally restricted via gateway
   allowlist (operational, not enforced at app layer).

### Delivery Acknowledgement

Webhook responds 200 immediately after persisting the **inbound message**
(stored in a `webhook_inbox` table with `status=PENDING`). A background processor
moves messages through `PENDING → APPLIED` and emits `inbound.asn.received` via
outbox. This decouples ingestion latency from domain processing and avoids
holding the ERP HTTP connection for the full domain TX.

### Failure-mode Tests (I10)

- Bad signature → 401, no row written
- Stale timestamp → 401
- Duplicate event-id → 200 with `ignored_duplicate`, single domain effect
- Backend slow → ingest still fast; processor catches up
- Schema-invalid payload → 422 with reason

---

## Idempotency

### Synchronous (REST + Webhook)

- REST: `Idempotency-Key` header on POST/PUT/PATCH/DELETE. Storage Redis
  `inbound:idempotency:{key}`. TTL 24h. Scope `(key, method, path)`.
- Webhook: dual-layer dedupe — application-level `erp_webhook_dedupe` (event-id)
  AND domain-level `Idempotency-Key` (synthetic from `event_id` for webhook
  origin). The domain layer treats webhook and manual REST identically.

### Asynchronous (Kafka master events)

- `inbound_event_dedupe(event_id PK, ...)` as above.

Full strategy: `specs/services/inbound-service/idempotency.md` (Open Items).

---

## Concurrency Control

### Optimistic Locking (default)

`Asn`, `Inspection`, `PutawayInstruction` aggregates carry `version` (`@Version`).
Conflicts → HTTP 409 `CONFLICT`.

### State Machine (T4)

ASN lifecycle:

```
   [receive]
      |
      v
   CREATED ---[start_inspection]---> INSPECTING
                                        |
                                  [complete_inspection]
                                        |
                                        v
                                   INSPECTED ---[instruct_putaway]---> IN_PUTAWAY
                                                                            |
                                                                      [confirm_putaway (last line)]
                                                                            |
                                                                            v
                                                                        PUTAWAY_DONE ---[close]---> CLOSED (terminal)

   CREATED / INSPECTING ---[cancel]---> CANCELLED (terminal)
```

- Direct status `UPDATE` is forbidden — domain methods only (T4).
- `STATE_TRANSITION_INVALID` returned for disallowed transitions.
- `CANCELLED` allowed only before any putaway happens.

State diagram in: `specs/services/inbound-service/state-machines/asn-status.md`
(Open Items).

### Inspection Discrepancy Locking

Two operators inspecting the same ASN simultaneously: aggregate-level optimistic
lock on `Asn.version`. Last writer detects conflict and retries with merged view.

---

## Key Domain Invariants

Enforced at the domain layer; surfaced via codes from `rules/domains/wms.md` § Inbound:

| Invariant | Source | Error code |
|---|---|---|
| Cannot inspect a non-existent ASN | wms.md | `ASN_NOT_FOUND` |
| Cannot inspect / putaway / close a `CANCELLED` or `CLOSED` ASN | wms.md, T4 | `ASN_ALREADY_CLOSED` |
| Inspection quantity per line ≤ ASN expected quantity (or recorded as discrepancy) | wms.md | `INSPECTION_QUANTITY_MISMATCH` |
| Putaway destination must be `ACTIVE` (per local read-model) | wms.md W6 | `LOCATION_INACTIVE` |
| Putaway destination must be in same warehouse as ASN | derived | `WAREHOUSE_MISMATCH` |
| Putaway quantity ≤ inspection-passed quantity for that line | wms.md | `PUTAWAY_QUANTITY_EXCEEDED` |
| Cumulative putaway sum cannot exceed inspection total | derived | Same |
| Sum of putaway lines fits within destination `capacity_units` (advisory in v1; warning) | derived | warning, not block |
| ASN supplier (partner) must be `ACTIVE` and of type `SUPPLIER` or `BOTH` | wms.md W6 | `PARTNER_INVALID_TYPE` |
| SKU must be `ACTIVE` (per local read-model) | wms.md W6 | `SKU_INACTIVE` |
| LOT-tracked SKU putaway requires `lot_no` (and Lot must exist on `master-service` snapshot) | wms.md | `LOT_REQUIRED` |
| ASN cannot transition forward while any line has open discrepancy without resolution | derived | `INSPECTION_INCOMPLETE` |

---

## Inbound Workflow

Captured here at a glance; full document at
`specs/services/inbound-service/workflows/inbound-flow.md` (Open Items, per
`rules/domains/wms.md` Required Artifact 3).

```
1. ASN Received          → outbox: inbound.asn.received
   (webhook or manual)

2. Goods physically arrive at dock

3. Inspection            → outbox: inbound.inspection.completed
   - per line: quantity_passed, quantity_damaged, quantity_short
   - discrepancies recorded but do not block flow (ops may resolve later)

4. Putaway Instructed    → outbox: inbound.putaway.instructed
   - ops or auto-planner assigns destination location per line

5. Putaway Confirmed     → outbox: inbound.putaway.completed
   - per line: scanned destination + quantity
   - this event credits stock in inventory-service

6. ASN Closed            → outbox: inbound.asn.closed
   - all lines either putaway-complete or marked unresolvable
```

Each step is a single `@Transactional` use-case; no step crosses aggregate
boundaries.

---

## Persistence

- Database: PostgreSQL (one logical DB per service)
- Migrations: Flyway, `apps/inbound-service/src/main/resources/db/migration/`
- Outbox table: `inbound_outbox`
- Event dedupe table: `inbound_event_dedupe`
- Webhook inbox: `erp_webhook_inbox(event_id, raw_payload, signature, received_at, status, processed_at)`
- Webhook dedupe: `erp_webhook_dedupe(event_id PK, received_at)`

High-level table layout in `specs/services/inbound-service/domain-model.md`
(Open Items).

---

## Observability

- Metrics: standard REST + consumer + outbox lag (same set as inventory)
- Webhook-specific:
  - `inbound.webhook.received{result=accepted|signature_invalid|timestamp_invalid|duplicate}`
  - `inbound.webhook.processing.lag.seconds` — webhook receipt → domain applied
- Business metrics:
  - `inbound.asn.created.count{source=webhook|manual}`
  - `inbound.inspection.discrepancy.rate` — discrepancies per inspection
  - `inbound.putaway.completed.count`
  - `inbound.asn.cycle.time.seconds` — receipt-to-close p50/p95/p99

---

## Security

- Roles (v1):
  - `INBOUND_READ` — GET endpoints
  - `INBOUND_WRITE` — manual ASN creation, inspection recording, putaway confirmation
  - `INBOUND_ADMIN` — ASN cancellation, force-close, override discrepancy
- Webhook endpoint is anonymous (signature replaces JWT). It is **not** routed
  through `gateway-service`'s JWT enforcement; instead it has its own dedicated
  route with HMAC validation.
- Webhook secrets per ERP environment loaded from Secret Manager (env-var
  fallback only in `dev` profile).

No PII stored. Supplier contact data inherited from master-service is operational.

---

## Testing Requirements

### Unit (Domain)
- Every state transition; every invariant per the table above
- Inspection arithmetic: pass/short/damaged sums vs ASN expected
- Putaway cumulative-quantity invariant

### Application Service (port fakes)
- Happy path + every domain error per use-case
- Outbox row written in same TX
- Idempotency: same key returns cached result

### Persistence Adapter (Testcontainers Postgres)
- All repo methods
- Webhook inbox + dedupe table behavior

### REST Controllers (`@WebMvcTest`)
- All endpoints in `inbound-service-api.md`

### Webhook Controller (Integration with WireMock for outgoing ack — I10)
- Valid signature → 200, row in inbox
- Invalid signature → 401
- Stale timestamp → 401
- Duplicate event-id → 200 + `ignored_duplicate`
- Schema-invalid payload → 422
- Domain processor consumes inbox row → outbox event written

### Consumers (Testcontainers Kafka)
- Each master event consumer: happy path, dedupe-hit, poison → DLT

### Contract Tests
- All endpoints in `inbound-service-api.md`
- All published event schemas
- Webhook contract per `webhooks/erp-asn-webhook.md`

### Failure-mode (per trait `transactional` Required Artifact 5)
- Same `Idempotency-Key` POST twice → identical result, single mutation
- Same webhook event-id → single ASN created
- Inspection conflict (two operators) → second gets `CONFLICT`
- Putaway destination deactivated mid-flow → `LOCATION_INACTIVE`

---

## Saga Participation

`inbound-service` is **not a saga orchestrator** in v1. It is a single
self-contained workflow that emits authoritative events (`inbound.putaway.completed`)
which other services react to. No compensation flows owned here in v1.

The "ASN cancellation after inventory has been credited" case is **forbidden**
in v1 (`ASN_ALREADY_CLOSED` once putaway has begun). v2 may introduce a reverse
saga.

---

## Extensibility Notes

- **Returns / RMA inbound** — new ASN sub-type with reverse-flow validation. Not v1.
- **Auto-putaway planner** — domain service that suggests destination locations
  based on velocity, capacity, FEFO. v1 puts manual instruction; v2 may add this
  behind a `PutawayPlannerPort`.
- **Scanner / RFID integration** — adds `ScannerInputPort` inbound adapter that
  triggers `confirmPutaway` use-case directly. v1 uses REST only.
- **ERP outbound ack** — push receipt confirmation back to ERP. v1 may skip;
  ERP polls our `GET /asns?status=CLOSED` instead.

---

## Open Items (Before First Implementation Task)

These must be completed before any `TASK-BE-*` targeting `inbound-service` is
moved to `tasks/ready/`:

1. `specs/services/inbound-service/domain-model.md` — entities, fields,
   relationships, invariants, state per entity (ASN, AsnLine, Inspection,
   InspectionDiscrepancy, PutawayInstruction, PutawayConfirmation)
2. `specs/contracts/http/inbound-service-api.md` — REST endpoints
3. `specs/contracts/webhooks/erp-asn-webhook.md` — webhook signature, headers,
   body schema, dedupe contract
4. `specs/contracts/events/inbound-events.md` — published event schemas
5. `specs/services/inbound-service/idempotency.md` — REST + webhook + event-dedupe
6. `specs/services/inbound-service/external-integrations.md` — per
   `integration-heavy` Required Artifact 1 (ERP catalog: timeouts, circuit
   breakers, retry policy)
7. `specs/services/inbound-service/workflows/inbound-flow.md` — per
   `rules/domains/wms.md` Required Artifact 3 (ASN → Inspection → Putaway → Close)
8. `specs/services/inbound-service/state-machines/asn-status.md` — ASN state machine
9. Register new error codes in `platform/error-handling.md`:
   `WAREHOUSE_MISMATCH`, `PUTAWAY_QUANTITY_EXCEEDED`, `PARTNER_INVALID_TYPE`,
   `LOT_REQUIRED`, `INSPECTION_INCOMPLETE`, `WEBHOOK_SIGNATURE_INVALID`,
   `WEBHOOK_REPLAY_DETECTED`, `WEBHOOK_TIMESTAMP_INVALID`
10. Add a gateway route for `inbound-service` (REST) and a separate HMAC-only
    route for `/webhooks/erp/asn` in `gateway-service`

---

## References

- `CLAUDE.md`, `PROJECT.md`
- `rules/domains/wms.md` — Inbound bounded context, W1, W2, W6
- `rules/traits/transactional.md` — T1–T8 (especially T1, T3, T4, T5, T8)
- `rules/traits/integration-heavy.md` — I1–I10 (especially I1, I2, I5, I6, I7, I10)
- `platform/architecture-decision-rule.md`
- `platform/service-types/rest-api.md`
- `specs/services/master-service/architecture.md` — sibling reference pattern
- `specs/services/inventory-service/architecture.md` — counterpart on putaway-completed contract
- `.claude/skills/backend/architecture/hexagonal/SKILL.md`
