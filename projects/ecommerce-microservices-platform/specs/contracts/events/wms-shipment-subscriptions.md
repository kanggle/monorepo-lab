# Event Contract — ecommerce subscriptions to wms (cross-project: ← wms)

Implements **ADR-MONO-022** (ecommerce ↔ wms order-fulfillment integration), D1 return leg.

Two ecommerce services subscribe to **wms-platform** return-leg events to close the loop:

- `shipping-service` — advances the Shipping record (`PREPARING → SHIPPED`) on ship, and
  raises an ops alert on backorder (replacing today's manual-admin step).
- `order-service` — **v2(a), TASK-MONO-197**: on the backorder/cancel signal, auto-cancels the
  Order and triggers the existing refund saga (see § order-service consumer below).

The authoritative envelope + payload schemas live in the **producing service** (wms):
`projects/wms-platform/specs/contracts/events/outbound-events.md`.

---

## Consumer Groups

- `shipping-service-wms` — distinct from the `shipping-service` group used for ecommerce-internal
  topics, so wms-leg offsets/rebalancing are independent.
- `order-service-wms` (v2(a), TASK-MONO-197) — distinct from the `order-service` group used for
  ecommerce-internal topics, so the wms backorder leg has independent offsets. Both groups receive
  `wms.outbound.order.cancelled.v1` (different groups ⇒ independent delivery).

## Subscribed Topics

| Topic | wms event | Consumer (group) | Effect on ecommerce |
|---|---|---|---|
| `wms.outbound.shipping.confirmed.v1` | `outbound.shipping.confirmed` | `WmsShippingConfirmedConsumer` (`shipping-service-wms`) | Shipping `PREPARING → SHIPPED` with `trackingNumber = shipmentNo`, `carrier = carrierCode`; existing `ShippingStatusChanged` then drives order-service `Order → SHIPPED`. |
| `wms.outbound.order.cancelled.v1` | `outbound.order.cancelled` | `WmsOutboundCancelledConsumer` (`shipping-service-wms`) | Backorder/cancel path: ops alert; Shipping stays `PREPARING`-flagged (no Shipping row typically exists yet at backorder time). Unchanged from MONO-196. |
| `wms.outbound.order.cancelled.v1` | `outbound.order.cancelled` | `WmsOutboundCancelledConsumer` (`order-service-wms`, **v2(a)**) | Locate Order by `orderId == orderNo`; if cancellable (PENDING/CONFIRMED) → `Order → CANCELLED` (system-initiated) → emit `order.cancelled` → existing `payment-service` refund + `promotion-service` coupon-restore fan-out. Status-safe + idempotent (see § order-service consumer). |

## Envelope — **wms convention (camelCase)**

> ⚠️ wms events use camelCase `eventId`/`eventType`/`occurredAt`/`aggregateType`/`aggregateId`/`payload`
> (see `outbound-events.md` § Global Envelope) — **not** ecommerce's `event_id`/`event_type` shape.
> The new consumer DTOs map the wms shape. Reuse `EventDeduplicationChecker.isDuplicate(eventId, eventType)`
> with the camelCase `eventId`.

## Correlation (ADR-022 D5)

`wms.outbound.shipping.confirmed.v1` carries the **additive `orderNo`** field (= the ecommerce
order id this consumer sent on the forward leg). The consumer locates the local Shipping by its
`orderId == orderNo`. No wms↔ecommerce id map is stored. (If `orderNo` is absent — a pre-D5 wms —
the consumer logs + DLTs; it does not guess.)

## Fields read

**`outbound.shipping.confirmed` payload** (subset shipping-service reads):

```json
{
  "orderId": "<wms internal id, ignored>",
  "orderNo": "<= ecommerce orderId — the correlation key>",
  "shipmentNo": "SHP-20260608-0001",
  "carrierCode": "CJ-LOGISTICS",
  "shippedAt": "2026-06-08T15:00:00Z"
}
```

**`outbound.order.cancelled` payload** (subset):

```json
{
  "orderNo": "<= ecommerce orderId>",
  "previousStatus": "PICKING",
  "reason": "INSUFFICIENT_STOCK | ...",
  "cancelledAt": "2026-06-08T11:30:00Z"
}
```

## order-service consumer (v2(a), TASK-MONO-197)

`order-service` consumes `wms.outbound.order.cancelled.v1` (group `order-service-wms`) and
auto-cancels the Order, realizing ADR-022 §D4 v2(a). It builds **no new refund machinery** — it
re-emits the existing `order.cancelled` event, whose downstream (`payment-service` refund +
`promotion-service` coupon restore + order-service `markRefunded`) is already wired for the
user-initiated cancel path.

`OrderBackorderCancellationService.cancelForBackorder(orderId, reason)` is **system-initiated**
(no userId ownership check, unlike the user REST cancel) and **status-safe + idempotent**:

| Order status at event time | Action |
|---|---|
| PENDING / CONFIRMED (cancellable) | `Order → CANCELLED`; publish `order.cancelled` (fires refund + coupon-restore fan-out). |
| CANCELLED (already) | no-op (idempotent re-delivery or user-cancelled-first). |
| SHIPPED / DELIVERED / STUCK_RECOVERY_FAILED | **ALERT log + skip** — never mutate a shipped order (backorder-after-ship is a contract anomaly). |
| not found | warn + skip (no fabricated order). |

Correlation = `orderId == payload.orderNo` (D5), same as shipping-service. The customer ends with a
CANCELLED order + refund; shipping-service's alert remains the ops signal.

## Idempotency / Retry / DLT

- Dedupe on envelope `eventId` (`processed_events`, T8) — re-delivery is a no-op (both consumers,
  independent dedupe rows keyed by their own `eventType`).
- Shipping transition `PREPARING → SHIPPED` is itself idempotent (re-applying SHIPPED to an
  already-SHIPPED record is a no-op per the shipping state machine).
- order-service cancel is idempotent via dedupe **and** the CANCELLED-status no-op guard above.
- Retry: 3 attempts exponential backoff; then `<topic>.dlq`. Unparseable / missing `orderNo` →
  non-retryable → DLT + alert.

## Schema compatibility

wms v1 envelope + the additive `orderNo` (ADR-022 D5). If wms bumps to `*.v2`, this consumer
continues on v1 during the grace period; a separate task migrates (scm↔wms precedent).

## Standalone-publish degradation (ADR-022 D8)

Without wms present, these topics never arrive; Shipping stays manual-admin (today's behavior).
No hard dependency.
