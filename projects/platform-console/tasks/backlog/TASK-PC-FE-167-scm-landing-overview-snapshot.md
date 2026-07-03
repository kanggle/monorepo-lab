# TASK-PC-FE-167 — scm landing **operator overview snapshot**

**Status:** backlog
**Renumbered:** from PC-FE-159 (concurrent-session ID collision with the shared-status-badge series; the implemented status-badge task kept 159).
**Area:** platform-console / console-web · **Route:** `app/(console)/scm/page.tsx`
**Reference impl (template):** TASK-PC-FE-156 (ecommerce overview snapshot) — mirror the pattern, adapt the data source.

## Goal

Elevate the `/scm` section landing (currently health card + links) into an **operator overview snapshot** —
per-area counts + key status distribution + recent activity — matching the ecommerce landing (PC-FE-156). One of
the 4 per-domain overview tasks that must land **before** the cross-domain "운영 → 개요" rename (TASK-PC-FE-162).

## Read-leg decision (RESOLVED — TASK-PC-FE-168)

- **Data source / read leg — RESOLVED: console-web DIRECT fan-out.** The "console-bff READ leg" framing was a
  premise error: the scm section already reaches its producer server-side via `getDomainFacingToken()`
  (`SCM_GATEWAY_BASE_URL`, § 2.4.6 direct client) — same model as ecommerce (§ 2.4.10). Per the PC-FE-168 shared
  decision, the overview reuses the existing scm `list*` reads' `totalElements` (`?page=0&size=1`); **NO console-bff
  leg, NO producer `/summary`, NO producer retrofit** (ADR-MONO-017 D3.B). Follow the PC-FE-166 (wms) template.
  Carry the § 2.4.6 scm S5 `meta.warning` freshness honesty + the 429 bounded-backoff discipline (no re-storm).
- **Which metrics** (still finalize before ready): procurement PO `totalElements` + PO-status distribution + recent
  POs; optionally replenishment-suggestion backlog (PC-FE-077) / config surfaces (PC-FE-080). Confirm the concrete
  set with the scm section owner + architecture.md.

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

- **Blocked by:** TASK-PC-FE-168 (shared read-leg decision) — promote to `ready` only after 168 lands; follows the PC-FE-166 (wms) reference impl.
- **Blocks:** TASK-PC-FE-162 (cross-domain rename capstone).
- **Reference:** TASK-PC-FE-156 (ecommerce, DONE).

## Promotion note

Not ready to implement: data-source decision + metric set + spec/AC must be filled in before `backlog → ready`.
