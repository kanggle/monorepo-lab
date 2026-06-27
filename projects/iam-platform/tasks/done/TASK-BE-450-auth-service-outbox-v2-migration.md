# TASK-BE-450 (iam) — Migrate auth-service outbox v1 → v2 (AbstractOutboxPublisher)

**Status:** done

**Type:** TASK-BE
**Analysis model:** Opus 4.8 / **Recommended impl model:** Opus (schema migration + write-path port conversion + relay cutover of an at-least-once delivery component in a security-critical identity service — wire compatibility is non-negotiable)

**Service:** auth-service (iam-platform)

> **Origin.** ADR-MONO-004 § 6 / project memory `project_outbox_v2_migration_playbook` — the v1→v2 outbox sweep across the remaining holdouts. iam-platform's auth/account/admin are the iam group-A holdouts. Each is **dual-axis** v1: it uses BOTH `AuthEventPublisher extends BaseEventPublisher` (write path) AND `AuthOutboxPollingScheduler extends OutboxPollingScheduler` (relay). MySQL precedent = finance account-service (TASK-FIN-BE-045) + erp approval-service (TASK-ERP-BE-025).

---

## Goal

Replace auth-service's v1 outbox stack with the shared v2 `AbstractOutboxPublisher<R extends OutboxRow>` (`libs/java-messaging`), mirroring finance account-service + erp approval-service. auth-service gains the v2 behaviours (exponential backoff, `eventId`+`eventType` Kafka headers, per-eventType lag/success/failure metrics + pending gauge, a `UUID eventId` UUIDv7 CHAR(36) natural key).

**Behaviour preserved exactly (wire compatibility — security-service / account-service consume these):**

- **Topics** — iam topics are **bare** (NO `.v1` suffix): the v1 `AuthOutboxPollingScheduler.resolveTopic` maps each `auth.*` eventType to its identically-named topic. Ported VERBATIM, incl. reject-unmapped.
- **On-wire envelope** — the v1 7-field `BaseEventPublisher.writeEvent` shape `{eventId, eventType, source="auth-service", occurredAt, schemaVersion=1, partitionKey, payload}`, every payload field/order + TASK-BE-131 ordering invariants + conditional omissions unchanged.
- At-least-once delivery, FIFO by created-order, Kafka key = `aggregateId` (= `partitionKey`).

## Scope

**In scope (auth-service only — `projects/iam-platform/apps/auth-service/`):**

1. **Flyway migration `V0027__auth_outbox_v2.sql`** — `auth_outbox` mirroring the finance/erp MySQL precedent (InnoDB/utf8mb4): `id CHAR(36) PK, aggregate_type VARCHAR(60), aggregate_id VARCHAR(64), event_type VARCHAR(80), payload TEXT, partition_key VARCHAR(64) NOT NULL, created_at DATETIME(6), published_at DATETIME(6)` + `idx_auth_outbox_unpublished (published_at, created_at)`. **OMIT `event_version`** (iam v1 never carried one). **Retain** the v1 `outbox` + `processed_events` (KEEP-auto-config).
2. **Entity** `infrastructure.persistence.AuthOutboxJpaEntity implements OutboxRow` (`@Table("auth_outbox")`, `id` UUID `@JdbcTypeCode(SqlTypes.CHAR) CHAR(36)`) + **repo** `AuthOutboxJpaRepository extends JpaRepository<…, UUID>` (`findPending(Pageable)` + `countByPublishedAtIsNull()`). Placed in `infrastructure.persistence` — the existing `JpaConfig` `@EntityScan`/`@EnableJpaRepositories` base (no edit needed).
3. **Write path (port + impl).** `application.event.AuthEventPublisher` → **interface** (all method signatures verbatim). New `@Component infrastructure.outbox.OutboxAuthEventPublisher` builds the exact 7-field envelope and persists an `AuthOutboxJpaEntity` (`eventId = UuidV7.randomUuid()` = envelope eventId = row PK; `occurredAt = Instant.now(clock)`). Deps `(AuthOutboxJpaRepository, ObjectMapper, Clock)`. All call sites + `@Mock AuthEventPublisher` use-case tests unaffected (interface mock).
4. **Relay.** Delete `AuthOutboxPollingScheduler`; add `infrastructure.outbox.AuthOutboxPublisher extends AbstractOutboxPublisher<AuthOutboxJpaEntity>` — `@Component`, **NO `@ConditionalOnProperty`** (v1 had none), plain `MicrometerOutboxMetrics(registry,"auth")` (v1 had no failure counter), `auth.outbox.pending.count` gauge, `@Scheduled` override, `static topicFor` = the v1 resolveTopic ported verbatim.
5. **Config.** Add `infrastructure.config.OutboxConfig` providing a `TransactionTemplate` AND a `Clock systemUTC()` (auth had neither). KEEP lib `OutboxAutoConfiguration` (NOT excluded). Do NOT add `@EnableScheduling` (already on `AuthApplication`). `application.yml` / `application-test.yml`: add `auth.outbox.{poll-ms,initial-delay-ms,batch-size}`.
6. **Tests.** New `OutboxAuthEventPublisherTest` (envelope/payload shape + eventId==row.id) + `AuthOutboxPublisherTest` (`topicFor` all 8 + reject; publish round-trip headers/key/value/mark-published/gauge). Delete the v1 `AuthEventPublisherTest` + `AuthOutboxPollingSchedulerTest`. Repoint ITs querying v1 `outbox` → `auth_outbox` (`OAuthLoginIntegrationTest`) + lib-repo autowire → `AuthOutboxJpaRepository` (`DeviceSessionIntegrationTest`, `OutboxRelayIntegrationTest`).
7. **Specs.** `specs/services/auth-service/architecture.md` § Outbox (v2).

**Out of scope:** changing the event set, payloads, topics, or envelope wire (pure infra swap); dropping the retained v1 tables.

## Acceptance Criteria

- **AC-1 (topics)** — every `auth.*` event still publishes to its identically-named bare topic; unknown eventType rejected. Proven by `AuthOutboxPublisherTest.topicFor`.
- **AC-2 (envelope)** — persisted/published payload byte-identical to the v1 7-field envelope; `source="auth-service"`; per-event payload + field order (TASK-BE-131) unchanged; envelope `eventId == row id`. Proven by `OutboxAuthEventPublisherTest`.
- **AC-3 (v2 behaviours)** — records carry `eventId`+`eventType` headers; transient Kafka failures back off; per-eventType lag/success/failure metrics + `auth.outbox.pending.count` gauge.
- **AC-4 (schema)** — `V0027` applies cleanly; `ddl-auto=validate` passes (new entity matches; retained v1 tables still EntityScanned by the kept lib auto-config).
- **AC-5 (no v1 residue)** — `AuthOutboxPollingScheduler` + the `extends BaseEventPublisher` write path removed; the lib `OutboxWriter` no longer referenced by auth code.
- **AC-6 (build + IT)** — `:projects:iam-platform:apps:auth-service:test` GREEN (Docker-free). The Testcontainers `:integrationTest` lane is authoritative on CI Linux (local Rancher/Testcontainers blocker) — push and read CI; do not claim IT-green locally.
- **AC-7 (specs)** — architecture.md § Outbox (v2) updated.

## Related Specs / Contracts

- `projects/iam-platform/specs/services/auth-service/architecture.md` (§ Outbox v2).
- `projects/iam-platform/specs/contracts/events/auth-events.md` (§ Topics — unchanged).
- `docs/adr/ADR-MONO-004-shared-messaging-scaffolding.md` § 5 (v2 pattern) + § 6 (follow-up).
- **Reference impl:** finance `account-service` `infrastructure/outbox/*` + `db/migration/V2__account_outbox_v2.sql`; erp `approval-service` `infrastructure/outbox/*` + `config/OutboxConfig.java` + `db/migration/V5__approval_outbox_v2.sql`.

## Dependencies

- None blocking. Sibling iam tasks TASK-BE-451 (account) / TASK-BE-452 (admin) share the same playbook and may land together; each service is independent (separate DB, separate outbox table).

## Edge Cases

- **KEEP-auto-config / cutover.** Unlike finance account (which EXCLUDES the lib auto-config), auth follows the **erp approval / wms master KEEP stance**: the lib `OutboxAutoConfiguration` is NOT excluded, so `OutboxJpaEntity`/`ProcessedEventJpaEntity` stay EntityScanned and `ddl-auto=validate` still requires the v1 `outbox`/`processed_events` tables (V0004 comment marks `processed_events` required by the lib EntityScan). Both tables are retained as present-but-unused stubs. In-flight v1 `outbox` rows at cutover are abandoned (low-volume, re-derivable in demo/fed-e2e/CI) — F1.
- **Envelope fidelity.** Only intentional change: envelope `eventId` now equals the row PK (both UUIDv7) — value semantics unchanged (it was already a random UUIDv7 string via `UuidV7.randomString()`).
- **No gate, no failure counter.** The v1 `AuthOutboxPollingScheduler` was an unconditional `@Component` with no `@ConditionalOnProperty` and no custom failure counter — so the v2 relay adds neither (plain `MicrometerOutboxMetrics`).
- **OutboxLagMetric.** The legacy `infrastructure.messaging.OutboxLagMetric` reads `FROM outbox WHERE status='PENDING'` (the retained-but-now-empty v1 table → reports 0). Left as-is (compiles, harmless); the v2 `auth.outbox.pending.count` gauge is the live signal.

## Failure Scenarios

- **F1 — silent event loss at cutover** — abandoning unpublished v1 `outbox` rows undocumented. Mitigation: accept+document (Edge Case "cutover").
- **F2 — envelope/topic drift** — wrapping/altering the envelope or adding a `.v1` suffix would silently break security-service / account-service consumers. Mitigation: AC-1 + AC-2; tests assert it.
- **F3 — ddl validate boot failure** — mismatched `auth_outbox` columns vs entity, or dropping the v1 tables while keeping the lib auto-config. Mitigation: AC-4 + Edge Case KEEP-auto-config.
- **F4 — claiming IT-green from a blocked local run** — Testcontainers is blocked locally. Mitigation: AC-6 — CI Linux is authoritative.
