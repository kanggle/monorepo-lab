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

- `TASK-FAN-BE-003-artist-service-bootstrap.md` — artist-service Spring Boot 부트스트랩. Hexagonal (ports/adapters). artist 프로필 + group + fandom 메타데이터. admin only 등록/수정. read-heavy 디렉토리 검색 + Redis 캐시. outbox + Kafka 이벤트. reference: wms master-service.
- `TASK-FAN-BE-004-prometheus-rate-limit.md` — gateway-service 의 `/actuator/prometheus` rate-limit (또는 네트워크 격리). PR #116 review Warning 4 follow-up — community-api.md 의 "gateway-rate-limited" 주장이 현재 false 라 정정 + 옵션 3가지 (gateway route + RateLimiter / in-process Bucket4j / network isolation) 비교 결정 필요.

## in-progress

(empty)

## review

- `TASK-FAN-BE-001-gateway-service-bootstrap.md` — gateway-service Spring Boot 부트스트랩 완료. OIDC OAuth2 Resource Server (GAP JWKS 검증, RS256), `tenant_id=fan-platform` claim 필터, Redis rate limit, Traefik label 통합. 47/47 unit+slice 통과, integration 은 CI Linux 에서 실행. 2026-05-03.
- `TASK-FAN-BE-002-community-service-bootstrap.md` — community-service Spring Boot 부트스트랩 완료. Layered + post 상태 기계 (DRAFT/PUBLISHED/HIDDEN/DELETED), 5 controller (post/feed/comment/reaction/follow), Flyway V1, libs:java-messaging 기반 outbox + Kafka, Redis feed cache, multi-tenant + service-level fail-closed, visibility tier (PUBLIC/MEMBERS_ONLY/PREMIUM v1 always-pass). 5 spec + 2 contract + docker-compose + Kafka 추가. 69/69 unit+slice 통과, 24 integration 클래스 작성 (Windows skip, CI Linux 실행). 2026-05-03.

## done

(empty — fan-platform 부트스트랩 직후 최초 발행 상태)
