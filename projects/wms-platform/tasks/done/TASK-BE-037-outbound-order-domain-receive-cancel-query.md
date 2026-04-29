# TASK-BE-037 — outbound-service: Order domain, receive/cancel/query + saga consumers + real outbox

## Goal

Implement the **Order aggregate and core lifecycle** for `outbound-service`:

- `Order` + `OrderLine` domain model with full state machine
- `OutboundSaga` real transition methods (replace `UnsupportedOperationException` stubs)
- `ReceiveOrderUseCase` (manual REST entry + webhook inbox path)
- `CancelOrderUseCase`
- `QueryOrderUseCase` (by id + paginated list)
- JPA entities + persistence adapters for Order/OrderLine and OutboundSaga
- Real `OutboxWriterAdapter` + `EventEnvelopeSerializer` + `OutboxPublisher`
- `OutboundSagaCoordinator` real implementation
- Saga event consumers: `InventoryReservedConsumer`, `InventoryReleasedConsumer`, `InventoryConfirmedConsumer` (with `EventDedupePort`)
- REST: `OrderController` (POST create), `OrderQueryController` (GET by id, GET list)
- Schema alignment via V10 migration (fix gaps in V2 `outbound_order` vs. domain-model spec)
- Domain events: `outbound.order.received`, `outbound.order.cancelled`, `outbound.picking.requested`, `outbound.picking.cancelled`
- Unit tests for all of the above

## Scope

**Target service**: `outbound-service` (`projects/wms-platform/apps/outbound-service/`)

**In scope:**
- `domain/model/`: `Order`, `OrderLine`, real `OutboundSaga` transition methods
- `domain/event/`: `OrderReceived`, `OrderCancelled`, `PickingRequested`, `PickingCancelled`
- `application/port/in/`: `ReceiveOrderUseCase`, `CancelOrderUseCase`, `QueryOrderUseCase`
- `application/port/out/`: `OrderPersistencePort`, `SagaPersistencePort` (add to existing out-ports)
- `application/command/`: `ReceiveOrderCommand`, `CancelOrderCommand`, `OrderQueryCommand`
- `application/result/`: `OrderResult`, `OrderSummaryResult`
- `application/service/`: `ReceiveOrderService`, `CancelOrderService`, `OrderQueryService`
- `application/saga/OutboundSagaCoordinator`: replace stub with real implementation
- `adapter/in/messaging/consumer/`: `InventoryReservedConsumer`, `InventoryReleasedConsumer`, `InventoryConfirmedConsumer`
- `adapter/in/rest/controller/`: `OrderController`, `OrderQueryController`
- `adapter/in/rest/dto/request/`: `CreateOrderRequest`, `CreateOrderLineRequest`
- `adapter/in/rest/dto/response/`: `OrderResponse`, `OrderSummaryResponse`, `OrderLineResponse`
- `adapter/out/persistence/entity/`: `OrderEntity`, `OrderLineEntity` (complete with all domain-model.md fields)
- `adapter/out/persistence/repository/`: `OrderRepository`, `OrderLineRepository`
- `adapter/out/persistence/mapper/`: `OrderMapper`, `OrderSagaMapper`
- `adapter/out/persistence/adapter/`: `OrderPersistenceAdapter`, `SagaPersistenceAdapter` (complete)
- `adapter/out/event/outbox/`: `OutboxWriterAdapter` (real, replacing `StubOutboxWriterAdapter`)
- `adapter/out/event/publisher/`: `EventEnvelopeSerializer`, `OutboxPublisher` (@Scheduled)
- `adapter/in/webhook/erp/ErpOrderWebhookInboxProcessor`: wire in `ReceiveOrderUseCase` (replace APPLIED-only stub)
- `resources/db/migration/V10__order_schema_align.sql`: ADD COLUMN IF NOT EXISTS to fix V2 gaps

**Out of scope (→ TASK-BE-038):**
- `PickingRequest`, `PickingConfirmation`, `PackingUnit`, `Shipment` domain + JPA + adapters
- `ConfirmPickingUseCase`, `ConfirmPackingUseCase`, `ConfirmShippingUseCase`, `CreatePackingUnitUseCase`
- `PickingController`, `PackingController`, `ShippingController`
- Domain events: `outbound.picking.completed`, `outbound.packing.completed`, `outbound.shipping.confirmed`
- TMS notification wiring

## Acceptance Criteria

**AC-01** `Order` aggregate has all domain-model.md §1 fields and state machine methods (`startPicking`, `completePicking`, `startPacking`, `completePacking`, `confirmShipping`, `cancel`, `backorder`). `STATE_TRANSITION_INVALID` thrown for forbidden transitions.

**AC-02** `OrderLine` is immutable after `Order.startPicking()` is called. Invariant enforced at domain level.

**AC-03** `OutboundSaga.onInventoryReserved()`, `onInventoryReleased()`, `onInventoryConfirmed()`, `onReserveFailed()` no longer throw `UnsupportedOperationException`; they drive the saga state machine per `specs/services/outbound-service/state-machines/saga-status.md`.

**AC-04** `ReceiveOrderService` creates an `Order` (status=`RECEIVED`), calls `Order.startPicking()` (→ `PICKING`), creates `OutboundSaga` (status=`REQUESTED`), writes two outbox rows (`outbound.order.received`, `outbound.picking.requested`) in one `@Transactional` boundary. MasterReadModel validation performed: partner must be `ACTIVE + CUSTOMER/BOTH`; all SKUs must be `ACTIVE`.

**AC-05** `WebhookInboxProcessorService.processNextBatch()` calls `ReceiveOrderUseCase.receive()` per inbox row instead of just marking APPLIED.

**AC-06** `POST /api/v1/outbound/orders` returns `201` with `OrderResponse` including `sagaId`, `sagaState`, `status=PICKING`. Requires `Idempotency-Key` header and `OUTBOUND_WRITE` role.

**AC-07** `GET /api/v1/outbound/orders/{id}` returns `200` with full `OrderResponse`; `404 ORDER_NOT_FOUND` otherwise. Requires `OUTBOUND_READ` role.

**AC-08** `GET /api/v1/outbound/orders` returns paginated list with filters: `status`, `warehouseId`, `customerPartnerId`, `source`, `orderNo`, `requiredShipAfter/Before`, `createdAfter/Before`. Requires `OUTBOUND_READ` role.

**AC-09** `CancelOrderService` cancels the order for `RECEIVED/PICKING/PICKED/PACKING/PACKED` states; writes `outbound.order.cancelled` outbox row; if saga is `RESERVED` or later, also writes `outbound.picking.cancelled` to trigger inventory release. Requires `OUTBOUND_WRITE` (pre-pick) or `OUTBOUND_ADMIN` (post-pick). Forbidden on `SHIPPED` order → `422 ORDER_ALREADY_SHIPPED`.

**AC-10** `InventoryReservedConsumer` calls `OutboundSagaCoordinator.onInventoryReserved(sagaId)` which:
  - loads saga + order from DB
  - calls `OutboundSaga.onInventoryReserved()` → saga state `REQUESTED → RESERVED`
  - calls `Order.startPicking()` is already done at order creation; no second transition here
  - saves saga
  - writes `outbound.order updated` audit (no additional outbox event for this step)
  Each consumer wraps in `@Transactional` and uses `EventDedupePort` (Propagation.MANDATORY).

**AC-11** `InventoryReleasedConsumer` calls coordinator → saga transitions to `CANCELLED`; order transitions to `CANCELLED` (if not already).

**AC-12** `InventoryConfirmedConsumer` calls coordinator → saga transitions from `SHIPPED` to `COMPLETED`. (Shipment creation and `SHIPPED` order state are done in TASK-BE-038; this consumer only handles the final `COMPLETED` saga step.)

**AC-13** `OutboxPublisher` polls `outbound_outbox` for `status=PENDING` rows, publishes to Kafka, marks rows `PUBLISHED`. Exponential backoff on failure. Metrics: `outbound.outbox.pending` (gauge), `outbound.outbox.lag_seconds` (gauge), `outbound.outbox.publish_failures_total` (counter).

**AC-14** `EventEnvelopeSerializer` produces correct JSON envelope per `specs/contracts/events/outbound-events.md §Global Envelope` for all four events in scope.

**AC-15** V10 migration adds missing columns to `outbound_order` as `ADD COLUMN IF NOT EXISTS` (nullable for zero-downtime): `order_no VARCHAR(40)`, `source VARCHAR(20)`, `notes VARCHAR(1000)`, `customer_partner_id UUID`. The bootstrap column `erp_order_number` is retained (not dropped) for zero-downtime compatibility.

**AC-16** All new domain methods have unit tests. `ReceiveOrderService`, `CancelOrderService`, `OrderQueryService`, `OutboundSagaCoordinator`, all three consumers have unit tests using port fakes (no Testcontainers). Build passes (`./gradlew :apps:outbound-service:test`).

## Related Specs

- `projects/wms-platform/specs/services/outbound-service/architecture.md`
- `projects/wms-platform/specs/services/outbound-service/domain-model.md` §1 (Order), §6 (OutboundSaga)
- `projects/wms-platform/specs/services/outbound-service/sagas/outbound-saga.md`
- `projects/wms-platform/specs/services/outbound-service/state-machines/order-status.md`
- `projects/wms-platform/specs/services/outbound-service/state-machines/saga-status.md`
- `projects/wms-platform/specs/services/outbound-service/idempotency.md`
- `projects/wms-platform/specs/services/outbound-service/workflows/outbound-flow.md`

## Related Contracts

- `projects/wms-platform/specs/contracts/http/outbound-service-api.md` §1 (Order Lifecycle), §1.4 (Cancel Order)
- `projects/wms-platform/specs/contracts/events/outbound-events.md` §1 (order.received), §2 (order.cancelled), §3 (picking.requested), §4 (picking.cancelled)
- `projects/wms-platform/specs/contracts/events/inventory-events.md` §reservation.reserved, §reservation.released, §reservation.confirmed (consumed events for saga coordination)

## Edge Cases

- **Duplicate `orderNo`**: `DataIntegrityViolationException` on `order_no` unique constraint → map to `409 CONFLICT` in `GlobalExceptionHandler`.
- **ERP webhook duplicate**: `ErpOrderWebhookInboxProcessor` calls `ReceiveOrderUseCase`; if `orderNo` already exists (idempotent re-delivery), `CONFLICT` is swallowed and inbox row marked `APPLIED`.
- **MasterReadModel miss**: partner or SKU not in local snapshot → `PARTNER_INVALID_TYPE` (422) or `SKU_INACTIVE` (422). Do not call master-service synchronously.
- **Optimistic lock on saga update**: `@Version` on `outbound_saga.version`; coordinator retries once before failing (T5).
- **Order cancel after SHIPPED**: `ORDER_ALREADY_SHIPPED` (422). Checked in `Order.cancel()` domain method.
- **`InventoryReservedConsumer` arrives after cancellation**: saga already `CANCELLATION_REQUESTED` or `CANCELLED`; consumer must handle gracefully (idempotent skip via EventDedupePort).
- **OutboxPublisher Kafka timeout**: exponential backoff; row stays `PENDING`. Alert metric incremented.

## Failure Scenarios

- **`ReceiveOrderService` fails mid-TX**: full rollback (order + saga + outbox rows). Webhook inbox row stays `PENDING` for next batch.
- **`InventoryReservedConsumer` deduplication conflict**: duplicate `eventId` in `outbound_event_dedupe` → `DedupeResult.DUPLICATE` → consumer skips business logic, commits TX normally. No saga state change.
- **Outbox publish failure**: row stays `PENDING`; publisher retries with exponential backoff up to configurable max attempts; then marks `FAILED` and increments failure counter.
- **OutboundSaga version conflict during saga consumer**: `@Version` mismatch → `ObjectOptimisticLockingFailureException` → consumer retries message (at-least-once Kafka retry). `EventDedupePort` prevents double-application on replay.
