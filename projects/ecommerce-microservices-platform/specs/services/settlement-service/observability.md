# settlement-service Observability

Defines business metrics specific to settlement-service.

Platform-wide observability rules are defined in `platform/observability.md`.

---

# Business Metrics

> 🔵 *계획* 표시 행은 아직 코드가 내보내지 않는다(2026-11-25 UTC 전수, TASK-MONO-837). 알림 · 대시보드가 필요해질 때 구현한다.

| Metric | Type | Description |
|---|---|---|
| `settlement_snapshot_recorded_total` | Counter | Total `OrderPlaced` snapshots recorded (line cache upserts) — 🔵 *계획 · 미구현 (TASK-MONO-837)* |
| `settlement_accrual_total` | Counter | Total `ACCRUAL` rows appended (per `PaymentCompleted`) — 🔵 *계획 · 미구현 (TASK-MONO-837)* |
| `settlement_reversal_total` | Counter | Total `REVERSAL` rows appended (per `PaymentRefunded`) — 🔵 *계획 · 미구현 (TASK-MONO-837)* |
| `settlement_duplicate_event_total` | Counter | Total deduplicated (already-processed) events by type — 🔵 *계획 · 미구현 (TASK-MONO-837)* |
| `settlement_snapshot_missing_total` | Counter | Total `PaymentCompleted` events where the `OrderPlaced` snapshot was absent (F2 — unattributable) — 🔵 *계획 · 미구현 (TASK-MONO-837)* |
| `settlement_period_closed_total` | Counter | Total settlement periods closed (one per successful close) |
| `settlement_payout_total` | Counter | Total seller payouts by terminal status — tag `status` ∈ `{PAID, FAILED}` (incremented when the simulated adapter resolves a payout) |

---

# Change Rule

New settlement-service metrics must be documented here before implementation.
