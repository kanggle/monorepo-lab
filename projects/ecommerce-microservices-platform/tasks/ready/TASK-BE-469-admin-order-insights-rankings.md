# TASK-BE-469 — Admin order-insights ranking endpoint (top products / sellers by order-count & revenue)

**Status:** ready

**Type:** TASK-BE
**Analysis model:** Opus 4.8 / **Recommended impl model:** Sonnet 4.6 (single additive read endpoint; the only non-mechanical part is the two group-by aggregation queries + top-N sort)

> Cross-project feature — the consuming half is **TASK-PC-FE-170** (platform-console overview charts + 도메인 상태 removal + contract). Both land in **one atomic PR** (branch `task/ecom-overview-charts`). This task is the producer half.

---

## Goal

The platform-console E-Commerce **운영 개요** (`/ecommerce`) shows per-area counts + period metrics (TASK-BE-468). The operator additionally asked for **ranking visualizations**: which products and sellers drive the most orders and the most revenue. There is no aggregation endpoint today (verified: no `GROUP BY` / `SUM` query exists in order-service — TASK-BE-468 only added flat period counts).

This task adds **one dedicated read endpoint** on `order-service` that returns four top-N rankings computed from the order line-items, in a single round-trip. All four rankings derive from the same source (`order_items`), so they belong in one endpoint on the order line owner — `product_name` is denormalized onto the line (no product-service join) and `unit_price`/`quantity` (revenue) live only on the order line.

## Scope

Add **`GET /api/admin/orders/insights`** on the existing `AdminOrderController` (same `@RequestMapping("/api/admin/orders")` base → inherits the same gateway admission + `WHERE tenant_id` chokepoint; no new RBAC / gateway route).

**Response shape** (tenant-scoped):

```json
{
  "topProductsByOrderCount": [ { "id": "prod-1", "label": "베이직 티셔츠", "value": 42 } ],
  "topProductsByRevenue":    [ { "id": "prod-1", "label": "베이직 티셔츠", "value": 1284000 } ],
  "topSellersByOrderCount":  [ { "id": "seller-1", "label": "seller-1", "value": 30 } ],
  "topSellersByRevenue":     [ { "id": "seller-1", "label": "seller-1", "value": 980000 } ]
}
```

- Each array holds **≤ 5** entries (`INSIGHTS_TOP_N = 5`), sorted by `value` **DESC**, ties broken by `id` ASC (deterministic).
- `value` is a non-negative `long`:
  - **order-count** = `COUNT(DISTINCT order)` — number of distinct orders that included that product / seller.
  - **revenue** = `SUM(unit_price * quantity)` over that product's / seller's lines.
- **CANCELLED orders are excluded** from both metrics (a cancelled order is not realised business).
- Product `label` = the line's denormalized `product_name` (`MAX(product_name)` within the group — one product id may carry historically-renamed snapshots; any is acceptable).
- Seller `label` = the raw `seller_id` (order-service has no seller display name; the console overlays the product-service seller displayName, falling back to this id).

**The additive slice** (mirror the TASK-BE-468 summary wiring; do NOT touch list/detail/mutation/summary endpoints):

1. **`OrderJpaRepository`** — two aggregation `@Query` methods over `OrderItemJpaEntity` grouped by `productId` / `sellerId`, filtered `WHERE i.tenantId = :tenantId AND i.order.status <> :excluded`, returning `List<Object[]>` = `[id, (productName,) count(distinct order), sum(unitPrice*quantity)]`.
2. **Domain repository port `OrderRepository`** — add `List<ProductOrderRankingRow> aggregateProductRanking()` + `List<SellerOrderRankingRow> aggregateSellerRanking()` (tenant-scoped via `TenantContext.currentTenant()` in the impl, `OrderStatus.CANCELLED` excluded).
3. **`OrderRepositoryImpl`** — implement, mapping `Object[]` → the row records.
4. **`OrderQueryService`** — `@Transactional(readOnly = true) OrderInsights getInsights()` that fetches both aggregations and derives the four top-N rankings (sort DESC by the metric, take 5).
5. **`AdminOrderController`** — `@GetMapping("/insights")` returning the `OrderInsights` record directly (same posture as `/summary` returning `PeriodSummary`).

**DTOs**: the two aggregation rows are **repository projections** (port return types) so they live in the domain layer — `domain/repository/ProductOrderRankingRow(String productId, String productName, long orderCount, long revenue)` and `domain/repository/SellerOrderRankingRow(String sellerId, long orderCount, long revenue)` (alongside `OrderRepository`, keeping the port free of an application dependency). The endpoint output DTO stays in application: `application/dto/OrderInsights(List<RankedEntry> topProductsByOrderCount, …ByRevenue, …SellersByOrderCount, …ByRevenue)` with nested `RankedEntry(String id, String label, long value)`.

**Out of scope:** any time-series / grouped-by-day payload; a configurable top-N query param (fixed 5); seller display-name resolution (console-side); date-range filtering of the rankings (all-time, ex-CANCELLED); any change to the `/summary` or list endpoints.

## Acceptance Criteria

- **AC-1** — `GET /api/admin/orders/insights` returns `200` with the four ranking arrays for an `ADMIN`-role token on the tenant; each array ≤ 5, sorted by `value` DESC.
- **AC-2** — order-count = distinct-order count and revenue = Σ(unit_price×quantity) per group, **excluding CANCELLED** orders; a product/seller appearing only in cancelled orders is absent from every ranking.
- **AC-3** — Tenant isolation: lines under tenant B never appear in tenant A's rankings (same `WHERE tenant_id` guarantee as the sibling reads). A test pins this.
- **AC-4** — The existing list / detail / mutation / summary endpoints are byte-unchanged; `./gradlew :order-service:compileJava :order-service:test` passes.
- **AC-5** — A focused unit test on `OrderQueryService.getInsights()` (mocked `OrderRepository`) covers: empty (four empty arrays), top-N truncation (> 5 groups → exactly 5, correct order), and both orderings from one dataset (a product ranked #1 by revenue but lower by order-count). Testcontainers IT is authoritative in CI Linux (local Windows npipe is flaky — do not gate on local IT).

## Related Specs

- `projects/platform-console/specs/contracts/console-integration-contract.md` § 2.4.10 (#21 order insights) — the consumer contract updated in TASK-PC-FE-170.
- ADR-MONO-030 Step 2/3 (tenant chokepoint + order-line seller attribution — `OrderItem.sellerId`), TASK-BE-468 (sibling period-summary reads on the same controller).

## Related Contracts

- New read, added to console-integration-contract § 2.4.10 as endpoint #21 (`GET /admin/orders/insights`) — see TASK-PC-FE-170.

## Edge Cases

- **Product renamed over time** — one `product_id` may carry multiple `product_name` snapshots across lines; `MAX(product_name)` picks one deterministically (label is display-only; grouping is by id).
- **Default seller** — lines placed without a seller carry `OrderItem.DEFAULT_SELLER_ID` (`"default"`); it aggregates as a normal seller group (label `"default"`).
- **Fewer than 5 groups** — the ranking returns however many exist (0–5); never padded, never null.
- **All-cancelled tenant** — every ranking is an empty array (not an error).
- **Revenue overflow** — `unit_price` (long) × `quantity` (int) summed as `long`; demo-scale data is far from `long` overflow.

## Failure Scenarios

- **Missing `TenantContext`** — same as sibling reads (gateway `TenantClaimValidator` rejects a blank `tenant_id` before the controller; the aggregation never runs tenant-less).
- **DB unavailable** — the aggregation query throws; `GlobalExceptionHandler` maps it to the standard `503`/`500` envelope. The console treats a non-200 insights leg as a degraded chart panel ("데이터를 불러올 수 없습니다"), never blanks the section (TASK-PC-FE-170).
- **Large tenant (many products/sellers)** — the aggregation fetches all groups then sorts in-memory for the two orderings; acceptable at demo scale. A future push-down (`ORDER BY … LIMIT` per metric) is noted but not required here.
