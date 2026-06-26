# TASK-BE-440 — OrderCancelled→refund/void consumer path persists without an active transaction

**Status:** done

**Type:** TASK-BE
**Analysis model:** Opus 4.8 / **Recommended impl model:** Opus (money-safety consumer tx-boundary + re-verify the BE-437 REQUIRES_NEW durability proof under the fix)

**Service:** payment-service

> **Origin.** Surfaced by **TASK-MONO-307** (ecommerce integration CI lane) — the first-ever CI execution of the payment `@Tag("integration")` suite. The two OrderCancelled-driven refund ITs failed with `InvalidDataAccessApiUsageException: No EntityManager with actual transaction available - cannot reliably process 'persist'`, and the BE-437 stranded-durability IT found 0 escalation rows (likely a downstream symptom of the same setup failure). All three are **quarantined** with `@Disabled("TASK-BE-440: …")` (`PaymentRefundIntegrationTest.orderCancelled_refundsPayment`, `…completedThenCancelled_paymentTimeout_refunds`; `PaymentRefundStrandedDurabilityIntegrationTest` class). This task fixes the bug and lifts the quarantine.

---

## Goal

When payment-service consumes `OrderCancelled` and runs the money-safe state-branching handler (`PaymentRefundService.handleOrderCancelled(orderId)` — TASK-BE-435: `COMPLETED`/`PARTIALLY_REFUNDED`→refund, `PENDING`→void), the persistence write (`save` / `persist`) executes **without an active transaction**, throwing `InvalidDataAccessApiUsageException: No EntityManager with actual transaction available - cannot reliably process 'persist'`. The refund/void is therefore not durably applied on the consumer path.

This is a **money-safety-critical latent defect** in a merged flow (TASK-BE-435): a captured payment for a cancelled order would not be refunded (or a never-captured payment not voided) via the event path. It was not caught because the unit tests drive the service with a mocked repository (no real tx manager), and the ITs that boot the real persistence context never ran until TASK-MONO-307.

## Scope

**In scope (payment-service only):**

1. **Diagnose the tx boundary.** Determine why no transaction is active when the OrderCancelled handler persists:
   - Is `PaymentRefundService.handleOrderCancelled` (and the refund/void methods it calls) annotated `@Transactional`? Is the annotation on a `public` method invoked through the Spring proxy (not a self-invocation that bypasses AOP)?
   - Does the `@KafkaListener` consumer (`OrderCancelledEventConsumer`) invoke the service through the proxy so the `@Transactional` advice applies on the consumer thread?
   - Confirm the behaviour is identical under a **real** `@KafkaListener` delivery and the IT's direct `onMessage(...)` drive (the IT must reproduce the production path, not a test-only artifact — if it is a test-only artifact, fix the IT instead and document it).
2. **Fix** so the refund/void write commits in a proper transaction on the consumer path (add/correct `@Transactional` on the right bean/method, mirroring the confirm path's boundary), without weakening the existing idempotency / state-branch guards.
3. **Re-verify the BE-437 durability proof.** With the consumer tx fixed, the `PaymentRefundStrandedDurabilityIntegrationTest` setup (replays OrderPlaced + OrderCancelled, then confirm() strands) should reach the post-capture stranded path again. Confirm the `PaymentRefundStranded` escalation row survives the `confirm()` rollback (the REQUIRES_NEW durability invariant). If the durability failure has a **separate** root cause from the consumer-tx bug, split it into its own task and re-quarantine with the new reference.
4. **Lift the TASK-MONO-307 quarantine** on the three ITs; they must pass on the ecommerce integration lane.

**Out of scope:** the order-side sweeper lazy-init bug (**TASK-BE-439**); the other 11 ecommerce services' IT phases; changing BE-435 success-path semantics beyond the tx fix.

## Acceptance Criteria

- **AC-1** — The OrderCancelled consumer path refunds a `COMPLETED` payment / voids a `PENDING` payment **within an active transaction** — no `InvalidDataAccessApiUsageException`. Proven by `PaymentRefundIntegrationTest.orderCancelled_refundsPayment` + `completedThenCancelled_paymentTimeout_refunds` passing on the integration lane.
- **AC-2** — `PaymentRefundStrandedDurabilityIntegrationTest` passes: the `PaymentRefundStranded` escalation row survives the `confirm()` rollback (BE-437 AC-2 / REQUIRES_NEW), OR — if a distinct root cause — a follow-up task is filed and the IT re-quarantined against it.
- **AC-3** — All three ITs are un-`@Disabled`; Docker-free `:payment-service:check` stays green (unit baseline unchanged).
- **AC-4** — The already-passing refund ITs (`AC-3 CANCELLED→COMPLETED`, `AC-7 멱등`, no-payment-no-op) remain green — the tx fix must not regress the idempotency / state-branch behaviour.

## Related Specs

- `projects/ecommerce-microservices-platform/specs/services/payment-service/architecture.md` — § "OrderCancelled consumer — money-safe late-payment compensation (TASK-BE-435)".
- `docs/adr/ADR-MONO-005` § 2.6 D6 (ecommerce refund saga / payment stranded-refund).

## Related Contracts

- None (internal tx fix; `PaymentRefunded` / `PaymentRefundStranded` contracts unchanged).

## Dependencies / Prior Work

- **TASK-MONO-307** — the integration lane that surfaced this; holds the quarantine. This task lifts it.
- **TASK-BE-435** — authored the OrderCancelled money-safe consumer branch this task fixes the tx boundary for.
- **TASK-BE-437 / TASK-BE-438** — the stranded-refund escalation + sweeper whose durability IT is affected.

## Edge Cases

- **Direct `onMessage` vs `@KafkaListener`** — the IT drives the consumer directly; confirm the tx behaviour matches a real Kafka delivery (the `@Transactional` proxy must apply in both). If the IT bypasses the proxy, the fix is in the test wiring, not production — document which.
- **REQUIRES_NEW interaction** — the stranded recorder runs in `REQUIRES_NEW`; ensure the consumer-path outer transaction fix does not change the recorder's independent-commit semantics.
- **At-least-once redelivery** — the consumer is idempotent (gates on already-REFUNDED / terminal state); the tx fix must preserve idempotency under duplicate OrderCancelled.

## Failure Scenarios

- **F1 — silent money loss** — without a committing transaction, a captured payment for a cancelled order is never refunded on the event path. Mitigation: AC-1 proves a durable refund.
- **F2 — masking the durability regression** — if the durability IT is re-enabled without confirming the escalation row survives, the BE-437 money-safety net is unproven. Mitigation: AC-2.
- **F3 — fixing the test, not the bug** — if the "no transaction" is a real production defect but is "fixed" only by wrapping the test in a TransactionTemplate, production stays broken. Mitigation: scope item 1 requires confirming the production path.
