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

(empty)

## in-progress

(empty)

## review

(empty)

## done

- `TASK-FAN-BE-007-e2e-path-and-oauth-client-name-drift.md` — **REVIEWED → approved** (2026-05-16, `/review-task` single-task, review-checklist Spec/Arch/Quality/Security PASS · Perf N/A · Testing PASS via task Verification; fix task 0). impl `task/spec-drift-cohort-2026-05-16` (spec-only, GAP V0011 read-only, no `apps/`). **WI-1 (F16)**: `v1-e2e-scenarios.md` step 1/2 `/api/v1/artist/artists`·`/api/v1/artist/artists/{id}/status` off-by-one → canonical `/api/v1/artists`·`/api/v1/artists/{id}/status` (artist-api.md L16-19 + gateway-service/architecture.md L146-149 route 표 = authority; 두 step 모두 positive valid-call 201/200 확인 — Edge Case negative-test 아님). **Edge-Case 편집 제외**: `gateway-service/architecture.md:152` 의 `/api/v1/artist/**` 는 회피한 catch-all glob 을 설명하는 prose (route 표는 이미 canonical) — task Scope("e2e doc 가 유일 edit target") + Edge Case + Failure Scenario("정당한 다른 endpoint blind replace 금지") 에 따라 미편집·문서화. AC grep 의도 "(no extra-segment off-by-one form remains)" 충족. **WI-2 (F21)**: `fan-platform-web/architecture.md:150` `fan-platform-realm-internal-services-client` → `fan-platform-internal-services-client` (client_id 확인, Keycloak realm 아님; GAP V0011 seed L84 = canonical; gap-integration.md:48/103 이미 정합). `grep realm-internal-services-client`=0, GAP/apps diff 0, dead-ref 0. 분석=Opus 4.7 / 구현=Opus 4.7 / 리뷰=Opus 4.7.

- `TASK-FAN-BE-006-gateway-and-web-overview-skeleton.md` — spec PR #466 (squash `0fff6dc1`) + impl PR #467 (squash `bf83b4a7`) 머지 (2026-05-14). **`/refactor-spec all --dry-run` (2026-05-13~14) fan-platform audit critical #1+2 finding closure** — 4 service 중 sibling 2 (artist-service + community-service) 는 `overview.md` 보유, gateway-service + fan-platform-web 미존재 → sibling 7-section skeleton 답습으로 2 file 신규 authoring. **gateway-service/overview.md** (~60 line) = edge gateway role (rest-api, Layered, Spring Cloud Gateway reactive) + 4 RewritePath routes table (TASK-FAN-BE-005) + 6 key invariants (JWT validation 강제, tenant fail-closed, stateless, fail-open rate limit 등). **fan-platform-web/overview.md** (~80 line) = frontend-app (FSD lite, Next.js 15 App Router + next-auth v5 PKCE + TanStack Query) + 7-page table (5 pages + login + next-auth handler) + 6 key invariants (HttpOnly cookie only, server-only accessToken via `getFanSession()`, single fetch boundary, tenant locked, MEMBERS_ONLY v1 stub, cross-feature isolation). content source = 각 service architecture.md 의 Identity + Role + Routes + Style Rationale 발췌. 4 file / +132 / -10 (2 신규 overview + task spec lifecycle + INDEX). production code = 0. **Impl PR CI = 15 SKIP + 1 changes PASS** (markdown-only path-filter, TASK-MONO-075 자연 검증). lifecycle = ready → review 직접 (in-progress 우회, mechanical skeleton fill single-PR closure 패턴 3번째 적용 — TASK-BE-281 / TASK-MONO-084 precedent). Sibling 답습 패턴 = TASK-MONO-083 / TASK-BE-280/281 / TASK-SCM-BE-011 / TASK-MONO-084 (모두 same-day single-PR closure, 본 task 가 6번째 entry). 분석=Opus 4.7 / 구현=Opus 4.7.

- `TASK-FAN-BE-001-gateway-service-bootstrap.md` — gateway-service Spring Boot 부트스트랩 (OIDC + Traefik). PR #115 머지 2026-05-03 (review fix 포함: JWKS startup probe + FailOpenRateLimiter narrowing).
- `TASK-FAN-BE-002-community-service-bootstrap.md` — community-service Spring Boot 부트스트랩 (Layered + post 상태 기계 + outbox). PR #116 머지 2026-05-03 (review fix 8건 포함: Critical status_changed 이벤트, FeedCache read-through, etc.). follow-up: TASK-FAN-BE-004 (prometheus rate-limit), TASK-MONO-025 (UUID v7 마이그레이션).
- `TASK-FAN-BE-003-artist-service-bootstrap.md` — artist-service Spring Boot 부트스트랩 (Hexagonal ports/adapters + outbox). PR #125 머지 2026-05-03 (review fix 6건 포함: 인덱스 tenant_id prefix, FANDOM POST/PATCH 분리, error envelope 일관, ARTIST_ARCHIVED 정책 문서화, AddRole 전용 enum, outbox 테스트 race fix).
- `TASK-FAN-BE-004-prometheus-rate-limit.md` — gateway 의 `/actuator/prometheus` 네트워크 격리 (option c). PR #128 머지 2026-05-03. spec 정정 + 신규 ops guide + 통합 테스트 2건. follow-up: Prometheus 컨테이너 docker-compose 추가 시 `fan-platform-net` join 필요.
- `TASK-FAN-BE-005-gateway-rewrite-path-fix.md` — gateway `application.yml` 에 4 routes (community + artists + artist-groups + fandoms) `RewritePath` 추가. 외부 `/api/v1/...` → 내부 `/api/...` 매핑. 5 통합 테스트. PR #136 머지 2026-05-03. follow-up: PR #131 머지 후 e2e SPRING_APPLICATION_JSON 우회 제거 cleanup.
- `TASK-FAN-INT-001-v1-services-e2e.md` — fan-platform v1 3 backend service (gateway/community/artist) cross-service E2E 스위트. JUnit 5 + Testcontainers (postgres/redis/kafka/wiremock JWKS) + 3 시나리오 (artist+post happy path / multi-tenant isolation / visibility tier). 새 CI job `fan-platform-e2e`. SPRING_APPLICATION_JSON 우회는 production 측 FAN-BE-005 fix 후 cleanup PR 로 정리. PR #131 머지 2026-05-03.
- `TASK-FAN-FE-001-frontend-bootstrap.md` — fan-platform-web Next.js 15 App Router + Tailwind + next-auth v5 + GAP OIDC PKCE. 4 페이지 + 27 vitest + 4 playwright smoke 통과. FSD lite 아키텍처. CI 3 job 확장 (frontend-checks/unit-tests/e2e-smoke). OIDC client 는 TASK-MONO-026 으로 등록됨. PR #132 머지 2026-05-03.
