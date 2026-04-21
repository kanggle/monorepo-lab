# notification-service Observability

Defines business metrics specific to notification-service.

Platform-wide observability rules are defined in `specs/platform/observability.md`.

---

# Business Metrics

| Metric | Type | Description |
|---|---|---|
| `notification_sent_total` | Counter | Total notifications sent by channel (email, sms, push) |
| `notification_failed_total` | Counter | Total notification delivery failures by channel and reason |
| `notification_retry_total` | Counter | Total notification delivery retries |
| `event_consumed_total` | Counter | Total domain events consumed by event type |
| `event_duplicate_total` | Counter | Total duplicate events detected (idempotency) |

---

# Change Rule

New notification-service metrics must be documented here before implementation.
