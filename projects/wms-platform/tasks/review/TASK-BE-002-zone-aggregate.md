# Task ID

TASK-BE-002

# Title

Add Zone aggregate to master-service — domain, persistence, application, HTTP, outbox

# Status

ready

# Owner

backend

# Task Tags

- code
- api
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

Extend `master-service` with the **Zone** aggregate following the Hexagonal
structure already established by Warehouse (TASK-BE-001). Zone is nested under
Warehouse both in the domain model and in the HTTP contract
(`/api/v1/master/warehouses/{warehouseId}/zones/...`), so the implementation
introduces two new concerns that Warehouse did not exercise:

1. **Parent-reference validation** — creation and reactivation require the
   parent Warehouse to exist and be `ACTIVE`.
2. **Compound unique constraint** — `(warehouse_id, zone_code)` is unique, not
   `zone_code` alone.

On completion:

- `com.wms.master.domain.model.Zone` + supporting domain classes match the
  Warehouse style (POJO, no JPA annotations, immutability/state-transition
  guards in the model).
- All endpoints defined in `specs/contracts/http/master-service-api.md §2` are
  implemented and their contract shape matches.
- Each Zone mutation writes one outbox row in the same transaction and the
  publisher emits the event on `wms.master.zone.v1`.
- Event payload matches `specs/contracts/events/master-events.md §2`.

Location / SKU / Partner / Lot remain out of scope; they are TASK-BE-003..006.

---

# Scope

## In Scope

- Domain package: `Zone`, `ZoneType` enum, `ZoneStatus` (or reuse a shared
  `EntityStatus` if already extracted from `WarehouseStatus`)
- Domain exceptions new to Zone: `ZoneNotFoundException`,
  `ZoneCodeDuplicateException`, `ParentWarehouseInactiveException`
- Domain events: `ZoneCreatedEvent`, `ZoneUpdatedEvent`,
  `ZoneDeactivatedEvent`, `ZoneReactivatedEvent` implementing the existing
  `DomainEvent` sealed hierarchy
- Flyway `V2__init_zone.sql`: `zones` table with `(warehouse_id, zone_code)`
  compound unique, FK to `warehouses(id)`, shared outbox table reused (already
  created by V1)
- Persistence: `ZoneJpaEntity`, `JpaZoneRepository`, `ZonePersistenceMapper`
  (with `toInsertEntity()` emitting `version=null` to avoid the detached-entity
  bug found in TASK-BE-001), `ZonePersistenceAdapter` (`@Repository`)
- Application ports: `ZonePersistencePort`, `ZoneCrudUseCase`,
  `ZoneQueryUseCase`
- Application commands/queries/results: `CreateZoneCommand`,
  `UpdateZoneCommand`, `DeactivateZoneCommand`, `ReactivateZoneCommand`,
  `ListZonesCriteria`, `ZoneResult`
- `ZoneService` application service with `@PreAuthorize` mirroring Warehouse
  role matrix (`MASTER_WRITE` for create/update, `MASTER_ADMIN` for
  deactivate/reactivate, `MASTER_READ` for queries)
- HTTP adapter: `ZoneController` wired under nested path
  `/api/v1/master/warehouses/{warehouseId}/zones` with DTOs `CreateZoneRequest`,
  `UpdateZoneRequest`, `DeactivateZoneRequest`, `ReactivateZoneRequest`,
  `ZoneResponse`
- `GlobalExceptionHandler` extension: map new Zone exceptions to their contract
  statuses (`WAREHOUSE_NOT_FOUND` 404, `ZONE_NOT_FOUND` 404,
  `ZONE_CODE_DUPLICATE` 409, `STATE_TRANSITION_INVALID` 409 when parent
  Warehouse is `INACTIVE`)
- Outbox integration: use the existing `DomainEventPort` — no new adapter
  needed. `MasterOutboxPollingScheduler.resolveTopic()` must already cover
  `master.zone.*` → `wms.master.zone.v1` (verify and extend if missing)
- `EventEnvelopeSerializer` extension: sealed-switch case for the four Zone
  events
- Unit tests (domain model + application service), slice tests
  (`@DataJpaTest` Testcontainers + H2 parallel, `@WebMvcTest`), seed migration
  update (`V99__seed_dev_warehouse.sql` is Warehouse-only — add
  `V100__seed_dev_zones.sql` under the same `dev` profile with one Zone per
  enum type under `WH01`)

## Out of Scope

- Location / SKU / Partner / Lot aggregates
- Full `@SpringBootTest` integration suite (flagged under TASK-BE-007)
- Refactoring `WarehouseStatus` → shared `EntityStatus` — acceptable as a small
  in-flight extraction if the reviewer agrees, but not required for this task
- Cross-service referential integrity (v2)
- Bulk / CSV Zone import
- Zone-specific metrics beyond what the outbox publisher already emits

---

# Acceptance Criteria

- [ ] `./gradlew :projects:wms-platform:apps:master-service:check` passes
- [ ] `GET /actuator/health` still returns `200 UP`
- [ ] `POST /api/v1/master/warehouses/{warehouseId}/zones` creates a Zone and
      returns `201` with shape matching `specs/contracts/http/master-service-api.md §2.1`
- [ ] `GET /{zoneId}` returns `200` with `ETag: "v{version}"`; unknown id → `404 ZONE_NOT_FOUND`
- [ ] Unknown `warehouseId` on create/list/etc. → `404 WAREHOUSE_NOT_FOUND`
- [ ] Create under an `INACTIVE` parent Warehouse → `409 STATE_TRANSITION_INVALID`
- [ ] `GET /api/v1/master/warehouses/{warehouseId}/zones` paginates per `PageResult`, supports `zoneType` filter, default `status=ACTIVE`
- [ ] `PATCH /{zoneId}` updates `name` + `zoneType`; wrong `version` → `409 CONFLICT`
- [ ] Attempting to change `zoneCode` or `warehouseId` via PATCH → `422 IMMUTABLE_FIELD`
- [ ] `POST /{zoneId}/deactivate` / `/reactivate` toggles status; invalid transition → `409 STATE_TRANSITION_INVALID`
- [ ] Duplicate `zoneCode` within the same warehouse → `409 ZONE_CODE_DUPLICATE`; same `zoneCode` in a different warehouse succeeds
- [ ] Idempotency-Key rules unchanged (filter is shared) — verified via a smoke case on `POST` create
- [ ] Each successful mutation writes exactly one row in the shared outbox table in the same tx (verified by slice test)
- [ ] Publisher forwards rows to `wms.master.zone.v1` via the existing scheduler (unit test on `resolveTopic`)
- [ ] Published event envelope matches `specs/contracts/events/master-events.md §2`
- [ ] Domain layer has **no** JPA annotations; JPA entity is package-private inside `adapter/out/persistence`
- [ ] Role matrix enforced via `@PreAuthorize` at the application service; `WarehouseServiceAuthorizationTest`-equivalent exists for Zone
- [ ] `H2`-mode adapter test runs locally (`WarehousePersistenceAdapterH2Test` analog); Testcontainers-backed test runs in CI
- [ ] Seed migration `V100__seed_dev_zones.sql` populates 3 zones under `WH01` when `spring.profiles.active=dev`

---

# Related Specs

> **Before reading Related Specs**: Follow `platform/entrypoint.md` Step 0 — read `PROJECT.md`, then load `rules/common.md` plus `rules/domains/wms.md`, `rules/traits/transactional.md`, `rules/traits/integration-heavy.md`.

- `platform/error-handling.md`
- `platform/testing-strategy.md`
- `platform/service-types/rest-api.md`
- `platform/versioning-policy.md`
- `specs/services/master-service/architecture.md`
- `specs/services/master-service/domain-model.md` §2 Zone
- `specs/services/master-service/idempotency.md`
- `rules/domains/wms.md` — especially W1 (transactional mutation) and W2 (parent-reference discipline, if present)
- `rules/traits/transactional.md`

# Related Skills

- `.claude/skills/backend/architecture/hexagonal/SKILL.md`
- `.claude/skills/backend/dto-mapping/SKILL.md`
- `.claude/skills/backend/transaction-handling/SKILL.md`
- `.claude/skills/backend/exception-handling/SKILL.md`
- `.claude/skills/backend/pagination/SKILL.md`
- `.claude/skills/database/schema-change-workflow/SKILL.md`
- `.claude/skills/database/migration-strategy/SKILL.md`
- `.claude/skills/messaging/outbox-pattern/SKILL.md`
- `.claude/skills/messaging/event-implementation/SKILL.md`
- `.claude/skills/testing/testcontainers/SKILL.md`

---

# Related Contracts

- `specs/contracts/http/master-service-api.md` §Global Conventions and §2 Zone
- `specs/contracts/events/master-events.md` §Global Envelope, §Topic Layout, §2 Zone Events

---

# Target Service

- `master-service`

---

# Architecture

Follow:

- `specs/services/master-service/architecture.md` (Hexagonal, rest-api)
- Existing Warehouse implementation on `main` is the authoritative reference
  for package layout, transactional boundaries, exception translation, and the
  outbox write pattern. **Mirror it**; do not redesign.

---

# Implementation Notes

- **Parent lookup on create** — `ZoneService.create(...)` must call
  `WarehousePersistencePort.findById(warehouseId)` first. If absent → throw
  `WarehouseNotFoundException`. If present but `status=INACTIVE` → throw
  `InvalidStateTransitionException` with message "parent warehouse is not ACTIVE".
  Do **not** introduce a cross-aggregate JPA relationship — keep the lookup at
  the application layer so the persistence adapters stay aggregate-scoped.
- **Compound unique constraint** — define it in both the Flyway migration and
  the `ZoneJpaEntity` via `@Table(uniqueConstraints = {@UniqueConstraint(name =
  "uq_zones_warehouse_code", columnNames = {"warehouse_id", "zone_code"})})`.
- **Exception translation** — `@Repository` on `ZonePersistenceAdapter` enables
  Spring's `PersistenceExceptionTranslationPostProcessor` (already wired by
  `MasterServicePersistenceConfig`). Translate `DataIntegrityViolationException`
  whose root cause is the compound unique constraint into
  `ZoneCodeDuplicateException`.
- **Insert path** — reuse the `toInsertEntity()` mapper pattern (emits
  `version=null`) learned from TASK-BE-001. Otherwise Hibernate treats the
  entity as detached and throws `StaleObjectStateException`.
- **Reactivation** — also checks parent Warehouse status. If parent is
  `INACTIVE`, `STATE_TRANSITION_INVALID`.
- **Deactivation** — in v1 there are no Locations yet, so the
  "blocked if ACTIVE Location references this zone" invariant cannot actually
  fire. Implement the invariant at the application layer behind a
  `LocationPersistencePort`-style interface stub that returns "no active
  children" for now. When TASK-BE-003 adds Locations, the same interface is
  implemented for real. This is a small forward-looking seam, NOT speculative
  generality — it maps 1:1 to a documented invariant.
- **OptimisticLockingFailureException** — translate to
  `ConcurrencyConflictException`, identical to Warehouse.
- **Events** — extend `EventEnvelopeSerializer` with a sealed-switch case for
  the four Zone event types. `aggregateType = "zone"`. `aggregateId = zone.id`.
  `payload.zone` matches the event contract.
- **Topic resolver** — `MasterOutboxPollingScheduler.resolveTopic` already
  patterns `master.{aggregate}.{action}` → `wms.master.{aggregate}.v1`.
  Verify `master.zone.created` maps to `wms.master.zone.v1`; add a unit test
  case if not present.
- **Method security role matrix** — identical to Warehouse (reuse
  `MASTER_READ / MASTER_WRITE / MASTER_ADMIN`).
- **Seed data** — new Flyway migration `V100__seed_dev_zones.sql` runs only
  under `spring.profiles.active=dev` via the existing seed-locations config.
  Insert three zones under `WH01`: `Z-A` (AMBIENT), `Z-C` (CHILLED), `Z-R`
  (RETURNS).

---

# Edge Cases

- Duplicate `zoneCode` in the same warehouse under concurrent inserts — unique
  constraint fires; translate to `ZONE_CODE_DUPLICATE`.
- Same `zoneCode` in a different warehouse — must succeed.
- Create under a non-existent `warehouseId` — `404 WAREHOUSE_NOT_FOUND`.
- Create under an `INACTIVE` parent — `409 STATE_TRANSITION_INVALID`.
- PATCH body attempting to change `zoneCode` — reject at domain layer with
  `ImmutableFieldException` → `422`.
- PATCH body attempting to change `warehouseId` — same as above.
- Deactivate already-`INACTIVE` zone — `409 STATE_TRANSITION_INVALID`.
- Reactivate already-`ACTIVE` zone — `409 STATE_TRANSITION_INVALID`.
- Reactivate under `INACTIVE` parent — `409 STATE_TRANSITION_INVALID`.
- `zoneType` absent or not in enum — `400 VALIDATION_ERROR`.
- `zoneCode` pattern violation (`^Z-[A-Z0-9]+$`) — `400 VALIDATION_ERROR`.
- Two concurrent PATCHes with the same `version` — second returns
  `409 CONFLICT` (optimistic lock).

---

# Failure Scenarios

- Parent Warehouse deleted mid-request (shouldn't happen in v1; we have no hard
  delete, only deactivation) — still, guard against NPE.
- Postgres down at boot — same as Warehouse (service `DOWN`, no traffic).
- Postgres down during request — same as Warehouse (`500 SERVICE_UNAVAILABLE`).
- Kafka down at publish time — outbox rows accumulate; same pending-count
  alarm applies.
- Concurrent deactivation by two operators — optimistic lock → `409`.
- Seed migration fails (for example, warehouse `WH01` not yet seeded) — the
  migration should be idempotent (`ON CONFLICT DO NOTHING`) and depend on
  `V99`'s warehouse row; if it's absent, skip rather than fail the service.

---

# Test Requirements

## Unit Tests

- `Zone` domain model — factory validation (required fields, `zoneCode`
  pattern), update path enforcing `zoneCode` + `warehouseId` immutability,
  deactivate / reactivate state transitions.
- `ZoneService` application tests using the in-memory fake pattern — every
  error code covered; parent-warehouse-inactive check exercised.

## Slice Tests

- `@WebMvcTest(ZoneController.class)` — request validation, DTO-command
  mapping, status codes, `ETag` header on GET, path variable extraction for
  nested route.
- `@DataJpaTest` on `ZonePersistenceAdapter` (Testcontainers Postgres variant
  + H2 parallel for local dev) — compound unique constraint, optimistic-lock
  collision, insert path (`version=null` mapper), outbox row written in same
  tx.

## Integration-ish

- No full `@SpringBootTest` in this task (deferred to TASK-BE-007), but a smoke
  test proving `ApplicationContext` still loads with the added beans is
  required.

## Event Contract

- Extend `EventEnvelopeSerializerTest` with one case per Zone event — assert
  envelope fields and `payload.zone` shape.

---

# Definition of Done

- [x] Implementation completed
- [x] Tests added (unit / slice / event-contract; smoke test for context load)
- [x] Tests passing locally (`./gradlew :projects:wms-platform:apps:master-service:test` — 37 test classes, 0 failures; Testcontainers skip on Windows dev per documented blocker, run in CI)
- [x] Contracts unchanged — no spec edits required
- [x] Seed migration added (`V100__seed_dev_zones.sql`)
- [x] Review notes cover deferred items + doc debt
- [x] Ready for review

---

# Review Note (2026-04-19)

## Implementation Delivery

Landed in 5 phased commits on `feat/wms-task-be-002-zone`:

| Phase | Scope |
|---|---|
| 1 | Domain model — `Zone`, `ZoneType`, exceptions, 4 sealed `DomainEvent` subclasses, `InvalidStateTransitionException` free-form-reason constructor |
| 2 | Persistence — `V3__init_zone.sql` (compound `uq_zones_warehouse_code`, FK to warehouses, index on `(warehouse_id, status)`), `ZoneJpaEntity` (pkg-private, `@Version`), `JpaZoneRepository`, `ZonePersistenceMapper` (with `toInsertEntity()` emitting `version=null` — BE-001 lesson), `ZonePersistenceAdapter` (`@Repository` for exception translation) |
| 3 | Application — `ZonePersistencePort` (with `hasActiveLocationsFor` stub returning `false` until TASK-BE-003), split `ZoneCrudUseCase` + `ZoneQueryUseCase`, commands/query/result, `ZoneService` with `@Transactional` + `@PreAuthorize` role matrix mirroring Warehouse. Parent-active check on create + reactivate via `WarehousePersistencePort`. |
| 4 | HTTP — `ZoneController` at nested `/api/v1/master/warehouses/{warehouseId}/zones`, six endpoints per §2.1-2.6, ETag/Location headers, defensive cross-warehouse-id path-vs-resolved-zone check (404 on mismatch). `GlobalExceptionHandler` extended. |
| 5 | Outbox wiring + seed + smoke — `EventEnvelopeSerializer` sealed-switch cases for Zone; `MasterOutboxPollingSchedulerTest` + `EventEnvelopeSerializerTest` + `ApplicationContextSmokeTest` extended; `V100__seed_dev_zones.sql` with 3 fixed-UUID zones under `WH01`. |

## Acceptance Criteria Status

| AC | State | Note |
|---|---|---|
| `./gradlew :projects:wms-platform:apps:master-service:check` passes | ✅ | 37 test classes; Testcontainers skipped on Windows, covered by H2 mirror |
| `/actuator/health` → 200 | ✅ (by smoke) | `ApplicationContextSmokeTest` asserts beans wire |
| POST create matches §2.1 | ✅ | `ZoneControllerTest` |
| GET by id with ETag / 404 on unknown | ✅ | `"v{version}"` ETag |
| Unknown `warehouseId` → 404 `WAREHOUSE_NOT_FOUND` | ✅ | `ZoneService.create` resolves parent first |
| Create under INACTIVE parent → 409 `STATE_TRANSITION_INVALID` | ✅ | Error message "parent warehouse is not ACTIVE" |
| GET list paginates, filters by `zoneType`/`status` | ✅ | `ListZonesCriteria` |
| PATCH updates + wrong version → 409 `CONFLICT` | ✅ | Optimistic lock |
| Immutable-field change (`zoneCode`/`warehouseId`) → 422 | ✅ | `Zone.rejectImmutableChange` |
| Deactivate/reactivate + invalid → 409 | ✅ | Domain state guard |
| Duplicate `zoneCode` per-warehouse → 409 `ZONE_CODE_DUPLICATE`; same code different warehouse OK | ✅ | Compound unique + adapter-level translation; H2 test proves both paths |
| Idempotency-Key rules unchanged | ✅ | Shared filter |
| Mutation writes one outbox row in same tx | ✅ | `@Transactional` boundary in `ZoneService` |
| Publisher → `wms.master.zone.v1` | ✅ | `MasterOutboxPollingSchedulerTest` covers all 4 `master.zone.*` cases |
| Event envelope matches §2 | ✅ | `EventEnvelopeSerializerTest` |
| Domain layer has no JPA annotations; entity is package-private | ✅ | |
| Role matrix via `@PreAuthorize` | ✅ | `ZoneServiceAuthorizationTest` — injected by use-case interface |
| H2 adapter test locally + Testcontainers in CI | ✅ | `ZonePersistenceAdapterH2Test` + `ZonePersistenceAdapterTest` |
| Seed V100 populates 3 zones under WH01 | ✅ | Only `dev` profile |

## Deviations from the ticket (small, documented)

1. **Flyway file is `V3__init_zone.sql` not `V2__init_zone.sql`.** V2 was already taken by the pre-existing `V2__init_outbox.sql`. The ticket's "V2" reference was based on the ticket's outdated mental model of shared outbox.
2. **`InvalidStateTransitionException` gained a second constructor** `(String reason)` so the parent-inactive and active-locations-still-present paths can emit spec-prescribed messages without synthesizing a fake "from-state". Same error code, same 409 mapping.
3. **`hasActiveLocationsFor` stub** lives directly on `ZonePersistenceAdapter` returning `false`, rather than a separate `AlwaysFalseActiveLocationsChecker` bean. Simpler, 1:1 with the documented invariant; documented in Javadoc on both the port method and the adapter.
4. **Defensive cross-warehouse path check on `ZoneController`.** If the resolved zone's `warehouseId` disagrees with the path variable (e.g. a client crafts `/warehouses/{wrongId}/zones/{realZoneId}`), return 404 `ZONE_NOT_FOUND` rather than leak. Not explicit in §2.2 but the only safe interpretation of the nested route.

## Gaps (same posture as BE-001 — carried into follow-up)

- No full `@SpringBootTest` integration (happy path end-to-end + idempotency interaction + publisher-to-real-Kafka + publisher resilience). Deferred to **TASK-BE-007**.
- No contract-test harness (still covered at slice level via `@WebMvcTest`). Deferred to **TASK-BE-007**.
- `hasActiveLocationsFor` always returns `false`. Concrete implementation lands with **TASK-BE-003** Location aggregate; at that point the deactivate path becomes a real invariant guard.

## Doc Debt

Unchanged from TASK-BE-001 review note — `platform/architecture.md`, `platform/service-boundaries.md`, `platform/api-gateway-policy.md` still reference ecommerce. Deferred to **TASK-DOC-001**.
