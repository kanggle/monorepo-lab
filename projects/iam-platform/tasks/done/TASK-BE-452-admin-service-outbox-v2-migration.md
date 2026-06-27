# TASK-BE-452 (iam) — Migrate admin-service outbox v1 → v2 (AbstractOutboxPublisher)

**Status:** done

**Type:** TASK-BE
**Analysis model:** Opus 4.8 / **Recommended impl model:** Opus (schema migration + DUAL-publisher port conversion with TWO distinct wire shapes — one FLAT, one SELF-BUILT envelope — preserved byte-identically + relay cutover in an audit-heavy regulated service)

**Service:** admin-service (iam-platform)

> **Origin.** ADR-MONO-004 § 6 / project memory `project_outbox_v2_migration_playbook` — the iam group-A v1→v2 outbox sweep (siblings: TASK-BE-450 auth, TASK-BE-451 account). admin-service is **dual-axis** v1 (`AdminOutboxPollingScheduler extends OutboxPollingScheduler`) with **TWO publishers** sharing the outbox: `AdminEventPublisher` (admin.action.performed) + `TenantEventPublisher` (tenant.*). MySQL precedent = finance account-service (TASK-FIN-BE-045) + erp approval-service (TASK-ERP-BE-025).

---

## Goal

Replace admin-service's v1 outbox stack with the shared v2 `AbstractOutboxPublisher<R extends OutboxRow>`. Gains the v2 behaviours (exponential backoff, `eventId`+`eventType` Kafka headers, per-eventType metrics + pending gauge, UUIDv7 CHAR(36) natural key).

**Behaviour preserved exactly — TWO distinct wire shapes:**

- **`admin.action.performed` is FLAT.** The v1 `AdminEventPublisher.saveEvent` serialised the canonical-action map AS-IS (eventId/occurredAt/actor/action/target/outcome/reason at the JSON root — NOT a 7-field `{eventType,source,schemaVersion,payload}` wrapper). The v2 adapter reproduces the EXACT flat bytes incl. the centralised PII `displayHint` masking.
- **`tenant.*` is a SELF-BUILT full envelope.** The v1 `TenantEventPublisher` built the complete canonical 7-field envelope INSIDE each method and passed it to `saveEvent` — so the wire IS an envelope. The v2 adapter reproduces those EXACT bytes; **NO double-wrap**.
- Both reuse the payload's own `eventId` as the row PK (the header then matches). Topics bare (no `.v1`), ported VERBATIM covering BOTH publishers. Reject-unmapped preserved.

## Scope

**In scope (admin-service only — `projects/iam-platform/apps/admin-service/`):**

1. **Flyway `V0038__admin_outbox_v2.sql`** — `admin_outbox` (InnoDB/utf8mb4): `id CHAR(36) PK, aggregate_type VARCHAR(60), aggregate_id VARCHAR(64), event_type VARCHAR(80), payload TEXT, partition_key VARCHAR(64) NOT NULL, created_at DATETIME(6), published_at DATETIME(6)` + `idx_admin_outbox_unpublished`. OMIT `event_version`. Retain v1 `outbox` + `processed_events` (KEEP-auto-config). (V0038 is the next free version; dev migrations fill V0014/V0023/V0028.)
2. **Entity + repo** in `infrastructure.persistence` (the `JpaConfig` scan base — no edit): `AdminOutboxJpaEntity implements OutboxRow` (`CHAR(36)` UUID) + `AdminOutboxJpaRepository`. One repo, both write adapters.
3. **Write path — TWO ports + TWO impls.** `AdminEventPublisher` (KEEP the nested `Envelope` record + `publishAdminActionPerformed`) + `TenantEventPublisher` → interfaces. New `@Component`s `infrastructure.outbox.OutboxAdminEventPublisher` (reproduces the FLAT action payload + displayHint masking) + `OutboxTenantEventPublisher` (reproduces the SELF-BUILT envelope verbatim). Both persist `AdminOutboxJpaEntity` via the shared repo; row PK = the payload's embedded `eventId`. `@Transactional` carried. Deps include a `Clock`.
4. **Relay.** Delete `AdminOutboxPollingScheduler`; add `infrastructure.outbox.AdminOutboxPublisher extends AbstractOutboxPublisher<AdminOutboxJpaEntity>` — `@Component`, NO gate, plain `MicrometerOutboxMetrics(registry,"admin")`, `admin.outbox.pending.count` gauge, `@Scheduled` override, `static topicFor` covering BOTH publishers (verbatim port).
5. **Config.** Add `infrastructure.config.OutboxConfig` providing a `TransactionTemplate` AND a `Clock systemUTC()` (admin had neither). KEEP lib `OutboxAutoConfiguration`. Do NOT add `@EnableScheduling` (already on `AdminApplication`). `application.yml` / `application-test.yml`: `admin.outbox.{poll-ms,initial-delay-ms,batch-size}`.
6. **Tests.** New `OutboxAdminEventPublisherTest` (FLAT payload + displayHint + eventId reuse) + `OutboxTenantEventPublisherTest` (self-built envelope reproduced, no double-wrap) + `AdminOutboxPublisherTest` (`topicFor` covering BOTH publishers + reject; round-trip). Delete v1 `AdminEventPublisherTest` + `AdminEventPublisherCanonicalEnvelopeTest` + `AdminOutboxPollingSchedulerTest`. Repoint ITs querying v1 `outbox` → `admin_outbox` (`AdminIntegrationTest`, `TenantAdminIntegrationTest`). Use-case tests `@Mock TenantEventPublisher` unaffected (interface mock).
7. **Specs.** `specs/services/admin-service/architecture.md` § Outbox (v2).

**Out of scope:** changing the event set, payloads, topics, or either wire shape; dropping the retained v1 tables.

## Acceptance Criteria

- **AC-1 (topics, both publishers)** — `topicFor` resolves BOTH admin.action.performed AND tenant.* to identically-named bare topics; unknown rejected. Proven by `AdminOutboxPublisherTest`.
- **AC-2 (two wire shapes)** — admin.action.performed payload byte-identical to the v1 FLAT `saveEvent` wire (root-level fields, displayHint masking); tenant.* payload byte-identical to the v1 SELF-BUILT 7-field envelope (no double-wrap); embedded `eventId` reused as row PK. Proven by the two write-adapter tests.
- **AC-3 (v2 behaviours)** — `eventId`+`eventType` headers; backoff; per-eventType metrics + `admin.outbox.pending.count` gauge.
- **AC-4 (schema)** — `V0038` applies cleanly; `ddl-auto=validate` passes (retained v1 tables still EntityScanned).
- **AC-5 (no v1 residue)** — `AdminOutboxPollingScheduler` + both `extends BaseEventPublisher` write paths removed; lib `OutboxWriter` no longer referenced by admin code.
- **AC-6 (build + IT)** — `:projects:iam-platform:apps:admin-service:test` GREEN (Docker-free). Testcontainers `:integrationTest` lane authoritative on CI Linux — push and read CI.
- **AC-7 (specs)** — architecture.md § Outbox (v2) updated.

## Related Specs / Contracts

- `projects/iam-platform/specs/services/admin-service/architecture.md` (§ Outbox v2).
- `projects/iam-platform/specs/contracts/events/admin-events.md` (admin.action.performed — FLAT canonical) + `tenant-events.md` (tenant.* — envelope).
- `docs/adr/ADR-MONO-004-shared-messaging-scaffolding.md` § 5 + § 6.
- **Reference impl:** finance `account-service` `infrastructure/outbox/*`; erp `approval-service` `infrastructure/outbox/*` + `config/OutboxConfig.java`.

## Dependencies

- None blocking. Sibling TASK-BE-450 (auth) / TASK-BE-451 (account) share the playbook; independent (separate DB/outbox table). May land together.

## Edge Cases

- **Two wire shapes in one table (the subtle one).** admin.action.performed (FLAT) and tenant.* (SELF-BUILT envelope) coexist in `admin_outbox`. Each adapter must reproduce its publisher's exact v1 bytes — a uniform 7-field wrapper would (a) double-wrap the tenant.* events and (b) wrongly envelope the flat admin.action. The relay is shape-agnostic (it never parses payload). F2.
- **eventId reuse.** Both v1 publishers self-mint an `eventId` (UuidV7) inside their payload; the adapters reuse it as the row PK so the additive header matches.
- **KEEP-auto-config / cutover.** Lib `OutboxAutoConfiguration` NOT excluded; v1 `outbox`/`processed_events` retained (required under `ddl-auto=validate`). In-flight v1 rows abandoned (F1).
- **No gate / no failure counter** — the v1 scheduler had neither; v2 adds neither (plain metrics).

## Failure Scenarios

- **F1 — silent event loss at cutover** — abandoning unpublished v1 rows undocumented. Mitigation: accept+document.
- **F2 — wire drift** — double-wrapping tenant.* or enveloping the flat admin.action, or altering topics, would silently break consumers. Mitigation: AC-1 + AC-2; tests assert each shape.
- **F3 — ddl validate boot failure** — mismatched columns or dropping v1 tables while keeping the lib auto-config. Mitigation: AC-4.
- **F4 — claiming IT-green from a blocked local run** — Testcontainers blocked locally. Mitigation: AC-6 — CI Linux authoritative.
