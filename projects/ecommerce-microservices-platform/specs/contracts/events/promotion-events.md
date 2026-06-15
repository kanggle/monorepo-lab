# Event Contract: promotion-service

## Overview
Domain events published by promotion-service.
Consumers must not depend on fields not defined in this contract.

---

## Event Envelope (common to all events)
```json
{
  "event_id": "string (UUID)",
  "event_type": "string",
  "occurred_at": "string (ISO 8601)",
  "source": "promotion-service",
  "tenant_id": "string (owning tenant; default 'ecommerce')",
  "payload": {}
}
```

> **`tenant_id` (ADR-MONO-030 Step 4 / TASK-BE-368, M5).** The outer-axis tenant
> that owns the promotion/coupon the event concerns. Carried on the **outbox**
> envelope (not the payload) so consumers can route/scope per tenant across the
> async boundary without parsing the payload. For request-originated events
> (`CouponUsed`) it is the request tenant; for the batch-origin `CouponExpired` it
> is the **expiring coupon's row tenant**. A standalone deployment or a
> pre-multi-tenant promotion resolves to the default tenant `'ecommerce'`
> (net-zero, D8). Always present (never blank); tenant-unaware consumers may ignore
> it (additive).

---

## CouponUsed

Published when a coupon is successfully applied to an order.

**Consumers:** no consumer in v1 (no `@KafkaListener` for `promotion.coupon.used` exists in order-service or any other service; notification-service is a planned future consumer)

**Topic:** `promotion.coupon.used`

**Payload**
```json
{
  "couponId": "string (UUID)",
  "promotionId": "string (UUID)",
  "userId": "string (UUID)",
  "orderId": "string (UUID)",
  "discountAmount": 5000,
  "usedAt": "string (ISO 8601)"
}
```

---

## CouponExpired

Published when a coupon expires (batch processing).

**Consumers:** notification-service (future)

**Topic:** `promotion.coupon.expired`

**Payload**
```json
{
  "couponId": "string (UUID)",
  "promotionId": "string (UUID)",
  "userId": "string (UUID)",
  "expiredAt": "string (ISO 8601)"
}
```

---

## Consumer Rules

- Consumers must handle duplicate events idempotently.
- Consumers must not fail the entire pipeline on a single malformed event — route to DLQ instead.
- Consumers must not call promotion-service HTTP API to compensate for missing event data.
- Fields not listed in this contract must not be relied upon.
