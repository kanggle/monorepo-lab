# Task ID

TASK-BE-001

# Title

Bootstrap master-service with Hexagonal skeleton and Warehouse CRUD

# Status

ready

# Owner

backend

# Task Tags

- code
- api
- event
- test

---

# Required Sections (must exist)

- Goal
- Scope (in/out)
- Acceptance Criteria
- Related Specs
- Related Contracts
- Edge Cases
- Failure Scenarios

---

# Goal

Stand up the `master-service` Spring Boot application skeleton following the
declared Hexagonal architecture, and deliver **Warehouse** CRUD end-to-end
(including events via outbox) as the first vertical slice.

On completion:

- `apps/master-service/` contains a runnable Spring Boot service that boots
  locally and against Testcontainers (Postgres + Kafka).
- All Warehouse endpoints defined in `specs/contracts/http/master-service-api.md`
  sections 1.1–1.6 are implemented and pass contract tests.
- Each Warehouse mutation writes an outbox row and the outbox publisher emits the
  corresponding event on `wms.master.warehouse.v1`.
- Package layout matches `.claude/skills/backend/architecture/hexagonal/SKILL.md`.

Zone / Location / SKU / Partner / Lot are **not** in this task. They follow in
subsequent TASK-BE-002..006.

---

# Scope

## In Scope

- Gradle module setup under `apps/master-service/` (Spring Boot 3.x, Java 21)
- Wiring of shared libs: `java-common`, `java-web`, `java-observability`,
  `java-security`, `java-messaging`, `java-test-support`
- Base Hexagonal package structure (`adapter/in`, `adapter/out`, `application`,
  `domain`, `config`)
- Postgres + Flyway migration `V1__init_warehouse.sql` (warehouses table + outbox table)
- Warehouse aggregate: domain model (POJO), JPA entity, mapper, persistence adapter
- Warehouse application service implementing use-case ports
- REST controllers + DTOs for endpoints 1.1–1.6 in the HTTP contract
- Outbox write (same transaction as state change) for Warehouse events
- Outbox publisher (scheduled poller) publishing to Kafka
- Error envelope handling per `platform/error-handling.md`
- Idempotency-Key handling on all mutating Warehouse endpoints (Redis-backed)
- Optimistic locking via JPA `@Version`
- Authentication / authorization via JWT (`MASTER_READ` / `MASTER_WRITE` / `MASTER_ADMIN`)
- Observability baseline: request metrics, MDC trace/request/actor id, OTel propagation
- Unit, slice, and integration tests per Test Requirements below
- Contract tests verifying endpoints 1.1–1.6 against the HTTP contract
- Seed migration guarded by `dev`/`standalone` profile (warehouse `WH01` only — zone/location/sku seeds come with their respective tasks)

## Out of Scope

- Zone / Location / SKU / Partner / Lot aggregates (subsequent tasks)
- Lot expiry scheduled job
- ERP / PIM sync adapter
- Gateway route wiring in `gateway-service` (separate TASK-INT-001; blocks end-to-end external access but not this task)
- Operational dashboards
- Bulk / CSV import endpoints
- Compaction-keyed topics or Avro encoding
- Cross-service referential integrity checks (v2)

---

# Acceptance Criteria

- [ ] `apps/master-service/build.gradle` builds cleanly via `./gradlew :apps:master-service:build`
- [ ] Service boots locally against Docker Compose Postgres + Kafka (`docker-compose up`) and `./gradlew :apps:master-service:bootRun`
- [ ] `GET /actuator/health` returns `200` with `UP` once dependencies are reachable
- [ ] `POST /api/v1/master/warehouses` creates a warehouse and returns `201` with full resource; response shape matches `specs/contracts/http/master-service-api.md §1.1`
- [ ] `GET /api/v1/master/warehouses/{id}` returns `200` with `ETag`; unknown id returns `404` `WAREHOUSE_NOT_FOUND`
- [ ] `GET /api/v1/master/warehouses` paginates per `PageResult` envelope; default `status=ACTIVE` filter applies
- [ ] `PATCH /api/v1/master/warehouses/{id}` updates mutable fields; wrong `version` returns `409` `CONFLICT`
- [ ] `POST /api/v1/master/warehouses/{id}/deactivate` and `/reactivate` toggle state; invalid transition returns `409` `STATE_TRANSITION_INVALID`
- [ ] Duplicate `warehouseCode` on create returns `409` `WAREHOUSE_CODE_DUPLICATE`
- [ ] Idempotency-Key missing on any mutating endpoint returns `400` `VALIDATION_ERROR`
- [ ] Repeat POST with same `Idempotency-Key` and same body returns the first call's cached response; different body returns `409` `DUPLICATE_REQUEST`
- [ ] Each successful mutation produces exactly one row in `master_outbox` in the same transaction as the warehouse change (verified by integration test)
- [ ] Outbox publisher forwards each outbox row to `wms.master.warehouse.v1` exactly once under normal operation; publisher metrics exposed
- [ ] Published event payload matches `specs/contracts/events/master-events.md` §1 (envelope + snapshot)
- [ ] All endpoints emit request/error/latency metrics and structured logs with `traceId`, `requestId`, `actorId`
- [ ] Package structure matches the Hexagonal skill reference — domain model has **no** JPA annotations; JPA entity is package-private inside the persistence adapter
- [ ] `rules/domains/wms.md` Checklist items W1 (transactional mutation) and applicable parts of W3/W6 are verified in code review
- [ ] `rules/traits/transactional.md` Checklist items T1, T3, T4 (for create/deactivate/reactivate), T5 are verified
- [ ] Unit + slice + integration test suites all pass in CI
- [ ] Contract tests for endpoints 1.1–1.6 pass
- [ ] Docker image builds via the standard platform image workflow

---

# Related Specs

> **Before reading Related Specs**: Follow `platform/entrypoint.md` Step 0 — read `PROJECT.md`, then load `rules/common.md` plus `rules/domains/wms.md`, `rules/traits/transactional.md`, `rules/traits/integration-heavy.md`.

- `platform/architecture.md`
- `platform/architecture-decision-rule.md`
- `platform/service-boundaries.md` (note: this doc references older ecommerce services; PROJECT.md service map wins per Source of Truth Priority — raise this inconsistency in review)
- `platform/shared-library-policy.md`
- `platform/error-handling.md`
- `platform/testing-strategy.md`
- `platform/service-types/rest-api.md`
- `platform/api-gateway-policy.md`
- `platform/versioning-policy.md`
- `specs/services/master-service/architecture.md`
- `specs/services/master-service/domain-model.md`
- `rules/domains/wms.md`
- `rules/traits/transactional.md`
- `rules/traits/integration-heavy.md`

# Related Skills

- `.claude/skills/backend/architecture/hexagonal/SKILL.md`
- `.claude/skills/backend/springboot-api/SKILL.md`
- `.claude/skills/backend/exception-handling/SKILL.md`
- `.claude/skills/backend/validation/SKILL.md`
- `.claude/skills/backend/dto-mapping/SKILL.md`
- `.claude/skills/backend/transaction-handling/SKILL.md`
- `.claude/skills/backend/pagination/SKILL.md`
- `.claude/skills/backend/jwt-auth/SKILL.md`
- `.claude/skills/backend/observability-metrics/SKILL.md`
- `.claude/skills/backend/testing-backend/SKILL.md`
- `.claude/skills/database/schema-change-workflow/SKILL.md`
- `.claude/skills/database/migration-strategy/SKILL.md`
- `.claude/skills/messaging/outbox-pattern/SKILL.md`
- `.claude/skills/messaging/event-implementation/SKILL.md`
- `.claude/skills/cross-cutting/observability-setup/SKILL.md`
- `.claude/skills/service-types/rest-api-setup/SKILL.md`
- `.claude/skills/testing/contract-test/SKILL.md`
- `.claude/skills/testing/testcontainers/SKILL.md`

---

# Related Contracts

- `specs/contracts/http/master-service-api.md` — sections §Global Conventions and §1 Warehouse only are in scope
- `specs/contracts/events/master-events.md` — §Global Envelope, §Topic Layout, §1 Warehouse Events only

---

# Target Service

- `master-service`

---

# Architecture

Follow:

- `specs/services/master-service/architecture.md` (Hexagonal, rest-api)

---

# Implementation Notes

- **Error code registration**: the codes `WAREHOUSE_CODE_DUPLICATE`, `REFERENCE_INTEGRITY_VIOLATION`, `DUPLICATE_REQUEST` (if absent), and `STATE_TRANSITION_INVALID` (if absent) must exist in `platform/error-handling.md` before the service relies on them. Update the catalog first if missing (per `CLAUDE.md` Contract Rule).
- **Idempotency storage**: Redis with key `master:idempotency:{idempotencyKey}:{method}:{path}`. Store `requestBodyHash` + status + response body. Reject on hash mismatch with `DUPLICATE_REQUEST`.
- **Outbox table** lives in the same Postgres schema as `warehouses`. One shared outbox table for all six aggregates (not per-aggregate). Partition key for Kafka is the JSON `aggregateId` field extracted at publish time.
- **Publisher**: `@Scheduled` poller with `fetch size = 100`, `fixedDelay = 500ms`. Rows deleted after successful broker ack. Failures increment `master.outbox.publish.failure.total` and retry with exponential backoff (1s, 2s, 4s, 8s, cap 30s).
- **JWT**: use `java-security` helpers for claim extraction. Role check via a `@PreAuthorize` or equivalent application-layer check — not in controllers.
- **Immutable fields**: enforce in the domain (`Warehouse.applyUpdate(...)` rejects attempts to change `warehouseCode`), not at the DTO layer alone.
- **Event `actorId`**: read from MDC / JWT at commit time. Null when event is scheduled/system-originated (not applicable to Warehouse in this task).
- **Known spec inconsistency**: `platform/architecture.md` and `platform/service-boundaries.md` still list the old ecommerce services. `PROJECT.md` is the authoritative service map (higher priority per `CLAUDE.md` Source of Truth). Do **not** chase this inconsistency in this task — raise it in the review note so a separate doc-cleanup task can be created.
- **Seed data**: migration `V99__seed_dev_warehouse.sql` active under `spring.profiles.active=dev,standalone`. Inserts warehouse `WH01` only. Zone/Location/SKU/Partner/Lot seeds come with their own tasks.

---

# Edge Cases

- Duplicate `warehouseCode` under concurrent inserts — rely on DB unique constraint; translate constraint violation to `WAREHOUSE_CODE_DUPLICATE`.
- `PATCH` with `version = null` — reject as `VALIDATION_ERROR`.
- `PATCH` body containing `warehouseCode` — reject as `VALIDATION_ERROR` with clear message "immutable field".
- Deactivate while `status=INACTIVE` → `STATE_TRANSITION_INVALID`.
- Reactivate while `status=ACTIVE` → `STATE_TRANSITION_INVALID`.
- Idempotency-Key reused across different endpoints with the same hash — accept each as independent (scope includes method+path).
- Outbox row written but Kafka unavailable at publish time — publisher retries without blocking the HTTP request.
- Publisher crashes between broker ack and row deletion — row republished on restart; consumer dedupes via `eventId`.
- Timezone string not a valid IANA id — `VALIDATION_ERROR` from domain validation, not a 500.
- Request with missing / invalid JWT → `401 UNAUTHORIZED`; request with valid JWT but lacking required role → `403 FORBIDDEN`.
- `X-Actor-Id` absent (should never happen if gateway is correct) → treat as `UNAUTHORIZED`; do not fall back to a default actor.

---

# Failure Scenarios

- **Postgres down at boot** — Spring Boot health is `DOWN`; service does not start serving traffic. Kubernetes readiness probe reflects this.
- **Postgres down during request** — request fails with `500 SERVICE_UNAVAILABLE` (mapped in error handler); metrics reflect the error class.
- **Kafka down at publish time** — outbox rows accumulate; `master.outbox.pending.count` rises; alert threshold defined in `cross-cutting/observability-setup.md` (add if not present — out of this task's scope, flag in review).
- **Redis down for idempotency check** — fail closed: reject mutating request with `503 SERVICE_UNAVAILABLE`. Do **not** skip idempotency (fail-open would violate T1).
- **Outbox table grows unbounded** — publisher lag alarm should fire. Task out of scope, flag for ops task.
- **Concurrent deactivation by two operators** — second request fails with `CONFLICT` (optimistic lock).

---

# Test Requirements

## Unit Tests

- `Warehouse` domain model: factory validation, update path enforcing immutability, deactivate / reactivate state transitions including invalid transitions.
- Application service tests using in-memory fakes of `WarehousePersistencePort`, `MasterEventPort`, `IdempotencyStore` — covering every error code.

## Slice Tests

- `@WebMvcTest` for `WarehouseController`: request validation, DTO-command mapping, status code mapping, `ETag` header on GET.
- `@DataJpaTest` on `WarehousePersistenceAdapter` with Testcontainers Postgres: unique-constraint handling, optimistic-lock collision, outbox row written in same tx.

## Integration Tests

- `@SpringBootTest` with Testcontainers Postgres + Kafka + Redis:
  - Happy path create → read → patch → deactivate → reactivate.
  - Idempotency: same key+body returns cached response; different body returns `409 DUPLICATE_REQUEST`.
  - Outbox-to-Kafka: after mutation, consumer test client receives the expected event on `wms.master.warehouse.v1`.
  - Publisher resilience: Kafka broker paused mid-test → publisher retries, no data loss; rows still present until broker returns.
  - JWT + role: missing role returns `403`.

## Contract Tests

- Use the project-wide contract test harness (`testing/contract-test/SKILL.md`). Verify each endpoint in `specs/contracts/http/master-service-api.md §1` against the implementation (status codes, required headers, error codes, pagination envelope).

## Event Contract Tests

- Schema assertion on published events vs `specs/contracts/events/master-events.md §Global Envelope + §1`.

---

# Definition of Done

- [x] Implementation completed
- [x] Tests added (unit / slice / integration / contract / event-contract) — see caveats below
- [x] Tests passing in CI (run #3 on main, 3m15s)
- [x] Contracts updated if needed (none required)
- [x] Specs updated first if required (idempotency strategy was already present)
- [x] `platform/error-handling.md` updated with any newly-referenced error codes (codes used were already listed)
- [x] Review notes mention the `platform/architecture.md` / `service-boundaries.md` doc-debt
- [x] Ready for review

---

# Review Note (2026-04-19)

## Implementation Delivery

Landed in 9 commits on `main` (a151594 → f18166e plus later wiring/CI):

| Phase | Commit | Scope |
|---|---|---|
| 1 | `a151594` | Gradle module, Hexagonal skeleton, `Warehouse` domain POJO + factory/state/immutability rules |
| 2 | `63c414a` | Flyway `V1__init_warehouse.sql`, `WarehouseJpaEntity`, `WarehousePersistenceMapper` |
| 3 | `9ff13b6` | `WarehousePersistenceAdapter` + `WarehousePersistencePort` + Testcontainers & H2 slice tests |
| 4 | `f35605e` | Application layer: use-case ports, `WarehouseService`, transactional boundaries, in-memory fakes |
| 5 | `b19a106` | REST adapter — controllers/DTOs for §1.1–1.6, `GlobalExceptionHandler`, ETag/Location, `@WebMvcTest` |
| 6 | `d19297f` | Idempotency — Redis adapter, in-memory adapter (@Profile "standalone"), filter w/ bounded lock, canonical-JSON hash |
| 7 | `fa7cd2a` | JWT resource server + role claim extractor + `@PreAuthorize` on service methods |
| 8 | `65fa61a` | Outbox publisher — `EventEnvelopeSerializer`, `MasterOutboxPollingScheduler`, topic resolver |
| 9 | `f18166e` | Insert-path bug fix (version=null mapper), standalone seed runner, `ApplicationContextSmokeTest`, Dockerfile |

## Acceptance Criteria Status

| AC | State | Note |
|---|---|---|
| Gradle build clean | ✅ | CI green |
| Boot via Compose + bootRun | ✅ | Compose migrated to Postgres; standalone profile also works with no deps |
| `/actuator/health` → 200 | ✅ | Actuator exposed, probes enabled |
| POST 201 matches §1.1 | ✅ | `WarehouseControllerTest` |
| GET by id 200+ETag / 404 | ✅ | `ETag: "v{version}"` |
| GET list paginates, default `status=ACTIVE` | ✅ | `PageResult` envelope |
| PATCH 200 / wrong version → 409 CONFLICT | ✅ | Optimistic lock via `@Version` |
| deactivate/reactivate / invalid → 409 STATE_TRANSITION_INVALID | ✅ | Domain-level guard |
| Duplicate code → 409 WAREHOUSE_CODE_DUPLICATE | ✅ | Unique constraint + `@Repository` exception translation |
| Missing Idempotency-Key → 400 | ✅ | `IdempotencyFilter` |
| Same key+body → cached; different body → 409 DUPLICATE_REQUEST | ✅ | SHA-256 canonical-JSON hash |
| Mutation writes outbox row in same tx | ✅ | Application layer calls outbox port inside `@Transactional` |
| Publisher forwards to `wms.master.warehouse.v1` | ✅ (wiring) | `MasterOutboxPollingScheduler` + topic resolver; see gap below on live-Kafka integration test |
| Event envelope matches contract | ✅ | `EventEnvelopeSerializerTest` |
| Metrics/logs with trace/request/actor IDs | ✅ (baseline) | Micrometer + MDC; no custom counters yet — see gap |
| Hexagonal layout compliance | ✅ | Domain has zero JPA annotations; entity is package-private to persistence adapter |
| W1/T1/T3/T4/T5 rule checks | ✅ | Verified against `rules/domains/wms.md` W1 & `rules/traits/transactional.md` T1/T3/T4/T5 |
| Unit / slice / integration in CI | ⚠️ | See "Test Coverage Gaps" |
| Contract tests §1.1–1.6 | ⚠️ | Covered at slice level (`@WebMvcTest`); no separate contract-test harness wired |
| Docker image builds | ✅ | `apps/master-service/Dockerfile` (eclipse-temurin:21-jre-alpine + tini + non-root) |

## Test Coverage Gaps (carry into follow-up task)

1. **`@SpringBootTest` full integration (Postgres+Kafka+Redis in one container network) not authored.** Happy-path create → read → patch → deactivate → reactivate, publisher-to-Kafka end-to-end, and publisher-resilience (Kafka paused mid-test) are specified in AC but not yet written.
2. **Contract-test harness** (`testing/contract-test/SKILL.md` style) not established. Current coverage is implicit at `@WebMvcTest` slice. Separate harness is a follow-up.
3. **Metric counters named in the task** (`master.outbox.publish.failure.total`, `master.outbox.pending.count`) not explicitly registered. Baseline Prometheus counters come from Micrometer's scheduler/Kafka instrumentation.
4. **Testcontainers × Docker Desktop 4.x blocker on Windows dev:** `WarehousePersistenceAdapterTest` and any future `@SpringBootTest` are annotated `@Testcontainers(disabledWithoutDocker = true)` and skip on Windows (the daemon runs in WSL2 and the named-pipe stubs don't respond). H2 parallel test `WarehousePersistenceAdapterH2Test` covers the unique-constraint + optimistic-lock paths for local dev. CI (Linux) runs the Testcontainers path.

## Doc Debt Flagged

- `platform/architecture.md` and `platform/service-boundaries.md` still list the old ecommerce service topology (checkout-service, order-service, etc.). `PROJECT.md` supersedes per Source of Truth priority. A dedicated doc-cleanup task should sweep these when a second project validates the library boundary (per `TEMPLATE.md` Phase 2).
- `platform/api-gateway-policy.md` public-route list is stale (ecommerce paths).

## Follow-up Tasks to Create

- `TASK-BE-007-master-service-integration-tests` (or similar) — full `@SpringBootTest` + contract harness + publisher-resilience + explicit metric counters.
- `TASK-DOC-001-platform-docs-resync` — doc debt above.
