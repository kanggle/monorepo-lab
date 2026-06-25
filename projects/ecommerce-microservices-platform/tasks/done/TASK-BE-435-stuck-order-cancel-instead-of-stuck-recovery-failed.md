# TASK-BE-435 — Stuck payment-pending orders auto-CANCEL (with money-safe late-payment compensation) instead of terminal STUCK_RECOVERY_FAILED

**Status:** done

> **DONE (2026-06-26, 3-dim verified — impl PR #1954 squash `ce4d5f6cab688365f369a75437d3b7c3b51d2a0f`).** Prerequisite **TASK-MONO-306** (ADR-MONO-005 § 2.3 D3 auto-resolving-terminal refinement) merged first (#1952 `e0052945`) — amend-before-implement honoured, HARDSTOP-09 gate satisfied. **order-service (§A):** `CancelReason{OPERATOR,PAYMENT_TIMEOUT}`; `Order.cancel(CancelReason,Clock)` (reason transient, event sole carrier — no DB column); `OrderCancelledEvent.cancelReason` additive (legacy/null→OPERATOR); `OrderStuckRecoveryHandler.markExhausted` → `cancel(PAYMENT_TIMEOUT)` + `OrderCancelled(PAYMENT_TIMEOUT)` + retained informational `OrderSagaRecoveryExhausted` (R3), co-commit (R4 rollback→PENDING retry); `STUCK_RECOVERY_FAILED` retained. **payment-service (§B):** `PaymentStatus.VOIDED`; `Payment.voidForOrderCancelled()` idempotent + `confirm()` rejects VOIDED; consumer→`handleOrderCancelled` (COMPLETED→refund / PENDING→void / terminal→no-op, reason-independent, idempotent); `PaymentConfirmService` pre-capture VOIDED reject + post-capture re-read auto-refund — both event orderings money-safe. **product-service (§C):** no code change (NEW release no-op), AC-4 test added. **specs (§D):** order-events.md (cancelReason additive + lifecycle) / order+payment architecture.md / payment-events.md.
>
> **3-dim:** (a) state=MERGED + `ce4d5f6c`; (b) origin/main tip = `ce4d5f6ca`; (c) pre-merge failing required = 0 (20 checks pass / 1 skipping / 0 fail — **Build & Test (JDK 21, Linux)** runs ecommerce `:check` = order 87 / payment 68 / product 10 unit GREEN).
>
> **⚠️ Test-coverage caveat (honest, AC-8 nuance):** the AC-2/AC-3 money-safety ITs (both `OrderCancelled`↔`PaymentCompleted` orderings) + `OrderStuckRecoveryIT` are authored `@Tag("integration")` but run **NOWHERE in automation** — ecommerce `:check` carries `excludeTags 'integration'` AND (unlike wms/iam/scm/fan/finance/erp/console) there is **no ecommerce `:integrationTest` CI lane**, and the local Windows host cannot run Testcontainers (`project_testcontainers_docker_desktop_blocker`). They **compile** in CI but do not execute. The branch/guard **logic** (confirm pre/post-capture guards, void idempotency, consumer COMPLETED→refund/PENDING→void, stuck-handler terminal) **is** covered by **unit tests that DO run in CI** via `:check`. The full Spring/Postgres ordering proof is therefore unverified in automation until either an ecommerce IT CI lane is added or the ITs are run on a Docker-capable host. The two backend-engineer agents' "CI-Linux-authoritative" claim was inaccurate for ecommerce and is corrected here. **Residual follow-up candidate:** add an ecommerce `:integrationTest` CI job (monorepo-level CI change) so these ITs gain a real automation gate. 분석=Opus 4.8 / 구현=2×Opus backend-engineer(order/payment split) + Opus 오케스트레이터 검수(stuck-handler·confirm/refund 레이스 가드·Payment void 직접 diff 리뷰).

**Type:** TASK-BE
**Analysis model:** Opus 4.8 / **Recommended impl model:** Opus (payment-integrity race — concurrent ordering of `OrderCancelled` ↔ `PaymentCompleted` across order-service + payment-service; not a routine fix)

**Services:** order-service, payment-service (product-service: no code change — verify only)

---

## Goal

Today, when a customer clicks **결제하기**, the order is created `PENDING` **before** the Toss payment widget opens (`placeOrder()` → `requestPayment()`, [`apps/web-store/src/features/checkout/ui/CheckoutForm.tsx`](../../apps/web-store/src/features/checkout/ui/CheckoutForm.tsx)). If the customer closes the widget without paying, the order stays `PENDING`. The stuck-detector (TASK-BE-138, ADR-MONO-005 § D3 Category A) eventually sweeps it: after the grace window (default 1800 s) and `max-attempts` (default 5) it transitions the order to the **terminal `STUCK_RECOVERY_FAILED`** status and emits `OrderSagaRecoveryExhausted` for an operator to handle.

`STUCK_RECOVERY_FAILED` is an operator hand-off, not a customer-meaningful outcome — the customer sees a permanently "stuck" order that is neither cancelled nor refundable (`isCancellable()` excludes it). This task changes the stuck-detector terminal to a real **`CANCELLED`** (with a distinguishing `cancelReason = PAYMENT_TIMEOUT`), so the order resolves cleanly and self-serve, while **closing the one money-safety hole** that switching the terminal to `CANCELLED` opens.

**Why this is the chosen approach (decision already made):** evaluated against a *payment-first* redesign (create the order only after payment confirms). Payment-first was rejected — Toss requires an `orderId` at `requestPayment()` time (so it merely renames the orphan into a "payment intent"), and it introduces the strictly worse failure mode *"money captured, no order"* requiring refund compensation, plus a saga dependency-direction reversal across services. Keeping order-first + improving the compensation logic delivers the same "clean terminal" at a fraction of the blast radius. Full comparison lives in the originating design discussion; this task executes the order-first + compensation option.

## Background — verified facts that shape the design

1. **Stuck PENDING orders have NO inventory reserved.** Reservation requires `paymentReceived == true` ([`product-service/.../StockReservation.isReadyToReserve()`](../../apps/product-service/src/main/java/com/example/product/domain/model/reservation/StockReservation.java)); a payment-pending order never reached that leg. ⇒ **Cancelling a stuck order needs no stock restoration** — the existing `OrderCancelledReservationConsumer` already treats a `NEW`/`BACKORDERED` reservation as a no-op `RELEASED`. This part is free.

2. **The order-side state-corruption guard already exists.** [`Order.markPaymentCompleted()`](../../apps/order-service/src/main/java/com/example/order/domain/model/Order.java) throws `InvalidOrderException` when `status == CANCELLED`, and `PaymentConfirmationService` catches it and logs. So a late `PaymentCompleted` **cannot corrupt** a cancelled order's status. ✅ already safe.

3. **The one real hole.** The stuck-detector filters `PENDING AND payment_id IS NULL`. `payment_id` is set by the `PaymentCompleted` consumer. For it to still be null 30 min+ after creation, the payment essentially did not complete *as far as order-service knows* — but payment-service may have **actually captured** the money and the `PaymentCompleted` event is merely delayed (outbox/consumer stuck, PG webhook retry). If we now `CANCEL` the order, and that captured payment is never refunded, the **customer loses money**. The current `STUCK_RECOVERY_FAILED` path masks this because a human operator triages it; an automated `CANCELLED` must instead guarantee a refund. **Closing this hole is the core of the task.**

## Scope

### A. order-service — stuck terminal becomes CANCELLED(PAYMENT_TIMEOUT)

1. In [`OrderStuckRecoveryHandler`](../../apps/order-service/src/main/java/com/example/order/application/saga/OrderStuckRecoveryHandler.java), replace the `markStuckRecoveryFailed(now)` terminal (reached at `nextAttempt >= maxAttempts`) with a cancel that emits `OrderCancelled`. Re-use the existing cancel path (`Order.cancel()` already permits `PENDING` via `isCancellable()` and the application layer publishes `OrderCancelled`) rather than inventing a new transition. The 1–4 attempt increments (`recordStuckRecoveryAttempt`) stay — they remain the late-payment grace window before cancel.
2. Tag the cancellation with a **reason** distinguishing system-timeout cancel from operator cancel. Thread a `cancelReason` (enum: at minimum `OPERATOR`, `PAYMENT_TIMEOUT`) from the cancel call → `OrderCancelledEvent` payload (see § D contract change). Existing operator cancel (`/api/internal/orders/{orderId}/cancel`) passes `OPERATOR`.
3. **Preserve operator visibility.** Keep emitting an informational alert so the auto-cancellation is still observable (today's `OrderSagaRecoveryExhausted` semantics). Either (a) keep publishing `OrderSagaRecoveryExhausted` alongside `OrderCancelled` with `failureReason = order_auto_cancelled_payment_timeout`, or (b) fold the signal into the alert dashboard via the new `cancelReason`. Pick one and document it; do **not** silently drop the operator signal that `STUCK_RECOVERY_FAILED` provided.
4. **Retain the `STUCK_RECOVERY_FAILED` enum value** (no DB migration to drop it). It is repurposed as a *defensive fallback*: if the cancel compensation cannot be co-committed (e.g. outbox write fails), the order may still fall back to `STUCK_RECOVERY_FAILED` rather than silently staying `PENDING`. Primary path = `CANCELLED(PAYMENT_TIMEOUT)`.

### B. payment-service — money-safe handling of the late-payment race (the core)

When `OrderCancelled` arrives, branch on the current `Payment` row state (payment-service holds a `PENDING` Payment created from `OrderPlaced`):

| Payment state at `OrderCancelled` | Action |
|---|---|
| **COMPLETED** (already captured) | Existing `PaymentRefundService.refundPayment(orderId)` full auto-refund. **Already works** — payment-service knows it captured even if order-service didn't. Verify, add test. |
| **PENDING** (not yet confirmed) | **NEW:** transition Payment to a terminal **void** state so any *later* `confirm()` is rejected (do not capture). |

Plus a concurrency belt-and-suspenders in `PaymentConfirmService.confirm()`: if, after a successful capture, the Payment is already in the void state (or the order is known-cancelled), **immediately auto-refund/void** the just-captured amount. This closes the genuinely-concurrent interleaving where `confirm()` and `OrderCancelled` cross.

This makes both event orderings safe:
- `COMPLETED → CANCELLED`: existing refund path.
- `CANCELLED → COMPLETED`: void-guard rejects the late confirm, or the post-capture auto-refund reverses it.

Implementation notes:
- Add a terminal `PaymentStatus` value for "voided because the order was cancelled before capture" (e.g. `VOIDED` / `CANCELLED`) — distinct from `FAILED` (PG-rejected) for analytics/observability. Additive enum; confirm whether the status column needs a Flyway change (likely a plain string/enum column — additive, no destructive migration).
- All transitions idempotent and keyed so a duplicate `OrderCancelled` or a retried `confirm` is a no-op.

### C. product-service — no code change (verify only)

Confirm the `OrderCancelled` → `release()` path treats a `NEW` reservation (the stuck case) as a no-op `RELEASED` with **zero stock movement**. Add/confirm a test asserting no `StockChanged` is emitted for a never-reserved order. No production change expected.

### D. Spec & contract changes (do these **before**/with code, per source-of-truth priority)

1. **`specs/contracts/events/order-events.md`** — `OrderCancelled` payload: add `cancelReason` (`"OPERATOR" | "PAYMENT_TIMEOUT"`). Document it as **additive / back-compatible** (a consumer reading a legacy event without it treats the cancel as `OPERATOR`, matching pre-change behaviour — mirror the `PaymentRefunded.fullyRefunded` back-compat note style). Update the `OrderSagaRecoveryExhausted` section to reflect that the stuck-detector's primary terminal is now `CANCELLED(PAYMENT_TIMEOUT)` and this alert is informational/fallback.
2. **`specs/services/order-service/architecture.md`** (Order state machine row, ~L24) — change the stuck-detector terminal from "terminal `STUCK_RECOVERY_FAILED`" to "`CANCELLED(PAYMENT_TIMEOUT)` (stuck-detector primary) with `STUCK_RECOVERY_FAILED` retained as defensive fallback." Mirror the same lifecycle note in `order-events.md` § "Order lifecycle & BACKORDERED".
3. **`specs/services/payment-service/architecture.md`** — document the `OrderCancelled` consumer branch (COMPLETED→refund, PENDING→void) and the post-capture auto-refund guard; add the new `PaymentStatus` terminal value to the payment state machine.
4. **`specs/contracts/events/payment-events.md`** — if the auto-void emits anything observable, document it; `PaymentRefunded` (partial-refund-aware, TASK-BE-425) already covers the COMPLETED→refund case — confirm no new field is needed.
5. **ADR amendment — CONFIRMED PREREQUISITE (gate resolved 2026-06-25).** ADR-MONO-005 **does** pin `STUCK_RECOVERY_FAILED`: § 2.3 D3 line 67 is a `MUST` ("At cap the saga MUST transition to a terminal `STUCK_RECOVERY_FAILED`-shaped state"), and § 2.6 D6 line 101 records the ecommerce-order decision as compliant with that terminal ("No further change"). Changing the terminal to `CANCELLED(PAYMENT_TIMEOUT)` is therefore an architecture-decision change not in specs (**HARDSTOP-09**). The ADR amendment is tracked as the monorepo-level prerequisite **[TASK-MONO-306](../../../../tasks/ready/TASK-MONO-306-adr-005-category-a-auto-resolving-terminal-refinement.md)** (root `tasks/ready/`, shared `docs/adr/`). **Do not implement BE-435 until MONO-306 is merged** (or land the ADR amendment first within the same atomic PR). The escalation-event retention (§ A.3) satisfies MONO-306's R3 condition.

## Acceptance Criteria

- **AC-1** — Stuck-detector terminal: an order in `PENDING + payment_id IS NULL` past grace × max-attempts transitions to `CANCELLED` (not `STUCK_RECOVERY_FAILED`) and publishes `OrderCancelled` with `cancelReason = PAYMENT_TIMEOUT`. The 1..(max-1) attempt-increment grace behaviour is unchanged.
- **AC-2 (money safety, COMPLETED→CANCELLED)** — Given a payment already `COMPLETED` in payment-service when `OrderCancelled(PAYMENT_TIMEOUT)` arrives, a full `PaymentRefunded` is emitted (no funds retained). Covered by an integration test.
- **AC-3 (money safety, CANCELLED→COMPLETED)** — Given `OrderCancelled` is processed while the Payment is `PENDING`, a subsequent `confirm()` does **not** retain captured funds: either it is rejected pre-capture (Payment voided) or the captured amount is auto-refunded. The order remains `CANCELLED` (order-side guard already prevents status flip). Covered by an integration test exercising **both** orderings.
- **AC-4 (no phantom stock movement)** — Cancelling a never-reserved stuck order moves the reservation `NEW → RELEASED` with **zero** stock decrement/restore and emits no `StockChanged`. Test asserts it.
- **AC-5 (operator visibility preserved)** — The auto-cancellation remains observable to operators (chosen mechanism from § A.3 implemented and documented). The signal that `STUCK_RECOVERY_FAILED` previously provided is not silently lost.
- **AC-6 (contract back-compat)** — `OrderCancelled.cancelReason` is additive; existing consumers (promotion-service, payment-service) behave identically when the field is absent (treated as `OPERATOR`). `payment-events`/`order-events` contracts updated and internally consistent (no dead refs).
- **AC-7 (idempotency)** — Duplicate `OrderCancelled` and retried `confirm()` are no-ops (no double-refund, no double state transition).
- **AC-8 (build/tests GREEN)** — `:order-service:check` and `:payment-service:check` pass. New integration tests authored for the race (AC-2/AC-3); if local Testcontainers is blocked on this Windows host (`project_testcontainers_docker_desktop_blocker`), the IT is authored + excluded from the Docker-free baseline per project convention and CI Linux is authoritative. Unit baselines must not regress.
- **AC-9 (spec/code parity)** — order/payment `architecture.md` state machines, `order-events.md`, `payment-events.md` all reflect the new terminal + reason + void state; no spec still claims `STUCK_RECOVERY_FAILED` is the sole stuck terminal.

## Related Specs

- `projects/ecommerce-microservices-platform/specs/services/order-service/architecture.md` (Order state machine; stuck-detector terminal).
- `projects/ecommerce-microservices-platform/specs/services/payment-service/architecture.md` (Payment state machine; OrderCancelled consumer; refund).
- `docs/adr/ADR-MONO-005-*` § D3 Category A (choreographed-saga stuck-detector recovery outcome — the decision this task refines).

## Related Contracts

- `projects/ecommerce-microservices-platform/specs/contracts/events/order-events.md` — `OrderCancelled` (add `cancelReason`), `OrderSagaRecoveryExhausted` (re-scope to informational/fallback).
- `projects/ecommerce-microservices-platform/specs/contracts/events/payment-events.md` — `PaymentRefunded` (partial-refund-aware, TASK-BE-425) for the COMPLETED→refund leg.
- `projects/ecommerce-microservices-platform/specs/contracts/http/internal/order-confirm-paid-stale.md` / operator cancel internal API — `cancelReason = OPERATOR` for the existing manual path.

## Dependencies / Prior Work

- **TASK-MONO-306 (BLOCKING PREREQUISITE)** — ADR-MONO-005 § 2.3 D3 amendment permitting an auto-resolving Category A terminal. BE-435 must not be implemented until MONO-306 is merged (or the ADR amendment lands first in the same atomic PR). See § D.5.
- **TASK-BE-138** — `OrderStuckDetector` / `OrderStuckRecoveryHandler` (the code being modified).
- **TASK-BE-428** — payment-driven reservation saga (establishes that PENDING orders hold no reserved stock — the fact that makes § C free).
- **TASK-BE-425** — partial-refund-aware `PaymentRefundService` / `PaymentRefunded` (the refund mechanism re-used in AC-2).
- **TASK-BE-139** — `TossPaymentsAdapter` resilience wrap (refund/cancel call goes through this; auto-refund must tolerate PG transient failure → retry/DLQ).

## Edge Cases

- **Late `PaymentCompleted` wins the grace window** — payment confirms during attempts 1..(max-1); `payment_id` becomes non-null; the detector's `paymentId != null` skip guard already aborts cancel. No change needed — verify the guard still holds with the new terminal.
- **Auto-refund itself fails at the PG** (5xx/timeout/circuit-open via TASK-BE-139) — the refund must not be silently dropped; it must retry / route to DLQ / surface an operator alert so the customer is not left un-refunded. Define the failure handling explicitly.
- **Order already operator-cancelled** before the stuck-detector fires — detector sees non-`PENDING`, skips. `cancelReason` of the existing cancel is `OPERATOR`.
- **Double `OrderCancelled`** (operator + detector race) — idempotent cancel; only one `OrderCancelled` effect, one refund (AC-7).
- **`BACKORDERED` order** — out of scope here (it has a completed payment and is operator-cancelled via the existing refund fan-out); this task only changes the *payment-pending* stuck terminal.

## Failure Scenarios

- **F1 — customer loses money (the hole this task closes)** — terminal flips to `CANCELLED` but a delayed-but-real captured payment is never refunded. Guarded by AC-2/AC-3 (both orderings) + the auto-refund-on-late-confirm guard.
- **F2 — double refund** — both the COMPLETED→refund leg and the post-capture auto-refund fire for the same payment. Guarded by AC-7 idempotency (refund keyed; `PaymentRefunded.fullyRefunded`/remaining-refundable gating).
- **F3 — silent operator blind spot** — operators relied on `STUCK_RECOVERY_FAILED` + `OrderSagaRecoveryExhausted` to notice stuck orders; auto-cancel removes that surface. Guarded by AC-5 (visibility preserved).
- **F4 — spec drift** — code changes the terminal but a spec/contract still documents `STUCK_RECOVERY_FAILED` as the only outcome, or `cancelReason` is undocumented. Guarded by AC-6/AC-9.
- **F5 — ADR divergence** — if ADR-MONO-005 mandates `STUCK_RECOVERY_FAILED` as the Category A terminal, changing it without an ADR note is an undocumented architecture decision → **HARDSTOP-09**; resolve via § D.5 before implementation.
