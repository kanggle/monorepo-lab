# TASK-PC-FE-164 — E-Commerce 운영 개요: 기간별(오늘/주간/월간) 지표

**Status:** done

**Type:** TASK-PC-FE
**Analysis model:** Opus 4.8 / **Recommended impl model:** Sonnet 4.6 (consumer API + server-component overview change; no new architectural decision)

> Cross-project feature — the producer half is **TASK-BE-468** (7 `GET .../summary` endpoints). Both land in **one atomic PR** (branch `feat/ecommerce-ops-period-metrics`). This task is the consumer + contract half.

---

## Goal

Replace the E-Commerce 운영 개요 (`/ecommerce`) whole-cumulative area counts with **period-based (오늘 / 주간 / 월간) metrics**, consuming the new per-service `GET .../summary` endpoints (TASK-BE-468). Each of the 7 area cards shows 오늘 / 주간 / 월간 counts (calendar KST) with the cumulative total retained as context.

Today the snapshot fan-out (`getEcommerceOverviewState`, `EcommerceOverview.tsx`) calls each `list*({ page: 0, size: 1 })` and reads `totalElements`. This task swaps the count leg to the summary endpoints and renders the period breakdown; the resilience model (per-cell ok / forbidden / degraded, 401 → whole-session re-login, no auto-refetch) is preserved verbatim.

## Scope

**In scope** (`projects/platform-console/apps/console-web/src/features/ecommerce-ops/`):

1. **Consumer API** — add a `get*Summary()` server function per area alongside the existing `list*` clients (`orders-api.ts`, `products-api.ts`, `sellers-api.ts`, `shippings-api.ts`, `promotions-api.ts`, `users-api.ts`, `notifications-api.ts`), each calling the area's `…/summary` path and Zod-parsing `{ today, week, month, total }` (all non-negative ints; `.passthrough()` tolerance like the sibling read schemas).
2. **Overview state** — `overview-state.ts`: replace the per-area `list*({size:1})` count leg with the `get*Summary()` fan-out. `AreaCount` gains `today/week/month` (keep `count` = `total` for back-compat). Same `cell()` resilience wrapper (403 → forbidden, 401 → re-throw → redirect, else degraded). The order-status distribution + recent-orders/sellers legs are unchanged.
3. **Overview presentation** — `EcommerceOverview.tsx`: each `CountCard` renders 오늘 / 주간 / 월간 (with 전체/total as secondary). Degraded/forbidden cells keep the "점검 필요" / "권한 없음" placeholder (never blank). Preserve the PC-FE-155 back-compat `data-testid`s (`ecommerce-<area>-link`, `<key>-count`) and add period test-ids (`<key>-count-today|week|month`).
4. **Contract** — update `specs/contracts/console-integration-contract.md` § 2.4.10 (+ the § 2.4.9.1 snapshot note) to document the 7 summary endpoints and the `{today,week,month,total}` shape.
5. **Tests** — extend the overview-state + `EcommerceOverview` vitest to cover: all-ok period render, one degraded cell, one forbidden cell, 401 redirect.

**Out of scope:** the list/detail/mutation screens (unchanged); any period *picker/toggle* (all three periods shown at once — no interactive filter this task); server-side aggregation logic (owned by TASK-BE-468); a period breakdown for the order-status distribution or recent-activity panels.

## Acceptance Criteria

- **AC-1** — Each of the 7 area cards displays 오늘 / 주간 / 월간 counts sourced from the area's `…/summary` endpoint, plus the cumulative total.
- **AC-2** — Resilience unchanged: a `403` summary → "권한 없음" on that card only; a `503`/timeout → "점검 필요" on that card only; a `401` → whole-session `redirect('/login')`; no card blanks the section. No client-side auto-refetch.
- **AC-3** — Back-compat test-ids retained (`ecommerce-<area>-link`, quick-launch links still work); new `<key>-count-{today,week,month}` test-ids present.
- **AC-4** — `pnpm --filter console-web lint && tsc --noEmit && vitest run` green (local front-end gate — lint catches `no-unused-vars` that tsc/vitest miss).
- **AC-5** — Contract § 2.4.10 lists the 7 summary endpoints with the response shape; no dangling reference.

## Related Specs

- `specs/contracts/console-integration-contract.md` § 2.4.9.1 (overview snapshot leg) + § 2.4.10 (ecommerce operator binding).
- The producer half: `projects/ecommerce-microservices-platform/tasks/ready/TASK-BE-468-admin-period-summary-endpoints.md`.

## Related Contracts

- 7 new read endpoints (`GET …/summary`) added to console-integration-contract § 2.4.10.

## Edge Cases

- **Partial degrade** — one area's summary `503` must not blank the others; the card shows "점검 필요" while the rest render period counts (existing `cell()` semantics).
- **`today > week`? never** — the backend guarantees `today ≤ week ≤ month ≤ total`; the UI does not re-derive or assert this, just renders.
- **notEligible operator** — no fan-out, section renders null (unchanged).
- **Zero activity** — `{0,0,0,0}` renders as `0`/`0`/`0`, not "점검 필요" (a successful zero is `ok`, distinct from a degraded cell).

## Failure Scenarios

- **Summary endpoint missing (pre-deploy skew)** — a `404` from `…/summary` is treated as `degraded` ("점검 필요"), matching the `cell()` non-401/403 branch — the overview never crashes even if the producer half lags.
- **Schema drift** — a summary payload missing a period field fails the Zod parse in the api client → the `cell()` catch degrades that card only (tolerant `.passthrough()` allows extra fields; missing required fields degrade, not crash).
