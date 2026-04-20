# Task ID

TASK-BE-010

# Title

Introduce `ReferenceIntegrityViolationException` — align Zone deactivate with contract code

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

Fix the contract divergence flagged in the TASK-BE-003 review:

`specs/contracts/http/master-service-api.md` §2.5 requires Zone deactivate,
when blocked by active children, to return `409 REFERENCE_INTEGRITY_VIOLATION`.
The current implementation throws `InvalidStateTransitionException("zone has
active locations")` which serializes as `STATE_TRANSITION_INVALID`. HTTP
status is the same (409) but the error code in the response body is wrong.

Also audit the Warehouse deactivate path — `WarehouseService.java` comment
references `REFERENCE_INTEGRITY_VIOLATION` but the actual implementation
currently allows deactivate without child checks (Zone-only guard exists;
Warehouse has no children-blocking check landed yet — this task adds it
for symmetry when TASK-BE-005 / TASK-BE-006 make it relevant).

---

# Scope

## In Scope

- New domain exception: `ReferenceIntegrityViolationException extends
  MasterDomainException` with error code `REFERENCE_INTEGRITY_VIOLATION`
- `ZoneService.deactivate(...)` replaces the
  `InvalidStateTransitionException("zone has active locations")` throw with
  the new exception
- `GlobalExceptionHandler.handleReferenceIntegrity(...)` mapping to 409
  `REFERENCE_INTEGRITY_VIOLATION`
- `ZoneServiceTest` — update the assertion at the active-locations-present
  case to expect `ReferenceIntegrityViolationException` with code
  `REFERENCE_INTEGRITY_VIOLATION`
- Extend `ZoneControllerTest` (if present; else add one case) — POST
  `/{zoneId}/deactivate` with an active-locations scenario returns body
  `{"error": {"code": "REFERENCE_INTEGRITY_VIOLATION", ...}}`
- Audit Warehouse / Location deactivate paths for the same gap — if they
  have a documented reference-integrity block in spec but throw
  `InvalidStateTransitionException` or equivalent, fix those too

## Out of Scope

- TASK-BE-006 (Lot) has its own active-children guard on SKU. That lands
  separately; this task only deals with the existing Zone → Location guard.
- Renaming `STATE_TRANSITION_INVALID` cases that ARE state transitions
  (e.g., deactivate already-INACTIVE) — those stay as state transitions.
- HTTP status change: both codes stay at 409 per the contract.

---

# Acceptance Criteria

- [ ] `ReferenceIntegrityViolationException` exists in `domain/exception/`
      and extends `MasterDomainException` with code `REFERENCE_INTEGRITY_VIOLATION`
- [ ] `ZoneService.deactivate` throws `ReferenceIntegrityViolationException`
      when `hasActiveLocationsFor == true`
- [ ] `GlobalExceptionHandler` maps the new exception to 409 with the
      correct code
- [ ] `ZoneServiceTest` asserts the new exception type AND the code
      (prevents regression on the body shape)
- [ ] Controller-level assertion in `ZoneControllerTest` or `LocationPersistenceAdapterTest`
      (whichever is the right layer) verifies the response body carries
      `"code": "REFERENCE_INTEGRITY_VIOLATION"`
- [ ] `./gradlew check` passes
- [ ] Warehouse / Location deactivate paths audited; findings documented in
      review note (fix inline if cheap; spin off a follow-up if non-trivial)

---

# Related Specs

- `platform/error-handling.md`
- `specs/contracts/http/master-service-api.md` §2.5, §3.5 (Zone / Location deactivate)
- `rules/domains/wms.md` (referential-integrity invariants)

# Related Contracts

- `specs/contracts/http/master-service-api.md` §Error Envelope table

---

# Target Service

- `master-service`

---

# Implementation Notes

- Model the new exception after `WarehouseCodeDuplicateException` (has a
  dedicated code + constructor taking aggregate id / context).
- Keep the exception-to-HTTP-status mapping in `GlobalExceptionHandler`;
  do NOT add it to the domain.
- Coordinate with TASK-BE-008 (platform error-envelope compliance) — if
  both land around the same time, the `timestamp` field addition and this
  exception addition touch the same handler; merge in one PR if landing
  together to avoid trivial conflicts.

---

# Edge Cases

- Deactivate with 0 active locations → succeeds (not reference-integrity violation).
- Deactivate with version mismatch → `ConcurrencyConflictException` (409 CONFLICT), unchanged.
- Deactivate on already-INACTIVE zone → `InvalidStateTransitionException` (409 STATE_TRANSITION_INVALID), unchanged.

---

# Failure Scenarios

- Concurrent deactivation race: one request sees 0 active locations, another creates a location, first commits the deactivate. Expected under v1; optimistic lock doesn't prevent this because the `Zone` row wasn't touched by the location insert. Out of scope for this task; documented in review.

---

# Test Requirements

- Unit test in `ZoneServiceTest` for the refactored guard
- Controller or integration test asserting the response body code string
- No new integration test beyond what the harness already catches

---

# Definition of Done

- [ ] Exception + handler + service refactor landed
- [ ] Tests updated and green
- [ ] Warehouse / Location deactivate audit documented
- [ ] Ready for review
