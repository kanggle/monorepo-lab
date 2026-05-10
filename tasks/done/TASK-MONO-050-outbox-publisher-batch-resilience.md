# Task ID

TASK-MONO-050

# Title

`libs/java-messaging` `OutboxPublisher` batch resilience — permanent vs transient failure classification

# Status

ready

# Owner

backend / monorepo

# Task Tags

- code
- lib
- reliability

---

# Goal

Eliminate the **batch-halt-on-permanent-failure** failure mode in
`libs/java-messaging`'s outbox relay. Today a single corrupt outbox row
(e.g. unknown `event_type` that no subclass's `resolveTopic` matches)
returns `false` from `OutboxPublisher.EventSender.send`, which triggers
`break` in `OutboxPublisher.publishPendingEvents` — every subsequent
PENDING row in the same batch is then stalled until the next polling
cycle, where the same corrupt row will again hit first and break the loop
again. The batch never drains.

Classify failure into **transient** (Kafka unreachable, broker timeout
— retry the same row next cycle, break is correct) vs **permanent**
(unknown event type, unserializable payload — mark the row terminal and
continue draining the batch). This converts a possible **persistent
service-wide stall** into a localized per-row poison-pill quarantine.

Surfaced by TASK-BE-136 code review (PR #345 self-review W2).

---

# Scope

## In Scope

- Evolve `OutboxPublisher.EventSender` to return a 3-value outcome:
  `SUCCESS` / `FAILURE_TRANSIENT` / `FAILURE_PERMANENT`. The current
  `boolean send(...)` signature is the breaking change inside the lib.
- Adjust `OutboxPublisher.publishPendingEvents` per-row dispatch:
  - `SUCCESS` → `row.markPublished()` + continue
  - `FAILURE_TRANSIENT` → leave row in `PENDING` + **break** (preserve the
    existing retry-storm avoidance for broker-wide outages)
  - `FAILURE_PERMANENT` → `row.markFailed(reason)` + continue (drain the
    remainder of the batch; this row will not be picked up again because
    `findPendingWithLock` filters on `status='PENDING'`)
- Add `OutboxJpaEntity.markFailed(String reason)` — sets `status='FAILED'`
  and records `published_at` (= terminal timestamp) and an optional
  `failure_reason TEXT` column.
- Adjust `OutboxPollingScheduler.sendToKafka` to map exception classes to
  outcomes:
  - `IllegalArgumentException` (resolveTopic rejection) → `FAILURE_PERMANENT`
  - `EventSerializationException` (envelope build failed — should never
    happen since the writer already serialized before persist, but is
    catch-all defensive) → `FAILURE_PERMANENT`
  - any other `Exception` → `FAILURE_TRANSIENT`
- Add a new schema migration in `libs/java-messaging` reference Flyway
  fragment (and document the schema delta for downstream services that
  must add the column themselves — most outbox tables are
  service-owned).
- New `onPermanentFailure(eventType, aggregateId, exception)` hook on
  `OutboxPollingScheduler` (default = `log.error` + no-op metric). Mirror
  the existing `onKafkaSendFailure` / `onKafkaSendSuccess` hook shape.
- Update `MicrometerOutboxMetrics` to record `recordPermanentFailure(eventType, reason)`.
- Lib unit tests covering the 3-outcome dispatch + the per-row classification.
- ADR-MONO-004 amendment: add a "Batch Resilience" subsection documenting
  the failure taxonomy + the API evolution (boolean → enum).

## Out of Scope

- Service-level migration of `outbox.failure_reason` columns. This task
  ships the schema reference; each project's service that already has an
  `outbox` table adds the column in its own Flyway migration. List the
  affected services in `## Affected Services` below; the impl PR
  enumerates them but does not migrate them all (they get follow-up
  TASK-MONO-050a / per-project tasks).
- Retry-cap / dead-letter-topic forwarding for permanent failures (a
  future enhancement — for now, `FAILED` rows are operator-inspectable
  via the `outbox` table, no auto-DLT).
- Changes to `AbstractOutboxPublisher` (v2 API path) — that publisher
  uses a different code path; this task targets only the v1
  `OutboxPublisher` + `OutboxPollingScheduler` pair.

---

# Acceptance Criteria

- [ ] `OutboxPublisher.EventSender` interface declares `SendOutcome send(...)` (enum return).
- [ ] `OutboxPublisher.publishPendingEvents` dispatches per outcome: PUBLISHED on SUCCESS, break on FAILURE_TRANSIENT, FAILED + continue on FAILURE_PERMANENT.
- [ ] `OutboxJpaEntity` has `markFailed(String reason)` + new column `failure_reason TEXT` (nullable, default NULL).
- [ ] `OutboxPollingScheduler.sendToKafka` classifies exceptions into transient vs permanent per the mapping above.
- [ ] New `OutboxPollingScheduler.onPermanentFailure` hook (default no-op).
- [ ] Lib unit tests prove the per-outcome dispatch path (SUCCESS / TRANSIENT / PERMANENT).
- [ ] Lib unit tests prove the exception → outcome classification (unknown event type → PERMANENT; generic exception → TRANSIENT).
- [ ] All 6 downstream relay subclasses (order-service, promotion-service, review-service, shipping-service, payment-service, procurement-service) compile + unit tests pass unchanged (sendToKafka is base-only — no subclass override).
- [ ] ADR-MONO-004 amendment landed in the same impl PR (Batch Resilience subsection).
- [ ] Schema delta (`failure_reason` column) documented in the impl PR description for downstream service Flyway migration follow-up.

---

# Related Specs

- `docs/adr/ADR-MONO-004-shared-messaging-scaffolding.md` (target amendment)
- `platform/event-driven-policy.md`
- `libs/java-messaging` source (OutboxPublisher / OutboxPollingScheduler / OutboxJpaEntity / OutboxJpaRepository / EventSender / OutboxMetrics)
- `rules/traits/transactional.md` § T3 (outbox table + polling invariants)
- Surfacing artifact: `projects/ecommerce-microservices-platform/docs/adr/ADR-006-at-least-once-delivery-policy.md` (TASK-BE-136 / PR #345 review W2)

# Related Skills

- `.claude/skills/messaging/outbox-pattern/SKILL.md`

---

# Related Contracts

None — internal lib API evolution only. No external HTTP / event envelope contract changes.

---

# Target Service

- `libs/java-messaging` (shared)
- 6 downstream relay subclasses verified for compile + regression: order-service, promotion-service, review-service, shipping-service, payment-service, procurement-service.

---

# Architecture

`libs/java-messaging` v1 path remains the production target for all 6
existing relays. The v2 `AbstractOutboxPublisher` path is untouched.
This task is a forward-compatible evolution of v1 — bumps `EventSender`
from `boolean` to enum, but only the base class consumes the return
value (subclasses don't override `sendToKafka`), so subclass blast
radius is zero.

---

# Implementation Notes

- The `SendOutcome` enum lives next to `EventSender` in
  `libs/java-messaging/src/main/java/com/example/messaging/outbox/OutboxPublisher.java`.
- `OutboxJpaEntity.status` column is `VARCHAR(20)` — `FAILED` fits.
  Add `failure_reason TEXT` column nullable.
- `findPendingWithLock` already filters `status='PENDING'`, so newly-FAILED
  rows are naturally excluded from subsequent polls. No repository change.
- The `OutboxJpaRepository.findPendingWithLock` returns rows in
  `createdAt ASC` order. Permanent failure of row N does not re-order
  row N+1 — it just continues, preserving original event order.
- Subclasses already implement `onKafkaSendFailure` (per-service failure
  metric). `onPermanentFailure` is a separate hook — subclasses opt in
  by overriding. Default = log + no-op so existing services keep behaving
  the same (transient failures continue to fire `onKafkaSendFailure`).
- `EventSerializationException` is already in lib — confirm catch-block
  reachability.

---

# Edge Cases

- **First row in batch = transient, second row = permanent**: handled by
  break-on-transient (we don't even reach the second row this cycle). Next
  cycle picks up both rows; first is now transient (broker still down) →
  break again. Eventually broker recovers → first succeeds → second
  classified PERMANENT → marked FAILED → continue. Correct.
- **First row = permanent, second row = transient**: first marked FAILED
  + continue → second classified TRANSIENT → break. Batch partially
  drained. Next cycle retries from row 2 (row 1 is no longer PENDING).
  Correct.
- **All rows permanent**: full batch drains in one cycle, all marked
  FAILED. Operator must inspect.
- **Subclass overrides `sendToKafka` directly** (none today; defensive):
  the base class signature change forces compile error → subclass author
  must opt into the new return type. Acceptable.

---

# Failure Scenarios

- **Migration order mismatch (FAILED status enum not added to consumer
  code)**: `OutboxJpaEntity.status` is a `String` column, not a Java
  enum — `findPendingWithLock` uses string equality on `'PENDING'` →
  unchanged. No consumer code reads `status='FAILED'` today, so adding
  the new value is forward-safe. Verified.
- **Downstream service tests assert `status='PUBLISHED'` count**: an
  integration test that intentionally writes a corrupt row would now
  observe `FAILED` instead of "stuck PENDING". Currently no service has
  such a test (corrupt-row IT would require deliberate constructor
  bypass). No regression risk.
- **OutboxPublisher.publishPendingEvents already inside `@Transactional`**:
  per-row `markFailed` mutates the entity; flush at TX commit. Multiple
  PERMANENT rows in one batch → all marked in single commit. Atomic.
  Verified.

---

# Test Requirements

- **Unit (lib)**: `OutboxPublisherTest` — three test cases for the three
  outcomes (SUCCESS / TRANSIENT / PERMANENT), assert the row state after.
- **Unit (lib)**: `OutboxPollingSchedulerTest` — assert exception →
  outcome classification (unknown event type → PERMANENT; Kafka send
  CompletableFuture failure → TRANSIENT).
- **Regression**: all 6 relay subclasses compile + their existing unit
  tests pass.

---

# Definition of Done

- [ ] Lib code changes implemented per the file list above.
- [ ] Lib unit tests added + green.
- [ ] 6 downstream service unit tests run + green (no source change required).
- [ ] ADR-MONO-004 amendment landed (Batch Resilience subsection + SendOutcome enum doc).
- [ ] Impl PR description enumerates downstream service Flyway migration TODO (failure_reason column) as follow-up tasks.
- [ ] Schema delta documented in the impl PR.

---

# Affected Services (regression matrix)

The following relays extend `OutboxPollingScheduler` and use the v1
EventSender boolean → enum API path. All compile + unit-test pass
required:

- `order-service` — `OutboxPollingScheduler` (own subclass in `order/infrastructure/event/`)
- `promotion-service`, `review-service`, `shipping-service` — per ADR-006 audit table
- `payment-service` — `PaymentEventOutboxRelay` (TASK-BE-136)
- `procurement-service` — `ProcurementOutboxPollingScheduler` (TASK-SCM-BE-002 series)

The v2 `AbstractOutboxPublisher` path (used by wms inbound/outbound/inventory)
is untouched.

---

# Provenance

Filed by TASK-BE-136 (PR #345) self-review — code-reviewer surfaced W2
("`OutboxPublisher.publishPendingEvents` breaks on first failure and does
not retry the skipped batch"). This task is the lib-level fix.

분석=Opus 4.7 / 구현 권장=Opus (lib-level invariant change with 6-service
regression surface; transactional semantics + per-row status mutation +
exception classification — non-trivial)
