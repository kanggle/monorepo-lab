# TASK-BE-445 (ecommerce) — Migrate review-service outbox v1 → v2 (AbstractOutboxPublisher)

**Status:** ready

**Type:** TASK-BE
**Analysis model:** Opus 4.8 / **Recommended impl model:** Opus (schema migration + write-path rewrite + publisher/metrics swap)

**Service:** review-service (ecommerce-microservices-platform)

> **Origin.** ADR-MONO-004 § 6 ecommerce follow-up. Second ecommerce service migrated to v2, mirroring the in-platform reference established by `promotion-service` (TASK-BE-444). review-service has no live ecommerce consumer for `review.review.*`, so it remains low-blast-radius.

---

## Goal

Replace review-service's v1 outbox stack (lib `OutboxPollingScheduler` relay + lib `OutboxWriter`/`OutboxJpaEntity` write path) with the shared v2 `AbstractOutboxPublisher`, gaining exponential backoff, `eventId`/`eventType` Kafka headers, per-event lag metrics, and a UUID natural key.

**Wire preserved exactly:**
- Topics `ReviewCreated/Updated/Deleted → review.review.{created,updated,deleted}`.
- Kafka record **value** = `objectMapper.writeValueAsString(ReviewEventMessage)` (the existing DTO envelope), byte-identical.
- Kafka record **key** = `aggregateId` = `reviewId`.
- The `ReviewEventMessage` envelope (`event_id`/`event_type`/`occurred_at`/`source`/`tenant_id`/`payload`) is unchanged.

## Scope

**In scope (review-service only):**
1. `V6__review_outbox_v2.sql` — `review_outbox` (mirror `master_outbox`/`promotion_outbox`; Postgres, partial pending index). Retain v1 `outbox` + `processed_events` (V4) — EntityScan validate.
2. `ReviewOutboxEntity extends OutboxRowEntity` + `ReviewOutboxRepository` (`findPending`/`countByPublishedAtIsNull`).
3. Rewrite `OutboxReviewEventPublisher` (implements `ReviewEventPublisher`) to persist a `ReviewOutboxEntity` directly: `event_id = event.eventId()` (UUID — reuse the envelope id), `occurred_at = event.occurredAt()`, `aggregate_type = "Review"`, `aggregate_id = reviewId`, `partition_key = null`, `payload = writeValueAsString(toMessage(event))` (unchanged). Keep `toMessage(...)` (still used + covered by `ReviewEventMessageMappingTest`).
4. `ReviewOutboxPublisher extends AbstractOutboxPublisher<ReviewOutboxEntity>` (mirror promotion) — `TopicResolver` switch ported verbatim incl. reject-unmapped, `MicrometerOutboxMetrics("review")` + preserved `review.outbox.pending.count` gauge, `@Scheduled`, no `@Profile`.
5. `OutboxConfig` (TransactionTemplate; `Clock` already in `ClockConfig`). Keep lib `OutboxAutoConfiguration`.
6. `review.outbox.*` config keys (`batch-size`/`poll-ms`/`initial-delay-ms`); legacy `outbox.polling.*` retained (lib beans bind them).
7. Tests: rewrite `OutboxReviewEventPublisherTest` (v2 row assertions, byte-identical payload), new `ReviewOutboxPublisherTest` (topic resolve + reject-unmapped, publish round-trip headers/key/value, mark-published, metrics, gauge, backoff), `ReviewOutboxRelayIntegrationTest` (`@Tag("integration")`, authored but not CI-run). Delete `ReviewOutboxPollingScheduler` + `ReviewOutboxPollingSchedulerTest`.
8. Update `specs/services/review-service/architecture.md` § Outbox.

**Out of scope:** other ecommerce services; ADR-MONO-004 § 6 edit (deferred to a single end-of-series reconciliation to avoid cross-PR conflicts on the shared doc); the `processed_events` consumer-dedupe table.

## Acceptance Criteria
- **AC-1** topics preserved (`review.review.{created,updated,deleted}`) + reject-unmapped.
- **AC-2** v2 behaviours: headers, backoff, per-eventType metrics.
- **AC-3** wire preserved: value = `writeValueAsString(ReviewEventMessage)` byte-identical; key = reviewId; row `event_id` == envelope `eventId`.
- **AC-4** `V6` applies on fresh + on top of V1–V5; new entity validates; retained v1 `outbox`/`processed_events` still validate.
- **AC-5** write path persists a `review_outbox` row with non-null UUID `event_id`, correct `occurred_at`/`aggregate_*`, serialized envelope.
- **AC-6** v1 `ReviewOutboxPollingScheduler` removed (grep clean); write path no longer uses lib `OutboxWriter`.
- **AC-7** `:review-service:test` GREEN (Docker-free unit) is the authoritative gate — review-service has **no CI Testcontainers lane** (only order/payment), so the `@Tag("integration")` IT is not CI-run; correctness assured by mirroring the CI-validated master/promotion pattern + unit coverage.

## Related Specs
- `projects/ecommerce-microservices-platform/specs/services/review-service/architecture.md`
- `docs/adr/ADR-MONO-004-shared-messaging-scaffolding.md` § 5

## Related Contracts
- `projects/ecommerce-microservices-platform/specs/contracts/events/review-events.md` (if present) — topics/envelope unchanged.

## Dependencies / Prior Work
- **TASK-BE-444 (promotion-service)** — the in-platform ecommerce v2 reference this mirrors.
- **TASK-BE-438 (wms master-service)** — the CI-validated Postgres v2 reference.

## Edge Cases
- EntityScan / retained v1 tables (`outbox` + `processed_events`, both in V4) — kept for `ddl-auto=validate`.
- `event_id` is already a UUID on `ReviewEvent`; reused as the row PK (header == payload id). No payload mutation.
- Cutover: in-flight v1 `outbox` rows abandoned (no live consumer, re-derivable).
- No CI IT lane — verification via unit + schema-mirror (AC-7).

## Failure Scenarios
- **F1** in-flight v1 rows abandoned — acceptable (no live consumer).
- **F2** dropping `outbox`/`processed_events` → validate boot failure — mitigated by retaining them.
- **F3** wire drift — mitigated by unchanged `ReviewEventMessage` serialization + exact topic switch + key=reviewId, unit-asserted.
