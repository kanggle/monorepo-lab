# TASK-FIN-BE-009 — ledger-service GL/AP feed (3rd increment: transactional outbox + event emission)

**Status:** review

**Type:** TASK-FIN-BE
**Analysis model:** Opus 4.8 / **Recommended impl model:** Opus (complex domain — outbox introduction, terminal→publishing consumer transition, exactly-once emission semantics, event-contract finalization)

---

## Goal

Deliver the **GL/AP-feed** increment forward-declared by
`specs/services/ledger-service/architecture.md` § Increment Scope — the increment
the period-close increment (TASK-FIN-BE-008) explicitly deferred event emission to
("emission lands with the GL/AP-feed increment that introduces the outbox — that
increment will emit both `entry.posted.v1` and `period.closed.v1`").

ledger-service transitions from a **terminal consumer** to a **publishing
consumer**: it gains a **transactional outbox** and emits the two
forward-declared events as the **forward interface for an external accounting /
ERP / AP system**:

- **`finance.ledger.entry.posted.v1`** — appended for **every** posted journal
  entry (auto-journal + reversal), in the SAME `@Transactional` as the entry +
  audit row (atomic — the GL feed can never diverge from the books).
- **`finance.ledger.period.closed.v1`** — appended when an accounting period is
  closed, in the SAME `@Transactional` as the close + snapshot.

**Outbox path decision — per-service `OutboxRow`, NOT the libs `OutboxWriter`.**
The libs `OutboxAutoConfiguration` (`OutboxWriter` path) entity-scans the libs
`ProcessedEventJpaEntity` (mapped to `processed_events`), which would **collide**
with ledger-service's OWN `processed_events` consumer-dedupe table (different
schema — that collision is exactly why the first increment excluded
`OutboxAutoConfiguration`). So this increment uses the **`AbstractOutboxPublisher`
+ per-service `OutboxRow` entity** path (ADR-MONO-004; the wms inbound/inventory/
outbound services are the reference) — ledger keeps `OutboxAutoConfiguration` (and
`OutboxMetricsAutoConfiguration`) **excluded** and adds its own `ledger_outbox`
table + relay. The consumer dedupe path is untouched.

## Scope

**Spec PR (this task → ready):** authored already —
`specs/services/ledger-service/architecture.md` (§ Identity Event-publication row
None→the two topics; § Increment Scope moves GL/AP feed IN + records the
`OutboxRow`-path decision + the terminal→publishing-consumer transition; new
§ Event publication; § Failure Modes outbox rows; § Layer Structure outbox
package) + `specs/contracts/events/finance-ledger-events.md` (move the two events
from "Published — forward-declared / NOT emitted" to "Published — emitted";
finalize the payload + envelope shapes).

**Impl PR — IN:**
- **Per-service outbox** (`infrastructure/outbox/` + persistence):
  - `LedgerOutboxJpaEntity implements OutboxRow` (`@Table("ledger_outbox")`,
    MySQL — `payload TEXT` [NOT Postgres `jsonb`; account-service `outbox` is the
    MySQL precedent], `id UUID`, aggregate_type/aggregate_id/event_type/
    event_version/partition_key/created_at/published_at) + `LedgerOutboxJpaRepository`
    (`findPending(Pageable)` ordered by created_at asc, `countByPublishedAtIsNull()`).
  - `LedgerOutboxPublisher extends AbstractOutboxPublisher<LedgerOutboxJpaEntity>`
    — `@Scheduled` relay; `TopicResolver` `finance.ledger.X → finance.ledger.X.v1`;
    `MicrometerOutboxMetrics(meterRegistry, "ledger")`; reuses the EXISTING
    `KafkaTemplate<String,String>` bean (already present for `@RetryableTopic` DLT);
    `@Profile`/`@ConditionalOnProperty` gate so slice/unit runs skip background
    polling (wms precedent).
  - `TransactionTemplate` bean (the relay marks-published in a fresh Tx) +
    `ledger.outbox.*` properties (batch-size, polling-interval).
- **Append-side publisher** `LedgerEventPublisher` (application or infrastructure):
  builds the **canonical envelope** (the same shape ledger-service's own consumer
  parses — `{eventId, eventType, occurredAt, tenantId, source, aggregateType,
  aggregateId, payload}`, `source = "finance-platform-ledger-service"`) and
  persists a `ledger_outbox` row. Two methods:
  - `publishEntryPosted(JournalEntry)` → payload `{ entryId, postedAt,
    lines:[{ledgerAccountCode, direction, money:{amount,currency}}], source:
    {sourceType, sourceTransactionId, sourceEventId}, reversalOfEntryId? }`,
    aggregateType `JournalEntry`, aggregateId/partitionKey `entryId`.
  - `publishPeriodClosed(AccountingPeriod, PeriodBalanceSnapshot)` → payload
    `{ periodId, from, to, closedAt, entryCount }`, aggregateType
    `AccountingPeriod`, aggregateId/partitionKey `periodId`.
  - Wired INTO the existing write boundaries: `PostJournalEntryUseCase.post`
    appends `entry.posted` after the entry + audit save (same Tx); 
    `CloseAccountingPeriodUseCase.close` appends `period.closed` after the close +
    snapshot save (same Tx). **Atomic** — the outbox row commits with the domain
    write or not at all (transactional outbox; F1/T3).
- **Persistence (Flyway `V3__create_ledger_outbox.sql`)**: `ledger_outbox`
  (InnoDB/utf8mb4, `published_at` nullable, `idx_ledger_outbox_unpublished` on
  `(published_at, created_at)` or `(created_at)` filtered) — schema matching the
  `OutboxRow` accessors. Money in payloads stays minor-units string (F5).
- **Config**: un-comment the terminal-consumer note in `KafkaConsumerConfig`
  (the `KafkaTemplate` is now also the outbox transport — still the same bean);
  `LedgerServiceApplication` keeps `OutboxAutoConfiguration` +
  `OutboxMetricsAutoConfiguration` excluded (own outbox entity).
- **Tests**:
  - Unit: `LedgerEventPublisher` builds the exact envelope + payload for both
    events (entry-posted incl. reversal flag; period-closed); a `ledger_outbox`
    row is created with the right aggregateType/eventType/partitionKey.
  - `PostJournalEntryUseCase` / `CloseAccountingPeriodUseCase` — the publisher is
    invoked in-Tx (mock the publisher port; verify call + ordering after save).
  - `LedgerOutboxPublisher` topic resolution unit (`finance.ledger.entry.posted →
    finance.ledger.entry.posted.v1`).
  - **Integration** (Testcontainers MySQL + real Kafka + WireMock JWKS): the full
    round-trip — produce a `finance.transaction.completed.v1` (TOPUP/TRANSFER) →
    ledger posts the entry → a `ledger_outbox` row appears → the relay publishes
    → **consume `finance.ledger.entry.posted.v1`** from Kafka in the test and
    assert the envelope + payload (balanced lines). Close a period →
    **consume `finance.ledger.period.closed.v1`** and assert `{periodId, from, to,
    closedAt, entryCount}`. A guard-rejected posting into a closed period emits
    **no** `entry.posted` (no outbox row). The pre-existing IT scenarios still pass
    (net-zero on the consume/post/read paths).

**Impl PR — OUT (still forward-declared):** reconciliation matching; manual
journal posting; multi-currency; an external GL/AP *consumer* (this increment
ships the producer/topics only — there is no in-repo consumer of the ledger feed
yet); a console GL-export view.

## Acceptance Criteria

- **AC-1 (entry.posted emission)** — every successfully posted journal entry
  (auto-journal + reversal) appends exactly one `finance.ledger.entry.posted`
  outbox row in the posting `@Transactional`; the relay publishes it to
  `finance.ledger.entry.posted.v1` with the contract payload (entryId, postedAt,
  balanced lines, source, reversalOfEntryId?). An Integration test consumes the
  topic and asserts the payload.
- **AC-2 (period.closed emission)** — closing a period appends one
  `finance.ledger.period.closed` outbox row in the close `@Transactional`; the
  relay publishes `finance.ledger.period.closed.v1` with `{periodId, from, to,
  closedAt, entryCount}`. An Integration test consumes and asserts it.
- **AC-3 (atomic / transactional outbox)** — the outbox row commits atomically
  with the domain write (entry+audit / close+snapshot); a guard-rejected posting
  into a CLOSED period (`LEDGER_PERIOD_CLOSED`) emits **no** outbox row (the whole
  Tx rolls back). No emission outside a domain write path.
- **AC-4 (per-service OutboxRow path / no collision)** — ledger-service keeps the
  libs `OutboxAutoConfiguration` + `OutboxMetricsAutoConfiguration` **excluded**;
  the consumer's own `processed_events` table is untouched; the new outbox lives
  in `ledger_outbox` via `LedgerOutboxJpaEntity implements OutboxRow`. The app
  starts (no duplicate `processed_events` entity mapping).
- **AC-5 (envelope + money)** — emitted events carry the canonical envelope the
  ledger consumer parses (`{eventId, eventType, occurredAt, tenantId, source,
  aggregateType, aggregateId, payload}`); all money is minor-units string +
  currency (grep-zero float/double in the publisher); no regulated PII (F7 — ids +
  amounts only).
- **AC-6** — `:ledger-service:check` BUILD SUCCESSFUL; **CI "Integration
  (finance-platform, Testcontainers)" GREEN** — the authoritative gate: real
  Kafka consume→post→**outbox→relay→publish**→re-consume round-trip for both
  events. No deploy-wiring change beyond the `V3` migration (ledger-service already
  wired by FIN-BE-007); new topics auto-created / pre-created in the IT.

## Related Specs

- `projects/finance-platform/specs/services/ledger-service/architecture.md` (this PR — governing; § Event publication + § Increment Scope OutboxRow-path decision)
- `projects/finance-platform/specs/services/account-service/architecture.md` (the finance outbox precedent — `BaseEventPublisher`/`OutboxWriter` path)
- `docs/adr/ADR-MONO-004-shared-messaging-scaffolding.md` (the `AbstractOutboxPublisher` + `OutboxRow` v2 path)

## Related Contracts

- `projects/finance-platform/specs/contracts/events/finance-ledger-events.md` (this PR — the two events move forward-declared → emitted)
- `platform/error-handling.md` (no new codes — outbox failures are ops/metrics, not API errors)
- precedent: `projects/wms-platform/apps/inventory-service` (`OutboxRow` entity + `AbstractOutboxPublisher` relay + `TopicResolver`, consumes AND publishes)

## Edge Cases

- **processed_events collision** — the libs `OutboxWriter` path is forbidden here
  (it pulls in the libs `ProcessedEventJpaEntity` mapped to `processed_events`,
  colliding with the ledger's own consumer-dedupe table). Use the `OutboxRow` path.
- **Every entry emits** — auto-journal AND reversal entries each emit
  `entry.posted` (the GL feed wants every confirmed movement); a HOLD/RELEASE
  (no entry) emits nothing (no entry → no `post()` → no row).
- **Guard-rejected posting** — a posting into a CLOSED period throws before the
  outbox append → the Tx rolls back → no `entry.posted` row (AC-3).
- **At-least-once delivery** — the relay is at-least-once (Kafka). Downstream
  consumers dedupe on `eventId` (the envelope carries it). Documented; no in-repo
  consumer in this increment.
- **partition key** — `entry.posted` keyed by `entryId`, `period.closed` by
  `periodId` (entries/periods are independent; no cross-entry ordering needed).
- **MySQL payload column** — `TEXT` (account-service precedent), NOT Postgres
  `jsonb` (the wms reference is Postgres).
- **Relay gating in non-Kafka runs** — `@Profile`/`@ConditionalOnProperty` so
  `@WebMvcTest`/unit runs don't start the scheduler (wms precedent).

## Failure Scenarios

- **F1 — GL feed diverges from the books** — if emission were a separate
  post-commit publish it could be lost after the entry commits. Guarded by the
  transactional outbox: the row commits in the SAME Tx as the entry (AC-1/AC-3).
- **F2 — processed_events duplicate-mapping crash** — including
  `OutboxAutoConfiguration` would register a 2nd `processed_events` entity →
  startup failure. Guarded by AC-4 (OutboxRow path, exclusion preserved); an
  Integration boot is the proof.
- **F3 — double-publish on relay retry** — a Kafka ACK lost after send would
  re-publish. Tolerated (at-least-once); consumers dedupe on `eventId`. The relay
  marks-published only after ACK (lib `AbstractOutboxPublisher`).
- **F4 — emission on a rolled-back posting** — a closed-period rejection must NOT
  emit. Guarded by appending the row inside the same Tx that throws (AC-3); an
  Integration assertion proves no `entry.posted` for the rejected event.
- **F5 — Docker-free `:check` passes but the relay/round-trip is broken** — the
  unit/slice tests don't exercise real Kafka publish→re-consume; the Testcontainers
  Integration job is the authoritative gate (AC-6).
