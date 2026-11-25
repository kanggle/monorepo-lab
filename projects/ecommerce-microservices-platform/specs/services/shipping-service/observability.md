# shipping-service Observability

Defines business metrics specific to shipping-service.

Platform-wide observability rules are defined in `platform/observability.md`.

---

# Business Metrics

> 🔵 *계획* 표시 행은 아직 코드가 내보내지 않는다(2026-11-25 UTC 전수, TASK-MONO-837). 알림 · 대시보드가 필요해질 때 구현한다.

| Metric | Type | Description |
|---|---|---|
| `shipping_created_total` | Counter | Total shipping records created — 🔵 *계획 · 미구현 (TASK-MONO-837)* |
| `shipping_status_transition_total` | Counter | Total shipping status transitions by from/to state — 🔵 *계획 · 미구현 (TASK-MONO-837)* |
| `shipping_delivered_total` | Counter | Total shipments successfully delivered — 🔵 *계획 · 미구현 (TASK-MONO-837)* |

---

# Change Rule

New shipping-service metrics must be documented here before implementation.
