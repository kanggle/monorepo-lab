# batch-worker Observability

Defines business metrics specific to batch-worker.

Platform-wide observability rules are defined in `platform/observability.md`.

---

# Business Metrics

| Metric | Type | Description |
|---|---|---|
| `batch_job_total` | Counter | Total job executions by job_name and status (completed/failed) |
| `batch_job_duration_seconds` | Histogram | Job execution duration by job_name |
| `batch_job_failure_total` | Counter | Total job failures by job_name and error reason |
| `batch_expired_sessions_cleaned_total` | Counter | Total expired sessions cleaned per execution |
| `batch_paid_orders_confirmed_total` | Counter | Total stale paid-but-unconfirmed orders forward-confirmed (`PENDING → CONFIRMED`) per execution (TASK-BE-410 reframe — was `batch_stale_orders_cancelled_total`; the job confirms, it does not cancel) |
| `batch_paid_orders_confirm_skipped_total` | Counter | Stale paid-order confirm attempts no-op'd (already CONFIRMED / raced out of PENDING) per execution |
| `batch_sales_aggregation_total` | Counter | Total daily sales aggregation executions |
| `batch_index_inconsistencies_detected_total` | Counter | Total Elasticsearch index inconsistencies detected per execution |

---

# Change Rule

New batch-worker metrics must be documented here before implementation.
