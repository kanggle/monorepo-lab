# TASK-PC-FE-166 — wms landing **operator overview snapshot**

**Status:** done
**Renumbered:** from PC-FE-158 (concurrent-session ID collision with the shared-status-badge series; the implemented status-badge task kept 158).
**Area:** platform-console / console-web · **Route:** `app/(console)/wms/page.tsx`
**Reference impl (template):** TASK-PC-FE-156 (ecommerce overview snapshot) — mirror the pattern, adapt the data source.
**Implemented:** branch `task/pc-fe-166-wms-overview` (bundled with the PC-FE-168 shared read-leg decision). `pnpm lint` + `tsc --noEmit` + `vitest` green.
**Analysis model:** Opus 4.8 · **Impl model:** Opus (server fan-out + resilience semantics).

## Goal

Elevate the `/wms` section landing into an **operator overview snapshot** — per-area counts + key status
distribution + recent activity — so the wms landing gives a domain overview at a glance, matching the ecommerce
landing (PC-FE-156). This is the **first bff-domain reference impl** of the 4 per-domain overview tasks that must
land **before** the cross-domain "운영 → 개요" rename capstone (TASK-PC-FE-162).

> **Premise corrections (found during impl).** Two task premises were stale: (1) `/wms` is NOT "health card +
> links" — it already renders the full `WmsOpsScreen` (inventory/shipments/alerts tables); the snapshot is added as
> a band ABOVE those tables. (2) There is NO existing wms domain-health card to "keep" (only ecommerce's landing has
> one); no health card is added here (out of scope — would pull in a console-bff leg).

## Read-leg decision (RESOLVED — TASK-PC-FE-168)

- **console-web DIRECT fan-out** (Option 1). The "console-bff READ leg" framing was a premise error: wms already
  reaches its producer server-side via `getDomainFacingToken()` (`WMS_ADMIN_BASE_URL`, § 2.4.5 direct client) —
  same model as ecommerce (§ 2.4.10). The overview reuses the existing wms `list*` reads' `totalElements`
  (`?page=0&size=1`); **NO console-bff leg, NO producer `/summary`, NO producer retrofit** (ADR-MONO-017 D3.B). Full
  rationale + the 4-domain shared decision: `console-web/architecture.md § 도메인 랜딩 운영 개요 스냅샷`.
- **Metrics (chosen):** area counts = 재고(`/dashboard/inventory`) · 배송(`/dashboard/shipments`) · 알림
  (`/dashboard/alerts`) `totalElements`; alert-ack distribution = 미확인(`acknowledged=false`) / 확인
  (`acknowledged=true`); recent activity = 최근 출고 (`/dashboard/shipments?size=5`).
- **Deviation vs ecommerce:** count tiles are **read-only stat tiles, NOT nav links** — `/wms` is a single-route ops
  screen (no per-area drill-in routes), so tiles summarize the tables rendered directly below.

## Scope (implemented)

- **NEW** `features/wms-ops/api/overview-state.ts` — `getWmsOverviewState(eligible)` → `WmsOverviewState`
  (counts[], alertStatus[], recentShipments, each carrying a per-cell `status`). Reuses `listInventory` /
  `listShipments` / `listAlerts`; bounded parallel fan-out; a `401` re-throws → whole-session `redirect('/login')`;
  `403` → forbidden, `503`/timeout/network → degraded (per-cell). `eligible === false` ⇒ no calls.
- **NEW** `features/wms-ops/components/WmsOverview.tsx` — presentational server component (no `'use client'`):
  count tiles + alert-ack distribution + recent-shipments panel; degraded/forbidden cells render a compact
  placeholder (never blank).
- **MODIFY** `features/wms-ops/components/WmsOpsScreen.tsx` — optional `overview?: ReactNode` slot rendered above the
  tables (RSC-idiomatic: the server page passes a `<WmsOverview>` node into the client screen).
- **MODIFY** `app/(console)/wms/page.tsx` — fire `getWmsOverviewState(eligible)` concurrently with
  `getWmsSectionState`; pass `<WmsOverview>` into `WmsOpsScreen`. Eligibility / degraded / forbidden / 401 branches
  unchanged.
- **MODIFY** `features/wms-ops/index.ts` (barrel) — export the new state fn + component + types.

## Acceptance Criteria

- [x] **AC-1** Per-area counts (재고/배송/알림) + an alert-ack distribution + recent shipments render on `/wms`
  (eligible path), above the existing ops tables.
- [x] **AC-2** Counts come from `list*({page:0,size:1}).totalElements`; distribution from `acknowledged`-filtered
  lists; recent from `size:5`. NO new producer endpoint / no `/summary` / no console-bff call introduced.
- [x] **AC-3** Per-cell resilience: `403` → "권한 없음", `503`/timeout/other → "점검 필요" (compact, no crash, other
  cells unaffected); a `401` from ANY cell → `redirect('/login')` (whole-session).
- [x] **AC-4** `notEligible` short-circuits before any fan-out (no wms call when ineligible). Section degrade /
  forbidden / not-eligible branches unchanged.
- [x] **AC-5** Contract `§ 2.4.5.2` subsection + architecture wms section + the shared read-leg decision updated;
  ADR-MONO-017 D3.B compliance noted.
- [x] **AC-6** `pnpm lint` + `tsc --noEmit` + `vitest` green — unit: `wms-overview-state` (count/distribution
  mapping, 401 redirect, per-cell degrade/forbidden, not-eligible short-circuit), `wms-overview` (renders
  counts/distribution/recent + degraded cells).

## Dependencies

- **Blocked by:** TASK-PC-FE-168 (shared read-leg decision) — RESOLVED + landed in the same PR.
- **Blocks:** TASK-PC-FE-162 (cross-domain rename capstone — needs all 4 domain landings to be real overviews first).
- **Reference:** TASK-PC-FE-156 (ecommerce, DONE) — reference implementation (direct-model).

## Related Specs / Contracts

- `specs/contracts/console-integration-contract.md` § 2.4.5.2 (wms overview snapshot) / § 2.4.5 / § 2.5
- `specs/services/console-web/architecture.md` § 도메인 랜딩 운영 개요 스냅샷 (PC-FE-168 decision) + wms-ops feature line
- Producer: wms `admin-service-api.md` § 1.1/1.3/1.6 **list** reads — consumed read-only, already documented; no redefinition.
