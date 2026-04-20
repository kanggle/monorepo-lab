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

(empty — master-service v1 complete)

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
- `TASK-INT-004-e2e-scenario2-guard-and-kafka-port.md` — scenario-2 guard + Kafka port + outbox smoke. Review verdict 2026-04-20: FIX NEEDED → follow-up in TASK-INT-006
- `TASK-BE-010-reference-integrity-violation-exception.md` — ReferenceIntegrityViolationException + Zone deactivate guard. Review verdict 2026-04-20: FIX NEEDED → follow-up in TASK-BE-014
- `TASK-BE-008-error-envelope-compliance.md` — error envelope `timestamp` + `STATE_TRANSITION_INVALID` → 422. Review verdict 2026-04-20: **APPROVED** (2 non-blocking suggestions)
- `TASK-BE-009-persistence-adapter-cleanup.md` — removed `existsById` pre-checks, narrowed catch, Javadoc drift. Review verdict 2026-04-20: **APPROVED** (2 non-blocking suggestions)
- `TASK-BE-011-sku-test-coverage-followup.md` — `SkuControllerTest` + `SkuPersistenceAdapterTest` Testcontainers variant. Review verdict 2026-04-20: **APPROVED** (2 non-blocking suggestions)
- `TASK-INT-003-gateway-rate-limit-and-fail-open.md` — `(ip, routeId)` key + fail-open decorator + empty role header. Review verdict 2026-04-20: **APPROVED** (2 non-blocking warnings noted — metric emission + blank-list filtering)
- `TASK-BE-014-warehouse-deactivate-active-zones-guard.md` — warehouse deactivate active-zones guard + hasActiveZonesFor port/adapter. Review verdict 2026-04-20: **APPROVED**
- `TASK-INT-006-drain-destructive-in-awaitility.md` — accumulate drain() across Awaitility retries; fixes masked field-mismatch failures. Review verdict 2026-04-20: **APPROVED** (1 pre-existing UUID.fromString nit noted)
- `TASK-BE-006-lot-aggregate.md` — master-service v1 final aggregate (Lot), scheduled expiration, SKU reverse-guard upgrade. Review verdict 2026-04-20: FIX NEEDED → follow-up in TASK-BE-015 (5 missing test classes + contract harness). 2 non-blocking warnings on LotService (WarehouseStatus comparison, expireBatch transaction scope)
- `TASK-BE-015-lot-test-coverage-followup.md` — Lot test classes (5/5 confirmed). Review verdict 2026-04-20: FIX NEEDED → follow-up in TASK-BE-016 (contract harness Lot wiring only). All 5 test classes high-quality; contract harness missing Lot cases.
- `TASK-BE-016-lot-contract-harness-wiring.md` — Lot cases wired into EventContractTest + HttpContractTest (3 new tests). Review verdict 2026-04-20: **APPROVED** (1 non-blocking note — master-lot-created.schema.json not asserted alongside generic envelope, can be added in future if envelope-depth parity desired)
