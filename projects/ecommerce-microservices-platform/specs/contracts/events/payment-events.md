# Event Contract: payment-service

## Overview
Domain events published by payment-service.
Consumers must not depend on fields not defined in this contract.

---

## Event Envelope (common to all events)

> `tenant_id` was added to the envelope in TASK-BE-400 (ADR-MONO-030 Step 4 facet c).
> Consumers that do not yet read `tenant_id` are unaffected — it is an additive field.
> Pre-existing outbox rows serialized before this change may lack the field; consumers
> must treat a missing `tenant_id` as the default tenant (`'ecommerce'`).

```json
{
  "event_id": "string (UUID)",
  "event_type": "string",
  "occurred_at": "string (ISO 8601)",
  "source": "payment-service",
  "tenant_id": "string (tenant slug, e.g. 'ecommerce')",
  "payload": {}
}
```

---

## PaymentCompleted

Published when a payment is successfully processed.

**Consumers:** order-service, settlement-service (commission accrual — ADR-MONO-030 Step 4 facet b; see `settlement-subscriptions.md`)

**Payload**
```json
{
  "paymentId": "string (UUID)",
  "orderId": "string (UUID)",
  "userId": "string (UUID)",
  "amount": 30000,
  "paidAt": "string (ISO 8601)"
}
```

---

## PaymentFailed

Published when a payment processing fails.

**Consumers:** order-service

**Payload**
```json
{
  "paymentId": "string (UUID)",
  "orderId": "string (UUID)",
  "userId": "string (UUID)",
  "reason": "string",
  "failedAt": "string (ISO 8601)"
}
```

---

## PaymentRefunded

Published when a payment refund is processed. Refunds may be **partial** — a payment
can emit several `PaymentRefunded` events whose `amount`s sum (at most) to the captured
total. Each event describes **one** refund.

**Consumers:** order-service, settlement-service (commission reversal — ADR-MONO-030 Step 4 facet b; see `settlement-subscriptions.md`)

**Payload**
```json
{
  "paymentId": "string (UUID)",
  "orderId": "string (UUID)",
  "userId": "string (UUID)",
  "amount": 30000,
  "totalRefunded": 30000,
  "fullyRefunded": true,
  "refundedAt": "string (ISO 8601)"
}
```

- `amount` — the amount of **this** refund (a partial refund < the captured total).
- `totalRefunded` — cumulative refunded for the payment **including** this event
  (`≤` captured amount).
- `fullyRefunded` — `true` iff `totalRefunded == captured amount` (this event closed
  the payment out). Consumers gate full-refund effects on this flag, not on `amount`.

**Back-compatibility:** `totalRefunded` / `fullyRefunded` are additive. A consumer
reading a legacy event without them must treat the refund as a **full** refund
(`fullyRefunded = true`), matching the pre-partial-refund behaviour.

---

## OrderCancelled handling — no new event (TASK-BE-435)

When payment-service consumes `OrderCancelled` (`order.order.cancelled`), it branches on the
current payment state for money safety (see `specs/services/payment-service/architecture.md`
§ "OrderCancelled consumer"):

- **Captured payment** (`COMPLETED` / `PARTIALLY_REFUNDED`) → full auto-refund, which emits the
  existing **`PaymentRefunded`** above. The partial-refund-aware `totalRefunded` / `fullyRefunded`
  fields (TASK-BE-425) already cover this leg — **no new field is required**.
- **Never-captured payment** (`PENDING`) → the payment row transitions to the terminal `VOIDED`
  state. **No PG money movement occurred and no observable event is emitted** — voiding a
  never-captured payment owes nothing to refund and notifies no downstream consumer. (The order
  is already terminal via the order-service `OrderCancelled`; payment-service emits nothing extra.)

`VOIDED` is therefore an internal payment-service state only; it does not appear on the event wire.

---

## PaymentRefundStranded (money-safety alert — TASK-BE-437)

Published when the synchronous HTTP `confirm()` **post-capture auto-refund** (the
TASK-BE-435 belt-and-suspenders guard — see `specs/services/payment-service/architecture.md`
§ "OrderCancelled consumer") captures funds for a concurrently-cancelled order and then
**fails to reverse the capture at the PG** (`PgGatewayUnavailableException` 5xx/circuit-open/
timeout, or `PgConfirmFailedException` 4xx). Without this alert the captured customer funds
would be silently stranded — this synchronous path has no DLT/retry net (unlike the consumer
refund path). The escalation is written to the transactional outbox in a **`REQUIRES_NEW`**
boundary (separate bean `PaymentRefundStrandedRecorder`) so it **commits even though `confirm()`
rolls back**.

**Topic:** `payment.alert.refund.stranded`

**Consumers:** notification-service / operator alert dashboard (out-of-scope for ecommerce v1;
the topic is published so future alert subscribers can consume it without spec drift — same
disposition as `OrderSagaRecoveryExhausted` in `order-events.md`).

**Payload**
```json
{
  "paymentId": "string (UUID)",
  "orderId": "string (UUID)",
  "paymentKey": "string (PG payment key)",
  "amount": 30000,
  "reason": "PgGatewayUnavailableException",
  "occurredAt": "string (ISO 8601)"
}
```

- `paymentKey` / `orderId` / `amount` — carried so a reconciliation/operator can **check PG
  state first** before any compensating action (F3: a transient 5xx cancel *may* have actually
  succeeded at the PG — acting blindly risks a double-refund).
- `reason` — the PG failure kind (exception simple-name): `PgGatewayUnavailableException`
  (transient 5xx/circuit-open/timeout — cancel outcome **unknown**) vs `PgConfirmFailedException`
  (definitive 4xx — cancel **rejected**, money captured, needs intervention).

**At-least-once / idempotency:** a client retry of the same `confirm()` re-reads `VOIDED` and may
re-emit this escalation. The (future) alert consumer dedupes on `paymentId` / `event_id`.
Acceptable and documented (AC-4). The full **auto-reconciliation sweeper** that retries the PG
cancel (formerly an out-of-scope follow-up) is delivered by **TASK-BE-438** — see
`PaymentRefundUnresolved` below for its terminal escalation.

---

## PaymentRefundUnresolved (terminal money-safety escalation — TASK-BE-438)

Published when the **stranded-refund auto-reconciliation sweeper** (`StrandedRefundSweeper` +
`StrandedRefundReconciler`; see `specs/services/payment-service/architecture.md` § "Stranded-refund
reconciliation sweeper") **could not auto-heal** a `PaymentRefundStranded` stranding. The sweeper
retries the PG cancel (PG-state-first, double-refund-safe) with bounded exponential backoff; a
record reaches the terminal `UNRESOLVED` state when **either** the attempt budget
(`payment.stranded-refund.max-attempts`, default 8) is exhausted **or** the PG issues a
**definitive 4xx rejection** of the cancel (`PgConfirmFailedException`). At that point the machine
has given up and an **operator must act** on captured funds it could not reverse (ADR-MONO-005
§ 2.3 D3 Category-A terminal). Distinct topic so operator paging can route the terminal case
separately from the transient one.

The terminal status transition (`stranded_refund.status STRANDED → UNRESOLVED`) and this event are
written to the transactional outbox in the reconciler's **`REQUIRES_NEW`** boundary and **co-commit**
(F3 — a terminal record must never be buried without its escalation).

**Topic:** `payment.alert.refund.unresolved`

**Consumers:** notification-service / operator alert dashboard (out-of-scope for ecommerce v1; the
topic is published so future alert subscribers can consume it without spec drift — same disposition
as `PaymentRefundStranded` / `OrderSagaRecoveryExhausted`).

**Payload**
```json
{
  "paymentId": "string (UUID)",
  "orderId": "string (UUID)",
  "paymentKey": "string (PG payment key)",
  "amount": 30000,
  "reason": "PgGatewayUnavailableException",
  "attempts": 8,
  "lastError": "attempt cap (8) exhausted; last=cancel transient failure: PgGatewayUnavailableException",
  "occurredAt": "string (ISO 8601)"
}
```

- `reason` — the **original** stranding cause (the failing PG exception kind at `confirm()` time),
  carried through from `PaymentRefundStranded`.
- `attempts` — how many reconciliation attempts were made before terminating (recovery history).
- `lastError` — the most recent failure detail (why the auto-reconciliation could not heal it).

**Double-refund safety (the central invariant, F1).** Before re-issuing a cancel the sweeper reads
the **actual PG state** (`GET /v1/payments/{paymentKey}`). An already-`CANCELED` payment is marked
`RESOLVED` **without** a second cancel (the transient stranding's original cancel actually
succeeded). A terminal `UNRESOLVED` is therefore reached only after the PG was observed to still
hold the capture (or the read/cancel definitively failed) — never by re-cancelling a
already-reversed payment.

**At-least-once / idempotency:** terminal is terminal — an `UNRESOLVED` record is excluded from the
sweeper's poll predicate and never re-selected, so the event fires **once** per stranding under
normal operation. The (future) alert consumer dedupes on `paymentId` / `event_id`.

---

## Consumer Rules

- Consumers must handle duplicate events idempotently.
- Consumers must not fail the entire pipeline on a single malformed event — route to DLQ instead.
- Consumers must not call payment-service HTTP API to compensate for missing event data.
- Fields not listed in this contract must not be relied upon.
