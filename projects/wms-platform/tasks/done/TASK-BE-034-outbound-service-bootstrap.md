# TASK-BE-034 — Bootstrap outbound-service with Hexagonal skeleton, Flyway schema, MasterReadModel consumers, ERP webhook ingest, and TMS/Saga stubs

| Field | Value |
|---|---|
| **Task ID** | TASK-BE-034 |
| **Title** | outbound-service: Hexagonal skeleton, Flyway schema, MasterReadModel consumers, ERP order webhook ingest, TMS port stub, Saga infrastructure stubs |
| **Status** | review |
| **Owner** | backend |
| **Tags** | outbound, bootstrap, event-consumer, webhook, saga, test |

---

## Goal

Stand up the `outbound-service` Spring Boot application skeleton following the
declared Hexagonal architecture, and deliver the infrastructure foundation:

- Flyway-managed DB schema for all tables declared in `domain-model.md` (V1–V8)
- MasterReadModel consumers (`MasterWarehouseConsumer`, `MasterZoneConsumer`,
  `MasterLocationConsumer`, `MasterSkuConsumer`, `MasterLotConsumer`,
  `MasterPartnerConsumer`) that receive `master.*` events and upsert
  corresponding snapshots
- ERP order webhook **ingest path**: HMAC-SHA256 signature/timestamp/dedupe
  verification + `erp_order_webhook_inbox` write + 200 ack — no domain
  processing yet (the background processor stub marks rows `APPLIED` without
  creating an Order; full domain handling lands in TASK-BE-035)
- Redis idempotency store, Kafka consumer group `outbound-service`, PostgreSQL
  wired and healthy
- `EventDedupePort` + `EventDedupePersistenceAdapter` (`Propagation.MANDATORY`)
  — shared by all future Kafka consumers including saga-step consumers
- `OutboundSaga` JPA entity + stub table — saga-step consumers land in
  TASK-BE-036 (post-domain); table shape and aggregate model exist here so no
  migration is needed later
- `ShipmentNotificationPort` stub implementation that logs and records
  `tms_status = NOTIFIED` immediately (real Resilience4j TMS adapter lands in
  TASK-BE-037 alongside ConfirmShipping use-case)
- `OutboundSagaCoordinator` stub — application-layer class exists with empty
  method stubs for saga event handlers (implementations land in TASK-BE-036)
- `OutboundOutbox` table + `OutboxWriter` port stub (real publisher in
  TASK-BE-035)
- JWT resource server config, role guards, security config

On completion `outbound-service` boots, subscribes to `master.*` topics,
maintains a local read-model of all 6 master entities, accepts ERP order
webhooks (200 + inbox row), and is ready for order domain implementation
in TASK-BE-035.

No Order / PickingRequest / PickingConfirmation / PackingUnit / Shipment
domain logic is in scope. Tables exist; domain code lands in subsequent tasks.

---

## Scope

### In Scope

- Gradle module `apps/outbound-service/` (Spring Boot 3.x, Java 21)
- Shared libs: `java-common`, `java-web`, `java-observability`, `java-security`,
  `java-messaging`
- Hexagonal package layout per `architecture.md` § Package Structure:
  `adapter/in/{rest/controller,webhook/erp,messaging/consumer}`,
  `adapter/out/{persistence/{entity,repository,mapper,adapter},event/{outbox,publisher},tms/adapter,masterref/readmodel}`,
  `application/{port/{in,out},service,saga,command,result}`,
  `domain/{model,event,service}`, `config/`
- **Flyway migrations** (all tables from `domain-model.md` created up front):
  - `V1__init_master_readmodel.sql` —
    `warehouse_snapshot`, `zone_snapshot`, `location_snapshot`,
    `sku_snapshot`, `lot_snapshot`, `partner_snapshot`
  - `V2__init_order_tables.sql` — `outbound_order`, `outbound_order_line`
    (columns per `domain-model.md` §1; no domain code uses them yet)
  - `V3__init_picking_tables.sql` —
    `picking_request`, `picking_request_line`,
    `picking_confirmation`, `picking_confirmation_line`
  - `V4__init_packing_shipping_tables.sql` —
    `packing_unit`, `packing_unit_line`,
    `shipment`, `tms_request_dedupe`
  - `V5__init_saga_table.sql` — `outbound_saga`
  - `V6__init_outbox_dedupe.sql` — `outbound_outbox`, `outbound_event_dedupe`
  - `V7__init_webhook_inbox.sql` —
    `erp_order_webhook_inbox`, `erp_order_webhook_dedupe`
  - `V8__role_grants.sql` — `REVOKE UPDATE, DELETE ON outbound_outbox,
    outbound_event_dedupe, erp_order_webhook_dedupe, tms_request_dedupe FROM
    <app_role>` (W2 append-only invariant for ledger/dedupe tables)
- **`EventDedupePort`** (out-port) + `EventDedupePersistenceAdapter` —
  `Propagation.MANDATORY`; same contract as inventory-service / inbound-service
  siblings; mirrors TASK-BE-027 pattern
- **6 master snapshot consumers** — `MasterWarehouseConsumer`,
  `MasterZoneConsumer`, `MasterLocationConsumer`, `MasterSkuConsumer`,
  `MasterLotConsumer`, `MasterPartnerConsumer`. Each:
  - Consumes matching `wms.master.*.v1` topic
  - Calls `EventDedupePort.process(eventId, eventType, () -> upsert(...))`
  - Upserts via `ON CONFLICT DO UPDATE WHERE snapshot.master_version <
    EXCLUDED.master_version`
  - Maps `*.deactivated` → `status=INACTIVE`, `*.reactivated` → `status=ACTIVE`,
    `master.lot.expired` → `status=EXPIRED`
- **`MasterReadModelPort`** (out-port) + `MasterReadModelPersistenceAdapter` —
  `findWarehouse(id)`, `findZone(id)`, `findLocation(id)`, `findSku(id)`,
  `findLot(id)`, `findPartner(id)` — each returns `Optional<Snapshot>`
- DLQ wiring: `DefaultErrorHandler` 3-retry exp-backoff `[1s, 2s, 4s]` +
  `DeadLetterPublishingRecoverer` for all consumers
- **`ErpOrderWebhookController`** (`POST /webhooks/erp/order`):
  - HMAC-SHA256 signature verification via `WebhookSecretPort`
  - Timestamp window check (±5 min, configurable)
  - Atomic `INSERT INTO erp_order_webhook_dedupe ON CONFLICT DO NOTHING` +
    `INSERT INTO erp_order_webhook_inbox` in one TX
  - Schema-validate body against `webhooks/erp-order-webhook.md` (Bean
    Validation on `@RequestBody` DTO)
  - Returns 200 `{status: "accepted"|"ignored_duplicate", eventId, ...}`
  - Anonymous route (`permitAll()`) in SecurityConfig
- **`WebhookSecretPort`** + env-var-backed adapter:
  `ERP_WEBHOOK_ORDER_SECRET_<SOURCE_UPPER>` per environment
- **`ErpOrderWebhookInboxProcessor`** (`@Scheduled`, every 1s, non-test
  profile):
  - Selects `erp_order_webhook_inbox WHERE status='PENDING' LIMIT 50`
  - **Stub**: marks rows `APPLIED` without creating Order (real logic in
    TASK-BE-035)
- **`IdempotencyStore`** (out-port) + `RedisIdempotencyStore` (Redis adapter)
  — key layout: `outbound:idempotency:{method}:{path_hash}:{key}`, TTL 86400s
- **`InMemoryIdempotencyStore`** — for tests/standalone; uses atomic
  `ConcurrentHashMap.compute()` (mirrors inbound-service TASK-BE-033 fix)
- **`OutboundSaga`** JPA entity + `OutboundSagaRepository` — fields per
  `domain-model.md` §6. Domain aggregate `OutboundSaga` (pure POJO) with stub
  state-transition methods (implementations land in TASK-BE-036)
- **`OutboundSagaCoordinator`** (application/saga/) — stub class with methods
  `onInventoryReserved(...)`, `onInventoryReleased(...)`,
  `onInventoryConfirmed(...)`, `onReserveFailed(...)`. Each method is a no-op
  stub logging "saga event received, handler not yet implemented". Saga consumer
  wiring lands in TASK-BE-036.
- **`ShipmentNotificationPort`** (out-port) + **`StubTmsClientAdapter`** —
  logs "TMS notify stub: shipment {id}" and returns
  `TmsAcknowledgement(success=true, requestId)` immediately. Real
  Resilience4j TMS adapter lands in TASK-BE-037.
- **`OutboxWriter`** port + stub implementation (table created in V6; real
  publisher with retry/backoff/metrics in TASK-BE-035)
- JWT resource server (via `java-security`); roles `OUTBOUND_READ`,
  `OUTBOUND_WRITE`, `OUTBOUND_ADMIN`. SecurityConfig:
  - `/webhooks/erp/order` → `permitAll()`
  - GET endpoints → `OUTBOUND_READ`
  - POST/PATCH/DELETE endpoints → `OUTBOUND_WRITE`
  - Cancel (post-pick) + TMS retry → `OUTBOUND_ADMIN`
- Observability: MDC `traceId`/`requestId`/`actorId`, Micrometer metrics,
  OTel on Kafka consumers and webhook ingest span `webhook.erp.order.ingest`
- `GET /actuator/health`, `GET /actuator/info`
- Dev-profile seed: `V99__seed_dev_masterref.sql` — minimal warehouse +
  location + sku + partner + lot snapshots; Spring profile `dev` or `standalone`
- Unit tests: master consumer version-guard, HMAC verification, timestamp
  window, body hash canonicalization, `InMemoryIdempotencyStore` atomicity
- `@SpringBootTest` smoke: boots, health UP, all Flyway migrations applied,
  webhook controller reachable

### Out of Scope

- Order reception domain logic (`ReceiveOrderUseCase`, `Order` aggregate
  mutations) — TASK-BE-035
- PickingRequest / PickingConfirmation lifecycle — TASK-BE-036
- PackingUnit / Shipment / ConfirmShipping use-case — TASK-BE-037
- Saga step consumers (`InventoryReservedConsumer`, etc.) — TASK-BE-036
- Real TMS Resilience4j adapter — TASK-BE-037
- `OutboundSagaCoordinator` full implementation — TASK-BE-036
- Outbox publisher (`@Scheduled` with retry/backoff/metrics) — TASK-BE-035
- Gateway route wiring (separate `TASK-INT-*`)
- Real Secret Manager adapter (v2)
- Webhook inbox FAILED retry endpoint (v2)

---

## Acceptance Criteria

1. `./gradlew :projects:wms-platform:apps:outbound-service:build` passes cleanly
2. Service boots against Docker Compose (Postgres + Kafka + Redis); `GET /actuator/health` returns `200 UP`
3. All 8 Flyway migrations (V1–V8) plus V99 under `dev` apply cleanly on boot
4. Append-only role grants: `UPDATE`/`DELETE` rejected for app role on `outbound_outbox`, `outbound_event_dedupe`, `erp_order_webhook_dedupe`, `tms_request_dedupe`; verified by Testcontainers test asserting rejection on app-role connection
5. All 6 master snapshot consumers upsert correctly on `*.created` / `*.updated` / `*.deactivated` / `*.reactivated`; `MasterLotConsumer` handles `master.lot.expired` → `status=EXPIRED`; verified by Testcontainers Kafka tests
6. DB version guard: a `master.location.updated` event with `master_version <= cached` is silently ignored; later higher-version event applies
7. `EventDedupePort.process(eventId, eventType, runnable)` inserts and invokes runnable on first call; second call with same `eventId` returns `IGNORED_DUPLICATE` without re-executing
8. EventDedupe write + snapshot upsert are in the **same** `@Transactional` boundary (`Propagation.MANDATORY` on adapter)
9. DLQ routing: an unparseable master event routes to `wms.master.location.v1.DLT` after 3 retries
10. `IdempotencyStore` Redis adapter: `put(key, response, ttl)` persists; `lookup(key)` retrieves within TTL; literal key shape matches `outbound:idempotency:{method}:{path_hash}:{key}` (pinned assertion in `RedisIdempotencyStoreTest`)
11. Webhook smoke: valid HMAC + timestamp + new event-id → 200 `accepted`, row in `erp_order_webhook_inbox` with `status=PENDING`, row in `erp_order_webhook_dedupe`
12. Webhook failure-modes per `webhooks/erp-order-webhook.md` § Failure-mode Test Cases: all cases pass
13. Webhook controller does NOT enforce JWT (`permitAll()` for `/webhooks/erp/order`)
14. Inbox processor stub: PENDING row → `APPLIED` on next tick (scheduler disabled in test; processor invoked directly); no Order row created
15. JWT roles loaded: unauthenticated request to any protected endpoint → 401
16. `MasterReadModelPort` returns empty Optional for unknown IDs for all 6 entities
17. `OutboundSagaCoordinator` stub methods exist and are callable without error (log "not yet implemented")
18. `ShipmentNotificationPort` stub returns success immediately; logs the shipment ID
19. Structured logs contain `traceId`, `eventId`, `consumer` in MDC on consumer log lines; webhook log contains `eventId`, `source` but NOT raw body or signature value
20. All unit + slice + smoke tests pass (`./gradlew :projects:wms-platform:apps:outbound-service:test`)

---

## Related Specs

- `platform/entrypoint.md` — read first; load `PROJECT.md`, `rules/common.md`,
  `rules/domains/wms.md`, `rules/traits/transactional.md`,
  `rules/traits/integration-heavy.md`
- `platform/architecture.md`, `platform/architecture-decision-rule.md`
- `platform/shared-library-policy.md`, `platform/testing-strategy.md`
- `platform/service-types/rest-api.md`, `platform/service-types/event-consumer.md`
- `platform/security-rules.md`
- `specs/services/outbound-service/architecture.md`
- `specs/services/outbound-service/domain-model.md`
- `specs/services/outbound-service/idempotency.md` — §1 REST, §2 webhook, §3 Kafka, §4 saga-level
- `specs/services/outbound-service/external-integrations.md` — §1 ERP webhook, §2 Kafka, §3 Postgres, §4 Redis, §5 TMS, §6 Secret Manager
- `specs/services/outbound-service/sagas/outbound-saga.md` — §1 overview, §2 steps (for stub method signatures)
- `specs/services/outbound-service/state-machines/order-status.md` — Order status enum values (for V2 migration)
- `specs/services/outbound-service/state-machines/saga-status.md` — OutboundSaga state enum values (for V5 migration)
- `rules/domains/wms.md` — W2 (append-only), W6 (master ref integrity)
- `rules/traits/transactional.md` — T3 (outbox), T8 (eventId dedupe)
- `rules/traits/integration-heavy.md` — I1–I5 (DLQ), I6 (webhook), I8 (model translation), I10 (test fakes)

---

## Related Contracts

- `specs/contracts/events/master-events.md` — §1–§6 (all 6 consumed topics)
- `specs/contracts/webhooks/erp-order-webhook.md` — full webhook wire format
- `specs/contracts/events/outbound-events.md` — topic names and event types
  (used only for outbox table `event_type` enum values in V6 migration)

---

## Edge Cases

1. `master.location.deactivated` received before `master.location.created`
   — upsert with `status=INACTIVE`; subsequent `created` with lower
   `master_version` is ignored by version guard
2. Duplicate `master.sku.updated` (broker redelivery) — `EventDedupePort`
   returns `IGNORED_DUPLICATE`; no double upsert
3. Redis unavailable at idempotency store write — fail closed: 503; do not
   skip idempotency (T1)
4. Kafka unavailable at startup — consumer retries per Spring Kafka policy;
   health probe DOWN until reconnected
5. `lot_snapshot` for a lot whose SKU is not yet in `sku_snapshot` — insert
   allowed; SKU arrives eventually via `master.sku.created`
6. Webhook arrives during DB failover — TX fails → 503; ERP retries with
   same `X-Erp-Event-Id`
7. Webhook payload valid HMAC but JSON body null or empty — 422
   `VALIDATION_ERROR`; no inbox/dedupe write
8. Inbox processor crashes mid-loop — TX rolls back per row; PENDING rows
   remain for next tick

---

## Failure Scenarios

1. **Postgres down at boot** — health DOWN; readiness probe fails
2. **Kafka down for master consumers** — consumer lag grows; MasterReadModel
   becomes stale; eventual consistency per `domain-model.md` §11
3. **Redis down for idempotency** — fail closed 503; partial idempotency NOT served
4. **Malformed master event** — DLT after 3 retries; DLT-depth metric alert
5. **V8 role grant migration fails** (insufficient DB privileges) — boot fails;
   operator must grant REVOKE privilege to migration user
6. **Webhook secret missing** — 401 `WEBHOOK_SIGNATURE_INVALID`;
   `outbound.webhook.received.total{result=signature_invalid}` increments
7. **Inbox table grows unbounded** — gauge `outbound.webhook.inbox.pending.count`
   alerts at >100

---

## Target Service

`projects/wms-platform/apps/outbound-service`

## Architecture

Hexagonal (Ports & Adapters). Service type composition: `rest-api` +
`event-consumer` + webhook receiver. Read both `platform/service-types/rest-api.md`
and `platform/service-types/event-consumer.md` (same documented exception as
`inventory-service` and `inbound-service`).

## Test Requirements

### Unit Tests
- `EventDedupePersistenceAdapter`: first call applies runnable, second call skips
- 6 master consumers: version-guard (lower ignored, higher applied), status mapping
- HMAC verifier: valid passes, mismatch fails, constant-time compare
- Timestamp window: in-window passes, boundary cases, out-of-window fails
- Body hash: order-independent, stable across whitespace
- `InMemoryIdempotencyStore.tryAcquireLock()`: atomic (uses `ConcurrentHashMap.compute()`)

### Slice / Integration Tests (Testcontainers)
- Flyway migration test (Postgres): all 8 migrations; 4 append-only tables
  reject UPDATE/DELETE on app-role connection
- 6 master consumers (Kafka): `*.created` upserts; same event again → dedupe
  skips; `*.deactivated` → INACTIVE; DLT routing on unparseable record
- Webhook controller (`@SpringBootTest`): all failure-mode cases per
  `erp-order-webhook.md` § Failure-mode Test Cases
- `RedisIdempotencyStoreTest` (Redis): literal key shape pinned with assertion
- Inbox processor stub: PENDING → APPLIED on manual invocation, no Order created

### Smoke Test
- `@SpringBootTest` (Postgres + Kafka + Redis): boots, health UP, all
  migrations applied, all 6 consumers subscribe without error, webhook
  controller responds to valid POST

## Definition of Done

- All 20 acceptance criteria satisfied
- `./gradlew :projects:wms-platform:apps:outbound-service:test` passes (zero failures)
- No TODO / FIXME left in production code
- Task moved to `review/` with `Status: review`
