# promotion-service Observability

Defines business metrics specific to promotion-service.

Platform-wide observability rules are defined in `specs/platform/observability.md`.

---

# Business Metrics

| Metric | Type | Description |
|---|---|---|
| `promotion_created_total` | Counter | Total promotions created |
| `coupon_issued_total` | Counter | Total coupons issued |
| `coupon_used_total` | Counter | Total coupons successfully applied |
| `coupon_expired_total` | Counter | Total coupons expired |
| `coupon_restored_total` | Counter | Total coupons restored due to order cancellation |

---

# Change Rule

New promotion-service metrics must be documented here before implementation.
