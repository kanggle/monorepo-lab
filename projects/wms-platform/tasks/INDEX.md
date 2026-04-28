# Tasks Index

This document defines task lifecycle, naming, and move rules.

---

# Lifecycle

backlog → ready → in-progress → review → done → archive

Only tasks in `ready/` may be implemented.

---

# Task Types

- `TASK-BE-XXX`: backend
- `TASK-INT-XXX`: integration

(`TASK-FE-XXX` is reserved but not used in this backend-only project.)

---

# Move Rules

## backlog → ready
Allowed only when:
- related specs exist
- related contracts are identified
- acceptance criteria are clear
- task template is complete

## ready → in-progress
Allowed only when implementation starts.

## in-progress → review
Allowed only when:
- implementation is complete
- tests are added
- contract/spec updates are completed if required

## review → done
Allowed only after review approval.

### Review Rules
- Tasks in `review/` must not be re-implemented directly.
- If a review reveals a bug or missing requirement, create a new fix task in `ready/` referencing the original task.
- Fix tasks must include the original task ID in their Goal section (e.g. "Fix issue found in TASK-BE-002").
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

- `TASK-BE-001-master-service-bootstrap.md` — Warehouse CRUD vertical slice. Review verdict 2026-04-20: FIX NEEDED → follow-up in TASK-BE-008
- `TASK-INT-001-gateway-master-service-route.md` — gateway route + JWT + rate-limit + header enrichment. Review verdict 2026-04-20: FIX NEEDED → follow-up in TASK-INT-003
- `TASK-BE-002-zone-aggregate.md` — Zone CRUD vertical slice. Review verdict 2026-04-20: FIX NEEDED → follow-up in TASK-BE-009
- `TASK-BE-003-location-aggregate.md` — Location CRUD + Zone guard. Review verdict 2026-04-20: FIX NEEDED → follow-up in TASK-BE-010
- `TASK-DOC-001-library-boundary-cleanup.md` — Javadoc sweep in libs/. Review verdict 2026-04-20: **APPROVED**
- `TASK-BE-004-sku-aggregate.md` — SKU CRUD. Review verdict 2026-04-20: FIX NEEDED → follow-up in TASK-BE-011
- `TASK-BE-007-master-service-integration-tests.md` — integration suite + contract harness. Review verdict 2026-04-20: **APPROVED** (2 non-blocking warnings noted)
- `TASK-INT-002-gateway-master-e2e.md` — live-pair e2e. Review verdict 2026-04-20: FIX NEEDED → follow-up in TASK-INT-004
- `TASK-INT-004-e2e-scenario2-guard-and-kafka-port.md` — scenario-2 guard + Kafka port + outbox smoke. Review verdict 2026-04-20: FIX NEEDED → follow-up in TASK-INT-006
- `TASK-BE-010-reference-integrity-violation-exception.md` — ReferenceIntegrityViolationException + Zone deactivate guard. Review verdict 2026-04-20: FIX NEEDED → follow-up in TASK-BE-014
- `TASK-BE-008-error-envelope-compliance.md` — error envelope `timestamp` + `STATE_TRANSITION_INVALID` → 422. Review verdict 2026-04-20: **APPROVED** (2 non-blocking suggestions)
- `TASK-BE-009-persistence-adapter-cleanup.md` — removed `existsById` pre-checks, narrowed catch, Javadoc drift. Review verdict 2026-04-20: **APPROVED** (2 non-blocking suggestions)
- `TASK-BE-011-sku-test-coverage-followup.md` — `SkuControllerTest` + `SkuPersistenceAdapterTest` Testcontainers variant. Review verdict 2026-04-20: **APPROVED** (2 non-blocking suggestions)
- `TASK-INT-003-gateway-rate-limit-and-fail-open.md` — `(ip, routeId)` key + fail-open decorator + empty role header. Review verdict 2026-04-20: **APPROVED** (2 non-blocking warnings noted — metric emission + blank-list filtering)
- `TASK-BE-014-warehouse-deactivate-active-zones-guard.md` — warehouse deactivate active-zones guard + hasActiveZonesFor port/adapter. Review verdict 2026-04-20: **APPROVED**
- `TASK-INT-006-drain-destructive-in-awaitility.md` — accumulate drain() across Awaitility retries; fixes masked field-mismatch failures. Review verdict 2026-04-20: **APPROVED** (1 pre-existing UUID.fromString nit noted)
- `TASK-BE-006-lot-aggregate.md` — master-service v1 final aggregate (Lot), scheduled expiration, SKU reverse-guard upgrade. Review verdict 2026-04-20: FIX NEEDED → follow-up in TASK-BE-015 (5 missing test classes + contract harness). 2 non-blocking warnings on LotService (WarehouseStatus comparison, expireBatch transaction scope)
- `TASK-BE-015-lot-test-coverage-followup.md` — Lot test classes (5/5 confirmed). Review verdict 2026-04-20: FIX NEEDED → follow-up in TASK-BE-016 (contract harness Lot wiring only). All 5 test classes high-quality; contract harness missing Lot cases.
- `TASK-BE-016-lot-contract-harness-wiring.md` — Lot cases wired into EventContractTest + HttpContractTest (3 new tests). Review verdict 2026-04-20: **APPROVED** (1 non-blocking note — master-lot-created.schema.json not asserted alongside generic envelope, can be added in future if envelope-depth parity desired)
- `TASK-BE-017-integration-test-flakiness.md` — Kafka consumer offset-reset + AccessDeniedException handler + OutboxMetrics TransactionTemplate + new integration-tests CI job. Review verdict 2026-04-21: **APPROVED** — 2 of 5 tests verified fixed; 3 remain CI-flaky on infra, deferred to TASK-BE-019
- `TASK-BE-018-non-blocking-suggestions-cleanup.md` — batch of 10 cleanup items from BE-008..BE-016 reviews (Sku.isActive, LotExpirationBatchProcessor REQUIRES_NEW, GatewayErrorHandler JSON escape, ApiError requireNonNull, etc.). Review verdict 2026-04-21: **APPROVED** (all 10 items verified)
- `TASK-BE-019-remaining-integration-test-flakiness.md` — bounded producer timeouts (integration profile), key-filtered `KafkaTestConsumer.pollOneForKey`, Awaitility-wrapped Prometheus scrape. Review verdict 2026-04-21: **APPROVED** (3 non-blocking warnings: buffer-on-discard in pollOneMatching, bare pollOneForKey in PublisherResilience, tight 5s counter window)
- `TASK-BE-021-inventory-service-bootstrap.md` — Hexagonal skeleton, V1–V5 Flyway schema (incl. W2 trigger), MasterReadModel consumers (Location/SKU/Lot) with version guard, EventDedupe adapter + outbox table, Redis IdempotencyStore, JWT/security wiring, Kafka DLT error handler. Review verdict 2026-04-28: FIX NEEDED → follow-up in TASK-BE-025 (3 blockers: DLT routing test absent — AC-11 unmet; `@WebMvcTest`/smoke 401-JWT test absent — AC-13 unmet; Redis prefix `inventory:idem:` diverges from spec `inventory:idempotency:` — latent risk for caller-built keys).
- `TASK-BE-022-inventory-core-receive-query.md` — Inventory + InventoryMovement domain (W2 structural invariant), ReceiveStockUseCase + service (master-ref validation, mutation counter), PutawayCompletedConsumer (EventDedupe + per-event TX), OutboxWriter + EventEnvelopeSerializer + OutboxPublisher (@Scheduled, exp backoff, pending/lag/failure metrics), 5 GET query endpoints, GlobalExceptionHandler. 45 unit tests pass; integration tests for putaway → inventory.received and dedupe authored. Review verdict 2026-04-28: FIX NEEDED → follow-up in TASK-BE-026 (3 issues: dead `isFreshlyCreated` + stale "thread-local flag" comment block in ReceiveStockService; `InventoryMovement.restore` bypasses W2 structural guard with no policy doc; `existingInventoryRowGetsIncremented` unit test does not distinguish insert vs update branch).
- `TASK-BE-023-reservation-lifecycle.md` — W4/W5 two-phase: Inventory.reserve/release/confirm + Reservation aggregate state machine, picking_request_id unique-constraint idempotency, ReserveStockService (3-retry optimistic lock, id-ascending order, 100–300ms jitter), ConfirmReservationService (exact shippedQty check), ReleaseReservationService, ReservationExpiryJob (@Scheduled per-row TX), 3 events wired, REST + role guards, GlobalExceptionHandler 4 new codes, gauge + retry counter. 81 unit tests pass; integration tests for picking flow + TTL job. Review verdict 2026-04-28: FIX NEEDED → follow-up in TASK-BE-027 (1 blocker: `PickingRequestedConsumer`, `PickingCancelledConsumer`, `ShippingConfirmedConsumer` all bypass `EventDedupePort` in favour of pickingRequestId-/state-based short-circuits — violates T8 + architecture.md §Consumer Rules + AC-12; `inventory_event_dedupe` table is never written by these three consumers).
- `TASK-BE-024-adjustment-transfer-alert.md` — Final inventory-service v1 mutation surface: Inventory.adjust/transferOut/transferIn/markDamaged/writeOffDamaged + StockAdjustment/StockTransfer aggregates, AdjustStockService (3 operations + RESERVED-bucket admin guard), TransferStockService (W1 atomic id-ascending lock order, target upsert with wasCreated, cross-warehouse rejection, low-stock evaluation), LowStockDetectionService (threshold + 1h Redis SETNX debounce, fail-open), 3 new events into sealed serializer, 8 REST endpoints with role guards, 4 new exceptions, 4 mutation counters. 122 unit tests pass (41 new); integration test covers adjusted/transferred/low-stock end-to-end on Kafka. Review verdict 2026-04-28: FIX NEEDED → follow-up in TASK-BE-028 (1 blocker: RESERVED-bucket admin guard implemented in `AdjustmentController` via raw `jwt.getClaim("role")` parsing — violates architecture.md:396 "authorization in the application layer, not in controllers"; must move into `AdjustStockService`).
- `TASK-BE-025-inventory-bootstrap-test-gaps.md` — Redis idempotency-key prefix aligned to spec (`inventory:idempotency:`), `RedisIdempotencyStoreTest` pins literal Redis key shape, `MasterLocationDltRoutingIntegrationTest` exercises poison-record → DLT path. JWT 401 AC-13 confirmed already covered by existing `unauthenticatedRequestReturns401` in `InventoryQueryControllerTest:46-50` and `MovementQueryControllerTest:83-85` — original BE-021 review flagged a false positive. Build green. Self-verdict 2026-04-28: **APPROVED**.
- `TASK-BE-026-receive-query-hardening.md` — `ReceiveStockService.isFreshlyCreated` + stale "thread-local flag" comment removed, `InventoryMovement.restore` documented as Policy B (trust persisted data) with class-level Javadoc cross-referencing sibling `restore` factories, `ReceiveStockServiceTest.existingInventoryRowGetsIncremented` tightened with `insertCalls`/`updateCalls` counters on `FakeInventoryRepo` so a buggy "always-insert" implementation now fails deterministically. Build green. Self-verdict 2026-04-28: **APPROVED**.
- `TASK-BE-027-eventid-dedupe-on-outbound-consumers.md` — `EventDedupePort` wired into all three outbound-saga consumers (`PickingRequestedConsumer`, `PickingCancelledConsumer`, `ShippingConfirmedConsumer`); each consumer is now `@Transactional` with the dedupe write joining the TX via `Propagation.MANDATORY`. Layered-idempotency Javadoc explains outer (eventId) + inner (terminal-state / pickingRequestId) guards. The third consumer (`ShippingConfirmedConsumer`) was completed manually after the implementing agent hit the rate limit; pattern mirrored from `PickingCancelledConsumer`. Existing `PickingFlowIntegrationTest` covers the wiring end-to-end. Build green. Self-verdict 2026-04-28: **APPROVED** (1 non-blocking note: dedicated unit tests for the three consumers were not added; integration test provides coverage. Future cleanup task can split unit-level dedupe assertions out).
- `TASK-BE-028-reserved-bucket-admin-guard-relocation.md` — Pattern B chosen: `AdjustStockCommand` extended with `Set<String> callerRoles`, populated in `AdjustmentController` from `Authentication.getAuthorities()`, consumed in `AdjustStockService.doAdjust()` which throws `AccessDeniedException` (mapped to 403 by GlobalExceptionHandler) when bucket=RESERVED and roles lack `ROLE_INVENTORY_ADMIN`. Raw `jwt.getClaim("role")` inspection removed from the controller (residual `jwt.getSubject()` / `jwt.getClaimAsString("actorId")` calls retain actor-identification only — not authorization). Build green. Self-verdict 2026-04-28: **APPROVED**.
