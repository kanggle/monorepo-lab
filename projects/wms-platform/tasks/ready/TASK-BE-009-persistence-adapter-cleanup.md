# Task ID

TASK-BE-009

# Title

Persistence-adapter cleanup — remove redundant `existsById` pre-checks, narrow exception translation, fix Javadoc drift

# Status

ready

# Owner

backend

# Task Tags

- code
- refactor

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

Address pattern-level debt flagged across the TASK-BE-001, TASK-BE-002, and
TASK-BE-003 reviews:

1. **Redundant `existsById` pre-check before `saveAndFlush`** in
   `WarehousePersistenceAdapter.update()`, `ZonePersistenceAdapter.update()`,
   and `LocationPersistenceAdapter.update()`. The service layer's
   `loadOrThrow` already guarantees the entity exists; the adapter-level
   check is a spurious extra SELECT.
2. **Overly broad `DataIntegrityViolationException` catch** in
   `ZonePersistenceAdapter.insert()` — the current catch remaps every
   integrity violation to `ZoneCodeDuplicateException`. FK violations or
   CHECK-constraint violations would be misclassified. Narrow to constraint
   name (`uq_zones_warehouse_code`).
3. **`ZonePersistencePort.hasActiveLocationsFor` Javadoc says "stubbed to
   `false`"** — but TASK-BE-003 replaced the stub with a real query. The
   Javadoc contradicts the implementation. Also update the comment in
   `ZonePersistenceAdapter.hasActiveLocationsFor` if it still claims stub.
4. **`SkuPersistenceAdapter.translateIntegrityViolation`** uses a
   lowercased-message substring match to detect the constraint name
   (`uq_skus_sku_code` vs `uq_skus_barcode`). This can silently fall through
   to 500 on an internationalized Postgres error message. Switch to
   `PSQLException.getServerErrorMessage().getConstraintName()` where
   available, falling back to message inspection only as a last resort.

---

# Scope

## In Scope

- `WarehousePersistenceAdapter.update()`, `ZonePersistenceAdapter.update()`,
  `LocationPersistenceAdapter.update()` — remove `existsById` pre-check;
  rely on the service-layer `loadOrThrow` contract
- `ZonePersistenceAdapter.insert()` — narrow the catch to inspect the
  constraint name before remapping
- `ZonePersistencePort.java` — fix the Javadoc on `hasActiveLocationsFor`
  to reflect the real implementation landed in TASK-BE-003
- `ZonePersistenceAdapter.hasActiveLocationsFor` — align inline comment
  with the actual query
- `SkuPersistenceAdapter.translateIntegrityViolation` — prefer
  `getConstraintName()` over message matching; keep message fallback
- Tests: add an H2-level assertion that a deliberate FK violation on Zone
  does NOT surface as `ZoneCodeDuplicateException` (proves the narrowed
  catch); add an H2 test case for SKU barcode-duplicate translation via
  constraint name

## Out of Scope

- Refactoring the persistence adapters to use a new `BaseAdapter` superclass
- Changing the mapper pattern (`toInsertEntity` / `toEntity`)
- Moving existence-check logic into a new port layer

---

# Acceptance Criteria

- [ ] No adapter's `update()` calls `existsById` before `saveAndFlush`
- [ ] `ZonePersistenceAdapter.insert()` translates ONLY the compound unique
      constraint violation to `ZoneCodeDuplicateException`; any other
      integrity violation rethrows as-is
- [ ] `ZonePersistencePort.hasActiveLocationsFor` Javadoc reflects the
      real implementation (no longer says "stubbed")
- [ ] `SkuPersistenceAdapter.translateIntegrityViolation` uses
      `getConstraintName()` first
- [ ] Existing H2 and Testcontainers tests still pass
- [ ] New H2 test: inserting a zone with a non-existent warehouseId FK
      fails with an exception that is NOT `ZoneCodeDuplicateException`
- [ ] New H2 test: barcode-duplicate on SKU translates correctly even when
      the error message only contains the constraint name (simulate with
      a mock exception where needed)

---

# Related Specs

- `platform/testing-strategy.md`
- `.claude/skills/backend/architecture/hexagonal/SKILL.md`

# Related Contracts

- None (internal refactor; no contract change)

---

# Target Service

- `master-service`

---

# Implementation Notes

- **`existsById` removal**: the service layer's `loadOrThrow` throws a 404
  exception before calling `update()`. The adapter never sees a non-existent
  id in practice. If a concurrent delete somehow occurs between load and
  save, Hibernate surfaces `ObjectOptimisticLockingFailureException` or
  `StaleStateException` — both translate to the existing conflict handler.
  Removing the pre-check saves a SELECT per mutation with no behavior
  change.
- **Constraint-name detection** on Postgres: `SQLException.getSQLState()
  == "23505"` identifies unique violations; cast to `PSQLException` and
  call `getServerErrorMessage().getConstraintName()`. On H2 the exception
  hierarchy differs — `org.h2.api.ErrorCode` + cause message. Keep both
  paths.
- **Tests without Docker**: H2 supports FK CASCADE = NO ACTION by default;
  inserting with a bogus `warehouseId` UUID will trip `DataIntegrityViolationException`
  rooted at a FK violation message, not the unique-constraint message.

---

# Edge Cases

- Concurrent delete of the parent warehouse while a zone update is in
  flight — the optimistic-lock path handles it.
- Postgres error-message localization — covered by `getConstraintName()`.
- Null `rootCause().getMessage()` — handle defensively (fallthrough to
  rethrow).

---

# Failure Scenarios

- Removing `existsById` in `update()` has no behavioral impact at the
  public HTTP layer as long as the service always `loadOrThrow`s first.
  If a future service bypasses this contract, the adapter will no longer
  issue a clean 404 — the subsequent save will fail with a different
  exception. This is an acceptable trade-off documented here.

---

# Test Requirements

- Extend `ZonePersistenceAdapterH2Test` with an FK-violation case
- Extend `SkuPersistenceAdapterH2Test` with an explicit barcode-duplicate
  case (two SKUs with the same non-null barcode)
- Existing `update()` tests still pass with one fewer query (verify by
  reading the test output if using query logging)

---

# Definition of Done

- [ ] Refactor landed
- [ ] Tests added and green
- [ ] Javadoc drift corrected
- [ ] Review note records the per-adapter diff and any pattern extracted
- [ ] Ready for review
