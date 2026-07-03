# TASK-PC-FE-167 — scm landing **operator overview snapshot**

**Status:** done
**Renumbered:** from PC-FE-159 (concurrent-session ID collision with the shared-status-badge series; the implemented status-badge task kept 159).
**Area:** platform-console / console-web · **Route:** `app/(console)/scm/page.tsx`
**Reference impl (template):** TASK-PC-FE-166 (wms overview snapshot) / TASK-PC-FE-156 (ecommerce, direct-model).
**Implemented:** branch `task/pc-fe-167-161-scm-erp-overview` (bundled with PC-FE-161 erp). `pnpm lint` + `tsc --noEmit` + `vitest` green.
**Analysis model:** Opus 4.8 · **Impl model:** Opus.

## Goal

Elevate the `/scm` section landing into an **operator overview snapshot** — per-area counts + a PO-status
distribution + recent activity — matching the wms landing (PC-FE-166). One of the 4 per-domain overview tasks that
must land **before** the cross-domain "운영 → 개요" rename (TASK-PC-FE-162).

> **Premise correction:** `/scm` was already a full ops screen (`ScmOpsScreen` — PO list + inventory snapshot +
> staleness), not "health card + links"; the snapshot is a band ABOVE those tables. No wms/scm health card exists.

## Read-leg decision (RESOLVED — TASK-PC-FE-168)

- **console-web DIRECT fan-out** reusing the existing § 2.4.6 `listPurchaseOrders` / `getSnapshot` reads'
  `totalElements` (`?page=0&size=1`); **NO console-bff leg, NO producer `/summary`, NO retrofit** (ADR-MONO-017 D3.B).
- **Metrics (chosen):** area counts = 발주(PO) + 재고 스냅샷; PO-status distribution over `KNOWN_PO_STATUSES`;
  recent = 최근 발주 (size=5). **S5 (§ 2.4.6):** the 재고 스냅샷 count surfaces the REQUIRED `meta.warning` via
  `<S5Warning>` (never stripped). Count tiles are **non-link stat tiles** (single-route ops screen — PC-FE-168 deviation).

## Scope (implemented)

- **NEW** `features/scm-ops/api/overview-state.ts` (`getScmOverviewState`) + `components/ScmOverview.tsx`.
- **MODIFY** `ScmOpsScreen.tsx` (optional `overview` slot), `scm/page.tsx` (fire fan-out concurrently, slot above
  tables), `scm-ops/index.ts` (barrel).

## Acceptance Criteria

- [x] **AC-1** 발주/재고 스냅샷 counts + PO-status distribution + recent POs render on `/scm` (eligible path).
- [x] **AC-2** Counts from `totalElements` (`size=1`); distribution from `status`-filtered lists; recent from `size=5`.
  NO new producer endpoint / no `/summary` / no console-bff call.
- [x] **AC-3** Per-cell resilience (403→권한 없음, 503/timeout/429→점검 필요); a 401 in ANY leg → `redirect('/login')`.
- [x] **AC-4** `notEligible` short-circuits before any fan-out. Section degrade/forbidden/ratelimited/not-eligible
  branches unchanged. **S5 warning surfaced whenever the inventory count is shown.**
- [x] **AC-5** Contract `§ 2.4.6.3` subsection + architecture scm section updated; ADR-MONO-017 D3.B noted.
- [x] **AC-6** `pnpm lint` + `tsc --noEmit` + `vitest` green — `scm-overview-state` + `scm-overview` unit tests.

## Dependencies

- **Blocked by:** TASK-PC-FE-168 (shared read-leg decision) — DONE (merged #2148).
- **Blocks:** TASK-PC-FE-162 (cross-domain rename capstone).
- **Reference:** TASK-PC-FE-166 (wms, DONE) / TASK-PC-FE-156 (ecommerce, DONE).
