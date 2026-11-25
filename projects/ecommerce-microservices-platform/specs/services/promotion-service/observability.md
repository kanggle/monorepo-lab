# promotion-service Observability

Defines business metrics specific to promotion-service.

Platform-wide observability rules are defined in `platform/observability.md`.

---

# Business Metrics

> 🔵 *계획* 표시 행은 아직 코드가 내보내지 않는다(2026-11-25 UTC 전수, TASK-MONO-837). 알림 · 대시보드가 필요해질 때 구현한다.

| Metric | Type | Description |
|---|---|---|
| `promotion_created_total` | Counter | Total promotions created — 🔵 *계획 · 미구현 (TASK-MONO-837)* |
| `coupon_issued_total` | Counter | Total coupons issued — 🔵 *계획 · 미구현 (TASK-MONO-837)* |
| `coupon_used_total` | Counter | Total coupons successfully applied — 🔵 *계획 · 미구현 (TASK-MONO-837)* |
| `coupon_expired_total` | Counter | Total coupons expired — 🔵 *계획 · 미구현 (TASK-MONO-837)* |
| `coupon_restored_total` | Counter | Total coupons restored due to order cancellation — 🔵 *계획 · 미구현 (TASK-MONO-837)* |

---

# Change Rule

New promotion-service metrics must be documented here before implementation.
