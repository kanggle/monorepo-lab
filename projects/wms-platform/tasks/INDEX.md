# Tasks Index

This document defines task lifecycle, naming, and move rules.

---

# Lifecycle

backlog → ready → in-progress → review → done → archive

Only tasks in `ready/` may be implemented.

---

# Task Types

- `TASK-BE-XXX`: backend
- `TASK-INT-XXX`: integration

(`TASK-FE-XXX` is reserved but not used in this backend-only project.)

---

# Move Rules

## backlog → ready
Allowed only when:
- related specs exist
- related contracts are identified
- acceptance criteria are clear
- task template is complete

## ready → in-progress
Allowed only when implementation starts.

## in-progress → review
Allowed only when:
- implementation is complete
- tests are added
- contract/spec updates are completed if required

## review → done
Allowed only after review approval.

### Review Rules
- Tasks in `review/` must not be re-implemented directly.
- If a review reveals a bug or missing requirement, create a new fix task in `ready/` referencing the original task.
- Fix tasks must include the original task ID in their Goal section (e.g. "Fix issue found in TASK-BE-002").
- Do not modify a task file after it moves to `review/` or `done/`.

## done → archive
Allowed when no further active change is expected.

---

# Rule

Tasks must not be implemented from `backlog/`, `in-progress/`, `review/`, `done/`, or `archive/`.

---

# Task List

## backlog

(empty — subsequent tasks TASK-BE-002..006 will cover Zone/Location/SKU/Partner/Lot; follow-ups flagged in review notes: `TASK-BE-007` full integration tests, `TASK-INT-002` e2e gateway-master, `TASK-INT-003` circuit breaker, `TASK-DOC-001` platform doc resync)

## ready

- `TASK-BE-008-error-envelope-compliance.md` — add `timestamp` to error envelope; correct `STATE_TRANSITION_INVALID` to 422 per `platform/error-handling.md` (addresses BE-001 blockers + cross-aggregate impact on BE-002/003/004/INT-001)
- `TASK-BE-009-persistence-adapter-cleanup.md` — remove redundant `existsById` pre-checks in update() across Warehouse/Zone/Location adapters; narrow ZonePersistenceAdapter.insert() exception translation; fix hasActiveLocationsFor Javadoc drift; switch SkuPersistenceAdapter to `getConstraintName()` (addresses BE-002/BE-003 warnings + BE-004 constraint-detection robustness)
- `TASK-BE-010-reference-integrity-violation-exception.md` — introduce `ReferenceIntegrityViolationException` with code `REFERENCE_INTEGRITY_VIOLATION`; replace Zone deactivate's `InvalidStateTransitionException("zone has active locations")` (addresses BE-003 contract divergence)
- `TASK-BE-011-sku-test-coverage-followup.md` — author `SkuControllerTest` + `SkuPersistenceAdapterTest` (Testcontainers); fix the broken `@link` in `SkuPersistenceAdapterH2Test` (addresses BE-004 coverage gaps)
- `TASK-INT-003-gateway-rate-limit-and-fail-open.md` — rate-limit key `(clientIp, routeId)`; fail-open RedisRateLimiter decorator; empty `X-User-Role` on missing-claim JWT (addresses INT-001 criticals)
- `TASK-INT-004-e2e-scenario2-guard-and-kafka-port.md` — expose master-service `metrics` actuator (integration profile); fix Kafka bootstrap port 9092 in E2EBase; Awaitility counter stability check (addresses INT-002 scenario-2 silent false-negative + Kafka port bug)

## in-progress

(empty)

## review

(empty)

## done

- `TASK-BE-001-master-service-bootstrap.md` — Warehouse CRUD vertical slice. Review verdict 2026-04-20: FIX NEEDED → follow-up in TASK-BE-008
- `TASK-INT-001-gateway-master-service-route.md` — gateway route + JWT + rate-limit + header enrichment. Review verdict 2026-04-20: FIX NEEDED → follow-up in TASK-INT-003
- `TASK-BE-002-zone-aggregate.md` — Zone CRUD vertical slice. Review verdict 2026-04-20: FIX NEEDED → follow-up in TASK-BE-009
- `TASK-BE-003-location-aggregate.md` — Location CRUD + Zone guard. Review verdict 2026-04-20: FIX NEEDED → follow-up in TASK-BE-010
- `TASK-DOC-001-library-boundary-cleanup.md` — Javadoc sweep in libs/. Review verdict 2026-04-20: **APPROVED**
- `TASK-BE-004-sku-aggregate.md` — SKU CRUD. Review verdict 2026-04-20: FIX NEEDED → follow-up in TASK-BE-011
- `TASK-BE-007-master-service-integration-tests.md` — integration suite + contract harness. Review verdict 2026-04-20: **APPROVED** (2 non-blocking warnings noted)
- `TASK-INT-002-gateway-master-e2e.md` — live-pair e2e. Review verdict 2026-04-20: FIX NEEDED → follow-up in TASK-INT-004
