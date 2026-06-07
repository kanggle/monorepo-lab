# Event Contract — shipping-service subscriptions (cross-project: ← wms)

Implements **ADR-MONO-022** (ecommerce ↔ wms order-fulfillment integration), D1 return leg.

`shipping-service` subscribes to **wms-platform** events to close the fulfillment loop:
when the warehouse ships (or backorders) the order, the ecommerce Shipping record is
advanced automatically — replacing today's manual-admin `PREPARING → SHIPPED` step.

The authoritative envelope + payload schemas live in the **producing service** (wms):
`projects/wms-platform/specs/contracts/events/outbound-events.md`.

---

## Consumer Group

`shipping-service-wms` — distinct from the `shipping-service` group used for ecommerce-internal
topics, so wms-leg offsets/rebalancing are independent.

## Subscribed Topics

| Topic | wms event | Handler (new) | Effect on ecommerce Shipping/Order |
|---|---|---|---|
| `wms.outbound.shipping.confirmed.v1` | `outbound.shipping.confirmed` | `WmsShippingConfirmedConsumer` | Shipping `PREPARING → SHIPPED` with `trackingNumber = shipmentNo`, `carrier = carrierCode`; existing `ShippingStatusChanged` then drives order-service `Order → SHIPPED`. |
| `wms.outbound.order.cancelled.v1` | `outbound.order.cancelled` | `WmsOutboundCancelledConsumer` | Backorder/cancel path (ADR-022 D4): surface ops alert + (v1) leave Shipping in `PREPARING` flagged; auto-refund/cancel saga = v2. |

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

## Idempotency / Retry / DLT

- Dedupe on envelope `eventId` (`processed_events`, T8) — re-delivery is a no-op.
- Shipping transition `PREPARING → SHIPPED` is itself idempotent (re-applying SHIPPED to an
  already-SHIPPED record is a no-op per the shipping state machine).
- Retry: 3 attempts exponential backoff; then `<topic>.dlq`. Unparseable / missing `orderNo` →
  non-retryable → DLT + alert.

## Schema compatibility

wms v1 envelope + the additive `orderNo` (ADR-022 D5). If wms bumps to `*.v2`, this consumer
continues on v1 during the grace period; a separate task migrates (scm↔wms precedent).

## Standalone-publish degradation (ADR-022 D8)

Without wms present, these topics never arrive; Shipping stays manual-admin (today's behavior).
No hard dependency.
