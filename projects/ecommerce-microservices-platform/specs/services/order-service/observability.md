# order-service Observability

Defines business metrics specific to order-service.

Platform-wide observability rules are defined in `platform/observability.md`.

---

# Business Metrics

| Metric | Type | Description |
|---|---|---|
| `order_placed_total` | Counter | Total orders placed |
| `order_confirmed_total` | Counter | Total orders confirmed (stock reserved) |
| `order_cancelled_total` | Counter | Total orders cancelled by reason (user, user_withdrawn, stock_insufficient, timeout) |
| `order_status_transition_total` | Counter | Total order status transitions by from/to state |
| `order_amount_sum` | Counter | Cumulative order amount (for revenue tracking) |

---

# Change Rule

New order-service metrics must be documented here before implementation.
