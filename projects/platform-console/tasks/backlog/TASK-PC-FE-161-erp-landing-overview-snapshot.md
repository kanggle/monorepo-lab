# TASK-PC-FE-161 — erp landing **operator overview snapshot**

**Status:** backlog
**Area:** platform-console / console-web · **Route:** `app/(console)/erp/page.tsx`
**Reference impl (template):** TASK-PC-FE-156 (ecommerce overview snapshot) — mirror the pattern, adapt the data source.

## Goal

Elevate the `/erp` section landing (currently a small links-only page, ~70 lines) into an **operator overview
snapshot** — masterdata counts + recent activity — matching the ecommerce landing (PC-FE-156). One of the 4
per-domain overview tasks that must land **before** the cross-domain "운영 → 개요" rename (TASK-PC-FE-162).

## Open design decision (resolve at backlog → ready)

- **Data source / read leg.** erp is a **console-bff READ leg** domain (read-only masterdata: 부서/직급/직원/원가센터/
  거래처, PC-FE-010; nav PC-FE-034; notification bell rewire PC-FE-137). Decide reuse of the existing erp read leg
  vs a new console-bff erp overview leg. Prefer consumed endpoints' `totalElements` (ADR-MONO-017 D3.B — no
  producer `/summary`).
- **Which metrics** for erp (e.g. 부서/직원/거래처 counts + recent masterdata changes / recent notifications).
  erp is read-only masterdata, so "recent activity" may be thin — decide whether a distribution row is meaningful
  or whether counts + a recent-changes list suffice.

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

- **Blocks:** TASK-PC-FE-162 (cross-domain rename capstone).
- **Reference:** TASK-PC-FE-156 (ecommerce, DONE).

## Promotion note

Not ready to implement: data-source decision + metric set + spec/AC must be filled in before `backlog → ready`.
Note erp's thinner surface may make it the smallest of the 4.
