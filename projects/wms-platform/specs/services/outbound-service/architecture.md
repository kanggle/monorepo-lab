# outbound-service — Architecture

This document declares the internal architecture of `outbound-service`.
All implementation tasks targeting this service must follow this declaration
and `platform/architecture-decision-rule.md`.

---

## Identity

| Field | Value |
|---|---|
| Service name | `outbound-service` |
| Service Type | `rest-api` + `event-consumer` (dual; saga orchestrator) |
| Architecture Style | **Hexagonal (Ports & Adapters)** |
| Primary language / stack | Java 21, Spring Boot |
| Bounded Context | **Outbound** (per `rules/domains/wms.md`) |
| Deployable unit | `apps/outbound-service/` |
| Data store | PostgreSQL (owned, not shared) |
| Event publication | Kafka via outbox (per trait `transactional`, rule T3) |
| Event consumption | Kafka with eventId-based dedupe (per trait `transactional`, rule T8) |

### Service Type Composition

`outbound-service` combines `rest-api` (mutation + query) and `event-consumer`
(saga orchestration + master-data refresh). Read both
`platform/service-types/rest-api.md` and `platform/service-types/event-consumer.md`
when implementing — same documented exception as `inventory-service`.

---

## Responsibility

`outbound-service` owns the **outbound (shipping) workflow** end-to-end and
**orchestrates the picking saga** that spans `outbound-service` ↔ `inventory-service`:

- **Order** — customer-issued shipping order. Received via ERP webhook or manual
  ops entry.
- **Picking Request** — the outbound saga's first step. Reserves stock in
  `inventory-service` per order line (W4: reserve phase).
- **Picking Confirmation** — operator confirms physical pick from the assigned
  location.
- **Packing** — packed unit (box / pallet) recording.
- **Shipping Confirmation** — final step. Drives `inventory-service` to consume
  reserved stock (W5: shipped → consumed).
- **TMS handover** — outbound notification to TMS (Transportation Management
  System) of shipment ready.

It is the **single system of record** for order identity, picking instructions,
packing units, and shipping records. It does **not** own stock quantities.

---

## Out of Scope

`outbound-service` does NOT own:

- Inventory quantities (owned by `inventory-service`; outbound only reserves /
  releases / confirms via saga)
- ASN / inspection / putaway (owned by `inbound-service`)
- Master data identity (owned by `master-service`)
- Multi-warehouse picking (v1: single-warehouse orders only; cross-warehouse →
  v2 saga)
- Returns (RMA outbound) — different lifecycle, deferred to v2
- Carrier rating / TMS quote (handled by external TMS; we only push the shipment)

---

## Architecture Style: Hexagonal

### Rationale

- **Saga orchestration** is the most complex domain logic in WMS v1. Hexagonal
  forces saga state and compensation logic into the domain layer (a `Saga`
  aggregate) rather than scattering it across services / handlers.
- WMS traits (`transactional`, `integration-heavy`) demand sharp separation —
  TMS adapter, ERP webhook adapter, inventory-service stub adapter all live in
  outbound port implementations.
- Uniform with `master-service`, `inventory-service`, `inbound-service`.

### Trade-off Accepted

- Higher boilerplate cost (~2× over Layered) is **strongly justified** here.
  Order → Pick → Pack → Ship is a 4-step saga with compensations at each step;
  Hexagonal makes the saga aggregate testable in isolation against port fakes
  (no Kafka, no inventory-service running in tests).

### Package Structure

Follow `.claude/skills/backend/architecture/hexagonal/SKILL.md` exactly.

```
com.wms.outbound/
├── adapter/
│   ├── in/
│   │   ├── rest/
│   │   │   ├── controller/      # OrderController, PickingController, PackingController,
│   │   │   │                    #   ShippingController, OrderQueryController
│   │   │   └── dto/{request,response}/
│   │   ├── webhook/
│   │   │   └── erp/             # ErpOrderWebhookController + signature/replay validators
│   │   └── messaging/
│   │       └── consumer/        # InventoryReservedConsumer, InventoryReleasedConsumer,
│   │                            #   InventoryConfirmedConsumer (saga steps),
│   │                            #   MasterLocationConsumer, MasterSkuConsumer, MasterPartnerConsumer
│   └── out/
│       ├── persistence/
│       │   ├── entity/
│       │   ├── repository/
│       │   ├── mapper/
│       │   └── adapter/
│       ├── event/
│       │   ├── outbox/
│       │   └── publisher/
│       ├── tms/
│       │   └── adapter/         # TmsClientAdapter (HTTP) — implements ShipmentNotificationPort
│       └── masterref/
│           └── readmodel/
├── application/
│   ├── port/
│   │   ├── in/                  # ReceiveOrderUseCase, RequestPickingUseCase,
│   │   │                        #   ConfirmPickingUseCase, ConfirmPackingUseCase,
│   │   │                        #   ConfirmShippingUseCase, CancelOrderUseCase,
│   │   │                        #   QueryOrderUseCase
│   │   └── out/                 # OrderPersistencePort, PickingPersistencePort,
│   │                            #   PackingPersistencePort, ShipmentPersistencePort,
│   │                            #   SagaPersistencePort, OutboundEventPort,
│   │                            #   ShipmentNotificationPort, MasterReadModelPort,
│   │                            #   IdempotencyStorePort, EventDedupePort
│   ├── service/                 # Use-case implementations (@Service, @Transactional)
│   ├── saga/                    # OutboundSagaCoordinator — application-level orchestrator
│   ├── command/                 # Input records
│   └── result/                  # Output records
├── domain/
│   ├── model/                   # Pure POJOs: Order, OrderLine, PickingRequest, PickingConfirmation,
│   │                            #   PackingUnit, Shipment, OutboundSaga (saga-state aggregate)
│   ├── event/                   # OrderReceived, PickingRequested, PickingConfirmed,
│   │                            #   PackingCompleted, ShippingConfirmed, OrderCancelled
│   └── service/                 # Domain services (OrderStateMachine, SagaStateMachine,
│                                #   PickingPlanner, ShipmentBuilder)
└── config/
```

### Layer Rules

Same five rules as siblings. Specifics for outbound:

1. Domain layer hosts **two state machines**: `Order` lifecycle and `OutboundSaga`
   lifecycle. Both are aggregates with explicit transition methods.
2. Application layer contains a **SagaCoordinator** — orchestrates step transitions
   in response to events. Coordinator is **stateless**; saga state lives in DB
   under the `OutboundSaga` aggregate.
3. TMS adapter is purely outbound. Domain calls `ShipmentNotificationPort.notify(shipment)`
   without knowing the vendor.
4. ERP order-webhook reception identical pattern to `inbound-service` ASN webhook
   (signature + replay + inbox table).
5. Mappers package-private inside persistence adapter.

---

## Dependencies (Inbound)

| Caller | Contract | Purpose |
|---|---|---|
| `gateway-service` | `specs/contracts/http/outbound-service-api.md` | External UI — manual order entry, picking confirmation, packing/shipping |
| **External ERP** | `specs/contracts/webhooks/erp-order-webhook.md` | Push of new orders (signed webhook) |
| `admin-service` | `outbound-service-api.md` | Read-only KPI / dashboards |
| `master-service` | Events on `wms.master.*.v1` | Master snapshot refresh |
| `inventory-service` | Events on `wms.inventory.*.v1` | Saga step replies (reserved / released / confirmed) |

---

## Dependencies (Outbound)

v1 outbound dependencies:

- **PostgreSQL** — owned DB
- **Kafka** — event publication (via outbox), event consumption (consumer group
  `outbound-service`)
- **Redis** — idempotency-key store and event-dedupe cache
- **External TMS** — outbound HTTP `POST /shipments` to push shipment-ready
  notification. Per `integration-heavy` rule I1–I4, I7–I9 (timeout, circuit
  breaker, retry, vendor adapter, internal model translation, bulkhead pool).
- **External ERP** (acknowledgement of order receipt — optional in v1)

---

## Event Publication

All outbound state changes publish events via the **transactional outbox pattern**
(trait `transactional`, rule T3):

| Event | Topic | Trigger |
|---|---|---|
| `outbound.order.received` | `wms.outbound.order.received.v1` | New order created (webhook or manual) |
| `outbound.order.cancelled` | `wms.outbound.order.cancelled.v1` | Order cancelled before shipping |
| `outbound.picking.requested` | `wms.outbound.picking.requested.v1` | **Saga step 1**: triggers `inventory-service` reserve |
| `outbound.picking.cancelled` | `wms.outbound.picking.cancelled.v1` | **Compensation**: triggers `inventory-service` release |
| `outbound.picking.completed` | `wms.outbound.picking.completed.v1` | Operator confirmed all picks |
| `outbound.packing.completed` | `wms.outbound.packing.completed.v1` | Packing units finalised |
| `outbound.shipping.confirmed` | `wms.outbound.shipping.confirmed.v1` | **Saga step 4**: triggers `inventory-service` consume; TMS notified |

Full schemas: `specs/contracts/events/outbound-events.md` (Open Items).

> **Cross-service contracts** (jointly owned):
> - `wms.outbound.picking.requested.v1` is consumed by `inventory-service` to
>   reserve stock. Payload includes a `sagaId`.
> - `wms.outbound.picking.cancelled.v1` is consumed by `inventory-service` to
>   release a reservation. Compensation guarantee: this event is fired exactly
>   once per cancelled saga.
> - `wms.outbound.shipping.confirmed.v1` is consumed by `inventory-service` to
>   consume reserved stock. Once published, **the saga is terminal**.

---

## Event Consumption

| Subscribed Event | Source Topic | Effect |
|---|---|---|
| `inventory.reserved` | `wms.inventory.reserved.v1` | Saga step 1 success → advance to `RESERVED` |
| `inventory.released` | `wms.inventory.released.v1` | Compensation acknowledged → advance to `CANCELLED` |
| `inventory.confirmed` | `wms.inventory.confirmed.v1` | Saga step 4 success → advance to `COMPLETED` |
| `inventory.adjusted` (with reason `INSUFFICIENT_STOCK`) | `wms.inventory.adjusted.v1` | Out-of-stock signal during reserve → trigger compensation |
| `master.warehouse.*` | `wms.master.warehouse.v1` | Local read-model refresh |
| `master.zone.*` | `wms.master.zone.v1` | Same |
| `master.location.*` | `wms.master.location.v1` | Same |
| `master.sku.*` | `wms.master.sku.v1` | Same |
| `master.partner.*` | `wms.master.partner.v1` | Same; deactivated partners rejected as new order customer |

Same dedupe + DLQ pattern as siblings:

- `outbound_event_dedupe(event_id PK, ...)` — 30 days
- DLT topics, 3 retries, exponential backoff + jitter
- Partition key for inventory replies = `sagaId` (preserves order within saga)

---

## Outbound Saga (the heart of this service)

### Goal

Order fulfillment requires coordinated state changes across `outbound-service`
(order/picking/packing/shipping) and `inventory-service` (reserve/release/consume).
Per trait `transactional` rules T2 (no distributed TX) and T6 (compensation
required), this is modelled as a **choreographed saga** with `outbound-service`
as the **state-keeping orchestrator**.

### Saga Steps

```
[ORDER_RECEIVED]
       |
       v
   STEP_1: Request picking ──> emit outbound.picking.requested
       |                       inventory-service consumes, calls Reserve
       |
       v ◄── consume inventory.reserved
   RESERVED
       |
       v
   STEP_2: Operator picks (REST: confirmPicking)
       |
       v
   PICKING_CONFIRMED
       |
       v
   STEP_3: Operator packs (REST: confirmPacking)
       |
       v
   PACKING_CONFIRMED
       |
       v
   STEP_4: Operator confirms shipping ──> emit outbound.shipping.confirmed
       |                                  inventory-service consumes, calls Confirm
       |                                  TMS notified via outbound port
       |
       v ◄── consume inventory.confirmed
   COMPLETED (terminal)
```

### Compensation Paths

| Failure Point | Compensation |
|---|---|
| Reserve fails (insufficient stock event surfaced) | Saga → `RESERVE_FAILED` (terminal); order → `BACKORDERED` |
| Pre-pick cancellation (`CANCEL` REST call before pick) | Emit `outbound.picking.cancelled` → inventory releases → saga → `CANCELLED` |
| Pick-stage cancellation | Same as above |
| Post-pick / pre-ship cancellation | Same — pick-confirmed reservations are still releasable |
| Post-ship cancellation | **Forbidden in v1**. Returns is v2 (creates RMA inbound) |
| TMS notify fails | Retry per `integration-heavy` I3 (3 attempts, exp backoff, jitter); on exhaustion, saga stays `SHIPPED_NOT_NOTIFIED` and an alert fires. Stock is already consumed — TMS retry is the only recovery |

### Saga State Machine

```
        [request_picking]
              |
              v
          REQUESTED ──[reserve_failed]──> RESERVE_FAILED (terminal)
              |
        [reserved]
              |
              v
          RESERVED ──[cancel]──> CANCELLATION_REQUESTED ──[released]──> CANCELLED (terminal)
              |
       [confirm_picking]
              |
              v
       PICKING_CONFIRMED ──[cancel]──> CANCELLATION_REQUESTED ──[released]──> CANCELLED
              |
       [confirm_packing]
              |
              v
       PACKING_CONFIRMED ──[cancel]──> CANCELLATION_REQUESTED ──[released]──> CANCELLED
              |
       [confirm_shipping]
              |
              v
            SHIPPED ──[confirmed]──> COMPLETED (terminal)
                      ──[notify_tms_failed (after retries)]──> SHIPPED_NOT_NOTIFIED (alert)
```

Diagram in: `specs/services/outbound-service/state-machines/saga-status.md`
(Open Items).

Full saga document: `specs/services/outbound-service/sagas/outbound-saga.md`
(Open Items, per trait `transactional` Required Artifact 2).

### Saga Persistence

`outbound_saga` table:

| Column | Type | Notes |
|---|---|---|
| `saga_id` | UUID (PK) | |
| `order_id` | UUID | One saga per order |
| `state` | enum (above) | |
| `version` | Long | Optimistic lock |
| `started_at` | Instant | |
| `last_transition_at` | Instant | |
| `failure_reason` | String | Populated on `RESERVE_FAILED` / `SHIPPED_NOT_NOTIFIED` |

Saga events are persisted to outbox in the **same TX** as the state transition.

---

## Idempotency

### Synchronous (REST + webhook)

- `Idempotency-Key` on POST/PUT/PATCH/DELETE; Redis storage; TTL 24h.
- ERP order webhook: same dual-layer dedupe as inbound (`erp_webhook_dedupe` +
  domain idempotency).

### Asynchronous (Kafka)

- `outbound_event_dedupe(event_id PK, ...)` — 30 days.
- **Saga-level idempotency**: every saga transition is conditioned on current
  state. Re-delivered `inventory.reserved` for an already-`RESERVED` saga is a
  no-op (state machine rejects the transition silently as already-applied).

Full strategy: `specs/services/outbound-service/idempotency.md` (Open Items).

---

## Concurrency Control

### Optimistic Locking

`Order`, `PickingRequest`, `OutboundSaga` aggregates carry `version`. Conflicts
→ HTTP 409 `CONFLICT` (REST) or message reprocessing on next consumer poll
(Kafka).

### Saga Race Conditions

Two events for the same saga arriving close together (e.g., `inventory.reserved`
and a user-issued `CANCEL`):

- Both load saga at version V
- First commit succeeds at V+1
- Second commit retries; on retry, state machine rejects the now-invalid
  transition with `STATE_TRANSITION_INVALID`
- For Kafka path: message goes back to consumer for redelivery; on redelivery,
  state may have advanced — handler still uses the same idempotent transition
  function, which becomes a no-op

### Forbidden

- Pessimistic locks (per trait T5)
- Distributed transactions (per trait T2)
- `UPDATE outbound_saga SET state = ?` directly (per T4)

---

## Key Domain Invariants

Enforced at the domain layer; surfaced via codes from `rules/domains/wms.md` § Outbound:

| Invariant | Source | Error code |
|---|---|---|
| Cannot pick / pack / ship a non-existent order | wms.md | `ORDER_NOT_FOUND` |
| Cannot mutate a shipped or cancelled order | wms.md, T4 | `ORDER_ALREADY_SHIPPED` |
| Picking quantity per line ≤ order line quantity | wms.md | `PICKING_QUANTITY_EXCEEDED` |
| Picking source location must hold the SKU/Lot reserved (verified upstream by inventory) | derived | `RESERVATION_NOT_FOUND` (echoed) |
| Cannot pack incomplete pick (sum of pick-confirmed < order line) | wms.md | `PICKING_INCOMPLETE` |
| Cannot ship incomplete pack | wms.md | `PACKING_INCOMPLETE` |
| Order customer (partner) must be `ACTIVE` and of type `CUSTOMER` or `BOTH` | wms.md W6 | `PARTNER_INVALID_TYPE` |
| SKU must be `ACTIVE` (per local read-model) | wms.md W6 | `SKU_INACTIVE` |
| LOT-tracked SKU order line requires `lot_no` (or downstream FEFO assignment — v2) | wms.md | `LOT_REQUIRED` |
| Order cannot have lines from multiple warehouses (v1 single-warehouse) | derived | `WAREHOUSE_MISMATCH` |
| Saga state transitions follow defined machine | T4 | `STATE_TRANSITION_INVALID` |
| Cancellation forbidden after `SHIPPED` | derived | `ORDER_ALREADY_SHIPPED` |

---

## Outbound Workflow (compressed)

Full document at `specs/services/outbound-service/workflows/outbound-flow.md`
(Open Items, per `rules/domains/wms.md` Required Artifact 4).

```
1. Order Received          → outbox: outbound.order.received
   (webhook or manual)
   → starts OutboundSaga (state = ORDER_RECEIVED → REQUESTED)
                             outbox: outbound.picking.requested

2. inventory-service reserves stock
   → consume inventory.reserved
   → saga state RESERVED

3. Operator confirms picks  → outbox: outbound.picking.completed
   → saga state PICKING_CONFIRMED

4. Operator records packing → outbox: outbound.packing.completed
   → saga state PACKING_CONFIRMED

5. Operator confirms shipping → outbox: outbound.shipping.confirmed
   → TMS notified via outbound port
   → consume inventory.confirmed (eventual)
   → saga state COMPLETED
```

---

## TMS Integration (integration-heavy)

Outbound HTTP call to TMS via `ShipmentNotificationPort`:

- **Adapter**: `TmsClientAdapter` in `adapter/out/tms/`
- **Timeouts**: connect 5s, read 30s (I1)
- **Circuit breaker**: Resilience4j; threshold 50% over 20 calls, open for 60s (I2)
- **Retry**: 3 attempts, exponential backoff 1s/2s/4s + ±200ms jitter (I3)
- **Idempotency**: vendor's `Idempotency-Key` header populated with our `shipment_id`
  (I4); falls back to `tms_request_dedupe` table if vendor doesn't honour
- **Bulkhead**: dedicated thread pool + connection pool, separate from any other
  vendor (I9). Pool size starts at 10.
- **Internal model translation**: `Shipment` domain object → `TmsShipmentRequest`
  in adapter (I8). TMS response translated to `TmsAcknowledgement` internal type.
- **Failure handling**: after retry exhaustion, saga moves to `SHIPPED_NOT_NOTIFIED`
  and emits an alert. Stock is already consumed — re-notify is via a manual
  ops endpoint (`POST /shipments/{id}/retry-tms-notify`).

Full vendor catalog: `specs/services/outbound-service/external-integrations.md`
(Open Items).

---

## Webhook Reception (ERP Order Push)

Identical pattern to `inbound-service` ASN webhook:

- Signature (HMAC-SHA256 over body)
- Timestamp window ±5 min
- `erp_order_webhook_dedupe(event_id PK, received_at)`
- Webhook inbox table → background processor → domain
- `WEBHOOK_SIGNATURE_INVALID` / `WEBHOOK_REPLAY_DETECTED` /
  `WEBHOOK_TIMESTAMP_INVALID` errors as appropriate

Full contract: `specs/contracts/webhooks/erp-order-webhook.md` (Open Items).

---

## Persistence

- Database: PostgreSQL (one logical DB per service)
- Migrations: Flyway, `apps/outbound-service/src/main/resources/db/migration/`
- Outbox: `outbound_outbox`
- Event dedupe: `outbound_event_dedupe`
- Webhook inbox: `erp_order_webhook_inbox`
- Webhook dedupe: `erp_order_webhook_dedupe`
- Saga state: `outbound_saga`
- TMS request dedupe: `tms_request_dedupe(request_id PK, sent_at, response_snapshot)`

High-level table layout in `specs/services/outbound-service/domain-model.md`
(Open Items).

---

## Observability

- Metrics: standard REST + consumer + outbox lag (same set as siblings)
- **Saga-specific**:
  - `outbound.saga.active.count` — gauge of currently in-progress sagas
  - `outbound.saga.state.transitions{from,to}` — transition counter
  - `outbound.saga.completed.duration.seconds` — receipt-to-complete histogram
  - `outbound.saga.failed.count{reason=reserve_failed|tms_notify_failed}`
  - `outbound.saga.compensation.fired.count`
- **TMS-specific** (per integration-heavy):
  - `outbound.tms.request.count{result=success|timeout|5xx|circuit_open}`
  - `outbound.tms.request.duration.seconds` p50/p95/p99
  - `outbound.tms.circuit.state{vendor}` — gauge: 0=closed, 1=half-open, 2=open
  - `outbound.tms.retry.count{attempt}`
- **Webhook-specific**: same set as inbound

---

## Security

- Roles (v1):
  - `OUTBOUND_READ` — GET endpoints
  - `OUTBOUND_WRITE` — manual order, picking/packing confirmations, shipping confirmation
  - `OUTBOUND_ADMIN` — order cancellation, manual TMS retry, force-saga-fail
- ERP webhook anonymous (signature replaces JWT), dedicated route in gateway
- TMS API key per environment, loaded from Secret Manager (per
  `platform/security-rules.md`)
- Service-account token used by `inventory-service` to publish back; outbound
  trusts inventory's events as long as broker auth holds (no per-event signing
  internal)

No PII stored. Customer contact data inherited from master-service is operational.

---

## Testing Requirements

### Unit (Domain)
- Order state machine transitions (every allowed and disallowed)
- Saga state machine transitions (especially compensations)
- Picking / packing arithmetic invariants
- Shipment builder edge cases (multi-line orders, mixed SKUs)

### Application Service (port fakes)
- Happy path + every domain error per use-case
- Saga: full happy path, reserve-failed compensation, pre-pick cancellation,
  pre-ship cancellation
- Outbox row written in same TX as saga transition

### Persistence Adapter (Testcontainers Postgres)
- All repo methods
- Saga concurrency: parallel state-transition attempts → optimistic-lock conflict

### REST Controllers (`@WebMvcTest`)
- All endpoints in `outbound-service-api.md`
- Idempotency-Key behavior

### Webhook Controller (WireMock — I10)
- Same matrix as inbound

### Consumers (Testcontainers Kafka)
- Each saga-step consumer: happy path, redelivery (idempotent transition),
  poison → DLT
- Out-of-order events: `inventory.confirmed` arriving before saga is `SHIPPED`
  is silently dropped (impossible state — log + alert)

### TMS Adapter (WireMock — I10)
- Success → ack stored, saga → `COMPLETED`
- Timeout → 3 retries, then `SHIPPED_NOT_NOTIFIED`
- 5xx → 3 retries, same outcome
- 4xx → no retry, immediate `SHIPPED_NOT_NOTIFIED`
- Circuit open → fast-fail, same outcome
- Manual retry endpoint → success on second attempt → saga → `COMPLETED`

### Contract Tests
- All endpoints in `outbound-service-api.md`
- All published event schemas
- Webhook contract per `webhooks/erp-order-webhook.md`
- TMS request/response per `external-integrations.md`

### Failure-mode (per trait `transactional` Required Artifact 5)
- Same `Idempotency-Key` POST twice → identical result
- Same webhook event-id → single order created
- Reserve fails → saga lands in `RESERVE_FAILED`, order in `BACKORDERED`,
  no double compensation
- Pre-pick cancel → release event fired exactly once, idempotent on redelivery
- Saga restart from any non-terminal state via background sweeper (timeout-based
  re-emission of pending step)

---

## Saga Sweeper (recovery)

Background job runs every minute:

- Find sagas in `REQUESTED` for > 5 min → re-emit `outbound.picking.requested`
  (idempotent at inventory consumer)
- Find sagas in `CANCELLATION_REQUESTED` for > 5 min → re-emit
  `outbound.picking.cancelled`
- Find sagas in `SHIPPED` for > 5 min without `inventory.confirmed` → re-emit
  `outbound.shipping.confirmed`

This is the **failure-recovery loop** that makes the saga eventually consistent
even if a Kafka message is lost. Combined with consumer-side dedupe, re-emission
is safe.

---

## Extensibility Notes

- **Returns / RMA** — new flow, distinct lifecycle. Out of v1.
- **Multi-warehouse picking** — split-order saga. Out of v1.
- **FEFO auto-allocation** — currently order line specifies Lot or it's null
  (any lot). v2: outbound calls inventory's allocation endpoint to pick lots
  by FEFO.
- **Wave picking / batch picking** — group multiple orders into one operator
  pick run. v2: introduces `Wave` aggregate.
- **Carrier rating / quote** — TMS quote API. v2.

---

## Open Items (Before First Implementation Task)

These must be completed before any `TASK-BE-*` targeting `outbound-service` is
moved to `tasks/ready/`:

1. `specs/services/outbound-service/domain-model.md` — entities, fields,
   relationships, invariants, state per entity (Order, OrderLine, PickingRequest,
   PickingConfirmation, PackingUnit, Shipment, OutboundSaga)
2. `specs/contracts/http/outbound-service-api.md` — REST endpoints
3. `specs/contracts/webhooks/erp-order-webhook.md` — webhook contract
4. `specs/contracts/events/outbound-events.md` — published event schemas
5. `specs/services/outbound-service/idempotency.md` — REST + webhook + event
   + saga-level
6. `specs/services/outbound-service/external-integrations.md` — TMS + ERP
   catalog (timeouts, circuit, retry, secrets)
7. `specs/services/outbound-service/workflows/outbound-flow.md` — per
   `rules/domains/wms.md` Required Artifact 4
8. `specs/services/outbound-service/state-machines/order-status.md`
9. `specs/services/outbound-service/state-machines/saga-status.md`
10. `specs/services/outbound-service/sagas/outbound-saga.md` — saga document
    per trait `transactional` Required Artifact 2
11. Register new error codes in `platform/error-handling.md`:
    `STATE_TRANSITION_INVALID` (already global), `WAREHOUSE_MISMATCH` (shared
    with inbound), `PARTNER_INVALID_TYPE` (shared), `LOT_REQUIRED` (shared),
    `EXTERNAL_SERVICE_UNAVAILABLE` (already global per integration-heavy),
    `EXTERNAL_TIMEOUT` (already global)
12. Add a gateway route for `outbound-service` (REST) and a separate HMAC-only
    route for `/webhooks/erp/order` in `gateway-service`

---

## References

- `CLAUDE.md`, `PROJECT.md`
- `rules/domains/wms.md` — Outbound bounded context, W1, W2, W4, W5, W6
- `rules/traits/transactional.md` — T1–T8 (especially T2, T3, T4, T5, T6, T7, T8)
- `rules/traits/integration-heavy.md` — I1–I10 (TMS adapter)
- `platform/architecture-decision-rule.md`
- `platform/service-types/rest-api.md`
- `platform/service-types/event-consumer.md`
- `specs/services/master-service/architecture.md` — sibling reference
- `specs/services/inventory-service/architecture.md` — counterpart on saga steps
- `specs/services/inbound-service/architecture.md` — counterpart on webhook pattern
- `.claude/skills/backend/architecture/hexagonal/SKILL.md`
