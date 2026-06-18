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
  "tenant_id": "string (additive — TASK-BE-403, ADR-MONO-030 Step 4 facet c; defaults to 'ecommerce' for pre-migration rows)",
  "payload": {}
}
```

> **Additive change (TASK-BE-403):** `tenant_id` was added to the envelope in TASK-BE-403.
> Existing consumers that do not read `tenant_id` are unaffected. Consumers must tolerate
> a missing/`null` `tenant_id` in any outbox rows written before this migration.

---

## ReviewCreated

Published when a new review is created.

**Consumers:** none in v1 (published for future search-index / product-aggregate use; no `@KafkaListener` for `review.review.created` exists in any service as of v1)

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

**Consumers:** none in v1 (published for future search-index / product-aggregate use; no `@KafkaListener` for `review.review.updated` exists in any service as of v1)

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

**Consumers:** none in v1 (published for future search-index / product-aggregate use; no `@KafkaListener` for `review.review.deleted` exists in any service as of v1)

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
