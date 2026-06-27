# TASK-FAN-BE-020 (fan) — Migrate membership-service outbox v1 → v2 (AbstractOutboxPublisher)

**Status:** ready

**Type:** TASK-FAN-BE
**Analysis model:** Opus 4.8 / **Recommended impl model:** Opus (dual-axis schema migration + write-path port/adapter split + relay swap with a preserved custom failure metric; producer with a CI Testcontainers lane)

**Service:** membership-service (fan-platform)

> **Origin.** ADR-MONO-004 § 6 follow-up — first fan-platform service migrated to the shared v2 `AbstractOutboxPublisher`, mirroring the just-merged scm `procurement-service` (TASK-SCM-BE-032, Postgres `OutboxRowEntity`) and the erp approval/masterdata pattern. membership-service runs BOTH the v1 `BaseEventPublisher` write path AND the v1 `OutboxPollingScheduler` relay (dual-axis). It **has a CI Testcontainers `:integrationTest` lane**, so the round-trip is runtime-verified on CI Linux.

---

## Goal

Replace membership-service's v1 outbox stack (lib `BaseEventPublisher` write path → `OutboxWriter`/`OutboxJpaEntity`; lib `OutboxPollingScheduler` relay) with the shared v2 `AbstractOutboxPublisher`, gaining exponential backoff, `eventId`/`eventType` headers, per-event lag metrics and a UUID natural key — **while preserving the wire + the failure metric exactly**.

**Wire preserved exactly:**
- Topics (all 3, verbatim, with the existing `.v1` suffix): `fan.membership.{activated,canceled,expired}.v1`.
- Kafka record **value** = the canonical 7-field envelope JSON (`eventId, eventType, source, occurredAt, schemaVersion=1, partitionKey, payload`) built in the same field order the lib `BaseEventPublisher.writeEvent` used — byte-identical. Per-event payload maps copied verbatim.
- Kafka record **key** = `aggregateId` (membershipId). `partition_key` left null → the v2 publisher falls back to `aggregateId`, exactly as the v1 `kafkaTemplate.send(topic, aggregateId, payload)`.
- Header `eventId`/`eventType` is **additive** (v1 had none).
- `membership_outbox_publish_failures_total` (v1 `onKafkaSendFailure` hook) preserved by wrapping the lib `OutboxMetrics`.

## Scope

**In scope (membership-service only):**
1. `V3__membership_outbox_v2.sql` — `membership_outbox` (Postgres, mirror `procurement_outbox`; UUID `event_id` PK, `occurred_at`, `retries`/`last_error`; partial pending index). Retain v1 `outbox` + `processed_events` (EntityScan validate).
2. `MembershipOutboxJpaEntity extends OutboxRowEntity` + `MembershipOutboxJpaRepository` (both under `infrastructure.jpa` — the `@EnableJpaRepositories` base; the outbox entity package is ADDED to `JpaConfig` `@EntityScan` so the entity validates — payment §27 lesson).
3. Convert `MembershipEventPublisher` (application/event) to a **port interface** (3 `EVENT_*` constants + 3 publish method signatures preserved). New `OutboxMembershipEventPublisher` (infrastructure/outbox) implements it: builds the 7-field envelope (fresh UUIDv7 as `eventId` + row PK, `source=fan-platform-membership-service`, `schemaVersion=1`, `partitionKey=aggregateId`, payload maps verbatim) and persists a `membership_outbox` row in the caller's transaction. Callers + their unit tests unchanged (interface mock).
4. `MembershipOutboxPublisher extends AbstractOutboxPublisher<MembershipOutboxJpaEntity>` — `TopicResolver` switch ported verbatim (3 types + reject-unmapped), `MicrometerOutboxMetrics("membership")` **wrapped** to also increment `membership_outbox_publish_failures_total` on a per-event send failure (guarded `eventType != null`), new `membership.outbox.pending.count` gauge, `@Scheduled`. Unconditional `@Component` (no gate — matches v1). Delete `MembershipOutboxPollingScheduler`.
5. `OutboxConfig` — `TransactionTemplate` bean (a `Clock` bean already exists in `ClockConfig`). Keep lib `OutboxAutoConfiguration` (not excluded).
6. `membership.outbox.{poll-ms,initial-delay-ms,batch-size}` timing keys (application.yml + application-test.yml); legacy `outbox.polling.*` left inert.
7. Tests: new `MembershipOutboxPublisherTest` (3-topic resolve/reject, publish round-trip headers/key/value, mark-published, metrics, gauge, backoff, **preserved failure counter**), new `OutboxMembershipEventPublisherTest` (v2 row + canonical envelope incl. expired conditional omissions), **re-point the ITs to `membership_outbox`** (relay IT + the ITs asserting outbox rows: `MembershipServiceIntegrationTest`, `IdempotentSubscribeIntegrationTest`, `ExpirySweepIntegrationTest`, `PaymentDeclineIntegrationTest`; base `truncateAll` adds `membership_outbox`).
8. Update `specs/services/membership-service/architecture.md` § Outbox.

**Out of scope:** other fan services; the `processed_events` consumer-dedupe table; ADR-MONO-004 § 6 row edit (deferred to an end-of-series reconciliation across the dual-axis holdouts).

## Acceptance Criteria
- **AC-1** topics preserved (all 3, `.v1`) + reject-unmapped.
- **AC-2** v2 behaviours: headers, backoff, per-eventType metrics + lag.
- **AC-3** wire preserved: value byte-identical (canonical 7-field envelope, field order); key = aggregateId (partition_key null → fallback); row `event_id` == envelope `eventId`.
- **AC-4** `V3` applies on fresh + on top of V1–V2; new entity validates; retained v1 `outbox`/`processed_events` still validate.
- **AC-5** write path persists a `membership_outbox` row per event; callers + their unit tests unchanged (port mock).
- **AC-6** v1 `MembershipOutboxPollingScheduler` removed (grep clean); write path no longer uses lib `OutboxWriter`/`BaseEventPublisher`.
- **AC-7 (metric continuity)** `membership_outbox_publish_failures_total` still increments on a per-event Kafka send failure (verified by unit test).
- **AC-8 (build + CI IT)** `:membership-service:test` GREEN (Docker-free unit). The `:integrationTest` lane (CI Linux Testcontainers — authoritative) GREEN: the rewritten `OutboxRelayIntegrationTest` round-trip (subscribe → `membership_outbox` row → relay → Kafka → `published_at` set). Push and let CI validate; do not claim IT-green from local (Docker blocked locally).

## Related Specs
- `projects/fan-platform/specs/services/membership-service/architecture.md`
- `docs/adr/ADR-MONO-004-shared-messaging-scaffolding.md` § 5/§ 6

## Related Contracts
- `projects/fan-platform/specs/contracts/events/fan-membership-events.md` — topics + envelopes unchanged.

## Dependencies / Prior Work
- **TASK-SCM-BE-032** — procurement-service Postgres `OutboxRowEntity` dual-axis reference (the closest mirror).
- **TASK-BE-438 / TASK-BE-444** — Postgres `OutboxRowEntity` v2 reference (master / promotion).

## Edge Cases
- **Dual-axis**: both the v1 write path (`BaseEventPublisher`) and relay (`OutboxPollingScheduler`) are replaced; the port/adapter split keeps the application layer + caller tests untouched.
- **Custom failure metric**: v1 `onKafkaSendFailure` hook preserved by wrapping the lib `OutboxMetrics` (guard on `eventType != null`).
- **EntityScan / keep-auto-config**: membership has both `outbox` + `processed_events`; `OutboxAutoConfiguration` is **retained** (not excluded) and the v1 tables stay.
- **Scan package**: the v2 entity/repo live in `infrastructure.jpa`; the outbox entity package is ADDED to `JpaConfig` `@EntityScan` so it registers — mock-repo unit tests don't catch a missing scan, only the full-boot IT does (payment §27 lesson).
- **No relay gate**: the v1 scheduler was an unconditional `@Component`; the v2 relay keeps that (no `@ConditionalOnProperty`).
- **Cutover**: in-flight v1 `outbox` rows abandoned (re-derivable in demo/CI).

## Failure Scenarios
- **F1** in-flight v1 rows abandoned — acceptable.
- **F2** dropping `outbox`/`processed_events` → validate boot failure — mitigated by retaining them + keeping `OutboxAutoConfiguration`.
- **F3** wire drift — mitigated by the canonical-envelope write adapter + exact 3-topic switch + key=aggregateId, unit-asserted + CI IT.
- **F4** stale IT querying the v1 `outbox` table → CI integrationTest RED — mitigated by re-pointing every outbox-asserting IT to `membership_outbox` (AC-8).
- **F5** lost custom failure metric → dashboard/alert gap — mitigated by the wrapping `OutboxMetrics` (AC-7).
