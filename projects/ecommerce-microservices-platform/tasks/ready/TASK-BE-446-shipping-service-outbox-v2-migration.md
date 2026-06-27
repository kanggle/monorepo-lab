# TASK-BE-446 (ecommerce) — Migrate shipping-service outbox v1 → v2 (AbstractOutboxPublisher)

**Status:** ready

**Type:** TASK-BE
**Analysis model:** Opus 4.8 / **Recommended impl model:** Opus (schema migration + multi-shape write-path rewrite + publisher/metrics swap; medium coupling — wms fulfillment loop consumer)

**Service:** shipping-service (ecommerce-microservices-platform)

> **Origin.** ADR-MONO-004 § 6 ecommerce follow-up. Third ecommerce service migrated to v2, mirroring promotion (TASK-BE-444) / review (TASK-BE-445). Medium coupling: `ShippingStatusChanged` is consumed by order-service + notification-service; the two fulfillment legs (`FulfillmentRequested`, `ManualShipConfirmRequested`) feed the wms outbound-service (ADR-MONO-022 fulfillment loop).

---

## Goal

Replace shipping-service's v1 outbox stack with the shared v2 `AbstractOutboxPublisher`, gaining backoff, `eventId`/`eventType` headers, per-event lag metrics, UUID natural key.

**Wire preserved exactly:**
- Topics (mixed conventions, verbatim): `ShippingStatusChanged → shipping.shipping.status-changed` (no `.v1`); `FulfillmentRequested → ecommerce.fulfillment.requested.v1`; `ManualShipConfirmRequested → ecommerce.shipping.manual-confirm-requested.v1`.
- Kafka record **value**: the serialized envelope (snake_case `ShippingStatusChangedMessage`; wms camelCase `ManualShipConfirmRequestedMessage`) or the opaque pre-serialized `messageJson` (forward fulfillment leg) — byte-identical.
- Kafka record **key** = `aggregateId` (`shippingId` for status-changed; `orderId` for the two fulfillment legs).
- Header `eventId`/`eventType` is **additive** (v1 had no headers). The wms consumer dedupes on the payload's `eventId` via its `EventEnvelopeParser`, not the Kafka header — so it's unaffected.

## Scope

**In scope (shipping-service only):**
1. `V9__shipping_outbox_v2.sql` — `shipping_outbox` (mirror `master_outbox`; partial pending index). Retain v1 `outbox` + `processed_events` (V4, the consumer-dedupe table) — EntityScan validate.
2. `ShippingOutboxEntity extends OutboxRowEntity` + `ShippingOutboxRepository`.
3. Rewrite `SpringShippingEventPublisher` (implements `ShippingEventPublisher`) to persist a `ShippingOutboxEntity` per event. `event_id`: reuse the structured envelope's own `event_id` (`ShippingStatusChanged`, `ManualShipConfirmRequested`); mint a fresh UUID for the opaque `FulfillmentRequested` leg (payload stored verbatim). `occurred_at`: parsed from the envelope timestamp (structured) or `clock.instant()` (fulfillment). `aggregate_type`/`aggregate_id`/routing-key `eventType` unchanged.
4. `ShippingOutboxPublisher extends AbstractOutboxPublisher<ShippingOutboxEntity>` — `TopicResolver` switch ported verbatim (mixed conventions + reject-unmapped), `MicrometerOutboxMetrics("shipping")` + preserved `shipping.outbox.pending.count` gauge, `@Scheduled`, no `@Profile`.
5. `OutboxConfig` (TransactionTemplate; `Clock` already in `ClockConfig`). Keep lib `OutboxAutoConfiguration`.
6. `shipping.outbox.*` config keys; legacy `outbox.polling.*` retained.
7. Tests: rewrite `SpringShippingEventPublisherTest` (v2 row + per-shape payload assertions incl. opaque fulfillment + nullable manual-confirm fields), new `ShippingOutboxPublisherTest` (3-topic resolve + reject, publish round-trip headers/key/value, mark-published, metrics, gauge, backoff), `ShippingOutboxRelayIntegrationTest` (`@Tag("integration")`, authored but not CI-run). Delete `OutboxPollingScheduler`.
8. Update `specs/services/shipping-service/architecture.md` § Outbox.

**Out of scope:** other ecommerce services; ADR-MONO-004 § 6 edit (deferred to single end-of-series reconciliation); the `processed_events` consumer-dedupe table; the consumers (`OrderConfirmedEventConsumer`, `WmsShippingConfirmedConsumer`, `WmsOutboundCancelledConsumer`) — producer-side only.

## Acceptance Criteria
- **AC-1** topics preserved (all 3, mixed conventions) + reject-unmapped.
- **AC-2** v2 behaviours: headers, backoff, per-eventType metrics.
- **AC-3** wire preserved: value byte-identical per shape (incl. opaque fulfillment `messageJson` verbatim); key = aggregateId; structured rows' `event_id` == envelope id.
- **AC-4** `V9` applies on fresh + on top of V1–V8; new entity validates; retained v1 `outbox`/`processed_events` still validate.
- **AC-5** write path persists a `shipping_outbox` row per event with correct fields.
- **AC-6** v1 `OutboxPollingScheduler` removed (grep clean); write path no longer uses lib `OutboxWriter`.
- **AC-7** `:shipping-service:test` GREEN (Docker-free unit) is the authoritative gate — shipping-service has **no CI Testcontainers lane** (only order/payment); the `@Tag("integration")` IT is not CI-run. Correctness assured by mirroring the CI-validated master/promotion pattern + unit coverage of all three shapes.

## Related Specs
- `projects/ecommerce-microservices-platform/specs/services/shipping-service/architecture.md`
- `docs/adr/ADR-MONO-004-shared-messaging-scaffolding.md` § 5; `ADR-MONO-022` (fulfillment loop)

## Related Contracts
- `projects/ecommerce-microservices-platform/specs/contracts/events/fulfillment-events.md` — wms camelCase envelopes unchanged; topics unchanged.

## Dependencies / Prior Work
- **TASK-BE-444 / TASK-BE-445** — ecommerce v2 references.
- **TASK-BE-438** — CI-validated Postgres v2 reference.

## Edge Cases
- EntityScan / retained v1 tables (`outbox` + `processed_events`) — kept for validate; the app also `@EntityScan`s `com.example.messaging` explicitly.
- Three event types, two envelope shapes, mixed topic conventions — all preserved.
- Opaque `FulfillmentRequested` payload (pre-serialized `messageJson`) — stored verbatim; fresh UUID PK (consumer dedupes on payload id, not header).
- Cutover: in-flight v1 `outbox` rows abandoned (re-derivable in demo/CI).
- No CI IT lane — verification via unit + schema-mirror (AC-7).

## Failure Scenarios
- **F1** in-flight v1 rows abandoned — acceptable.
- **F2** dropping `outbox`/`processed_events` → validate boot failure — mitigated by retaining them.
- **F3** wire drift across the 3 shapes — mitigated by unchanged serialization + exact topic switch + key=aggregateId, unit-asserted per shape.
