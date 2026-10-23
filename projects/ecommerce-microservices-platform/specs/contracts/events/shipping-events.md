# Event Contract: shipping-service

## Overview
Domain events published by shipping-service.
Consumers must not depend on fields not defined in this contract.

---

## Event Envelope (common to all events)
```json
{
  "event_id": "string (UUID)",
  "event_type": "string",
  "occurred_at": "string (ISO 8601)",
  "source": "shipping-service",
  "payload": {}
}
```

### `event_id` semantics (idempotency key)

`event_id` is the consumer-facing idempotency key: both consumers dedupe on it
(order-service `EventDeduplicationChecker`, notification-service
`existsByEventId(event_id, tenant_id)`). For `event_id` to actually *be* a
dedup key it must be **deterministic for one logical transition** — a fresh
random UUID per publish makes every consumer's dedup a no-op, so two concurrent
publishes of the same transition slip through as two distinct events
(TASK-BE-547).

- **`ShippingStatusChanged`** derives `event_id` deterministically from
  `(shippingId, newStatus)` — `UUID.nameUUIDFromBytes("ShippingStatusChanged:" +
  shippingId + ":" + newStatus)`. The shipping status machine is forward-only
  (`PREPARING → SHIPPED → IN_TRANSIT → DELIVERED`, plus the terminal side branch
  `PREPARING → CANCELLED`; no self-transition, no regression), so a given shipment
  enters each status **at most once** and each `newStatus` appears in **at most one
  legitimate event**.
  Two publishes carrying the same `(shippingId, newStatus)` therefore only ever
  arise from a concurrent double-transition or an at-least-once retry — exactly
  the cases dedup must collapse. Since `event_id` is also the `shipping_outbox`
  row PK, the second concurrent publish collides on that PK and its whole
  transaction rolls back (surfacing `409 DATA_INTEGRITY_VIOLATION` to the losing
  caller — see `shipping-api.md`), so the duplicate never even reaches the topic.
- The wms-bound fulfillment legs (`FulfillmentRequested`,
  `ManualShipConfirmRequested`, `FulfillmentCancelRequested`) are not
  `ShippingStatusChanged` events; their idempotency is defended downstream by the
  wms consumer (see `fulfillment-events.md`).

## Shipping status machine

`PREPARING → SHIPPED → IN_TRANSIT → DELIVERED`, plus one terminal side branch
`PREPARING → CANCELLED` (ADR-MONO-022 D4 v2(d), TASK-BE-649).

- `CANCELLED` is reached **only** when shipping-service consumes `OrderCancelled`
  while the row is still `PREPARING` (`order-events.md` § OrderCancelled). It is not
  an operator target: `PUT /api/shippings/{id}/status` with `CANCELLED` is rejected
  (`shipping-api.md`).
- `CANCELLED` is terminal. No carrier refresh, carrier webhook or wms ship-confirm
  moves it again. A wms ship-confirm that arrives for a `CANCELLED` row (wms refused
  the cancel after pick, or raced it) is an `ALERT` log and a no-op — the goods
  shipped for a refunded order, which is the return flow (Y-A).

---

## ShippingStatusChanged

Published when a shipping record's status changes — **except** the
`PREPARING → CANCELLED` transition, which publishes nothing on this topic
(TASK-BE-649). Reason: no consumer needs it, and one would turn it into an unapproved
customer message. order-service ignores every `newStatus` other than `SHIPPED` /
`DELIVERED`, so it would be a no-op there. notification-service sends a
`SHIPPING_STATUS_CHANGED` notification for **every** event and renders the raw
`newStatus` string, so the customer would receive a shipping notice reading `CANCELLED`
for an order whose cancel they already know about. That copy has no owner approval.
The cancel is still visible on the shipping read API (`status` + `statusHistory`).
Emitting it later is an additive change that needs that copy decided first.

**Consumers:** order-service, notification-service

order-service's `ShippingStatusChangedEventConsumer` (group `order-service`) reads
this event and, on `newStatus = SHIPPED`, flips the Order `CONFIRMED → SHIPPED`,
and on `newStatus = DELIVERED`, flips the Order `SHIPPED → DELIVERED` (return-leg
tail of ADR-MONO-022 §D7). This event is the **sole** path by which an Order reaches
`SHIPPED`/`DELIVERED` — the admin status endpoint
(`POST /api/admin/orders/{orderId}/status`) does **not** offer those
operator-initiated transitions (it rejects them `400 INVALID_ORDER_REQUEST`).
Other transitions (e.g. `IN_TRANSIT`) are ignored by order-service. Consumption is
idempotent (dedupe on `event_id` + idempotent `Order.ship` / `Order.deliver`).

**Topic:** `shipping.shipping.status-changed`

**Payload**
```json
{
  "shippingId": "string (UUID)",
  "orderId": "string (UUID)",
  "userId": "string (UUID)",
  "previousStatus": "PREPARING",
  "newStatus": "SHIPPED",
  "trackingNumber": "string | null",
  "carrier": "string | null",
  "changedAt": "string (ISO 8601)"
}
```

---

## Consumer Rules

- Consumers must handle duplicate events idempotently.
- Consumers must not fail the entire pipeline on a single malformed event — route to DLQ instead.
- Consumers must not call shipping-service HTTP API to compensate for missing event data.
- Fields not listed in this contract must not be relied upon.
