# TASK-BE-454 (iam) — Migrate membership-service outbox v1 → v2 (AbstractOutboxPublisher)

**Status:** ready

**Type:** TASK-BE
**Analysis model:** Opus 4.8 / **Recommended impl model:** Opus (schema migration + write-path port conversion + relay cutover of an at-least-once delivery component — wire compatibility is non-negotiable)

**Service:** membership-service (iam-platform)

> **Origin.** ADR-MONO-004 § 6 / project memory `project_outbox_v2_migration_playbook` — the v1→v2 outbox sweep. iam-platform group-B holdouts (security/membership/community). **Dual-axis** v1: `MembershipEventPublisher extends BaseEventPublisher` (write path) + `MembershipOutboxPollingScheduler extends OutboxPollingScheduler` (relay). MySQL precedent = in-worktree auth-service (TASK-BE-450) + finance account-service (TASK-FIN-BE-045) + erp approval-service (TASK-ERP-BE-025).

---

## Goal

Replace membership-service's v1 outbox stack with the shared v2 `AbstractOutboxPublisher<R extends OutboxRow>` (`libs/java-messaging`), mirroring auth-service. membership-service gains the v2 behaviours (exponential backoff, `eventId`+`eventType` Kafka headers, per-eventType lag/success/failure metrics + pending gauge, a `UUID eventId` UUIDv7 CHAR(36) natural key).

**Behaviour preserved exactly (wire compatibility):**

- **Topics** — iam topics are **bare** (NO `.v1` suffix): the v1 `MembershipOutboxPollingScheduler.resolveTopic` maps each `membership.subscription.*` eventType to its identically-named topic. Ported VERBATIM, incl. reject-unmapped.
- **On-wire envelope** — the v1 7-field `BaseEventPublisher.writeEvent` shape `{eventId, eventType, source="membership-service", occurredAt, schemaVersion=1, partitionKey, payload}`. The `eventType`+`payload` are produced VERBATIM by the domain factories (`Subscription#buildActivatedEvent()` / `buildExpiredEvent()` / `buildCancelledEvent()`) — kept as-is.
- At-least-once delivery, FIFO by created-order, Kafka key = `aggregateId` (= `partitionKey` = `accountId`).

## Scope

**In scope (membership-service only — `projects/iam-platform/apps/membership-service/`):**

1. **Flyway migration `V0007__membership_outbox_v2.sql`** — `membership_outbox` mirroring the auth/finance MySQL precedent (InnoDB/utf8mb4): `id CHAR(36) PK, aggregate_type VARCHAR(60), aggregate_id VARCHAR(64), event_type VARCHAR(80), payload TEXT, partition_key VARCHAR(64) NOT NULL, created_at DATETIME(6), published_at DATETIME(6)` + `idx_membership_outbox_unpublished (published_at, created_at)`. **OMIT `event_version`**. **Retain** the v1 `outbox` + `processed_events` (KEEP-auto-config).
2. **Entity** `infrastructure.persistence.MembershipOutboxJpaEntity implements OutboxRow` (`@Table("membership_outbox")`, `id` UUID `CHAR(36)`) + **repo** `MembershipOutboxJpaRepository` (`findPending(Pageable)` + `countByPublishedAtIsNull()`). Placed in `infrastructure.persistence` — one of the existing `JpaConfig` `@EntityScan`/`@EnableJpaRepositories` bases (the config also scans `…domain`; no edit needed).
3. **Write path (port + impl).** `application.event.MembershipEventPublisher` → **interface** (all 3 method signatures verbatim). New `@Component infrastructure.outbox.OutboxMembershipEventPublisher` builds the exact 7-field envelope (reusing the domain factories' `eventType()`+`payload()`) and persists a `MembershipOutboxJpaEntity` (`eventId = UuidV7.randomUuid()` = envelope eventId = row PK; `occurredAt = Instant.now(clock)`). Deps `(MembershipOutboxJpaRepository, ObjectMapper, Clock)`. Use-case `@Mock MembershipEventPublisher` tests unaffected.
4. **Relay.** Delete `MembershipOutboxPollingScheduler`; add `infrastructure.outbox.MembershipOutboxPublisher extends AbstractOutboxPublisher<MembershipOutboxJpaEntity>` — `@Component`, **NO `@ConditionalOnProperty`**, plain `MicrometerOutboxMetrics(registry,"membership")`, `membership.outbox.pending.count` gauge, `@Scheduled` override, `static topicFor` = the v1 resolveTopic verbatim.
5. **Config.** Add `infrastructure.config.OutboxConfig` providing a `TransactionTemplate` AND a `Clock systemUTC()` (membership had neither). KEEP lib `OutboxAutoConfiguration`. Do NOT add `@EnableScheduling` (already on `MembershipApplication`). `application.yml` / `application-test.yml`: add `membership.outbox.{poll-ms,initial-delay-ms,batch-size}`.
6. **Tests.** New `OutboxMembershipEventPublisherTest` (envelope/payload shape + eventId==row.id, FREE-plan null-expiry) + `MembershipOutboxPublisherTest` (`topicFor` all 3 + reject; publish round-trip). Delete the v1 `MembershipEventPublisherTest` + `MembershipOutboxPollingSchedulerTest`. Repoint ITs' raw SQL `outbox` → `membership_outbox` (`ActivateSubscriptionIntegrationTest`, `SubscriptionExpirySchedulerIntegrationTest`, `SubscriptionReactivationIntegrationTest`).
7. **Specs.** `specs/services/membership-service/architecture.md` § Outbox (v2).

**Out of scope:** changing the event set, payloads, topics, or envelope wire; dropping the retained v1 tables.

## Acceptance Criteria

- **AC-1 (topics)** — every `membership.subscription.*` event still publishes to its identically-named bare topic; unknown eventType rejected. Proven by `MembershipOutboxPublisherTest.topicFor`.
- **AC-2 (envelope)** — persisted/published payload byte-identical to the v1 7-field envelope; `source="membership-service"`; the domain-factory payload (incl. FREE-plan null `expiresAt`) unchanged; envelope `eventId == row id`. Proven by `OutboxMembershipEventPublisherTest`.
- **AC-3 (v2 behaviours)** — `eventId`+`eventType` headers; transient Kafka backoff; per-eventType metrics + `membership.outbox.pending.count` gauge.
- **AC-4 (schema)** — `V0007` applies cleanly; `ddl-auto=validate` passes (new entity matches; retained v1 `outbox`/`processed_events` still EntityScanned via the kept lib auto-config).
- **AC-5 (no v1 residue)** — `MembershipOutboxPollingScheduler` + the `extends BaseEventPublisher` write path removed; the lib `OutboxWriter` no longer referenced by membership code.
- **AC-6 (build + IT)** — `:projects:iam-platform:apps:membership-service:test` GREEN (Docker-free). The Testcontainers `:integrationTest` lane is authoritative on CI Linux.
- **AC-7 (specs)** — architecture.md § Outbox (v2) updated.

## Related Specs / Contracts

- `projects/iam-platform/specs/services/membership-service/architecture.md` (§ Outbox v2).
- `projects/iam-platform/specs/contracts/events/membership-events.md` (§ Topics — unchanged).
- `docs/adr/ADR-MONO-004-shared-messaging-scaffolding.md` § 5 + § 6.
- **Reference impl:** in-worktree auth-service `infrastructure/outbox/*` + `db/migration/V0027__auth_outbox_v2.sql`; finance `account-service`.

## Dependencies

- None blocking. Sibling iam group-B tasks TASK-BE-453 (security) / TASK-BE-455 (community) share the same playbook and land together; each service independent.

## Edge Cases

- **KEEP-auto-config / cutover.** The lib `OutboxAutoConfiguration` is NOT excluded — the v1 `outbox` (BIGINT/status, V0005) + `processed_events` (V0006) tables are retained (still EntityScanned, required under `ddl-auto=validate`). Both remain present-but-unused stubs. In-flight v1 rows at cutover are abandoned (low-volume, re-derivable) — F1.
- **Envelope fidelity.** Only intentional change: envelope `eventId` now equals the row PK. The domain-factory payloads (`Subscription#buildXxxEvent()`) are reused unchanged — including the FREE-plan branch that emits `expiresAt: null`.
- **No gate, no failure counter.** v1 scheduler had neither — v2 relay adds neither.

## Failure Scenarios

- **F1 — silent event loss at cutover** — abandoning unpublished v1 `outbox` rows undocumented. Mitigation: accept+document.
- **F2 — envelope/topic drift** — wrapping/altering the envelope or adding a `.v1` suffix breaks consumers. Mitigation: AC-1 + AC-2.
- **F3 — ddl validate boot failure** — mismatched columns or dropping v1 tables while keeping the lib auto-config. Mitigation: AC-4.
- **F4 — claiming IT-green from a blocked local run** — Testcontainers blocked locally. Mitigation: AC-6 — CI Linux authoritative.
