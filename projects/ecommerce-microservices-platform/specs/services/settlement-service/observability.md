# settlement-service Observability

Defines business metrics specific to settlement-service.

Platform-wide observability rules are defined in `platform/observability.md`.

---

# Business Metrics

| Metric | Type | Description |
|---|---|---|
| `settlement_snapshot_recorded_total` | Counter | Total `OrderPlaced` snapshots recorded (line cache upserts) |
| `settlement_accrual_total` | Counter | Total `ACCRUAL` rows appended (per `PaymentCompleted`) |
| `settlement_reversal_total` | Counter | Total `REVERSAL` rows appended (per `PaymentRefunded`) |
| `settlement_duplicate_event_total` | Counter | Total deduplicated (already-processed) events by type |
| `settlement_snapshot_missing_total` | Counter | Total `PaymentCompleted` events where the `OrderPlaced` snapshot was absent (F2 — unattributable) |

---

# Change Rule

New settlement-service metrics must be documented here before implementation.
