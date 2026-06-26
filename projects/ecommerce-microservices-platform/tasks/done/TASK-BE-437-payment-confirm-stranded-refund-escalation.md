# TASK-BE-437 — Money-safety: escalate a stranded refund when the HTTP confirm() post-capture auto-refund fails at the PG

**Status:** done

> **DONE (2026-06-26, 3-dim verified — impl PR #1959 squash `b4ce981cab...` `b4ce981c`).** Closed the BE-435 money-safety hole: `PaymentConfirmService.confirm()` post-capture `cancelPayment` was uncaught → PG failure stranded captured funds silently. Fix: catch both PG exceptions → `PaymentRefundStrandedRecorder` (separate `@Component`, `@Transactional(REQUIRES_NEW)`) writes a durable `PaymentRefundStranded` outbox escalation (topic `payment.alert.refund.stranded`) that **commits across the confirm() rollback** + `payment_refund_stranded_total` metric + ERROR log (F1 inner try/catch) → reject confirm. Success path unchanged. New event/port/outbox-writer/relay-topic/standalone-publisher wiring + specs (payment-events.md, architecture.md). **3-dim:** (a) MERGED + `b4ce981c`; (b) origin/main tip = `b4ce981c3`; (c) pre-merge 20 checks pass / 1 skipping / **0 fail** (Build & Test = ecommerce `:check` incl payment unit; PaymentConfirmServiceTest +3 GREEN). **⚠️ caveat (same as BE-435):** the AC-2 durability IT is `@Tag("integration")` → runs nowhere in automation until the ecommerce IT CI lane exists (TASK-MONO-307); the REQUIRES_NEW separation is structurally + unit verified, the cross-rollback commit proof is IT-only. **Follow-up (out of scope):** auto-reconciliation sweeper retrying the PG cancel for stranded payments (Category-A saga). 분석=Opus 4.8 / 구현=Opus backend-engineer + Opus 검수(confirm catch·REQUIRES_NEW recorder·relay topic 직접 diff).

**Type:** TASK-BE
**Analysis model:** Opus 4.8 / **Recommended impl model:** Opus (payment-integrity money-safety + transactional-boundary/REQUIRES_NEW reasoning; not a routine fix)

**Service:** payment-service

> **Origin.** Direct follow-up to TASK-BE-435 (merged #1954), which its own Edge Cases (§ "Auto-refund itself fails at the PG") and Failure Scenario F1 flagged and its DONE note recorded as an Open Risk. The synchronous HTTP `confirm()` post-capture auto-refund has **no failure recovery** — a PG cancel failure there silently strands captured customer funds.

---

## Goal

In [`PaymentConfirmService.confirm()`](../../apps/payment-service/src/main/java/com/example/payment/application/service/PaymentConfirmService.java) the BE-435 **post-capture auto-refund guard** (lines ~87–96) handles the genuinely-concurrent interleave where an `OrderCancelled` commits a `VOIDED` transition *during* the slow PG capture: after capturing, it re-reads the row, finds `VOIDED`, and calls `paymentGateway.cancelPayment(paymentKey, …)` to reverse the just-captured amount.

**That `cancelPayment` call is uncaught.** If it throws — `PgGatewayUnavailableException` (5xx / circuit-open / timeout) or `PgConfirmFailedException` (4xx) — the exception propagates out of `confirm()`, the `@Transactional` rolls back, and there is **no DLT, no retry, no operator alert, no durable record** of the captured-but-not-refunded funds. The `Payment` row is already `VOIDED` (committed by the consumer's separate TX), the `OrderCancelled` consumer has already run its PENDING→void branch so it will **not** re-fire, and the money sits captured at the PG with nothing tracking the refund obligation. **Silent customer-money loss.**

(Contrast: the **consumer** refund path — `OrderCancelledEventConsumer` → `PaymentRefundService.refundPayment` — IS protected by the Kafka `DefaultErrorHandler` retry-3×-then-`.dlq`. Only this **synchronous HTTP** path lacks a net.)

This task makes that failure **non-silent, durable, and operator-recoverable** — satisfying BE-435 Edge Case L109 ("must not be silently dropped; it must retry / route to DLQ / surface an operator alert").

## Scope

**In scope (payment-service only):**

1. **Catch the post-capture `cancelPayment` failure** in `PaymentConfirmService.confirm()`. On success, behaviour is unchanged (metric + reject the confirm). On failure (either PG exception):
   - **Durably record a stranded-refund escalation** so it survives the `confirm()` rollback (see #2),
   - increment a **money-safety metric** (new `incrementRefundStranded()` on `PaymentMetricRecorder`),
   - `log.error(...)` with orderId/paymentId/paymentKey/amount/cause,
   - then still **reject the confirm** (throw `PaymentAlreadyCompletedException`, as today — the order is cancelled, the payment must not advance to `COMPLETED`).
2. **Durable escalation via the transactional outbox, in a `REQUIRES_NEW` transaction** (so it commits independently of the rolled-back `confirm()` TX — mirrors order-service's `OrderStuckRecoveryHandler` REQUIRES_NEW split, which uses a **separate bean** to defeat Spring AOP self-invocation):
   - New escalation event `PaymentRefundStranded` (payload: `paymentId`, `orderId`, `paymentKey`, `amount`, `reason`/`failureCause`, `occurredAt`) on a new topic `payment.alert.refund.stranded` (mirror the `order.alert.saga.recovery.exhausted` shape/intent).
   - New `PaymentEventPublisher.publishPaymentRefundStranded(...)` + `PaymentEventOutboxWriter` impl (`outboxWriter.save(AGGREGATE_TYPE, paymentId, "PaymentRefundStranded", json)`); register the new event-type→topic mapping wherever the relay/topic config lives.
   - A new `@Component` (e.g. `PaymentRefundStrandedRecorder`) with a `@Transactional(propagation = REQUIRES_NEW)` method that performs the outbox write, called from `confirm()`'s catch block. **Separate bean** (not a private method on `PaymentConfirmService`) so the REQUIRES_NEW boundary is honoured.
3. **Consumer**: `notification-service` / operator alert subscriber is **out of scope for v1** — publish the topic so a future subscriber consumes it without spec drift (same disposition as `OrderSagaRecoveryExhausted`).

**Out of scope (note as follow-ups):**
- A **full auto-reconciliation sweeper** that periodically retries the PG cancel for stranded payments (would auto-resolve transient 5xx without operator action — a Category-A saga per ADR-MONO-005; bigger build). This task delivers the non-silent operator-recoverable net; the auto-retry is a documented escalation follow-up.
- The **success-path** post-capture refund currently publishes no `PaymentRefunded` event (only a metric+log) — whether accounting/settlement needs one for a captured-then-immediately-cancelled amount is a separate question; do not change it here.
- The protected consumer refund path (already has retry+DLQ) — unchanged.

## Acceptance Criteria

- **AC-1** — When the post-capture `cancelPayment` throws (`PgGatewayUnavailableException` or `PgConfirmFailedException`), `confirm()` no longer lets it propagate silently: a `PaymentRefundStranded` escalation event is durably written to the outbox and the money-safety metric is incremented, **then** the confirm is rejected (`PaymentAlreadyCompletedException`).
- **AC-2 (durability across rollback)** — the escalation outbox row **survives** the `confirm()` `@Transactional` rollback (written in a `REQUIRES_NEW` boundary via a separate bean). A unit/slice test proves the outbox write commits even though `confirm()` throws.
- **AC-3 (no false escalation)** — when `cancelPayment` **succeeds**, no `PaymentRefundStranded` is emitted (behaviour unchanged: metric + reject).
- **AC-4 (idempotency / no double-capture)** — the path still never advances a VOIDED payment to `COMPLETED`; a retried confirm after a stranded escalation does not double-capture (re-read still sees VOIDED → same handling). The escalation may be emitted again on retry (at-least-once) — the downstream alert consumer dedupes; document this.
- **AC-5 (contract + spec)** — `specs/contracts/events/payment-events.md` documents the new `PaymentRefundStranded` alert event (topic, payload, "operator/alert subscriber, out-of-scope v1" disposition, mirroring `OrderSagaRecoveryExhausted`); `specs/services/payment-service/architecture.md` documents the confirm() failure-escalation path + the new metric + the new event in the payment event list.
- **AC-6 (build/tests)** — `:payment-service:test` (Docker-free unit baseline) GREEN. Unit tests cover: cancelPayment-fails→escalation+metric+reject; cancelPayment-succeeds→no escalation; the REQUIRES_NEW recorder writes the outbox row. A money-safety IT (both PG-failure exception types) may be authored `@Tag("integration")` — note it inherits the ecommerce IT-CI gap (TASK-MONO-307), so it is compile-only until that lane exists; CI unit coverage is the gate.

## Related Specs

- `projects/ecommerce-microservices-platform/specs/services/payment-service/architecture.md` (payment state machine + event list + the BE-435 confirm guards).
- `docs/adr/ADR-MONO-005` § 2.3 D3 / § 2.4 D4 (escalation-event + Category B fail-semantics philosophy this mirrors).

## Related Contracts

- `projects/ecommerce-microservices-platform/specs/contracts/events/payment-events.md` (add `PaymentRefundStranded`; `PaymentRefunded` unchanged).

## Dependencies / Prior Work

- **TASK-BE-435** (merged #1954) — introduced the post-capture guard whose failure path this hardens; its Edge Case L109 / F1 scoped this.
- **TASK-BE-136** (payment-service transactional outbox) — the outbox mechanism (`OutboxWriter`, `PaymentEventOutboxWriter`/`Relay`) reused for the escalation event.
- **TASK-BE-139** — `TossPaymentsAdapter` Resilience4j wrap defines the `PgGatewayUnavailableException` / `PgConfirmFailedException` taxonomy this catches.
- **TASK-MONO-307** (ready/) — any new `@Tag("integration")` IT here is compile-only until the ecommerce IT-CI lane exists.

## Edge Cases

- **`PgGatewayUnavailableException` (transient 5xx/circuit-open)** — the cancel *might* have actually succeeded at the PG (unknown). The escalation must carry enough context (`paymentKey`, `orderId`, `amount`) for a reconciliation/operator to check PG state and avoid a double-refund. Do not assume the money is definitely stranded — assume it *may* be.
- **`PgConfirmFailedException` (definitive 4xx)** — the cancel was rejected; money is captured and needs operator intervention. Same escalation event, `reason` distinguishes.
- **REQUIRES_NEW self-invocation** — calling a `@Transactional(REQUIRES_NEW)` method on `this` would bypass the proxy and inherit the outer (rolling-back) TX → the alert would be lost. The recorder MUST be a separate bean (AOP boundary), exactly like `OrderStuckRecoveryHandler`.
- **At-least-once escalation** — a client retry of the same confirm can re-emit the escalation; the (future) alert consumer dedupes on `paymentId`/`eventId`. Acceptable; documented.

## Failure Scenarios

- **F1 — the fix itself silently drops** — if the REQUIRES_NEW write throws (e.g. outbox/DB error) it must at minimum `log.error` + metric, never swallow. The captured-funds loss must never be invisible.
- **F2 — false money-safety signal** — emitting `PaymentRefundStranded` on the cancel-**success** path would page operators for nothing and erode trust. Guard strictly: escalate only on the catch path (AC-3).
- **F3 — double refund via reconciliation** — a future operator/sweeper acting on the escalation could double-refund if the original PG cancel had actually succeeded (transient case). The escalation payload must enable a check-PG-state-first reconciliation; flagged here for the consumer/sweeper follow-up.
