# TASK-BE-442 — Rework the stranded-refund durability IT to inject a genuinely-concurrent VOIDED-during-capture interleave

**Status:** done (superseded by [[TASK-BE-443]])

**Type:** TASK-BE
**Analysis model:** Opus 4.8 / **Recommended impl model:** Opus (concurrency-modeling test rework that must prove a money-safety durability invariant without re-introducing flakiness)

**Service:** payment-service

> **SUPERSEDED (2026-06-26) by [[TASK-BE-443]].** Mapping confirm()'s control flow to make this IT reachable revealed the rework is **infeasible test-only**: confirm()'s post-capture re-read (`findByOrderId`, same `@Transactional` Hibernate Session) returns the **stale L1-managed PENDING** entity and discards the freshly-committed VOIDED columns (session entity-identity, above DB isolation) — so a concurrently-committed VOIDED is invisible and the stranded path is never reached. That is a **latent production money-safety defect** (the BE-435/437 post-capture guard is dead for the very race it exists to handle; no `@Version` → last-writer-wins COMPLETED + `PaymentCompleted` on a cancelled order). The IT rework therefore folds into **TASK-BE-443** (the production fresh-read fix + this IT, which becomes the regression proof). This task is closed as superseded; no separate implementation.

> **Origin.** Surfaced by **TASK-BE-440**. Once BE-440 made the `OrderCancelled` consumer commit `VOIDED` in a proper transaction, `PaymentRefundStrandedDurabilityIntegrationTest.strandedEscalation_survivesConfirmRollback` started failing (`Expected size: 1 but was: 0`) — its **sequential** arrange became **unreachable**. The test is **quarantined** with `@Disabled("TASK-BE-442: …")` (the two BE-440 consumer-tx ITs stay lifted); this task reworks it so the BE-437 AC-2 durability invariant is actually exercised, then lifts the quarantine.

---

## Goal

`PaymentRefundStrandedDurabilityIntegrationTest` means to prove **BE-437 AC-2 / the REQUIRES_NEW durability invariant**: when `confirm()` captures funds for a concurrently-cancelled order and the post-capture auto-refund fails at the PG, the `PaymentRefundStranded` escalation outbox row — written in a `REQUIRES_NEW` tx — **survives** the `confirm()` parent-transaction rollback.

The current arrange (lines 149-150) replays `OrderPlaced` then `OrderCancelled` **sequentially and fully-committed**, then calls `confirm()`. After **TASK-BE-440** this can no longer reach the stranded path: the now-committed `VOIDED` row is seen by `confirm()`'s **pre-capture** state guard (`PaymentConfirmService.confirm` ~line 46), which throws `PaymentAlreadyCompletedException` **before** the PG capture (~line 58). So `confirm()` never captures, never reaches the post-capture re-read (~line 88) or the stranded-escalation `record(...)` (~line 104) → **0 rows**. (The `assertThatThrownBy` at ~line 153 still passes, masking that it short-circuited for the wrong reason.)

This is a **test-design defect, not a production defect**: production `confirm()` is correct and money-safe in every ordering — rejecting a committed-`VOIDED` order *before* capturing funds is the desired behaviour (it never calls the PG, so there is nothing to strand). The genuine stranded scenario is a **race**: `VOIDED` commits **after** `confirm()`'s initial read but **during** the slow PG capture, so the **post-capture** re-read is the first to observe `VOIDED` and must auto-refund → the auto-refund fails → stranded.

## Scope (payment-service test rework only)

1. **Rework the arrange to model the real interleave.** Drive the `VOIDED` transition to commit **during** the `confirmPayment(...)` PG-capture call, not before it — e.g. a Mockito `Answer` on the gateway-port `confirmPayment` mock that, before returning a successful capture, commits a `VOIDED` payment transition on a **separate** connection / `REQUIRES_NEW` (or via a `TransactionTemplate` on a distinct thread) so `confirm()`'s pre-capture read (~line 35) saw non-`VOIDED` but its post-capture re-read (~line 88) sees `VOIDED`. The post-capture auto-refund must then be made to fail at the PG (the existing strand trigger) so the escalation `record(...)` runs.
2. **Assert the durability invariant precisely:** after `confirm()` rolls back its parent tx, exactly **one** `PaymentRefundStranded` outbox row (and the matching `stranded_refund` row, per BE-438) **survives** (committed via `REQUIRES_NEW`). Keep the assertion at the equivalent of line 165/167.
3. **No production change.** If the rework reveals an actual `confirm()` ordering defect (it should not), STOP and file a separate product task — do not weaken the pre-capture guard.
4. **Lift the quarantine**: remove `@Disabled("TASK-BE-442: …")`; the test must pass on the ecommerce integration lane (TASK-MONO-307) **and be deterministic** (no sleep-based races — gate the interleave on the mock invocation, not wall-clock).

## Acceptance Criteria

- **AC-1** — The reworked IT exercises the **post-capture** stranded path via a VOIDED-during-capture interleave (pre-capture read non-VOIDED, post-capture re-read VOIDED), and asserts the `PaymentRefundStranded` escalation row (+ `stranded_refund` row) survives the `confirm()` rollback. GREEN on the integration lane.
- **AC-2** — Deterministic: the interleave is driven by the `confirmPayment` mock `Answer` (or equivalent synchronisation), not by timing/sleeps. No flakiness across repeated lane runs.
- **AC-3** — The `@Disabled("TASK-BE-442: …")` is removed; the two BE-440 consumer-tx ITs remain green; Docker-free `:payment-service:check` stays green (unit baseline unchanged).
- **AC-4** — No production-code change to `PaymentConfirmService` / `PaymentRefundStrandedRecorder` (test-only rework). If a product change appears necessary, it is split into its own task.

## Related Specs

- `projects/ecommerce-microservices-platform/specs/services/payment-service/architecture.md` — stranded-refund sweeper · PG-state-first · REQUIRES_NEW co-commit (BE-437/438).
- `docs/adr/ADR-MONO-005` § 2.3 D3 (Category-A saga) / § 2.6 D6 (payment stranded-refund row).

## Related Contracts

- None (test-only rework; `PaymentRefundStranded` / `PaymentRefundUnresolved` contracts unchanged).

## Dependencies / Prior Work

- **TASK-BE-440** — committed the `VOIDED` transition that made the old sequential arrange unreachable; holds the quarantine. This task lifts it.
- **TASK-BE-437 / TASK-BE-438** — the stranded-escalation REQUIRES_NEW durability invariant (AC-2) + the sweeper this IT is meant to protect.

## Edge Cases

- **Connection/tx isolation** — the injected `VOIDED` commit must use a genuinely separate physical connection (REQUIRES_NEW / separate thread) so it commits independently of `confirm()`'s parent tx; otherwise it joins the parent and rolls back with it, defeating the interleave.
- **Idempotency under redelivery** — the post-capture stranded `record(...)` is deduped per `payment_id` (BE-438 partial unique index); the rework must not write two open obligations.
- **Mock `Answer` ordering** — commit the VOIDED transition *before* the `Answer` returns the capture result, so the post-capture re-read deterministically observes it.

## Failure Scenarios

- **F1 — re-introducing a flaky timing race** — a sleep-based interleave would flake on CI. Mitigation: AC-2 mandates mock-invocation-gated synchronisation.
- **F2 — proving nothing** — if the arrange still lets the pre-capture guard fire, the test passes vacuously / fails as now. Mitigation: AC-1 requires the pre-capture read to observe non-VOIDED and the post-capture re-read to observe VOIDED — assert both transitions occurred.
