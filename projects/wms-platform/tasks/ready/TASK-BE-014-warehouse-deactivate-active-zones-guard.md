# Task ID

TASK-BE-014

# Title

Add active-zones guard to `WarehouseService.deactivate` — contract compliance

# Status

ready

# Owner

backend

# Task Tags

- code
- api
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

Fix the contract divergence found during TASK-BE-010 review:

`specs/contracts/http/master-service-api.md` §1.5 requires Warehouse deactivate,
when blocked by active child Zones, to return `409 REFERENCE_INTEGRITY_VIOLATION`.
The current `WarehouseService.deactivate` performs no active-zones check before
deactivating — `ReferenceIntegrityViolationException` is never thrown and a
Warehouse can be deactivated while active Zones still exist under it.

The stale comment in `WarehouseService.java` line 88 reads "Zone ships in a later
task" but Zone aggregate shipped in TASK-BE-002. That comment must be corrected.

---

# Scope

## In Scope

- Add `hasActiveZonesFor(UUID warehouseId): boolean` to `WarehousePersistencePort`
- Implement the query in the Warehouse persistence adapter (mirrors
  `ZonePersistenceAdapter.hasActiveLocationsFor` pattern)
- `WarehouseService.deactivate` checks `hasActiveZonesFor` before calling
  `loaded.deactivate(...)` and throws `ReferenceIntegrityViolationException`
  when active Zones exist
- Update the stale comment in `WarehouseService.deactivate` (line 88) to reflect
  the new guard
- `WarehouseServiceTest` — add a test case for the active-zones-present scenario,
  asserting `ReferenceIntegrityViolationException` with code
  `REFERENCE_INTEGRITY_VIOLATION`
- `WarehouseControllerTest` (if present; else add one case) — POST
  `/{warehouseId}/deactivate` with active-zones scenario returns
  `"code": "REFERENCE_INTEGRITY_VIOLATION"` and HTTP 409

## Out of Scope

- `GlobalExceptionHandler` — already maps `ReferenceIntegrityViolationException`
  to 409 CONFLICT (landed in TASK-BE-010); no handler changes needed
- Cross-service checks (e.g. Warehouse cannot deactivate if Locations or
  inventory exist elsewhere) — v2 scope per architecture.md Open Items
- SKU / Partner / Lot deactivate paths — they have no local child aggregates
  that require this guard

---

# Acceptance Criteria

- [ ] `WarehousePersistencePort` exposes `hasActiveZonesFor(UUID warehouseId): boolean`
- [ ] The persistence adapter implements the query correctly (returns true when
      at least one ACTIVE Zone exists for that warehouse)
- [ ] `WarehouseService.deactivate` throws `ReferenceIntegrityViolationException`
      when `hasActiveZonesFor == true`, BEFORE calling `loaded.deactivate()`
- [ ] The stale comment on `WarehouseService.java` line 88 is corrected
- [ ] `WarehouseServiceTest` asserts the new exception type AND the code string
      (`REFERENCE_INTEGRITY_VIOLATION`) for the active-zones case
- [ ] Controller-level assertion verifies response body carries
      `"code": "REFERENCE_INTEGRITY_VIOLATION"` + HTTP 409 for the
      Warehouse deactivate active-zones path
- [ ] `./gradlew check` passes

---

# Related Specs

- `platform/error-handling.md`
- `specs/contracts/http/master-service-api.md` §1.5 (Warehouse deactivate)
- `rules/domains/wms.md` (referential-integrity invariants)

# Related Contracts

- `specs/contracts/http/master-service-api.md` §Error Envelope table
  (`REFERENCE_INTEGRITY_VIOLATION` → 409)

---

# Target Service

- `master-service`

---

# Implementation Notes

- Follow the exact pattern established by `ZonePersistencePort.hasActiveLocationsFor`
  and `ZoneService.deactivate`'s guard — this is a direct symmetrical complement.
- The exception is already mapped in `GlobalExceptionHandler`; do NOT add a new
  handler entry.
- `AGGREGATE_TYPE` constant in `WarehouseService` is `"Warehouse"` — use it in the
  `ReferenceIntegrityViolationException` constructor call, with an appropriate reason
  string (e.g. `"warehouse has active zones"`).

---

# Edge Cases

- Deactivate with 0 active Zones → succeeds (not a reference-integrity violation).
- Deactivate with version mismatch → `ConcurrencyConflictException` (409 CONFLICT),
  unchanged.
- Deactivate already-INACTIVE Warehouse → `InvalidStateTransitionException` (422
  STATE_TRANSITION_INVALID), unchanged.

---

# Failure Scenarios

- Concurrent race: one request sees 0 active Zones, another creates a Zone, first
  commits the deactivate. Expected under v1 optimistic-lock model; the `Warehouse`
  row is not touched by a Zone insert so the lock does not help here.
  Out of scope for this task; document in review.

---

# Test Requirements

- Unit test in `WarehouseServiceTest` for the active-zones guard
- Controller or integration test asserting the response body code string
- No new Testcontainers integration test required beyond existing harness

---

# Definition of Done

- [ ] Port + adapter query + service guard + comment fix landed
- [ ] Tests added and green
- [ ] Ready for review
