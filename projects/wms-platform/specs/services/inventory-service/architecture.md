# inventory-service — Architecture

This document declares the internal architecture of `inventory-service`.
All implementation tasks targeting this service must follow this declaration
and `platform/architecture-decision-rule.md`.

---

## Identity

| Field | Value |
|---|---|
| Service name | `inventory-service` |
| Service Type | `rest-api` + `event-consumer` (dual; see Service Type Composition below) |
| Architecture Style | **Hexagonal (Ports & Adapters)** |
| Primary language / stack | Java 21, Spring Boot |
| Bounded Context | **Inventory** (per `rules/domains/wms.md`) |
| Deployable unit | `apps/inventory-service/` |
| Data store | PostgreSQL (owned, not shared) |
| Event publication | Kafka via outbox (per trait `transactional`, rule T3) |
| Event consumption | Kafka with eventId-based dedupe (per trait `transactional`, rule T8) |

### Service Type Composition

`inventory-service` is the only WMS v1 service that combines two service types in
one deployable unit:

- `rest-api` for synchronous mutations (adjustment, manual transfer, reserve, confirm)
  and queries (real-time stock lookup).
- `event-consumer` for asynchronous inbound:
  - `inbound.putaway.completed` → increase available stock
  - `outbound.picking.requested` → reserve stock
  - `outbound.shipping.confirmed` → confirm reserved → consumed
  - `master.location.deactivated` / `master.sku.deactivated` → local cache update + flag

Both surfaces share the same domain model and persistence. Read both
`platform/service-types/rest-api.md` and `platform/service-types/event-consumer.md`
when implementing — this is the documented exception to the
"read exactly one service-type file" rule, justified here.

---

## Responsibility

`inventory-service` owns **on-hand stock at every location** for every SKU (and Lot
when applicable). It is the **single system of record** for:

- **Inventory** — how much of `(location, sku, [lot])` is currently held, in three
  buckets: `available`, `reserved`, `damaged`
- **Inventory Movement** — append-only history of every quantity change
  (W2: 재고 변동 이력)
- **Stock Adjustment** — reason-recorded delta corrections (실사·파손·분실)
- **Stock Transfer** — atomic move between two locations within the same warehouse

### Quantity Buckets

Every `(location, sku, lot)` row exposes:

| Bucket | Meaning | Decreases when |
|---|---|---|
| `available` | Free for new picking allocation | reserve, adjust-down, transfer-out |
| `reserved` | Allocated to an outbound picking request, not yet shipped | confirm, release |
| `damaged` | Quarantined / unsellable; visible to ops, not pickable | manual write-off |

`on_hand = available + reserved + damaged` is a **derived** read, not a stored column.

---

## Out of Scope

`inventory-service` does NOT own:

- Master data identity (Warehouse / Zone / Location / SKU / Partner / Lot identity)
  — owned by `master-service`. Inventory only references their `id` values and keeps
  a read-model cache of name / status for display
- ASN lifecycle (`inbound-service`)
- Order, picking-request, packing, shipping lifecycle (`outbound-service`)
- Notification delivery (`notification-service`)
- Multi-warehouse transfer (a transfer that crosses warehouse boundaries is modeled
  as **outbound-from-A + inbound-to-B**, not as a single `inventory-service`
  transaction; out of v1)

If a change request introduces any of the above, promote it to the owning service.

---

## Architecture Style

Hexagonal (Ports & Adapters)

### Rationale

- WMS traits (`transactional`, `integration-heavy`) demand sharp separation between
  domain rules (W1 transactional protection, W2 history append-only, W4 reserve→confirm
  two-phase) and infrastructure (JPA, Kafka producer/consumer, future scanner adapters).
- Inventory has the **highest density of domain invariants** in the project
  (every quantity mutation must atomically: validate, mutate, append history, write
  outbox). Hexagonal makes the invariant set easy to test in isolation against
  port fakes.
- Uniform with `master-service`, `inbound-service`, `outbound-service` — same
  package layout reduces cognitive load when navigating between WMS services.

### Trade-off Accepted

- Inventory is a write-heavy CRUD-shaped surface. Hexagonal ~doubles the file count
  versus a Layered approach. This cost is paid because the **domain logic is
  non-trivial** (two-bucket reserve / confirm semantics, optimistic-locked
  multi-row transfer, idempotent event consumption) and because uniformity with
  the rest of the WMS services has long-term value.

### Package Structure

Follow `.claude/skills/backend/architecture/hexagonal/SKILL.md` exactly.

```
com.wms.inventory/
├── adapter/
│   ├── in/
│   │   ├── rest/
│   │   │   ├── controller/      # InventoryController, AdjustmentController, TransferController, ReservationController
│   │   │   └── dto/{request,response}/
│   │   └── messaging/
│   │       └── consumer/        # PutawayCompletedConsumer, PickingRequestedConsumer,
│   │                            # ShippingConfirmedConsumer, MasterLocationConsumer, MasterSkuConsumer,
│   │                            # MasterLotConsumer
│   └── out/
│       ├── persistence/
│       │   ├── entity/          # JPA entities — package-private
│       │   ├── repository/      # Spring Data JPA repositories
│       │   ├── mapper/          # Domain <-> JPA mappers
│       │   └── adapter/         # *PersistenceAdapter implementing out ports
│       ├── event/
│       │   ├── outbox/          # OutboxEntity, OutboxWriter
│       │   └── publisher/       # Kafka publisher reading outbox
│       └── masterref/
│           └── readmodel/       # Local read-model store for master snapshots
├── application/
│   ├── port/
│   │   ├── in/                  # Use-case interfaces (AdjustStockUseCase, TransferStockUseCase,
│   │   │                        #   ReserveStockUseCase, ConfirmReservationUseCase, ReleaseReservationUseCase,
│   │   │                        #   ReceiveStockUseCase, QueryInventoryUseCase)
│   │   └── out/                 # InventoryPersistencePort, MovementPersistencePort,
│   │                            #   InventoryEventPort, MasterReadModelPort,
│   │                            #   IdempotencyStorePort, EventDedupePort
│   ├── service/                 # Use-case implementations (@Service, @Transactional)
│   ├── command/                 # Input records (AdjustStockCommand, TransferCommand, ReserveCommand, ...)
│   └── result/                  # Output records
├── domain/
│   ├── model/                   # Pure POJOs: Inventory, InventoryMovement, StockAdjustment, StockTransfer, ReservationLine
│   ├── event/                   # InventoryAdjusted, InventoryTransferred, StockReserved, StockConfirmed, StockReleased, StockReceived
│   └── service/                 # Domain services for invariants (ReservationCalculator, TransferValidator, MovementWriter)
└── config/                      # Spring configuration, bean wiring
```

### Layer Rules

1. **Domain layer has no framework dependency.** No `@Entity`, no `@Component`,
   no Spring. Pure POJOs enforce invariants via static factories and explicit
   state-transition methods (`reserve(qty)`, `confirm(reservationId)`,
   `release(reservationId)`, `adjust(delta, reason)`).
2. **Application layer depends only on ports.** Never on adapter classes.
   `@Transactional` boundary lives here. Every mutation is one
   `@Transactional` method that: loads aggregate(s), invokes domain method,
   writes movement row, writes outbox row — all in one transaction (W1).
3. **Adapters depend inward.** They implement outbound ports or call inbound ports.
   Adapter-internal types (JPA entities, Kafka records) never leak into ports.
4. **Inbound port grouping**:
   - `AdjustStockUseCase`, `TransferStockUseCase` — REST mutation paths
   - `ReserveStockUseCase`, `ConfirmReservationUseCase`, `ReleaseReservationUseCase` —
     reservation lifecycle (T4 W4 two-phase)
   - `ReceiveStockUseCase` — internal use-case invoked by `PutawayCompletedConsumer`
   - `QueryInventoryUseCase` — read-side queries
5. **Mappers are adapter-internal.** Domain `Inventory` ↔ `InventoryJpaEntity` lives
   in `adapter/out/persistence/mapper/` and is package-private.

---

## Dependencies (Inbound)

| Caller | Contract | Purpose |
|---|---|---|
| `gateway-service` | `specs/contracts/http/inventory-service-api.md` | External admin/ops UI calls (adjustment, transfer, query) |
| `outbound-service` | `specs/contracts/http/inventory-service-api.md` | Synchronous reservation calls during picking-request creation; query during order validation |
| `inbound-service` | Events only (`wms.inbound.putaway.completed.v1`) | Triggers stock receipt |
| `outbound-service` | Events (`wms.outbound.shipping.confirmed.v1`) | Triggers reservation→consumed transition |
| `admin-service` | `inventory-service-api.md` | Read-only dashboards, KPI |

`inventory-service` does NOT call any other WMS service synchronously in v1.
Master-data lookups go through the local read-model cache, populated by
`master.*` events.

---

## Dependencies (Outbound)

v1 outbound dependencies:

- **PostgreSQL** — owned DB
- **Kafka** — event publication (via outbox), event consumption (consumer group
  `inventory-service`)
- **Redis** — idempotency-key store and event-dedupe cache (T1, T8)

No external systems in v1. Future scanner / RFID integration would add an outbound
port (`ScannerCommandPort`) — not in v1.

---

## Event Publication

All inventory state changes publish events via the **transactional outbox pattern**
(trait `transactional`, rule T3):

| Event | Topic | Trigger |
|---|---|---|
| `inventory.received` | `wms.inventory.received.v1` | Stock added via putaway-completed consumer |
| `inventory.adjusted` | `wms.inventory.adjusted.v1` | Manual adjustment with reason |
| `inventory.transferred` | `wms.inventory.transferred.v1` | Successful transfer between locations |
| `inventory.reserved` | `wms.inventory.reserved.v1` | Picking allocation created |
| `inventory.released` | `wms.inventory.released.v1` | Reservation released (cancelled / expired) |
| `inventory.confirmed` | `wms.inventory.confirmed.v1` | Reserved → consumed (shipped) |
| `inventory.low-stock-detected` | `wms.inventory.alert.v1` | Crossed below configured threshold |

Full event schemas: `specs/contracts/events/inventory-events.md` (to be authored
before first implementation task — see Open Items).

---

## Event Consumption

Per `service-types/event-consumer.md` and trait `transactional` rule T8
(at-least-once → idempotent handler):

| Subscribed Event | Source Topic | Effect |
|---|---|---|
| `inbound.putaway.completed` | `wms.inbound.putaway.completed.v1` | Increase `available` at target location |
| `outbound.picking.requested` | `wms.outbound.picking.requested.v1` | Reserve from `available` to `reserved` |
| `outbound.picking.cancelled` | `wms.outbound.picking.cancelled.v1` | Release reservation |
| `outbound.shipping.confirmed` | `wms.outbound.shipping.confirmed.v1` | Consume reserved (final decrement) |
| `master.location.deactivated` | `wms.master.location.v1` | Mark local read-model entry; new mutations on that location → `LOCATION_INACTIVE` error |
| `master.sku.deactivated` | `wms.master.sku.v1` | Same, for SKU |
| `master.location.created` / `.updated` | `wms.master.location.v1` | Refresh local read-model |
| `master.sku.created` / `.updated` | `wms.master.sku.v1` | Same, for SKU |
| `master.lot.*` | `wms.master.lot.v1` | Local read-model refresh; lot identity is referenced by `Inventory`, `ReservationLine`, `StockTransfer` rows (LOT-tracked SKUs) |

### Master aggregate coverage in v1 (intentional gaps)

The Identity table (above) lists six master aggregates that `master-service`
publishes — `Warehouse / Zone / Location / SKU / Partner / Lot`. v1
inventory-service consumes only `Location / SKU / Lot`. The other three are
**intentionally not consumed** (TASK-BE-053 audit, 2026-05-11):

- **Warehouse** — `warehouse_id` is a denormalized FK column on
  `inventory`, `inventory_movement`, `stock_transfer`, etc., but
  inventory-service never reads warehouse master attributes (name,
  status, address). The `MasterReadModelPort` does **not** declare
  `findWarehouse(...)` — there is no v1 use case that needs the warehouse
  display name or active-flag. If the future operator console requires
  warehouse name resolution, a `MasterWarehouseConsumer` is added then;
  v1 cost was unjustified.
- **Zone** — `zone_id` is **not** a separate read-model. It rides on
  `LocationSnapshot` (`zoneId` field — populated by
  `MasterLocationConsumer` from the same `wms.master.location.v1` event
  payload). Zone-level inventory invariants (e.g., picking eligibility
  by DRY/COLD zone) are not v1 features; if introduced in v2, the
  `LocationSnapshot.zoneId` already provides the lookup key without a
  separate `MasterZoneConsumer`.
- **Partner** — zero references in inventory-service code (verified by
  repository-wide grep: `partner_id` / `partnerId` / `MasterPartner` /
  `PartnerSnapshot` all 0 results). Partner identity is owned by
  `inbound-service` (ASN supplier) and `outbound-service` (shipping
  customer), not by inventory.

If a v2 invariant requires any of these consumers, add the consumer + a
matching row to the **Subscribed Event** table above. The negative-coverage
note here exists so a future reviewer can confirm the absence is
intentional, not drift.

### Consumer Rules

- **EventId-based dedupe** (T8): every consumed message carries an `eventId`; consumer
  upserts into `event_dedupe(event_id, processed_at)` with unique constraint. Re-deliveries
  hit the constraint and become no-ops.
- **DLQ**: Spring Kafka `SeekToCurrentErrorHandler` with `DeadLetterPublishingRecoverer`
  routing to `*.DLT` topics after configured retries (3, exponential backoff with jitter).
  Per `integration-heavy` rule I5.
- **Ordering**: partition key = `location_id` (for inventory mutations) or `sku_id` (for
  master refreshes). Single partition per location ensures ordered application of
  putaway → reserve → ship for that location's stock.

---

## Idempotency

### Synchronous (REST)

All mutating endpoints (POST, PUT, PATCH, DELETE) accept `Idempotency-Key` header
per trait `transactional` rule T1.

- Storage: Redis (`inventory:idempotency:{key}` — response snapshot)
- TTL: 24 hours
- Scope: `(Idempotency-Key, method, path)` tuple

### Asynchronous (Kafka)

EventId dedupe table `inventory_event_dedupe`:

| Column | Type | Notes |
|---|---|---|
| `event_id` | UUID (PK) | From event header |
| `event_type` | String | For observability |
| `processed_at` | Instant | |
| `outcome` | enum `APPLIED` / `IGNORED_DUPLICATE` / `FAILED` | |

Retention: 30 days (cron purge). Long enough to absorb broker re-delivery windows
and operator-initiated DLQ replays.

Full strategy: `specs/services/inventory-service/idempotency.md`.

---

## Concurrency Control

### Optimistic Locking (default)

Every `Inventory` row carries `version` (`@Version`, JPA), bumped on every UPDATE.
Conflicts surface as `CONFLICT` per `platform/error-handling.md` (trait
`transactional` rule T5).

### Row-Level Contention Pattern

The hot path is **same-location concurrent updates** (e.g., two picking requests
reserving from the same `(location, sku)`). Pattern:

1. `SELECT ... FOR UPDATE` (pessimistic) is **forbidden** in v1 (per trait T5).
2. Instead: `SELECT ... `, mutate in domain, `UPDATE ... WHERE id = ? AND version = ?`.
3. On affected-rows = 0 (version mismatch) → throw `OptimisticLockException` →
   surface as HTTP 409 `CONFLICT` with retryable hint.
4. Caller is expected to retry with fresh state (REST clients) or rely on consumer
   redelivery (Kafka path).

### Reservation Race Condition

Concurrent reservation of the same SKU at the same location:

- Both calls read `available = 100`
- Both attempt to reserve 80 → second `UPDATE ... WHERE version = ?` fails
- Second call retries inside the application service (max 3 retries, with jitter)
- After exhaustion → `CONFLICT` returned to caller; outbound-service consumer
  retry pulls the message back next cycle

### Transfer Atomicity (W1)

A transfer mutates **two** `Inventory` rows (source decrement + target increment).
Both must succeed or both rolled back:

- Single `@Transactional` method
- Acquire rows in deterministic order by `location_id` (or PK) to avoid deadlocks
- Both rows version-checked; if either fails → full rollback
- One `inventory_movement` record per side (two rows total), one outbox entry
  (`inventory.transferred`) — all in same TX

---

## Key Domain Invariants

Enforced at the domain layer, surfaced via dedicated error codes from
`rules/domains/wms.md` § Inventory:

| Invariant | Source | Error code |
|---|---|---|
| Cannot reserve more than `available` | wms.md W4 | `INSUFFICIENT_STOCK` |
| Cannot confirm more than `reserved` for a given reservation | wms.md W4, W5 | `RESERVATION_QUANTITY_MISMATCH` |
| Cannot release a non-existent reservation | derived | `RESERVATION_NOT_FOUND` |
| Adjustment requires non-empty `reason` | wms.md (no direct UPDATE) | `ADJUSTMENT_REASON_REQUIRED` |
| Adjustment cannot drive any bucket below zero | derived | `INSUFFICIENT_STOCK` |
| Transfer source ≠ target location | wms.md | `TRANSFER_SAME_LOCATION` |
| Transfer source must have sufficient `available` | wms.md W1 | `INSUFFICIENT_STOCK` |
| All quantities are positive integers (EA) — fractional handled by UOM upstream | derived | `VALIDATION_ERROR` |
| Location must be `ACTIVE` (per local read-model snapshot) | wms.md W6 | `LOCATION_INACTIVE` |
| SKU must be `ACTIVE` (per local read-model snapshot) | wms.md W6 | `SKU_INACTIVE` |
| Lot must be `ACTIVE` and not `EXPIRED`, if SKU is LOT-tracked | wms.md | `LOT_INACTIVE` / `LOT_EXPIRED` |
| Movement records are append-only (no UPDATE / DELETE) | wms.md W2 | enforced via DB grant + repo design |
| Quantity buckets sum invariant: `available + reserved + damaged ≥ 0` | derived | structural |

> **v1 simplification**: cross-warehouse transfer is rejected — both source and
> target locations must share the same `warehouse_id` (looked up from the local
> read-model). Cross-warehouse moves are modeled as outbound-from-A + inbound-to-B
> in v2.

---

## Persistence

- Database: PostgreSQL (one logical DB per service; no cross-service reads)
- Migrations: Flyway, `apps/inventory-service/src/main/resources/db/migration/`
- Outbox table: `inventory_outbox` with columns
  `id, aggregate_type, aggregate_id, event_type, payload, created_at, published_at`
- Movement table: `inventory_movement` — append-only.
  Recommended DB-level enforcement: revoke `UPDATE`/`DELETE` from app role
  (only `INSERT`/`SELECT`). Documented and enforced via Flyway role grants.
- Event dedupe table: `inventory_event_dedupe(event_id PK, event_type, processed_at, outcome)`

High-level table layout in `specs/services/inventory-service/domain-model.md`.

---

## Observability

Per `service-types/rest-api.md` + `service-types/event-consumer.md`:

- Metrics: request rate, error rate, latency per endpoint; consumer lag,
  consumer error rate, DLT depth (per topic)
- Traces: OTel propagation on all inbound REST + outbound Kafka publishes;
  consumer-side traces continue from publisher trace context
- Logs: structured JSON with `traceId`, `requestId`, `actorId`, `eventId` in MDC
- **Business metrics**:
  - `inventory.mutation.count{operation,reason}` — adjustments / transfers / reserves / confirms
  - `inventory.reservation.active.count` — gauge of currently held reservations
  - `inventory.reservation.expiry.swept.total` — counter of reservations released by the TTL expiry job per tick (ADR-MONO-005 § D5)
  - `inventory.outbox.lag.seconds` — commit-to-publish delay
  - `inventory.event.dedupe.hit.rate` — duplicates-suppressed ratio
  - `inventory.idempotency.hit.rate` — REST-side cached vs fresh
  - `inventory.consumer.lag.seconds{topic}` — per consumer group
  - `inventory.low-stock.alerts.fired` — counter
  - `inventory.optimistic-lock.retry.count{operation}` — operational health signal

---

## Security

- All endpoints (except health/info) require JWT bearer token validated by
  `gateway-service` and forwarded as headers.
- Authorization in the application layer — not in controllers.
- Roles (v1 baseline):
  - `INVENTORY_READ` — GET endpoints
  - `INVENTORY_WRITE` — adjustments, transfers, reservations
  - `INVENTORY_ADMIN` — manual reservation release / damaged-bucket write-off
- Internal service-to-service calls (`outbound-service` reserving stock):
  use service-account JWT with scope `INVENTORY_RESERVE`. Documented in
  `platform/security-rules.md` once service-account flow is finalized.
- Refer to `.claude/skills/backend/jwt-auth/SKILL.md` for validation wiring.

No PII stored. Quantities and reasons are operational data only.

---

## Testing Requirements

Per `platform/testing-strategy.md`, `service-types/rest-api.md`,
`service-types/event-consumer.md`:

### Unit (Domain)
- Every invariant has a unit test (factory, reserve, confirm, release, adjust,
  transfer, sum-invariant)
- State-transition tests for reserve→confirm→released sequences
- Quantity arithmetic edge cases (underflow, overflow, zero, negative reasons)

### Application Service (port fakes)
- Happy path + every domain error per use-case
- Optimistic-lock retry behavior (port fake throws version conflict N times)
- Outbox row written in same TX as state change (verify via fake outbox port)
- Idempotency: repeated POST with same key returns identical response without
  re-applying mutation

### Persistence Adapter (Testcontainers Postgres)
- Every repo method against real Postgres
- Movement table append-only enforcement (DB role grants) — explicit test that
  UPDATE/DELETE on `inventory_movement` is rejected
- Optimistic-lock end-to-end: parallel update test with two threads

### REST Controllers (`@WebMvcTest`)
- Per controller, all endpoints in `specs/contracts/http/inventory-service-api.md`
- Idempotency-Key header behavior (first call applies, second returns cached)
- Validation rejection responses

### Consumers (`@SpringBootTest` with embedded Kafka or Testcontainers Kafka)
- Each consumer: happy path, redelivery (dedupe-hit), poison message (→ DLT)
- Ordering preservation within a partition
- Consumer survives broker restart (rebalance test)

### Contract Tests
- Every endpoint in `inventory-service-api.md` verified against impl
- Every published event matches schema in `inventory-events.md`
- Every consumed event schema validated (consumer-driven contract or schema registry)

### Failure-Mode Tests (per trait `transactional` Required Artifacts § 5)
- Same `Idempotency-Key` POST twice → identical result, mutation applied once
- Same `eventId` consumed twice → mutation applied once
- Transfer with target row version conflict → full rollback (source unchanged)
- Reservation race: 10 concurrent reserves on same row, sum constrained correctly

---

## Saga Participation (v1 light)

Inventory participates in the **Outbound Saga** (Order → Pick → Pack → Ship) as
a callee:

1. `outbound.picking.requested` consumed → `Reserve` (compensable by `Release`)
2. `outbound.picking.cancelled` consumed → `Release` (terminal)
3. `outbound.shipping.confirmed` consumed → `Confirm` (terminal; reserved → consumed)

The saga orchestration lives in `outbound-service`. `inventory-service` only
exposes the operations and emits the corresponding `.reserved` / `.released` /
`.confirmed` events for visibility. Compensation is **always** `Release` for any
reservation that is not yet confirmed.

Reservation TTL: every reservation carries an `expires_at`. A scheduled job
auto-releases reservations past TTL and emits `inventory.released` with
reason `EXPIRED`. Default TTL: 24 hours (configurable per warehouse).

Full saga document:
[`sagas/reservation-saga.md`](sagas/reservation-saga.md).

---

## Saga / Long-running Flow (ADR-MONO-005)

Per [ADR-MONO-005](../../../../../docs/adr/ADR-MONO-005-saga-timeout-escalation-dead-letter-policy.md) — **Category D reference implementation** for the monorepo.

| Flow | Category | Poll / Batch | Terminal | Metrics | Status |
|---|---|---|---|---|---|
| reservation TTL expiry (`RESERVED → RELEASED` after `expires_at`) | **D** (TTL expiry sweep, persistent `reservation` row) | `inventory.reservation.ttl-job.interval-ms=60000` · `inventory.reservation.ttl-job.batch-size=200` | `RELEASED` via `ReleaseReservationService.releaseExpired(...)` — each row in its own `@Transactional` boundary; one failure does not abort the batch; OL race retries next tick | `inventory.reservation.expiry.swept.total` counter (incremented by released count per tick — ADR § D5) | **Compliant** |

Reservation is also a **participant** in outbound-service's Category A saga (see "Saga Participation (v1 light)" above) — that saga's orchestration lives in outbound-service; inventory only exposes idempotent operations and emits visibility events.

---

## State Machines

### Reservation lifecycle

```
              [reserve]
                  |
                  v
              RESERVED ----[release]----> RELEASED (terminal)
                  |
              [confirm]
                  |
                  v
              CONFIRMED (terminal)
```

- `RESERVED` carries `expires_at`; expiry triggers automatic `release` (reason `EXPIRED`)
- `CONFIRMED` is terminal — quantity is now consumed; movement record retains forever

### Inventory row

The `Inventory` row itself has no enum status — its quantity buckets are the state.
A row is "alive" while any bucket > 0. Empty rows are kept (not deleted) for ~30
days to keep movement history joinable by row identity, then archived.

Diagram in:
[`state-machines/reservation-status.md`](state-machines/reservation-status.md).

---

## Extensibility Notes

Known evolution paths (not part of v1 — documented to guide v2 decisions):

- **Multi-warehouse transfer**: model as outbound-from-A + inbound-to-B saga in
  `outbound-service` orchestration. Inventory itself stays single-warehouse-only.
- **Serial-number tracking**: requires new aggregate `SerialNumberInventory` and a
  new SKU `tracking_type=SERIAL` value. Current row keying assumes batch quantities.
- **Lot allocation strategy (FEFO)**: v1 picking specifies the Lot from outside
  (outbound-service decides). v2 may push allocation logic here behind a strategy
  port.
- **Cycle counting / 실사**: scheduled audit comparison; produces `inventory.adjusted`
  with reason `CYCLE_COUNT`. Architecturally a special case of adjustment.
- **Damaged-bucket workflow**: v1 only allows `INVENTORY_ADMIN` manual write-off.
  v2 may introduce a damage-claim sub-flow with photographs / approvals.

---

## Open Items (Retrospective Backfill Audit)

> Originally framed as "Before First Implementation Task" prerequisites.
> `inventory-service` has been in production since the BE-030 series (Reserve /
> Release / Confirm domain + outbound saga consumers). This list is now a
> **retrospective backfill audit** — each item has a ✅ done / ⚠️ partial /
> ❌ outstanding status. Outstanding items are candidates for separate
> `TASK-BE-*` (project-internal) or `TASK-MONO-*` (shared paths). Audit
> conducted in TASK-BE-152 (2026-05-14).

1. ✅ [`domain-model.md`](domain-model.md) — entities, fields, relationships,
   invariants, state per entity (`Inventory`, `InventoryMovement`,
   `Reservation`, `StockAdjustment`, `StockTransfer`, infrastructure-supporting
   records). Authored in BE-030 era.
2. ✅ [`../../contracts/http/inventory-service-api.md`](../../contracts/http/inventory-service-api.md)
   — REST endpoints. Authored in BE-030 era.
3. ✅ [`../../contracts/events/inventory-events.md`](../../contracts/events/inventory-events.md)
   — published event schemas (`inventory.reserved` / `.released` / `.confirmed`
   + adjustments / transfers / movements). Authored in BE-030 era.
4. ✅ Consumer-side schema references for `inbound.*` and `outbound.*` events —
   cross-linked from `inventory-events.md` § Consumer References + the
   publishing services' contract files.
5. ✅ [`idempotency.md`](idempotency.md) — REST + event-dedupe strategy.
   Authored in BE-030 era.
6. ✅ [`sagas/reservation-saga.md`](sagas/reservation-saga.md) — reservation
   lifecycle and compensation (authored in TASK-BE-151, 2026-05-14).
7. ✅ [`state-machines/reservation-status.md`](state-machines/reservation-status.md)
   — reservation state machine diagram (authored in TASK-BE-151, 2026-05-14).
8. ❌ `external-integrations.md` — per `integration-heavy` Required Artifact 1
   (v1 zero-state declaration: no external vendors). **Outstanding** —
   candidate for separate `TASK-BE-*` (project-internal, sibling pattern =
   `outbound-service/external-integrations.md` reverse — declare zero vendor
   surface).
9. ⚠️ Error codes in `platform/error-handling.md` — **5 of 6 registered**:
   `RESERVATION_NOT_FOUND` (404), `RESERVATION_QUANTITY_MISMATCH` (422),
   `LOCATION_INACTIVE` (422), `SKU_INACTIVE` (422), `LOT_EXPIRED` (422).
   **`LOT_INACTIVE` outstanding** — candidate for separate `TASK-MONO-*`
   (shared path `platform/error-handling.md`, 1-line addition mirroring
   `SKU_INACTIVE` / `LOCATION_INACTIVE` row pattern).
10. ❌ Gateway route for `inventory-service` in `gateway-service`. **Outstanding
    — and a portfolio-wide gap, not inventory-specific**: as of 2026-05-14,
    `gateway-service/src/main/resources/application.yml` only routes
    `master-service` (`/api/v1/master/**`). Inbound / Outbound / Inventory /
    Notification / Admin services are not exposed via the Spring Cloud Gateway.
    Whether this is intentional (services accessed directly via Traefik
    hostname routing per TASK-MONO-024, bypassing Spring Cloud Gateway) or a
    routing-config gap should be resolved in a separate audit
    (`TASK-MONO-*` candidate) before adding individual routes here.

---

## References

- `CLAUDE.md` — workflow and rule priority
- `PROJECT.md` — domain/traits that activate rule layers
- `rules/domains/wms.md` — Inventory bounded context, W1, W2, W4, W5, W6
- `rules/traits/transactional.md` — T1–T8 (especially T2, T3, T4, T5, T8)
- `rules/traits/integration-heavy.md` — I3 retry, I5 DLQ, I8 internal model translation
- `platform/architecture.md` — system-level architecture
- `platform/architecture-decision-rule.md` — architecture declaration rules
- `platform/service-types/rest-api.md` — rest-api mandatory requirements
- `platform/service-types/event-consumer.md` — event-consumer mandatory requirements
- `specs/services/master-service/architecture.md` — sibling reference pattern
- `.claude/skills/backend/architecture/hexagonal/SKILL.md` — implementation patterns
