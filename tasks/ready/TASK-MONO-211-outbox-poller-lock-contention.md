# Task ID

TASK-MONO-211

# Title

`libs/java-messaging` — eliminate the outbox-poller ↔ business-INSERT lock contention by running the poll transaction at READ COMMITTED. `OutboxPublisher.publishPendingEvents` is `@Transactional` (default REPEATABLE READ) and acquires a `PESSIMISTIC_WRITE` lock via `OutboxJpaRepository.findPendingWithLock` (`SELECT … WHERE status='PENDING' … FOR UPDATE`), then performs a **synchronous, blocking `kafkaTemplate.send(...).get()` while still holding that lock**. Under REPEATABLE READ the `FOR UPDATE` takes next-key/gap locks over the PENDING range, so while Kafka is slow/warming the poller blocks concurrent business `INSERT`s into `outbox` for up to `innodb_lock_wait_timeout` (50s) → `1205`/`PessimisticLockingFailureException` → the audit/event write 500s. Running the poll transaction at READ COMMITTED removes gap locking (RC locks only the matched rows, never the gaps), so business INSERTs proceed; the poller still claims its rows exclusively (FOR UPDATE row locks) and delivery semantics (at-least-once, FIFO `ORDER BY created_at`, single-poller exclusivity) are byte-unchanged. Amends ADR-MONO-004 § 4.7.

# Status

ready

# Owner

backend

# Task Tags

- lib
- messaging
- outbox
- bugfix
- concurrency
- adr

---

# Dependency Markers

- **amends**: ADR-MONO-004 (Shared Messaging Scaffolding) — adds § 4.7 "Poller Lock-Contention amendment" (same single-PR amendment convention as § 4.6 Batch Resilience / TASK-MONO-050).
- **surfaced by**: TASK-MONO-207 (spec flakiness) + TASK-MONO-210 (deterministic 500 on write-heavy admin e2e — the compose-log dump showed `1205`/`PessimisticLockingFailureException` on `outbox` INSERT while the poller held its lock during a slow Kafka publish).
- **affects**: every service that uses the shared outbox poller (all 5 backend domains + iam) — the change is in `libs/java-messaging`, so it is a cross-cutting infra fix; behaviour is semantics-preserving (no consumer/ordering change).

# Goal

Stop the shared outbox poller from blocking concurrent business writes: a slow/unavailable Kafka must degrade poller throughput (its own transaction waits on `.get()`) WITHOUT making unrelated `outbox` INSERTs (and therefore the business mutations that emit them) fail with a lock-wait timeout. Preserve at-least-once + FIFO delivery exactly.

# Scope

- `libs/java-messaging/.../outbox/OutboxPublisher.java` — annotate `publishPendingEvents` with `@Transactional(isolation = Isolation.READ_COMMITTED)` (+ explanatory comment). No other behaviour change (claim/mark/break logic byte-identical).
- `libs/java-messaging/.../outbox/OutboxPublisherTest.java` — add a defensive reflection test asserting the method carries `@Transactional` at READ COMMITTED (guards against an accidental future revert); existing behaviour tests stay green.
- `docs/adr/ADR-MONO-004-shared-messaging-scaffolding.md` — § 4.7 amendment (decision, rationale, semantics-preservation analysis, alternatives, the future claim-then-publish option deferred).

**Out of scope** (documented as deferred in the ADR amendment):
- claim-then-publish refactor (publish entirely outside the DB transaction + a CLAIMED state with crash-recovery) — the larger structural fix; RC removes the observed contention with far less risk.
- `SKIP LOCKED` — only relevant for multiple concurrent poller instances (today each service runs a single `@Scheduled` poller); orthogonal to the single-poller gap-lock-blocks-INSERT problem RC solves.

# Acceptance Criteria

- **AC-1** `OutboxPublisher.publishPendingEvents` runs at `Isolation.READ_COMMITTED` (asserted by a reflection test on the `@Transactional` annotation).
- **AC-2** Claim/mark/break behaviour is unchanged: SUCCESS→PUBLISHED+continue, FAILURE_TRANSIENT→PENDING+break, FAILURE_PERMANENT→FAILED+continue, no-pending→no sender call (existing `OutboxPublisherTest` cases pass byte-identically).
- **AC-3** `:libs:java-messaging:test` green; the shared lib + at least one consuming service (`compileJava`) build clean.
- **AC-4 (behavioural, CI)** The federation-hardening-e2e suite no longer exhibits the lock-wait 500 under Kafka warm-up: a `gh workflow run federation-hardening-e2e.yml` run is GREEN with the MONO-207 + MONO-210 specs passing (ideally without relying on the MONO-210 spec's warm-up gate masking the issue — the gate stays as defence-in-depth).
- **AC-5** ADR-MONO-004 § 4.7 documents the decision + semantics-preservation + deferred alternatives.

# Related Specs

- `docs/adr/ADR-MONO-004-shared-messaging-scaffolding.md` (the scaffolding this amends)
- `platform/shared-library-policy.md` (the shared-lib boundary the change respects — no project-specific content)

# Related Contracts

- none (no wire/event contract changes — delivery semantics preserved)

# Edge Cases

- RC + `SELECT … FOR UPDATE` still row-locks the matched PENDING rows for the transaction's duration, preventing a (future) second poller from double-claiming; only the GAP locks (which blocked INSERTs) are dropped. Single-poller exclusivity is therefore preserved.
- The poller reads the PENDING batch exactly once then writes — no re-read — so RC's weaker repeatable-read guarantee is irrelevant to correctness here.
- A slow/unavailable Kafka still holds the poll transaction open during `.get()`; under RC this no longer blocks business INSERTs, so the only effect is reduced poller throughput (its own batch waits), which is the intended degrade mode.
- FK/unique-constraint checks still take the minimal locks they need under RC (irrelevant to the outbox INSERT path, which has no FK to the locked rows).

# Failure Scenarios

- If the isolation were left at REPEATABLE READ, the gap lock recurs and any write-heavy mutation concurrent with a slow Kafka publish 500s (the MONO-210 failure) — AC-1/AC-4 guard it.
- If the FOR UPDATE were dropped instead of changing isolation, a future multi-instance deployment could double-publish — the fix keeps FOR UPDATE (row-level exclusivity) and only changes isolation, so single-poller and future multi-poller correctness both hold.
- If `publishPendingEvents` were made non-transactional to "avoid the lock", a partial batch (some rows marked PUBLISHED, then a crash) would lose the atomic mark+continue guarantee — the fix keeps the transaction, only relaxing its isolation.
