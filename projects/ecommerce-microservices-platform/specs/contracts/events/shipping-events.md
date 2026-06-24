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
