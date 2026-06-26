# TASK-BE-438 — Auto-reconciliation sweeper that retries stranded refunds (Category-A saga)

**Status:** review

**Type:** TASK-BE
**Analysis model:** Opus 4.8 / **Recommended impl model:** Opus (Category-A choreographed-saga sweeper + PG-state reconciliation to prevent double-refund + bounded-retry/terminal/escalation + REQUIRES_NEW boundaries; money-integrity, not a routine fix)

**Service:** payment-service

> **Origin.** Direct follow-up explicitly scoped Out-of-scope by **TASK-BE-437** (merged #1959, § Scope "Out of scope" L42 + Failure Scenario F3) and its DONE note ("Follow-up (out of scope): auto-reconciliation sweeper retrying the PG cancel for stranded payments (Category-A saga)"). BE-437 delivered the **non-silent, durable, operator-recoverable** net (a `PaymentRefundStranded` escalation event). It deliberately did **not** auto-resolve transient failures — a 5xx/circuit-open PG cancel that strands funds today still requires manual operator action. This task adds the **automatic reconciliation** so transient strandings self-heal without paging an operator, while preserving a bounded-retry terminal + escalation for the genuinely-stuck ones (ADR-MONO-005 § 2.3 D3 Category A).

---

## Goal

When [`PaymentConfirmService.confirm()`](../../apps/payment-service/src/main/java/com/example/payment/application/service/PaymentConfirmService.java) (BE-435 post-capture guard) captures funds for an order that was cancelled mid-capture and then **fails to reverse them at the PG**, BE-437 durably records the stranding (escalation event in a `REQUIRES_NEW` boundary) and rejects the confirm. Today that is the end of the automatic story: the captured funds sit at the PG until a human reads the alert and cancels them by hand.

This task makes the stranded refund **auto-reconciling**: a scheduled sweeper periodically (a) **checks the actual PG state first** (the transient-5xx case may already have cancelled — re-issuing would risk a double refund or a confusing PG error), and only if still captured (b) **retries `cancelPayment`**. Successful reversals mark the record `RESOLVED` with no operator involvement. A record that exhausts a bounded retry budget transitions to a **terminal `UNRESOLVED`** state and re-escalates for mandatory operator action — it never retries forever (ADR-MONO-005 § 2.3 D3: at the attempt cap a Category-A saga MUST reach a terminal escalation state, mirroring order-service's `OrderStuckDetector` → `OrderStuckRecoveryHandler.markExhausted`).

## Scope

**In scope (payment-service only):**

1. **Persist a queryable stranded-refund record** (a new `stranded_refund` table + entity/repository). BE-437 currently only emits the `PaymentRefundStranded` **event** to the outbox — there is no table the sweeper can poll. Extend the BE-437 `PaymentRefundStrandedRecorder` (still `@Transactional(REQUIRES_NEW)`, same bean/boundary) to also persist a `StrandedRefund` row in the **same REQUIRES_NEW transaction** as the escalation outbox write, so the durable record and the alert commit atomically across the `confirm()` rollback. Columns: `id`, `payment_id`, `order_id`, `payment_key`, `amount`, `reason` (the failing PG exception type), `status` (`STRANDED` | `RESOLVED` | `UNRESOLVED`), `attempts` (int), `next_attempt_at`, `last_error`, `created_at`, `updated_at`, `resolved_at`. Dedupe on `payment_id` (a client retry of the same confirm must not create a second open record — upsert / unique constraint on `payment_id` while a row is open).
2. **`StrandedRefundSweeper`** — a `@Scheduled` bean that polls `status = STRANDED AND next_attempt_at <= now` rows in a bounded batch and processes each through a **separate `REQUIRES_NEW` handler bean** (`StrandedRefundReconciler`, AOP boundary like `OrderStuckRecoveryHandler` — the `@Scheduled` method and the per-record transactional work must be on different beans so the proxy boundary is honoured and one poisoned record cannot roll back the batch).
3. **PG-state-first reconciliation (double-refund guard, BE-437 F3).** Before re-issuing a cancel, query the PG for the payment's current state via a **new gateway port method** (e.g. `PaymentGatewayPort.fetchStatus(paymentKey)` → maps Toss `GET /payments/{paymentKey}` `status`/`cancels`). Outcomes:
   - **Already cancelled at PG** (the transient case where the original cancel actually succeeded) → mark `RESOLVED`, emit resolution metric, **do not** call `cancelPayment` again.
   - **Still captured / `DONE`** → call `cancelPayment(paymentKey, …)`. On success → `RESOLVED`. On `PgGatewayUnavailableException` (transient) → `attempts++`, set `next_attempt_at` via exponential backoff (reuse the AbstractOutboxPublisher backoff shape: 1s→2s→4s…cap), stay `STRANDED`. On `PgConfirmFailedException` (definitive 4xx — the PG refuses the cancel) → this is not auto-resolvable; go straight to terminal `UNRESOLVED` + escalate.
   - **PG state fetch itself fails** → treat as transient (`attempts++`, backoff, stay `STRANDED`); never assume resolved.
4. **Bounded retry + terminal escalation (ADR-MONO-005 D3).** At `attempts >= cap` (configurable, e.g. `payment.stranded-refund.max-attempts:8`) transition to terminal `UNRESOLVED`, stop auto-retrying, and re-emit an escalation (reuse/extend the `PaymentRefundStranded` alert with a `terminal: true` / phase marker, or a distinct `PaymentRefundUnresolved` event — pick one, document in the contract) so operators are paged for the records the sweeper could not self-heal.
5. **Metrics**: `payment_refund_stranded_resolved_total` (auto-healed), `payment_refund_stranded_unresolved_total` (terminal), and a gauge `payment_refund_stranded_open` (current `STRANDED` count) for an alerting SLO. Increment the existing `payment_refund_stranded_total` (BE-437) unchanged at stranding time.
6. **Standalone profile**: the sweeper, like the outbox relay, is `@Profile("!standalone")` (no scheduler / no PG in standalone). The recorder's table write stays active in all profiles (it is part of the synchronous confirm path).

**Out of scope (note as follow-ups):**
- A **notification/operator-alert consumer** of the escalation event (still out of scope, same disposition as BE-437 / `OrderSagaRecoveryExhausted` — publish, don't consume).
- A **manual operator "force-resolve / write-off" admin action** on an `UNRESOLVED` record (a console/back-office concern; the terminal state + escalation is the boundary of this task).
- Reconciling **consumer-path** refunds (`PaymentRefundService` already has Kafka retry+DLQ) — unchanged.
- Changing the BE-435 **success-path** post-capture behaviour (metric+log only) — unchanged.

## Acceptance Criteria

- **AC-1 (durable record)** — when BE-437 escalates a stranding, a `StrandedRefund` row (`status=STRANDED`, `attempts=0`, `next_attempt_at` ≈ now) is persisted in the **same `REQUIRES_NEW`** transaction as the escalation outbox event, so both survive the `confirm()` rollback. A retried confirm for the same `payment_id` does not create a duplicate open row (dedupe/upsert proven).
- **AC-2 (auto-resolve, PG already cancelled)** — for a `STRANDED` record whose PG state is already `CANCELED`, the sweeper marks it `RESOLVED`, increments `payment_refund_stranded_resolved_total`, and **never calls `cancelPayment`** (no double refund — BE-437 F3). Proven by a unit/slice test stubbing the gateway `fetchStatus` → cancelled.
- **AC-3 (auto-resolve, retry succeeds)** — for a `STRANDED` record still captured at the PG, the sweeper calls `cancelPayment`; on success the record → `RESOLVED` + resolved metric.
- **AC-4 (bounded retry + backoff)** — repeated transient failures increment `attempts` and push `next_attempt_at` by exponential backoff; the sweeper skips a record whose `next_attempt_at` is in the future (no retry storm). Proven deterministically with an injected `Clock`.
- **AC-5 (terminal escalation, D3)** — at `attempts >= max-attempts` (or on a definitive `PgConfirmFailedException`) the record transitions to terminal `UNRESOLVED`, the sweeper stops selecting it, `payment_refund_stranded_unresolved_total` is incremented, and a terminal escalation event is emitted. An `UNRESOLVED` record is never auto-retried again.
- **AC-6 (REQUIRES_NEW / AOP boundary)** — the per-record reconciliation runs in a `REQUIRES_NEW` boundary on a **separate bean** from the `@Scheduled` sweeper, so a single record's failure rolls back only that record and the batch continues (one poisoned row does not block the rest). Verified structurally (separate beans) + by a test where record A throws and record B still processes.
- **AC-7 (contract + spec)** — `specs/contracts/events/payment-events.md` documents the terminal escalation event (or the `terminal` marker on `PaymentRefundStranded`) + any new gateway-status contract note; `specs/services/payment-service/architecture.md` documents the sweeper, the `stranded_refund` lifecycle state machine (`STRANDED → RESOLVED` | `STRANDED → UNRESOLVED`), the new metrics, and the PG-state-first reconciliation rule. ADR-MONO-005 § 2.6 D6 (or a short note) records that payment stranded-refund recovery is a Category-A saga with this terminal.
- **AC-8 (build/tests)** — `:payment-service:test` (Docker-free unit baseline) GREEN. Unit tests cover AC-2..AC-6. A reconciliation IT (`@Tag("integration")`, full PG-stub + DB) MAY be authored but inherits the ecommerce IT-CI gap (**TASK-MONO-307**) → compile-only until that lane exists; CI **unit** coverage is the gate. New Flyway migration (`stranded_refund` table) must apply cleanly (validated on the CI/IT path that runs migrations).

## Related Specs

- `projects/ecommerce-microservices-platform/specs/services/payment-service/architecture.md` — payment state machine, event list, BE-435 confirm guards, BE-437 escalation path; add the sweeper + `stranded_refund` state machine + metrics.
- `docs/adr/ADR-MONO-005-saga-timeout-escalation-dead-letter-policy.md` § 2.3 D3 (Category-A bounded-retry → terminal escalation, the pattern this mirrors) + § 2.6 D6 (per-saga decision record).

## Related Contracts

- `projects/ecommerce-microservices-platform/specs/contracts/events/payment-events.md` — the terminal escalation event (or `terminal` marker); `PaymentRefundStranded` (BE-437) otherwise unchanged.
- PG adapter port: a new read-only `fetchStatus(paymentKey)` capability on `PaymentGatewayPort` (Toss `GET /payments/{paymentKey}`); document the status mapping used for the double-refund guard.

## Dependencies / Prior Work

- **TASK-BE-437** (merged #1959) — emits the `PaymentRefundStranded` escalation + `PaymentRefundStrandedRecorder` (REQUIRES_NEW) that this task extends to also persist the queryable record. **Hard prerequisite.**
- **TASK-BE-435** (merged #1954) — the post-capture guard whose failure produces the stranding.
- **TASK-BE-139** — `TossPaymentsAdapter` Resilience4j wrap + `PgGatewayUnavailableException` / `PgConfirmFailedException` taxonomy the sweeper branches on; the `fetchStatus` call must be wrapped the same way.
- **TASK-BE-136** — payment-service transactional outbox reused for the terminal escalation event.
- **ADR-MONO-005** § 2.3 D3 — the Category-A bounded-retry-then-terminal contract; order-service `OrderStuckDetector` + `OrderStuckRecoveryHandler` is the reference implementation to mirror (separate detector/handler beans, `@Scheduled` + REQUIRES_NEW split, terminal at attempt cap).
- **TASK-MONO-307** (ready/) — any new `@Tag("integration")` reconciliation IT is compile-only until the ecommerce IT-CI lane exists.

## Edge Cases

- **Double-refund (BE-437 F3, the central hazard)** — a `PgGatewayUnavailableException` means the original cancel's PG outcome is **unknown**; it may have actually succeeded. The sweeper MUST `fetchStatus` first and treat `CANCELED` as resolved without re-issuing. Re-issuing blindly risks a double refund (customer over-credited) or a PG error that masks the real state. This is the reason the sweeper is read-before-write.
- **Concurrent sweeper ticks / double processing** — two overlapping ticks (slow PG) must not both reconcile the same record. Claim rows with a pessimistic lock / `next_attempt_at` advance on selection (mirror the outbox `findPendingWithLock` pattern), or rely on `@Scheduled` single-thread + short batch. Document the chosen exclusivity.
- **Record dedupe at stranding time** — a client retrying the same failed confirm re-enters BE-437's catch path; the recorder must upsert (not insert a 2nd open row) on `payment_id` so the sweeper sees one obligation, not N.
- **PG `fetchStatus` ambiguous/partial** — if the status response is unparseable or the call fails, treat as transient (backoff, stay STRANDED). Never infer RESOLVED from an error.
- **Already-RESOLVED/UNRESOLVED row re-selected** — the WHERE clause must exclude terminal states so a resolved record is never re-touched; terminal is terminal (idempotent).
- **Clock/backoff determinism** — inject `Clock` (the codebase convention, cf. `OrderStuckDetector`, `AbstractOutboxPublisher`) so backoff and the attempt cap are unit-testable without sleeps.

## Failure Scenarios

- **F1 — sweeper double-refunds** — skipping the `fetchStatus` guard and re-cancelling an already-cancelled payment. Mitigation: AC-2 read-before-write is mandatory; the cancelled-at-PG branch must `RESOLVED` without a second cancel.
- **F2 — infinite retry / retry storm** — a permanently-failing PG cancel retried forever would hammer the PG and never page anyone. Mitigation: bounded `max-attempts` → terminal `UNRESOLVED` + escalation (AC-5), exponential backoff between attempts (AC-4).
- **F3 — silent terminal** — a record reaching `UNRESOLVED` without re-emitting an escalation would bury the unrecoverable money loss. Mitigation: the terminal transition MUST emit the escalation event + increment the unresolved metric (the operator still has to act on the records the machine could not heal).
- **F4 — recorder/table write fails at stranding time** — if persisting the `StrandedRefund` row throws, it must not mask the captured-funds loss: keep BE-437's F1 inner try/catch (log.error + metric) so a failed record-write still leaves a logged + metered trace, even if the sweeper then has nothing to poll.
- **F5 — batch blocked by one poisoned record** — without the separate-bean REQUIRES_NEW boundary, one record's exception rolls back the whole tick. Mitigation: AC-6 per-record boundary.
