# Task ID

TASK-BE-004

# Title

Add SKU aggregate to master-service — domain, persistence, application, HTTP, outbox

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

Extend `master-service` with the **SKU** (Stock Keeping Unit) aggregate.
SKU is the first **independent** aggregate the service gains after the
Warehouse → Zone → Location hierarchy. It has no parent references; its
invariants are intrinsic.

Three concerns SKU introduces that prior aggregates did not:

1. **Two separate unique constraints** — `sku_code` (case-insensitive,
   always present) and `barcode` (optional, unique when non-null)
2. **Two lookup endpoints** beyond the usual six — `GET
   /api/v1/master/skus/by-code/{skuCode}` and `GET
   /api/v1/master/skus/by-barcode/{barcode}` (scanner path)
3. **Immutability of operational fields** — `sku_code`, `baseUom`,
   `trackingType` cannot change after create (downstream services cache
   these; renaming breaks their semantics)

On completion:

- `com.wms.master.domain.model.Sku` + supporting classes follow the
  Hexagonal + POJO pattern established by Warehouse/Zone/Location.
- All endpoints in `specs/contracts/http/master-service-api.md §4` are
  implemented.
- Each mutation writes an outbox row and the publisher emits on
  `wms.master.sku.v1` with the envelope defined in
  `specs/contracts/events/master-events.md §4`.
- The "Lot" active-children guard **is stubbed for now** — same seam
  Zone used before Location existed. Concrete implementation lands with
  **TASK-BE-006** (Lot aggregate).

Partner and Lot remain out of scope (TASK-BE-005, TASK-BE-006).

---

# Scope

## In Scope

- Domain: `Sku` aggregate, `BaseUom` enum (`EA / BOX / PLT / KG / L`),
  `TrackingType` enum (`NONE / LOT`), reuse `WarehouseStatus`
- Domain exceptions: `SkuNotFoundException`, `SkuCodeDuplicateException`,
  `BarcodeDuplicateException`
- Domain events: `SkuCreatedEvent`, `SkuUpdatedEvent`,
  `SkuDeactivatedEvent`, `SkuReactivatedEvent` extending the sealed
  `DomainEvent`
- Flyway `V5__init_sku.sql`: `skus` table with:
  - Global `UNIQUE (sku_code)` — but with a functional twist: compare in a
    case-insensitive way. Two options:
    1. Store `sku_code` always **UPPERCASE** and apply a `CHECK` constraint
       that `sku_code = UPPER(sku_code)`. Unique constraint is on the raw
       column. Domain layer uppercases on factory input.
    2. Use `UNIQUE (UPPER(sku_code))` as a functional index. Store raw.
    **Pick option 1** — simpler for H2 parity and downstream consumers
    reading the column see a consistent form.
  - Partial `UNIQUE (barcode)` where `barcode IS NOT NULL` — Postgres
    supports `CREATE UNIQUE INDEX ... WHERE barcode IS NOT NULL`.
    (H2 parity note: H2 2.x supports partial indexes; verify.)
  - Index on `(status)` for active-filter list queries
- Persistence: `SkuJpaEntity`, `JpaSkuRepository` (with `existsBySkuCode`,
  `existsByBarcode`, `findBySkuCodeIgnoreCase`, `findByBarcode`,
  filter-based page), `SkuPersistenceMapper` (with `toInsertEntity()`
  version=null), `SkuPersistenceAdapter` (`@Repository`). Translate both
  unique-constraint violations to the right domain exception (distinguish
  via SQLState / constraint name).
- Application ports: `SkuPersistencePort`, `SkuCrudUseCase`,
  `SkuQueryUseCase`
- Commands / queries / result mirror Warehouse
- `SkuService` with `@Transactional` + `@PreAuthorize` role matrix identical
  to Warehouse/Zone/Location
- HTTP adapter: single `SkuController` at `/api/v1/master/skus` covering
  all 8 endpoints (§4.1–4.8 plus the two lookup endpoints). Flat routing,
  no nesting.
- DTOs + `GlobalExceptionHandler` extensions (`SkuNotFoundException` → 404
  `SKU_NOT_FOUND`, `SkuCodeDuplicateException` → 409 `SKU_CODE_DUPLICATE`,
  `BarcodeDuplicateException` → 409 `BARCODE_DUPLICATE`)
- Outbox wiring: `EventEnvelopeSerializer` cases for Sku;
  `MasterOutboxPollingScheduler` verified for `master.sku.*` → `wms.master.sku.v1`
- **Lot active-children stub** on `SkuPersistencePort` (`hasActiveLotsFor`
  returning `false`) — same shape as Zone's pre-Location guard
- Seed migration `V102__seed_dev_skus.sql` — three fixed-UUID SKUs, mix
  of `NONE` and `LOT` tracking, under the `dev` profile
- Unit + slice tests (domain, application service with fakes, `@WebMvcTest`,
  `@DataJpaTest` with Testcontainers + H2 mirror)

## Out of Scope

- Partner / Lot aggregates
- Full `@SpringBootTest` integration suite (TASK-BE-007)
- Bulk SKU import (CSV)
- Barcode validation beyond presence/uniqueness (no EAN/UPC check-digit
  validation in v1)
- Serial tracking (`SERIAL` tracking type) — v2
- Cross-service inventory reference check on deactivate (v2 saga)
- Image/media fields on SKU (out of spec)
- Pricing — explicitly not an SKU concern (lives in a pricing service if
  a future project adds one)

---

# Acceptance Criteria

- [ ] `./gradlew :projects:wms-platform:apps:master-service:check` passes
- [ ] `POST /api/v1/master/skus` creates an SKU and returns `201`
      matching §4.1; sku_code is stored UPPERCASE regardless of input case
- [ ] `GET /api/v1/master/skus/{id}` returns `200` with `ETag`; unknown id
      → `404 SKU_NOT_FOUND`
- [ ] `GET /api/v1/master/skus` paginates with filters (`status`, `q`,
      `trackingType`, `baseUom`, `barcode`), default `status=ACTIVE`
- [ ] `GET /api/v1/master/skus/by-code/{skuCode}` returns `200` for an
      existing SKU regardless of input case; `404` if absent
- [ ] `GET /api/v1/master/skus/by-barcode/{barcode}` returns `200` when a
      unique SKU matches; `404` if absent
- [ ] `PATCH /{id}` mutates `name`, `description`, `barcode`,
      `weightGrams`, `volumeMl`, `hazardClass`, `shelfLifeDays`; attempts
      to change `skuCode`, `baseUom`, `trackingType` → `422 IMMUTABLE_FIELD`
- [ ] PATCH with wrong `version` → `409 CONFLICT`
- [ ] `POST /{id}/deactivate` / `/reactivate` toggles status; invalid → `409`
- [ ] Deactivate is **blocked** when `hasActiveLotsFor(skuId)` returns
      `true` — still stubbed `false` in this task, but the application
      service logic + slice test exercise the code path
- [ ] Duplicate `skuCode` (any casing variant of an existing code) → `409
      SKU_CODE_DUPLICATE`
- [ ] Duplicate `barcode` (when non-null) → `409 BARCODE_DUPLICATE`;
      multiple SKUs with null barcode succeed
- [ ] Each mutation writes one outbox row in same tx; publisher forwards
      to `wms.master.sku.v1`
- [ ] Event envelope matches `specs/contracts/events/master-events.md §4`
- [ ] `V102__seed_dev_skus.sql` creates three SKUs (two `NONE`, one `LOT`
      with `shelfLifeDays`) under `dev` profile; `ON CONFLICT DO NOTHING`

---

# Related Specs

> **Before reading Related Specs**: Follow `platform/entrypoint.md` Step 0 — read `PROJECT.md`, then load `rules/common.md` plus `rules/domains/wms.md`, `rules/traits/transactional.md`, `rules/traits/integration-heavy.md`.

- `platform/error-handling.md`
- `platform/testing-strategy.md`
- `platform/service-types/rest-api.md`
- `specs/services/master-service/architecture.md`
- `specs/services/master-service/domain-model.md` §4 SKU
- `specs/services/master-service/idempotency.md`
- `rules/domains/wms.md`
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

- `specs/contracts/http/master-service-api.md` §4 SKU (4.1–4.8)
- `specs/contracts/events/master-events.md` §4 SKU Events

---

# Target Service

- `master-service`

---

# Architecture

Follow:

- `specs/services/master-service/architecture.md` (Hexagonal, rest-api)
- Warehouse is the authoritative reference (no parent refs like SKU).
  Location is a partial reference for the Lot stub seam.

---

# Implementation Notes

- **Case-insensitive `skuCode`** — pick option 1 from the Scope: store
  UPPERCASE, factory calls `skuCode.toUpperCase(Locale.ROOT)`. This way
  the DB's `UNIQUE (sku_code)` is a straight unique constraint; no
  functional index needed. Downstream consumers see a consistent value.
  Query endpoints (`by-code`, list filters) must also uppercase the
  argument before lookup.
- **Partial unique on `barcode`** — Postgres syntax:
  `CREATE UNIQUE INDEX uq_skus_barcode ON skus (barcode) WHERE barcode IS NOT NULL;`
  H2 2.x supports the same syntax. Declared as a raw SQL line in the
  migration rather than via `@UniqueConstraint` (JPA `@UniqueConstraint`
  does not support a filter). On the JPA entity side, no annotation is
  needed — the index exists purely at the DB level.
- **Insert path** — `toInsertEntity()` emits `version=null`. Same as prior
  aggregates.
- **Exception translation** — the adapter must distinguish between the two
  unique constraints by constraint name in the caught
  `DataIntegrityViolationException`. `SkuCodeDuplicateException` for
  `uq_skus_sku_code` (or whatever the migration names it),
  `BarcodeDuplicateException` for `uq_skus_barcode`. Check the raw SQL
  message via `rootCause().getMessage()` if driver-level access is needed;
  both Postgres and H2 expose the constraint name.
- **Lot active-children stub** — place `hasActiveLotsFor(UUID skuId)` on
  `SkuPersistencePort`. Default implementation on the adapter returns
  `false`. When TASK-BE-006 lands, replace with a real
  `JpaLotRepository.existsBySkuIdAndStatus` call — same shape as Location
  turned on Zone's guard.
- **Tracking type transition** — `trackingType` is immutable per spec.
  The domain factory accepts it; `applyUpdate()` does not expose it.
  `rejectImmutableChange()` throws `ImmutableFieldException` for
  `trackingType`, `baseUom`, and `skuCode` equally.
- **Query endpoints** — `/by-code/{skuCode}` uppercases the path variable
  and calls `findBySkuCodeIgnoreCase`. `/by-barcode/{barcode}` is exact
  match (no case fold for barcodes — EAN/UPC are all digits).
- **Role matrix** — identical to Warehouse/Zone/Location.

---

# Edge Cases

- Duplicate `skuCode` with different casing (e.g. `sku-apple-001` vs
  `SKU-APPLE-001`) → `409 SKU_CODE_DUPLICATE` because both normalize to
  the same uppercase value.
- Two SKUs without a barcode — both pass the partial unique constraint.
- `PATCH` body with `skuCode` / `baseUom` / `trackingType` → `422
  IMMUTABLE_FIELD`.
- `trackingType = LOT` without `shelfLifeDays` — spec says "warning, not
  error". Domain does not reject; a future ops dashboard could surface
  the warning via a soft-validation service. No behavior in this task.
- Barcode `"0"` — valid (single digit). We do not enforce EAN/UPC format.
- `q` filter in list — case-insensitive substring match against `name`
  and `sku_code`. Implementation via JPA Specification or a
  `LOWER(name) LIKE ?` query.
- `GET /by-code/` with a code that has URL-reserved characters — spec
  implies standard URL-encoded; Spring decodes. No special handling.
- Deactivate-without-lots-yet-in-this-task case — the stub returns
  `false`, so deactivate always succeeds. The test asserts the stub is
  consulted (not just that deactivate works).

---

# Failure Scenarios

- Postgres down at boot / during request — same as Warehouse.
- Kafka down at publish time — outbox rows accumulate per normal.
- Concurrent SKU creation with the same (uppercased) code — second insert
  fails on unique constraint → `SKU_CODE_DUPLICATE`.
- Concurrent PATCH with the same version — second returns `409 CONFLICT`
  (optimistic lock).
- H2 partial-index syntax not accepted — extremely unlikely on H2 2.x but
  if encountered, author the migration with H2-compatible syntax and
  declare a Postgres variant via a repeatable migration. Document in the
  review note.

---

# Test Requirements

## Unit Tests

- `Sku` domain model — factory validation (pattern, casing
  normalization, required fields), update/immutability, state transitions.
- `SkuService` application tests with `FakeSkuPersistencePort` — every
  error path including duplicate code (any casing), duplicate barcode,
  immutable-field mutation, active-lots-present (via fake stub returning
  `true`).

## Slice Tests

- `@WebMvcTest(SkuController.class)` — validation, status mapping, ETag,
  both lookup endpoints, case-insensitive `by-code` path.
- `@DataJpaTest` on `SkuPersistenceAdapter` (Testcontainers + H2 mirror)
  — `skuCode` uniqueness (case-insensitive via UPPERCASE storage),
  `barcode` partial uniqueness (two nulls OK, two non-null equal rejected),
  optimistic lock, insert path.

## Event Contract

- Extend `EventEnvelopeSerializerTest` with one case per SKU event.

## Smoke

- Extend `ApplicationContextSmokeTest` for `SkuController` +
  `SkuCrudUseCase` + `SkuQueryUseCase` beans.

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added (unit / slice / event-contract; smoke)
- [ ] Tests passing locally and in CI
- [ ] Contracts unchanged
- [ ] Seed migration added + verified under `dev` profile
- [ ] Review note covers deviations + follow-ups (particularly anything
      around the partial unique index or case-insensitive strategy)
- [ ] Ready for review
