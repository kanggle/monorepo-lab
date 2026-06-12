# Event Contract — settlement-service subscriptions (intra-ecommerce: ← order, ← payment)

Implements **ADR-MONO-030 Step 4 facet b** (marketplace seller settlement / commission).

`settlement-service` is a **terminal consumer** — it builds its commission ledger
entirely from the order/payment event streams and **publishes nothing in v1** (a
`settlement.commission.accrued.v1` is forward-declared for the payout increment).
It reads only fields already published by the producer contracts below; it **never**
calls order/payment HTTP APIs to backfill missing data (consumer rule).

Authoritative producer schemas:
[`order-events.md`](order-events.md) (OrderPlaced) + [`payment-events.md`](payment-events.md)
(PaymentCompleted / PaymentRefunded). This subscription adds `settlement-service`
to those events' **Consumers** lists (additive — no producer payload change).

---

## Consumer Group

`settlement-service` — independent offsets from order-service / promotion-service
(which also consume `OrderPlaced` / `payment.payment.*`).

## Subscribed Topics

| Topic | Producer event | Handler (new) | Effect on the settlement ledger |
|---|---|---|---|
| `order.order.placed` | `OrderPlaced` | `OrderPlacedSnapshotConsumer` | **Line snapshot.** Upsert `order_id → [{seller_id, gross_minor = unitPrice × quantity}]` + the envelope `tenant_id`. Idempotent on `order_id`. No accrual yet (money not captured). The snapshot is the **only** source of the order's `tenant_id` + per-line `seller_id` for settlement. |
| `payment.payment.completed` | `PaymentCompleted` | `PaymentCompletedAccrualConsumer` | **Accrual.** Join the snapshot by `orderId`; per line compute `commission = round(gross × rate_bps / 10000)`, `seller_net = gross − commission`; append an `ACCRUAL` row per line. Idempotent on `(order_id, payment_id)`. |
| `payment.payment.refunded` | `PaymentRefunded` | `PaymentRefundedReversalConsumer` | **Reversal.** Append `REVERSAL` rows (negatives) that net the order's accruals to zero. v1 = **full** reversal of the order's accruals (the refund is treated as whole-order; partial/proportional clawback against `PaymentRefunded.amount` is forward-declared). Idempotent on `(order_id, payment_id)`. |

## Excluded topics (by design — NOT consumed)

| Topic | Why excluded |
|---|---|
| `order.order.confirmed` | Its payload carries **no `sellerId` and no amounts** (only `sku`/`productId`/`variantId`/`quantity`) — useless for commission. The seller-attributed amounts live in `OrderPlaced`. |
| `order.order.cancelled` | A cancel before payment capture means **no accrual was ever booked** (accrual fires on `PaymentCompleted`). Nothing to reverse. A cancel after capture surfaces as a `PaymentRefunded`. |
| `payment.payment.failed` | No money captured → no accrual. |
| `order.alert.saga.recovery.exhausted` | Operator alert, not a money event. |

## Envelope & tenant derivation — **ecommerce convention (snake_case)**

order/payment events use the ecommerce envelope `event_id` / `event_type` /
`occurred_at` / `source` / `payload` (snake_case). Each consumer dedupes on
`event_id` via a `processed_event` table (libs `java-messaging` processed-events
pattern) in the same `@Transactional` boundary as the ledger write.

> **★ tenant_id derivation (settlement-specific).** `order.order.placed` carries
> `tenant_id` on the **envelope** (ADR-030 Step 2, M5). `payment.payment.*` does
> **NOT** carry `tenant_id` — payment-service has not joined Step 2 (see
> `payment-events.md` envelope: `event_id`/`event_type`/`occurred_at`/`source`/
> `payload`, no `tenant_id`). Therefore the accrual/reversal consumers derive the
> order's `tenant_id` from the **cached `OrderPlaced` snapshot** (joined on
> `orderId`), never from the payment event. This is why the snapshot must be
> persisted first (see Consumer Rules — ordering).

## Consumer Rules

- Handle duplicate events idempotently (dedupe on `event_id`; accrual/reversal also
  keyed on `(order_id, payment_id)`).
- Do not fail the whole pipeline on one malformed event — route to the retry topic → DLQ.
- Do not call order/payment HTTP APIs to compensate for missing event data.
- **Ordering (F2):** `PaymentCompleted` is expected to arrive **after** its
  `OrderPlaced` (placement precedes capture). If the snapshot is missing when a
  `PaymentCompleted` arrives (out-of-order / lost placement), the accrual cannot be
  attributed (no `seller_id` / `tenant_id`): the consumer raises → the event is
  retried → DLQ after the configured attempts. A snapshot-buffering / deferred-accrual
  mechanism is forward-declared if this proves frequent in practice.
- Fields not listed in the producer contracts must not be relied upon.
