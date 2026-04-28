# Task ID

TASK-BE-022

# Title

Inventory core domain, ReceiveStockUseCase, PutawayCompletedConsumer, and query endpoints

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

Implement the `Inventory` domain aggregate and its first mutation path (RECEIVE) plus
all read-side query endpoints. After this task:

- `PutawayCompletedConsumer` consumes `inbound.putaway.completed`, calls `ReceiveStockUseCase`,
  and increases `available_qty` for each line in one `@Transactional` boundary per line
- `inventory.received` event is published via the outbox (Outbox publisher also implemented here)
- All query endpoints (`GET /inventory`, `GET /inventory/{id}`, `GET /inventory/by-key`,
  `GET /inventory/{id}/movements`, `GET /inventory/movements`) return correct data
- The Inventory domain enforces its non-negative bucket invariants at the domain layer
- MasterReadModel validation (Location/SKU/Lot status check) is applied before any mutation

Depends on TASK-BE-021 (bootstrap, MasterReadModel, EventDedupe infrastructure).

---

# Scope

## In Scope

- `Inventory` domain POJO: fields, `receive(qty)` method, bucket non-negative invariant,
  `(location_id, sku_id, lot_id)` uniqueness semantics, `warehouse_id` denormalization
- `InventoryMovement` domain POJO: append-only, `qty_after = qty_before + delta` structural check,
  `reason_code` enum, all fields per `domain-model.md §2`
- `ReceiveStockUseCase` (in-port) + `ReceiveStockService` (application service):
  upsert Inventory row if absent; call `Inventory.receive(qty)`; write `InventoryMovement`
  row (RECEIVE, PUTAWAY, `bucket=AVAILABLE`); write `InventoryOutbox` row; all in one TX
- `PutawayCompletedConsumer`: consumes `wms.inbound.putaway.completed.v1`; parses payload
  per `inventory-events.md §C1`; calls `ReceiveStockUseCase` per line; EventDedupe per event
- `QueryInventoryUseCase` (in-port) + implementation: queries for list, by-id, by-key
- `MovementQueryUseCase`: paginated movement history per inventory row; cross-row movement query
- REST controllers and DTOs:
  - `GET /api/v1/inventory` — list with filters (`warehouseId`, `locationId`, `skuId`, `lotId`,
    `hasStock`, `minAvailable`), pagination
  - `GET /api/v1/inventory/{id}` — single row with `ETag`
  - `GET /api/v1/inventory/by-key` — by `(locationId, skuId, lotId)`; 404 = zero stock
  - `GET /api/v1/inventory/{inventoryId}/movements` — paginated, filters per contract
  - `GET /api/v1/inventory/movements` — cross-row; `occurredAfter` required if `inventoryId` absent
- Response DTOs: `InventoryResponse` with `onHandQty` computed field; `MovementResponse`
- `OutboxPublisher`: `@Scheduled` poller (`fixedDelay=500ms`, `fetchSize=100`); publishes to
  Kafka; deletes row on ack; exponential backoff on failure (1s → 2s → 4s → 8s → cap 30s);
  metrics `inventory.outbox.pending.count`, `inventory.outbox.lag.seconds`,
  `inventory.outbox.publish.failure.total`
- `inventory.received` event shape per `inventory-events.md §1`
- Unit tests: Inventory domain, ReceiveStockService, MovementWriter invariant
- Consumer tests: PutawayCompletedConsumer happy-path, EventDedupe re-delivery skip, DLT routing
- REST tests: `@WebMvcTest` for all 5 query endpoints
- Integration test: putaway → received event emitted on Kafka; query returns the stocked row

## Out of Scope

- `reserve`, `confirm`, `release` domain methods (TASK-BE-023)
- `adjust`, `transferOut`, `transferIn`, `markDamaged`, `writeOffDamaged` (TASK-BE-024)
- Mutation REST endpoints: POST /adjustments, /transfers, /reservations (subsequent tasks)
- Low-stock detection (TASK-BE-024)
- Reservation TTL job (TASK-BE-023)

---

# Acceptance Criteria

- [ ] `Inventory` domain POJO: `receive(qty)` increments `available_qty`; `qty <= 0` throws `VALIDATION_ERROR`; Location/SKU/Lot status validated against snapshot before mutation
- [ ] `InventoryMovement` factory: `qty_after != qty_before + delta` throws `IllegalStateException` (structural invariant, never a business error)
- [ ] `ReceiveStockUseCase`: if no Inventory row exists for `(locationId, skuId, lotId)`, a new row is created with `available_qty = qty`; if row exists, `available_qty += qty`; `inventory_movement` row written in same TX
- [ ] `PutawayCompletedConsumer`: one Inventory + Movement + Outbox row per line in the event payload; all in one TX per event (all lines processed atomically; if one line fails, full rollback)
- [ ] `inventory.received` published on `wms.inventory.received.v1`; envelope shape matches `inventory-events.md §Global Envelope`; payload shape matches `§1`
- [ ] `EventDedupe`: same `eventId` consumed twice → second call is no-op; DB row `outcome=IGNORED_DUPLICATE`
- [ ] Outbox publisher: after `ReceiveStockUseCase` commits, the outbox poller publishes the event within 1 second (integration test with Testcontainers Kafka)
- [ ] `GET /api/v1/inventory` filters work: `warehouseId`, `locationId`, `skuId`, `hasStock=true` returns only rows with any bucket > 0
- [ ] `GET /api/v1/inventory/{id}` returns `404 INVENTORY_NOT_FOUND` for unknown id; `ETag: "v{version}"` on success
- [ ] `GET /api/v1/inventory/by-key` returns `404` (not an error payload) when no row exists; returns the row when it does
- [ ] `GET /inventory/{inventoryId}/movements` returns movement rows in descending `occurredAt` order; `movementType`, `bucket`, `delta`, `qtyBefore`, `qtyAfter`, `reasonCode` are populated
- [ ] `GET /inventory/movements` without `inventoryId` and without `occurredAfter` → `400 VALIDATION_ERROR`
- [ ] `onHandQty = availableQty + reservedQty + damagedQty` in all responses
- [ ] `locationCode`, `skuCode`, `lotNo` in responses are populated from `MasterReadModel` (not from master-service via REST)
- [ ] Location/SKU/Lot status check: if location snapshot is `INACTIVE`, `ReceiveStockUseCase` throws `LOCATION_INACTIVE` (422); same for `SKU_INACTIVE`, `LOT_INACTIVE`, `LOT_EXPIRED`
- [ ] Outbox publisher metrics: `inventory.outbox.pending.count` gauge; `inventory.outbox.lag.seconds` histogram; `inventory.outbox.publish.failure.total` counter — all registered and scraped
- [ ] `inventory.mutation.count{operation=RECEIVE}` counter increments on every successful receive
- [ ] Unit + consumer + REST + integration tests all pass

---

# Related Specs

> **Before reading Related Specs**: Follow `platform/entrypoint.md` Step 0 — read `PROJECT.md`, then load `rules/common.md` plus `rules/domains/wms.md`, `rules/traits/transactional.md`, `rules/traits/integration-heavy.md`.

- `platform/testing-strategy.md`
- `platform/service-types/rest-api.md`
- `platform/service-types/event-consumer.md`
- `platform/error-handling.md`
- `specs/services/inventory-service/architecture.md` — §Responsibility (quantity buckets), §Event Publication, §Event Consumption, §Key Domain Invariants, §Persistence
- `specs/services/inventory-service/domain-model.md` — §1 Inventory, §2 InventoryMovement, §8 MasterReadModel, §6 InventoryOutbox
- `specs/services/inventory-service/idempotency.md` — §2 Kafka Consumer Idempotency, §2.4 Master Snapshot (version guard)
- `rules/domains/wms.md` — W1 (transactional boundary), W2 (append-only movement)
- `rules/traits/transactional.md` — T3 (outbox), T8 (dedupe)
- `rules/traits/integration-heavy.md` — I5 (DLQ)

# Related Skills

- `.claude/skills/backend/architecture/hexagonal/SKILL.md`
- `.claude/skills/backend/springboot-api/SKILL.md`
- `.claude/skills/backend/transaction-handling/SKILL.md`
- `.claude/skills/backend/pagination/SKILL.md`
- `.claude/skills/backend/testing-backend/SKILL.md`
- `.claude/skills/messaging/outbox-pattern/SKILL.md`
- `.claude/skills/messaging/idempotent-consumer/SKILL.md`
- `.claude/skills/testing/testcontainers/SKILL.md`

---

# Related Contracts

- `specs/contracts/http/inventory-service-api.md` — §1 Inventory queries, §5 Movement History
- `specs/contracts/events/inventory-events.md` — §1 `inventory.received` (published), §C1 `inbound.putaway.completed` (consumed)

---

# Target Service

- `inventory-service`

---

# Architecture

Follow:

- `specs/services/inventory-service/architecture.md` (Hexagonal, dual rest-api + event-consumer)

---

# Implementation Notes

- **Upsert on receive**: `ReceiveStockUseCase` uses
  `INSERT INTO inventory (...) VALUES (...) ON CONFLICT (location_id, sku_id, COALESCE(lot_id, '00000000-0000-0000-0000-000000000000')) DO UPDATE SET available_qty = inventory.available_qty + EXCLUDED.available_qty, version = inventory.version + 1`.
  The partial-unique index handling for nullable `lot_id` is documented in `domain-model.md §1` and
  must be implemented as a DB expression index in V1 migration.
- **Movement rows are append-only**: the `InventoryMovementRepository` exposes only `save` (INSERT) — no update/delete methods. The DB role revocation from TASK-BE-021 enforces this at DB level.
- **OutboxPublisher is a `@Scheduled` bean**: `@TransactionalEventListener(phase = AFTER_COMMIT)` is an alternative but introduces complexity with retry. Use the polling approach (consistent with master-service).
- **`by-key` endpoint returns 404 for zero stock**: the contract explicitly says callers should treat 404 as zero-stock (not an application error). Return `404` with no error body, or with `INVENTORY_NOT_FOUND` — pick one and document it in the response. Recommended: return `404 INVENTORY_NOT_FOUND` for consistency; callers handle it as zero.
- **Response enrichment**: `locationCode`, `skuCode`, `lotNo` are read from `MasterReadModelPort` in the query adapter. If the snapshot is missing (race on first boot), return `null` for display fields — do not fail the query.
- **Outbox partition key**: `location_id` for inventory mutations (per `domain-model.md §6`).

---

# Edge Cases

- `inbound.putaway.completed` with multiple lines for the same `(locationId, skuId)` in one event — each line is a separate `ReceiveStockUseCase` call; all in one TX; second call increments further
- `inbound.putaway.completed` arrives before the corresponding Location snapshot is in `MasterReadModel` (startup race) — `ReceiveStockUseCase` checks `MasterReadModelPort`; if missing → allow receive (no snapshot = no status check; treat as ACTIVE for receive path; document this as a startup-race allowance)
- `lot_id` present in putaway event but SKU is non-LOT-tracked — `VALIDATION_ERROR`; event goes to DLT
- Zero qty in a putaway line — `VALIDATION_ERROR`; event goes to DLT
- DB unique constraint violation on Inventory upsert under concurrent receives at same location+sku — UPSERT handles this; no conflict possible with the ON CONFLICT clause
- `GET /inventory/movements` with `inventoryId` absent and `occurredAfter` present but very old date → allow (large date range is caller's responsibility); add a note in implementation that large ranges should use pagination

---

# Failure Scenarios

- **Kafka unavailable when outbox publisher fires** — publisher increments failure counter; row remains; publisher retries with backoff; no data loss; `inventory.outbox.pending.count` rises
- **Redis unavailable** — does not affect this task (no REST mutations; query endpoints and consumer path don't use idempotency store)
- **Postgres down during PutawayCompletedConsumer** — Spring Kafka retries the message; if Postgres recovers before 3 retries exhausted, message applied; else goes to DLT
- **MasterReadModel snapshot missing for deactivated location** — `ReceiveStockUseCase` cannot check status; allow receive (startup-race allowance); subsequent consumer refresh will populate status; new mutations will then be checked

---

# Test Requirements

## Unit Tests

- `Inventory.receive(qty)`: `qty > 0` increments available; `qty <= 0` throws; multiple receives accumulate correctly
- `InventoryMovement` factory: `qty_after = qty_before + delta` structural assertion
- `ReceiveStockService` with port fakes: creates row when absent; increments when present;
  writes movement row; writes outbox row; throws `LOCATION_INACTIVE` when location snapshot is inactive

## Consumer Tests (`@SpringBootTest` with embedded Kafka or Testcontainers Kafka)

- `PutawayCompletedConsumer`: publish valid `inbound.putaway.completed` → Inventory row created + movement written + outbox row written; verify with DB assertions
- Re-delivery with same `eventId` → no second inventory row or movement; DB `outcome=IGNORED_DUPLICATE`
- Malformed JSON → DLT receives the message after 3 retries

## REST Tests (`@WebMvcTest`)

- `GET /inventory` — filter params, pagination envelope, `onHandQty` computed
- `GET /inventory/{id}` — 200 with ETag; 404 on unknown id
- `GET /inventory/by-key` — 200 present; 404 absent
- `GET /inventory/{id}/movements` — 200 with movement list
- `GET /inventory/movements` — 400 when `inventoryId` absent and `occurredAfter` absent

## Integration Test (`@SpringBootTest`, Testcontainers)

- Full path: publish `inbound.putaway.completed` → poll outbox → event published on
  `wms.inventory.received.v1` → `GET /inventory/by-key` returns the stocked row
- `onHandQty = availableQty` on first receive (no reserved or damaged yet)
- Outbox publisher metrics are scraped and > 0 after first publish

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added and passing
- [ ] `inventory-events.md §C1` shape confirmed in consumer; no schema deviation
- [ ] `inventory-events.md §1` envelope validated in event contract test
- [ ] `inventory-service-api.md §1 + §5` endpoints verified in REST test
- [ ] Ready for review
