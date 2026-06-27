# TASK-ERP-BE-025 (erp) — Migrate approval-service outbox v1 → v2 (AbstractOutboxPublisher)

**Status:** ready

**Type:** TASK-ERP-BE
**Analysis model:** Opus 4.8 / **Recommended impl model:** Opus (dual-axis MySQL schema migration + write-path port/adapter split + relay swap with a preserved custom failure metric + a preserved gate + a contract-aligning delegation-gap fix)

**Service:** approval-service (erp-platform)

> **Origin.** ADR-MONO-004 § 6 follow-up — the **first erp-platform** service migrated to the shared v2 `AbstractOutboxPublisher` (erp/iam/scm/fan/ecommerce were the dual-axis holdouts running BOTH the v1 `BaseEventPublisher` write path AND the v1 `OutboxPollingScheduler` relay). Mirrors the finance-platform `account-service` **MySQL** dual-axis migration (TASK-FIN-BE-045) for the `CHAR(36)` entity + the port/adapter split, and the scm `procurement-service` migration (TASK-SCM-BE-032) for the relay + the preserved failure metric.

---

## Goal

Replace approval-service's v1 outbox stack (lib `BaseEventPublisher` write path → `OutboxWriter`/`OutboxJpaEntity`; lib `OutboxPollingScheduler` relay) with the shared v2 `AbstractOutboxPublisher`, gaining exponential backoff, `eventId`/`eventType` headers, per-event lag metrics and a UUID natural key — **while preserving the wire + the failure metric + the polling gate exactly**, and **fixing a latent v1 delegation topic-mapping gap**.

**Wire preserved exactly:**
- Topics (the four transition topics verbatim, with the existing `.v1` suffix): `erp.approval.{submitted,approved,rejected,withdrawn}.v1`. **Plus** the two delegation topics the contract already defines (see delegation-gap fix): `erp.approval.delegated.v1` + `erp.approval.delegation.revoked.v1`.
- Kafka record **value** = the canonical 7-field envelope JSON (`eventId, eventType, source, occurredAt, schemaVersion=1, partitionKey, payload`) built in the same field order the lib `BaseEventPublisher.writeEvent` used — byte-identical. Per-event payload maps + NON_NULL omissions copied verbatim.
- Kafka record **key** = `aggregateId` (`approvalRequestId` / `grantId`). `partition_key = aggregateId` (NOT NULL — the account MySQL precedent).
- Header `eventId`/`eventType` is **additive** (v1 had none); consumers parse the payload JSON / dedupe on it.
- `approval_outbox_publish_failures_total` (v1 `onKafkaSendFailure` hook — name + description verbatim) preserved by wrapping the lib `OutboxMetrics`.
- The relay on/off gate stays `outbox.polling.enabled`.

## Scope

**In scope (approval-service only):**
1. `V5__approval_outbox_v2.sql` — `approval_outbox` (MySQL, mirror account `account_outbox` MINUS `event_version`; `id CHAR(36)` PK, `aggregate_type`/`aggregate_id`/`event_type`/`payload TEXT`/`partition_key NOT NULL`/`created_at`/`published_at`; `idx_approval_outbox_unpublished (published_at, created_at)`; InnoDB utf8mb4). Retain v1 `outbox` + `processed_events` — EntityScan validate (KEEP-auto-config).
2. `ApprovalOutboxJpaEntity implements OutboxRow` (`CHAR(36)` UUID via `@JdbcTypeCode(SqlTypes.CHAR)`) + `ApprovalOutboxJpaRepository` (both under `infrastructure.persistence.jpa` — the `JpaConfig` `@EntityScan`/`@EnableJpaRepositories` base; payment §27 lesson).
3. Convert `ApprovalEventPublisher` (application/event) to a **port interface** (all `EVENT_*` + `AGGREGATE_TYPE`/`DELEGATION_AGGREGATE_TYPE` constants + the `eventTypeFor` static helper + all six publish method signatures preserved). New `OutboxApprovalEventPublisher` (infrastructure/outbox) implements it: builds the 7-field envelope (fresh UUIDv7 = `eventId` + row PK, `source=erp-platform-approval-service`, `schemaVersion=1`, `partitionKey=aggregateId`, payload maps verbatim) and persists an `approval_outbox` row in the caller's transaction. Callers (`ApprovalApplicationService`, `DelegationApplicationService`) + their unit tests unchanged (interface mock).
4. `ApprovalOutboxPublisher extends AbstractOutboxPublisher<ApprovalOutboxJpaEntity>` (`@ConditionalOnProperty("outbox.polling.enabled")`) — `TopicResolver` switch with reject-unmapped, mapping **all six** event types (delegation-gap fix), `MicrometerOutboxMetrics("approval")` **wrapped** to also increment `approval_outbox_publish_failures_total` on a per-event send failure (guarded `eventType != null`), new `approval.outbox.pending.count` gauge, `@Scheduled`. Delete `ApprovalOutboxPollingScheduler`.
5. `OutboxConfig` — `TransactionTemplate` bean only (Clock already from `ClockConfig`). Keep lib `OutboxAutoConfiguration` (not excluded).
6. `approval.outbox.{poll-ms,initial-delay-ms,batch-size}` timing keys (application.yml + application-test.yml); the `outbox.polling.enabled` gate retained.
7. Tests: new `ApprovalOutboxPublisherTest` (6-topic resolve/reject incl. delegation topics, publish round-trip headers/key/value, mark-published, metrics, gauge, backoff, preserved failure counter), rewrite the v1 publisher unit test to `OutboxApprovalEventPublisherTest` (v2 row + canonical envelope for delegated GLOBAL/REQUEST + revoked), **rewrite the ITs that query the v1 `outbox` table** (`ApprovalLifecycleIntegrationTest`, `DelegationIntegrationTest`) to `approval_outbox`.
8. Update `specs/services/approval-service/architecture.md` § Outbox + the contract delegation-gap note.

**Out of scope:** other erp services (masterdata = TASK-ERP-BE-026); activating the relay (no `@EnableScheduling` is added — dormant-relay preserved, see Edge Cases); the v1 `outbox`/`processed_events` table drop; ADR-MONO-004 § 6 row edit (deferred to an end-of-series reconciliation across the dual-axis holdouts).

## Acceptance Criteria
- **AC-1** four transition topics preserved (`.v1`) + reject-unmapped.
- **AC-2 (delegation-gap fix)** `topicFor` maps ALL SIX event types — incl. `erp.approval.delegated → erp.approval.delegated.v1` and `erp.approval.delegation.revoked → erp.approval.delegation.revoked.v1` (both defined in `erp-approval-events.md`). The v1 scheduler mapped only four, so the delegated/revoked rows were written to the outbox then poison-pilled by the relay's reject-unmapped — a latent v1 head-of-line-blocking bug, now fixed.
- **AC-3** v2 behaviours: headers, backoff, per-eventType metrics + lag.
- **AC-4** wire preserved: value byte-identical (canonical 7-field envelope, field order); key = aggregateId (partition_key = aggregateId NOT NULL); row `id` == envelope `eventId`.
- **AC-5** `V5` applies on fresh + on top of V1–V4; new entity validates; retained v1 `outbox`/`processed_events` still validate.
- **AC-6** write path persists an `approval_outbox` row per event with correct fields; `ApprovalApplicationService` + `DelegationApplicationService` + their unit tests unchanged (port mock).
- **AC-7** v1 `ApprovalOutboxPollingScheduler` removed (grep clean); write path no longer uses lib `OutboxWriter`/`BaseEventPublisher`.
- **AC-8 (metric continuity)** `approval_outbox_publish_failures_total` still increments on a per-event Kafka send failure (unit-asserted).
- **AC-9 (gate continuity)** `outbox.polling.enabled=false` still disables the relay (preserved property name).
- **AC-10 (build)** `:approval-service:test` GREEN (Docker-free unit; also compiles the integrationTest sources, which share src/test). The Testcontainers `:integrationTest` round-trip (write → `approval_outbox` row) runs on CI Linux (authoritative; Docker blocked locally — do not claim IT-green locally).

## Related Specs
- `projects/erp-platform/specs/services/approval-service/architecture.md`
- `docs/adr/ADR-MONO-004-shared-messaging-scaffolding.md` § 5/§ 6

## Related Contracts
- `projects/erp-platform/specs/contracts/events/erp-approval-events.md` — topics + envelopes unchanged; `erp.approval.delegated.v1` + `erp.approval.delegation.revoked.v1` already defined (the delegation-gap fix aligns the relay to the contract).

## Dependencies / Prior Work
- **TASK-FIN-BE-045** — account-service MySQL dual-axis (`CHAR(36)` entity + port/adapter split) reference.
- **TASK-SCM-BE-032** — procurement-service relay + preserved-failure-metric reference.

## Edge Cases
- **Dual-axis**: both the v1 write path (`BaseEventPublisher`) and relay (`OutboxPollingScheduler`) are replaced; the port/adapter split keeps the application layer + caller tests untouched.
- **Delegation-gap**: the v1 relay poison-pilled `delegated`/`delegation.revoked` rows; the v2 `topicFor` maps all six (contract-aligning fix) — also prevents a v2 head-of-line block.
- **Dormant relay preserved**: approval-service has NO `@EnableScheduling` (none under v1 either), so the `@Scheduled` relay never runs — events are written to `approval_outbox` but not drained until scheduling is activated (a separate, out-of-scope concern). The ITs assert only the outbox ROW is written, never Kafka receipt; behaviour is preserved.
- **MySQL `CHAR(36)`**: the UUIDv7 PK is stored as its 36-char canonical string via `@JdbcTypeCode(SqlTypes.CHAR)` (account precedent), NOT Postgres `OutboxRowEntity`.
- **EntityScan / keep-auto-config**: `OutboxAutoConfiguration` is RETAINED; the v1 `outbox` + `processed_events` tables stay (validate).
- **Scan package**: the v2 entity/repo live in `infrastructure.persistence.jpa` (the app scan base) so they register — mock-repo unit tests don't catch a missing scan, only the full-boot IT does (payment §27 lesson).
- **Cutover**: in-flight v1 `outbox` rows abandoned (re-derivable in demo/CI).

## Failure Scenarios
- **F1** in-flight v1 rows abandoned — acceptable.
- **F2** dropping `outbox`/`processed_events` → validate boot failure — mitigated by retaining them + keeping `OutboxAutoConfiguration`.
- **F3** wire drift — mitigated by the canonical-envelope write adapter + exact topic switch + key=aggregateId, unit-asserted + CI IT.
- **F4** stale IT querying the v1 `outbox` table → CI integrationTest RED — mitigated by rewriting `ApprovalLifecycleIntegrationTest` + `DelegationIntegrationTest` to `approval_outbox` (the v2 table has no `status` column; a written row is simply present, pending = `published_at IS NULL`).
- **F5** lost custom failure metric / changed gate property → dashboard/alert gap or test break — mitigated by the wrapping `OutboxMetrics` (AC-8) + the preserved `outbox.polling.enabled` gate (AC-9).
- **F6** delegation events silently dropped (the latent v1 bug) → mitigated by AC-2 (all six topics mapped + unit-asserted).
