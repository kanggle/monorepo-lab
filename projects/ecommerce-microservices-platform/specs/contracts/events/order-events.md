# Event Contract: order-service

## Overview
Domain events published by order-service.
Consumers must not depend on fields not defined in this contract.

---

## Event Envelope (common to all events)
```json
{
  "event_id": "string (UUID)",
  "event_type": "string",
  "occurred_at": "string (ISO 8601)",
  "source": "order-service",
  "tenant_id": "string (owning tenant; default 'ecommerce')",
  "payload": {}
}
```

> **`tenant_id` (ADR-MONO-030 Step 2, M5).** The outer-axis tenant that owns the
> order. Carried on the envelope (not the payload) so consumers can route/scope
> per tenant across the async boundary without parsing the payload. Derived from
> the request's tenant context at publish time; saga-path events (confirm via
> consumed `product.product.stock-changed`, etc.) carry the order's tenant. A
> standalone deployment or a pre-multi-tenant order resolves to the default
> tenant `'ecommerce'` (net-zero, D8). The inner `seller_id` axis (Step 3 /
> TASK-BE-363) is carried **per order-line** in the `OrderPlaced` payload
> `items[].sellerId` (below), not on the envelope — the order header is
> tenant-only, each line is independently seller-attributed. ADR-MONO-022
> fulfillment-loop events are threaded in Step 4.

---

## OrderPlaced

Published when a new order is successfully created.

**Consumers:** payment-service, promotion-service, settlement-service (line snapshot — ADR-MONO-030 Step 4 facet b; see `settlement-subscriptions.md`)

**Payload**
```json
{
  "orderId": "string (UUID)",
  "userId": "string (UUID)",
  "totalPrice": 30000,
  "items": [
    {
      "productId": "string (UUID)",
      "variantId": "string (UUID)",
      "quantity": 2,
      "unitPrice": 15000,
      "sellerId": "string"
    }
  ],
  "shippingAddress": {
    "recipientName": "string",
    "phone": "string",
    "zipCode": "string",
    "address1": "string",
    "address2": "string | null"
  }
}
```

`items[].sellerId` (inner marketplace axis — ADR-MONO-030 Step 3 §3.2) is the
seller this line is attributed to, captured immutably at placement from the line
snapshot (order-service does not call product-service). A single order may span
multiple sellers (each line attributed independently). Absent at placement →
the default seller `default` (D8 net-zero).

---

## OrderConfirmed

Published when an order is confirmed (status transitions `PENDING → CONFIRMED`,
or `BACKORDERED → CONFIRMED` when a held order is satisfied by a later
replenishment — see the lifecycle note below). Co-committed with the status
transition via the transactional outbox.

**Consumers:** shipping-service

**Topic:** `order.order.confirmed`

**Payload**
```json
{
  "orderId": "string (UUID)",
  "userId": "string (UUID)",
  "confirmedAt": "string (ISO 8601)",
  "lines": [
    {
      "sku": "string (ecommerce sellable-unit id: variantId, else productId)",
      "productId": "string (UUID)",
      "variantId": "string (UUID) | null",
      "quantity": 2
    }
  ],
  "shippingAddress": {
    "recipientName": "string",
    "address": "string (single-line full address)",
    "phone": "string | null"
  }
}
```

> **Additive enrichment (ADR-MONO-022 §D7).** `lines` and `shippingAddress` were
> added so shipping-service can build the cross-project fulfillment-intent event
> (`ecommerce.fulfillment.requested.v1`) without a synchronous call back to
> order-service. Envelope stays in the ecommerce-internal `event_id`/`event_type`
> shape — only the payload gained additive fields. Consumers that read only
> `orderId`/`userId`/`confirmedAt` are unaffected. `shippingAddress` may be null
> for orders without one; a line's `sku` is the ecommerce sellable-unit id (the
> variant when present, else the product), to be ACL-mapped to a wms SKU code.

---

## OrderCancelled

Published when an order is cancelled.

**Consumers:** payment-service, promotion-service

**Payload**
```json
{
  "orderId": "string (UUID)",
  "userId": "string (UUID)",
  "cancelledAt": "string (ISO 8601)",
  "cancelReason": "OPERATOR"
}
```

- `cancelReason` (TASK-BE-435) — why the order was cancelled. One of
  `"OPERATOR"` (a human-/user-initiated cancel: REST user cancel, operator internal
  API, warehouse-backorder cancel, user-withdrawal cascade) or `"PAYMENT_TIMEOUT"`
  (the stuck-detector auto-cancelled a payment-pending order that exhausted its grace
  attempts — see `OrderSagaRecoveryExhausted` and the lifecycle note below).
  payment-service branches on this to drive the money-safe refund/void compensation:
  a `PAYMENT_TIMEOUT` cancel of a payment that nonetheless captured must refund/void.

**Back-compatibility:** `cancelReason` is additive. A consumer reading a legacy
`OrderCancelled` event without it MUST treat the cancel as `"OPERATOR"`, matching the
pre-TASK-BE-435 behaviour (every cancel was an operator/user cancel; the stuck terminal
was `STUCK_RECOVERY_FAILED`, not a cancel). Mirrors the `PaymentRefunded.fullyRefunded`
back-compat note in `payment-events.md`.

---

## OrderSagaRecoveryExhausted

Published by the order-service stuck-detector (TASK-BE-138, ADR-MONO-005 § 2.3
D3 Category A) when an order has remained in `PENDING + payment_id IS NULL` past
the configured grace period for the maximum number of attempts.

> **Re-scoped to informational (TASK-BE-435 / TASK-MONO-306 — ADR-MONO-005 § 2.3
> D3 auto-resolving-terminal refinement).** The stuck-detector's **primary terminal
> is now `CANCELLED(PAYMENT_TIMEOUT)`** (an `OrderCancelled` event with
> `cancelReason = "PAYMENT_TIMEOUT"` is published — see above), **not**
> `STUCK_RECOVERY_FAILED`. This alert is **retained as an informational
> operator-visibility signal** co-committed (T3) alongside the cancel in the same
> stuck-recovery transaction (R3): it preserves the observability that
> `STUCK_RECOVERY_FAILED` previously provided, without being an operator hand-off
> anymore. `STUCK_RECOVERY_FAILED` is retained as a defensive fallback (R4) for the
> case where the cancel compensation cannot be co-committed.

**Topic:** `order.alert.saga.recovery.exhausted`

**Consumers:** notification-service / operator alert dashboard (out-of-scope
for ecommerce v1; the topic is published so future alert subscribers can
consume it without spec drift).

**Payload**
```json
{
  "orderId": "string (UUID)",
  "userId": "string (UUID)",
  "lastState": "PENDING",
  "attemptCount": 5,
  "placedAt": "string (ISO 8601)",
  "lastTransitionAt": "string (ISO 8601)",
  "failureReason": "order_auto_cancelled_payment_timeout"
}
```

---

## Order lifecycle & BACKORDERED (TASK-BE-428)

`OrderStatus`: `PENDING → CONFIRMED → SHIPPED → DELIVERED`, with `CANCELLED`
(from `PENDING|CONFIRMED|BACKORDERED`) and `STUCK_RECOVERY_FAILED`.

**Stuck-detector terminal (TASK-BE-435 / TASK-MONO-306).** A payment-pending order
(`PENDING + payment_id IS NULL`) that exhausts its grace attempts is **auto-cancelled
to `CANCELLED(PAYMENT_TIMEOUT)`** (the stuck-detector **primary** terminal), publishing
`OrderCancelled` with `cancelReason = "PAYMENT_TIMEOUT"` plus the informational
`OrderSagaRecoveryExhausted` alert. `STUCK_RECOVERY_FAILED` is **retained as a defensive
fallback** (R4) — reached only if the cancel compensation cannot be co-committed — and is
no longer the primary terminal.

**`BACKORDERED`** (payment-driven reservation saga, TASK-BE-428): after payment,
product-service attempts an all-or-nothing stock reservation. On success it emits
`StockChanged(ORDER_RESERVED)` and the order confirms (`PENDING → CONFIRMED`). If
any line is short, product-service emits `OrderReservationFailed`
(`product.product.reservation-failed`) and the order moves `PENDING → BACKORDERED`
with **no stock decremented**. A later replenishment of the short variants triggers
a FIFO re-reservation; on success the order moves `BACKORDERED → CONFIRMED` (a normal
`OrderConfirmed`). A `BACKORDERED` order may be cancelled by an operator
(`OrderCancelled` → refund fan-out). No new outbound event is published for the
`BACKORDERED` transition itself — the status is observed via the order read API.

## Consumer Rules

- Consumers must handle duplicate events idempotently.
- Consumers must not fail the entire pipeline on a single malformed event — route to DLQ instead.
- Consumers must not call order-service HTTP API to compensate for missing event data.
- Fields not listed in this contract must not be relied upon.
