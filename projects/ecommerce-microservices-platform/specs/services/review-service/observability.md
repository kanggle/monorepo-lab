# review-service Observability

Defines business metrics specific to review-service.

Platform-wide observability rules are defined in `platform/observability.md`.

---

# Business Metrics

| Metric | Type | Description |
|---|---|---|
| `review_created_total` | Counter | Total reviews created |
| `review_updated_total` | Counter | Total reviews updated |
| `review_deleted_total` | Counter | Total reviews deleted |
| `review_purchase_verification_total` | Counter | Total purchase verification calls by result (verified, rejected) |

---

# Change Rule

New review-service metrics must be documented here before implementation.
