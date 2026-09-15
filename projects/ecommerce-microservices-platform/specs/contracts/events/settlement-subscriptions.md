# Event Contract — settlement-service subscriptions (intra-ecommerce: ← order, ← payment)

Implements **ADR-MONO-030 Step 4 facet b** (marketplace seller settlement / commission).

`settlement-service` builds its commission ledger entirely from the order/payment
event streams (this contract). The **accrual/reversal consume path publishes
nothing**; the only published event is `settlement.period.closed.v1` on the
**period-close** path (see the producer contract
[`settlement-events.md`](settlement-events.md) — introduced in the period-close
increment). `settlement.commission.accrued.v1` remains forward-declared / deferred.
This consumer reads only fields already published by the producer contracts below;
it **never** calls order/payment HTTP APIs to backfill missing data (consumer rule).

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
| `order.order.placed` | `OrderPlaced` | `OrderPlacedSnapshotConsumer` | **Line snapshot.** Upsert `order_id → [{seller_id, gross_minor = unitPrice × quantity}]` + the envelope `tenant_id` + the order's coupon `discount_minor = payload.discountAmount` and `coupon_id = payload.couponId` (both absent → `0` / `null`, TASK-BE-592). Idempotent on `order_id`. No accrual yet (money not captured). The snapshot is the **only** source of the order's `tenant_id` + per-line `seller_id` + discount for settlement. |
| `payment.payment.completed` | `PaymentCompleted` | `PaymentCompletedAccrualConsumer` | **Accrual.** Join the snapshot by `orderId`; per line compute `commission = round(gross × rate_bps / 10000)`, `seller_net = gross − commission`; append an `ACCRUAL` row per line. **When the snapshot's `discount_minor > 0`**, also append one order-level `COST` row to the separate `promotion_cost` ledger (`amount_minor = discount_minor`). Idempotent on `(order_id, payment_id)`. |
| `payment.payment.refunded` | `PaymentRefunded` | `PaymentRefundedReversalConsumer` | **Proportional reversal.** Append `REVERSAL` rows (negatives) clawing back commission — and, for a discounted order, the promotion cost — in proportion to the refund. See the proportional rule below. Idempotent on the envelope `event_id` (each `PaymentRefunded` reverses exactly once — a payment may emit several partial refunds). |

### Coupon discount — who bears it (TASK-BE-592, owner decision 2026-09-15)

**The platform bears the coupon discount, recorded as a separate promotion-cost row.**
Coupons are issued by the tenant's operator, not by a seller, so:

- `commission_accrual` rows stay on the **pre-discount** line gross — commission and
  `seller_net` are exactly what they would be without the coupon. Seller payouts
  (`Σ seller_net`) are unaffected.
- The discount lives in the append-only `promotion_cost` ledger, one order-level row per
  capture (`COST`, positive) and per refund (`REVERSAL`, negative). It is not split per
  seller.
- Per order: `Σ commission_accrual.gross_minor − Σ promotion_cost.amount_minor = captured amount`.
- An `OrderPlaced` without the coupon fields (older order-service, or no coupon) has
  `discount_minor = 0` and produces **no** `promotion_cost` row — byte-for-byte today's
  behaviour.

### Proportional clawback rule (`PaymentRefunded`)

For a refund of `amount` against an order with `accruedGross = Σ ACCRUAL.gross_minor`
and the snapshot's `discount_minor`, the order's **captured** amount is
`captured = accruedGross − discount_minor` (equal to `accruedGross` for an order without
a coupon). The refund fraction is taken against `captured`, because that is the money a
refund gives back:

- **Per accrual row** (`reverses_accrual_id` links each REVERSAL to its parent ACCRUAL),
  reverse `round(orig_gross × amount / captured)` of the gross, **clamped** to the
  row's remaining un-reversed gross (cumulative cap — total reversed never exceeds
  accrued). The reversed gross is re-split via the commission policy
  (`commission = round(gross × rate_bps / 10000)`, `seller_net = remainder`) and
  negated, so every REVERSAL row independently satisfies
  `commission_minor + seller_net_minor = gross_minor` (DB `ck_commission_accrual_split`).
- **Promotion cost** (discounted orders only): reverse
  `round(discount_minor × amount / captured)`, clamped to the order's remaining
  un-reversed promotion cost, as one `promotion_cost` `REVERSAL` row linked to its `COST`
  row.
- **On the final refund** (`fullyRefunded = true`) reverse the **exact remaining**
  per field (`orig − already-reversed` for gross / commission / seller_net, and for the
  promotion cost) rather than a fresh proportional round. This absorbs all accumulated
  partial-rounding drift so the order's accruals net **exactly** zero per seller and its
  promotion cost nets **exactly** zero.
- **Idempotency** is the consumer's `event_id` dedupe (`processed_event`). The old
  `(order_id, payment_id)` reversal gate is **removed** — it would block a second partial
  refund of the same payment. No accruals (cancel-before-capture) → no-op.

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
