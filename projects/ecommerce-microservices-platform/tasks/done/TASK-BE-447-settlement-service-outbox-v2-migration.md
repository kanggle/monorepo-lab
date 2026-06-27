# TASK-BE-447 (ecommerce) — Migrate settlement-service outbox v1 → v2 (AbstractOutboxPublisher)

**Status:** done

**Type:** TASK-BE
**Analysis model:** Opus 4.8 / **Recommended impl model:** Opus (schema migration + write-path rewrite + publisher/metrics swap; standalone-profile gating)

**Service:** settlement-service (ecommerce-microservices-platform)

> **Origin.** ADR-MONO-004 § 6 ecommerce follow-up. Fourth ecommerce service migrated to v2, mirroring promotion/review/shipping. Single event type (`settlement.period.closed.v1`), no live downstream consumer; money-critical (payout amounts) but producer-only. `@Profile("!standalone")`-gated (standalone keeps the no-op publisher).

---

## Goal

Replace settlement-service's v1 outbox stack with the shared v2 `AbstractOutboxPublisher`, gaining backoff, `eventId`/`eventType` headers, per-event lag metrics, UUID natural key.

**Wire preserved exactly:**
- Topic `settlement.period.closed.v1 → settlement.period.closed`.
- Kafka record **value** = serialized snake_case envelope, byte-identical.
- Kafka record **key** = `aggregateId` = `periodId`.
- Row `event_id` reuses the envelope `event_id`.

## Scope

**In scope (settlement-service only):**
1. `V4__settlement_outbox_v2.sql` — `settlement_outbox` (mirror `master_outbox`; partial pending index). Retain v1 `outbox` (V2) — EntityScan validate. The local `processed_event` dedupe table is unrelated, untouched.
2. `SettlementOutboxEntity extends OutboxRowEntity` + `SettlementOutboxRepository` — both under `com.example.settlement.infrastructure.persistence` (the `@EnableJpaRepositories` base package).
3. Rewrite `SpringSettlementEventPublisher` (`@Profile("!standalone")`) to persist a `SettlementOutboxEntity`: `event_id = UUID.fromString(event.eventId())`, `occurred_at = Instant.parse(event.occurredAt())`, `aggregate_type = "SettlementPeriod"`, `aggregate_id = periodId`, `payload = writeValueAsString(event)` (unchanged). Standalone keeps `NoopSettlementEventPublisher`.
4. `SettlementOutboxPublisher extends AbstractOutboxPublisher<SettlementOutboxEntity>` (`@Profile("!standalone")`) — `TopicResolver` ported verbatim (single type + reject-unmapped), `MicrometerOutboxMetrics("settlement")` + preserved `settlement.outbox.pending.count` gauge, `@Scheduled`.
5. `OutboxConfig` (TransactionTemplate; `Clock` already in `ClockConfig`). Keep lib `OutboxAutoConfiguration`.
6. `settlement.outbox.*` config keys (merged under the existing top-level `settlement:` block — avoids a YAML duplicate-key boot failure).
7. Tests: new `SettlementOutboxPublisherTest` (topic resolve + reject, publish round-trip headers/key/value, mark-published, metrics, gauge, backoff), `SpringSettlementEventPublisherTest` (v2 row + byte-identical payload), `SettlementOutboxRelayIntegrationTest` (`@Tag("integration")`, authored but not CI-run). Delete `SettlementOutboxPollingScheduler`. Add `excludeTags 'integration'` to the test task (matching sibling services) so the IT is excluded from the Docker-free unit lane.
8. Update `specs/services/settlement-service/architecture.md` § Outbox.

**Out of scope:** other ecommerce services; ADR-MONO-004 § 6 edit (deferred to single end-of-series reconciliation); the local `processed_event` dedupe table; payout execution (TASK-BE-416).

## Acceptance Criteria
- **AC-1** topic preserved (`settlement.period.closed`) + reject-unmapped.
- **AC-2** v2 behaviours: headers, backoff, per-eventType metrics.
- **AC-3** wire preserved: value byte-identical; key = periodId; row `event_id` == envelope id.
- **AC-4** `V4` applies on fresh + on top of V1–V3; new entity validates; retained v1 `outbox` still validates; boot behaviour unchanged (existing EntityScan config kept).
- **AC-5** write path persists a `settlement_outbox` row with correct fields; standalone still uses the no-op publisher (no row).
- **AC-6** v1 `SettlementOutboxPollingScheduler` removed (grep clean); write path no longer uses lib `OutboxWriter`.
- **AC-7** `:settlement-service:test` GREEN (Docker-free unit) is the authoritative gate — settlement-service has **no CI Testcontainers lane** (only order/payment); the `@Tag("integration")` IT is not CI-run. Correctness assured by mirroring the CI-validated master/promotion pattern + unit coverage.

## Related Specs
- `projects/ecommerce-microservices-platform/specs/services/settlement-service/architecture.md`
- `docs/adr/ADR-MONO-004-shared-messaging-scaffolding.md` § 5

## Related Contracts
- `projects/ecommerce-microservices-platform/specs/contracts/events/settlement-events.md` — topic + envelope unchanged.

## Dependencies / Prior Work
- **TASK-BE-444/445/446** — ecommerce v2 references.
- **TASK-BE-438** — CI-validated Postgres v2 reference.

## Edge Cases
- `@Profile("!standalone")` preserved on both write path + relay; standalone keeps `NoopSettlementEventPublisher`.
- EntityScan: keep current config (the service `@EntityScan`s `com.example.messaging` + imports the lib auto-config); only ADD `settlement_outbox` — boot behaviour unchanged.
- Repo/entity must live under `com.example.settlement.infrastructure.persistence` (the `@EnableJpaRepositories` base package).
- YAML: `settlement.outbox.*` merged into the existing `settlement:` block (no duplicate top-level key).
- Cutover: in-flight v1 `outbox` rows abandoned (no live consumer, re-derivable).
- No CI IT lane — verification via unit + schema-mirror (AC-7).

## Failure Scenarios
- **F1** in-flight v1 rows abandoned — acceptable.
- **F2** dropping v1 `outbox` → validate boot failure — mitigated by retaining it.
- **F3** wire drift — mitigated by unchanged serialization + exact topic + key=periodId, unit-asserted.
- **F4** YAML duplicate `settlement:` key → boot failure — mitigated by merging under the existing block (the same trap hit + fixed on shipping).
