# payment-service Observability

Defines business metrics specific to payment-service.

Platform-wide observability rules are defined in `platform/observability.md`.

---

# Business Metrics

| Metric | Type | Description |
|---|---|---|
| `payment_created_total` | Counter | Total payments created |
| `payment_completed_total` | Counter | Total payments successfully completed |
| `payment_failed_total` | Counter | Total payment failures by reason. Counted once when a payment is finally decided FAILED (not on retryable faults, not for refund failures). `reason` values: `pg_rejected` (PG definitively rejected the confirm, 4xx) |
| `payment_refunded_total` | Counter | Total refunds processed |
| `payment_amount_sum` | Counter | Cumulative payment amount processed |

---

# Change Rule

New payment-service metrics must be documented here before implementation.
