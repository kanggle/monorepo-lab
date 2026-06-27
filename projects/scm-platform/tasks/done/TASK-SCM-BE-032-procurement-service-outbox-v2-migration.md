# TASK-SCM-BE-032 (scm) — Migrate procurement-service outbox v1 → v2 (AbstractOutboxPublisher)

**Status:** done

**Type:** TASK-SCM-BE
**Analysis model:** Opus 4.8 / **Recommended impl model:** Opus (dual-axis schema migration + write-path port/adapter split + relay swap with a preserved custom failure metric + a preserved gate; producer with a CI Testcontainers lane)

**Service:** procurement-service (scm-platform)

> **Origin.** ADR-MONO-004 § 6 follow-up — the **first scm-platform** service migrated to the shared v2 `AbstractOutboxPublisher`, and the **pilot for the dual-axis holdouts** (iam / erp / scm / fan all run BOTH the v1 `BaseEventPublisher` write path AND the v1 `OutboxPollingScheduler` relay). Mirrors the finance-platform `account-service` dual-axis migration (TASK-FIN-BE-045) for the port/adapter split, and the wms `master-service` / ecommerce `promotion-service` Postgres `OutboxRowEntity` pattern for the entity. procurement-service **has a CI Testcontainers `:integrationTest` lane**, so the round-trip is runtime-verified on CI Linux.

---

## Goal

Replace procurement-service's v1 outbox stack (lib `BaseEventPublisher` write path → `OutboxWriter`/`OutboxJpaEntity`; lib `OutboxPollingScheduler` relay) with the shared v2 `AbstractOutboxPublisher`, gaining exponential backoff, `eventId`/`eventType` headers, per-event lag metrics and a UUID natural key — **while preserving the wire + the failure metric + the polling gate exactly**.

**Wire preserved exactly:**
- Topics (all 7, verbatim, with the existing `.v1` suffix): `scm.procurement.po.{submitted,acknowledged,confirmed,canceled,received,closed}.v1` + `scm.procurement.asn.received.v1`.
- Kafka record **value** = the canonical 7-field envelope JSON (`eventId, eventType, source, occurredAt, schemaVersion=1, partitionKey, payload`) built in the same field order the lib `BaseEventPublisher.writeEvent` used — byte-identical. Per-event payload maps copied verbatim.
- Kafka record **key** = `aggregateId` (PO id / ASN id). `partition_key` left null → the v2 publisher falls back to `aggregateId`, exactly as the v1 `kafkaTemplate.send(topic, aggregateId, payload)`.
- Header `eventId`/`eventType` is **additive** (v1 had none); consumers parse the payload JSON / dedupe on it, so consumption is unaffected.
- `procurement_outbox_publish_failures_total` (v1 `onKafkaSendFailure` hook) preserved by wrapping the lib `OutboxMetrics`.
- The relay on/off gate stays `outbox.polling.enabled` (slice/unit tests set it false).

## Scope

**In scope (procurement-service only):**
1. `V3__procurement_outbox_v2.sql` — `procurement_outbox` (Postgres, mirror `master_outbox`; UUID `event_id` PK, `occurred_at`, `retries`/`last_error`; partial pending index). Retain v1 `outbox` (V1) + `processed_events` (V1) — EntityScan validate (V1__init.sql notes `processed_events` is required by the lib EntityScan).
2. `ProcurementOutboxJpaEntity extends OutboxRowEntity` + `ProcurementOutboxJpaRepository` (both under `infrastructure.persistence.jpa` — the `@EnableJpaRepositories`/`@EntityScan` base package; mirrors the payment §27 lesson — entity/repo must live in the scanned package).
3. Convert `ProcurementEventPublisher` (application/event) to a **port interface** (7 `EVENT_*` constants + 6 publish method signatures preserved). New `OutboxProcurementEventPublisher` (infrastructure/outbox) implements it: builds the 7-field envelope (fresh UUIDv7 as `eventId` + row PK, `source=scm-platform-procurement-service`, `schemaVersion=1`, `partitionKey=aggregateId`, payload maps verbatim) and persists a `procurement_outbox` row in the caller's transaction. Callers (`PurchaseOrderApplicationService`) + their unit tests unchanged (interface mock).
4. `ProcurementOutboxPublisher extends AbstractOutboxPublisher<ProcurementOutboxJpaEntity>` (`@ConditionalOnProperty("outbox.polling.enabled")`) — `TopicResolver` switch ported verbatim (7 types + reject-unmapped), `MicrometerOutboxMetrics("procurement")` **wrapped** to also increment `procurement_outbox_publish_failures_total` on a per-event send failure (guarded `eventType != null`), new `procurement.outbox.pending.count` gauge, `@Scheduled`. Delete `ProcurementOutboxPollingScheduler`.
5. `OutboxConfig` — `TransactionTemplate` + `Clock` (system UTC) beans (procurement had neither). Keep lib `OutboxAutoConfiguration` (not excluded).
6. `procurement.outbox.{poll-ms,initial-delay-ms,batch-size}` timing keys (application.yml + application-test.yml); the `outbox.polling.enabled` gate retained (legacy `outbox.polling.interval-ms`/`batch-size` left as inert).
7. Tests: new `ProcurementOutboxPublisherTest` (7-topic resolve/reject, publish round-trip headers/key/value, mark-published, metrics, gauge, backoff, **preserved failure counter**), new `OutboxProcurementEventPublisherTest` (v2 row + canonical envelope for a PO event + an ASN event), **rewrite `OutboxRelayIntegrationTest` to v2** (query `procurement_outbox` via the new repo, `published_at`-based, v2 timing knobs).
8. Update `specs/services/procurement-service/architecture.md` § Outbox.

**Out of scope:** other scm services; the scm `EventDedupePort` rename (ADR-MONO-004 § 6, separate trivial item); the `processed_events` consumer-dedupe table; ADR-MONO-004 § 6 row edit (deferred to a single end-of-series reconciliation across the dual-axis holdouts).

## Acceptance Criteria
- **AC-1** topics preserved (all 7, `.v1`) + reject-unmapped.
- **AC-2** v2 behaviours: headers, backoff, per-eventType metrics + lag.
- **AC-3** wire preserved: value byte-identical (canonical 7-field envelope, field order); key = aggregateId (partition_key null → fallback); row `event_id` == envelope `eventId`.
- **AC-4** `V3` applies on fresh + on top of V1–V2; new entity validates; retained v1 `outbox`/`processed_events` still validate.
- **AC-5** write path persists a `procurement_outbox` row per event with correct fields; `PurchaseOrderApplicationService` + its unit tests unchanged (port mock).
- **AC-6** v1 `ProcurementOutboxPollingScheduler` removed (grep clean); write path no longer uses lib `OutboxWriter`/`BaseEventPublisher`.
- **AC-7 (metric continuity)** `procurement_outbox_publish_failures_total` still increments on a per-event Kafka send failure (verified by unit test).
- **AC-8 (gate continuity)** `outbox.polling.enabled=false` still disables the relay (preserved property name).
- **AC-9 (build + CI IT)** `:procurement-service:test` GREEN (Docker-free unit). The **`:integrationTest`** lane (CI Linux Testcontainers — authoritative) GREEN: the rewritten `OutboxRelayIntegrationTest` round-trip (PO submit → `procurement_outbox` row → relay → Kafka → `published_at` set). Push and let CI validate; do not claim IT-green from local (Docker blocked locally).

## Related Specs
- `projects/scm-platform/specs/services/procurement-service/architecture.md`
- `docs/adr/ADR-MONO-004-shared-messaging-scaffolding.md` § 5/§ 6

## Related Contracts
- `projects/scm-platform/specs/contracts/events/scm-procurement-events.md` — topics + envelopes unchanged.

## Dependencies / Prior Work
- **TASK-FIN-BE-045** — account-service dual-axis (port/adapter split) reference.
- **TASK-BE-438 / TASK-BE-444** — Postgres `OutboxRowEntity` v2 reference (master / promotion).

## Edge Cases
- **Dual-axis**: both the v1 write path (`BaseEventPublisher`) and relay (`OutboxPollingScheduler`) are replaced; the port/adapter split keeps the application layer + caller tests untouched.
- **Custom failure metric**: v1 `onKafkaSendFailure` hook preserved by wrapping the lib `OutboxMetrics` (guard on `eventType != null`).
- **EntityScan / keep-auto-config**: procurement has both `outbox` + `processed_events`; `V1__init.sql` documents `processed_events` as required by the lib EntityScan. So `OutboxAutoConfiguration` is **retained** (not excluded) and the v1 tables stay — the ecommerce keep-auto-config stance, not the account exclude stance.
- **Scan package**: the v2 entity/repo live in `infrastructure.persistence.jpa` (the app's `@EnableJpaRepositories`/`@EntityScan` base) so they register — mock-repo unit tests don't catch a missing scan, only the full-boot IT does (payment §27 lesson).
- **`po.closed` topic**: `EVENT_PO_CLOSED` + its `.v1` topic mapping are preserved verbatim even though no publish method currently emits it.
- **Cutover**: in-flight v1 `outbox` rows abandoned (re-derivable in demo/CI).

## Failure Scenarios
- **F1** in-flight v1 rows abandoned — acceptable.
- **F2** dropping `outbox`/`processed_events` → validate boot failure — mitigated by retaining them + keeping `OutboxAutoConfiguration`.
- **F3** wire drift — mitigated by the canonical-envelope write adapter + exact 7-topic switch + key=aggregateId, unit-asserted + CI IT.
- **F4** stale IT querying the v1 `outbox` table → CI integrationTest RED — mitigated by rewriting `OutboxRelayIntegrationTest` to `procurement_outbox` (AC-9).
- **F5** lost custom failure metric / changed gate property → dashboard/alert gap or test break — mitigated by the wrapping `OutboxMetrics` (AC-7) + the preserved `outbox.polling.enabled` gate (AC-8).
