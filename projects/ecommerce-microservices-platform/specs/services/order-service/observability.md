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
| `order_backordered_total` | Counter | Orders held for backorder (paid but stock short); incremented once per order moved to backorder by `OrderBackorderService`. No tags |
| `order_stuck_detector_recovery_fired_total` | Counter | Saga stuck-detector recovery attempts fired for a stuck order. Tag `from_state` (order status the order was stuck in; currently always `PENDING`) |
| `order_stuck_detector_exhausted_total` | Counter | Stuck orders whose recovery attempts were exhausted and which were auto-cancelled (payment timeout) with a saga-recovery-exhausted alert event published. Tag `from_state` (currently always `PENDING`). Any increase needs operator attention |

---

# Change Rule

New order-service metrics must be documented here before implementation.
