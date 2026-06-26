# TASK-FIN-BE-045 (finance) — Migrate account-service outbox v1 → v2 (AbstractOutboxPublisher)

**Status:** done

**Type:** TASK-FIN-BE
**Analysis model:** Opus 4.8 / **Recommended impl model:** Opus (schema migration + dual-axis write-path + relay cutover of an at-least-once delivery component in a money-critical, regulated, audit-heavy service — not a routine fix)

**Service:** account-service (finance-platform)

> **Origin.** ADR-MONO-004 § 6 "Outstanding follow-ups" — the v1→v2 outbox migration sweep (master-service closed it for wms under TASK-BE-438). account-service is finance-platform's **last v1 holdout**: its sibling `ledger-service` already runs the v2 `AbstractOutboxPublisher` stack (TASK-FIN-BE-009). Completing account-service makes finance-platform 100% v2 (mirroring wms). account-service is **dual-axis** v1 — it uses BOTH the older `AccountEventPublisher extends BaseEventPublisher` (write path) AND `AccountOutboxPollingScheduler extends OutboxPollingScheduler` (relay).

---

## Goal

Replace account-service's v1 outbox stack with the shared v2 `AbstractOutboxPublisher<R extends OutboxRow>` (`libs/java-messaging`), mirroring the in-platform reference **ledger-service** (`LedgerOutboxPublisher` / `LedgerOutboxJpaEntity` / `OutboxLedgerEventPublisher`). account-service gains the v2 behaviours:

- **Exponential backoff** across failed ticks (1s → 2s → … → 30s cap).
- **`eventId` + `eventType` Kafka record headers** for pre-deserialize dedupe/short-circuit.
- **Per-event publish-lag metrics** via the lib `MicrometerOutboxMetrics` (eventType-tagged success/failure + lag) + a preserved pending-count gauge.
- **A `UUID eventId` natural key** (UUIDv7-sortable, CHAR(36)) instead of the v1 `BIGINT AUTO_INCREMENT id` + `status` string.

**Behaviour that MUST be preserved exactly (this is a money-critical, regulated service — wire compatibility is non-negotiable):**

- The **topic mapping**: every event still publishes to `<eventType>.v1` (the 11 `finance.account.* / finance.balance.* / finance.transaction.* / finance.compliance.*` topics in `finance-account-events.md` § Topics). An unmapped/invalid eventType is still rejected (whitelist).
- The **on-wire envelope JSON shape**: the existing 7-field `BaseEventPublisher` envelope `{eventId, eventType, source, occurredAt, schemaVersion, partitionKey, payload}` with `source = "finance-platform-account-service"` and every payload field/order unchanged. **Do NOT adopt ledger's 8-field envelope** — `ledger-service` consumes `finance.transaction.*` and other consumers parse this exact shape; changing it is a silent break (transactional T8 dedupe is on `eventId`).
- At-least-once delivery, FIFO ordering by occurrence, the Kafka record key = `aggregateId` (= `partitionKey`), and the `outbox.polling.enabled=false` test gate (renamed to `account.outbox.polling.enabled`).

## Scope

**In scope (account-service only — `projects/finance-platform/apps/account-service/`):**

1. **Flyway migration `V2__account_outbox_v2.sql`** — create `account_outbox` mirroring `ledger_outbox` (MySQL 8 / InnoDB / utf8mb4): `id CHAR(36) PK, aggregate_type VARCHAR(60), aggregate_id VARCHAR(64), event_type VARCHAR(80), event_version VARCHAR(10), payload TEXT, partition_key VARCHAR(64), created_at DATETIME(6), published_at DATETIME(6)` + `idx_account_outbox_unpublished (published_at, created_at)`. **Retain** the v1 `outbox` + `processed_events` tables (do NOT drop — see Edge Case "EntityScan / cutover").
2. **New entity** `AccountOutboxJpaEntity implements OutboxRow` (`@Table("account_outbox")`, `id` UUID `@JdbcTypeCode(SqlTypes.CHAR) CHAR(36)`) + **repo** `AccountOutboxJpaRepository extends JpaRepository<…, UUID>` with `findPending(Pageable)` (`published_at IS NULL ORDER BY created_at ASC`) and `countByPublishedAtIsNull()` — the exact ledger shape consumed by `SpringDataOutboxRowRepository.wrap`. Place under `infrastructure.outbox`.
3. **Write path (port + impl, mirroring ledger).** Convert `AccountEventPublisher` (application.event) into an **interface** (port) carrying the 11 `EVENT_*` constants + the 11 `publish*` methods. Move the envelope/payload building into a new `@Component OutboxAccountEventPublisher` (infrastructure.outbox) implementing it — it builds the **exact same 7-field envelope** (preserved) and persists an `AccountOutboxJpaEntity` via the repo (generate `UUID eventId = UuidV7.randomUuid()`, reuse it as both the envelope `eventId` and the row PK; `created_at`/`occurredAt = clock.instant()`). Constructor deps: `(AccountOutboxJpaRepository, ObjectMapper, Clock)`. All call sites (`AccountApplicationService`, `ComplianceFailureRecorder`) and the `@Mock AccountEventPublisher` tests are unaffected (interface mock).
4. **Relay.** Delete `AccountOutboxPollingScheduler extends OutboxPollingScheduler`; add `AccountOutboxPublisher extends AbstractOutboxPublisher<AccountOutboxJpaEntity>` (mirror `LedgerOutboxPublisher`): wrapped repo, `KafkaTemplate`, `TransactionTemplate`, a `TopicResolver` mapping `eventType → eventType + ".v1"` **with the v1 whitelist preserved** (reject unknown eventType, throw `IllegalArgumentException`), `MicrometerOutboxMetrics(meterRegistry, "account")`, `Clock`, `@Value batchSize`; `@Scheduled` override; `@ConditionalOnProperty("account.outbox.polling.enabled", matchIfMissing=true)`; preserved `account.outbox.pending.count` gauge.
5. **Config rewiring** (mirror ledger): add `OutboxConfig` providing `TransactionTemplate`; `@SpringBootApplication(exclude = {OutboxAutoConfiguration.class, OutboxMetricsAutoConfiguration.class})` + `@EnableScheduling` on `AccountServiceApplication`; broaden `JpaConfig` `@EntityScan`/`@EnableJpaRepositories` to include `infrastructure.outbox`; `application.yml` / `application-test.yml` property namespace `outbox.polling.*` → `account.outbox.*` (`polling.enabled`, `polling-interval-ms`, `initial-delay-ms`, `batch-size`). `Clock` bean already exists (`ClockConfig`).
6. **Metrics swap.** Replace the bespoke `account_outbox_publish_failures_total` counter with the lib `MicrometerOutboxMetrics` (prefix `account` → `account.outbox.publish.success.total` / `.failure.total` eventType-tagged + `account.outbox.lag.seconds`) + the preserved `account.outbox.pending.count` gauge. Document the rename of the failure counter (name changes from `account_outbox_publish_failures_total` to `account.outbox.publish.failure.total`).
7. **Tests.** New `OutboxAccountEventPublisherTest` (envelope shape + row fields preserved per event) + `AccountOutboxPublisherTopicTest` (topic mapping + reject-unknown) + an outbox round-trip integration test (persist → poll → Kafka → published_at) tagged for the CI Testcontainers lane. Update the `@Mock AccountEventPublisher` tests only if signatures shift (they should not).
8. **Specs / contract / ADR.** Update `specs/services/account-service/architecture.md` (§Outbox invariants, §Layer Structure, §Metrics, §Trait-compliance) and `specs/contracts/events/finance-account-events.md` (§intro publisher class names) to the v2 stack. Strike/annotate **ADR-MONO-004 § 6** (add account-service to the migrated set; finance now 100% v2).

**Out of scope:**
- Changing the event SET, payloads, topics, or envelope wire shape (pure infra swap).
- ledger-service / other finance services (already v2 or n/a).
- Dropping the retained v1 `outbox` / `processed_events` tables (separate later cleanup).

## Acceptance Criteria

- **AC-1 (behaviour-preserving topics)** — every event still publishes to `<eventType>.v1`; unknown eventType rejected (whitelist). Proven by `AccountOutboxPublisherTopicTest`.
- **AC-2 (behaviour-preserving envelope)** — the persisted/published payload is byte-identical in shape to the v1 `BaseEventPublisher` 7-field envelope (`eventId, eventType, source, occurredAt, schemaVersion, partitionKey, payload`), `source` unchanged, per-event payload unchanged. Proven by `OutboxAccountEventPublisherTest`.
- **AC-3 (v2 behaviours)** — records carry `eventId` + `eventType` headers; transient Kafka failures back off exponentially; per-eventType lag/success/failure metrics + pending gauge emitted.
- **AC-4 (schema + migration)** — `V2__account_outbox_v2.sql` applies cleanly on a fresh DB and on top of V1; `ddl-auto=validate` passes (new entity matches; retained v1 tables no longer EntityScanned once the lib auto-config is excluded, so they are simply present-and-unused).
- **AC-5 (write path)** — a fund-movement mutation persists an `account_outbox` row with non-null `id` (UUID), correct `created_at`, `aggregate_*`, and the serialized 7-field envelope; the relay publishes it and sets `published_at`.
- **AC-6 (no v1 residue)** — `AccountOutboxPollingScheduler`, the `extends BaseEventPublisher` write path, the bespoke failure counter, and any lib `OutboxWriter`/`OutboxJpaEntity` use are removed from account-service (grep clean); lib `OutboxAutoConfiguration` + `OutboxMetricsAutoConfiguration` excluded.
- **AC-7 (build + IT)** — `:projects:finance-platform:apps:account-service:test` GREEN locally (Docker-free unit/slice). The account-service Testcontainers integration suite (outbox round-trip + migration apply) GREEN on the **CI Linux runner** (authoritative per the local Rancher/Testcontainers blocker). Push and let CI validate; do not claim IT-green from local.
- **AC-8 (specs + ADR)** — architecture.md + finance-account-events.md updated to v2; ADR-MONO-004 § 6 annotated (account-service migrated; finance 100% v2).

## Related Specs / Contracts

- `projects/finance-platform/specs/services/account-service/architecture.md` (§Outbox, §Layer Structure, §Metrics).
- `projects/finance-platform/specs/contracts/events/finance-account-events.md` (§Topics — unchanged; §intro publisher classes — update).
- `docs/adr/ADR-MONO-004-shared-messaging-scaffolding.md` § 5 (v2 pattern) + § 6 (follow-up).
- **Reference impl:** `projects/finance-platform/apps/ledger-service/.../infrastructure/outbox/{LedgerOutboxPublisher,LedgerOutboxJpaEntity,LedgerOutboxJpaRepository,OutboxLedgerEventPublisher}.java` + `config/{OutboxConfig,ClockConfig}.java` + `db/migration/V3__create_ledger_outbox.sql`.

## Edge Cases

- **EntityScan / cutover (the subtle one).** Unlike master-service (which kept the lib auto-config and thus had to retain `outbox`/`processed_events`), account-service follows the **ledger stance**: exclude `OutboxAutoConfiguration` (+ `OutboxMetricsAutoConfiguration`). That removes the lib `OutboxJpaEntity`/`ProcessedEventJpaEntity` from the EntityScan, so `ddl-auto=validate` no longer requires the v1 `outbox`/`processed_events` tables. We still **retain** both tables in V2 (do not drop) to keep the migration minimal and low-risk — they become present-but-unused stubs, droppable in a later cleanup. Any unpublished rows left in the v1 `outbox` at cutover are **abandoned** (acceptable: low-volume, re-derivable in demo/fed-e2e/CI). State this disposition in the PR (F1).
- **Envelope fidelity.** The new write path must emit the EXACT v1 envelope (7 fields, `source`, field order). The only intentional change: the envelope `eventId` now equals the row PK (both UUIDv7) — value semantics unchanged (it was already a random UUIDv7 string).
- **Scheduling.** Excluding `OutboxAutoConfiguration` removes the lib's `OutboxSchedulerConfig` scheduling support, so `@EnableScheduling` must be added to `AccountServiceApplication` (ledger precedent).
- **Metric-name continuity.** The bespoke `account_outbox_publish_failures_total` counter is renamed to `account.outbox.publish.failure.total` (lib convention, eventType+reason tagged). If a dashboard references the old name, note the rename. New: `account.outbox.publish.success.total`, `account.outbox.lag.seconds`, `account.outbox.pending.count`.

## Failure Scenarios

- **F1 — silent event loss at cutover** — abandoning unpublished v1 `outbox` rows without documenting it. Mitigation: drain or explicitly accept+document (Edge Case "cutover").
- **F2 — envelope/topic drift** — adopting ledger's 8-field envelope or a different topic mapping would silently break `ledger-service` + other `finance.*` consumers (dedupe on `eventId`). Mitigation: AC-1 + AC-2 preserve the exact wire; tests assert it.
- **F3 — ddl validate boot failure** — mismatched `account_outbox` columns vs entity, or forgetting to exclude the lib auto-config while dropping the v1 tables. Mitigation: AC-4 + Edge Case "EntityScan".
- **F4 — claiming IT-green from a blocked local run** — Testcontainers is blocked on the local Windows host. Mitigation: AC-7 — CI Linux runner is authoritative; push and read CI.
