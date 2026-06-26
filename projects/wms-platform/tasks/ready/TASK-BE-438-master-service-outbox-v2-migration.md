# TASK-BE-438 (wms) — Migrate master-service outbox v1 → v2 (AbstractOutboxPublisher)

**Status:** ready

**Type:** TASK-BE
**Analysis model:** Opus 4.8 / **Recommended impl model:** Opus (schema migration + write-path rewrite + publisher/metrics swap + cutover of an at-least-once delivery component; not a routine fix despite the ADR's "cosmetic" label)

**Service:** master-service (wms-platform)

> **Origin.** ADR-MONO-004 § 6 "Outstanding follow-ups", row 1: *"Migrate master-service to AbstractOutboxPublisher v2 — Cosmetic; defer until next bundle — wms backlog."* master-service is the **last** wms service still on the v1 outbox stack (`OutboxPollingScheduler` + lib `OutboxJpaEntity`); outbound/inbound/inventory were migrated to v2 under TASK-MONO-049 / ADR-MONO-004 § 5. **Scope note:** the ADR labels this "cosmetic," but it is in fact a **schema migration + write-path cutover** (the v2 row contract is UUID-keyed with different columns), so it is a proper code task, not an inline edit. Authored as `ready`; implementation deferred to a dedicated session.

---

## Goal

Replace master-service's v1 outbox publishing stack with the shared v2 `AbstractOutboxPublisher<R extends OutboxRow>` (`libs/java-messaging`), so master-service gains the v2 behaviours its siblings already have:

- **Exponential backoff** across failed ticks (1s → 2s → 4s → … → 30s cap) instead of v1's "break the batch, retry next fixed tick" with no backoff.
- **`eventId` + `eventType` Kafka record headers** so consumers can short-circuit/dedupe before deserializing the payload.
- **Per-event publish-lag metrics** via the lib `MicrometerOutboxMetrics` (eventType-tagged success/failure + lag), replacing master's bespoke no-arg `OutboxMetrics`.
- **A `UUID eventId` natural key** (dedupe-friendly, UUIDv7-sortable) instead of the v1 `BIGSERIAL id` + `status` string.

Behaviour that MUST be preserved exactly: the `wms.master.<aggregate>.v1` topic mapping (current `MasterOutboxPollingScheduler.resolveTopic`), at-least-once delivery, FIFO ordering by occurrence, and the `@Profile("!standalone")` gating (no Kafka in standalone).

## Scope

**In scope (master-service only — `projects/wms-platform/apps/master-service/`):**

1. **Flyway migration (`V8__master_outbox_v2.sql`)** creating a v2-shaped outbox table (recommended name `master_outbox` to avoid colliding with the retained v1 `outbox` table — see Edge Case "EntityScan"):
   ```
   event_id       UUID PRIMARY KEY,
   event_type     VARCHAR(100) NOT NULL,
   aggregate_type VARCHAR(60)  NOT NULL,
   aggregate_id   VARCHAR(60)  NOT NULL,
   partition_key  VARCHAR(60),
   payload        TEXT         NOT NULL,
   occurred_at    TIMESTAMP    NOT NULL,
   published_at   TIMESTAMP,
   retries        INT NOT NULL DEFAULT 0,
   last_error     TEXT
   ```
   + an index on `(published_at, occurred_at)` (or `WHERE published_at IS NULL`) for the pending poll. (Match the exact column lengths/types of the outbound-service v2 migration — copy it as the template.)
2. **New entity** `MasterOutboxEntity extends OutboxRowEntity` (`@Entity @Table(name = "master_outbox")`) + a Spring Data repo `MasterOutboxRepository extends JpaRepository<MasterOutboxEntity, UUID>` with `List<MasterOutboxEntity> findPending(Pageable)` (`WHERE published_at IS NULL ORDER BY occurred_at ASC`) and `long countByPublishedAtIsNull()` — exactly the outbound-service shape consumed by `SpringDataOutboxRowRepository.wrap(...)`.
3. **Rewrite the write path.** `OutboxDomainEventAdapter` / `OutboxWriter` currently call `OutboxJpaEntity.create(aggregateType, aggregateId, eventType, payload)` (v1, server-assigned `BIGSERIAL`, `status=PENDING`). The v2 row requires the producer to supply `event_id` (generate a UUID — prefer UUIDv7 if a generator is available, else `randomUUID`), `occurred_at` (the domain event timestamp), and optional `partition_key`. Update the adapter to construct/persist a `MasterOutboxEntity` via `MasterOutboxRepository`. (The `EventEnvelopeSerializer` payload step is unchanged.)
4. **Replace the publisher.** Delete `MasterOutboxPollingScheduler extends OutboxPollingScheduler` (v1) and introduce a thin `MasterOutboxPublisher extends AbstractOutboxPublisher<MasterOutboxEntity>` (mirror `outbound-service`'s `OutboxPublisher`): supply the wrapped repo, `KafkaTemplate`, `TransactionTemplate`, the `TopicResolver` (`eventType -> "wms.master." + aggregate + ".v1"`, ported from the current `resolveTopic`, including its `master.<aggregate>.<action>` validation), a `MicrometerOutboxMetrics(meterRegistry, "master")`, `Clock`, and a `batchSize` property; add the `@Scheduled(fixedDelayString=..., initialDelayString=...)` `publishPending()` override; keep `@Profile("!standalone")`.
5. **Metrics swap.** Replace the bespoke `OutboxMetrics` (`recordPublishSuccess()` / `recordPublishFailure()` no-arg) with the lib `MicrometerOutboxMetrics`. If the existing master metric **names** are referenced by dashboards/alerts, preserve them with an explicit `Gauge`/naming shim (as outbound-service preserved `outbound.outbox.pending.count`); otherwise adopt the lib names and note the rename.
6. **Update `OutboxConfig`** bean wiring (drop the v1 scheduler bean + the custom metrics bean; the publisher is now a `@Component`, or wire it here — match the outbound-service convention).
7. **Rewrite `MasterOutboxPollingSchedulerTest`** → `MasterOutboxPublisherTest` covering topic resolution (including the reject-unmapped-eventType case), the backoff schedule (injected `Clock`), header emission, and mark-published.
8. **Update specs**: `specs/services/master-service/architecture.md` + `database-design.md` to document the v2 outbox table/columns and the publisher; `scheduled-jobs.md` if it names the poller. Update **ADR-MONO-004 § 6** to strike row 1 (mark this follow-up done) — note ADR-MONO-004 is a **shared** doc under `docs/adr/`, so this single-file edit is the only shared-path touch; the rest is project-internal.

**Out of scope:**
- The other ADR-MONO-004 § 6 follow-ups (BaseEventPublisher migrations, scm `EventDedupePort` rename, admin-service dedupe fate, BaseEventPublisher javadoc cleanup) — separate rows, separate tasks.
- Changing the `processed_events` consumer-dedupe table (master is producer-only; leave as-is).
- Any new master domain events — pure infrastructure swap, identical event set + topics.

## Acceptance Criteria

- **AC-1 (behaviour-preserving topics)** — every `master.<aggregate>.<action>` event still publishes to `wms.master.<aggregate>.v1`; an unmapped/invalid eventType is still rejected (terminally, as today). Proven by the ported topic-resolver test.
- **AC-2 (v2 behaviours present)** — published Kafka records carry `eventId` + `eventType` headers; repeated transient Kafka failures back off exponentially (deterministic with injected `Clock`); per-eventType lag/success/failure metrics are emitted.
- **AC-3 (schema + migration)** — `V8__master_outbox_v2.sql` applies cleanly on a fresh DB and on top of the existing V1–V7; `hibernate.ddl-auto=validate` passes (the new entity matches the table, and the retained v1 `outbox`/`processed_events` tables still validate against the still-EntityScanned lib entities — see Edge Case).
- **AC-4 (write path)** — a master domain event mutation persists a `master_outbox` row with a non-null `event_id` (UUID), correct `occurred_at`, `aggregate_type`/`aggregate_id`, and the serialized envelope payload; the poller publishes it and sets `published_at`.
- **AC-5 (standalone)** — under `standalone` the publisher bean is absent (no Kafka), and master still boots + serves (the write path persists rows that simply are not drained).
- **AC-6 (no v1 residue)** — `MasterOutboxPollingScheduler`, the bespoke `OutboxMetrics`, and any `OutboxJpaEntity`-based write are removed from master-service (grep clean); master no longer instantiates a v1 `OutboxPublisher`/`OutboxPollingScheduler`.
- **AC-7 (build + IT)** — `:projects:wms-platform:apps:master-service:test` GREEN locally (Docker-free unit). The master-service **`:integrationTest`** suite (Testcontainers — runs on the **CI Linux runner**, authoritative per the local Rancher/Testcontainers blocker) GREEN: outbox round-trip (persist → poll → Kafka → published_at) + migration apply. Push and let CI validate; do not claim IT-green from local.
- **AC-8 (ADR updated)** — ADR-MONO-004 § 6 row 1 struck/marked done with a pointer to this task's merge.

## Related Specs

- `projects/wms-platform/specs/services/master-service/architecture.md`, `database-design.md`, `scheduled-jobs.md` — outbox table + publisher documentation.
- `docs/adr/ADR-MONO-004-shared-messaging-scaffolding.md` § 5 (the v2 migration pattern outbound/inbound/inventory followed) + § 6 (this follow-up row).

## Related Contracts

- `projects/wms-platform/specs/contracts/events/master-events.md` — § Topic Layout; topics + event set are **unchanged** (this is an infra swap). Re-confirm the `wms.master.<aggregate>.v1` mapping matches the new `TopicResolver`.

## Dependencies / Prior Work

- **TASK-MONO-049 / ADR-MONO-004** — introduced `AbstractOutboxPublisher` v2 and migrated outbound/inbound/inventory. **Reference implementation:** `projects/wms-platform/apps/outbound-service/.../publisher/OutboxPublisher.java` (the exact v2 wiring shape to mirror — wrapped repo, `MicrometerOutboxMetrics`, `@Scheduled` override, preserved pending-count gauge).
- **libs/java-messaging** v2 types: `AbstractOutboxPublisher`, `OutboxRow`, `OutboxRowEntity`, `OutboxRowRepository`, `SpringDataOutboxRowRepository.wrap`, `TopicResolver`, `MicrometerOutboxMetrics`.

## Edge Cases

- **EntityScan / retained v1 tables (the subtle one).** `libs/java-messaging` registers `OutboxJpaEntity` (table `outbox`) **and** `ProcessedEventJpaEntity` (table `processed_events`) in its EntityScan; with `ddl-auto=validate`, **both tables must remain present** or the context fails to boot. So the V8 migration must **add** `master_outbox` and **NOT drop** `outbox`/`processed_events`. (Alternatively, exclude the lib v1 outbox entity from the EntityScan/auto-config — heavier and riskier; prefer keeping the now-unused `outbox` table present-but-empty and document it. A later cleanup task can drop it once the lib stops force-scanning v1 entities.)
- **Cutover / in-flight rows.** On deploy, any unpublished rows sitting in the old v1 `outbox` table will **no longer be polled** (the new poller reads `master_outbox`). In prod that would orphan undelivered events; for this monorepo's demo/fed-e2e + CI usage it is acceptable, but the task MUST either (a) drain the v1 `outbox` to empty before cutover, or (b) explicitly document that in-flight v1 rows are abandoned (acceptable only because master events are low-volume + re-derivable in the demo). State the chosen disposition in the PR.
- **UUID generation.** v1 used a server-assigned `BIGSERIAL`; v2 requires the producer to set `event_id`. Use UUIDv7 if the codebase already has a generator (sortable, index-friendly); otherwise `UUID.randomUUID()` is correct but note the index-locality tradeoff.
- **`occurred_at` vs `created_at`.** v1 stored `created_at = Instant.now()` at insert; v2's `occurred_at` is the **domain** event timestamp. If the domain event carries its own timestamp, use it; else `clock.instant()` at write — be consistent with the lag-metric semantics (lag = publish - occurred).
- **Metric-name continuity.** If any Grafana/alert references the current master outbox metric names, renaming them silently breaks dashboards. Inventory the names first; preserve or document the rename (cf. outbound-service preserving `outbound.outbox.pending.count`).

## Failure Scenarios

- **F1 — silent event loss at cutover** — abandoning unpublished v1 `outbox` rows without draining or documenting it would silently drop events. Mitigation: Edge Case "Cutover" — drain or explicitly accept+document.
- **F2 — ddl validate boot failure** — dropping `outbox`/`processed_events` (still EntityScanned by the lib) crashes the context. Mitigation: AC-3 + Edge Case "EntityScan" — retain the v1 tables.
- **F3 — topic drift** — a subtly different `TopicResolver` (e.g. dropping the `master.` validation) would mis-route or stop rejecting bad eventTypes. Mitigation: AC-1 ports the exact logic + test.
- **F4 — claiming IT-green from a blocked local run** — Testcontainers is blocked on the local Windows host; asserting the outbox round-trip IT passed locally would be false. Mitigation: AC-7 — CI Linux runner is the authoritative IT verifier; push and read the CI result.
