# TASK-BE-443 — confirm() post-capture re-read returns a stale persistence-context entity, so a concurrent VOIDED is never detected (money-safety)

**Status:** done

**Type:** TASK-BE
**Analysis model:** Opus 4.8 / **Recommended impl model:** Opus (money-safety production fix to the confirm() post-capture guard + the durability IT that proves it)

**Service:** payment-service

> **Origin.** Surfaced by **TASK-BE-442** (the stranded-durability IT rework). Mapping confirm()'s control flow to make the durability IT reachable revealed that the IT could not be written test-only — because the production guard it was meant to prove is itself **unreachable for the concurrent race it was designed for**. Supersedes TASK-BE-442 (the IT rework folds into this task once the production re-read is fixed).

---

## Goal

`PaymentConfirmService.confirm(...)` (`PaymentConfirmService.java`) is a single `@Transactional` method. Its **post-capture** money-safety guard (TASK-BE-435/437, lines ~74–125) re-reads the payment after the PG capture and, if it finds the row `VOIDED` (an `OrderCancelled` that committed *during* the slow capture), auto-refunds the just-captured funds and — if that refund fails at the PG — records a `PaymentRefundStranded` escalation in a `REQUIRES_NEW` boundary (TASK-BE-437) so it survives the confirm() rollback.

The re-read (`paymentRepository.findByOrderId(orderId)`, ~line 88) runs in the **same Hibernate Session** that loaded the payment at the pre-capture read (~line 35). The payment entity is therefore already **managed** in the first-level cache, so the re-read query — although its SQL does hit the DB — returns the **stale managed `PENDING` instance** and discards the freshly-fetched `VOIDED` columns (session-level entity identity, *above* the DB isolation level). There is no `EntityManager.clear()`/`refresh()`/`detach`, no `@Version` on `PaymentJpaEntity`, and no separate read boundary between the two reads.

**Consequence (money-safety defect).** In the genuine void-during-capture race, `latest.isVoided()` is **false**, the auto-refund/stranded branch is skipped, and confirm() proceeds to mark the payment `COMPLETED` and publish `PaymentCompleted` — **last-writer-wins over the committed VOIDED row (no `@Version`)**. The customer is charged for a cancelled order, no refund is issued, and the BE-437 stranded safety-net never fires. The whole BE-435/437 post-capture guard is dead for the ordering it exists to handle.

**Why it was never caught.** The unit `PaymentConfirmServiceTest` stubs a **mock** repository to return two different objects for the two `findByOrderId` calls (`PENDING` then `VOIDED`) — which a real Hibernate Session never does. The guard is "green" in unit tests for a reason that does not hold in production; only a real-Postgres IT (the BE-442/TASK-MONO-307 lane) exposes it.

## Scope (payment-service only)

1. **Fix the post-capture re-read** so it observes a concurrently-committed `VOIDED`. Hexagonal-clean: add a port method `PaymentRepository.findByOrderIdFresh(orderId)` (or equivalently-named) used at the post-capture site, whose `PaymentRepositoryImpl` implementation forces a fresh DB view that bypasses the stale first-level-cache identity — e.g. `entityManager.refresh(entity)` on the managed entity returned by the query (or a `REQUIRES_NEW` separate-session read). The **pre-capture** read stays as-is.
2. **Preserve the success path byte-for-byte.** In the no-race case the fresh re-read returns `PENDING` (unchanged) → `latest.isVoided()` is false → confirm() proceeds to `payment.confirm()` + `save(payment)` + `PaymentCompleted` exactly as before. The fix must be a no-op on the happy path.
3. **Correct the stale rationale.** The production comment (~lines 82–87) attributes the re-read's correctness to READ_COMMITTED isolation; rewrite it to state the real mechanism (the fresh/evicting re-read defeats session entity-identity).
4. **Rework + lift the durability IT** (folds in TASK-BE-442). With the fresh re-read in place, `PaymentRefundStrandedDurabilityIntegrationTest` injects a deterministic VOIDED-during-capture interleave: a Mockito `Answer` on the gateway `confirmPayment` commits a `VOIDED` transition on a **separate connection** (`TransactionTemplate` `REQUIRES_NEW`) before returning the capture, the `cancelPayment` auto-refund is mocked to fail, and the test asserts exactly one `PaymentRefundStranded` outbox row (+ the `stranded_refund` row, BE-438) **survives** the confirm() rollback. Deterministic (mock-invocation-gated, no sleep). Remove the `@Disabled`.
5. **Unit baseline.** Keep `:payment-service:check` green; update `PaymentConfirmServiceTest` stubs to the (possibly renamed) re-read method.

**Out of scope:** the four invariants already shipped (BE-435/437/438); the order-side; any non-payment service.

## Acceptance Criteria

- **AC-1** — In the void-during-capture race, confirm()'s post-capture re-read observes the committed `VOIDED`, takes the auto-refund branch, and (on PG refund failure) writes the `PaymentRefundStranded` + `stranded_refund` rows in `REQUIRES_NEW`; the payment is **never** advanced to `COMPLETED` and **no** `PaymentCompleted` is published. Proven by the reworked durability IT on the ecommerce integration lane.
- **AC-2** — The durability IT is deterministic (mock-invocation-gated, no `Thread.sleep`) and un-`@Disabled`; the `PaymentRefundStranded` escalation row survives the confirm() rollback (BE-437 AC-2 / REQUIRES_NEW).
- **AC-3** — The success path (no concurrent void) is behaviourally unchanged: a clean confirm still captures, marks `COMPLETED`, and publishes `PaymentCompleted`. Existing confirm ITs/units stay green; `:payment-service:check` green.
- **AC-4** — The fix is contained to payment-service; the pre-capture guard, the four BE-435/437/438 invariants, and idempotency/dedup are preserved.

## Related Specs

- `projects/ecommerce-microservices-platform/specs/services/payment-service/architecture.md` — § post-capture auto-refund / stranded re-read (update the mechanism).
- `docs/adr/ADR-MONO-005` § 2.3 D3 / § 2.6 D6.

## Related Contracts

- None (internal persistence/read-semantics fix; `PaymentRefundStranded` / `PaymentCompleted` contracts unchanged).

## Dependencies / Prior Work

- **TASK-BE-442** — superseded by this task (its IT rework was infeasible test-only because of this defect; it folds in here).
- **TASK-BE-435 / 437 / 438** — the post-capture guard + stranded escalation + sweeper this fix makes actually reachable.
- **TASK-MONO-307** — the integration lane that made the real-Postgres path observable.

## Edge Cases

- **`entityManager.refresh` on a row deleted concurrently** — refresh of a since-deleted row throws `EntityNotFoundException`; the void path only transitions status (no delete), so not applicable, but the fresh-read impl must handle "row vanished" gracefully (fall back to the pre-capture instance, as the current `.orElse(payment)` does).
- **Refresh vs the success path** — on the happy path the refresh reloads identical `PENDING` state; ensure no spurious dirtying / extra flush.
- **REQUIRES_NEW visibility** — the IT's injected VOIDED commit must use a genuinely separate connection so it is committed (and thus visible to the fresh re-read) before `confirmPayment` returns.

## Failure Scenarios

- **F1 — silent charge on a cancelled order (the production impact)** — the stale re-read lets confirm() complete a payment for a cancelled order with no refund and no escalation. Mitigation: AC-1 proves detection + escalation against a real DB.
- **F2 — fixing the test, not the read** — proving the invariant only via a mock-repo two-stub (as the unit test does) does not exercise the real session semantics. Mitigation: AC-1/AC-2 require the Testcontainers IT with a REQUIRES_NEW concurrent commit.
- **F3 — breaking the happy path** — an over-broad `entityManager.clear()` could detach in-flight state and corrupt the success-path save. Mitigation: AC-3 + a targeted refresh (not a context-wide clear).
