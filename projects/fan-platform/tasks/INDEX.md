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

- `TASK-FAN-INT-001-v1-services-e2e.md` — PR #131 open (사용자 머지 대기 — gh CLI token `workflow` scope 부족). v1 e2e 테스트 스위트 + 3 시나리오 + `fan-platform-e2e` CI job. INT-001 구현 시 발견된 production bug 는 TASK-FAN-BE-005 (PR #136 머지) 로 production 측 fix 완료 — e2e suite 의 SPRING_APPLICATION_JSON 우회 제거가 #131 머지 후 cleanup 작업으로 남음.
- `TASK-FAN-FE-001-frontend-bootstrap.md` — PR #132 open (사용자 머지 대기 — 같은 token scope 이슈). Next.js 15 + next-auth v5 + Tailwind + FSD lite, 4 페이지 + 27 vitest + 4 playwright smoke 통과. OIDC client 는 TASK-MONO-026 (PR #135 머지) 로 이미 등록됨 — #132 머지 시 즉시 production OIDC 동작.

## in-progress

(empty)

## review

(empty)

## done

- `TASK-FAN-BE-001-gateway-service-bootstrap.md` — gateway-service Spring Boot 부트스트랩 (OIDC + Traefik). PR #115 머지 2026-05-03 (review fix 포함: JWKS startup probe + FailOpenRateLimiter narrowing).
- `TASK-FAN-BE-002-community-service-bootstrap.md` — community-service Spring Boot 부트스트랩 (Layered + post 상태 기계 + outbox). PR #116 머지 2026-05-03 (review fix 8건 포함: Critical status_changed 이벤트, FeedCache read-through, etc.). follow-up: TASK-FAN-BE-004 (prometheus rate-limit), TASK-MONO-025 (UUID v7 마이그레이션).
- `TASK-FAN-BE-003-artist-service-bootstrap.md` — artist-service Spring Boot 부트스트랩 (Hexagonal ports/adapters + outbox). PR #125 머지 2026-05-03 (review fix 6건 포함: 인덱스 tenant_id prefix, FANDOM POST/PATCH 분리, error envelope 일관, ARTIST_ARCHIVED 정책 문서화, AddRole 전용 enum, outbox 테스트 race fix).
- `TASK-FAN-BE-004-prometheus-rate-limit.md` — gateway 의 `/actuator/prometheus` 네트워크 격리 (option c). PR #128 머지 2026-05-03. spec 정정 + 신규 ops guide + 통합 테스트 2건. follow-up: Prometheus 컨테이너 docker-compose 추가 시 `fan-platform-net` join 필요.
- `TASK-FAN-BE-005-gateway-rewrite-path-fix.md` — gateway `application.yml` 에 4 routes (community + artists + artist-groups + fandoms) `RewritePath` 추가. 외부 `/api/v1/...` → 내부 `/api/...` 매핑. 5 통합 테스트. PR #136 머지 2026-05-03. follow-up: PR #131 머지 후 e2e SPRING_APPLICATION_JSON 우회 제거 cleanup.
