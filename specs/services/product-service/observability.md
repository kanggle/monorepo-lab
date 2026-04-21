# product-service Observability

Defines business metrics specific to product-service.

Platform-wide observability rules are defined in `specs/platform/observability.md`.

---

# Business Metrics

| Metric | Type | Description |
|---|---|---|
| `product_created_total` | Counter | Total products created |
| `product_updated_total` | Counter | Total product updates |
| `product_deleted_total` | Counter | Total products deleted |
| `product_stock_adjusted_total` | Counter | Total stock adjustments by type (increase, decrease, reserve) |
| `product_out_of_stock_total` | Counter | Total out-of-stock events |

---

# Change Rule

New product-service metrics must be documented here before implementation.
