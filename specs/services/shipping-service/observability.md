# shipping-service Observability

Defines business metrics specific to shipping-service.

Platform-wide observability rules are defined in `specs/platform/observability.md`.

---

# Business Metrics

| Metric | Type | Description |
|---|---|---|
| `shipping_created_total` | Counter | Total shipping records created |
| `shipping_status_transition_total` | Counter | Total shipping status transitions by from/to state |
| `shipping_delivered_total` | Counter | Total shipments successfully delivered |

---

# Change Rule

New shipping-service metrics must be documented here before implementation.
