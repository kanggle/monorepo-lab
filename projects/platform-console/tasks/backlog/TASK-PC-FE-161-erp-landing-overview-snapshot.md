# TASK-PC-FE-161 — erp landing **operator overview snapshot**

**Status:** backlog
**Area:** platform-console / console-web · **Route:** `app/(console)/erp/page.tsx`
**Reference impl (template):** TASK-PC-FE-156 (ecommerce overview snapshot) — mirror the pattern, adapt the data source.

## Goal

Elevate the `/erp` section landing (currently a small links-only page, ~70 lines) into an **operator overview
snapshot** — masterdata counts + recent activity — matching the ecommerce landing (PC-FE-156). One of the 4
per-domain overview tasks that must land **before** the cross-domain "운영 → 개요" rename (TASK-PC-FE-162).

## Read-leg decision (RESOLVED — TASK-PC-FE-168)

- **Data source / read leg — RESOLVED: console-web DIRECT fan-out.** The "console-bff READ leg" framing was a
  premise error: erp already reaches its producer server-side via `getDomainFacingToken()` (§ 2.4.8 direct client) —
  same model as ecommerce. Per the PC-FE-168 shared decision, the overview reuses the existing erp `list*` reads'
  `totalElements` (`?page=0&size=1`); **NO console-bff leg, NO producer `/summary`, NO producer retrofit**
  (ADR-MONO-017 D3.B). Follow the PC-FE-166 (wms) template.
- **Placement caveat:** erp is split into 4 routes (`/erp` masters, `/erp/orgview`, `/erp/approval`, `/erp/delegation`
  — PC-FE-076). The overview belongs on the `/erp` **masters** route (`getErpMastersState`).
- **Which metrics** (still finalize before ready): 부서/직원/거래처(+직급/원가센터) master `totalElements`; optionally
  approval-inbox pending + delegation-facts counts. erp is read-only masterdata, so a status distribution may be
  thin — counts + (optional) a recent/pending list likely suffice. Thinnest of the 4.

## Scope (to be finalized)

- Add an erp overview state fan-out + presentational component (mirror PC-FE-156), wire into `erp/page.tsx`.
  Per-cell degrade + 401→whole-session redirect. Keep the aggregate erp domain-health card if present.

## Acceptance Criteria (draft — finalize before ready)

- Masterdata counts (+ recent activity where meaningful) render on `/erp` (eligible path).
- Reuses existing consumed endpoints; no producer `/summary`, no new producer retrofit.
- Per-cell degrade cell-local; 401 → `redirect('/login')`; eligibility/degraded branches unchanged.
- Spec-first: `console-integration-contract.md` + `console-web/architecture.md` erp section updated.
- `pnpm lint` + `tsc` + `vitest` green.

## Dependencies

- **Blocked by:** TASK-PC-FE-168 (shared read-leg decision) — promote to `ready` only after 168 lands; follows the PC-FE-166 (wms) reference impl.
- **Blocks:** TASK-PC-FE-162 (cross-domain rename capstone).
- **Reference:** TASK-PC-FE-156 (ecommerce, DONE).

## Promotion note

Not ready to implement: data-source decision + metric set + spec/AC must be filled in before `backlog → ready`.
Note erp's thinner surface may make it the smallest of the 4.
