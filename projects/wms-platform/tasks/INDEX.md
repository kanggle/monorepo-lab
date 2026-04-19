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

(empty)

## in-progress

(empty)

## review

- `TASK-BE-001-master-service-bootstrap.md` — Warehouse CRUD vertical slice (impl + unit/slice tests + CI green; full `@SpringBootTest` integration suite flagged as follow-up)
- `TASK-INT-001-gateway-master-service-route.md` — gateway route + JWT + rate-limit + header enrichment (impl + filter unit tests + CI green; live-pair e2e flagged as follow-up)
- `TASK-BE-002-zone-aggregate.md` — Zone CRUD vertical slice (domain + persistence + application + HTTP + outbox + seed; mirrors Warehouse pattern; integration-test gaps carried into TASK-BE-007)
- `TASK-BE-003-location-aggregate.md` — Location CRUD vertical slice + Zone guard turned on (dual-parent + globally-unique code + split HTTP routing; `hasActiveLocationsFor` stub replaced with real JPA query)
- `TASK-DOC-001-library-boundary-cleanup.md` — Javadoc sweep in libs/ (auth-service / admin-service citations + TASK-BE-028c / TASK-BE-047 references removed; platform/* docs were already clean via commit 09e7e95)
- `TASK-BE-004-sku-aggregate.md` — SKU CRUD vertical slice (independent aggregate; UPPERCASE normalization + partial barcode unique + `by-code`/`by-barcode` lookup endpoints; Lot active-children guard stubbed for TASK-BE-006). `SkuControllerTest` + `SkuPersistenceAdapterTest` (Testcontainers) flagged as follow-up punch-list items.
- `TASK-BE-007-master-service-integration-tests.md` — full `@SpringBootTest` suite (Postgres + Kafka + Redis in one Testcontainers `Network`) + contract-test harness (networknt JSON-schema) + 3 named Micrometer counters. CI-gated verification per Windows blocker
- `TASK-INT-002-gateway-master-e2e.md` — live-pair e2e (gateway + master + infra containers + JWKS MockWebServer) covering 5 scenarios; new `e2e-tests` CI job wired. Trace propagation assertion reduced scope (deferred to BE-007 contract harness)

## done

(empty)
