# TASK-PC-FE-161 — erp landing **operator overview snapshot**

**Status:** done
**Area:** platform-console / console-web · **Route:** `app/(console)/erp/page.tsx` (masters route)
**Reference impl (template):** TASK-PC-FE-166 (wms overview snapshot) / TASK-PC-FE-156 (ecommerce, direct-model).
**Implemented:** branch `task/pc-fe-167-161-scm-erp-overview` (bundled with PC-FE-167 scm). `pnpm lint` + `tsc --noEmit` + `vitest` green.
**Analysis model:** Opus 4.8 · **Impl model:** Opus.

## Goal

Elevate the `/erp` **masters** landing into an **operator overview snapshot** — masterdata counts — matching the wms
landing (PC-FE-166). One of the 4 per-domain overview tasks that must land **before** the cross-domain "운영 → 개요"
rename (TASK-PC-FE-162). The **thinnest** of the 4.

## Read-leg decision (RESOLVED — TASK-PC-FE-168)

- **console-web DIRECT fan-out** reusing the existing § 2.4.8 master `list*` reads' `meta.totalElements`
  (`?page=0&size=1`); **NO console-bff leg, NO producer `/summary`, NO retrofit** (ADR-MONO-017 D3.B).
- **Metrics (chosen):** 5 master counts — 부서/직원/직급/원가센터/거래처. **No status distribution / no recent feed**
  (erp masters are effective-dated masterdata, not an activity stream — the thinnest overview). `?asOf=` (E3) threads
  through every count leg verbatim (matching `getErpMastersState`); `active` omitted so retired masters are counted
  (E2 honesty). Count tiles are **non-link stat tiles** (single-route masters screen — PC-FE-168 deviation).
- **Placement:** the `/erp` **masters** route (`getErpMastersState` / `ErpMastersScreen`), the first ERP drill child.

## Scope (implemented)

- **NEW** `features/erp-ops/api/overview-state.ts` (`getErpMastersOverviewState`) + `components/ErpMastersOverview.tsx`.
- **MODIFY** `ErpMastersScreen.tsx` (optional `overview` slot), `erp/page.tsx` (fire fan-out concurrently with `asOf`,
  slot above the master lists), `erp-ops/index.ts` (barrel).

## Acceptance Criteria

- [x] **AC-1** 5 masterdata counts render on `/erp` (eligible path), above the master lists.
- [x] **AC-2** Counts from `meta.totalElements` (`size=1`). NO new producer endpoint / no `/summary` / no console-bff call.
- [x] **AC-3** Per-cell resilience (403→권한 없음, 503/timeout→점검 필요); a 401 in ANY leg → `redirect('/login')`.
- [x] **AC-4** `notEligible` short-circuits before any fan-out. Section degrade/forbidden/not-eligible branches
  unchanged. `?asOf=` (E3) threaded through every count leg.
- [x] **AC-5** Contract `§ 2.4.8.1` subsection + architecture erp section updated; ADR-MONO-017 D3.B noted.
- [x] **AC-6** `pnpm lint` + `tsc --noEmit` + `vitest` green — `erp-masters-overview-state` + `erp-masters-overview` unit tests.

## Dependencies

- **Blocked by:** TASK-PC-FE-168 (shared read-leg decision) — DONE (merged #2148).
- **Blocks:** TASK-PC-FE-162 (cross-domain rename capstone).
- **Reference:** TASK-PC-FE-166 (wms, DONE) / TASK-PC-FE-156 (ecommerce, DONE).
