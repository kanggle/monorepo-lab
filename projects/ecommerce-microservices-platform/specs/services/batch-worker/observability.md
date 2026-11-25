# batch-worker Observability

Defines business metrics specific to batch-worker.

Platform-wide observability rules are defined in `platform/observability.md`.

---

# Business Metrics

> 🔵 *계획* 표시 행은 아직 코드가 내보내지 않는다(2026-11-25 UTC 전수, TASK-MONO-837). 알림 · 대시보드가 필요해질 때 구현한다.

| Metric | Type | Description |
|---|---|---|
| `batch_job_total` | Counter | Total job executions by job_name and status (completed/failed) — 🔵 *계획 · 미구현 (TASK-MONO-837)* |
| `batch_job_duration_seconds` | Histogram | Job execution duration by job_name — 🔵 *계획 · 미구현 (TASK-MONO-837)* |
| `batch_job_failure_total` | Counter | Total job failures by job_name and error reason — 🔵 *계획 · 미구현 (TASK-MONO-837)* |
| `batch_expired_sessions_cleaned_total` | Counter | Total expired sessions cleaned per execution — 🔵 *계획 · 미구현 (TASK-MONO-837)* |
| `batch_paid_orders_confirmed_total` | Counter | Total stale paid-but-unconfirmed orders forward-confirmed (`PENDING → CONFIRMED`) per execution (TASK-BE-410 reframe — was `batch_stale_orders_cancelled_total`; the job confirms, it does not cancel) |
| `batch_paid_orders_confirm_skipped_total` | Counter | Stale paid-order confirm attempts no-op'd (already CONFIRMED / raced out of PENDING) per execution |
| `batch_index_inconsistencies_detected_total` | Counter | Total Elasticsearch index inconsistencies detected per execution |

---

# Change Rule

New batch-worker metrics must be documented here before implementation.
