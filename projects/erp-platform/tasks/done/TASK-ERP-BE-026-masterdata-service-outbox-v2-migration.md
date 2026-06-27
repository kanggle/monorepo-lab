# TASK-ERP-BE-026 (erp) — Migrate masterdata-service outbox v1 → v2 (AbstractOutboxPublisher)

**Status:** done

**Type:** TASK-ERP-BE
**Analysis model:** Opus 4.8 / **Recommended impl model:** Opus (dual-axis MySQL schema migration + write-path port/adapter split + relay swap with a preserved custom failure metric + a preserved gate)

**Service:** masterdata-service (erp-platform)

> **Origin.** ADR-MONO-004 § 6 follow-up — the **second erp-platform** service migrated to the shared v2 `AbstractOutboxPublisher` (paired with TASK-ERP-BE-025 approval-service). Mirrors the finance-platform `account-service` **MySQL** dual-axis migration (TASK-FIN-BE-045) for the `CHAR(36)` entity + the port/adapter split, and the scm `procurement-service` migration (TASK-SCM-BE-032) for the relay + the preserved failure metric.

---

## Goal

Replace masterdata-service's v1 outbox stack (lib `BaseEventPublisher` write path → `OutboxWriter`/`OutboxJpaEntity`; lib `OutboxPollingScheduler` relay) with the shared v2 `AbstractOutboxPublisher`, gaining exponential backoff, `eventId`/`eventType` headers, per-event lag metrics and a UUID natural key — **while preserving the wire + the failure metric + the polling gate exactly**.

**Wire preserved exactly:**
- Topics (all five, verbatim, with the existing `.v1` suffix): `erp.masterdata.{department,employee,jobgrade,costcenter,businesspartner}.changed.v1`.
- Kafka record **value** = the canonical 7-field envelope JSON (`eventId, eventType, source, occurredAt, schemaVersion=1, partitionKey, payload`) built in the same field order the lib `BaseEventPublisher.writeEvent` used — byte-identical. Per-event payload maps copied verbatim, **including the `before`/`after`/`reason` keys written UNCONDITIONALLY** (a null serialises as JSON null — the exact v1 shape; no NON_NULL omission).
- Kafka record **key** = `aggregateId`. `partition_key = aggregateId` (NOT NULL — the account MySQL precedent).
- Header `eventId`/`eventType` is **additive** (v1 had none); consumers parse the payload JSON / dedupe on it.
- `masterdata_outbox_publish_failures_total` (v1 `onKafkaSendFailure` hook — name + description verbatim) preserved by wrapping the lib `OutboxMetrics`.
- The relay on/off gate stays `outbox.polling.enabled`.

## Scope

**In scope (masterdata-service only):**
1. `V2__masterdata_outbox_v2.sql` — `masterdata_outbox` (MySQL, mirror account `account_outbox` MINUS `event_version`; `id CHAR(36)` PK, `aggregate_type`/`aggregate_id`/`event_type`/`payload TEXT`/`partition_key NOT NULL`/`created_at`/`published_at`; `idx_masterdata_outbox_unpublished (published_at, created_at)`; InnoDB utf8mb4). Retain v1 `outbox` + `processed_events` — EntityScan validate (KEEP-auto-config).
2. `MasterdataOutboxJpaEntity implements OutboxRow` (`CHAR(36)` UUID via `@JdbcTypeCode(SqlTypes.CHAR)`) + `MasterdataOutboxJpaRepository` (both under `infrastructure.persistence.jpa` — the `JpaConfig` `@EntityScan`/`@EnableJpaRepositories` base; payment §27 lesson).
3. Convert `MasterdataEventPublisher` (application/event) to a **port interface** (all `EVENT_*` + `AGG_*` constants + the `ChangeKind` enum + all five publish method signatures preserved). New `OutboxMasterdataEventPublisher` (infrastructure/outbox) implements it: builds the 7-field envelope (fresh UUIDv7 = `eventId` + row PK, `source=erp-platform-masterdata-service`, `schemaVersion=1`, `partitionKey=aggregateId`, payload maps verbatim) and persists a `masterdata_outbox` row in the caller's transaction. Callers (`MasterdataApplicationService`) + their unit tests unchanged (interface mock).
4. `MasterdataOutboxPublisher extends AbstractOutboxPublisher<MasterdataOutboxJpaEntity>` (`@ConditionalOnProperty("outbox.polling.enabled")`) — `TopicResolver` switch ported verbatim (5 types + reject-unmapped), `MicrometerOutboxMetrics("masterdata")` **wrapped** to also increment `masterdata_outbox_publish_failures_total` on a per-event send failure (guarded `eventType != null`), new `masterdata.outbox.pending.count` gauge, `@Scheduled`. Delete `MasterdataOutboxPollingScheduler`.
5. `OutboxConfig` — `TransactionTemplate` bean only (Clock already from `ClockConfig`). Keep lib `OutboxAutoConfiguration` (not excluded).
6. `masterdata.outbox.{poll-ms,initial-delay-ms,batch-size}` timing keys (application.yml + application-test.yml); the `outbox.polling.enabled` gate retained.
7. Tests: new `MasterdataOutboxPublisherTest` (5-topic resolve/reject, publish round-trip headers/key/value, mark-published, metrics, gauge, backoff, preserved failure counter), new `OutboxMasterdataEventPublisherTest` (v2 row + canonical envelope for a department CREATED + RETIRED event, asserting the unconditional before/after/reason keys). (No existing IT queries the `outbox` table — masterdata ITs assert `audit_log` rows — so no IT outbox-table rewrite is required; the existing `MasterdataApplicationServiceTest` mocks the `MasterdataEventPublisher` port and is unchanged.)
8. Update `specs/services/masterdata-service/architecture.md` § Outbox.

**Out of scope:** other erp services (approval = TASK-ERP-BE-025); activating the relay (no `@EnableScheduling` is added — dormant-relay preserved, see Edge Cases); the v1 `outbox`/`processed_events` table drop; ADR-MONO-004 § 6 row edit (deferred to an end-of-series reconciliation).

## Acceptance Criteria
- **AC-1** all five topics preserved (`.v1`) + reject-unmapped.
- **AC-2** v2 behaviours: headers, backoff, per-eventType metrics + lag.
- **AC-3** wire preserved: value byte-identical (canonical 7-field envelope, field order; `before`/`after`/`reason` written unconditionally as JSON null); key = aggregateId (partition_key = aggregateId NOT NULL); row `id` == envelope `eventId`.
- **AC-4** `V2` applies on fresh + on top of V1; new entity validates; retained v1 `outbox`/`processed_events` still validate.
- **AC-5** write path persists a `masterdata_outbox` row per event with correct fields; `MasterdataApplicationService` + its unit tests unchanged (port mock).
- **AC-6** v1 `MasterdataOutboxPollingScheduler` removed (grep clean); write path no longer uses lib `OutboxWriter`/`BaseEventPublisher`.
- **AC-7 (metric continuity)** `masterdata_outbox_publish_failures_total` still increments on a per-event Kafka send failure (unit-asserted).
- **AC-8 (gate continuity)** `outbox.polling.enabled=false` still disables the relay (preserved property name).
- **AC-9 (build)** `:masterdata-service:test` GREEN (Docker-free unit; also compiles the integrationTest sources, which share src/test). The Testcontainers `:integrationTest` suite runs on CI Linux (authoritative; Docker blocked locally — do not claim IT-green locally).

## Related Specs
- `projects/erp-platform/specs/services/masterdata-service/architecture.md`
- `docs/adr/ADR-MONO-004-shared-messaging-scaffolding.md` § 5/§ 6

## Related Contracts
- `projects/erp-platform/specs/contracts/events/erp-masterdata-events.md` — topics + envelopes unchanged.

## Dependencies / Prior Work
- **TASK-FIN-BE-045** — account-service MySQL dual-axis (`CHAR(36)` entity + port/adapter split) reference.
- **TASK-SCM-BE-032** — procurement-service relay + preserved-failure-metric reference.

## Edge Cases
- **Dual-axis**: both the v1 write path (`BaseEventPublisher`) and relay (`OutboxPollingScheduler`) are replaced; the port/adapter split keeps the application layer + caller tests untouched.
- **Unconditional payload keys**: the v1 `MasterdataEventPublisher.payload` `put`s `before`/`after`/`reason` UNCONDITIONALLY (null → JSON null) — the v2 adapter copies this verbatim (NOT a NON_NULL omission), preserving the exact wire.
- **Dormant relay preserved**: masterdata-service has NO `@EnableScheduling` (none under v1 either), so the `@Scheduled` relay never runs — events are written to `masterdata_outbox` but not drained until scheduling is activated (a separate, out-of-scope concern). Behaviour is preserved.
- **MySQL `CHAR(36)`**: the UUIDv7 PK is stored as its 36-char canonical string via `@JdbcTypeCode(SqlTypes.CHAR)` (account precedent), NOT Postgres `OutboxRowEntity`.
- **EntityScan / keep-auto-config**: `OutboxAutoConfiguration` is RETAINED; the v1 `outbox` + `processed_events` tables stay (validate).
- **Scan package**: the v2 entity/repo live in `infrastructure.persistence.jpa` (the app scan base) so they register — mock-repo unit tests don't catch a missing scan, only the full-boot IT does (payment §27 lesson).
- **Cutover**: in-flight v1 `outbox` rows abandoned (re-derivable in demo/CI).

## Failure Scenarios
- **F1** in-flight v1 rows abandoned — acceptable.
- **F2** dropping `outbox`/`processed_events` → validate boot failure — mitigated by retaining them + keeping `OutboxAutoConfiguration`.
- **F3** wire drift — mitigated by the canonical-envelope write adapter + exact 5-topic switch + key=aggregateId + the unconditional before/after/reason keys, unit-asserted + CI IT.
- **F4** stale IT querying the v1 `outbox` table → CI integrationTest RED — N/A here (masterdata ITs assert `audit_log`, not the outbox table), but verified by grep during impl.
- **F5** lost custom failure metric / changed gate property → dashboard/alert gap or test break — mitigated by the wrapping `OutboxMetrics` (AC-7) + the preserved `outbox.polling.enabled` gate (AC-8).
