# Event Contract: payment-service

## Overview
Domain events published by payment-service.
Consumers must not depend on fields not defined in this contract.

---

## Event Envelope (common to all events)
```json
{
  "event_id": "string (UUID)",
  "event_type": "string",
  "occurred_at": "string (ISO 8601)",
  "source": "payment-service",
  "payload": {}
}
```

---

## PaymentCompleted

Published when a payment is successfully processed.

**Consumers:** order-service

**Payload**
```json
{
  "paymentId": "string (UUID)",
  "orderId": "string (UUID)",
  "userId": "string (UUID)",
  "amount": 30000,
  "paidAt": "string (ISO 8601)"
}
```

---

## PaymentFailed

Published when a payment processing fails.

**Consumers:** order-service

**Payload**
```json
{
  "paymentId": "string (UUID)",
  "orderId": "string (UUID)",
  "userId": "string (UUID)",
  "reason": "string",
  "failedAt": "string (ISO 8601)"
}
```

---

## PaymentRefunded

Published when a payment refund is processed.

**Consumers:** order-service

**Payload**
```json
{
  "paymentId": "string (UUID)",
  "orderId": "string (UUID)",
  "userId": "string (UUID)",
  "amount": 30000,
  "refundedAt": "string (ISO 8601)"
}
```

---

## Consumer Rules

- Consumers must handle duplicate events idempotently.
- Consumers must not fail the entire pipeline on a single malformed event — route to DLQ instead.
- Consumers must not call payment-service HTTP API to compensate for missing event data.
- Fields not listed in this contract must not be relied upon.
