# TASK-PC-FE-167 — scm landing **operator overview snapshot**

**Status:** backlog
**Renumbered:** from PC-FE-159 (concurrent-session ID collision with the shared-status-badge series; the implemented status-badge task kept 159).
**Area:** platform-console / console-web · **Route:** `app/(console)/scm/page.tsx`
**Reference impl (template):** TASK-PC-FE-156 (ecommerce overview snapshot) — mirror the pattern, adapt the data source.

## Goal

Elevate the `/scm` section landing (currently health card + links) into an **operator overview snapshot** —
per-area counts + key status distribution + recent activity — matching the ecommerce landing (PC-FE-156). One of
the 4 per-domain overview tasks that must land **before** the cross-domain "운영 → 개요" rename (TASK-PC-FE-162).

## Open design decision (resolve at backlog → ready)

- **Data source / read leg.** scm is a **console-bff READ leg** domain (§2.4.9.1/§2.4.9.2), unlike ecommerce's
  DIRECT model (§2.4.10). Decide: reuse existing scm section list reads on the console side vs a console-bff scm
  overview leg. Prefer reusing consumed endpoints' `totalElements` (ADR-MONO-017 D3.B — no producer `/summary`).
- **Which metrics** for scm (e.g. procurement PO counts, inventory-visibility, replenishment-suggestion backlog +
  suggestion status distribution + recent POs/suggestions). Define with the scm section owner / architecture.md.
  Note scm already has some operator surfaces (replenishment gate PC-FE-077, config PC-FE-080) to draw counts from.

## Scope (to be finalized)

- Add an scm overview state fan-out + presentational component (mirror PC-FE-156), wire into `scm/page.tsx`.
  Per-cell degrade + 401→whole-session redirect. Keep the existing aggregate scm domain-health card.

## Acceptance Criteria (draft — finalize before ready)

- Per-area counts + a status distribution + recent activity render on `/scm` (eligible path).
- Reuses existing consumed endpoints; no producer `/summary`, no new producer retrofit.
- Per-cell degrade cell-local; 401 → `redirect('/login')`; eligibility/degraded branches unchanged.
- Spec-first: `console-integration-contract.md` + `console-web/architecture.md` scm section updated.
- `pnpm lint` + `tsc` + `vitest` green.

## Dependencies

- **Blocks:** TASK-PC-FE-162 (cross-domain rename capstone).
- **Reference:** TASK-PC-FE-156 (ecommerce, DONE).

## Promotion note

Not ready to implement: data-source decision + metric set + spec/AC must be filled in before `backlog → ready`.
