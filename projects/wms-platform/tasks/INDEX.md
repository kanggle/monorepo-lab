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

- `TASK-BE-003-location-aggregate.md` — add Location aggregate to master-service (dual-parent, globally-unique code, split HTTP routing) and turn on Zone's active-children guard

## in-progress

(empty)

## review

- `TASK-BE-001-master-service-bootstrap.md` — Warehouse CRUD vertical slice (impl + unit/slice tests + CI green; full `@SpringBootTest` integration suite flagged as follow-up)
- `TASK-INT-001-gateway-master-service-route.md` — gateway route + JWT + rate-limit + header enrichment (impl + filter unit tests + CI green; live-pair e2e flagged as follow-up)
- `TASK-BE-002-zone-aggregate.md` — Zone CRUD vertical slice (domain + persistence + application + HTTP + outbox + seed; mirrors Warehouse pattern; integration-test gaps carried into TASK-BE-007)

## done

(empty)
