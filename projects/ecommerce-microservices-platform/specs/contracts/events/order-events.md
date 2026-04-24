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
  "payload": {}
}
```

---

## OrderPlaced

Published when a new order is successfully created.

**Consumers:** payment-service, promotion-service

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
      "productName": "string",
      "optionName": "string",
      "quantity": 2,
      "unitPrice": 15000
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

---

## OrderConfirmed

Published when an order is confirmed (payment completed and order status transitions to CONFIRMED).

**Consumers:** shipping-service

**Topic:** `order.order.confirmed`

**Payload**
```json
{
  "orderId": "string (UUID)",
  "userId": "string (UUID)",
  "confirmedAt": "string (ISO 8601)"
}
```

---

## OrderCancelled

Published when an order is cancelled.

**Consumers:** payment-service, promotion-service

**Payload**
```json
{
  "orderId": "string (UUID)",
  "userId": "string (UUID)",
  "cancelledAt": "string (ISO 8601)"
}
```

---

## Consumer Rules

- Consumers must handle duplicate events idempotently.
- Consumers must not fail the entire pipeline on a single malformed event — route to DLQ instead.
- Consumers must not call order-service HTTP API to compensate for missing event data.
- Fields not listed in this contract must not be relied upon.
