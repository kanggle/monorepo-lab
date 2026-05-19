# Tasks Index — erp-platform

This document defines task lifecycle, naming, and move rules for the **erp-platform** project. Repo-root [tasks/INDEX.md](../../../tasks/INDEX.md) covers monorepo-level (cross-project) tasks; this file covers erp-platform-internal tasks only.

---

# Lifecycle

backlog → ready → in-progress → review → done → archive

Only tasks in `ready/` may be implemented.

---

# Task Types

- `TASK-ERP-BE-XXX`: backend (Spring Boot service implementations)
- `TASK-ERP-INT-XXX`: cross-service integration / E2E (Testcontainers · Docker compose)
- `TASK-ERP-FE-XXX`: frontend — declared for future use, erp v1 is backend-only (platform console renders erp per ADR-MONO-013 §3.3)

---

# Move Rules

## backlog → ready
Allowed only when:
- related specs exist (`specs/services/<service>/architecture.md`, `specs/contracts/...`)
- related contracts are identified
- acceptance criteria are clear
- task template is complete

## ready → in-progress
Allowed only when implementation starts.

## in-progress → review
Allowed only when:
- implementation is complete
- tests are added
- contract / spec updates are completed if required

## review → done
Allowed only after review approval.

### Review Rules
- Tasks in `review/` must not be re-implemented directly.
- If a review reveals a bug or missing requirement, create a new fix task in `ready/` referencing the original task.
- Fix tasks must include the original task ID in their Goal section (e.g. "Fix issue found in TASK-ERP-BE-001").
- Do not modify a task file after it moves to `review/` or `done/`.

### PR Separation Rule (lifecycle ↔ PR boundary)

Each lifecycle transition lands in its own PR. **Never bundle task spec authoring with implementation in the same PR.**

| Stage | Recommended PR shape |
|---|---|
| `(writing) → ready` | **spec PR** — adds the task file to `ready/` + updates this `INDEX.md` ready list. No implementation code. |
| `ready → in-progress → review` | **impl PR** — moves the task file through `in-progress/` to `review/` and lands the implementation. Lifecycle moves and impl commits should be separate commits but live in one PR. |
| `review → done` | **chore PR** — moves merged task file(s) from `review/` to `done/` + updates the `INDEX.md` done list. May batch multiple merged tasks. |

The repo-root [tasks/INDEX.md](../../../tasks/INDEX.md) is the authoritative definition. This summary applies the same rule at the project level.

## done → archive
Allowed when no further active change is expected.

---

# Rule

Tasks must not be implemented from `backlog/`, `in-progress/`, `review/`, `done/`, or `archive/`.

---

# Task List

## backlog

(empty)

## ready

- `TASK-ERP-BE-001-masterdata-service-bootstrap.md` — erp-platform 의 첫 service `masterdata-service` Spring Boot 부트스트랩 (Hexagonal; 조직 마스터데이터 — 부서/직원/직급/비용센터/거래처 / 참조 무결성 / 유효기간 / 불변 audit_log). 부트스트랩 PR (TASK-MONO-119) 에서 task 만 author, 구현은 본 task 에서.

## in-progress

(empty)

## review

(empty)

## done

(empty)

## archive

(empty)
