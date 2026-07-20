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
  shippingId + ":" + newStatus)`. The shipping status machine is strictly linear
  and forward-only (`PREPARING → SHIPPED → IN_TRANSIT → DELIVERED`, no
  self-transition, no regression), so a given shipment enters each status **at
  most once** and each `newStatus` appears in **at most one legitimate event**.
  Two publishes carrying the same `(shippingId, newStatus)` therefore only ever
  arise from a concurrent double-transition or an at-least-once retry — exactly
  the cases dedup must collapse. Since `event_id` is also the `shipping_outbox`
  row PK, the second concurrent publish collides on that PK and its whole
  transaction rolls back (surfacing `409 DATA_INTEGRITY_VIOLATION` to the losing
  caller — see `shipping-api.md`), so the duplicate never even reaches the topic.
- The two wms-bound fulfillment legs (`FulfillmentRequested`,
  `ManualShipConfirmRequested`) are unaffected by this convention; their
  idempotency is defended downstream by the wms consumer (see
  `fulfillment-events.md`).

---

## ShippingStatusChanged

Published when a shipping record's status changes.

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
