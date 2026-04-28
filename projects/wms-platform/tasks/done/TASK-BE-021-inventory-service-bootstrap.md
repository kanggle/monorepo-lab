# Task ID

TASK-BE-021

# Title

Bootstrap inventory-service with Hexagonal skeleton, Flyway schema, and MasterReadModel consumers

# Status

ready

# Owner

backend

# Task Tags

- code
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

Stand up the `inventory-service` Spring Boot application skeleton following the
declared Hexagonal architecture, and deliver the infrastructure foundation:

- Flyway-managed DB schema for all tables declared in `domain-model.md`
- MasterReadModel consumers (`MasterLocationConsumer`, `MasterSkuConsumer`, `MasterLotConsumer`)
  that receive `master.*` events and upsert `LocationSnapshot`, `SkuSnapshot`, `LotSnapshot`
- Redis (idempotency), Kafka (consumer group `inventory-service`), PostgreSQL wired and healthy
- EventDedupe table + deduplication helper in place (used by all future consumers)
- InventoryOutbox table in place (used by all future mutation use-cases)

On completion `inventory-service` boots, subscribes to `master.*` topics, keeps a
local read-model cache of Location/SKU/Lot, and is ready for the Inventory domain
implementation in TASK-BE-022.

No inventory mutation logic is in scope for this task.

---

# Scope

## In Scope

- Gradle module under `apps/inventory-service/` (Spring Boot 3.x, Java 21)
- Shared libs wiring: `java-common`, `java-web`, `java-observability`, `java-security`,
  `java-messaging`, `java-test-support`
- Hexagonal package layout per `.claude/skills/backend/architecture/hexagonal/SKILL.md`:
  `adapter/in/{rest,messaging}`, `adapter/out/{persistence,event,masterref}`,
  `application/{port/{in,out},service,command,result}`, `domain/{model,event,service}`, `config/`
- Flyway migrations:
  - `V1__init_inventory_tables.sql` — `inventory`, `inventory_movement`, `inventory_outbox`,
    `inventory_event_dedupe`
  - `V2__init_reservation_tables.sql` — `reservation`, `reservation_line`
  - `V3__init_adjustment_transfer_tables.sql` — `stock_adjustment`, `stock_transfer`
  - `V4__init_master_readmodel.sql` — `location_snapshot`, `sku_snapshot`, `lot_snapshot`
  - `V5__role_grants.sql` — revoke UPDATE/DELETE on `inventory_movement` from app role (W2)
- `EventDedupeRepository` + `EventDedupeHelper` (upsert-or-skip pattern, used by all consumers)
- `MasterLocationConsumer`, `MasterSkuConsumer`, `MasterLotConsumer` — consume `wms.master.location.v1`,
  `wms.master.sku.v1`, `wms.master.lot.v1`; upsert snapshots; version-guard (ignore older events)
- `MasterReadModelPort` (out-port) + `MasterReadModelPersistenceAdapter` (implementation)
- DLQ wiring: `DefaultErrorHandler` with 3-retry exponential backoff + `DeadLetterPublishingRecoverer`
  for all consumers (baseline; individual consumers inherit it)
- Redis idempotency infrastructure: `IdempotencyStore` port + Redis adapter (key layout:
  `inventory:idempotency:{method}:{path_hash}:{key}`, TTL 86400s)
- JWT resource server config (via `java-security`); roles `INVENTORY_READ`, `INVENTORY_WRITE`,
  `INVENTORY_ADMIN`, `INVENTORY_RESERVE`
- Observability baseline: MDC `traceId` / `requestId` / `actorId`, Micrometer metrics,
  OTel propagation on Kafka consumer
- `GET /actuator/health` and `GET /actuator/info`
- Dev-profile seed: `V99__seed_dev_masterref.sql` — 1 LocationSnapshot, 1 SkuSnapshot,
  1 LotSnapshot seeded; activated only under Spring profile `dev` or `standalone`
- Unit tests for master consumer logic (version guard, upsert behavior)
- `@SpringBootTest` smoke: boots, health `UP`, all Flyway migrations applied

## Out of Scope

- Inventory domain logic (TASK-BE-022)
- Reservation lifecycle (TASK-BE-023)
- Adjustment / transfer / low-stock (TASK-BE-024)
- REST endpoints for inventory mutations or queries (TASK-BE-022)
- Outbox publisher implementation (bootstrapped here as table + placeholder; publishing
  starts in TASK-BE-022 when the first use-case is implemented)
- Gateway route wiring (separate TASK-INT-*)

---

# Acceptance Criteria

- [ ] `./gradlew :apps:inventory-service:build` passes cleanly
- [ ] Service boots against Docker Compose (Postgres + Kafka + Redis) and `./gradlew bootRun`
- [ ] `GET /actuator/health` returns `200 UP` when all dependencies are reachable
- [ ] All 5 Flyway migrations applied cleanly on boot (V1–V5 plus V99 under `dev` profile)
- [ ] `inventory_movement` table: `UPDATE` and `DELETE` are rejected for the app role (V5 role grant); verified by an explicit Testcontainers test that asserts the rejection
- [ ] `MasterLocationConsumer` upserts `location_snapshot` on `master.location.created` / `.updated` / `.deactivated` / `.reactivated`; verified by Testcontainers Kafka test
- [ ] `MasterSkuConsumer` and `MasterLotConsumer` behave identically for their respective snapshot tables
- [ ] Version guard: a `master.location.updated` event with `master_version <= cached` is silently ignored (no upsert, no error); a later event with higher version applies
- [ ] `EventDedupeHelper.process(eventId, ...)` inserts and returns `APPLIED` on first call; on second call with same `eventId` returns `IGNORED_DUPLICATE` without re-executing the lambda
- [ ] EventDedupe + master snapshot upsert are in the **same** `@Transactional` boundary (atomic)
- [ ] DLQ routing: an unparseable master event is routed to `wms.master.location.v1.DLT` after 3 retries; test verifies the DLT receives the message
- [ ] `IdempotencyStore` Redis adapter: `store(key, response)` persists; `get(key)` retrieves within TTL; TTL is 24h
- [ ] JWT roles loaded correctly; a request without bearer token on a future protected endpoint will return `401` (verified via `@WebMvcTest` stub endpoint or smoke test)
- [ ] `MasterReadModelPort` methods `findLocation(id)`, `findSku(id)`, `findLot(id)` return empty Optional when not in cache
- [ ] Structured logs contain `traceId`, `eventId`, `consumer` in MDC on every consumer log line
- [ ] Unit tests for consumer version-guard logic pass
- [ ] All Testcontainers tests pass in CI (Linux); Windows dev uses `@Testcontainers(disabledWithoutDocker=true)` where needed

---

# Related Specs

> **Before reading Related Specs**: Follow `platform/entrypoint.md` Step 0 — read `PROJECT.md`, then load `rules/common.md` plus `rules/domains/wms.md`, `rules/traits/transactional.md`, `rules/traits/integration-heavy.md`.

- `platform/architecture.md`
- `platform/architecture-decision-rule.md`
- `platform/shared-library-policy.md`
- `platform/testing-strategy.md`
- `platform/service-types/rest-api.md`
- `platform/service-types/event-consumer.md`
- `specs/services/inventory-service/architecture.md`
- `specs/services/inventory-service/domain-model.md` — §8 MasterReadModel, §7 EventDedupe, §6 InventoryOutbox
- `specs/services/inventory-service/idempotency.md` — §2 Kafka Consumer Idempotency
- `rules/domains/wms.md` — W2 (append-only movement), W6 (master ref integrity)
- `rules/traits/transactional.md` — T3 (outbox table), T8 (eventId dedupe)
- `rules/traits/integration-heavy.md` — I1–I3 (retry), I5 (DLQ), I8 (model translation)

# Related Skills

- `.claude/skills/backend/architecture/hexagonal/SKILL.md`
- `.claude/skills/backend/springboot-api/SKILL.md`
- `.claude/skills/backend/jwt-auth/SKILL.md`
- `.claude/skills/backend/observability-metrics/SKILL.md`
- `.claude/skills/backend/testing-backend/SKILL.md`
- `.claude/skills/database/schema-change-workflow/SKILL.md`
- `.claude/skills/database/migration-strategy/SKILL.md`
- `.claude/skills/messaging/idempotent-consumer/SKILL.md`
- `.claude/skills/messaging/event-implementation/SKILL.md`
- `.claude/skills/service-types/event-consumer-setup/SKILL.md`
- `.claude/skills/testing/testcontainers/SKILL.md`

---

# Related Contracts

- `specs/contracts/events/master-events.md` — §1 Warehouse, §2 Zone, §3 Location, §4 SKU, §5 Partner, §6 Lot
  (all consumed for MasterReadModel; only Location / SKU / Lot affect inventory logic)

---

# Target Service

- `inventory-service`

---

# Architecture

Follow:

- `specs/services/inventory-service/architecture.md` (Hexagonal, dual rest-api + event-consumer)
  Both `platform/service-types/rest-api.md` and `platform/service-types/event-consumer.md` apply —
  documented exception in the architecture.

---

# Implementation Notes

- **DB role grants (W2)**: `V5__role_grants.sql` must contain
  `REVOKE UPDATE, DELETE ON inventory_movement FROM <app_role>;` where `<app_role>` is the
  application user configured in the datasource. The test that verifies rejection must use
  the same role, not a superuser connection.
- **EventDedupeHelper pattern**: the helper should accept `(eventId, eventType, Supplier<Void>)`.
  It does the INSERT-or-IGNORE, calls the supplier only on first occurrence, and returns
  the outcome enum. Called inside the consumer's `@Transactional` boundary.
- **Version guard for MasterReadModel**: use `ON CONFLICT (id) DO UPDATE SET ... WHERE snapshot.master_version < EXCLUDED.master_version`. No application-layer version check needed — the DB handles it atomically.
- **Outbox table**: create `inventory_outbox` in V1 (all columns per `domain-model.md §6`).
  The publisher process is **not** implemented in this task — the table is created and the
  `OutboxWriter` port stub exists. TASK-BE-022 wires the first actual use-case + publishing.
- **Consumer group**: `spring.kafka.consumer.group-id=inventory-service`. All consumers
  share the group. Partition assignment ensures per-location ordering.
- **Master consumer subscribes to all master topics** (not just Location/SKU/Lot) — Zone and
  Warehouse events are also received but currently ignored (no snapshot table). Implement
  a fallback `NoOpHandler` for unrecognized `aggregateType` values to avoid DLT pollution.

---

# Edge Cases

- `master.location.deactivated` received before `master.location.created` (out-of-order startup) — consumer upserts with `status=INACTIVE`; subsequent `created` event has lower `master_version` and is ignored by version guard
- Duplicate `master.sku.updated` event (broker redelivery) — `EventDedupeHelper` returns `IGNORED_DUPLICATE`; no double upsert
- Redis unavailable at idempotency store write — fail closed: return `503`; do not skip idempotency (T1)
- Kafka unavailable at startup — consumer retries connection per Spring Kafka retry policy; health probe reflects `DOWN` until connected
- `lot_snapshot` for a lot whose SKU is not yet in `sku_snapshot` — insert is allowed; SKU snapshot arrives eventually via `master.sku.created`

---

# Failure Scenarios

- **Postgres down at boot** — health `DOWN`; Kubernetes readiness probe fails; no traffic routed
- **Kafka down for master consumer** — consumer lag grows; `LocationSnapshot` becomes stale;
  mutations based on stale cache proceed with stale status (eventual consistency — documented in `domain-model.md §8`)
- **Redis down for idempotency** — fail closed with `503`; do not serve partial idempotency
- **Malformed master event in topic** — routed to DLT after 3 retries; alert triggered via DLT depth metric
- **V5 role grant migration fails** (insufficient DB privileges during Flyway) — boot fails; operator must grant `REVOKE` privilege to migration user

---

# Test Requirements

## Unit Tests

- `EventDedupeHelper`: first call applies lambda, second call skips — using in-memory repository fake
- `MasterLocationConsumer`: version-guard logic (lower version ignored, higher applied), status mapping
  (`deactivated` → `INACTIVE`, `reactivated` → `ACTIVE`)
- `MasterSkuConsumer`, `MasterLotConsumer`: same coverage

## Slice / Integration Tests

- Flyway migration test (Testcontainers Postgres): all 5 migrations apply cleanly; `inventory_movement`
  UPDATE rejection confirmed via direct JDBC on app-role connection
- `MasterLocationConsumer` Testcontainers Kafka test: publish `master.location.created` → snapshot upserted;
  publish same event again → dedupe skips; publish `master.location.deactivated` → status = INACTIVE
- DLT routing: publish a non-JSON message → verify it arrives on `wms.master.location.v1.DLT`

## Smoke Test

- `@SpringBootTest` with Testcontainers (Postgres + Kafka + Redis): boots, health UP,
  all migrations applied, consumer subscribes without error

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added and passing
- [ ] Contracts not changed (no mutation endpoints yet)
- [ ] Specs not changed (bootstrap only)
- [ ] Ready for review
