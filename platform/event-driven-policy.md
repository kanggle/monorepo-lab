# Event-Driven Policy

Defines rules for asynchronous event communication between services.

---

# Principles

- Events represent facts that have already happened (past tense): `OrderPlaced`, `PaymentCompleted`.
- Events are immutable once published.
- Producers own their events and are responsible for the event contract.
- Consumers must not assume ordering unless explicitly guaranteed by the topic configuration.

---

# Broker

- Message broker: Apache Kafka
- Topic naming: `{service}.{entity}.{event}` in `kebab-case`
  - Examples: `order.order.placed`, `payment.payment.completed`, `product.product.stock-changed`

---

# Event Envelope Format

All events must follow this JSON envelope:

```json
{
  "event_id": "string (UUID)",
  "event_type": "string",
  "occurred_at": "string (ISO 8601 UTC)",
  "source": "string",
  "payload": { }
}
```

- `event_id`: unique identifier for deduplication.
- `event_type`: matches the topic event type (e.g. `OrderPlaced`).
- `occurred_at`: time the event occurred (not published).
- `source`: name of the producing service (e.g. `auth-service`, `order-service`).
- `payload`: event-specific data.

---

# Contract Rule

- Every event must have a published contract under `specs/contracts/events/`.
- Consumers must implement their logic based on the published contract, not the producer's internal model.
- Breaking changes to event payloads require a new contract version.

---

# Consumer Rules

- Consumers must be idempotent: processing the same event twice must produce the same result.
- Use `event_id` for deduplication where necessary.
- Dead-letter queues (DLQ) must be configured for all consumer groups.
- Consumer failures must not cause data loss; use DLQ + alerting.

## Retry Policy

| Parameter | Default | Notes |
|---|---|---|
| Max retries | 3 | Before sending to DLQ |
| Backoff strategy | Exponential | Base interval × 2^attempt |
| Base interval | 1 second | First retry after 1s, second after 2s, third after 4s |
| Max interval | 30 seconds | Cap for exponential backoff |

## DLQ Policy

- Topic naming: `{original-topic}.dlq`
- DLQ messages must retain original headers (`event_id`, `event_type`, `occurred_at`, `source`).
- Add error metadata: `error_message`, `retry_count`, `failed_at`.
- DLQ messages must trigger alerts (see `observability.md`).
- Manual replay: DLQ messages can be replayed to the original topic after root cause is resolved.

## Error Classification

| Error Type | Action |
|---|---|
| Transient (network timeout, DB connection) | Retry with backoff |
| Deserialization failure | Send to DLQ immediately (no retry) |
| Business rule violation | Send to DLQ immediately (no retry) |
| Unknown / unhandled | Retry, then DLQ after max retries |

---

# Producer Rules

- Producers must publish events after the transaction commits (transactional outbox pattern or equivalent).
- Events must not be published from within a database transaction boundary that may roll back.

---

# Services Using Events

| Producer | Event | Consumer(s) |
|---|---|---|
| auth-service | UserSignedUp | user-service, notification-service |
| auth-service | UserLoggedIn | audit-service (future), analytics (future) |
| auth-service | UserLoggedOut | audit-service (future), analytics (future) |
| auth-service | TokenRefreshed | audit-service (future) |
| auth-service | LoginFailed | audit-service (future), security-monitoring (future) |
| auth-service | SessionLimitExceeded | audit-service (future) |
| order-service | OrderPlaced | payment-service, notification-service |
| order-service | OrderCancelled | payment-service, promotion-service |
| order-service | OrderConfirmed | shipping-service |
| payment-service | PaymentCompleted | order-service, notification-service |
| payment-service | PaymentFailed | order-service |
| payment-service | PaymentRefunded | order-service |
| promotion-service | CouponUsed | order-service, notification-service (future) |
| promotion-service | CouponExpired | notification-service (future) |
| review-service | ReviewCreated | search-service, product-service |
| review-service | ReviewUpdated | search-service, product-service |
| review-service | ReviewDeleted | search-service, product-service |
| shipping-service | ShippingStatusChanged | order-service, notification-service |
| user-service | UserProfileUpdated | admin-dashboard (future), notification-service |
| user-service | UserWithdrawn | order-service, auth-service |
| product-service | ProductCreated | search-service |
| product-service | ProductUpdated | search-service |
| product-service | ProductDeleted | search-service |
| product-service | StockChanged | search-service, order-service |

> **Future/external services**: `audit-service`, `analytics`, `security-monitoring` appear as consumers in event contracts but do not have service specs yet. They are planned services. Implementations consuming events for these services should not be built until their service specs are created under `specs/services/`. Event contracts mark these consumers as `(future)`.

---

# Change Rule

New event types or changes to existing events must update the contract in `specs/contracts/events/` before implementation.
