# Tasks Index — scm-platform

This document defines task lifecycle, naming, and move rules for the **scm-platform** project. Repo-root [tasks/INDEX.md](../../../tasks/INDEX.md) covers monorepo-level (cross-project) tasks; this file covers scm-platform-internal tasks only.

---

# Lifecycle

backlog → ready → in-progress → review → done → archive

Only tasks in `ready/` may be implemented.

---

# Task Types

- `TASK-SCM-BE-XXX`: backend (Spring Boot service implementations)
- `TASK-SCM-INT-XXX`: cross-service integration / E2E (Testcontainers · Docker compose)
- `TASK-SCM-FE-XXX`: frontend — declared for future use, scm v1 is backend-only

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
- Fix tasks must include the original task ID in their Goal section (e.g. "Fix issue found in TASK-SCM-BE-001").
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

- `TASK-SCM-BE-001-gateway-service-bootstrap.md` — scm-platform 의 첫 service `gateway-service` Spring Boot 부트스트랩. Spring Cloud Gateway 3.4 + OAuth2 Resource Server (GAP RS256 JWT 검증) + TenantClaimValidator (`tenant_id=scm` only) + Redis rate limit + Traefik label 활성화 (`scm.local`). procurement / inventory-visibility 라우트는 placeholder (BE-002/003 활성화). spec 3개 (gateway architecture / public-routes / gap-integration). 단위·슬라이스·통합 테스트 + 루트 CI Build & Test 에 모듈 추가. 분석=Opus 4.7 / 구현 권장=Opus — security + reactive + filter 동시 작성.

## in-progress

(empty)

## review

(empty)

## done

(empty)

## archive

(empty)
