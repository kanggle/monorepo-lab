# Task ID

TASK-BE-023

# Title

Reservation lifecycle — reserve, confirm, release, event consumers, TTL job

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

Implement the W4/W5 two-phase stock allocation: reserve → confirm (or release).

After this task:

- `PickingRequestedConsumer` consumes `outbound.picking.requested` → creates `Reservation`
  and decrements `Inventory.available_qty` / increments `reserved_qty` atomically
- `PickingCancelledConsumer` releases the reservation
- `ShippingConfirmedConsumer` confirms (terminal consume of reserved stock)
- REST endpoints allow `outbound-service` (via service account `INVENTORY_RESERVE`) and
  `INVENTORY_ADMIN` operators to manage reservations synchronously
- TTL job auto-releases expired `RESERVED` reservations
- `inventory.reserved`, `inventory.released`, `inventory.confirmed` events published via outbox
- Full Outbound Saga participation on inventory side (W4, W5, T7)

Depends on TASK-BE-021 (bootstrap) and TASK-BE-022 (Inventory domain, OutboxPublisher).

---

# Scope

## In Scope

- `Reservation` + `ReservationLine` domain model: fields per `domain-model.md §3`, state machine
  `RESERVED → CONFIRMED` / `RESERVED → RELEASED`; both terminal states immutable
- `Inventory` domain methods added in this task:
  - `reserve(qty, reservationId)` — `available -= qty`, `reserved += qty`
  - `release(qty, reservationId, reason)` — `reserved -= qty`, `available += qty`
  - `confirm(qty, reservationId)` — `reserved -= qty` (terminal)
- `ReserveStockUseCase`, `ConfirmReservationUseCase`, `ReleaseReservationUseCase` (in-ports +
  application service implementations)
- `PickingRequestedConsumer`: consumes `wms.outbound.picking.requested.v1`; calls
  `ReserveStockUseCase`; full rollback if any line fails (`INSUFFICIENT_STOCK`)
- `PickingCancelledConsumer`: consumes `wms.outbound.picking.cancelled.v1`; calls
  `ReleaseReservationUseCase` with `reason=CANCELLED`
- `ShippingConfirmedConsumer`: consumes `wms.outbound.shipping.confirmed.v1`; calls
  `ConfirmReservationUseCase`; enforces `shippedQuantity == reservedQuantity` per line
- REST endpoints:
  - `POST /api/v1/inventory/reservations` — Auth: `INVENTORY_RESERVE`
  - `POST /api/v1/inventory/reservations/{id}/confirm` — Auth: `INVENTORY_RESERVE`
  - `POST /api/v1/inventory/reservations/{id}/release` — Auth: `INVENTORY_ADMIN` or `INVENTORY_RESERVE`
  - `GET /api/v1/inventory/reservations/{id}` — Auth: `INVENTORY_READ`
  - `GET /api/v1/inventory/reservations` — Auth: `INVENTORY_READ`
- Idempotency-Key handling on POST endpoints (Redis)
- Domain-level idempotency for `picking_request_id` unique constraint (two-layer defense)
- `@Scheduled` TTL job `ReservationExpiryJob`: runs every minute; fetches `RESERVED` reservations
  where `expires_at < now()`; calls `ReleaseReservationUseCase` with `reason=EXPIRED`;
  `actorId = "system:reservation-ttl-job"` in outbox
- Published events: `inventory.reserved`, `inventory.released`, `inventory.confirmed`
  per `inventory-events.md §4, §5, §6`
- `inventory.reservation.active.count` gauge metric
- `inventory.optimistic-lock.retry.count{operation=RESERVE}` counter
- Optimistic-lock retry inside `ReserveStockService` (max 3 retries, jitter) before returning `CONFLICT`
- Unit, consumer, REST, integration tests per Test Requirements

## Out of Scope

- Stock adjustment / transfer (TASK-BE-024)
- Low-stock detection (TASK-BE-024)
- Partial shipment support (v2)
- Reservation TTL extension endpoint (not in v1)
- Multi-warehouse picking (v2)

---

# Acceptance Criteria

- [ ] `Inventory.reserve(qty, reservationId)`: `available >= qty` → `available -= qty`, `reserved += qty`; `available < qty` → `INSUFFICIENT_STOCK` (422); two Movement rows written (AVAILABLE -N, RESERVED +N)
- [ ] `Inventory.release(qty, reservationId, reason)`: `reserved >= qty` → `reserved -= qty`, `available += qty`; two Movement rows (RESERVED -N, AVAILABLE +N)
- [ ] `Inventory.confirm(qty, reservationId)`: `reserved >= qty` → `reserved -= qty`; one Movement row (RESERVED -N); `available` unchanged (W5)
- [ ] `Reservation` state machine: `RESERVED → CONFIRMED` and `RESERVED → RELEASED` only; `CONFIRMED → *` and `RELEASED → *` throw `STATE_TRANSITION_INVALID`
- [ ] `picking_request_id` is unique across all Reservations; duplicate triggers `DUPLICATE_REQUEST` (409) if body differs or `CONFLICT` if same body replayed after Redis TTL expiry
- [ ] `POST /reservations`: full rollback if any line fails; no partial reservation created
- [ ] `POST /reservations/{id}/confirm`: `shippedQuantity != reservedQuantity` → `RESERVATION_QUANTITY_MISMATCH` (422)
- [ ] `POST /reservations/{id}/release` with `reason=CANCELLED`: Reservation `→ RELEASED`, all line inventory restored
- [ ] `PickingRequestedConsumer`: `outbound.picking.requested` consumed → Reservation created, inventory reserved, `inventory.reserved` emitted on `wms.inventory.reserved.v1`
- [ ] `PickingCancelledConsumer`: `outbound.picking.cancelled` consumed → Reservation released, `inventory.released` emitted
- [ ] `ShippingConfirmedConsumer`: `outbound.shipping.confirmed` consumed → Reservation confirmed, `inventory.confirmed` emitted
- [ ] EventDedupe: all three consumers dedupe on `eventId`; re-delivery is a no-op
- [ ] `ReservationExpiryJob`: runs every minute; releases all `RESERVED` rows where `expires_at < now()`; emits `inventory.released` with `releasedReason=EXPIRED`; job runs inside a `@Transactional` per reservation batch
- [ ] `inventory.reservation.active.count` gauge reflects current count of `RESERVED` rows
- [ ] Optimistic-lock retry: `ReserveStockService` retries up to 3 times with jitter on `OptimisticLockException`; after exhaustion returns `CONFLICT` (409)
- [ ] All published event shapes match `inventory-events.md §4, §5, §6`
- [ ] `GET /reservations/{id}` returns full Reservation with `lines` array; `404 RESERVATION_NOT_FOUND` on unknown id
- [ ] `GET /reservations` filters: `status`, `warehouseId`, `pickingRequestId` exact match work correctly
- [ ] All tests pass

---

# Related Specs

> **Before reading Related Specs**: Follow `platform/entrypoint.md` Step 0 — read `PROJECT.md`, then load `rules/common.md` plus `rules/domains/wms.md`, `rules/traits/transactional.md`, `rules/traits/integration-heavy.md`.

- `platform/testing-strategy.md`
- `platform/service-types/rest-api.md`
- `platform/service-types/event-consumer.md`
- `platform/error-handling.md`
- `specs/services/inventory-service/architecture.md` — §Saga Participation, §State Machines, §Concurrency Control (reservation race), §Key Domain Invariants
- `specs/services/inventory-service/domain-model.md` — §1 Inventory (reserve/release/confirm methods), §3 Reservation + ReservationLine, §2 InventoryMovement
- `specs/services/inventory-service/idempotency.md` — §1.7 Domain-level idempotency for Reservations, §2 Kafka Consumer Idempotency, §3 Decision Table

# Related Skills

- `.claude/skills/backend/architecture/hexagonal/SKILL.md`
- `.claude/skills/backend/transaction-handling/SKILL.md`
- `.claude/skills/backend/springboot-api/SKILL.md`
- `.claude/skills/backend/testing-backend/SKILL.md`
- `.claude/skills/messaging/idempotent-consumer/SKILL.md`
- `.claude/skills/messaging/outbox-pattern/SKILL.md`
- `.claude/skills/testing/testcontainers/SKILL.md`

---

# Related Contracts

- `specs/contracts/http/inventory-service-api.md` — §4 Reservations
- `specs/contracts/events/inventory-events.md` — §4 `inventory.reserved`, §5 `inventory.released`,
  §6 `inventory.confirmed` (published); §C2 `outbound.picking.requested`, §C3 `outbound.picking.cancelled`,
  §C4 `outbound.shipping.confirmed` (consumed)

---

# Target Service

- `inventory-service`

---

# Architecture

Follow:

- `specs/services/inventory-service/architecture.md` (Hexagonal, dual rest-api + event-consumer)
- Saga participation: `inventory-service` is a **callee** in the Outbound Saga. Orchestration
  lives in `outbound-service`. Inventory only exposes operations and emits events.

---

# Implementation Notes

- **W4 two-phase**: reserve is a two-row Movement write (AVAILABLE -N + RESERVED +N). confirm
  is a one-row write (RESERVED -N). release is a two-row write (RESERVED -N + AVAILABLE +N).
  Each transition must be atomic with the Reservation status update and Outbox write.
- **Multi-row TX for reservations**: `ReserveStockService` loads Reservation + N Inventory rows
  in one `@Transactional`. Load Inventory rows in deterministic `location_id ASC` order to avoid
  deadlocks under concurrent reservation attempts for overlapping locations.
- **Optimistic lock retry**: Spring Retry or a manual retry loop. Max 3 attempts, jitter 100–300ms.
  `inventory.optimistic-lock.retry.count{operation=RESERVE}` increments on each retry.
  After 3 failures → propagate `CONFLICT` to caller.
- **Reservation create idempotency**: `POST /reservations` uses both Redis (24h) and the
  `picking_request_id` unique constraint. If Redis key expired but the reservation exists →
  the INSERT hits the unique constraint → application catches `DataIntegrityViolationException`
  → checks if existing reservation matches `pickingRequestId` → returns `DUPLICATE_REQUEST`.
- **TTL job**: use `@Transactional(propagation=REQUIRES_NEW)` per reservation batch (or per
  individual expiry) to avoid one failure aborting the entire sweep.
- **`actorId` for TTL job**: `"system:reservation-ttl-job"` — no JWT; set this string directly
  when building the outbox row.
- **`shippedQuantity` vs `reservedQuantity`**: v1 requires exact equality. `ConfirmReservationUseCase`
  checks each line before calling any `Inventory.confirm()`. If any mismatch → `RESERVATION_QUANTITY_MISMATCH` (422); full rollback; no inventory state changed.

---

# Edge Cases

- `POST /reservations` with 10 concurrent requests for the same `(locationId, skuId, lot)` —
  the sum of requested quantities may exceed available stock; optimistic lock retries ensure
  only feasible allocations succeed; others receive `INSUFFICIENT_STOCK` or `CONFLICT`
- `outbound.picking.requested` received twice for same `pickingRequestId` (broker redelivery) —
  second consumption hits EventDedupe → `IGNORED_DUPLICATE`; no double reservation
- `outbound.picking.cancelled` for a reservation already in `RELEASED` state (e.g., expired
  before cancel event arrives) — `STATE_TRANSITION_INVALID`; consumer logs WARN and does NOT
  route to DLT (terminal state = already resolved; treat as no-op by catching the exception)
- `outbound.shipping.confirmed` for a reservation already in `CONFIRMED` state — same: no-op
  (EventDedupe catches it first if the event is a duplicate; if not, domain throws
  `STATE_TRANSITION_INVALID` which is caught and treated as no-op for already-confirmed)
- Reservation lines reference an `inventoryId` that no longer exists (unlikely but theoretically
  possible after archive) — `INVENTORY_NOT_FOUND`; goes to DLT; requires ops investigation
- `expires_at` set to past time — `VALIDATION_ERROR` at API layer; `ttlSeconds < 1` also rejected
- TTL job runs concurrently with a `release` REST call for the same reservation — optimistic
  lock on Reservation prevents double-release; one wins, one retries and finds `RELEASED`
  terminal state → no-op

---

# Failure Scenarios

- **Kafka unavailable during `PickingRequestedConsumer`** — Spring Kafka retries; if retries exhausted before Kafka recovers → DLT; Outbound Saga sweeper in `outbound-service` re-emits after 5-minute timeout
- **`PickingRequestedConsumer` TX fails after Inventory rows updated but before Outbox written** — full rollback; no outbox row; no `inventory.reserved` emitted; Saga sweeper re-emits `picking.requested`; second consumption deduplicated by EventDedupe
- **Postgres down during TTL job** — job fails silently (transaction rolls back); reservations remain `RESERVED`; job retries on next scheduled tick; no double-release possible
- **Optimistic lock retry exhausted under high concurrency** — `CONFLICT` returned to `outbound-service`; Saga sweeper re-emits `picking.requested` after timeout; second attempt may succeed after contention resolves
- **`ReservationExpiryJob` crashes mid-sweep** — processed reservations are committed (REQUIRES_NEW); unprocessed ones are picked up on next job run; no double-release due to optimistic lock

---

# Test Requirements

## Unit Tests

- `Inventory.reserve`: sufficient stock → correct buckets; insufficient → `INSUFFICIENT_STOCK`; two Movement rows
- `Inventory.release`: sufficient reserved → correct buckets; insufficient → `INSUFFICIENT_STOCK`; two Movement rows
- `Inventory.confirm`: sufficient reserved → correct buckets; `available` unchanged; one Movement row (W5)
- `Reservation` state machine: valid transitions; terminal → `STATE_TRANSITION_INVALID`
- `ReserveStockService` with port fakes: all-lines success; one-line failure → full rollback; idempotency hit → no mutation

## Consumer Tests

- `PickingRequestedConsumer`: happy path; EventDedupe re-delivery; `INSUFFICIENT_STOCK` → DLT
- `PickingCancelledConsumer`: happy path; already-RELEASED reservation → no-op (no DLT)
- `ShippingConfirmedConsumer`: happy path; `RESERVATION_QUANTITY_MISMATCH` → DLT

## REST Tests (`@WebMvcTest`)

- `POST /reservations`: missing `Idempotency-Key` → 400; missing `lines` → 400; happy path → 201
- `POST /reservations/{id}/confirm`: unknown id → 404; already CONFIRMED → 422; mismatch qty → 422
- `POST /reservations/{id}/release`: unknown id → 404; already RELEASED → 422; success → 200
- `GET /reservations/{id}`: 200 with lines; 404 for unknown
- `GET /reservations`: filter by `status`, `pickingRequestId` works

## Failure-Mode Tests

- Same `Idempotency-Key` POST twice → identical 201; mutation applied once
- Same `eventId` consumed twice → mutation applied once; second outcome = `IGNORED_DUPLICATE`
- 10 concurrent reserve requests: sum > available → correct subset succeeds; totals constrained
- TTL job: seed a `RESERVED` reservation with `expires_at = now() - 1s`; job run → `RELEASED`; `inventory.released` emitted

## Integration Test

- Full path: `outbound.picking.requested` consumed → Reservation RESERVED + Inventory reserved → `inventory.reserved` on Kafka → `outbound.shipping.confirmed` → `inventory.confirmed` on Kafka

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added and passing
- [ ] `inventory-events.md §4, §5, §6` shapes verified in event contract tests
- [ ] `inventory-service-api.md §4` endpoints verified in REST tests
- [ ] W4, W5 rules checked and documented in AC completion notes
- [ ] Ready for review
