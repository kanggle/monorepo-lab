# Tasks Index — fan-platform

This document defines task lifecycle, naming, and move rules for the **fan-platform** project. Repo-root [tasks/INDEX.md](../../../tasks/INDEX.md) covers monorepo-level (cross-project) tasks; this file covers fan-platform-internal tasks only.

---

# Lifecycle

backlog → ready → in-progress → review → done → archive

Only tasks in `ready/` may be implemented.

---

# Task Types

- `TASK-FAN-BE-XXX`: backend
- `TASK-FAN-FE-XXX`: frontend (Next.js)
- `TASK-FAN-INT-XXX`: cross-service integration / E2E

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
- Fix tasks must include the original task ID in their Goal section (e.g. "Fix issue found in TASK-FAN-BE-001").
- Do not modify a task file after it moves to `review/` or `done/`.

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

- `TASK-FAN-BE-001-gateway-service-bootstrap.md` — gateway-service Spring Boot 부트스트랩. OIDC OAuth2 Resource Server (GAP JWKS 검증, RS256), `tenant_id=fan-platform` claim 필터, rate limit (Redis), Traefik label 통합. 첫 reference implementation — community/artist 가 이 패턴 복제.

## in-progress

(empty)

## review

(empty)

## done

(empty — fan-platform 부트스트랩 직후 최초 발행 상태)
