# TASK-BE-444 (ecommerce) — Migrate promotion-service outbox v1 → v2 (AbstractOutboxPublisher)

**Status:** done

**Type:** TASK-BE
**Analysis model:** Opus 4.8 / **Recommended impl model:** Opus (schema migration + write-path rewrite + publisher/metrics swap of an at-least-once delivery component)

**Service:** promotion-service (ecommerce-microservices-platform)

> **Origin.** ADR-MONO-004 § 6 "Outstanding follow-ups", row "ecommerce services". promotion-service is the **first** ecommerce service migrated off the v1 outbox stack (lib `OutboxPollingScheduler` relay + lib `OutboxWriter`/`OutboxJpaEntity` write path) to the shared v2 `AbstractOutboxPublisher<R extends OutboxRow>` introduced in TASK-MONO-049 / ADR-MONO-004 § 5. It is the **lowest-blast-radius** ecommerce producer (its two events — `CouponUsed`, `CouponExpired` — have **no live consumer** per `promotion-events.md`), so it establishes the **in-platform v2 reference** the remaining ecommerce migrations (review/shipping/settlement/order/payment) mirror.
>
> **Correction to ADR-MONO-004 § 6.** The ADR notes ecommerce services run "BOTH the v1 `OutboxPollingScheduler` relay AND a `BaseEventPublisher` write path". That is inaccurate for the ecommerce producers: they use the lib **`OutboxWriter`** write path (not `BaseEventPublisher`). This is *simpler* than the master/account dual-axis shape — there is no per-service envelope builder to port; the write path is a single `outboxWriter.save(aggregateType, aggregateId, eventType, payload)` call.

---

## Goal

Replace promotion-service's v1 outbox stack with the shared v2 `AbstractOutboxPublisher`, gaining the v2 behaviours its peers in other platforms already have:

- **Exponential backoff** across failed ticks (1s → 2s → 4s → … → 30s cap) instead of v1's "break the batch, retry next fixed tick" with no backoff.
- **`eventId` + `eventType` Kafka record headers** (additive — promotion has no live consumer, so this is pure upside and breaks nothing).
- **Per-event publish-lag metrics** via the lib `MicrometerOutboxMetrics` (eventType-tagged success/failure + lag).
- **A `UUID eventId` natural key** (dedupe-friendly) instead of the v1 `BIGSERIAL id` + `status` string.

**Behaviour that MUST be preserved exactly (wire):**
- Topic mapping `CouponUsed → promotion.coupon.used`, `CouponExpired → promotion.coupon.expired` (NO `.v1` suffix — the existing topics have none; preserve verbatim).
- The Kafka record **value** (payload) — the full event-envelope JSON (`event_id`/`event_type`/`occurred_at`/`source`/`tenant_id`/`payload`) produced by `objectMapper.writeValueAsString(event)`. Stored byte-identically in the outbox row payload.
- The Kafka record **key** = `aggregateId` = `couponId` (v2 falls back to `aggregateId` when `partitionKey` is null).
- At-least-once delivery, FIFO ordering, the `tenant_id` envelope semantics (M5).

## Scope

**In scope (promotion-service only — `projects/ecommerce-microservices-platform/apps/promotion-service/`):**

1. **Flyway migration `V7__promotion_outbox_v2.sql`** — create the v2-shaped `promotion_outbox` table (mirror the validated `master_outbox` from TASK-BE-438; Postgres): `event_id UUID PK`, `event_type VARCHAR(100)`, `aggregate_type VARCHAR(60)`, `aggregate_id VARCHAR(60)`, `partition_key VARCHAR(60)`, `payload TEXT`, `occurred_at TIMESTAMP`, `published_at TIMESTAMP`, `retries INT DEFAULT 0`, `last_error TEXT`; partial index `WHERE published_at IS NULL ORDER BY occurred_at`. Retain (DO NOT DROP) the v1 `outbox` + `processed_events` tables — see Edge Case "EntityScan".
2. **New entity** `PromotionOutboxEntity extends OutboxRowEntity` (`@Entity @Table("promotion_outbox")`) + Spring Data repo `PromotionOutboxRepository extends JpaRepository<…, UUID>` with `findPending(Pageable)` (`WHERE publishedAt IS NULL ORDER BY occurredAt ASC`) + `countByPublishedAtIsNull()`.
3. **Rewrite the write path.** `SpringPromotionEventPublisher` (implements the `PromotionEventPublisher` port) currently calls `outboxWriter.save("Coupon", couponId, eventType, payload)`. Rewrite it to persist a `PromotionOutboxEntity` via `PromotionOutboxRepository`: `event_id = UUID.fromString(event.eventId())` (reuse the domain event's own envelope `event_id` so the Kafka header `eventId` matches the payload `event_id` — unified, no new id), `occurred_at = Instant.parse(event.occurredAt())`, `aggregate_type = "Coupon"`, `aggregate_id = couponId`, `partition_key = null`, `payload = objectMapper.writeValueAsString(event)` (unchanged → wire-preserving). The port interface is unchanged.
4. **Replace the publisher.** Delete `OutboxPollingScheduler` (the v1 `extends com.example.messaging.outbox.OutboxPollingScheduler`) and add a thin `PromotionOutboxPublisher extends AbstractOutboxPublisher<PromotionOutboxEntity>` (mirror `MasterOutboxPublisher`): wrapped repo via `SpringDataOutboxRowRepository.wrap`, `KafkaTemplate`, `TransactionTemplate`, a `TopicResolver` (`CouponUsed`/`CouponExpired` switch — ported verbatim incl. reject-unmapped), `MicrometerOutboxMetrics(registry, "promotion")`, the existing `Clock` bean, a `batch-size` property; `@Scheduled` `publishPending()` override. **No `@Profile`** — the v1 scheduler ran unconditionally; preserve.
5. **Config.** New `OutboxConfig` with a `TransactionTemplate` bean (the `Clock clock()` bean already exists in `ClockConfig` — reuse it). Keep the lib `OutboxAutoConfiguration` (do **not** exclude): its `OutboxJpaConfig` EntityScan keeps the retained v1 `outbox`/`processed_events` tables required under `ddl-auto=validate` (the master-service stance, CI-validated). The lib `OutboxWriter`/`OutboxPublisher` beans remain registered but unreferenced.
6. **Metrics.** `promotion.outbox.publish.success.total` / `.failure.total` / `.lag.seconds` (lib names) + a preserved `promotion.outbox.pending.count` gauge.
7. **Config keys.** Add `promotion.outbox.*` (`batch-size`, `poll-ms`, `initial-delay-ms`). The old `outbox.polling.*` keys stay (still bound by the retained lib beans; harmless).
8. **Tests.** `PromotionOutboxPublisherTest` (Docker-free unit): topic resolution incl. reject-unmapped, publish round-trip (mapped topic + `eventId`/`eventType` headers + key=aggregateId + value=payload), mark-published, `promotion`-prefixed metrics, pending-count gauge, kafka-failure backoff. `SpringPromotionEventPublisherTest` (mock repo): a `publishCouponUsed`/`publishCouponExpired` persists a `promotion_outbox` row with the reused `event_id`, parsed `occurred_at`, and **byte-identical payload**. A `@Tag("integration")` round-trip IT is authored for when a CI lane exists (see AC-7).
9. **Specs/ADR.** Update `specs/services/promotion-service/architecture.md` § Outbox (table `promotion_outbox`, publisher `PromotionOutboxPublisher`). Update **ADR-MONO-004 § 6** — split the ecommerce row to mark promotion-service done + correct the "BaseEventPublisher" note (ecommerce = `OutboxWriter`-based).

**Out of scope:**
- The other ecommerce services (review/shipping/settlement/order/payment) — separate tasks (this is the reference).
- user-service / product-service — those are **direct-Kafka** (no outbox at all); a separate gap-fill, not a v1→v2 swap.
- Changing the `processed_events` table (promotion is producer-only for these events; the table backs the `OrderCancelled` consumer dedupe — leave as-is).
- Any new promotion events / topic renames — pure infra swap.

## Acceptance Criteria

- **AC-1 (behaviour-preserving topics)** — `CouponUsed → promotion.coupon.used`, `CouponExpired → promotion.coupon.expired`; an unmapped eventType is rejected (`IllegalArgumentException`). Proven by the ported topic-resolver test.
- **AC-2 (v2 behaviours present)** — published records carry `eventId` + `eventType` headers; repeated transient Kafka failures back off exponentially (deterministic injected `Clock`); per-eventType lag/success/failure metrics emitted.
- **AC-3 (wire preserved)** — Kafka record **value** = `objectMapper.writeValueAsString(event)` byte-identical to v1; **key** = `couponId`; the payload envelope (`event_id`/`event_type`/`occurred_at`/`source`/`tenant_id`/`payload`) is unchanged. The row's `event_id` PK equals the envelope `event_id`.
- **AC-4 (schema + migration)** — `V7__promotion_outbox_v2.sql` applies cleanly on a fresh DB and on top of V1–V6; the new entity matches the table; the retained v1 `outbox`/`processed_events` tables still validate against the still-EntityScanned lib entities (`ddl-auto=validate`).
- **AC-5 (write path)** — `publishCouponUsed`/`publishCouponExpired` persist a `promotion_outbox` row with non-null UUID `event_id`, correct `occurred_at`/`aggregate_*`, and the serialized envelope payload.
- **AC-6 (no v1 residue)** — the v1 `OutboxPollingScheduler` subclass is removed (grep clean); promotion no longer references lib `OutboxWriter` for its write path; no service code instantiates a v1 `OutboxPollingScheduler`.
- **AC-7 (build + verification reality)** — `:projects:ecommerce-microservices-platform:apps:promotion-service:test` GREEN (Docker-free unit) is the authoritative gate **for this service**. NOTE: ecommerce CI runs Testcontainers `:integrationTest` only for order-service + payment-service (per the local Testcontainers blocker memo); promotion-service has **no CI IT lane**, so the `@Tag("integration")` round-trip IT will not execute in CI and is NOT claimed verified. The schema/mapping correctness is assured by mirroring the CI-validated `master_outbox` (TASK-BE-438) exactly + unit coverage of the resolver/publish/write paths. State this honestly in the PR.
- **AC-8 (ADR updated)** — ADR-MONO-004 § 6 ecommerce row updated (promotion struck/marked done; "BaseEventPublisher" note corrected to `OutboxWriter`).

## Related Specs
- `projects/ecommerce-microservices-platform/specs/services/promotion-service/architecture.md` — § Outbox.
- `docs/adr/ADR-MONO-004-shared-messaging-scaffolding.md` § 5 (v2 pattern) + § 6 (this follow-up row).

## Related Contracts
- `projects/ecommerce-microservices-platform/specs/contracts/events/promotion-events.md` — topics + envelope are **unchanged** (infra swap). Re-confirm `promotion.coupon.used`/`promotion.coupon.expired` match the new `TopicResolver`.

## Dependencies / Prior Work
- **TASK-BE-438 (wms master-service)** — the Postgres v2 reference this mirrors (`master_outbox`, `MasterOutboxEntity extends OutboxRowEntity`, `MasterOutboxPublisher`, `OutboxConfig`, retained-v1-tables stance).
- **libs/java-messaging** v2 types: `AbstractOutboxPublisher`, `OutboxRow`, `OutboxRowEntity`, `OutboxRowRepository`, `SpringDataOutboxRowRepository.wrap`, `TopicResolver`, `MicrometerOutboxMetrics`.

## Edge Cases
- **EntityScan / retained v1 tables.** The lib registers `OutboxJpaEntity` (table `outbox`) + `ProcessedEventJpaEntity` (table `processed_events`) in its EntityScan via the retained `OutboxAutoConfiguration`; with `ddl-auto=validate` both tables MUST remain present. V7 ADDS `promotion_outbox` and leaves `outbox` (now unused) + `processed_events` (still the `OrderCancelled`-consumer dedupe table) in place.
- **Two event ids.** The domain event already carries its own envelope `event_id` (random UUID, in the payload JSON). The v2 row PK reuses that exact UUID (`UUID.fromString(event.eventId())`) so the Kafka header `eventId` matches the payload `event_id` — no second id, no payload mutation.
- **Cutover / in-flight rows.** Unpublished rows in the v1 `outbox` at deploy are no longer polled (poller now reads `promotion_outbox`). Abandoned deliberately — promotion events are low-volume, have no live consumer, and are re-derivable in the demo/CI usage this monorepo targets (F1).
- **No CI IT lane (verification gap).** promotion-service has no Testcontainers lane on CI (only order/payment do). The round-trip is NOT runtime-verified; mitigation = mirror the CI-validated master pattern exactly + thorough Docker-free unit tests + careful schema review (AC-7).

## Failure Scenarios
- **F1 — silent event loss at cutover** — abandoning v1 `outbox` rows. Mitigation: documented + acceptable (no live consumer, re-derivable).
- **F2 — ddl validate boot failure** — dropping `outbox`/`processed_events`. Mitigation: AC-4 + Edge Case "EntityScan" — retain them.
- **F3 — wire drift** — a changed payload/topic/key would break the (future) consumer contract. Mitigation: AC-1 + AC-3 — payload via unchanged `writeValueAsString`, exact topic switch, key=aggregateId; unit-asserted.
- **F4 — claiming IT-green where no lane runs** — promotion has no CI IT lane. Mitigation: AC-7 — unit tests + schema-mirror are the honest gate; IT authored but not claimed.
