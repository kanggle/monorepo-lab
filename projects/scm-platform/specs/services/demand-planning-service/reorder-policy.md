# demand-planning-service — Reorder Policy

The reorder evaluation rule (ADR-MONO-027 D4). **scm-owned**, deliberately
distinct from the wms alert threshold.

## Two thresholds, two owners

| Threshold | Owner | Meaning |
|---|---|---|
| wms alert threshold | wms `inventory-service` (warehouse ops) | "tell someone stock is low" — already exists, unchanged. |
| scm reorder point | this service (procurement planning) | "should we reorder, and how much" — `reorder_policy.reorder_point` / `reorder_qty`. |

The wms alert is a **trigger to evaluate**, not the reorder decision itself. The
alert's own `threshold` field is read as *informational provenance only* — the
suggestion is decided against the scm `reorder_point`.

## v1 evaluation rule

On a low-stock alert (or a nightly batch row) for `(sku_code, warehouse)`:

```
policy   = reorder_policy[sku_code]            (fallback: none → use default below)
available = alert.availableQty                  (live)  | IVS snapshot qty (batch)

if available <= policy.reorder_point:
    qty      = policy.reorder_qty               (fallback: sku_supplier_map.default_order_qty)
    supplier = sku_supplier_map[sku_code].supplier_id   (unmapped → fail-closed DLT/alert)
    raise reorder_suggestion(SUGGESTED, sku_code, warehouse, supplier, qty)
        subject to the open-suggestion guard (D6)
else:
    no-op
```

- **Fallback when no `reorder_policy` row**: use `sku_supplier_map.default_order_qty`
  as `qty` and treat the alert's `threshold` as the comparison point (degraded —
  seeding a policy row is the intended path). If the SKU is also unmapped, the
  event is fail-closed to DLT + ops alert.
- **Re-check freshness (live path)**: an alert can arrive after stock was already
  replenished by another path. Before raising, the use case re-reads the current
  policy state for the SKU and skips if no longer below the reorder point (avoid
  stale-alert suggestions). The read source for the re-check is documented in the
  use case (the alert payload's `availableQty` is the v1 source; IVS is the batch
  source).
- **Open-suggestion guard** is the single arbiter for both the live and batch
  paths — a SKU low in both an alert and the same-window batch raises at most one
  open suggestion.

## Warehouse dimension

The suggestion key is `(sku_code, warehouse_id)`. A SKU low in warehouse A but
fine in warehouse B suggests only for A. Cross-warehouse rebalancing is **not** a
reorder concern and is out of scope (it would be a wms transfer, not a supplier PO).

## v2 extension point (deferred — not implemented)

`reorder_policy` is the seam for demand forecasting:

- moving-average / seasonality demand estimate → dynamic `reorder_point`,
- economic order quantity → dynamic `reorder_qty`,
- `safety_stock` (carried in v1, consumed in v2),
- `lead_time_days` (from `sku_supplier_map`) → reorder-point lead-time coverage.

v1 keeps a fixed-point/fixed-quantity rule. No forecasting model ships in v1
(ADR-027 D4).

## References

- [ADR-MONO-027](../../../../../docs/adr/ADR-MONO-027-wms-scm-replenishment-loop.md) D4
- [`data-model.md`](./data-model.md) (`reorder_policy`, `sku_supplier_map`, open-guard)
- [`replenishment-subscriptions.md`](../../contracts/events/replenishment-subscriptions.md) (the alert this evaluates)
