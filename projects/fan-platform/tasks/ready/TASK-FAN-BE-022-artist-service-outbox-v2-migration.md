# TASK-FAN-BE-022 (fan) — Migrate artist-service outbox v1 → v2 (AbstractOutboxPublisher)

**Status:** ready

**Type:** TASK-FAN-BE
**Analysis model:** Opus 4.8 / **Recommended impl model:** Opus (dual-axis schema migration + write-adapter rewrite preserving a write-side counter + relay swap with a preserved failure metric; producer with a CI Testcontainers lane)

**Service:** artist-service (fan-platform)

> **Origin.** ADR-MONO-004 § 6 follow-up — fan-platform `artist-service` migrated to the shared v2 `AbstractOutboxPublisher`, mirroring scm `procurement-service` (TASK-SCM-BE-032) and TASK-FAN-BE-020/021. artist-service is **already ports/adapters**: the outbound port `ArtistEventPublisher` is KEPT unchanged; only its adapter is rewritten. It runs BOTH the v1 `BaseEventPublisher` write path AND the v1 `OutboxPollingScheduler` relay (dual-axis), and **has a CI Testcontainers `:integrationTest` lane**.

---

## Goal

Replace artist-service's v1 outbox stack with the shared v2 `AbstractOutboxPublisher`, gaining exponential backoff, `eventId`/`eventType` headers, per-event lag metrics and a UUID natural key — **while preserving the wire + both custom metrics exactly**.

**Wire preserved exactly:**
- Topics (all 6, verbatim, `.v1`): `artist.{registered,published,updated,archived,group_created,group_member_changed}.v1`.
- Kafka record **value** = the canonical 7-field envelope JSON byte-identical to the v1 `BaseEventPublisher.writeEvent`. Per-event payload maps (incl. the `base()` helper and the conditional `reason`/`debutDate` puts) copied verbatim.
- Kafka record **key** = `aggregateId` (artist/group id). `partition_key` left null → v2 fallback.
- Header `eventId`/`eventType` additive.
- `artist_outbox_publish_failures_total` (relay) preserved by wrapping the lib `OutboxMetrics`; `artist_registered_total` (write-side, incremented in `publishArtistRegistered`) preserved verbatim in the rewritten adapter.

## Scope

**In scope (artist-service only):**
1. `V2__artist_outbox_v2.sql` — `artist_outbox` (Postgres, mirror `procurement_outbox`; UUID `event_id` PK, `occurred_at`, `retries`/`last_error`; partial pending index). Retain v1 `outbox` + `processed_events`.
2. `ArtistOutboxJpaEntity extends OutboxRowEntity` + `ArtistOutboxJpaRepository` (both under `adapter.out.persistence` — already covered by the existing `@EnableJpaRepositories`/`@EntityScan`; no JpaConfig edit required).
3. KEEP the `application/port/out/ArtistEventPublisher` port (6 methods + `MemberChangeAction` enum) unchanged. Rewrite the adapter `adapter/out/event/ArtistEventPublisherAdapter` (same class name): remove `extends BaseEventPublisher`, inject (`ArtistOutboxJpaRepository`, `ObjectMapper`, `Clock`, `MeterRegistry`), KEEP the `artist_registered_total` counter, copy all 6 publish bodies VERBATIM (incl. conditional `reason`/`debutDate` puts + `base()`), add the `writeEvent(...)` 7-field envelope helper persisting an `artist_outbox` row. The `EVENT_*` constants stay on the adapter (referenced by the relay). Callers + their unit tests unchanged (port mock).
4. `ArtistOutboxPublisher extends AbstractOutboxPublisher<ArtistOutboxJpaEntity>` (under `adapter.out.messaging`) — `TopicResolver` switch ported verbatim (6 types + reject-unmapped), `MicrometerOutboxMetrics("artist")` **wrapped** to also increment `artist_outbox_publish_failures_total` (guarded `eventType != null`), new `artist.outbox.pending.count` gauge, `@Scheduled`. Unconditional `@Component`. Delete `ArtistOutboxPollingScheduler`.
5. `OutboxConfig` (under `config`) — `TransactionTemplate` bean (a `Clock` bean already exists). Keep lib `OutboxAutoConfiguration`.
6. `artist.outbox.{poll-ms,initial-delay-ms,batch-size}` keys (application.yml + application-test.yml); legacy `outbox.polling.*` left inert.
7. Tests: new `ArtistOutboxPublisherTest` (6-topic resolve/reject, publish round-trip, mark-published, metrics, gauge, backoff, preserved failure counter), new `ArtistEventPublisherAdapterTest` (v2 row + canonical envelope; conditional `reason`/`debutDate` omissions; `artist_registered_total` increment), **re-point the relay IT timing to `artist.outbox.*`** (it reads Kafka directly, so only the property keys change).
8. Update `specs/services/artist-service/architecture.md` § Outbox.

**Out of scope:** other fan services; `processed_events`; ADR-MONO-004 § 6 row edit (deferred to end-of-series reconciliation).

## Acceptance Criteria
- **AC-1** topics preserved (all 6, `.v1`) + reject-unmapped.
- **AC-2** v2 behaviours: headers, backoff, per-eventType metrics + lag.
- **AC-3** wire preserved: value byte-identical (incl. conditional payload omissions); key = aggregateId (partition_key null → fallback); row `event_id` == envelope `eventId`.
- **AC-4** `V2` applies on fresh + on top of V1; new entity validates; retained v1 `outbox`/`processed_events` still validate.
- **AC-5** write adapter persists an `artist_outbox` row per event; the `ArtistEventPublisher` port is unchanged; callers + their unit tests unchanged (port mock).
- **AC-6** v1 `ArtistOutboxPollingScheduler` removed (grep clean); the adapter no longer extends lib `BaseEventPublisher` / uses `OutboxWriter`.
- **AC-7 (metric continuity)** `artist_outbox_publish_failures_total` still increments on a per-event Kafka send failure (unit-asserted); `artist_registered_total` still increments on `publishArtistRegistered` (unit-asserted).
- **AC-8 (build + CI IT)** `:artist-service:test` GREEN (Docker-free unit). The `:integrationTest` lane (CI Linux Testcontainers — authoritative) GREEN: the `OutboxRelayIntegrationTest` round-trip (register artist → `artist.registered.v1` published to Kafka via the v2 relay).

## Related Specs
- `projects/fan-platform/specs/services/artist-service/architecture.md`
- `docs/adr/ADR-MONO-004-shared-messaging-scaffolding.md` § 5/§ 6

## Related Contracts
- `projects/fan-platform/specs/contracts/events/artist-events.md` — topics + envelopes unchanged.

## Dependencies / Prior Work
- **TASK-FAN-BE-020 / TASK-FAN-BE-021** — membership / community Postgres v2 reference (same project pattern).
- **TASK-FIN-BE-045** — account-service port/adapter-split reference (artist is already split, so only the adapter body changes).

## Edge Cases
- **Already ports/adapters**: the port `ArtistEventPublisher` is KEPT; only the adapter body + its deps change. Caller tests (which mock the port) are untouched.
- **Write-side counter**: `artist_registered_total` (a write-path counter, distinct from the relay failure counter) is preserved verbatim in the rewritten adapter.
- **Conditional payload omissions**: `reason` (archived) and `debutDate` (group_created) are only put when non-null — preserved verbatim and unit-asserted.
- **Custom failure metric**: v1 `onKafkaSendFailure` hook preserved by wrapping the lib `OutboxMetrics` (guard `eventType != null`).
- **EntityScan / keep-auto-config**: `OutboxAutoConfiguration` retained; v1 `outbox`/`processed_events` stay; `adapter.out.persistence` already in `@EntityScan`.
- **No relay gate**: v1 scheduler was unconditional `@Component`; v2 keeps that.
- **Cutover**: in-flight v1 `outbox` rows abandoned.

## Failure Scenarios
- **F1** in-flight v1 rows abandoned — acceptable.
- **F2** dropping `outbox`/`processed_events` → validate boot failure — mitigated by retaining them + keeping `OutboxAutoConfiguration`.
- **F3** wire drift (esp. conditional omissions) — mitigated by the canonical-envelope write adapter + exact 6-topic switch + key=aggregateId, unit-asserted (omissions included) + CI IT.
- **F4** relay IT broken by the property rename → CI integrationTest RED — mitigated by re-pointing its `@TestPropertySource` to `artist.outbox.*` (AC-8).
- **F5** lost custom metric (`artist_outbox_publish_failures_total` or `artist_registered_total`) → dashboard/alert gap — mitigated by the wrapping `OutboxMetrics` + the preserved write-side counter (AC-7).
