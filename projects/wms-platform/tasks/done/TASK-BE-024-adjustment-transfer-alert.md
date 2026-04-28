# Task ID

TASK-BE-024

# Title

Stock adjustment, transfer, damaged-bucket, and low-stock alert

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

Complete the `inventory-service` REST mutation surface with adjustment, transfer,
and damaged-bucket operations, and add the low-stock alert mechanism.

After this task:

- `POST /adjustments`, `/{id}/mark-damaged`, `/{id}/write-off-damaged` are fully implemented
- `POST /transfers` atomically moves stock between two locations in one warehouse (W1)
- `inventory.adjusted` and `inventory.transferred` events published via outbox
- `inventory.low-stock-detected` alert published on `wms.inventory.alert.v1` when any
  mutation drives `available_qty` below the configured threshold
- Full integration test suite covering all mutation paths is in place

Depends on TASK-BE-021 (bootstrap), TASK-BE-022 (Inventory domain, OutboxPublisher),
TASK-BE-023 (Reservation lifecycle; all Inventory domain methods now exist).

---

# Scope

## In Scope

- `Inventory` domain methods added in this task:
  - `adjust(delta, reasonCode, bucket)` — signed delta on one bucket
  - `transferOut(qty)` — `available -= qty`
  - `transferIn(qty)` — `available += qty`
  - `markDamaged(qty)` — `available -= qty`, `damaged += qty`
  - `writeOffDamaged(qty, reason)` — `damaged -= qty`
- `StockAdjustment` domain aggregate: fields per `domain-model.md §4`; immutable post-create;
  reclassify pair logic
- `StockTransfer` domain aggregate: fields per `domain-model.md §5`; W1 two-row atomicity;
  deadlock-safe load order
- `AdjustStockUseCase`, `TransferStockUseCase` (in-ports + implementations)
- `LowStockDetectionService` (domain service): called after any mutation that may reduce
  `available_qty`; checks against configured threshold; publishes `inventory.low-stock-detected`
  via outbox when threshold is crossed (once per crossing — debounce logic)
- REST controllers and DTOs:
  - `POST /api/v1/inventory/adjustments` — Auth: `INVENTORY_WRITE` (AVAILABLE/DAMAGED bucket);
    `INVENTORY_ADMIN` required for RESERVED bucket adjustment
  - `POST /api/v1/inventory/{inventoryId}/mark-damaged` — Auth: `INVENTORY_WRITE`
  - `POST /api/v1/inventory/{inventoryId}/write-off-damaged` — Auth: `INVENTORY_ADMIN`
  - `GET /api/v1/inventory/adjustments/{id}` — Auth: `INVENTORY_READ`
  - `GET /api/v1/inventory/{inventoryId}/adjustments` — Auth: `INVENTORY_READ`
  - `GET /api/v1/inventory/adjustments` — Auth: `INVENTORY_READ`
  - `POST /api/v1/inventory/transfers` — Auth: `INVENTORY_WRITE`
  - `GET /api/v1/inventory/transfers/{id}` — Auth: `INVENTORY_READ`
  - `GET /api/v1/inventory/transfers` — Auth: `INVENTORY_READ`
- Idempotency-Key handling on all POST endpoints (Redis)
- Published events: `inventory.adjusted` (`wms.inventory.adjusted.v1`),
  `inventory.transferred` (`wms.inventory.transferred.v1`),
  `inventory.low-stock-detected` (`wms.inventory.alert.v1`)
- `inventory.mutation.count{operation}` counter for ADJUST, TRANSFER, MARK_DAMAGED, WRITE_OFF_DAMAGED
- Full integration test suite covering all mutation paths end-to-end

## Out of Scope

- Bulk adjustment endpoint (v2)
- Cross-warehouse transfer (v2 — returns `VALIDATION_ERROR` in v1)
- Cycle-count scheduling (v2)
- Damaged-bucket approval workflow (v2)
- Low-stock threshold configuration endpoint (read from `admin.settings` via read-model; threshold
  configuration API is admin-service responsibility, not inventory-service)

---

# Acceptance Criteria

- [ ] `Inventory.adjust(delta, reasonCode, bucket)`: resulting `bucket >= 0` → applies; would go negative → `INSUFFICIENT_STOCK`; writes one Movement row (or two for reclassify pair)
- [ ] `Inventory.markDamaged(qty)`: `available >= qty` → `available -= qty`, `damaged += qty`; two Movement rows (AVAILABLE -N, DAMAGED +N)
- [ ] `Inventory.writeOffDamaged(qty, reason)`: `damaged >= qty` → `damaged -= qty`; `INVENTORY_ADMIN` role required; `INSUFFICIENT_STOCK` if `damaged < qty`
- [ ] `StockTransfer` atomicity (W1): both Inventory rows (source + target) updated in one `@Transactional`; if source has insufficient available → full rollback; target row created if absent
- [ ] `source_location_id == target_location_id` → `TRANSFER_SAME_LOCATION` (422)
- [ ] Cross-warehouse transfer → `VALIDATION_ERROR` (400): "Cross-warehouse transfers not supported in v1"
- [ ] Source and target rows loaded in deterministic order (`location_id ASC`) — deadlock prevention
- [ ] `POST /adjustments` missing `reasonNote` → `400 ADJUSTMENT_REASON_REQUIRED`; `delta = 0` → `400 VALIDATION_ERROR`
- [ ] `POST /adjustments` with `bucket=RESERVED` and `INVENTORY_WRITE` role (not ADMIN) → `403 FORBIDDEN`
- [ ] `POST /adjustments` success: `StockAdjustment` row + `InventoryMovement` row + `InventoryOutbox` row all written in one TX; `inventory.adjusted` emitted
- [ ] `POST /{id}/mark-damaged` success: `StockAdjustment` row + two Movement rows (DAMAGE_MARK) + `InventoryOutbox`; `inventory.adjusted` emitted with `movementType=DAMAGE_MARK`
- [ ] `POST /{id}/write-off-damaged` requires `INVENTORY_ADMIN`; `403` for `INVENTORY_WRITE`; success path as above with `movementType=DAMAGE_WRITE_OFF`
- [ ] `POST /transfers` success: two-row atomic update; two Movement rows (TRANSFER_OUT + TRANSFER_IN); `inventory.transferred` emitted
- [ ] `inventory.low-stock-detected` fired when `available_qty < threshold` after a mutation; fires at most once per crossing; `actorId` and `triggeringEventType`/`triggeringEventId` populated in payload
- [ ] `inventory.mutation.count{operation=ADJUST}` etc. counters increment correctly
- [ ] All query endpoints for adjustments and transfers return correct data with pagination
- [ ] All adjustment query endpoints: `GET /adjustments/{id}` 200/404; `GET /{id}/adjustments` paginated; `GET /adjustments` with `occurredAfter` required when no `inventoryId`
- [ ] All tests pass; integration test covers full path for at least one adjustment and one transfer

---

# Related Specs

> **Before reading Related Specs**: Follow `platform/entrypoint.md` Step 0 — read `PROJECT.md`, then load `rules/common.md` plus `rules/domains/wms.md`, `rules/traits/transactional.md`, `rules/traits/integration-heavy.md`.

- `platform/testing-strategy.md`
- `platform/service-types/rest-api.md`
- `platform/error-handling.md`
- `specs/services/inventory-service/architecture.md` — §Key Domain Invariants, §Concurrency Control (Transfer Atomicity W1), §Observability
- `specs/services/inventory-service/domain-model.md` — §1 Inventory (adjust/transfer/damage methods), §4 StockAdjustment, §5 StockTransfer, §2 InventoryMovement (reason_code catalog)
- `specs/services/inventory-service/idempotency.md` — §1 REST Idempotency

# Related Skills

- `.claude/skills/backend/architecture/hexagonal/SKILL.md`
- `.claude/skills/backend/transaction-handling/SKILL.md`
- `.claude/skills/backend/springboot-api/SKILL.md`
- `.claude/skills/backend/testing-backend/SKILL.md`
- `.claude/skills/messaging/outbox-pattern/SKILL.md`
- `.claude/skills/testing/testcontainers/SKILL.md`

---

# Related Contracts

- `specs/contracts/http/inventory-service-api.md` — §2 Stock Adjustments, §3 Stock Transfers
- `specs/contracts/events/inventory-events.md` — §2 `inventory.adjusted`, §3 `inventory.transferred`,
  §7 `inventory.low-stock-detected` (all published)

---

# Target Service

- `inventory-service`

---

# Architecture

Follow:

- `specs/services/inventory-service/architecture.md` (Hexagonal, rest-api surface)

---

# Implementation Notes

- **Transfer deadlock prevention**: load source and target Inventory rows via
  `inventoryRepository.findAllByIdInForUpdate(List.of(id1, id2).stream().sorted().toList())`.
  Sorting by id (or `location_id`) ensures consistent acquisition order across concurrent
  reciprocal transfers.
- **Transfer target upsert**: if no Inventory row exists at `(targetLocationId, skuId, lotId)`,
  create one with `available_qty = quantity, version = 0` in the same TX. Use `saveAndFlush`
  to catch unique-constraint violations early (concurrent transfer to same new location).
- **Low-stock threshold**: read from `MasterReadModelPort` — v1 uses a flat threshold stored in
  `admin.settings` key `inventory.low-stock-threshold.{warehouseId}.{skuId}` (or global
  `inventory.low-stock-threshold.default`). If the setting is absent, low-stock detection is
  disabled (no alert). The `admin-service` publishes `admin.settings.changed` events; inventory
  does not subscribe in v1 — it reads settings on demand (acceptable for alert latency).
  Implement `LowStockThresholdPort` as a simple in-memory cache refreshed on a 5-minute TTL
  (not via event consumption — keeps this task bounded). Document this simplification.
- **Low-stock debounce**: maintain a Redis key `inventory:low-stock-alert:{inventoryId}` with
  TTL 1h. Fire the alert only if the key is absent. Set the key on fire. Key expires naturally
  → if stock drops again, alert fires again. This prevents alert storms during rapid succession
  mutations.
- **Adjustment `reasonNote` length check**: ≥ 3 characters after `trim()`. Implement in domain
  factory, not in controller validation layer.
- **Reclassify pair**: v1 models them as two separate POST /adjustments calls. Each call creates
  an independent `StockAdjustment` row. The `reasonNote` convention (reference the peer's intended
  id) is documentation only; no DB foreign key between adjustment rows.
- **`movementType` in `inventory.adjusted` payload**: set based on which domain method was called:
  `ADJUSTMENT` for `/adjustments`, `DAMAGE_MARK` for `/mark-damaged`, `DAMAGE_WRITE_OFF` for
  `/write-off-damaged`.

---

# Edge Cases

- `adjust(delta=0)` — `VALIDATION_ERROR` (delta ≠ 0 is a domain invariant)
- `adjust(delta=-100)` on an `AVAILABLE` bucket with `available_qty=50` — `INSUFFICIENT_STOCK` (422)
- Concurrent transfers: Thread A transfers 80 from Location X; Thread B transfers 60 from same Location X; only one succeeds (or both succeed if total available >= 140); optimistic lock protects consistency
- Transfer to a brand-new location not yet in `MasterReadModel` — `LOCATION_NOT_FOUND` in read-model → `VALIDATION_ERROR`; caller must ensure the Location was created in master-service first
- `markDamaged` on a row whose `damaged` bucket is at a high value — valid; `damaged += qty`; no upper bound in v1
- `writeOffDamaged` with `quantity > damaged_qty` — `INSUFFICIENT_STOCK` (422)
- Low-stock alert fires during transfer (source location drops below threshold) — alert fired with `triggeringEventType=inventory.transferred`
- Transfer where source and target are in different warehouses — `VALIDATION_ERROR`: look up both `warehouse_id`s from `MasterReadModel`; if they differ, reject

---

# Failure Scenarios

- **Source Inventory row version conflict during transfer** — full rollback (W1); `CONFLICT` (409) returned; caller retries with fresh state
- **Target upsert constraint violation** (concurrent transfer to same new location) — retry the upsert; if second attempt also fails, propagate `CONFLICT`
- **Redis unavailable for low-stock debounce** — fail open: fire the alert without debounce (duplicate alerts possible); do not block the mutation. Rationale: mutation correctness > alert deduplication
- **Redis unavailable for idempotency** — fail closed: return `503`; mutation not applied
- **Outbox publisher down** — adjustment/transfer committed; `inventory.adjusted`/`inventory.transferred` queued in outbox; published when publisher recovers; downstream consumers dedupe

---

# Test Requirements

## Unit Tests

- `Inventory.adjust`: positive delta increases bucket; negative delta with sufficient amount decreases; negative delta exceeds → `INSUFFICIENT_STOCK`; delta=0 → `VALIDATION_ERROR`
- `Inventory.markDamaged`: sufficient available → correct bucket pair; insufficient → `INSUFFICIENT_STOCK`
- `Inventory.writeOffDamaged`: sufficient damaged → decrements; insufficient → `INSUFFICIENT_STOCK`
- `AdjustStockService` with port fakes: `reasonNote` missing → `ADJUSTMENT_REASON_REQUIRED`; success writes adjustment + movement + outbox
- `TransferStockService` with port fakes: same-location → `TRANSFER_SAME_LOCATION`; source insufficient → `INSUFFICIENT_STOCK` + rollback; success writes two inventory rows + two movements + one outbox
- `LowStockDetectionService`: threshold crossed → outbox row written; threshold not crossed → no outbox; key already set (debounce) → no outbox

## REST Tests (`@WebMvcTest`)

- `POST /adjustments`: missing `Idempotency-Key` → 400; missing `reasonNote` → 400; `bucket=RESERVED` with `INVENTORY_WRITE` role → 403; success → 201
- `POST /{id}/mark-damaged`: success → 200; insufficient → 422; `INVENTORY_WRITE` role OK
- `POST /{id}/write-off-damaged`: `INVENTORY_WRITE` role → 403; `INVENTORY_ADMIN` → 200
- `POST /transfers`: same location → 422; cross-warehouse → 400; success → 201
- Query endpoints: GET /adjustments/{id} 200/404; GET /{id}/adjustments paginated; GET /adjustments missing `occurredAfter` without `inventoryId` → 400

## Failure-Mode Tests

- Same `Idempotency-Key` POST /adjustments twice → identical 201; mutation applied once; single adjustment row in DB
- Same `Idempotency-Key` POST /transfers twice → identical 201; single transfer + two movement rows
- Transfer source version conflict → `CONFLICT` (409); source row unchanged

## Integration Test (`@SpringBootTest`, Testcontainers)

- Full path: `POST /adjustments` → `inventory.adjusted` on `wms.inventory.adjusted.v1`
- Full path: `POST /transfers` → `inventory.transferred` on `wms.inventory.transferred.v1`
- Low-stock path: `POST /adjustments` drives `available_qty` below threshold → `inventory.low-stock-detected` on `wms.inventory.alert.v1`
- Repeat: same mutation again (threshold already crossed, debounce active) → no second alert within 1h

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added and passing
- [ ] `inventory-events.md §2, §3, §7` shapes verified in event contract tests
- [ ] `inventory-service-api.md §2 + §3` endpoints verified in REST tests
- [ ] W1 atomicity for transfer verified in failure-mode test (rollback test)
- [ ] `INVENTORY_ADMIN` role guard on write-off and RESERVED-bucket adjustment verified in REST test
- [ ] Ready for review
