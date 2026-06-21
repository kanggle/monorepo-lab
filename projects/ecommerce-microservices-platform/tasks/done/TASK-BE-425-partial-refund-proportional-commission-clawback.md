# Task ID

TASK-BE-425

# Title

Partial payment refunds + proportional commission clawback (payment + order + settlement, atomic cross-service)

# Status

done

# Owner

backend

# Task Tags

- code
- api
- event

---

# Required Sections (must exist)

- Goal
- Scope (in/out)
- Acceptance Criteria
- Related Specs
- Related Contracts
- Edge Cases
- Failure Scenarios

---

# Goal

Today payment refunds are **full-only** (`order.order.cancelled` → `PaymentRefundService.refundPayment(orderId)` → `payment.refund()` → `payment.payment.refunded` with `amount = full payment`), and settlement reverses the order's commission accruals to net-zero (full). Introduce **partial refunds** (refund a specified amount ≤ remaining refundable) and **proportional commission clawback** in settlement (the forward-declared increment in `settlement-subscriptions.md`).

This realises the `ReversePaymentCommand` javadoc's forward-declare ("partial/proportional clawback is forward-declared") by first building the missing upstream capability (partial refunds in payment-service) and the downstream proportional reversal together — a single increment is the only way either half delivers value.

# Scope

## In Scope

**Contracts (spec-first):**
- `specs/contracts/events/payment-events.md` — `PaymentRefunded` payload: `amount` = **this refund's** amount; add `totalRefunded` (cumulative), `fullyRefunded` (boolean). Additive, back-compatible (absent → treat as full).
- `specs/contracts/http/payment-api.md` — new `POST /api/payments/{paymentId}/refund {amount}`; add `PARTIALLY_REFUNDED` status value; error rows (400 over-refund, 403, 404).
- `specs/contracts/events/settlement-subscriptions.md` — reversal rule full → **proportional**; document cumulative-cap + final-exact-zero + per-row split-invariant + `event_id` idempotency.
- Reconcile service/feature specs that narrate "v1 = full reversal".

**payment-service:**
- `PaymentStatus` += `PARTIALLY_REFUNDED`; `Payment` += cumulative `refundedAmount`, `refund(long amount)` (0 < amount ≤ remaining; from COMPLETED|PARTIALLY_REFUNDED; status = fully? REFUNDED : PARTIALLY_REFUNDED), keep no-arg `refund()` = refund-remaining (OrderCancelled path unchanged).
- Flyway `V6__add_refunded_amount.sql` (backfill REFUNDED rows → amount, else 0, NOT NULL DEFAULT 0).
- `PaymentRefundedEvent.Payload` += `totalRefunded`, `fullyRefunded`; `amount` = this refund.
- `PaymentRefundService.refundPayment(paymentId, amount)` (HTTP path) + new controller endpoint + DTO. PG `cancelPayment` gains an `amount` (partial cancel; no-op gateway in demo).

**order-service:**
- `PaymentRefundedEvent.PaymentRefundedPayload` += `totalRefunded`, `fullyRefunded`.
- `PaymentRefundedEventConsumer`: call `markRefunded` **only when `fullyRefunded`**; partial → no-op on order status (no `PARTIALLY_REFUNDED` order state).

**settlement-service (accounting core):**
- `PaymentEvent.Payload` += `amount`, `fullyRefunded`; `ReversePaymentCommand` += `refundAmount`, `fullyRefunded`.
- `commission_accrual` += `reverses_accrual_id` (Flyway `V3`, nullable); `CommissionAccrual` += `reversesAccrualId`.
- `SettlementService.reverse` proportional: per accrual row reverse `round(orig_gross × refundAmount / accruedGross)` clamped to per-row remaining (partial), or **exact per-field remaining** (orig − already-reversed) on `fullyRefunded` (per-row exact zero; absorbs partial rounding drift). Idempotency = consumer `event_id` dedupe (drop the `(orderId, paymentId)` reversal gate that blocks a 2nd partial).
- Repo: fetch all rows by order (ACCRUAL + REVERSAL) to compute per-accrual already-reversed via `reverses_accrual_id`.

## Out of Scope

- Real Toss partial-cancel reconciliation/webhooks (pass `cancelAmount`; demo gateway is no-op).
- A `PARTIALLY_REFUNDED` **order** state / order-side partial visibility.
- HTTP refund idempotency-key store (domain cumulative cap + over-refund rejection already prevent double-charge).
- Line/SKU-targeted refunds (this claws back proportionally across all lines).
- Refund landing **after** a settlement period close (period-reopen interaction) — known edge, separate task.

# Acceptance Criteria

- **AC-1** payment `refund(amount)`: 0 < amount ≤ remaining else `InvalidPaymentException`; COMPLETED→PARTIALLY_REFUNDED→REFUNDED; cumulative `refundedAmount` tracked; over-refund rejected.
- **AC-2** `POST /api/payments/{paymentId}/refund {amount}` — 200 on success, 400 over-refund/invalid, 404 unknown, 403 non-owner. OrderCancelled full-refund path unchanged (full `refund()` still works, replay no-op).
- **AC-3** `payment.payment.refunded` carries `amount` (this refund), `totalRefunded`, `fullyRefunded`; serialization + contract tests updated; envelope unchanged.
- **AC-4** order-service marks the order refunded **iff** `fullyRefunded`; a partial event is a no-op on order status (no DLQ, no throw).
- **AC-5** settlement proportional: a single partial of fraction f reverses ~f of each accrual line; per-row split invariant (`commission+seller_net==gross`) holds on every REVERSAL row.
- **AC-6** two partials summing to full are BOTH processed (2nd not blocked); after the final (`fullyRefunded`) refund the order's accruals net **exactly** zero per seller (rounding residue absorbed by the final refund).
- **AC-7** cumulative cap: total reversed never exceeds total accrued (per-row clamp). No accruals → no-op.
- **AC-8** replay of the same refund `event_id` → skipped (no double reversal).
- **AC-9** `:payment-service`, `:order-service`, `:settlement-service` `compileJava` + unit `test` GREEN; ITs (`@Tag("integration")`) validated on CI Linux.

# Related Specs

- `specs/services/settlement-service/{architecture,overview}.md`, `specs/services/payment-service/{architecture,overview}.md`
- `specs/features/{payment-processing,marketplace-settlement}.md`, `specs/use-cases/payment-and-refund.md`

# Related Contracts

- `specs/contracts/events/payment-events.md`, `specs/contracts/http/payment-api.md`, `specs/contracts/events/settlement-subscriptions.md` (all edited)
- `specs/contracts/events/order-events.md` / `order-api.md` — **no change** (no partial order state).

# Edge Cases

- Partial that exactly equals remaining → fully refunded (status REFUNDED, `fullyRefunded=true`).
- Rounding: gross not divisible by fraction → per-row HALF_UP via `CommissionPolicy.split`; drift absorbed by final-refund exact-remaining.
- Multiple accrual rows for the same seller (two items) → `reverses_accrual_id` attributes each reversal to its row (per-row exact, not per-seller).
- Old `payment.payment.refunded` events without the new fields (replay) → `fullyRefunded` absent = treat as full (back-compat).
- Refund with no prior accrual (cancel-before-capture) → no-op.

# Failure Scenarios

- **F1 — over-refund**: cumulative > payment amount → domain rejects (`InvalidPaymentException`); HTTP 400.
- **F2 — 2nd-partial blocked**: the old `(orderId, paymentId)` reversal gate would skip it → removed; `event_id` dedupe is the guard.
- **F3 — rounding drift leaves non-zero balance after full refund**: prevented by exact per-field remaining on the `fullyRefunded` refund.
- **F4 — DB split-invariant violation on a REVERSAL row**: prevented by recomputing the split on the reversed gross (partial) / exact remaining (full), never independent rounding.
- **F5 — partial marks order fully refunded**: prevented by gating `markRefunded` on `fullyRefunded`.
