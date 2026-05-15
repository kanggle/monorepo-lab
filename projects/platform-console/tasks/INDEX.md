# Tasks Index — platform-console

This document defines task lifecycle, naming, and move rules for the **platform-console** project. Repo-root [tasks/INDEX.md](../../../tasks/INDEX.md) covers monorepo-level (cross-project) tasks; this file covers platform-console-internal tasks only.

---

# Lifecycle

backlog → ready → in-progress → review → done → archive

Only tasks in `ready/` may be implemented.

---

# Task Types

- `TASK-PC-FE-XXX`: frontend (`console-web` Next.js implementation)
- `TASK-PC-BE-XXX`: backend (`console-bff` — ADR-MONO-013 Phase 7, deferred)
- `TASK-PC-INT-XXX`: cross-service / cross-project integration / E2E (Testcontainers · Docker compose · federated domain APIs)

> Cross-project prerequisites that live in **another project** (e.g. GAP OIDC client / registry) are tracked as that project's task (e.g. `TASK-BE-296` in `global-account-platform`), referenced from the dependent `TASK-PC-*`.

---

# Move Rules

## backlog → ready
Allowed only when:
- related specs exist (`specs/services/<service>/architecture.md`, `specs/contracts/...`)
- related contracts are identified
- acceptance criteria are clear
- task template is complete
- cross-project prerequisite tasks (if any) are identified and linked

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
- Fix tasks must include the original task ID in their Goal section.
- Do not modify a task file after it moves to `review/` or `done/`.

### PR Separation Rule (lifecycle ↔ PR boundary)

| Stage | Recommended PR shape |
|---|---|
| `(writing) → ready` | **spec PR** — adds the task file to `ready/` + updates this `INDEX.md` ready list. No implementation code. |
| `ready → in-progress → review` | **impl PR** — moves the task through `in-progress/` to `review/` and lands the implementation. |
| `review → done` | **chore PR** — moves merged task file(s) from `review/` to `done/` + updates the `INDEX.md` done list. May batch. |

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

- `TASK-PC-FE-001-console-web-shell-gap-sso.md` — console-web 셸: GAP OIDC Auth Code+PKCE 로그인(HttpOnly cookie) + data-driven 서비스 카탈로그(GAP product/tenant 레지스트리 소비) + 테넌트 스위처. ADR-MONO-013 Phase 1→2 bridge. **선행**: GAP `TASK-BE-296` (OIDC public client + 레지스트리 surface).

## in-progress

(empty)

## review

(empty)

## done

(empty)

## archive

(empty)
