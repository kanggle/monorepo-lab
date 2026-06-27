# TASK-BE-448 (ecommerce) — Migrate order-service outbox v1 → v2 (AbstractOutboxPublisher)

**Status:** ready

**Type:** TASK-BE
**Analysis model:** Opus 4.8 / **Recommended impl model:** Opus (saga-critical schema migration + write-path rewrite + publisher/metrics swap with a preserved custom failure metric; multiple downstream consumers + a CI Testcontainers lane)

**Service:** order-service (ecommerce-microservices-platform)

> **Origin.** ADR-MONO-004 § 6 ecommerce follow-up. Fifth ecommerce service migrated to v2, mirroring promotion/review/shipping/settlement. **Saga-critical**: the four order events drive payment / product / settlement / shipping / notification / promotion. order-service **has a CI Testcontainers `:integrationTest` lane**, so the round-trip is runtime-verified on CI Linux (unlike the prior four).

---

## Goal

Replace order-service's v1 outbox stack with the shared v2 `AbstractOutboxPublisher`, gaining backoff, `eventId`/`eventType` headers, per-event lag metrics, UUID natural key — **while preserving the saga wire exactly**.

**Wire preserved exactly:**
- Topics: `OrderPlaced → order.order.placed`, `OrderConfirmed → order.order.confirmed`, `OrderCancelled → order.order.cancelled`, `OrderSagaRecoveryExhausted → order.alert.saga.recovery.exhausted`.
- Kafka record **value** = serialized snake_case envelope, byte-identical.
- Kafka record **key** = `aggregateId` = `orderId`.
- Header `eventId`/`eventType` is **additive** (v1 had none); ecommerce consumers parse the payload JSON, so consumption is unaffected.
- The pre-existing `event_publish_failure_total{service=order-service}` metric (v1 `onKafkaSendFailure` hook) is preserved.

## Scope

**In scope (order-service only):**
1. `V11__order_outbox_v2.sql` — `order_outbox` (mirror `master_outbox`; partial pending index). Retain v1 `outbox` (V5) + `processed_events` (V6) — EntityScan validate. (V10 already taken by `V10__add_order_idempotency_key.sql` — next free version is V11.)
2. `OrderOutboxEntity extends OutboxRowEntity` + `OrderOutboxRepository` — both under `com.example.order.infrastructure.persistence` (the `@EnableJpaRepositories` base package).
3. Rewrite `SpringOrderEventPublisher` (`@Profile("!standalone")`) to persist an `OrderOutboxEntity` per event: `event_id = UUID.fromString(event.eventId())`, `occurred_at = Instant.parse(event.occurredAt())`, `aggregate_type = "Order"`, `aggregate_id = orderId`, `payload = writeValueAsString(event)` (unchanged). Standalone keeps `StandaloneOrderEventPublisher`.
4. `OrderOutboxPublisher extends AbstractOutboxPublisher<OrderOutboxEntity>` (`@Profile("!standalone")`) — `TopicResolver` switch ported verbatim (4 types + reject-unmapped), `MicrometerOutboxMetrics("order")` **wrapped** to also fire `OrderMetricsPort.recordEventPublishFailure(eventType)` on a per-event send failure (preserving the v1 hook + `event_publish_failure_total` counter), preserved `order.outbox.pending.count` gauge, `@Scheduled`.
5. `OutboxConfig` (TransactionTemplate; `Clock` already in `ClockConfig`). Keep lib `OutboxAutoConfiguration`.
6. `order.outbox.*` config keys (merged under the existing `order:` block — no YAML duplicate key); legacy `outbox.polling.*` retained.
7. Tests: rewrite `SpringOrderEventPublisherTest` (v2 row + byte-identical payload), new `OrderOutboxPublisherTest` (4-topic resolve/reject, publish round-trip, mark-published, metrics, gauge, backoff, **preserved order-metric hook**), **rewrite `OrderEventPublishIntegrationTest` to v2** (query `order_outbox`, `published_at`-based, drive the relay explicitly with the poller dormant), and **update the v1-`outbox`-table SQL in `ConfirmPaidStaleIT` / `OrderStuckRecoveryIT` / `MultiTenantIsolationIntegrationTest`** → `order_outbox`. Delete `OutboxPollingScheduler` + `OutboxPollingSchedulerTest`.
8. Update `specs/services/order-service/architecture.md` § Outbox.

**Out of scope:** other ecommerce services; ADR-MONO-004 § 6 edit (deferred to single end-of-series reconciliation); the `processed_events` consumer-dedupe table; the inbound consumers.

## Acceptance Criteria
- **AC-1** topics preserved (all 4) + reject-unmapped.
- **AC-2** v2 behaviours: headers, backoff, per-eventType metrics.
- **AC-3** wire preserved: value byte-identical; key = orderId; row `event_id` == envelope id.
- **AC-4** `V10` applies on fresh + on top of V1–V9; new entity validates; retained v1 `outbox`/`processed_events` still validate.
- **AC-5** write path persists an `order_outbox` row per event with correct fields; standalone still uses `StandaloneOrderEventPublisher` (REST).
- **AC-6** v1 `OutboxPollingScheduler` removed (grep clean); write path no longer uses lib `OutboxWriter`.
- **AC-7 (metric continuity)** `event_publish_failure_total{service=order-service}` still increments on a per-event Kafka send failure (verified by unit test).
- **AC-8 (build + CI IT)** `:order-service:test` GREEN (Docker-free unit). The **`:integrationTest`** lane (CI Linux Testcontainers — authoritative) GREEN: the rewritten `OrderEventPublishIntegrationTest` round-trip + the updated `outbox`→`order_outbox` ITs. Push and let CI validate; do not claim IT-green from local (Docker blocked locally).

## Related Specs
- `projects/ecommerce-microservices-platform/specs/services/order-service/architecture.md`
- `docs/adr/ADR-MONO-004-shared-messaging-scaffolding.md` § 5

## Related Contracts
- `projects/ecommerce-microservices-platform/specs/contracts/events/order-events.md` — topics + envelopes unchanged.

## Dependencies / Prior Work
- **TASK-BE-444/445/446/447** — ecommerce v2 references.
- **TASK-BE-438** — CI-validated Postgres v2 reference.

## Edge Cases
- **Saga wire**: 4 events, 6+ consumers — header additive, value/key/topic preserved; ecommerce consumers parse payload JSON (header-agnostic).
- **Custom failure metric**: the v1 `onKafkaSendFailure` hook is preserved by wrapping the lib `OutboxMetrics` (guard on `eventType != null` so poll-level failures aren't double-counted into the order metric).
- **EntityScan**: keep current config; only ADD `order_outbox`. Repo/entity under `com.example.order.infrastructure.persistence`.
- **Existing ITs query the v1 `outbox` table** — these run on the CI integrationTest lane, so they MUST be updated to `order_outbox` (else CI RED). All four (`OrderEventPublish`, `ConfirmPaidStale`, `OrderStuckRecovery`, `MultiTenant`) updated.
- **YAML**: `order.outbox.*` merged into the existing `order:` block.
- Cutover: in-flight v1 `outbox` rows abandoned (saga re-driven from clean state in demo/CI).

## Failure Scenarios
- **F1** in-flight v1 rows abandoned — acceptable.
- **F2** dropping `outbox`/`processed_events` → validate boot failure — mitigated by retaining them.
- **F3** saga wire drift — mitigated by unchanged serialization + exact 4-topic switch + key=orderId, unit-asserted + CI IT.
- **F4** stale IT querying `outbox` → CI integrationTest RED — mitigated by updating all four ITs to `order_outbox` (AC-8).
- **F5** lost custom failure metric → dashboard/alert gap — mitigated by the wrapping OutboxMetrics + AC-7 unit test.
