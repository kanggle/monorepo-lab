# TASK-FAN-BE-021 (fan) — Migrate community-service outbox v1 → v2 (AbstractOutboxPublisher)

**Status:** done

**Type:** TASK-FAN-BE
**Analysis model:** Opus 4.8 / **Recommended impl model:** Opus (dual-axis schema migration + write-path port/adapter split + relay swap with a preserved custom failure metric; producer with a CI Testcontainers lane)

**Service:** community-service (fan-platform)

> **Origin.** ADR-MONO-004 § 6 follow-up — fan-platform `community-service` migrated to the shared v2 `AbstractOutboxPublisher`, mirroring scm `procurement-service` (TASK-SCM-BE-032, Postgres `OutboxRowEntity`) and TASK-FAN-BE-020 (membership). community-service runs BOTH the v1 `BaseEventPublisher` write path AND the v1 `OutboxPollingScheduler` relay (dual-axis). It **has a CI Testcontainers `:integrationTest` lane** (round-trip runtime-verified on CI Linux).

---

## Goal

Replace community-service's v1 outbox stack with the shared v2 `AbstractOutboxPublisher`, gaining exponential backoff, `eventId`/`eventType` headers, per-event lag metrics and a UUID natural key — **while preserving the wire + the failure metric exactly**.

**Wire preserved exactly:**
- Topics (all 4, verbatim, `.v1`): `community.post.published.v1`, `community.post.status_changed.v1`, `community.comment.added.v1`, `community.reaction.added.v1`.
- Kafka record **value** = the canonical 7-field envelope JSON (`eventId, eventType, source, occurredAt, schemaVersion=1, partitionKey, payload`) byte-identical to the v1 `BaseEventPublisher.writeEvent`. Per-event payload maps (incl. the `base()` helper) copied verbatim.
- Kafka record **key** = `aggregateId` (postId). `partition_key` left null → v2 fallback.
- Header `eventId`/`eventType` additive.
- `community_outbox_publish_failures_total` preserved by wrapping the lib `OutboxMetrics`.

## Scope

**In scope (community-service only):**
1. `V2__community_outbox_v2.sql` — `community_outbox` (Postgres, mirror `procurement_outbox`; UUID `event_id` PK, `occurred_at`, `retries`/`last_error`; partial pending index). Retain v1 `outbox` + `processed_events`.
2. `CommunityOutboxJpaEntity extends OutboxRowEntity` + `CommunityOutboxJpaRepository` (both under `infrastructure.jpa` — already covered by the existing `@EnableJpaRepositories`/`@EntityScan`; no JpaConfig edit required).
3. Convert `CommunityEventPublisher` (application/event) to a **port interface** (4 `EVENT_*` constants + 4 publish signatures preserved). New `OutboxCommunityEventPublisher` (infrastructure/outbox) implements it; builds the 7-field envelope (UUIDv7, `source=fan-platform-community-service`, `schemaVersion=1`, `partitionKey=aggregateId`, payload maps + `base()` verbatim) and persists a `community_outbox` row in the caller's transaction. Callers + their unit tests unchanged.
4. `CommunityOutboxPublisher extends AbstractOutboxPublisher<CommunityOutboxJpaEntity>` — `TopicResolver` switch ported verbatim (4 types + reject-unmapped), `MicrometerOutboxMetrics("community")` **wrapped** to also increment `community_outbox_publish_failures_total` (guarded `eventType != null`), new `community.outbox.pending.count` gauge, `@Scheduled`. Unconditional `@Component`. Delete `CommunityOutboxPollingScheduler`.
5. `OutboxConfig` — `TransactionTemplate` bean (a `Clock` bean already exists). Keep lib `OutboxAutoConfiguration`.
6. `community.outbox.{poll-ms,initial-delay-ms,batch-size}` keys (application.yml + application-test.yml); legacy `outbox.polling.*` left inert.
7. Tests: new `CommunityOutboxPublisherTest` (4-topic resolve/reject, publish round-trip, mark-published, metrics, gauge, backoff, preserved failure counter), new `OutboxCommunityEventPublisherTest` (v2 row + canonical envelope per event), **re-point the ITs to `community_outbox`** (relay IT keeps its Kafka envelope assertions + queries `community_outbox` for `published_at`; `CommunityServiceIntegrationTest`; base `truncateAll` adds `community_outbox`).
8. Update `specs/services/community-service/architecture.md` § Outbox.

**Out of scope:** other fan services; `processed_events`; ADR-MONO-004 § 6 row edit (deferred to end-of-series reconciliation).

## Acceptance Criteria
- **AC-1** topics preserved (all 4, `.v1`) + reject-unmapped.
- **AC-2** v2 behaviours: headers, backoff, per-eventType metrics + lag.
- **AC-3** wire preserved: value byte-identical; key = aggregateId (partition_key null → fallback); row `event_id` == envelope `eventId`.
- **AC-4** `V2` applies on fresh + on top of V1; new entity validates; retained v1 `outbox`/`processed_events` still validate.
- **AC-5** write path persists a `community_outbox` row per event; callers + their unit tests unchanged (port mock).
- **AC-6** v1 `CommunityOutboxPollingScheduler` removed (grep clean); write path no longer uses lib `OutboxWriter`/`BaseEventPublisher`.
- **AC-7 (metric continuity)** `community_outbox_publish_failures_total` still increments on a per-event Kafka send failure (unit-asserted).
- **AC-8 (build + CI IT)** `:community-service:test` GREEN (Docker-free unit). The `:integrationTest` lane (CI Linux Testcontainers — authoritative) GREEN: the rewritten `OutboxRelayIntegrationTest` round-trip (publish post → `community_outbox` row → relay → Kafka envelope → `published_at` set).

## Related Specs
- `projects/fan-platform/specs/services/community-service/architecture.md`
- `docs/adr/ADR-MONO-004-shared-messaging-scaffolding.md` § 5/§ 6

## Related Contracts
- `projects/fan-platform/specs/contracts/events/community-events.md` — topics + envelopes unchanged.

## Dependencies / Prior Work
- **TASK-FAN-BE-020** — membership-service Postgres v2 reference (same project pattern).
- **TASK-SCM-BE-032** — procurement-service dual-axis reference.

## Edge Cases
- **Dual-axis**: both v1 write path + relay replaced; port/adapter split keeps the application layer + caller tests untouched.
- **Custom failure metric**: v1 `onKafkaSendFailure` hook preserved by wrapping the lib `OutboxMetrics` (guard `eventType != null`).
- **EntityScan / keep-auto-config**: `OutboxAutoConfiguration` retained; v1 `outbox`/`processed_events` stay.
- **Scan package**: `infrastructure.jpa` is already in `@EntityScan`/`@EnableJpaRepositories` — entity/repo register without a JpaConfig edit (payment §27 still verified).
- **No relay gate**: v1 scheduler was unconditional `@Component`; v2 keeps that.
- **Cutover**: in-flight v1 `outbox` rows abandoned.

## Failure Scenarios
- **F1** in-flight v1 rows abandoned — acceptable.
- **F2** dropping `outbox`/`processed_events` → validate boot failure — mitigated by retaining them + keeping `OutboxAutoConfiguration`.
- **F3** wire drift — mitigated by the canonical-envelope write adapter + exact 4-topic switch + key=aggregateId, unit-asserted + CI IT (the relay IT still parses the Kafka envelope).
- **F4** stale IT querying the v1 `outbox` table → CI integrationTest RED — mitigated by re-pointing every outbox-asserting IT to `community_outbox` (AC-8).
- **F5** lost custom failure metric → dashboard/alert gap — mitigated by the wrapping `OutboxMetrics` (AC-7).
