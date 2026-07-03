# TASK-PC-FE-170 — E-Commerce 개요: remove 도메인 상태 block + add 4 ranking charts (products/sellers × order-count/revenue)

**Status:** done

**Type:** TASK-PC-FE
**Analysis model:** Opus 4.8 / **Recommended impl model:** Opus 4.8 (server-component fan-out surgery + a new chart primitive + test churn across page/overview/state suites)

> Cross-project feature — the producer half is **TASK-BE-469** (order-service `GET /admin/orders/insights`). Both land in **one atomic PR** (branch `task/ecom-overview-charts`). This task is the consumer half.

---

## Goal

Two operator-requested changes to the E-Commerce 개요 landing (`/ecommerce`):

1. **Remove the "도메인 상태" block** — the ecommerce `DomainHealthCard` (E-Commerce / OK / 정상 / `actuator status: UP`) currently rendered at the top of the section. It duplicates the per-area service-status dots already on each 운영 개요 count card and adds little operator value; the operator asked to drop it.
2. **Add four ranking bar charts** to the 운영 개요 — 상품별 주문횟수 / 상품별 매출 / 셀러별 주문횟수 / 셀러별 매출 — each a top-5 horizontal bar chart, sourced from the new producer endpoint TASK-BE-469 (`GET /admin/orders/insights`).

## Scope

**A. Remove 도메인 상태 (`app/(console)/ecommerce/page.tsx`):**

- Delete the `<div className="mb-8">…도메인 상태…<DomainHealthCard/>…</div>` block and the `ecommerce-health-unavailable` fallback.
- Delete the now-dead health wiring: `getDomainHealthState()` speculative call, the `healthState` await, `healthState.unauthorized` redirect, `ecommerceCard` derivation, and the `DomainHealthCard` / `getDomainHealthState` imports from `@/features/domain-health`.
- Do NOT touch the `features/domain-health` feature itself — it still powers the `(console)/dashboards/health` screen. Only this page's use of it is removed.
- The eligibility pre-flight, not-eligible / registry-degraded branches, and the `EcommerceOverview` render stay. (401 handling is preserved: `getCatalog()` 401 → `/login`, and `getEcommerceOverviewState`'s own leg-401 → `/login`.)

**B. Consume the insights endpoint:**

- `features/ecommerce-ops/api/order-types.ts` — add `RankedEntrySchema` (`{ id: string, label: string, value: number.nonnegative() }`, `.passthrough()`) + `OrderInsightsSchema` (`{ topProductsByOrderCount, topProductsByRevenue, topSellersByOrderCount, topSellersByRevenue }` each `z.array(RankedEntrySchema)`, `.passthrough()`) + exported types.
- `features/ecommerce-ops/api/orders-api.ts` — add `getOrderInsights(): Promise<OrderInsights>` calling `callEcommerce({ method:'GET', base: env.ECOMMERCE_ADMIN_BASE_URL, path:'/orders/insights' }, (j)=>OrderInsightsSchema.parse(j), ORDER_LABEL)`.

**C. Fan-out + seller-name resolution (`features/ecommerce-ops/api/overview-state.ts`):**

- Add an `insights` leg: `cell(getOrderInsights())` into the top-level `Promise.all` (same per-cell resilience: 403→forbidden, 401 re-thrown for whole-session redirect, else degraded).
- Add a seller-name resolution leg: `cell(listSellers({ page: 0, size: SELLER_MAX_PAGE_SIZE }))` (100) → build `Map<sellerId, displayName>`. This is separate from the existing `recentSellers` fetch (which is newest-5, not the top-volume sellers the rankings surface).
- Expose on `EcommerceOverviewState`: `insights: EcommerceInsights | null` + `insightsStatus: CellStatus`, where `EcommerceInsights` holds the four `RankedEntry[]` (`{ id, label, value }`) with **seller labels overlaid**: for the two seller rankings, `label = sellerNameMap.get(id) ?? entry.label`. Product rankings pass through unchanged (label = productName). If the seller-name leg degraded, fall back to the raw id label (never blank).

**D. Chart primitive (`features/ecommerce-ops/components/RankingBarChart.tsx`, server component — NO `'use client'`):**

- Pure CSS/flex horizontal bar chart (the project has NO chart library and `EcommerceOverview` is strictly server-rendered — do NOT add recharts / a client component). Props: `{ title: string; entries: RankedEntry[]; status: CellStatus; format: 'count' | 'currency'; testid: string }`.
- Each row: truncated label + a proportional bar (width = `value / maxValue * 100%`, min a sliver for non-zero) + the formatted value (`format==='currency'` → `₩{value.toLocaleString('ko-KR')}`, else `{value.toLocaleString()}건`).
- Degrade-safe: `status !== 'ok'` → compact "권한 없음" (forbidden) / "데이터를 불러올 수 없습니다" (degraded) note; empty `entries` → "데이터가 없습니다." Never throws, never blanks.
- Accessible: the chart is a `<dl>` / list with `aria-label={title}`; bars are decorative (`aria-hidden`), the value text carries the number.

**E. Render (`features/ecommerce-ops/components/EcommerceOverview.tsx`):**

- Add a "판매 순위" (or similar) section rendering the four `RankingBarChart` in a responsive 2-column grid (`md:grid-cols-2`), passing `state.insights?.<ranking> ?? []` + `state.insightsStatus`. Place it after the 주문 상태 block, before 최근 주문/셀러. `format='currency'` for the two 매출 charts, `'count'` for the two 주문횟수 charts.

**F. Tests:** update `tests/unit/ecommerce-page.test.tsx` (drop 도메인 상태 / DomainHealthCard expectations; the section still renders `EcommerceOverview`), `tests/unit/ecommerce-page-parallel.test.tsx` (drop the health speculative-fire assertion if present), `tests/unit/ecommerce-overview.test.tsx` (render the 4 charts; degraded/forbidden/empty states), `tests/unit/ecommerce-overview-state.test.ts` (insights leg + seller-name overlay + per-cell degrade). Keep the existing period-metric assertions intact.

**Out of scope:** any client-side interactivity (tooltips/animation) on the charts; a charting library; changes to `features/domain-health`; the producer endpoint (TASK-BE-469); date-range/time-series charts.

## Acceptance Criteria

- **AC-1** — `/ecommerce` no longer renders a "도메인 상태" heading or a `DomainHealthCard`; no dead `domain-health` import remains in `ecommerce/page.tsx`; the page still renders the eligible overview (counts + period metrics + charts + recent activity) and the not-eligible / degraded branches are unchanged.
- **AC-2** — The 운영 개요 shows four labelled top-5 horizontal bar charts (상품별 주문횟수 / 상품별 매출 / 셀러별 주문횟수 / 셀러별 매출); 매출 values render as `₩`-prefixed KRW, 주문횟수 as `건` counts; bars are proportional to the max in each chart.
- **AC-3** — Seller charts display the seller **displayName** when resolvable (from the product-service seller list), falling back to the raw `sellerId` when the name leg degraded or the id is absent from the map — never a blank label.
- **AC-4** — Resilience: an insights-leg `403` → the four charts show "권한 없음"; a `503`/timeout → "데이터를 불러올 수 없습니다"; a `401` in any leg → whole-session `/login` redirect. One leg's degrade never blanks the section (existing discipline).
- **AC-5** — `pnpm --filter console-web lint` + `tsc --noEmit` + `vitest run` all green (updated page/overview/state suites included).

## Related Specs

- `specs/contracts/console-integration-contract.md` § 2.4.10 (#21 order insights, added by this task) + § 2.4.9.1 (운영 개요 snapshot leg).
- ADR-MONO-017 (console fan-out model — direct ecommerce fan-out, no console-bff leg for the operator overview).

## Related Contracts

- Consumes the new producer read `GET /admin/orders/insights` (TASK-BE-469), documented as § 2.4.10 endpoint #21.

## Edge Cases

- **Seller id not in the name map** (top-volume seller beyond the fetched 100, or name leg degraded) → label falls back to the raw `sellerId` (AC-3).
- **Chart with a single dominant entry** → its bar is ~100% width, the rest proportional; a zero-value entry (won't occur for top-N of positive metrics, but defensively) renders a min-width sliver / no bar without dividing by zero (guard `maxValue > 0`).
- **Insights ok but a ranking empty** (e.g. no non-cancelled orders yet) → that chart shows "데이터가 없습니다.", the others still render.
- **Health-removal 401 regression** — verify the removed `healthState.unauthorized` redirect is still covered by `getCatalog()`'s 401→login and the overview leg's 401→login (no auth hole introduced).

## Failure Scenarios

- **Producer endpoint not yet deployed** (insights 404/503) → the insights leg degrades to the chart placeholder; the rest of the overview is unaffected (per-cell resilience).
- **Seller-name leg 403/503** → seller charts render with raw-id labels (degrade of the overlay only, not of the charts).
- **zod parse failure on a malformed insights body** → caught by `callEcommerce` → surfaced as `EcommerceUnavailable` → the insights cell degrades (chart placeholder), never a section crash (same tolerance rule as the other legs; schemas are `.passthrough()`).
