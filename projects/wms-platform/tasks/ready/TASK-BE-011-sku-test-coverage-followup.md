# Task ID

TASK-BE-011

# Title

SKU test-coverage follow-up — `SkuControllerTest` + `SkuPersistenceAdapterTest` (Testcontainers)

# Status

ready

# Owner

backend

# Task Tags

- test
- code

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

Address the two test-coverage gaps documented in the TASK-BE-004 review
note:

1. `SkuControllerTest` — `@WebMvcTest` slice coverage for the 8 SKU
   endpoints. Every peer aggregate (Warehouse, Zone, Location) has this;
   SKU is the only gap.
2. `SkuPersistenceAdapterTest` (Testcontainers variant) — covers the
   partial barcode unique index, the `CHECK (sku_code = UPPER(sku_code))`
   constraint, and `BarcodeDuplicateException` translation against real
   Postgres. The H2 mirror cannot verify these because JPA
   `@UniqueConstraint` can't express the `WHERE` filter and H2's
   constraint-violation message may differ.

Also fix the broken `@link SkuPersistenceAdapterTest` Javadoc tag in
`SkuPersistenceAdapterH2Test` which currently points at a class that
doesn't exist.

---

# Scope

## In Scope

- `SkuControllerTest` (new) — mirror of `WarehouseControllerTest` with
  SKU-specific cases:
  - POST create with lowercase `skuCode` → 201 with response carrying
    UPPERCASE value
  - GET /{id} → 200 + ETag header `"v{version}"`; unknown id → 404
  - GET `/by-code/{skuCode}` — both exact and mixed-case input resolve to
    the same SKU (or 404)
  - GET `/by-barcode/{barcode}` — exact match + 404 on absent
  - PATCH with change to `skuCode` / `baseUom` / `trackingType` → 422
    `IMMUTABLE_FIELD`
  - PATCH with wrong `version` → 409 `CONFLICT`
  - POST `/{id}/deactivate` + `/reactivate` → ETag updated
  - Validation errors: missing `skuCode`, invalid `baseUom`, etc. → 400
- `SkuPersistenceAdapterTest` (new, Testcontainers) — mirror of
  `WarehousePersistenceAdapterTest`:
  - `@Testcontainers(disabledWithoutDocker = true)` + Postgres 16 alpine
  - Insert persists with version 0
  - Two SKUs with NULL barcode coexist (partial unique passes)
  - Two SKUs with the same non-null barcode → `BarcodeDuplicateException`
  - Attempting raw-SQL insert with mixed-case `sku_code` trips the CHECK
    constraint (this proves the defense-in-depth works against a bypass
    attempt)
  - Optimistic lock collision
  - Insert path (`version=null` mapper) exercised end-to-end
- Fix the broken `@link SkuPersistenceAdapterTest` Javadoc tag in
  `SkuPersistenceAdapterH2Test` (line 30) — now that the class exists,
  the link resolves

## Out of Scope

- Refactoring `SkuServiceTest` (already comprehensive per BE-004 review)
- Adding `SkuServiceAuthorizationTest` assertion hardening (that's a
  separate nit noted in the BE-004 review)
- Any production-code changes beyond what the tests expose (if a test
  finds a bug, file it as a new fix task rather than expanding scope here)

---

# Acceptance Criteria

- [ ] `SkuControllerTest` exists with ≥ 15 test methods covering the 8
      endpoints
- [ ] `SkuPersistenceAdapterTest` exists, annotated
      `@Testcontainers(disabledWithoutDocker = true)`, and covers the
      partial barcode unique + UPPERCASE CHECK + `BarcodeDuplicateException`
      cases
- [ ] `SkuPersistenceAdapterH2Test` line 30 Javadoc `@link` resolves
- [ ] `./gradlew check` passes
- [ ] On CI Linux (where Docker is available), the new Testcontainers
      test runs and passes

---

# Related Specs

- `platform/testing-strategy.md`
- `specs/contracts/http/master-service-api.md` §4 SKU
- `.claude/skills/testing/testcontainers/SKILL.md`

# Related Contracts

- `specs/contracts/http/master-service-api.md` §4

---

# Target Service

- `master-service`

---

# Implementation Notes

- **Reference patterns**: `WarehouseControllerTest` (~414 lines) and
  `WarehousePersistenceAdapterTest` (Testcontainers variant) are the
  closest templates. Adjust for SKU specifics (UPPERCASE normalization,
  lookup endpoints, immutable fields set).
- **ETag format**: `"v{version}"` — quoted. Both Spring's ResponseEntity
  builder and the controller code already produce this. Assertions should
  match the exact string.
- **Windows blocker**: `SkuPersistenceAdapterTest` cannot run locally on
  Windows due to Docker Desktop 4.x / Testcontainers blocker. It will
  verify on CI Linux. Do NOT disable or conditionally skip — use the
  standard `@Testcontainers(disabledWithoutDocker = true)` which is
  the accepted pattern across the project.
- **Raw-SQL CHECK constraint test**: use
  `EntityManager.createNativeQuery("INSERT INTO skus ...")` to bypass
  the domain factory's uppercase normalization and trigger the CHECK.
  Assert `DataIntegrityViolationException` (or more specifically, a
  CHECK-constraint violation surfaced by Hibernate's translator).

---

# Edge Cases

- Multiple SKUs with NULL barcode — must coexist (partial unique excludes NULL)
- SKU with barcode `"0"` — valid single-digit
- `by-code` with URL-encoded special chars — Spring decodes, uppercases
  the decoded form; ensure the test doesn't double-encode
- `by-barcode` exact-match semantics — no case fold, no partial match

---

# Failure Scenarios

- Testcontainers test flakes due to cold Postgres startup — use the
  default wait strategy (tests should tolerate up to 60s startup)
- H2's Javadoc @link no longer resolves (if the Testcontainers class is
  renamed) — fix in the same PR

---

# Test Requirements

- 15+ `SkuControllerTest` cases as outlined in Scope
- 5+ `SkuPersistenceAdapterTest` cases covering the 4 invariants
  (partial barcode unique, UPPERCASE CHECK, version 0 on insert,
  constraint-name exception translation)
- Overall suite (`./gradlew :projects:wms-platform:apps:master-service:test`)
  stays green; Testcontainers tests add ~30s on CI Linux

---

# Definition of Done

- [ ] Both test classes added
- [ ] H2 Javadoc `@link` resolves
- [ ] Tests passing locally (non-Testcontainers) and on CI (full suite)
- [ ] Ready for review
