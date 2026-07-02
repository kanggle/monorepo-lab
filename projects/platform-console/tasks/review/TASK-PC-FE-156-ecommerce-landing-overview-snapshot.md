# TASK-PC-FE-156 — elevate the ecommerce landing into an **operator overview snapshot**

**Status:** review
**Area:** platform-console / console-web · **Route:** `app/(console)/ecommerce/page.tsx` + `features/ecommerce-ops`
**Parent:** ADR-MONO-031 §2.4.10 console-absorption. Follows TASK-PC-FE-155 (landing quick-links). Realizes the
"operator-overview snapshot leg" that the §2.4.10 landing doc-comment had marked a deferred follow-up.
**Analysis model:** Opus 4.8 · **Impl model recommendation:** Opus (server fan-out + resilience semantics).

## Goal

The `/ecommerce` landing today shows only an aggregate domain-health card + 7 nav links. Elevate it into a real
**operator overview snapshot** for the ecommerce domain: per-area entity counts, order-status distribution, and a
recent-activity glance — so an operator gets a domain overview at a glance instead of a bare nav hub.

## Architecture — console-web direct fan-out (chosen; no backend/producer change)

Per ADR-MONO-017 D3.B, **no producer `/summary` aggregation endpoint** may be added; counts are derived from the
existing list endpoints' `totalElements` with `?page=0&size=1`. Per §2.4.10 the ecommerce operator surface is
**console-web → ecommerce gateway DIRECT** (domain-facing IAM OIDC token, `getDomainFacingToken()`), so the
overview fan-out runs **server-side in the landing** reusing the existing `features/ecommerce-ops/api` list
functions (`listProducts/listOrders/listUsers/listPromotions/listShippings/listSellers/listTemplates`). **No
console-bff leg, no new producer endpoint, no producer retrofit** (contrast: the console-wide §2.4.9.1 operator
overview uses a console-bff fan-out — this is a domain-internal snapshot, so the direct model fits).

Data assembled by the fan-out (all reuse existing typed `list*` → `totalElements` / `content`):

- **Entity counts (7 areas):** each `list({ page: 0, size: 1 }).totalElements` — 상품/주문/사용자/프로모션/배송/셀러/알림템플릿.
- **Order-status distribution:** `listOrders({ status, page: 0, size: 1 }).totalElements` for each `ORDER_STATUS_VALUES`
  (PENDING/CONFIRMED/SHIPPED/DELIVERED/CANCELLED).
- **Recent activity:** `listOrders({ page: 0, size: 5 }).content` (recent orders) + `listSellers({ page: 0, size: 5 }).content` (recent sellers).
- **Per-area brief status ("각 서비스별 간략 상태"):** DERIVED from each fan-out cell's outcome — success → `정상`,
  `403` → `권한 없음`, `503`/timeout/network/other → `점검 필요`. (True per-microservice actuator health is NOT
  reachable from console-web direct calls behind the gateway; reachability-derived status is the honest,
  zero-backend signal. The existing aggregate ecommerce `/actuator/health` card is kept on top.)

## Resilience (§2.4.10 / §2.5) — the decisive rule

Fan-out is bounded + parallel (`Promise.all` over per-cell promises). Each cell **catches its own error into a
cell state** (ok / forbidden / degraded) **EXCEPT `401`, which it re-throws** so the top-level catch performs a
whole-session `redirect('/login')` (no partial authed state — same invariant as `mapSectionResilience`). One
area's degrade never blanks the section; the `(console)` shell + other sections stay intact.

## Scope

Under `projects/platform-console/apps/console-web/src/`:

- **NEW `features/ecommerce-ops/api/overview-state.ts`** — `getEcommerceOverviewState(eligible: boolean)` →
  `EcommerceOverviewState` (counts[], orderStatus[], recentOrders, recentSellers, each carrying a per-cell
  `status: 'ok'|'forbidden'|'degraded'`). `eligible === false` ⇒ no calls, `{ notEligible: true }`. Reuses the
  `list*` api fns; 401 re-throws to redirect. Bounded: single page-0 reads, `size` 1 (counts) / 5 (recent); no auto-refetch.
- **NEW `features/ecommerce-ops/components/EcommerceOverview.tsx`** — presentational **server component** (no
  `'use client'`) rendering: (a) a count-card grid where each card = area label + count + status badge and is the
  `Link` to that area (keeps the PC-FE-155 nav affordance — a card IS the quick-launch link, back-compat testids
  retained), (b) an order-status distribution row, (c) two recent-activity mini-lists (orders/sellers). Degraded
  cells render a compact "점검 필요"/"권한 없음" placeholder instead of a number (never blanks).
- **MODIFY `app/(console)/ecommerce/page.tsx`** — on the eligible path, after the health card, call
  `getEcommerceOverviewState(true)` and render `<EcommerceOverview />` in place of the plain PC-FE-155 grid.
  Eligibility / registry-degraded / not-eligible / 401-redirect / health-card branches unchanged.
- **MODIFY `features/ecommerce-ops/index.ts`** (barrel) — export the new state fn + component.

Non-goals: no producer change, no console-bff change, no mutation, no auto-refresh/polling, no revenue/₩ sum
(only counts + status + recent rows — matches the operator-overview "no synthetic aggregation" discipline).

## Spec-first

- `projects/platform-console/specs/contracts/console-integration-contract.md` §2.4.10 — add a subsection
  (e.g. **§2.4.10.6 ecommerce overview snapshot**) documenting: console-web direct fan-out, consumed **existing**
  list endpoints via `size=1` count + `status` filter + `size=5` recent, ADR-MONO-017 D3.B compliance (no
  `/summary`), 401→login / per-cell degrade. **No new consumed producer endpoint** (list endpoints already listed).
- `projects/platform-console/specs/services/console-web/architecture.md` — update the ecommerce section line
  (`page.tsx` = domain-health 랜딩 + overview snapshot; note the new `overview-state.ts` / `EcommerceOverview`).

## Acceptance Criteria

- **AC-1** Eligible `/ecommerce` renders 7 per-area count cards (each a `Link` to its area with the PC-FE-155
  back-compat testids), an order-status distribution, and recent orders + recent sellers lists.
- **AC-2** Counts come from `list*({page:0,size:1}).totalElements`; order-status from status-filtered lists; recent
  from `size:5` lists. NO new producer endpoint / no `/summary` call / no console-bff call is introduced.
- **AC-3** Per-cell resilience: a `403` cell → "권한 없음", a `503`/timeout/other cell → "점검 필요" (compact, no
  crash, other cells unaffected); a `401` from ANY cell → `redirect('/login')` (whole-session).
- **AC-4** `notEligible`/`registryDegraded` short-circuit before any fan-out (no ecommerce call when ineligible).
  Aggregate ecommerce health card + shell + other sections unchanged.
- **AC-5** Contract §2.4.10 subsection + architecture ecommerce line updated; ADR-MONO-017 D3.B compliance noted.
- **AC-6** `pnpm lint` + `tsc --noEmit` + `vitest` green — unit: `overview-state` (count mapping, 401 redirect,
  per-cell degrade/forbidden, not-eligible short-circuit), `EcommerceOverview` (renders counts/degraded cells),
  updated `ecommerce-page` test.

## Related Specs

- `projects/platform-console/specs/contracts/console-integration-contract.md` §2.4.10 / §2.4.9.1 (overview pattern reference) / §2.5
- `projects/platform-console/specs/services/console-web/architecture.md` (Server vs Client Components; ecommerce section)

## Related Contracts

- Producer: ecommerce product/order/user/promotion/shipping/seller/notification **list** endpoints
  (`GET /api/admin/<entity>?page&size&status`). Consumed read-only, already documented; **no redefinition**.

## Edge Cases

- `notEligible` / `registryDegraded` → no fan-out, no cards (no false affordance).
- A single area gateway 503 → that one card shows "점검 필요"; the other 6 counts + recent lists still render.
- Empty tenant (all counts 0) → cards render `0` (valid), not degraded.
- Unknown/future order status from producer → tolerated (passthrough); distribution shows only known
  `ORDER_STATUS_VALUES` buckets, no crash.
- Non-ASCII ids in recent rows → rendered as text only (no navigation double-encode risk on the overview itself).

## Failure Scenarios

- All legs 503 → every count card "점검 필요" + recent lists degraded, but the section shell + health card render
  (degrade-safe, §2.5).
- `401` mid-fan-out (session expired) → `redirect('/login')`; no half-rendered authed snapshot.
- `403` (operator lacks a sub-role for one area) → only that area's card degrades to "권한 없음".
