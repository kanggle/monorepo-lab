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

- `TASK-FAN-BE-004-prometheus-rate-limit.md` — gateway-service 의 `/actuator/prometheus` rate-limit (또는 네트워크 격리). PR #116 review Warning 4 follow-up — community-api.md 의 "gateway-rate-limited" 주장이 현재 false 라 정정 + 옵션 3가지 (gateway route + RateLimiter / in-process Bucket4j / network isolation) 비교 결정 필요.
- `TASK-FAN-INT-001-v1-services-e2e.md` — fan-platform v1 3 backend service (gateway/community/artist) 의 cross-service E2E 테스트 스위트. Testcontainers + WireMock JWKS, 3 시나리오 (artist+post happy path / multi-tenant isolation / visibility tier). 새 CI job `fan-platform-e2e`. wms tests/e2e + ecommerce frontend-e2e 패턴 reference.
- `TASK-FAN-FE-001-frontend-bootstrap.md` — Next.js 15 App Router + Tailwind + next-auth v5 + GAP OIDC PKCE. 5~7 페이지 (피드/아티스트 디렉토리/프로필/포스트 상세/로그인 + 옵션 멤버십/마이페이지). React Query + Vitest + Playwright smoke. ecommerce web-store 패턴 reference.

## in-progress

(empty)

## review

(empty)

## done

- `TASK-FAN-BE-001-gateway-service-bootstrap.md` — gateway-service Spring Boot 부트스트랩 (OIDC + Traefik). PR #115 머지 2026-05-03 (review fix 포함: JWKS startup probe + FailOpenRateLimiter narrowing).
- `TASK-FAN-BE-002-community-service-bootstrap.md` — community-service Spring Boot 부트스트랩 (Layered + post 상태 기계 + outbox). PR #116 머지 2026-05-03 (review fix 8건 포함: Critical status_changed 이벤트, FeedCache read-through, etc.). follow-up: TASK-FAN-BE-004 (prometheus rate-limit), TASK-MONO-025 (UUID v7 마이그레이션).
- `TASK-FAN-BE-003-artist-service-bootstrap.md` — artist-service Spring Boot 부트스트랩 (Hexagonal ports/adapters + outbox). PR #125 머지 2026-05-03 (review fix 6건 포함: 인덱스 tenant_id prefix, FANDOM POST/PATCH 분리, error envelope 일관, ARTIST_ARCHIVED 정책 문서화, AddRole 전용 enum, outbox 테스트 race fix).
