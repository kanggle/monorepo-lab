# Task ID

TASK-BE-006

# Title

Add Lot aggregate to master-service — domain, persistence, application, HTTP, outbox, expiration scheduler

# Status

ready

# Owner

backend

# Task Tags

- code
- api
- event
- test
- scheduled-job

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

Extend `master-service` with the **Lot** (batch/lot tracking) aggregate — the
6th and final aggregate in master-service v1.

Lot is the only aggregate with **cross-aggregate invariants beyond simple FK**:

1. **Forward invariant (create)**: Lot can only be created under a SKU with
   `trackingType = LOT` and `status = ACTIVE`. Creating under a `NONE`-tracked
   SKU or an INACTIVE SKU must be rejected with `STATE_TRANSITION_INVALID`.
2. **Reverse invariant (SKU deactivate guard)**: `SkuService.deactivate` is
   currently stubbed to call `hasActiveLotsFor == false`. This task wires the
   real query so SKU deactivate raises `REFERENCE_INTEGRITY_VIOLATION` when
   active Lots still exist under it.
3. **SKU.trackingType immutability** — already enforced at SKU domain factory
   (BE-004). This task does not change that; it only depends on it.

Also implement the **scheduled expiration job**: a daily Spring `@Scheduled`
task transitions `ACTIVE` → `EXPIRED` when `expiry_date < today`, emitting
`master.lot.expired` events with `triggeredBy: "scheduled-job:lot-expiry"`
and `actorId: null` per `specs/contracts/events/master-events.md` §6.5.

---

# Scope

## In Scope

### Domain layer

- `domain/model/Lot.java` — aggregate with:
  - Fields: `id`, `skuId`, `lotNo`, `manufacturedDate` (nullable `LocalDate`),
    `expiryDate` (nullable `LocalDate`), `supplierPartnerId` (nullable UUID),
    `status` (enum), `version`, `createdAt`, `createdBy`, `updatedAt`, `updatedBy`
  - `LotStatus` enum: `ACTIVE`, `INACTIVE`, `EXPIRED`
  - Factory `Lot.create(skuId, lotNo, manufacturedDate, expiryDate, supplierPartnerId, actor)` —
    validates date pair (`expiryDate >= manufacturedDate` when both present),
    `lotNo` length 1..40 and trimmed non-empty
  - State transitions:
    - `deactivate()` — ACTIVE → INACTIVE, or EXPIRED → INACTIVE (to hide from listings;
      per `domain-model.md` §6 line 304)
    - `reactivate()` — INACTIVE → ACTIVE (EXPIRED is terminal for reactivation —
      throw `InvalidStateTransitionException` if state is EXPIRED)
    - `expire()` — ACTIVE → EXPIRED (package-private or internal; only callable from the
      scheduled expiration pathway; no HTTP endpoint)
  - `updateMutableFields(expiryDate, supplierPartnerId, actor)` — updates only
    the two mutable fields. `skuId`, `lotNo`, `manufacturedDate` immutable after
    creation; attempts to change any of them raise `IMMUTABLE_FIELD` (422).
- `domain/exception/LotNoDuplicateException.java`
- `domain/exception/LotNotFoundException.java`
- No new exception for `SKU_NOT_FOUND` or `PARTNER_NOT_FOUND` on create —
  use existing `SkuNotFoundException` (BE-004). Partner validation is soft in v1
  (see note below).

### Persistence layer

- `V6__init_lot.sql` Flyway migration:
  ```sql
  CREATE TABLE lots (
      id UUID PRIMARY KEY,
      sku_id UUID NOT NULL REFERENCES skus(id),
      lot_no VARCHAR(40) NOT NULL CHECK (char_length(trim(lot_no)) > 0),
      manufactured_date DATE,
      expiry_date DATE,
      supplier_partner_id UUID,  -- no FK; Partner is soft-validated
      status VARCHAR(16) NOT NULL CHECK (status IN ('ACTIVE','INACTIVE','EXPIRED')),
      version BIGINT NOT NULL,
      created_at TIMESTAMPTZ NOT NULL,
      created_by VARCHAR(255) NOT NULL,
      updated_at TIMESTAMPTZ NOT NULL,
      updated_by VARCHAR(255) NOT NULL,
      CONSTRAINT uq_lots_sku_lotno UNIQUE (sku_id, lot_no),
      CONSTRAINT ck_lots_date_pair CHECK (
          manufactured_date IS NULL OR expiry_date IS NULL OR expiry_date >= manufactured_date
      )
  );
  CREATE INDEX idx_lots_sku_status ON lots (sku_id, status);
  CREATE INDEX idx_lots_expiry_active ON lots (expiry_date) WHERE status = 'ACTIVE';
  ```
- `adapter/out/persistence/LotJpaEntity.java` + mapper
- `adapter/out/persistence/JpaLotRepository.java` — methods:
  - `findById`, `existsBySkuIdAndStatus(skuId, status)` (for SKU deactivate guard),
  - `findBySkuIdAndLotNo` (for lookup — if the contract exposes it; otherwise skip)
  - Scheduler query: `findAllByStatusAndExpiryDateBefore(LotStatus.ACTIVE, today)` —
    returns lots to transition to EXPIRED
  - Paginated list queries per §6.3 / §6.4
- `application/port/out/LotPersistencePort.java` — Insert/Update/Load methods
  matching other aggregates' pattern
- `adapter/out/persistence/LotPersistenceAdapter.java` — implements port,
  translates `uq_lots_sku_lotno` violation to `LotNoDuplicateException` via
  `PSQLException.getServerErrorMessage().getConstraintName()` (mirrors
  post-BE-009 SKU pattern)

### Application layer

- `application/port/in/CreateLotUseCase`, `UpdateLotUseCase`, `GetLotUseCase`,
  `ListLotsUseCase`, `DeactivateLotUseCase`, `ReactivateLotUseCase`,
  `ExpireLotsBatchUseCase` (the scheduled job port)
- `application/service/LotService.java`:
  - `create(SkuId, cmd, actor)`:
    1. Load parent SKU (`SkuPersistencePort.loadOrThrow`) — 404 `SKU_NOT_FOUND` if missing
    2. Check `sku.status == ACTIVE` — 422 `STATE_TRANSITION_INVALID` if not
    3. Check `sku.trackingType == LOT` — 422 `STATE_TRANSITION_INVALID` if NONE
    4. `Lot.create(...)` — domain invariants (date pair, lot_no format)
    5. Partner soft check — if `supplierPartnerId` provided and `PartnerPersistencePort` exists
       (per BE-005), optionally validate existence; if BE-005 not yet landed, skip the check
       and document in notes that v2 will add it. (See Implementation Notes below.)
    6. Persist via port, emit `master.lot.created` event
  - `deactivate`, `reactivate` — standard pattern with `InvalidStateTransitionException`
    when state rules violated (EXPIRED → reactivate blocked)
  - `expireBatch(today)` (used by scheduler):
    - Query `findAllByStatusAndExpiryDateBefore(ACTIVE, today)`
    - For each: call `lot.expire()`, persist, emit `master.lot.expired` event with
      `triggeredBy: "scheduled-job:lot-expiry"`, `actorId: null`, `scheduledAt: now`
    - Batch is **best-effort**: a per-row failure is logged and does not abort the batch
      (one failed lot shouldn't block others). Use a per-row try/catch; aggregate failure
      count and emit a single `master.outbox.lot-expire.failure.count` metric at the end.

### HTTP layer

- `adapter/in/web/controller/LotController.java` — 7 endpoints per
  `specs/contracts/http/master-service-api.md` §6:
  - `POST /api/v1/master/skus/{skuId}/lots` — create
  - `GET /api/v1/master/lots/{id}` — get by id
  - `GET /api/v1/master/skus/{skuId}/lots` — list per SKU (paginated)
  - `GET /api/v1/master/lots` — flat list (paginated, filterable by `status`, `skuId`)
  - `PATCH /api/v1/master/lots/{id}` — update mutable fields
  - `POST /api/v1/master/lots/{id}/deactivate`
  - `POST /api/v1/master/lots/{id}/reactivate`
- DTOs: request/response records matching §6.1–§6.7 spec
- ETag header on GET / PATCH / POST deactivate / reactivate with value `"v{version}"`
  (mirrors post-BE-008 pattern)
- Validation: `@NotBlank` `lotNo`, `@Size(max=40)`, `@PastOrPresent` optional on
  `manufacturedDate`, date pair cross-validation via custom validator or domain factory
- Error mappings:
  - `SKU_NOT_FOUND` (404), `LOT_NOT_FOUND` (404)
  - `LOT_NO_DUPLICATE` (409) — per §6.1 spec
  - `STATE_TRANSITION_INVALID` (422) — parent SKU not ACTIVE / not LOT-tracked / lot already EXPIRED on reactivate
  - `IMMUTABLE_FIELD` (422) — attempts to change `skuId`, `lotNo`, `manufacturedDate`
  - `CONFLICT` (409) — version mismatch
  - `REFERENCE_INTEGRITY_VIOLATION` — NOT used on Lot itself (Lot has no children in v1)
  - `VALIDATION_ERROR` (400) — bean validation failures
- `GlobalExceptionHandler` — register `LotNoDuplicateException` → 409 `LOT_NO_DUPLICATE`

### SKU deactivate reverse guard (upgrade stub to real)

- `SkuPersistencePort.hasActiveLotsFor(UUID skuId)` — remove the stub implementation
  landed in BE-004
- `SkuPersistenceAdapter.hasActiveLotsFor` — delegate to
  `JpaLotRepository.existsBySkuIdAndStatus(skuId, ACTIVE)`
- `SkuService.deactivate` — change the body so it throws `ReferenceIntegrityViolationException`
  when `hasActiveLotsFor == true` (model after BE-010 Zone deactivate guard +
  BE-014 Warehouse guard)

### Outbox / events

- `EventEnvelopeSerializer` (libs/java-messaging) — no change needed; Lot events
  use the same envelope. Payload builder in the adapter-out for Lot events.
- 5 event types on `wms.master.lot.v1`:
  - `master.lot.created`
  - `master.lot.updated`
  - `master.lot.deactivated`
  - `master.lot.reactivated`
  - `master.lot.expired` — special envelope with `triggeredBy` and `actorId: null`
- Partition key: `lotId` (per `specs/contracts/events/master-events.md` partitioning rule)

### Scheduler

- `scheduler/LotExpirationScheduler.java`:
  - `@Scheduled(cron = "0 5 0 * * *")` — runs daily at 00:05 (avoid midnight thundering herd)
  - Calls `expireLotsBatchUseCase.execute(LocalDate.now())`
  - Add `@ConditionalOnProperty(name = "wms.scheduler.lot-expiration.enabled", havingValue = "true", matchIfMissing = true)`
    so integration / e2e harnesses can disable
- Application.yml: `wms.scheduler.lot-expiration.enabled: true` default
- `application-integration.yml` override: `enabled: false` (test harnesses set their own clock /
  trigger the batch use case directly)

### Tests

- **Unit** (`LotTest`): domain factory / state machine — create-with-valid-dates, create-with-invalid-date-pair,
  deactivate from ACTIVE/EXPIRED, reactivate from INACTIVE (pass) / EXPIRED (fail), expire from ACTIVE,
  immutable-field attempts
- **Service** (`LotServiceTest`): create-happy-path, create-when-sku-not-active (422),
  create-when-sku-is-NONE-tracked (422), create-when-sku-not-found (404), duplicate-lotNo (409),
  update-happy, update-immutable (422), deactivate-happy, deactivate-already-inactive (422),
  reactivate-from-inactive (happy), reactivate-from-expired (blocked), expire-batch-happy,
  expire-batch-with-one-failing-lot (batch continues), optimistic-lock-conflict (409)
- **Service Authorization** (`LotServiceAuthorizationTest`): ROLE_ADMIN / ROLE_MASTER_MANAGER allowed;
  other roles rejected per existing auth matrix
- **Controller** (`LotControllerTest`): 20+ `@WebMvcTest` cases covering all 7 endpoints, happy +
  error paths, ETag format `"v{version}"`, at least one 422 case asserts ISO 8601 `timestamp`
  regex on error envelope (per BE-008)
- **Persistence H2** (`LotPersistenceAdapterH2Test`): ORM paths including insert/version-0,
  update/version-bump, unique `(sku_id, lot_no)` per SKU, list queries
- **Persistence Testcontainers** (`LotPersistenceAdapterTest`):
  - `@Testcontainers(disabledWithoutDocker = true)` + Postgres 16 alpine
  - `uq_lots_sku_lotno` unique constraint via real Postgres (proves partial-style
    constraint-name detection path)
  - `ck_lots_date_pair` CHECK constraint via raw-SQL insert with bad date pair
    (bypasses domain factory, asserts `DataIntegrityViolationException`)
  - `idx_lots_expiry_active` partial index query correctness —
    `findAllByStatusAndExpiryDateBefore` returns only ACTIVE lots with expired dates
- **Scheduler** (`LotExpirationSchedulerTest`): smoke test with mocked use case; batch failure
  isolation (1 failing lot doesn't abort the batch)
- **SKU reverse-guard upgrade** (update `SkuServiceTest`): `deactivate_blockedByActiveLots` now
  asserts `ReferenceIntegrityViolationException` with code `REFERENCE_INTEGRITY_VIOLATION`
  (replaces the stub-based "always passes" assertion from BE-004)
- **SKU Controller upgrade**: one new case in `SkuControllerTest` covering the above via HTTP
  path — 409 + `"code": "REFERENCE_INTEGRITY_VIOLATION"`

## Out of Scope

- **Partner aggregate (BE-005)** — if not yet landed, `supplierPartnerId` is accepted as free UUID.
  Document in task review that hard Partner validation will land alongside BE-005 or as follow-up.
- Cross-service inventory / stock movement checks — Lot has no inventory-level logic in v1
  (that belongs to a future `inventory-service`).
- Timezone handling for `expiry_date` — use server-local `LocalDate.now()` at job time; timezone
  concerns deferred to v2.
- Bulk import / seeding — deferred to a separate tooling task.
- Auto-cleanup of old EXPIRED lots — not part of v1 scope; EXPIRED records persist.

---

# Acceptance Criteria

- [ ] `Lot` domain aggregate exists with factory + state machine + `LotStatus` enum
- [ ] `V6__init_lot.sql` migration created and applied locally
- [ ] `LotPersistencePort` + `LotPersistenceAdapter` + `JpaLotRepository` complete
- [ ] `LotService` implements the 7 use cases + `expireBatch`
- [ ] `LotController` exposes all 7 endpoints with correct error envelope mappings
- [ ] `GlobalExceptionHandler` maps `LotNoDuplicateException` → 409 `LOT_NO_DUPLICATE`
- [ ] 5 event types published to `wms.master.lot.v1`; `master.lot.expired` carries
      `triggeredBy` + `actorId: null`
- [ ] `LotExpirationScheduler` scheduled at 00:05 cron; respects
      `wms.scheduler.lot-expiration.enabled` property
- [ ] `application-integration.yml` disables the scheduler for test harnesses
- [ ] `SkuPersistencePort.hasActiveLotsFor` real query (not stubbed)
- [ ] `SkuService.deactivate` throws `ReferenceIntegrityViolationException`
      (`REFERENCE_INTEGRITY_VIOLATION`, 409) when active Lots exist
- [ ] `SkuServiceTest.deactivate_blockedByActiveLots` asserts the new exception
      (replaces stub-based assertion)
- [ ] `SkuControllerTest` new case for 409 + `REFERENCE_INTEGRITY_VIOLATION`
      on SKU deactivate with active lots
- [ ] Unit / Service / Controller / H2 / Testcontainers / Scheduler tests all added and passing
- [ ] `./gradlew :projects:wms-platform:apps:master-service:check` passes (Testcontainers run
      on WSL2 locally, CI Linux verifies all)
- [ ] Contract harness (`HttpContractTest`, `EventContractTest`) validates against updated schemas

---

# Related Specs

- `platform/error-handling.md` — error code catalog, including `REFERENCE_INTEGRITY_VIOLATION` (409)
- `platform/testing-strategy.md`
- `specs/contracts/http/master-service-api.md` §6 Lot
- `specs/contracts/events/master-events.md` §6 Lot (all 5 event types)
- `specs/services/master-service/architecture.md` — Lot data ownership, scheduled job overview
- `specs/services/master-service/domain-model.md` §4 SKU cross-invariants, §6 Lot state machine
- `rules/domains/wms.md` W6 referential integrity
- `.claude/skills/backend/architecture/hexagonal/SKILL.md` + testing skills (testcontainers, wiremock-free)

# Related Contracts

- `specs/contracts/http/master-service-api.md` §6
- `specs/contracts/events/master-events.md` §6

---

# Target Service

- `master-service`

---

# Implementation Notes

- **Reference aggregates**: model after SKU (BE-004) — most similar complexity with
  UPPERCASE-normalized code, immutable fields, and cross-aggregate check (SKU had
  Lot stub; Lot has SKU dependency). Warehouse/Zone/Location are simpler references.
- **Scheduler + outbox**: the expire batch runs as a normal transaction, using the same
  `OutboxPollingScheduler` pattern from `libs/java-messaging`. Each expired lot inserts an
  outbox row in the same tx as the lot row update; the outbox publisher picks them up
  asynchronously. Do NOT publish directly from the scheduler — always via outbox.
- **`triggeredBy` + `actorId: null`**: the event envelope builder needs a code path that
  accepts `Optional<String> actorId` and handles null for system-generated events. Check
  `libs/java-messaging/EventEnvelopeBuilder` to see if this is already supported; extend
  if needed (but keep the extension additive / backwards-compatible).
- **Partner soft validation**: if BE-005 (Partner aggregate) is not landed, still accept
  the `supplierPartnerId` field but do not verify existence. Add a TODO comment that
  references BE-005 so the check can be wired when Partner exists.
- **Date pair validation**: prefer enforcement at the domain factory (defense in depth),
  but the DB `ck_lots_date_pair` CHECK is the ultimate guard — the Testcontainers test
  uses raw SQL to prove the DB catches a bypass attempt.
- **Scheduler integration tests**: the scheduler itself is NOT exercised in the integration
  test suite (would require time-travel or a 5AM wait). Instead: test the scheduler class
  with a mock use case (unit test), and test the use case with Testcontainers using
  `LocalDate.now().plusDays(10)` to make test lots appear expired relative to the wall clock.
- **ETag on mutation endpoints**: `deactivate`, `reactivate`, `update`, `get` all return
  the new `"v{version}"` ETag. `expire` is internal and does not have an HTTP endpoint.
- **Windows blocker**: Testcontainers tests work on WSL2 per the recent setup. Keep
  `@Testcontainers(disabledWithoutDocker = true)` so Windows-native test runs skip them.
- **Event payload validation**: contract harness runs JSON schema validation against
  `src/test/resources/contracts/events/master-lot-*.schema.json` — create new schemas
  for each event type, mirroring existing master-sku / master-warehouse patterns.

---

# Edge Cases

- **Lot created on SKU just transitioned to LOT**: SKU's `trackingType` is immutable once
  Lots exist, but the reverse (NONE → LOT with no existing Lots) is theoretically allowed
  per domain-model.md §4 line 207. In v1 we enforce `trackingType` immutable at the SKU
  factory (BE-004 landed this). This task does not change that — it only depends on it.
- **Lot with `expiryDate = today` at scheduled-job time**: the query is `expiry_date < today`
  (strict), so a lot expiring exactly today does not expire until tomorrow's run.
  Document this in a Javadoc note.
- **Lot without `expiryDate`**: permanent / never expires via the scheduled job. Excluded
  from `idx_lots_expiry_active` naturally (NULL dates not in index).
- **Partial unique `(sku_id, lot_no)`**: two SKUs can have the same `lot_no` — only
  per-SKU uniqueness is enforced. Cover this in the Testcontainers test.
- **Reactivating an EXPIRED Lot**: must fail with `STATE_TRANSITION_INVALID`. A future
  business decision to allow EXPIRED → ACTIVE would require domain changes + contract update.
- **Scheduler running across DST**: cron `0 5 0 * * *` may fire twice or skip at DST
  boundaries. Idempotent query (`WHERE status = 'ACTIVE' AND expiry_date < today`) handles
  this — running twice produces no duplicate side effects (lots already EXPIRED no longer
  match the query).
- **SKU deactivate race**: two requests see `hasActiveLotsFor == false`, both proceed to
  deactivate. Optimistic lock on SKU catches only one committing. The `REFERENCE_INTEGRITY_VIOLATION`
  path covers the sequentially-serialized case. Race between `create-lot` and `deactivate-sku` is
  out of scope per BE-014 precedent.

---

# Failure Scenarios

- **Scheduler job runs during DB outage**: the job's SQL query fails, logs an error, and
  leaves `ACTIVE` lots un-expired. Next day's run catches them. No data loss; no partial
  state.
- **Outbox publisher falls behind during a mass-expiration day** (e.g., 10,000 lots expire
  same day): outbox table grows, Kafka backlog accumulates. `OutboxPollingScheduler`'s
  backpressure metric (`master.outbox.pending.count`) should surface this; alerting is
  ops concern, not this task's scope.
- **Partner soft-validation regression**: if a `supplierPartnerId` is accepted now and later
  BE-005 lands with hard validation, historical Lot rows may reference a non-existent Partner.
  Migration strategy: BE-005 review should consider a cleanup / soft-bind step; not in this
  task's scope.

---

# Test Requirements

- Minimum 50+ test methods across all layers (Lot is the most complex aggregate):
  - `LotTest` (domain): ~10 methods
  - `LotServiceTest`: ~15 methods
  - `LotServiceAuthorizationTest`: ~5 methods
  - `LotControllerTest`: ~20 methods
  - `LotPersistenceAdapterH2Test`: ~6 methods
  - `LotPersistenceAdapterTest` (Testcontainers): ~6 methods (including raw-SQL bypass,
    partial-per-SKU uniqueness, idx partial correctness, version-0 on insert)
  - `LotExpirationSchedulerTest`: ~3 methods (happy, disabled, one-failing-lot isolation)
  - `EventEnvelopeSerializerTest` extensions (5 new Lot event cases)
  - `MasterOutboxPollingSchedulerTest` extensions (Lot event publishing)
  - `SkuServiceTest.deactivate_blockedByActiveLots` — REPLACE stub with real assertion
  - `SkuControllerTest` — new 409 `REFERENCE_INTEGRITY_VIOLATION` case on SKU deactivate
- Existing tests remain green — no regression in W/Z/L/S test suites
- `./gradlew :projects:wms-platform:apps:master-service:check` passes locally on WSL2
  (Testcontainers runs) and Windows (skipped)

---

# Definition of Done

- [ ] Domain + persistence + application + HTTP + outbox + scheduler all landed
- [ ] SKU reverse guard upgraded (stub → real)
- [ ] Contract harness validates all 5 new event schemas + Lot HTTP endpoints
- [ ] All tests passing locally (WSL2) and on CI Linux
- [ ] README / architecture.md updated if scheduler behavior needs documenting
- [ ] Ready for review
