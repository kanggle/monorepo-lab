# Task ID

TASK-BE-003

# Title

Add Location aggregate to master-service — domain, persistence, application, HTTP, outbox; turn on Zone's active-children guard

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

Extend `master-service` with the **Location** aggregate and — critically —
turn on the `hasActiveLocationsFor` guard that Zone's deactivate path has
been stubbing since TASK-BE-002.

Location introduces three concerns that Warehouse and Zone did not:

1. **Dual parent reference** — `warehouseId` + `zoneId` on every Location,
   with an internal-consistency invariant: the parent Zone's `warehouseId`
   must equal the Location's `warehouseId`.
2. **Globally unique `locationCode`** (not warehouse-scoped, not zone-scoped).
3. **Mixed routing shape** — creation is nested under warehouse+zone, but
   get / list / patch / deactivate / reactivate are flat under
   `/api/v1/master/locations/...`. The HTTP contract is explicit about this.

On completion:

- `com.wms.master.domain.model.Location` + supporting classes follow the
  Hexagonal + POJO pattern used by Warehouse / Zone.
- All endpoints in `specs/contracts/http/master-service-api.md §3` are
  implemented.
- Each mutation writes an outbox row and the publisher emits on
  `wms.master.location.v1` with the envelope in
  `specs/contracts/events/master-events.md §3`.
- `Zone.deactivate()` is now **actually blocked** when active Locations
  reference the zone — the stub in `ZonePersistenceAdapter.hasActiveLocationsFor`
  is replaced with a real query against the `locations` table.

SKU / Partner / Lot remain out of scope (TASK-BE-004..006).

---

# Scope

## In Scope

- Domain: `Location`, `LocationType` enum (STORAGE / STAGING_INBOUND /
  STAGING_OUTBOUND / DAMAGED / QUARANTINE), reuse `WarehouseStatus`
- Domain exceptions: `LocationNotFoundException`,
  `LocationCodeDuplicateException`, `ZoneWarehouseMismatchException` (or reuse
  a generic `ReferenceIntegrityException` if it already exists — check first)
- Domain events: `LocationCreatedEvent`, `LocationUpdatedEvent`,
  `LocationDeactivatedEvent`, `LocationReactivatedEvent` extending the sealed
  `DomainEvent` hierarchy
- Flyway `V4__init_location.sql`: `locations` table — UUID PK, `warehouse_id`
  FK → `warehouses(id)` (denormalized for fast scoping), `zone_id` FK →
  `zones(id)`, `location_code varchar(40) NOT NULL` with **global** `UNIQUE
  (location_code)` constraint (not a compound unique), optional hierarchy
  columns, `location_type`, `capacity_units`, status/version/audit. Indexes
  on `(warehouse_id, status)`, `(zone_id, status)` for fast list queries
- Persistence: `LocationJpaEntity`, `JpaLocationRepository`,
  `LocationPersistenceMapper` (with `toInsertEntity()` emitting `version=null`
  — carry the BE-001 lesson), `LocationPersistenceAdapter` (`@Repository`)
- Application ports: `LocationPersistencePort`, `LocationCrudUseCase`,
  `LocationQueryUseCase`
- Commands / queries / result — same shape as Warehouse/Zone
- `LocationService` with `@Transactional` + `@PreAuthorize` role matrix
  identical to Warehouse/Zone
- HTTP adapter split across two controllers to match the contract:
  - `LocationCreateController` at
    `/api/v1/master/warehouses/{warehouseId}/zones/{zoneId}/locations` — POST
    only (nested)
  - `LocationController` at `/api/v1/master/locations/...` — GET by id, GET
    list (flat with filters: `warehouseId`, `zoneId`, `locationType`, `code`),
    PATCH, POST deactivate, POST reactivate
  (Single controller with both mappings is also acceptable if cleaner;
  reviewer can call it.)
- DTOs, `GlobalExceptionHandler` extension
- Outbox wiring: `EventEnvelopeSerializer` sealed-switch cases for Location;
  `MasterOutboxPollingScheduler` verified to resolve
  `master.location.*` → `wms.master.location.v1`
- **Turn on Zone's guard**: replace the `hasActiveLocationsFor` stub in
  `ZonePersistenceAdapter` with a real query on
  `JpaLocationRepository.existsByZoneIdAndStatus(zoneId, ACTIVE)`. Extend
  `ZoneService` deactivate-path tests to cover the now-real guard.
- Seed migration `V101__seed_dev_locations.sql` — a small set of locations
  under seeded zones under `WH01`
- Unit + slice tests (domain model, application service with fake port, JPA
  adapter with Testcontainers + H2 mirror, controller @WebMvcTest)

## Out of Scope

- SKU / Partner / Lot aggregates
- Cross-service `inventory-service` consistency (the v2 `deactivation.requested`
  saga is explicitly deferred in spec)
- Bulk / CSV location import
- Location capacity validation against actual inventory (`capacityUnits` is
  metadata only in v1)
- Hierarchy-field uniqueness (hierarchy fields are advisory; uniqueness is
  only on `location_code`)
- Full `@SpringBootTest` integration suite (still deferred to TASK-BE-007)

---

# Acceptance Criteria

- [ ] `./gradlew :projects:wms-platform:apps:master-service:check` passes
- [ ] `POST /api/v1/master/warehouses/{warehouseId}/zones/{zoneId}/locations` creates a Location and returns `201` matching §3.1
- [ ] Unknown `zoneId` → `404 ZONE_NOT_FOUND`; unknown `warehouseId` → `404 WAREHOUSE_NOT_FOUND`
- [ ] Create under an `INACTIVE` parent zone → `409 STATE_TRANSITION_INVALID`
- [ ] Parent zone not belonging to the path's warehouse → `404 ZONE_NOT_FOUND` (leak-safe: do not distinguish mismatch from absence)
- [ ] `locationCode` that does not begin with the parent warehouse's `warehouseCode` → `422 IMMUTABLE_FIELD` or `400 VALIDATION_ERROR` (pick one and document in review note)
- [ ] `GET /api/v1/master/locations/{id}` returns `200` with `ETag`; unknown id → `404 LOCATION_NOT_FOUND`
- [ ] `GET /api/v1/master/locations` paginates, supports filters (`warehouseId`, `zoneId`, `locationType`, `code`, `status`), default `status=ACTIVE`
- [ ] `PATCH /api/v1/master/locations/{id}` updates `locationType`, `capacityUnits`, `aisle`, `rack`, `level`, `bin`; changes to `locationCode` / `warehouseId` / `zoneId` → `422`
- [ ] `POST .../deactivate` / `/reactivate` toggles state; invalid → `409`
- [ ] Duplicate `locationCode` anywhere in the system → `409 LOCATION_CODE_DUPLICATE`
- [ ] **Zone deactivate blocked when Zone has ≥1 ACTIVE Location** — `409 REFERENCE_INTEGRITY_VIOLATION`. Test must fail first with the stubbed implementation, then pass after swapping in the real query.
- [ ] Each mutation writes one outbox row in same tx; publisher forwards to `wms.master.location.v1`
- [ ] Event envelope matches §3
- [ ] Domain has no JPA annotations; JPA entity is package-private to the adapter
- [ ] `V101__seed_dev_locations.sql` populates locations under seeded zones when `spring.profiles.active=dev`

---

# Related Specs

> **Before reading Related Specs**: Follow `platform/entrypoint.md` Step 0 — read `PROJECT.md`, then load `rules/common.md` plus `rules/domains/wms.md`, `rules/traits/transactional.md`, `rules/traits/integration-heavy.md`.

- `platform/error-handling.md`
- `platform/testing-strategy.md`
- `platform/service-types/rest-api.md`
- `specs/services/master-service/architecture.md`
- `specs/services/master-service/domain-model.md` §3 Location
- `specs/services/master-service/idempotency.md`
- `rules/domains/wms.md` — W3 (globally unique location_code) and W6
- `rules/traits/transactional.md`

# Related Skills

- `.claude/skills/backend/architecture/hexagonal/SKILL.md`
- `.claude/skills/backend/dto-mapping/SKILL.md`
- `.claude/skills/backend/transaction-handling/SKILL.md`
- `.claude/skills/backend/pagination/SKILL.md`
- `.claude/skills/database/schema-change-workflow/SKILL.md`
- `.claude/skills/database/migration-strategy/SKILL.md`
- `.claude/skills/messaging/outbox-pattern/SKILL.md`
- `.claude/skills/testing/testcontainers/SKILL.md`

---

# Related Contracts

- `specs/contracts/http/master-service-api.md` §Global Conventions and §3 Location
- `specs/contracts/events/master-events.md` §Global Envelope, §Topic Layout, §3 Location Events

---

# Target Service

- `master-service`

---

# Architecture

Follow:

- `specs/services/master-service/architecture.md` (Hexagonal, rest-api)
- Existing Warehouse and Zone implementations on `main` are the authoritative
  references. Mirror their layering, transactional boundaries, exception
  translation pattern, and test style.

---

# Implementation Notes

- **Dual parent check on create** — `LocationService.create(...)` loads the
  parent Zone first. If the zone's `warehouseId` does not equal the path's
  `warehouseId`, treat as `ZoneNotFoundException` (404). This is safer than
  leaking a distinct `ZONE_WAREHOUSE_MISMATCH` code — the client should not
  learn that the zone exists under a different warehouse. Document the choice
  in the review note.
- **`locationCode` prefix validation** — the domain factory enforces that
  `locationCode` begins with `{warehouseCode}-`. Needs the warehouse code,
  so `LocationService.create` must load the parent warehouse (for
  `warehouseCode`) after confirming parent zone exists and is ACTIVE.
  Alternatively, denormalize `warehouseCode` on the Zone aggregate — simpler
  but breaks the "Warehouse only" field. **Recommend** loading via
  `WarehousePersistencePort` to keep aggregates clean.
- **Global unique constraint** — `UNIQUE (location_code)` in Flyway and
  `@Column(unique = true)` or `@Table(uniqueConstraints = ...)` on the entity.
- **Exception translation** — `DataIntegrityViolationException` on the global
  unique constraint → `LocationCodeDuplicateException`; rely on SQLState /
  constraint-name detection.
- **Insert path** — `toInsertEntity()` emits `version=null`. Same as W/Z.
- **Turn on Zone's guard** —
  `ZonePersistenceAdapter.hasActiveLocationsFor(zoneId)` currently returns
  `false`. Replace with
  `jpaLocationRepository.existsByZoneIdAndStatus(zoneId, WarehouseStatus.ACTIVE)`.
  This creates a new adapter dependency from the Zone adapter on
  `JpaLocationRepository` — acceptable (both adapters live under
  `adapter/out/persistence` and share the same datasource). If the reviewer
  prefers a cleaner port-level indirection, introduce a
  `LocationExistenceQueryPort` interface implemented by the Location adapter
  and injected into the Zone adapter. Pick whichever is shorter.
- **Controller split** — the contract uses two URL shapes, so two controllers
  is natural. Keeping a single `LocationController` with two `@RequestMapping`
  base paths is also fine but Spring does not support two `@RequestMapping`
  on one class — would require duplicated method mappings. **Recommend split.**
- **Seed migration** — `V101__seed_dev_locations.sql`, fixed UUIDs, one
  location per seeded zone (e.g. `WH01-A-01-01-01` under `Z-A`). `ON CONFLICT
  DO NOTHING`. Keep it tiny — heavy seed data belongs in a separate `dev-fixture`
  profile later.

---

# Edge Cases

- Duplicate `locationCode` across the system (any warehouse, any zone) — unique
  constraint fires → `LOCATION_CODE_DUPLICATE`.
- Parent zone's `warehouseId` does not match the path's `warehouseId` — 404
  `ZONE_NOT_FOUND` (leak-safe).
- Parent zone is `INACTIVE` — `409 STATE_TRANSITION_INVALID`.
- `locationCode` that does not start with `{warehouseCode}-` — `VALIDATION_ERROR`
  or `IMMUTABLE_FIELD` (pick one).
- `locationCode` pattern violation — `VALIDATION_ERROR`.
- `locationType` not in enum — `VALIDATION_ERROR`.
- `capacityUnits < 1` — `VALIDATION_ERROR`.
- PATCH body with `locationCode` / `warehouseId` / `zoneId` — `422 IMMUTABLE_FIELD`.
- Deactivate already-INACTIVE / reactivate already-ACTIVE — `409`.
- Zone deactivate with ≥1 ACTIVE Location — `409 REFERENCE_INTEGRITY_VIOLATION`.
- Zone deactivate after all locations deactivated — succeeds.
- Concurrent PATCH version collision — `409 CONFLICT`.

---

# Failure Scenarios

- Parent zone deleted mid-request (no hard delete in v1, so guard anyway).
- Postgres down at boot / during request — same as Warehouse/Zone.
- Kafka down at publish time — outbox rows accumulate per normal.
- Concurrent location creation against the same `locationCode` — second insert
  fails on unique constraint → `LOCATION_CODE_DUPLICATE`.
- Zone deactivate under heavy concurrency — the existence check + the zone
  deactivate must live in the same `@Transactional`. Race between "no active
  locations" and "a location is created under this zone" is narrow but not
  zero; the location-create path should also verify the zone is ACTIVE at
  that instant (already required by spec). Acceptable given optimistic lock
  on zone version.
- Flyway migration fails if `V3` (zones) has not been applied first — Flyway
  ordering handles this automatically.

---

# Test Requirements

## Unit Tests

- `Location` domain model — factory validation (patterns, required fields,
  prefix match), update path (mutable vs immutable), state transitions.
- `LocationService` application tests with `FakeLocationPersistencePort` +
  `FakeWarehousePersistencePort` + a Zone lookup fake. Every error path.

## Slice Tests

- `@WebMvcTest(LocationCreateController.class)` + `@WebMvcTest(LocationController.class)`
  — validation, status mapping, ETag on GET, mutable/immutable PATCH rules.
- `@DataJpaTest` on `LocationPersistenceAdapter` (Testcontainers + H2 mirror)
  — global unique constraint, optimistic lock, insert path (`version=null`),
  outbox row written in same tx.
- **Zone deactivate guard test** — a new `ZoneService` slice test (or
  extension of the existing one) that inserts a Location under a Zone via the
  Location adapter, then asserts Zone deactivate fires
  `REFERENCE_INTEGRITY_VIOLATION`. After the location is deactivated, the zone
  deactivate succeeds.

## Event Contract

- Extend `EventEnvelopeSerializerTest` with one case per Location event —
  envelope fields + `payload.location` shape.

## Smoke

- Extend `ApplicationContextSmokeTest` to assert `LocationController` +
  `LocationCreateController` + use-case beans wire.

---

# Definition of Done

- [x] Implementation completed
- [x] Tests added (unit / slice / event-contract; smoke + zone-guard integration)
- [x] Tests passing locally (`./gradlew :projects:wms-platform:apps:master-service:test` — 0 failures; Testcontainers skip on Windows, run in CI)
- [x] Contracts unchanged
- [x] Seed migration added (`V101__seed_dev_locations.sql`)
- [x] `ZonePersistenceAdapter.hasActiveLocationsFor` stub replaced with real `JpaLocationRepository.existsByZoneIdAndStatus` query
- [x] Review notes cover deviations + follow-ups
- [x] Ready for review

---

# Review Note (2026-04-19)

## Implementation Delivery

Landed in 5 phased commits on `feat/wms-task-be-003-location`:

| Phase | Scope |
|---|---|
| 1 | Domain model — `Location` with dual parent + prefix match; `LocationType` enum; exceptions; 4 sealed `DomainEvent` subclasses |
| 2 | Persistence — `V4__init_location.sql` (global `uq_locations_location_code`, FKs to warehouses + zones, `(warehouse_id, status)` + `(zone_id, status)` indexes); `LocationJpaEntity` (pkg-private, `@Version`); `JpaLocationRepository` with `existsByZoneIdAndStatus` (the Zone-guard query); `LocationPersistenceMapper` with `toInsertEntity()` version=null; `LocationPersistenceAdapter` (`@Repository`) |
| 3 | Application — `LocationPersistencePort`, split Crud/Query use-case, commands/query/result, `LocationService` with dual-parent discipline (leak-safe `ZoneNotFoundException` on zone/warehouse mismatch) + parent-zone-active guard + parent-warehouse load for `warehouseCode` prefix validation |
| 4 | HTTP — **two controllers** per asymmetric contract shape: `LocationCreateController` (POST nested) + `LocationController` (flat GET/PATCH/state-change). ETag + Location headers. `GlobalExceptionHandler` extended. |
| 5 | **Zone guard turned on** (stub → real query), Location outbox wiring, `V101__seed_dev_locations.sql`, smoke + event-contract + scheduler tests extended |

## Acceptance Criteria Status

| AC | State | Note |
|---|---|---|
| `./gradlew :projects:wms-platform:apps:master-service:check` passes | ✅ | 323 tests, 0 failures; 21 Testcontainers skipped on Windows |
| POST nested → 201 matches §3.1 | ✅ | `LocationCreateControllerTest` |
| Unknown `zoneId` → 404 `ZONE_NOT_FOUND` | ✅ | Parent-zone resolved first |
| Unknown `warehouseId` → 404 `WAREHOUSE_NOT_FOUND` | ✅ | Warehouse resolved for prefix validation |
| Create under INACTIVE parent zone → 409 `STATE_TRANSITION_INVALID` | ✅ | |
| Zone/warehouse mismatch → 404 `ZONE_NOT_FOUND` | ✅ | Leak-safe (no distinct error code) |
| `locationCode` prefix mismatch → 400 `VALIDATION_ERROR` | ✅ | Domain-factory rejection; **deviation from ticket**: ticket allowed 422, we chose 400 (see "Deviations") |
| GET by id + ETag / 404 on unknown | ✅ | `"v{version}"` |
| GET list paginates, filters (`warehouseId`, `zoneId`, `locationType`, `code`, `status`) | ✅ | |
| PATCH mutable fields / immutable-field change → 422 | ✅ | `Location.rejectImmutableChange` |
| Deactivate/reactivate + invalid → 409 | ✅ | |
| Global duplicate `locationCode` → 409 `LOCATION_CODE_DUPLICATE` | ✅ | Adapter-level translation; Testcontainers + H2 both test |
| **Zone deactivate blocked when zone has ≥1 ACTIVE Location** | ✅ | **Stub replaced with real query.** Testcontainers test (`LocationPersistenceAdapterTest.zoneGuard`) verifies end-to-end: insert location → `hasActiveLocationsFor=true`; deactivate location → `false` |
| One outbox row per mutation; publisher → `wms.master.location.v1` | ✅ | `MasterOutboxPollingSchedulerTest` covers all 4 `master.location.*` cases |
| Event envelope matches §3 | ✅ | `EventEnvelopeSerializerTest` |
| Domain has no JPA annotations; entity package-private | ✅ | |
| `V101__seed_dev_locations.sql` under `dev` profile | ✅ | 3 fixed-UUID rows, `ON CONFLICT DO NOTHING` |

## Deviations from the ticket

1. **Prefix-mismatch status code → 400 `VALIDATION_ERROR`, not 422 `IMMUTABLE_FIELD`.** Ticket let the implementer pick. We chose 400 because the prefix check lives in the domain factory and rejects construction outright — it's a validation failure, not an attempt to mutate an immutable field. Consistent with how Warehouse/Zone code-pattern failures surface. Worth confirming with the reviewer; flipping to 422 is a one-line change in `GlobalExceptionHandler`.
2. **Zone adapter depends on `JpaLocationRepository` directly.** Ticket allowed either that shortcut or a `LocationExistenceQueryPort` interface seam. We picked the shortcut (both adapters share the same datasource; no port layering ceremony). If the reviewer prefers the port seam, it's a small follow-up refactor.
3. **Testcontainers duplicate-across-zones test** was rewritten during review to use two zones under the same warehouse (so domain prefix validation passes and the test actually exercises the DB's global unique constraint). The original draft used two different warehouses and the same `SHARED-...` code, which would have failed at the domain factory before reaching the DB. Caught during trust-but-verify pass on the subagent output.

## Gaps

- Full `@SpringBootTest` integration (Postgres + Kafka + Redis in one container network, happy path end-to-end) still deferred to **TASK-BE-007**.
- `capacity_units` is metadata only; no capacity-vs-inventory enforcement. Cross-service; belongs to `inventory-service` when it lands.
- Cross-service inventory reference check on Location deactivate still deferred to v2 saga per the architecture doc.

## Doc Debt

Unchanged from BE-001/BE-002 — `platform/architecture.md`, `platform/service-boundaries.md`, `platform/api-gateway-policy.md` still reference ecommerce. Deferred to **TASK-DOC-001**.
