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

## Consumer Rules

- Consumers must handle duplicate events idempotently.
- Consumers must not fail the entire pipeline on a single malformed event — route to DLQ instead.
- Consumers must not call payment-service HTTP API to compensate for missing event data.
- Fields not listed in this contract must not be relied upon.
