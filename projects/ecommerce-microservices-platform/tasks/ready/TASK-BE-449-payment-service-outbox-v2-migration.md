# TASK-BE-449 (ecommerce) — Migrate payment-service outbox v1 → v2 (AbstractOutboxPublisher)

**Status:** ready

**Type:** TASK-BE
**Analysis model:** Opus 4.8 / **Recommended impl model:** Opus (money-critical schema migration + write-path rewrite + publisher/metrics swap with a preserved custom failure metric + a preserved `@ConditionalOnProperty` gate; multiple downstream consumers + a CI Testcontainers lane)

**Service:** payment-service (ecommerce-microservices-platform)

> **Origin.** ADR-MONO-004 § 6 ecommerce follow-up — the **sixth and last** ecommerce service migrated to v2, completing the ecommerce outbox-v2 sweep (after promotion/review/shipping/settlement/order). **Money-critical**: the four payment events drive order-saga confirmation, settlement accrual/reversal, product, and notification. payment-service **has a CI Testcontainers `:integrationTest` lane**, so the round-trip is runtime-verified on CI Linux.

---

## Goal

Replace payment-service's v1 outbox stack with the shared v2 `AbstractOutboxPublisher`, gaining backoff, `eventId`/`eventType` headers, per-event lag metrics, UUID natural key — **while preserving the money wire + the failure metric + the polling gate exactly**.

**Wire preserved exactly:**
- Topics: `PaymentCompleted → payment.payment.completed`, `PaymentRefunded → payment.payment.refunded`, `PaymentRefundStranded → payment.alert.refund.stranded`, `PaymentRefundUnresolved → payment.alert.refund.unresolved`.
- Kafka record **value** = serialized snake_case envelope, byte-identical.
- Kafka record **key** = `aggregateId` = `paymentId`.
- Header `eventId`/`eventType` is **additive** (v1 had none); consumers parse the payload JSON + dedupe on the payload `event_id`, so consumption is unaffected.
- `event_publish_failure_total{service=payment-service}` (v1 `onKafkaSendFailure` hook) preserved.
- The relay on/off gate stays `outbox.polling.enabled` (5 ITs disable it).

## Scope

**In scope (payment-service only):**
1. `V8__payment_outbox_v2.sql` — `payment_outbox` (mirror `master_outbox`; partial pending index). Retain v1 `outbox` (V3) + `processed_events` (V4) — EntityScan validate.
2. `PaymentOutboxEntity extends OutboxRowEntity` + `PaymentOutboxRepository` (`adapter.out.event`; default `@SpringBootApplication` scan covers them).
3. Rewrite `PaymentEventOutboxWriter` (implements `PaymentEventPublisher`, `@Profile("!standalone")`) to persist a `PaymentOutboxEntity` per event: `event_id = UUID.fromString(event.eventId())`, `occurred_at = Instant.parse(event.occurredAt())`, `aggregate_type = "Payment"`, `aggregate_id = paymentId`, `payload = writeValueAsString(event)` (unchanged). Keep the `EVENT_TYPE_*` + `AGGREGATE_TYPE` constants (the publisher + tests reference them).
4. `PaymentOutboxPublisher extends AbstractOutboxPublisher<PaymentOutboxEntity>` (`@Profile("!standalone")` + `@ConditionalOnProperty("outbox.polling.enabled", matchIfMissing=true)`) — `TopicResolver` switch ported verbatim (4 types + reject-unmapped, reusing the writer's `EVENT_TYPE_*`), `MicrometerOutboxMetrics("payment")` **wrapped** to also fire `PaymentMetricRecorder.incrementEventPublishFailure(eventType)` on a per-event send failure, preserved `payment.outbox.pending.count` gauge, `@Scheduled`. Delete `PaymentEventOutboxRelay`.
5. `OutboxConfig` (TransactionTemplate; `Clock` already in `ClockConfig`). Keep lib `OutboxAutoConfiguration`.
6. `payment.outbox.*` timing keys (merged under the existing `payment:` block — no YAML duplicate key); the `outbox.polling.enabled` gate + legacy `outbox.polling.*` retained.
7. Tests: rewrite `PaymentEventOutboxWriterTest` (v2 row + byte-identical payload; uses valid UUID event ids), new `PaymentOutboxPublisherTest` (4-topic resolve/reject, publish round-trip, mark-published, metrics, gauge, backoff, **preserved payment-metric hook**), **rewrite `PaymentEventPublishIntegrationTest` to v2** (query `payment_outbox`, `published_at`-based, v2 timing knobs), and **update the v1-`outbox`-table SQL in `PaymentRefundStrandedDurabilityIntegrationTest` / `StrandedRefundReconciliationIntegrationTest`** → `payment_outbox`. Delete `PaymentEventOutboxRelay` + `PaymentEventOutboxRelayTest`.
8. Update `specs/services/payment-service/architecture.md` § Outbox.

**Out of scope:** other ecommerce services; ADR-MONO-004 § 6 edit (deferred to a single end-of-series reconciliation); the `processed_events` consumer-dedupe table; the `stranded_refund` table + sweeper/reconciler logic (unchanged — they publish via the `PaymentEventPublisher` port, which is preserved).

## Acceptance Criteria
- **AC-1** topics preserved (all 4) + reject-unmapped.
- **AC-2** v2 behaviours: headers, backoff, per-eventType metrics.
- **AC-3** wire preserved: value byte-identical; key = paymentId; row `event_id` == envelope id.
- **AC-4** `V8` applies on fresh + on top of V1–V7; new entity validates; retained v1 `outbox`/`processed_events` still validate.
- **AC-5** write path persists a `payment_outbox` row per event with correct fields; standalone path unchanged (writer `@Profile("!standalone")`).
- **AC-6** v1 `PaymentEventOutboxRelay` removed (grep clean); write path no longer uses lib `OutboxWriter`.
- **AC-7 (metric continuity)** `event_publish_failure_total{service=payment-service}` still increments on a per-event Kafka send failure (verified by unit test).
- **AC-8 (gate continuity)** `outbox.polling.enabled=false` still disables the relay (preserved property name — the 5 ITs that set it are unchanged).
- **AC-9 (build + CI IT)** `:payment-service:test` GREEN (Docker-free unit). The **`:integrationTest`** lane (CI Linux Testcontainers — authoritative) GREEN: the rewritten `PaymentEventPublishIntegrationTest` round-trip + the updated `outbox`→`payment_outbox` ITs. Push and let CI validate; do not claim IT-green from local (Docker blocked locally).

## Related Specs
- `projects/ecommerce-microservices-platform/specs/services/payment-service/architecture.md`
- `docs/adr/ADR-MONO-004-shared-messaging-scaffolding.md` § 5; `ADR-006` (at-least-once)

## Related Contracts
- `projects/ecommerce-microservices-platform/specs/contracts/events/payment-events.md` — topics + envelopes unchanged.

## Dependencies / Prior Work
- **TASK-BE-444/445/446/447/448** — ecommerce v2 references.
- **TASK-BE-438** — CI-validated Postgres v2 reference.

## Edge Cases
- **Money wire**: 4 events, 4+ consumers — header additive, value/key/topic preserved; consumers dedupe on the payload `event_id` (header-agnostic).
- **Custom failure metric**: v1 `onKafkaSendFailure` hook preserved by wrapping the lib `OutboxMetrics` (guard on `eventType != null`).
- **Polling gate**: kept as `outbox.polling.enabled` (NOT renamed) so the 5 ITs that set it stay green; v2 timing under `payment.outbox.*`.
- **occurred_at parse**: all 4 events build `occurredAt` from `Instant.now().toString()` (ISO instant) → `Instant.parse` safe.
- **EntityScan**: default `@SpringBootApplication` scan covers `com.example.payment` (entity + repo under `adapter.out.event`); the lib OutboxAutoConfiguration scans the v1 lib entities. Only ADD `payment_outbox`.
- **Existing ITs query the v1 `outbox` table** — these run on the CI integrationTest lane, so all three (`PaymentEventPublish`, `PaymentRefundStrandedDurability`, `StrandedRefundReconciliation`) updated to `payment_outbox` (else CI RED).
- Cutover: in-flight v1 `outbox` rows abandoned (re-driven from clean state in demo/CI).

## Failure Scenarios
- **F1** in-flight v1 rows abandoned — acceptable.
- **F2** dropping `outbox`/`processed_events` → validate boot failure — mitigated by retaining them.
- **F3** money wire drift — mitigated by unchanged serialization + exact 4-topic switch + key=paymentId, unit-asserted + CI IT.
- **F4** stale IT querying `outbox` → CI integrationTest RED — mitigated by updating all three ITs to `payment_outbox` (AC-9).
- **F5** lost custom failure metric / changed gate property → dashboard/alert gap or test break — mitigated by the wrapping OutboxMetrics + AC-7 + the preserved `outbox.polling.enabled` gate (AC-8).
