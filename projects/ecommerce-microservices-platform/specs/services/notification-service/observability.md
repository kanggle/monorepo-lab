# notification-service Observability

Defines business metrics specific to notification-service.

Platform-wide observability rules are defined in `platform/observability.md`.

---

# Business Metrics

| Metric | Type | Description |
|---|---|---|
| `notification_sent_total` | Counter | **Planned, not yet emitted (TASK-BE-533).** Total notifications sent by channel (email, sms, push). No Micrometer registration exists in `notification-service/src/main` as of 2026-07-20 (TASK-BE-532 recount) — the alert rules that referenced this metric were removed as no-data pending TASK-BE-533. |
| `notification_failed_total` | Counter | **Planned, not yet emitted (TASK-BE-533).** Total notification delivery failures by channel and reason. Same status as `notification_sent_total` above. |
| `notification_retry_total` | Counter | Total notification delivery retries |
| `event_consumed_total` | Counter | Total domain events consumed by event type |
| `event_duplicate_total` | Counter | Total duplicate events detected (idempotency) |

---

# Change Rule

New notification-service metrics must be documented here before implementation.
