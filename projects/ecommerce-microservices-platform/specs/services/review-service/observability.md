# review-service Observability

Defines business metrics specific to review-service.

Platform-wide observability rules are defined in `platform/observability.md`.

---

# Business Metrics

> 🔵 *계획* 표시 행은 아직 코드가 내보내지 않는다(2026-11-25 UTC 전수, TASK-MONO-837). 알림 · 대시보드가 필요해질 때 구현한다.

| Metric | Type | Description |
|---|---|---|
| `review_created_total` | Counter | Total reviews created — 🔵 *계획 · 미구현 (TASK-MONO-837)* |
| `review_updated_total` | Counter | Total reviews updated — 🔵 *계획 · 미구현 (TASK-MONO-837)* |
| `review_deleted_total` | Counter | Total reviews deleted — 🔵 *계획 · 미구현 (TASK-MONO-837)* |
| `review_purchase_verification_total` | Counter | Total purchase verification calls by result (verified, rejected) — 🔵 *계획 · 미구현 (TASK-MONO-837)* |

---

# Change Rule

New review-service metrics must be documented here before implementation.
