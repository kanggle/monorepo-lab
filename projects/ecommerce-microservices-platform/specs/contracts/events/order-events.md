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

## OrderSagaRecoveryExhausted

Published by the order-service stuck-detector (TASK-BE-138, ADR-MONO-005 § D3
Category A) when an order has remained in `PENDING + payment_id IS NULL` past
the configured grace period for the maximum number of attempts and has been
transitioned to the terminal `STUCK_RECOVERY_FAILED` status. Co-committed (T3)
with that status transition through the standard transactional outbox.

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
  "failureReason": "order_stuck_payment_pending_attempts_exhausted"
}
```

---

## Consumer Rules

- Consumers must handle duplicate events idempotently.
- Consumers must not fail the entire pipeline on a single malformed event — route to DLQ instead.
- Consumers must not call order-service HTTP API to compensate for missing event data.
- Fields not listed in this contract must not be relied upon.
