# demand-planning-service — Overview

## One-liner

scm's replenishment **decisioning** service: consumes wms low-stock alerts,
evaluates the scm reorder policy, and raises operator-reviewable **reorder
suggestions** that materialize into procurement **DRAFT** purchase orders.
Activated by ADR-MONO-027.

## Where it sits

```
wms inventory-service ──inventory.low-stock-detected──▶ demand-planning-service
                          (wms.inventory.alert.v1)         │
                                                           │ evaluate reorder policy
                                                           ▼
                                                  reorder_suggestion (SUGGESTED)
                                                           │ operator approve
                                                           ▼
                                          procurement-service: DRAFT PO  ──operator──▶ SUBMITTED (supplier)
```

The 4th scm domain service (after gateway, procurement, inventory-visibility).
The first to use all three of scm's `batch-heavy`-relevant facets together
(event-consumer + batch-job + rest-api).

## Service Type

`event-consumer` + `batch-job` + `rest-api` (see [`architecture.md`](./architecture.md)
§ Service Type Composition).

## Key invariants

- **Suggestion-only** — never auto-submits a PO to a supplier (operator gate, D2).
- **scm owns the reorder decision** — the wms alert threshold is informational
  (D4); the scm `reorder_point` decides.
- **Idempotent** — eventId T8 dedup + open-suggestion guard for business duplicates (D6).
- **Decisioning only** — owns no inventory, no PO lifecycle, no fulfillment (D7).
- **No hard dependency on wms** — standalone-published, the suggestion list is
  empty; the nightly batch over inventory-visibility still runs (D8).

## Not in scope (v1)

- Demand forecasting (moving-average/seasonality) — v2 (`reorder_policy` is the seam).
- `supplier-service` master/contract/catalog — v2 (`sku_supplier_map` is the minimal stand-in, D3).
- Cross-warehouse rebalancing — a wms transfer concern, not a supplier reorder.
- Fulfilling the customer's out-of-stock order — that is ADR-022's cancel+refund path.

## References

- [ADR-MONO-027](../../../../../docs/adr/ADR-MONO-027-wms-scm-replenishment-loop.md)
- [`architecture.md`](./architecture.md) / [`data-model.md`](./data-model.md) / [`reorder-policy.md`](./reorder-policy.md)
- [`replenishment-subscriptions.md`](../../contracts/events/replenishment-subscriptions.md) / [`demand-planning-api.md`](../../contracts/http/demand-planning-api.md)
