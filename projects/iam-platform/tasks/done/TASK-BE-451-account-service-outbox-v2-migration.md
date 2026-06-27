# TASK-BE-451 (iam) — Migrate account-service outbox v1 → v2 (AbstractOutboxPublisher)

**Status:** done

**Type:** TASK-BE
**Analysis model:** Opus 4.8 / **Recommended impl model:** Opus (schema migration + DUAL-publisher write-path port conversion preserving a contractually-locked FLAT wire + relay cutover — wire compatibility with ecommerce account.* consumers is non-negotiable)

**Service:** account-service (iam-platform)

> **Origin.** ADR-MONO-004 § 6 / project memory `project_outbox_v2_migration_playbook` — the iam group-A v1→v2 outbox sweep (sibling: TASK-BE-450 auth, TASK-BE-452 admin). account-service is **dual-axis** v1 (`AccountEventPublisher extends BaseEventPublisher` + `AccountOutboxPollingScheduler extends OutboxPollingScheduler`) AND has **TWO publishers** writing to the shared outbox: `AccountEventPublisher` + `TenantDomainSubscriptionEventPublisher`. MySQL precedent = finance account-service (TASK-FIN-BE-045) + erp approval-service (TASK-ERP-BE-025).

---

## Goal

Replace account-service's v1 outbox stack with the shared v2 `AbstractOutboxPublisher<R extends OutboxRow>` (`libs/java-messaging`). Gains the v2 behaviours (exponential backoff, `eventId`+`eventType` Kafka headers, per-eventType metrics + pending gauge, UUIDv7 CHAR(36) natural key).

**Behaviour preserved exactly:**

- **FLAT wire (NOT a 7-field envelope).** account-service's v1 publishers used `BaseEventPublisher.saveEvent` — the Kafka value is the bare payload map at the JSON root, NOT a canonical envelope. **TASK-BE-422/423 contractually locked the flat shape** (ecommerce account.* consumers parse top-level fields). The v2 adapters reproduce the EXACT v1 bytes — no double-wrap.
- **Topics** — bare (no `.v1`): ported VERBATIM from the v1 `AccountOutboxPollingScheduler.resolveTopic`, covering BOTH publishers (account.created/status.changed/locked/unlocked/roles.changed/deleted + tenant.subscription.changed — TASK-BE-348). Reject-unmapped preserved.
- At-least-once, FIFO by created-order, Kafka key = aggregateId. The tenantId-required guards preserved.

## Scope

**In scope (account-service only — `projects/iam-platform/apps/account-service/`):**

1. **Flyway `V0026__account_outbox_v2.sql`** — `account_outbox` (InnoDB/utf8mb4): `id CHAR(36) PK, aggregate_type VARCHAR(60), aggregate_id VARCHAR(64), event_type VARCHAR(80), payload TEXT, partition_key VARCHAR(64) NOT NULL, created_at DATETIME(6), published_at DATETIME(6)` + `idx_account_outbox_unpublished (published_at, created_at)`. OMIT `event_version`. Retain v1 `outbox` + `processed_events` (KEEP-auto-config). `aggregate_id` 64 fits both the accountId (36) and the composite `tenantId:domainKey`.
2. **Entity + repo** in `infrastructure.persistence` (the `JpaConfig` scan base — no edit): `AccountOutboxJpaEntity implements OutboxRow` (`CHAR(36)` UUID) + `AccountOutboxJpaRepository` (`findPending` + `countByPublishedAtIsNull`). One repo, both write adapters.
3. **Write path — TWO ports + TWO impls.** `AccountEventPublisher` + `TenantDomainSubscriptionEventPublisher` → interfaces (method signatures verbatim; `TenantDomainSubscriptionEventPublisher.EVENT_TYPE` constant kept). New `@Component`s `infrastructure.outbox.OutboxAccountEventPublisher` (reuses `AccountEventFactory`) + `OutboxTenantDomainSubscriptionEventPublisher` build the SAME flat payload and persist `AccountOutboxJpaEntity` via the shared repo. Row PK = the payload's embedded `eventId` if present (account.locked, tenant.subscription.changed), else a fresh UUIDv7 (header-only — flat payload unchanged). `@Transactional` carried on the impls. Deps include a `Clock`.
4. **Relay.** Delete `AccountOutboxPollingScheduler`; add `infrastructure.outbox.AccountOutboxPublisher extends AbstractOutboxPublisher<AccountOutboxJpaEntity>` — `@Component`, NO gate, plain `MicrometerOutboxMetrics(registry,"account")`, `account.outbox.pending.count` gauge, `@Scheduled` override, `static topicFor` covering BOTH publishers (verbatim port).
5. **Config.** Add `infrastructure.config.OutboxConfig` providing a `TransactionTemplate` AND a `Clock systemUTC()` (account had neither). KEEP lib `OutboxAutoConfiguration`. Do NOT add `@EnableScheduling` (already on `AccountApplication`). `application.yml` / `application-test.yml`: `account.outbox.{poll-ms,initial-delay-ms,batch-size}`.
6. **Tests.** New `OutboxAccountEventPublisherTest` + `OutboxTenantDomainSubscriptionEventPublisherTest` (FLAT payload + eventId-reuse) + `AccountOutboxPublisherTest` (`topicFor` covering BOTH publishers + reject; round-trip). Delete v1 `AccountEventPublisherTest` + `AccountOutboxPollingSchedulerTest`. Repoint ITs querying v1 `outbox` → `account_outbox` (no `status`; pending = `published_at IS NULL`) + swap `@MockitoBean OutboxPollingScheduler` → `@MockitoBean AccountOutboxPublisher` + the two scheduler ITs' lib `OutboxJpaRepository`/`OutboxJpaEntity` → `AccountOutbox*`.
7. **Specs.** `specs/services/account-service/architecture.md` § Outbox (v2).

**Out of scope:** changing the event set, payloads, topics, or (FLAT) wire shape; dropping the retained v1 tables.

## Acceptance Criteria

- **AC-1 (topics, both publishers)** — `topicFor` resolves BOTH account.* AND tenant.subscription.changed to identically-named bare topics; unknown rejected. Proven by `AccountOutboxPublisherTest`.
- **AC-2 (FLAT wire)** — persisted/published payload byte-identical to the v1 flat `saveEvent` wire (top-level fields, NO `payload`/`eventType`/`source` wrapper); embedded `eventId` reused as row PK where present. Proven by the two write-adapter tests.
- **AC-3 (v2 behaviours)** — `eventId`+`eventType` headers; backoff; per-eventType metrics + `account.outbox.pending.count` gauge.
- **AC-4 (schema)** — `V0026` applies cleanly; `ddl-auto=validate` passes (retained v1 tables still EntityScanned by the kept lib auto-config).
- **AC-5 (no v1 residue)** — `AccountOutboxPollingScheduler` + both `extends BaseEventPublisher` write paths removed; lib `OutboxWriter` no longer referenced by account code.
- **AC-6 (build + IT)** — `:projects:iam-platform:apps:account-service:test` GREEN (Docker-free). Testcontainers `:integrationTest` lane authoritative on CI Linux — push and read CI.
- **AC-7 (specs)** — architecture.md § Outbox (v2) updated.

## Related Specs / Contracts

- `projects/iam-platform/specs/services/account-service/architecture.md` (§ Outbox v2).
- `projects/iam-platform/specs/contracts/events/account-events.md` (§ Topics — unchanged; FLAT envelope per account-lifecycle-subscriptions.md).
- `docs/adr/ADR-MONO-004-shared-messaging-scaffolding.md` § 5 + § 6.
- **Reference impl:** finance `account-service` `infrastructure/outbox/*`; erp `approval-service` `infrastructure/outbox/*` + `config/OutboxConfig.java`.

## Dependencies

- None blocking. Sibling TASK-BE-450 (auth) / TASK-BE-452 (admin) share the playbook; independent (separate DB/outbox table). May land together.

## Edge Cases

- **FLAT vs envelope (the subtle one).** Unlike auth/admin-tenant which carry a canonical envelope, account-service's wire is FLAT. The v2 adapters MUST serialize the bare payload map as-is — a 7-field wrap would silently break TASK-BE-422's flat ecommerce consumers. F2.
- **eventId reuse.** account.locked + tenant.subscription.changed self-mint a flat `eventId` (UuidV7); the adapter reuses it as the row PK so the header matches. account.created/status.changed/deleted carry NO eventId (TASK-BE-422 accountId+phase dedupe) — the row PK is a fresh UUIDv7 used only for the header; the flat payload still carries no eventId.
- **KEEP-auto-config / cutover.** Lib `OutboxAutoConfiguration` NOT excluded; v1 `outbox`/`processed_events` retained (required under `ddl-auto=validate`). In-flight v1 rows abandoned (F1).
- **No gate / no failure counter** — the v1 scheduler had neither; v2 adds neither (plain metrics).

## Failure Scenarios

- **F1 — silent event loss at cutover** — abandoning unpublished v1 rows undocumented. Mitigation: accept+document.
- **F2 — wire drift (the big one)** — double-wrapping the flat payload in a 7-field envelope, or altering topics, would silently break the ecommerce account.* consumers (TASK-BE-422). Mitigation: AC-1 + AC-2; tests assert FLAT.
- **F3 — ddl validate boot failure** — mismatched columns or dropping v1 tables while keeping the lib auto-config. Mitigation: AC-4.
- **F4 — claiming IT-green from a blocked local run** — Testcontainers blocked locally. Mitigation: AC-6 — CI Linux authoritative.
