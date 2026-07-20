# notification-service Observability

Defines business metrics specific to notification-service.

Platform-wide observability rules are defined in `platform/observability.md`.

---

# Business Metrics

| Metric | Type | Description |
|---|---|---|
| `notification_sent_total` | Counter | Total notifications sent, labelled `channel` (`email`/`sms`/`push`). **Emitted** by `MicrometerNotificationMetrics#recordSent`, called from `NotificationSendService#renderAndSend` on the success branch (TASK-BE-533). |
| `notification_failed_total` | Counter | Total notification delivery failures, labelled `channel` and `reason`. **Emitted** by `MicrometerNotificationMetrics#recordFailed` from the same method's `catch` branch (TASK-BE-533). |
| `notification_retry_total` | Counter | Total notification delivery retries |
| `event_consumed_total` | Counter | Total domain events consumed by event type |
| `event_duplicate_total` | Counter | Total duplicate events detected (idempotency) |

---

# `notification_failed_total` — the `reason` label

`reason` is a **bounded enum** (`NotificationFailureReason`), never a raw exception message or
recipient address: an unbounded label would create one Prometheus series per distinct error string.
Anything unrecognised collapses to `unknown`, capping the series count at `channels x reasons`.

| `reason` | Raised when |
|---|---|
| `mail_auth` | SMTP rejected the credentials |
| `mail_send` | SMTP transport failed (unreachable, rejected, connection reset) |
| `push_delivery` | Web Push failed for **every** one of the user's subscriptions |
| `serialization` | The payload could not be serialised — a defect, not an outage |
| `timeout` | The send did not complete within its deadline |
| `unknown` | Not classified above |

## Counting rule (do not change without moving the alerts)

Both counters are incremented **exactly once per persisted notification row** — the same unit that
receives `markSent()` / `markFailed()` — so `failed / (failed + sent)` is a true rate. Senders must
not increment per delivery attempt; a fan-out sender (Web Push: one attempt per subscription)
reports its aggregate outcome by returning normally or throwing.

These names and labels are queried directly by `infra/prometheus/alert-rules.yml`
(`NotificationDeliveryFailureRateHigh` / `...Critical`) and are asserted by
`infra/prometheus/alert-rules.test.yml`. Treat a rename as a breaking change to those consumers:
metric, rules, rule tests, and this table move in one PR.

---

# Change Rule

New notification-service metrics must be documented here before implementation.
