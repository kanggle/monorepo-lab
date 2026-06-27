# TASK-BE-453 (iam) — Migrate security-service outbox v1 → v2 (AbstractOutboxPublisher)

**Status:** done

**Type:** TASK-BE
**Analysis model:** Opus 4.8 / **Recommended impl model:** Opus (schema migration + write-path port conversion + relay cutover of an at-least-once delivery component in a security-critical service — wire compatibility is non-negotiable)

**Service:** security-service (iam-platform)

> **Origin.** ADR-MONO-004 § 6 / project memory `project_outbox_v2_migration_playbook` — the v1→v2 outbox sweep across the remaining holdouts. iam-platform's security/membership/community are the iam group-B holdouts (group-A = auth/account/admin, TASK-BE-450/451/452). Each is **dual-axis** v1: it uses BOTH `SecurityEventPublisher extends BaseEventPublisher` (write path) AND `SecurityOutboxPollingScheduler extends OutboxPollingScheduler` (relay). MySQL precedent = in-worktree auth-service (TASK-BE-450) + finance account-service (TASK-FIN-BE-045) + erp approval-service (TASK-ERP-BE-025).

---

## Goal

Replace security-service's v1 outbox stack with the shared v2 `AbstractOutboxPublisher<R extends OutboxRow>` (`libs/java-messaging`), mirroring auth-service. security-service gains the v2 behaviours (exponential backoff, `eventId`+`eventType` Kafka headers, per-eventType lag/success/failure metrics + pending gauge, a `UUID eventId` UUIDv7 CHAR(36) natural key).

**Behaviour preserved exactly (wire compatibility — downstream consumers + operator paths consume these):**

- **Topics** — iam topics are **bare** (NO `.v1` suffix): the v1 `SecurityOutboxPollingScheduler.resolveTopic` maps each `security.*` eventType to its identically-named topic. Ported VERBATIM, incl. reject-unmapped.
- **On-wire envelope** — the v1 7-field `BaseEventPublisher.writeEvent` shape `{eventId, eventType, source="security-service", occurredAt, schemaVersion=1, partitionKey, payload}`, every payload field/order + TASK-BE-248 `tenant_id` presence unchanged.
- **Transaction semantics** — the three `SuspiciousEvent`-based publish methods keep `@Transactional(REQUIRED)` (TASK-MONO-046-8a); `publishPiiMasked` keeps NO `@Transactional` (TASK-BE-258, called within `PiiMaskingService`'s TX).
- At-least-once delivery, FIFO by created-order, Kafka key = `aggregateId` (= `partitionKey` = `accountId`).

## Scope

**In scope (security-service only — `projects/iam-platform/apps/security-service/`):**

1. **Flyway migration `V0011__security_outbox_v2.sql`** — `security_outbox` mirroring the auth/finance MySQL precedent (InnoDB/utf8mb4): `id CHAR(36) PK, aggregate_type VARCHAR(60), aggregate_id VARCHAR(64), event_type VARCHAR(80), payload TEXT, partition_key VARCHAR(64) NOT NULL, created_at DATETIME(6), published_at DATETIME(6)` + `idx_security_outbox_unpublished (published_at, created_at)`. **OMIT `event_version`**. **Retain** the v1 `outbox_events` + `processed_events` (KEEP-auto-config).
2. **Entity** `infrastructure.persistence.SecurityOutboxJpaEntity implements OutboxRow` (`@Table("security_outbox")`, `id` UUID `@JdbcTypeCode(SqlTypes.CHAR) CHAR(36)`) + **repo** `SecurityOutboxJpaRepository extends JpaRepository<…, UUID>` (`findPending(Pageable)` + `countByPublishedAtIsNull()`). Placed in `infrastructure.persistence` — the existing `JpaConfig` `@EntityScan`/`@EnableJpaRepositories` base (no edit needed).
3. **Write path (port + impl).** `application.event.SecurityEventPublisher` → **interface** (all method signatures + `TOPIC_*` constants verbatim). New `@Component infrastructure.outbox.OutboxSecurityEventPublisher` builds the exact 7-field envelope and persists a `SecurityOutboxJpaEntity` (`eventId = UuidV7.randomUuid()` = envelope eventId = row PK; `occurredAt = Instant.now(clock)`). Deps `(SecurityOutboxJpaRepository, ObjectMapper, Clock)`. Use-case `@Mock SecurityEventPublisher` tests unaffected (interface mock).
4. **Relay.** Delete `SecurityOutboxPollingScheduler`; add `infrastructure.outbox.SecurityOutboxPublisher extends AbstractOutboxPublisher<SecurityOutboxJpaEntity>` — `@Component`, **NO `@ConditionalOnProperty`** (v1 had none), plain `MicrometerOutboxMetrics(registry,"security")` (v1 had no failure counter), `security.outbox.pending.count` gauge, `@Scheduled` override, `static topicFor` = the v1 resolveTopic ported verbatim.
5. **Config.** Add `infrastructure.config.OutboxConfig` providing a `TransactionTemplate` AND a `Clock systemUTC()` (security had neither). KEEP lib `OutboxAutoConfiguration` (NOT excluded). Do NOT add `@EnableScheduling` (already on `SecurityApplication`). `application.yml` / `application-test.yml`: add `security.outbox.{poll-ms,initial-delay-ms,batch-size}`.
6. **Tests.** New `OutboxSecurityEventPublisherTest` (envelope/payload shape + eventId==row.id) + `SecurityOutboxPublisherTest` (`topicFor` all 4 + reject; publish round-trip headers/key/value/mark-published/gauge). Delete the v1 `SecurityEventPublisherTest` + `SecurityOutboxPollingSchedulerTest`. Repoint ITs querying v1 outbox → `security_outbox`: `DetectionE2EIntegrationTest` (lib `OutboxJpaRepository` autowire → `SecurityOutboxJpaRepository`), `PiiMaskingIntegrationTest` (`SELECT … FROM outbox_events` → `security_outbox`).
7. **Specs.** `specs/services/security-service/architecture.md` § Outbox (v2).

**Out of scope:** changing the event set, payloads, topics, or envelope wire (pure infra swap); dropping the retained v1 tables.

## Acceptance Criteria

- **AC-1 (topics)** — every `security.*` event still publishes to its identically-named bare topic; unknown eventType rejected. Proven by `SecurityOutboxPublisherTest.topicFor`.
- **AC-2 (envelope)** — persisted/published payload byte-identical to the v1 7-field envelope; `source="security-service"`; per-event payload + field order + `tenant_id` presence unchanged; envelope `eventId == row id`. Proven by `OutboxSecurityEventPublisherTest`.
- **AC-3 (v2 behaviours)** — records carry `eventId`+`eventType` headers; transient Kafka failures back off; per-eventType lag/success/failure metrics + `security.outbox.pending.count` gauge.
- **AC-4 (schema)** — `V0011` applies cleanly; `ddl-auto=validate` passes (new entity matches; retained v1 `outbox_events`/`processed_events` still EntityScanned via the kept lib auto-config + `META-INF/orm.xml` table re-point).
- **AC-5 (no v1 residue)** — `SecurityOutboxPollingScheduler` + the `extends BaseEventPublisher` write path removed; the lib `OutboxWriter` no longer referenced by security code.
- **AC-6 (build + IT)** — `:projects:iam-platform:apps:security-service:test` GREEN (Docker-free). The Testcontainers `:integrationTest` lane is authoritative on CI Linux (local Rancher/Testcontainers blocker) — push and read CI; do not claim IT-green locally.
- **AC-7 (specs)** — architecture.md § Outbox (v2) updated.

## Related Specs / Contracts

- `projects/iam-platform/specs/services/security-service/architecture.md` (§ Outbox v2).
- `projects/iam-platform/specs/contracts/events/security-events.md` (§ Topics — unchanged).
- `docs/adr/ADR-MONO-004-shared-messaging-scaffolding.md` § 5 (v2 pattern) + § 6 (follow-up).
- **Reference impl:** in-worktree auth-service `infrastructure/outbox/*` + `db/migration/V0027__auth_outbox_v2.sql`; finance `account-service` `infrastructure/outbox/*`.

## Dependencies

- None blocking. Sibling iam group-B tasks TASK-BE-454 (membership) / TASK-BE-455 (community) share the same playbook and land together; each service is independent (separate DB, separate outbox table). Group-A (auth/account/admin) already migrated in this worktree.

## Edge Cases

- **KEEP-auto-config / cutover.** Unlike finance account (which EXCLUDES the lib auto-config), security follows the **erp approval / auth KEEP stance**: the lib `OutboxAutoConfiguration` is NOT excluded, so `OutboxJpaEntity`/`ProcessedEventJpaEntity` stay EntityScanned and `ddl-auto=validate` still requires the v1 tables. NB security renamed the v1 table `outbox` → `outbox_events` (V0005); the lib `OutboxJpaEntity` is re-pointed to it via `src/main/resources/META-INF/orm.xml` — that override is retained. Both tables remain present-but-unused stubs. In-flight v1 rows at cutover are abandoned (low-volume, re-derivable in demo/fed-e2e/CI) — F1.
- **Envelope fidelity.** Only intentional change: envelope `eventId` now equals the row PK (both UUIDv7) — value semantics unchanged (it was already a random UUIDv7 string).
- **No gate, no failure counter.** The v1 `SecurityOutboxPollingScheduler` was an unconditional `@Component` with no `@ConditionalOnProperty` and no custom failure counter — so the v2 relay adds neither (plain `MicrometerOutboxMetrics`).
- **Transaction propagation.** Preserve the exact v1 propagation: `@Transactional(REQUIRED)` on the three `SuspiciousEvent` methods, none on `publishPiiMasked`.

## Failure Scenarios

- **F1 — silent event loss at cutover** — abandoning unpublished v1 `outbox_events` rows undocumented. Mitigation: accept+document (Edge Case "cutover").
- **F2 — envelope/topic drift** — wrapping/altering the envelope or adding a `.v1` suffix would silently break consumers. Mitigation: AC-1 + AC-2; tests assert it.
- **F3 — ddl validate boot failure** — mismatched `security_outbox` columns vs entity, or dropping the v1 tables while keeping the lib auto-config. Mitigation: AC-4 + Edge Case KEEP-auto-config.
- **F4 — claiming IT-green from a blocked local run** — Testcontainers is blocked locally. Mitigation: AC-6 — CI Linux is authoritative.
