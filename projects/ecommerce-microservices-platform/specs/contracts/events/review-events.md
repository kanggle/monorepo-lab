# Event Contract: review-service

## Overview
Domain events published by review-service.
Consumers must not depend on fields not defined in this contract.

---

## Event Envelope (common to all events)
```json
{
  "event_id": "string (UUID)",
  "event_type": "string",
  "occurred_at": "string (ISO 8601)",
  "source": "review-service",
  "payload": {}
}
```

---

## ReviewCreated

Published when a new review is created.

**Consumers:** search-service, product-service

**Topic:** `review.review.created`

**Payload**
```json
{
  "reviewId": "string (UUID)",
  "productId": "string (UUID)",
  "userId": "string (UUID)",
  "rating": 5,
  "createdAt": "string (ISO 8601)"
}
```

---

## ReviewUpdated

Published when a review is updated.

**Consumers:** search-service, product-service

**Topic:** `review.review.updated`

**Payload**
```json
{
  "reviewId": "string (UUID)",
  "productId": "string (UUID)",
  "userId": "string (UUID)",
  "rating": 4,
  "updatedAt": "string (ISO 8601)"
}
```

---

## ReviewDeleted

Published when a review is deleted.

**Consumers:** search-service, product-service

**Topic:** `review.review.deleted`

**Payload**
```json
{
  "reviewId": "string (UUID)",
  "productId": "string (UUID)",
  "userId": "string (UUID)",
  "deletedAt": "string (ISO 8601)"
}
```

---

## Consumer Rules

- Consumers must handle duplicate events idempotently.
- Consumers must not fail the entire pipeline on a single malformed event — route to DLQ instead.
- Consumers must not call review-service HTTP API to compensate for missing event data.
- Fields not listed in this contract must not be relied upon.
