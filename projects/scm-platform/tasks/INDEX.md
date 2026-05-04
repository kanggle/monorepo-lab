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

- `TASK-SCM-BE-002-procurement-service-bootstrap.md` — scm-platform 핵심 도메인 service `procurement-service` 부트스트랩. PO 상태기계 (DRAFT→SUBMITTED→ACKNOWLEDGED→CONFIRMED→…→CLOSED, S1) + Hexagonal (supplier adapter 분리) + outbox 패턴 + supplier 외부 통합 (Resilience4j circuit breaker + idempotency key, S2) + audit_log (S7) + supplier credentials AES-GCM 암호화 (S6). API 8 endpoint + webhook 2 (supplier-ack / asn). Event 7개 (`scm.procurement.po.{submitted,acknowledged,confirmed,canceled,received,closed}` + `asn.received`). Spec 7개 (architecture / state-machines/po-status / data-model / dependencies / observability / overview / integration/supplier-adapters) + contracts 2개 (procurement-api / procurement-events). 단위·슬라이스·통합 테스트 (multi-tenant isolation / outbox relay / supplier circuit breaker / supplier idempotency / state machine atomicity / audit log / asn overreceipt). gateway-service 의 procurement placeholder 라우트 활성화 검증. 선행=BE-001 (review). 분석=Opus 4.7 / 구현 권장=Opus — Hexagonal + 상태기계 + outbox + circuit breaker + idempotency + 암호화 동시 작성.
- `TASK-SCM-BE-003-inventory-visibility-service-bootstrap.md` — scm-platform 의 두 번째 도메인 service `inventory-visibility-service` 부트스트랩. cross-node 재고 가시성 read-model (자사 wms / supplier / 3PL / in-transit) — Service Type=`rest-api`+`event-consumer`, Hexagonal. wms-platform 의 `wms.inventory.{received,adjusted,transferred}.v1` 토픽 구독 (cross-project event consumption 첫 사례) + eventId 기반 멱등 처리 (T8). 노드별 staleness threshold 초과 감지 batch (5분 주기, ShedLock — `batch-heavy` trait 첫 코드 적용). API 5 endpoint (read-only) + alert 이벤트 1개 (`scm.inventory.alert.v1`). 4 테이블 + tenant_id + S5 (eventual consistency 명시적 노출 `meta.staleness`). Spec 6개 (architecture / data-model / dependencies / observability / overview / staleness-monitoring) + contracts 2개 (inventory-visibility-api / inventory-visibility-subscriptions). 단위·슬라이스·통합 테스트 (consumer 멱등 / cross-node aggregation / transferred atomic / staleness batch / Kafka retry+DLT). 선행=BE-001 (review). 병렬 가능=BE-002 (데이터 공유 0). 분석=Opus 4.7 / 구현 권장=Sonnet 4.6 — read-only + 단순 consumer + batch.

## in-progress

(empty)

## review

- `TASK-SCM-BE-001-gateway-service-bootstrap.md` — scm-platform 의 첫 service `gateway-service` Spring Boot 부트스트랩. Spring Cloud Gateway 3.4 + OAuth2 Resource Server (GAP RS256 JWT 검증) + TenantClaimValidator (`tenant_id=scm` only) + Redis rate limit + Traefik label 활성화 (`scm.local`). procurement / inventory-visibility 라우트는 placeholder (BE-002/003 활성화). spec 3개 (gateway architecture / public-routes / gap-integration). 단위·슬라이스·통합 테스트 + 루트 CI Build & Test 에 모듈 추가. 분석=Opus 4.7 / 구현 권장=Opus — security + reactive + filter 동시 작성.

## done

(empty)

## archive

(empty)
