# TASK-PC-FE-158 — wms landing **operator overview snapshot**

**Status:** backlog
**Area:** platform-console / console-web · **Route:** `app/(console)/wms/page.tsx`
**Reference impl (template):** TASK-PC-FE-156 (ecommerce overview snapshot) — mirror the pattern, adapt the data source.

## Goal

Elevate the `/wms` section landing (currently health card + links) into an **operator overview snapshot** —
per-area counts + key status distribution + recent activity — so the wms landing gives a domain overview at a
glance, matching the ecommerce landing (PC-FE-156). This is one of the 4 per-domain overview tasks that must land
**before** the cross-domain "운영 → 개요" rename capstone (TASK-PC-FE-162).

## Open design decision (resolve at backlog → ready)

- **Data source / read leg.** ecommerce used a console-web DIRECT fan-out (§2.4.10). wms is a **console-bff READ
  leg** domain (§2.4.9.1/§2.4.9.2). Decide: (a) reuse/extend the existing wms section list reads on the console
  side, or (b) add/extend a console-bff wms overview leg. Prefer reusing existing consumed endpoints'
  `totalElements` (ADR-MONO-017 D3.B — no producer `/summary`); NO producer retrofit.
- **Which metrics** are meaningful for wms (e.g. inbound/outbound/inventory/alert counts + open-alert distribution
  + recent movements). Define with the wms section owner / architecture.md.

## Scope (to be finalized)

- Add a wms overview state fan-out + presentational component (mirror `getEcommerceOverviewState` /
  `EcommerceOverview`), wire into `wms/page.tsx`. Per-cell degrade + 401→whole-session redirect.
- Keep the existing aggregate wms domain-health card.

## Acceptance Criteria (draft — finalize before ready)

- Per-area counts + a status distribution + recent activity render on `/wms` (eligible path).
- Reuses existing consumed endpoints; no producer `/summary`, no new producer retrofit.
- Per-cell degrade cell-local; 401 → `redirect('/login')`; eligibility/degraded branches unchanged.
- Spec-first: `console-integration-contract.md` + `console-web/architecture.md` wms section updated.
- `pnpm lint` + `tsc` + `vitest` green.

## Dependencies

- **Blocks:** TASK-PC-FE-162 (cross-domain rename capstone — needs all 4 domain landings to be real overviews first).
- **Reference:** TASK-PC-FE-156 (ecommerce, DONE) — reference implementation.

## Promotion note

Not ready to implement: the data-source decision + metric set + spec/AC must be filled in before `backlog → ready`
(per `tasks/INDEX.md` backlog→ready gate).
