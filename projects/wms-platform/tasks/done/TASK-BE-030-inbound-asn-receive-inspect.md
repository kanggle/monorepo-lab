# Task ID

TASK-BE-030

# Title

inbound-service — ASN receive + inspect domain: domain model, use cases, persistence, REST, outbox publisher, webhook inbox processor

# Status

ready

# Owner

backend

# Task Tags

- code
- event
- test

---

# Required Sections (must exist)

- Goal
- Scope (in/out)
- Acceptance Criteria
- Related Specs
- Related Contracts
- Edge Cases
- Failure Scenarios

---

# Goal

Implement the first domain vertical slice of `inbound-service`: the ASN receive
and inspect phase. This covers the full path from a new ASN arriving (either via
ERP webhook or manual operator entry), through physical inspection at the dock,
to the ASN reaching `INSPECTED` status and publishing `inbound.inspection.completed`.

Prerequisite: TASK-BE-029 (bootstrap — skeleton, consumers, webhook controller,
Redis idempotency store) is **DONE**. Domain tables V2–V3 (asn, asn_line,
inspection, inspection_line, inspection_discrepancy) and V5 (outbox) are
already in the Flyway migration set and must NOT be modified unless a spec
change is required first.

After this task:

- Operators can create ASNs manually via `POST /api/v1/inbound/asns`.
- ERP webhook payloads already in `erp_webhook_inbox` (from the bootstrap
  stub) are processed into real ASN records.
- Operators can start and record inspections; discrepancies are auto-raised and
  must be acknowledged before the ASN can advance.
- The transactional outbox publishes `inbound.asn.received`,
  `inbound.asn.cancelled`, and `inbound.inspection.completed` to Kafka.
- 100 % of the domain invariants for the receive/inspect phase are enforced and
  covered by tests.

---

# Scope

## In

### Domain layer (`domain/`)

- `AsnStatus` enum: `CREATED | INSPECTING | INSPECTED | IN_PUTAWAY | PUTAWAY_DONE | CLOSED | CANCELLED`
- `AsnSource` enum: `MANUAL | WEBHOOK_ERP`
- `Asn` (aggregate root POJO):
  - Fields per `domain-model.md §1` (id, asnNo, source, supplierPartnerId,
    warehouseId, expectedArriveDate, notes, status, version, timestamps)
  - Domain methods: `startInspection()`, `completeInspection()`, `cancel(reason)` —
    throw `StateTransitionInvalidException` when guard fails (T4)
  - Immutable `warehouse_id` and `source` after creation
- `AsnLine` (child POJO): id, asnId, lineNo, skuId, lotId (nullable), expectedQty
  — immutable once Asn enters `INSPECTING`
- `Inspection` (aggregate root POJO): id, asnId, inspectorId, completedAt, notes, version, timestamps
- `InspectionLine` (child POJO): id, inspectionId, asnLineId, skuId, lotId,
  lotNo, qtyPassed, qtyDamaged, qtyShort
- `InspectionDiscrepancy` (child POJO): id, inspectionId, asnLineId,
  discrepancyType enum (`QUANTITY_MISMATCH | LOT_MISMATCH | DAMAGE_EXCESS`),
  expectedQty, actualTotalQty, variance, acknowledged, acknowledgedBy,
  acknowledgedAt, notes
- Domain events (record classes, no framework dependency):
  `AsnReceivedEvent`, `AsnCancelledEvent`, `InspectionCompletedEvent` — matching
  payload shapes in `inbound-events.md §1–§3`

### Application layer (`application/`)

**In-ports** (`port/in/`):
- `ReceiveAsnUseCase` — create ASN (manual entry); also used by webhook processor
- `StartInspectionUseCase` — transition CREATED → INSPECTING
- `RecordInspectionUseCase` — submit all inspection lines; transition INSPECTING → INSPECTED
- `AcknowledgeDiscrepancyUseCase` — mark one discrepancy acknowledged
- `CancelAsnUseCase` — transition CREATED|INSPECTING → CANCELLED
- `QueryAsnUseCase` — GET single + paginated list with filters

**Out-ports** (`port/out/`) — new ones required by this task:
- `AsnPersistencePort` — save / load Asn + AsnLines; find by id / asnNo / filters
- `InspectionPersistencePort` — save / load Inspection + Lines + Discrepancies;
  find by id / asnId; update discrepancy acknowledged state
- `InboundEventPort` — write one outbox row per domain event in the same TX;
  signature: `void publish(InboundDomainEvent event, UUID aggregateId, String aggregateType)`
- `AsnNoSequencePort` — generate next `ASN-{YYYYMMDD}-{seq}` if caller passes
  null; sequence scoped per calendar day

**Commands** (`command/`): `ReceiveAsnCommand` (asnNo nullable for auto-gen,
source, supplierPartnerId, warehouseId, expectedArriveDate, notes, lines, actorId,
callerRoles), `StartInspectionCommand`, `RecordInspectionCommand` (lines: asnLineId,
qtyPassed, qtyDamaged, qtyShort, lotId, lotNo), `AcknowledgeDiscrepancyCommand`,
`CancelAsnCommand` (reason, version, actorId, callerRoles)

**Results** (`result/`): `AsnResult` (full detail incl. lines), `AsnSummaryResult`
(list view — no lines), `InspectionResult` (incl. lines + discrepancies)

**Services** (`service/`):
- `ReceiveAsnService` — validates supplier (ACTIVE, SUPPLIER|BOTH), warehouse
  (ACTIVE), SKU (ACTIVE per line), lot consistency; generates asnNo if null;
  persists Asn + AsnLines + outbox row for `inbound.asn.received` in one TX;
  role check: `INBOUND_WRITE`
- `InspectionService` — `startInspection`: loads Asn, calls `startInspection()`,
  persists + no outbox; `recordInspection`: creates Inspection + InspectionLines,
  auto-creates discrepancies for lines where sum < expected or damaged > 0 or
  short > 0, validates LOT-tracked lines, calls `completeInspection()` on Asn,
  writes outbox `inbound.inspection.completed` in one TX; role check: `INBOUND_WRITE`
- `AcknowledgeDiscrepancyService` — loads InspectionDiscrepancy, sets acknowledged;
  idempotent (already-ack'd returns cached); role check: `INBOUND_ADMIN`
- `CancelAsnService` — loads Asn, calls `cancel()`, persists, writes outbox
  `inbound.asn.cancelled` in one TX; version check (optimistic lock); role check:
  `INBOUND_ADMIN`
- `AsnQueryService` — paginated list with filters; GET by id

### Outbox publisher (`adapter/out/messaging/`)

- `OutboxWriterAdapter` — replaces existing stub; implements `InboundEventPort`;
  builds JSON envelope per `inbound-events.md` global envelope schema using
  `InboundEventEnvelopeSerializer`; writes to `inbound_outbox` table via JPA in
  the caller's TX (`Propagation.MANDATORY`)
- `InboundEventEnvelopeSerializer` — builds envelope + per-event `payload` JSON
  matching contracts §1–§3; UUIDv7 eventId; `occurredAt` = `Instant.now()` at
  write time; `producer = "inbound-service"`
- `OutboxPublisher` — `@Scheduled(fixedDelayString = "${inbound.outbox.poll-ms:1000}")`
  query unpublished rows → send to Kafka topic → mark `published_at`; exponential
  backoff + max attempts; emit metrics: `inbound.outbox.pending.count`,
  `inbound.outbox.lag.seconds`, `inbound.outbox.publish.failure.total`
  (same pattern as `inventory-service`'s `OutboxPublisher`)

### Webhook inbox processor (`application/service/`)

- `ErpWebhookInboxProcessor` — full implementation replacing the existing stub:
  - `@Scheduled(fixedDelayString = "${inbound.webhook.poll-ms:500}")` — polls
    `erp_webhook_inbox` for `status = PENDING` rows (batch, ordered by
    `received_at ASC`)
  - Maps each row's `raw_payload` JSON → `ReceiveAsnCommand` (source = `WEBHOOK_ERP`,
    actorId = `system:erp-webhook`)
  - Calls `ReceiveAsnService.receive(command)` — entire domain TX (Asn + outbox)
    in one boundary
  - On success: updates row `status = APPLIED`, `processed_at = now()`
  - On domain error (e.g., `PARTNER_INVALID_TYPE`, `SKU_INACTIVE`): updates row
    `status = FAILED`, `failure_reason = error code`; does NOT re-throw (processor
    continues to next row)
  - On unexpected exception: marks `FAILED`; logs at ERROR level; continues

### Persistence adapters (`adapter/out/persistence/`)

JPA entities + repositories + adapters for:
- `AsnJpaEntity` + `AsnLineJpaEntity` → `asn` + `asn_line` tables (V2 migration)
- `InspectionJpaEntity` + `InspectionLineJpaEntity` +
  `InspectionDiscrepancyJpaEntity` → `inspection*` tables (V3 migration)
- Mappers (package-private within adapter package, same pattern as master-service)
- `AsnPersistenceAdapter` implements `AsnPersistencePort` + `AsnNoSequencePort`
- `InspectionPersistenceAdapter` implements `InspectionPersistencePort`

The existing `OutboxWriterAdapter` stub in `adapter/out/messaging/` is replaced (not
moved — same class name, same package).

### REST adapters (`adapter/in/rest/`)

`AsnController` (`@RestController`, `@RequestMapping("/api/v1/inbound/asns")`):
- `POST /` — `ReceiveAsnUseCase`; requires `Idempotency-Key`; returns 201
- `GET /{id}` — `QueryAsnUseCase`; returns 200 + `ETag: "v{version}"`
- `GET /` — paginated list with query params per API contract §1.3
- `POST /{id}:cancel` — `CancelAsnUseCase`; requires `Idempotency-Key`; returns 200

`InspectionController` (`@RestController`, `@RequestMapping`):
- `POST /api/v1/inbound/asns/{asnId}/inspection:start` — `StartInspectionUseCase`
- `POST /api/v1/inbound/asns/{asnId}/inspection` — `RecordInspectionUseCase`; returns 201
- `POST /api/v1/inbound/inspections/{id}/discrepancies/{discrepancyId}:acknowledge`
  — `AcknowledgeDiscrepancyUseCase`; requires `Idempotency-Key`
- `GET /api/v1/inbound/inspections/{id}` — returns 200 + `ETag`
- `GET /api/v1/inbound/asns/{asnId}/inspection` — returns 200

DTOs: `CreateAsnRequest`, `CancelAsnRequest`, `RecordInspectionRequest`,
`AcknowledgeDiscrepancyRequest`, `AsnResponse`, `AsnSummaryResponse`,
`InspectionResponse` — matching API contract §1–§2.

Exception → HTTP mapping additions to `GlobalExceptionHandler`:
- `AsnNotFoundException` → 404 `ASN_NOT_FOUND`
- `InspectionNotFoundException` → 404 `INSPECTION_NOT_FOUND`
- `AsnNoDuplicateException` → 409 `ASN_NO_DUPLICATE`
- `StateTransitionInvalidException` → 422 `STATE_TRANSITION_INVALID`
- `AsnAlreadyClosedException` → 422 `ASN_ALREADY_CLOSED`
- `InspectionQuantityMismatchException` → 422 `INSPECTION_QUANTITY_MISMATCH`
- `InspectionIncompleteException` → 422 `INSPECTION_INCOMPLETE`
- `PartnerInvalidTypeException` → 422 `PARTNER_INVALID_TYPE`
- `SkuInactiveException` → 422 `SKU_INACTIVE`
- `LotRequiredException` → 422 `LOT_REQUIRED`
- `WarehouseNotFoundInReadModelException` → 422 `WAREHOUSE_NOT_FOUND`

### Tests

**Unit — domain**:
- `AsnTest`: every state transition (allowed + forbidden); `cancel()` invariants;
  immutability of warehouse_id / source
- `InspectionTest`: quantity arithmetic (passed+damaged+short = expected, under,
  over); discrepancy auto-creation; `INSPECTION_INCOMPLETE` guard; LOT-tracking
  invariants

**Unit — application service** (port fakes):
- `ReceiveAsnServiceTest`: happy path (manual), asnNo auto-gen, supplier inactive
  → `PARTNER_INVALID_TYPE`, SKU inactive → `SKU_INACTIVE`, duplicate asnNo →
  `ASN_NO_DUPLICATE`, outbox row written
- `InspectionServiceTest` (start + record): happy path, wrong ASN status,
  quantity mismatch, unresolved discrepancies block → `INSPECTION_INCOMPLETE`,
  LOT-required failure
- `AcknowledgeDiscrepancyServiceTest`: happy path, idempotent re-ack, already-ack
- `CancelAsnServiceTest`: from CREATED, from INSPECTING, from IN_PUTAWAY →
  `ASN_ALREADY_CLOSED`, version conflict → `CONFLICT`
- `ErpWebhookInboxProcessorTest`: happy path PENDING→APPLIED, domain error →
  FAILED row, unexpected exception → FAILED row, processor continues after failure

**Persistence** (Testcontainers Postgres):
- `AsnPersistenceAdapterTest`: save + load by id, load by asnNo, paginated list
  with filters, optimistic lock conflict
- `InspectionPersistenceAdapterTest`: save + load by id, load by asnId, update
  discrepancy acknowledged state

**REST** (`@WebMvcTest`):
- `AsnControllerTest`: POST 201, GET 200, list 200 with filters, cancel 200,
  cancel 422 (wrong status), cancel 409 (conflict), missing Idempotency-Key → 400
- `InspectionControllerTest`: start 200, record 201, acknowledge 200, GET 200,
  GET-by-asn 200, validation errors per contract

**Outbox publisher** (integration, Testcontainers Kafka + Postgres):
- Unpublished outbox rows → published to correct topic; `published_at` set;
  partition key = `asnId`
- Kafka unavailable → rows stay PENDING; metrics emitted

**Webhook processor** (integration, Testcontainers Postgres):
- PENDING row → ReceiveAsnService → Asn created + APPLIED
- Domain failure → FAILED with failure_reason populated
- Multiple PENDING rows processed in order

**Failure-mode** (per trait transactional Required Artifact 5):
- Same `Idempotency-Key` POST twice → identical result, single Asn row
- Same ERP webhook event-id (erp_webhook_dedupe) → single Asn created
- Two operators start inspection simultaneously → second gets 409 `CONFLICT`

## Out

- Putaway domain (PutawayInstruction / PutawayLine / PutawayConfirmation) — TASK-BE-031
- `POST /asns/{id}:close` endpoint — TASK-BE-031
- Putaway REST controllers (`/putaway/...`) — TASK-BE-031
- Events: `inbound.putaway.instructed`, `inbound.putaway.completed`,
  `inbound.asn.closed` — TASK-BE-031
- Webhook inbox admin REST endpoints (`GET /webhooks/inbox`) — TASK-BE-031
- `V99__seed_dev_data.sql` — separate task or TASK-BE-031
- `inbound-service` integration test (live-pair with gateway) — future INT task

---

# Acceptance Criteria

1. `./gradlew :apps:inbound-service:test` passes with zero failures; no skipped
   tests except those explicitly annotated with a reason.
2. `./gradlew :apps:inbound-service:check` passes (Checkstyle, SpotBugs if
   configured, compilation).
3. `Asn` domain POJO has no Spring or JPA imports; same for `Inspection`,
   `InspectionLine`, `InspectionDiscrepancy`.
4. Every state transition from the architecture state machine is covered by a
   unit test — both allowed and forbidden paths.
5. `ReceiveAsnService` calls `InboundEventPort.publish()` in the same
   `@Transactional` method that saves the `Asn`; no separate TX for the outbox
   write (T3).
6. `OutboxWriterAdapter` uses `Propagation.MANDATORY` — fails fast if called
   outside a TX.
7. `OutboxPublisher` emits `inbound.outbox.pending.count` gauge and
   `inbound.outbox.publish.failure.total` counter per the observability spec.
8. `ErpWebhookInboxProcessor` marks rows `APPLIED` on success and `FAILED`
   (with `failure_reason`) on domain or unexpected error; never loses a row;
   resumes from the PENDING rows on next poll.
9. `AsnController` and `InspectionController` enforce role requirements per
   the API contract (roles propagated through command records and checked in
   services — **not** in controllers, per BE-028 pattern).
10. `Idempotency-Key` absent on POST/DELETE endpoints → 400 `VALIDATION_ERROR`.
11. `AsnPersistenceAdapterTest` covers optimistic lock conflict path
    (`ObjectOptimisticLockingFailureException` → `AsnConflictException`).
12. `InspectionService.recordInspection()` auto-creates `InspectionDiscrepancy`
    rows for any line where `qty_passed + qty_damaged + qty_short < expected_qty`
    or `qty_damaged > 0` or `qty_short > 0`; `acknowledged = false`; unit test
    verifies auto-creation with correct `variance`.
13. `RecordInspectionUseCase` transitions ASN `INSPECTING → INSPECTED` only when
    there are zero unacknowledged discrepancies after recording; otherwise sets
    status to `INSPECTING` (not terminal — operator must ack discrepancies before
    the state advances).
    - Clarification: `recordInspection` always creates the Inspection entity and
      auto-raises discrepancies. If any are unacknowledged, the Asn remains
      `INSPECTING` and the response includes the discrepancy list. The Asn
      transitions to `INSPECTED` only if all discrepancies are acknowledged in the
      same call (zero-discrepancy case) or if `completeInspection()` is called
      separately after all discrepancies are ack'd.
      > **Implementation note**: `completeInspection()` is called at the end of
      > `recordInspection()` if and only if all InspectionDiscrepancies are
      > acknowledged (including the freshly auto-created ones). In the
      > zero-discrepancy case they are all vacuously acknowledged.
14. LOT-tracked SKU with both `lotId = null` AND `lotNo = null` in an inspection
    line → `LOT_REQUIRED` (422).
15. `AsnControllerTest` is a `@WebMvcTest` slice (no full Spring context); uses
    `@MockBean` for use-case ports.
16. Build passes from a clean checkout with
    `./gradlew :apps:inbound-service:test --rerun-tasks` (no flaky infrastructure
    dependency — unit tests use fakes, integration tests use Testcontainers).

---

# Related Specs

- `specs/services/inbound-service/architecture.md`
- `specs/services/inbound-service/domain-model.md`
- `specs/services/inbound-service/state-machines/asn-status.md`
- `specs/services/inbound-service/workflows/inbound-flow.md`
- `specs/services/inbound-service/idempotency.md`
- `rules/traits/transactional.md` — T1, T3, T4, T5, T8
- `rules/traits/integration-heavy.md` — I6 (webhook already in TASK-BE-029)
- `rules/domains/wms.md` — W1, W2, W6 and Standard Error Codes
- `platform/testing-strategy.md`
- `platform/error-handling.md`

---

# Related Contracts

- `specs/contracts/http/inbound-service-api.md` §1 (ASN lifecycle) and §2 (Inspection)
- `specs/contracts/events/inbound-events.md` §1 (`inbound.asn.received`),
  §2 (`inbound.asn.cancelled`), §3 (`inbound.inspection.completed`)

---

# Edge Cases

1. **ASN with no outbox row initially** (webhook path): the webhook controller
   writes to `erp_webhook_inbox` only. The `ErpWebhookInboxProcessor` is the
   sole writer of `inbound.asn.received` for webhook-origin ASNs — not the
   webhook controller. Manual REST `POST /asns` fires the outbox in the same TX
   as the domain write.
2. **Zero-discrepancy inspection**: `qty_passed + qty_damaged + qty_short ==
   expected_qty` for all lines AND `qty_damaged == 0` AND `qty_short == 0`. No
   `InspectionDiscrepancy` rows created. `completeInspection()` called immediately.
3. **Inspection with damaged qty only**: `qty_passed = 97, qty_damaged = 3, qty_short = 0,
   expected = 100`. Sum = 100, so no quantity-mismatch discrepancy. Whether a
   `DAMAGE_EXCESS` discrepancy is raised: per domain-model.md §2, damaged > 0 is
   treated as a discrepancy needing acknowledgement (implementation note: raise
   `QUANTITY_MISMATCH` discrepancy when the inspection quantities indicate any
   material deviation from expected full-pass).
   > **Clarification**: Only raise a discrepancy when the total received
   > (`passed + damaged + short`) is **less than** `expectedQty`. Damaged goods
   > arriving in full (total equals expected) record the damage but do NOT
   > auto-raise a discrepancy; ops may note it in `notes`. Damaged goods short of
   > expected qty do raise a `QUANTITY_MISMATCH` discrepancy.
4. **LOT-tracked SKU with `lotId = null` but `lotNo` provided**: accepted — lot
   reconciled later. `InspectionLine.lot_id = null`, `lot_no = "L-20260420-A"`.
5. **Duplicate `asnNo` on manual create**: `ASN_NO_DUPLICATE` (409). Same
   `Idempotency-Key` replay after initial success returns the cached 201 response.
6. **`asnNo` null on manual create**: service auto-generates `ASN-{YYYYMMDD}-{seq}`
   using `AsnNoSequencePort`. Sequence is per-calendar-day; gaps are acceptable.
7. **Acknowledge already-acknowledged discrepancy**: idempotent. Returns same
   response body as original ack. No second outbox row.
8. **Webhook processor races with manual create for same `asnNo`**: DB unique
   constraint on `asn_no` column (V2 migration). First writer wins; second gets
   `UNIQUE_CONSTRAINT_VIOLATION` → mapped to `ASN_NO_DUPLICATE` in processor →
   row marked `FAILED` with reason `ASN_NO_DUPLICATE`.
9. **Asn in `IN_PUTAWAY` cancel attempt**: `cancel()` domain method throws
   `AsnAlreadyClosedException`; `CancelAsnService` does not write any outbox row.

---

# Failure Scenarios

1. **Outbox write fails (DB full / constraint)**: `@Transactional` rolls back the
   entire Asn save + outbox write → client receives 500; state is not persisted;
   idempotency key is NOT cached (key is cached only on successful commit). Client
   may retry with the same `Idempotency-Key`.
2. **Kafka broker unavailable**: outbox rows accumulate as `published_at = null`;
   domain state is saved; publisher retries with exponential backoff; `inbound.outbox.publish.failure.total` increments; when Kafka recovers, backlog is drained in order.
3. **Webhook processor domain failure**: row marked `FAILED` with
   `failure_reason`; processor logs at WARN level; continues to next row; no
   DLQ write (webhook inbox IS the buffer); ops uses `GET /webhooks/inbox` (TASK-BE-031) to inspect and remediate.
4. **Optimistic lock conflict on Asn** (two concurrent start-inspection calls):
   second writer gets `ObjectOptimisticLockingFailureException` → mapped to 409
   `CONFLICT`. Client must `GET /asns/{id}` for fresh state and retry.
5. **`MasterReadModel` stale** (e.g., partner just deactivated, snapshot not yet
   received): ASN creation proceeds; W6 is local-only in v1. No hard fail for
   stale reads. Documented known limitation.
6. **Inspection lines mismatch ASN line count**: `lines` array in
   `RecordInspectionRequest` has fewer entries than `asnLine` count →
   `VALIDATION_ERROR` (400) listing missing `asnLineId`s; more entries than ASN
   lines → `VALIDATION_ERROR` listing unrecognised `asnLineId`s.
7. **Webhook inbox processor crash mid-batch**: rows already `APPLIED` in
   committed sub-TXes remain `APPLIED`; rows not yet processed stay `PENDING` and
   are picked up on next poll. No double-processing risk because each row's TX is
   separate.
