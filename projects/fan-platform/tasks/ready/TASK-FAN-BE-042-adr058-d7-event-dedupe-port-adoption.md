# Task ID

TASK-FAN-BE-042

# Title

ADR-MONO-058 D7 (fan-platform only, `EventDedupePort` sub-pattern) — replace notification-service's
hand-rolled `ProcessedEventStore` check-then-act idempotency with the shared `EventDedupePort` from
`libs/java-messaging`

# Status

ready

# Owner

backend

# Task Tags

- code
- test
- messaging

---

# Required Sections (must exist)

- Goal
- Scope (in/out)
- Acceptance Criteria
- Related Specs
- Related Contracts
- Edge Cases
- Failure Scenarios

If any section is missing or incomplete, this task must not be implemented.

---

# Goal

Close fan-platform's share of `ADR-MONO-058 § D7` (ACCEPTED 2026-07-30) — the `EventDedupePort` sub-pattern
only. `§ 1.1`'s audit table lists fan under "`EventDedupePort` hand-rolled instead of adopted — wms, erp,
ecommerce, fan". `§ D7`: "Both already exist in `libs/java-messaging` and `libs/java-common` respectively.
`.claude/skills/messaging/idempotent-consumer/SKILL.md` already instructs 'use the shared port — do not
hand-roll' and is being ignored by roughly 9 services… No ADR content needed here beyond stating the
adoption is expected — implementation is straightforward per-service substitution work."

**`ResilienceClientFactory` is explicitly NOT part of this task.** fan is not listed under the
`ResilienceClientFactory` sub-pattern in `§ 1.1`'s audit table (that row names only
console-bff/iam/ecommerce) — do not file or implement that adoption here.

---

## Measured against the tree — what is actually duplicated (not the ADR's paraphrase)

`notification-service` is the **only** event-consumer in fan-platform (confirmed: grep for
`@KafkaListener` across `projects/fan-platform/apps` returns matches only in
`notification-service/application/consumer/{CommunityEventConsumer,MembershipEventConsumer}.java`;
`community-service`/`artist-service`/`membership-service` are outbox **producers**, not consumers, and
`gateway-service` is a pure edge proxy).

notification-service's idempotency mechanism, as it exists today:

```java
// application/port/ProcessedEventStore.java
public interface ProcessedEventStore {
    boolean alreadyProcessed(String eventId);
    void markProcessed(String eventId, String eventType);
}

// infrastructure/messaging/idempotency/JpaProcessedEventStore.java
@Component @RequiredArgsConstructor
public class JpaProcessedEventStore implements ProcessedEventStore {
    public boolean alreadyProcessed(String eventId) { return repository.existsByEventId(eventId); }
    public void markProcessed(String eventId, String eventType) { repository.save(...); }
}
```

— a **two-call, check-then-act** shape: the use case calls `alreadyProcessed()`, branches, then calls
`markProcessed()` separately. This is exactly the hand-rolled pattern `libs/java-messaging.EventDedupePort`
exists to replace:

```java
// libs/java-messaging … dedupe/EventDedupePort.java
public interface EventDedupePort {
    Outcome process(UUID eventId, String eventType, Runnable work);
    enum Outcome { APPLIED, IGNORED_DUPLICATE, FAILED }
}
```

— a **single-call, PK-violation-detected** shape: `process(...)` inserts the dedupe row and runs `work` in
one method, catching PK-violation as the duplicate signal rather than doing an existence check first (which
has a narrower TOCTOU window under concurrent delivery of the same event to two consumer threads/instances
— the exact class of bug a dedupe mechanism exists to prevent).

`libs/java-messaging` is **already a declared dependency** of `notification-service`
(`build.gradle` line 71) — this is a pure adoption gap, no new dependency needed.

---

# Scope

## In Scope

- Replace `notification-service`'s `ProcessedEventStore` port + `JpaProcessedEventStore` adapter with the
  shared `EventDedupePort` contract:
  - Either implement a thin JPA-backed `EventDedupePort` adapter reusing the **existing**
    `ProcessedEventJpaRepository`/`ProcessedEventJpaEntity` persistence (rename/adapt as needed to fit the
    `process(eventId, eventType, work)` shape and PK-violation-based duplicate detection), or — check first
    — adopt a ready-made JPA adapter if `libs/java-messaging` already ships one (read the module before
    assuming it does not).
  - Refactor the two call sites (`HandleCommunityEventUseCase`, `HandleMembershipEventUseCase`, or wherever
    `alreadyProcessed`/`markProcessed` are currently invoked) from the check-then-act shape to
    `dedupePort.process(eventId, eventType, () -> { /* existing use-case body */ })`.
  - Preserve the existing transactional-atomicity guarantee documented on `ProcessedEventStore`'s javadoc:
    the dedupe row commits atomically with the `Notification` row, in the same transaction. Verify the
    `EventDedupePort` contract's own guarantee ("If `eventId` already exists → return
    `IGNORED_DUPLICATE`… If `work` throws → bubble the exception up; the surrounding transaction rolls
    back, including the dedupe row") matches this requirement before assuming it does.
  - `eventId` type: the port takes `UUID`; the current code takes `String`. Confirm the actual eventId
    values flowing through notification-service's consumers are UUIDv7 (per the port's own javadoc,
    "inbound event identifier (UUIDv7 from the envelope)") and convert at the boundary — do not silently
    widen the port's contract to accept non-UUID strings.
- Delete the now-redundant `ProcessedEventStore` interface (or keep it as a thin service-owned wrapper only
  if a genuine service-specific need is found — default assumption is deletion, since the port's contract
  fully subsumes it).
- One PR, notification-service only.

## Out of Scope

- **`ResilienceClientFactory` adoption.** Not applicable to fan-platform per `§ 1.1`'s audit table — do not
  file or implement it as part of this task or any follow-up from it.
- **Other projects' D7 `EventDedupePort` adoption** (wms, erp, ecommerce) — separate future tasks in their
  own projects.
- **The event-consuming use cases' domain logic** (`HandleCommunityEventUseCase`/
  `HandleMembershipEventUseCase`'s actual notification-creation behavior) — only the idempotency wrapping
  changes, not what happens inside `work`.
- **The outbox/producer side** of any fan-platform service — D7's `EventDedupePort` is consumer-side only;
  producer-side outbox publishing (`AbstractOutboxPublisher`, already adopted per `TASK-FAN-BE-020/021/022`)
  is untouched.
- **`ADR-MONO-058 § D8`** (`TenantContext`/`TenantClaimEnforcer`) — ecommerce-only, unrelated.

---

# Acceptance Criteria

- [ ] `notification-service`'s `ProcessedEventStore`/`JpaProcessedEventStore` check-then-act shape is
      replaced by the shared `EventDedupePort.process(eventId, eventType, work)` single-call shape at both
      consumer call sites (`CommunityEventConsumer`'s use case, `MembershipEventConsumer`'s use case).
- [ ] Repo-wide grep for `interface ProcessedEventStore` / `class JpaProcessedEventStore` under
      `projects/fan-platform/apps/notification-service/src/main` → **0 hits** (unless a genuine
      service-specific need for the old interface was found and explicitly justified in the PR body — the
      default expectation is deletion).
- [ ] Dedupe-row persistence continues to commit atomically with the `Notification` row in the same
      transaction — verified by a test that fails the use-case body mid-way (throws) and asserts **neither**
      the dedupe row **nor** the notification row persisted (rollback of both).
- [ ] Duplicate delivery of the same `eventId` is verified to still be a no-op: second delivery →
      `Outcome.IGNORED_DUPLICATE`, `work` is **not** re-run (no duplicate `Notification` row created).
- [ ] `eventId` UUID conversion at the consumer boundary verified correct against real envelope values (not
      a synthetic UUID fixture only).
- [ ] Guard mutation-check: temporarily break the dedupe check (e.g. always return `APPLIED`) and confirm at
      least one test goes RED for duplicate-delivery re-processing, then revert.
- [ ] No other fan-platform service touched — this is `notification-service` only.
- [ ] Test-count parity recorded (before/after); no test lost or weakened.
- [ ] `./gradlew :notification-service:check` GREEN. CI `Integration (fan-platform, Testcontainers)` and the
      `IdempotentConsumeIntegrationTest` lane GREEN — authoritative (local Windows Docker is not,
      `project_testcontainers_docker_desktop_blocker`).

---

# Related Specs

> **Before reading Related Specs**: Follow `platform/entrypoint.md` Step 0 — read `PROJECT.md`, then load
> `rules/common.md` plus any `rules/domains/<domain>.md` and `rules/traits/<trait>.md` matching the
> declared classification. Unknown tags are a Hard Stop per `CLAUDE.md`.

- `docs/adr/ADR-MONO-058-fleet-wide-shared-technical-scaffolding-consolidation.md` § D7, § 1.1, § 6 item 2
  (ACCEPTED 2026-07-30)
- `tasks/ready/TASK-MONO-495-adr-058-fleet-scaffolding-tracking.md` (the tracking task this splits from)
- `libs/java-messaging/src/main/java/com/example/messaging/dedupe/EventDedupePort.java` (the shared port —
  read its full javadoc contract before implementing an adapter)
- `.claude/skills/messaging/idempotent-consumer/SKILL.md` (the skill this task is closing the ignored-gap
  against — "use the shared port — do not hand-roll")
- `rules/traits/transactional.md` § T8 (the authoritative reference `EventDedupePort`'s own javadoc cites)
- `platform/testing-strategy.md`
- `projects/fan-platform/specs/services/notification-service/architecture.md` § Idempotency
- `projects/fan-platform/tasks/done/TASK-FAN-BE-013-notification-service-implementation.md` (origin of
  `ProcessedEventStore`/`JpaProcessedEventStore`)
- `projects/fan-platform/tasks/done/TASK-FAN-BE-026-v2-notification-wiring-community-events.md`,
  `…/TASK-FAN-BE-035-notification-envelope-parser-dedup.md` (recent history of the consumer call sites this
  task refactors)
- `projects/fan-platform/tasks/done/TASK-FAN-BE-038-adr058-d2-error-envelope-shared-handler-adoption.md`,
  `…/TASK-FAN-BE-039-adr058-d5-public-paths-shared-value-type.md`,
  `…/TASK-FAN-BE-040-adr058-d1-actor-jwt-claim-cluster.md` — prior art for this project's `ADR-MONO-058`
  adoption-task governance shape (before/after test-count table, guard mutation-check, explicit statement
  of observable-behaviour deltas)

---

# Related Contracts

None — `EventDedupePort` adoption is an internal consumer-side idempotency mechanism with no wire-format or
event-schema change. The inbound event contracts (`projects/fan-platform/specs/contracts/events/*.md`) are
read-only context; this task does not alter `eventId`, envelope shape, or any published field.

---

# Target Service

- `notification-service` (fan-platform)
- Consumes `libs/java-messaging` (already a declared dependency; not modified by this task)

---

# Architecture

Follow `notification-service/architecture.md`. The dedupe adapter replaces
`infrastructure/messaging/idempotency/JpaProcessedEventStore.java` in the same package; the
`application/port/ProcessedEventStore.java` interface is deleted (or, if retained, becomes a thin
service-owned wrapper — default is deletion per Scope). `ProcessedEventJpaRepository`/
`ProcessedEventJpaEntity` may be reused as the persistence layer underneath the new adapter — the task does
not require a schema change unless `EventDedupePort`'s PK-violation-based duplicate detection requires a
different table shape than the existing `existsByEventId` check-then-act query pattern (verify before
assuming either way).

---

# Implementation Notes

- Order of work: (1) read `EventDedupePort`'s full javadoc contract and check whether `libs/java-messaging`
  ships a ready-made JPA adapter class before writing a new one; (2) write/adapt the JPA-backed
  `EventDedupePort` implementation, reusing the existing `processed_events` table if its schema already
  supports PK-violation detection on `event_id` (check the existing unique constraint); (3) refactor one
  consumer call site (`CommunityEventConsumer`'s use case) end-to-end including its test; (4) replicate to
  `MembershipEventConsumer`'s use case; (5) delete `ProcessedEventStore`/`JpaProcessedEventStore` once both
  call sites are migrated and green.
- `IdempotentConsumeIntegrationTest` (existing, per the file list) already exercises duplicate-delivery
  behavior at the integration level — read it first; it may already give the regression net this task
  needs with only its internals (not its assertions) needing to change to match the new port shape.
- `Outcome.FAILED` is documented as "reserved for implementations that catch `work`'s exception (most do
  not)" — do not implement a catch-and-report-FAILED behavior unless a specific existing failure-handling
  requirement in notification-service's Kafka error-handling config demands it; default to letting `work`'s
  exception bubble (matches the port's own stated default).

---

# Edge Cases

- **Concurrent duplicate delivery** (two consumer threads/instances processing the same `eventId`
  simultaneously) — the whole reason `EventDedupePort`'s PK-violation-based detection is safer than
  check-then-act's TOCTOU window. If feasible, add a test that simulates this (e.g. two threads racing on
  the same `process()` call) to prove the new mechanism actually closes the gap the old one had, not just
  that it behaves identically to the old one on the sequential-delivery cases already tested.
- **`eventId` format assumption.** The port's javadoc specifically says "UUIDv7 from the envelope" — confirm
  fan-platform's event envelopes actually carry a UUIDv7-parseable `eventId` string for both
  `community.*` and `fan.membership.*` events consumed here (check `EventEnvelope`, promoted in
  `TASK-FAN-BE-035`) before assuming the `String → UUID` conversion at the boundary never throws.
- **Retention/cleanup of the dedupe table.** `EventDedupePort`'s own javadoc says "the persistence
  implementation lives in each service's adapter layer because the dedupe table's retention policy … is
  service-specific" — this task does not need to add retention/cleanup logic that doesn't already exist;
  do not invent one as unrequested scope.

---

# Failure Scenarios

- **TOCTOU regression disguised as a refactor.** If the new adapter re-implements
  `process()` internally as "check `existsByEventId`, then `save`" (i.e. the same two-step logic just
  wrapped in a single method signature), it inherits the exact race condition `EventDedupePort`'s contract
  exists to close, while looking adopted. The adapter must actually rely on a PK/unique-constraint
  violation for duplicate detection (insert-first, catch violation), not an existence pre-check.
- **Breaking transactional atomicity.** If the dedupe-row insert and the `Notification` row insert end up in
  different transactions (e.g. because the new adapter opens its own transaction), a crash between the two
  could leave a dedupe row with no corresponding notification (silent message loss) or vice versa (silent
  reprocessing gap). The AC-required rollback test exists specifically to catch this.
- **Scope creep into `ResilienceClientFactory`.** fan is not in that sub-pattern's affected-project list —
  implementing it here would be unrequested scope inconsistent with the ADR's own audit table.
- **Silent `eventId` truncation/collision from a bad `String → UUID` conversion.** If envelope `eventId`
  values are not actually well-formed UUIDs for some event type, a naive `UUID.fromString(...)` throws at
  runtime on a previously-working code path — verify against real envelope values, not a synthetic fixture.

---

# Test Requirements

- Unit/adapter test for the new `EventDedupePort` JPA implementation: first delivery → `APPLIED`, work runs;
  duplicate `eventId` → `IGNORED_DUPLICATE`, work does not run; `work` throws → transaction (including the
  dedupe row) rolls back.
- Concurrent-duplicate-delivery test if feasible (see Edge Cases).
- `IdempotentConsumeIntegrationTest` adapted to the new port shape, passing with duplicate-delivery
  assertions intact.
- `HandleCommunityEventUseCaseTest` / equivalent for membership events — updated for the new
  `dedupePort.process(...)` call shape, assertions preserved (no notification created twice; notification
  created once on first delivery).
- Before/after test-count table for `notification-service`, 0 failures/errors/skipped both sides.
- `./gradlew :notification-service:check` GREEN. CI `Integration (fan-platform, Testcontainers)` GREEN
  authoritative.

---

# Definition of Done

- [ ] `ProcessedEventStore`/`JpaProcessedEventStore` replaced by shared `EventDedupePort` adoption at both
      consumer call sites
- [ ] Transactional atomicity (dedupe row + notification row, same transaction) verified by test
- [ ] Duplicate-delivery no-op behavior verified; guard mutation-check recorded
- [ ] `ResilienceClientFactory` explicitly NOT touched (confirmed out of scope per ADR audit table)
- [ ] Test-count parity recorded; `:notification-service:check` + CI Integration lane GREEN
- [ ] Ready for review
