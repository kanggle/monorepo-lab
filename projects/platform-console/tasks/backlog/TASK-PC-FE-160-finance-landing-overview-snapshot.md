# TASK-PC-FE-160 — finance landing **operator overview snapshot**

**Status:** backlog
**Area:** platform-console / console-web · **Route:** `app/(console)/finance/page.tsx`
**Reference impl (template):** TASK-PC-FE-156 (ecommerce overview snapshot) — mirror the pattern, adapt the data source.

## Goal

Elevate the `/finance` section landing into an **operator overview snapshot** — key counts + a distribution + recent
activity — matching the ecommerce landing (PC-FE-156). One of the 4 per-domain overview tasks that must land
**before** the cross-domain "운영 → 개요" rename (TASK-PC-FE-162).

## Read-leg decision (RESOLVED — TASK-PC-FE-168) — ⚠️ finance is the DEGENERATE case

- **Data source / read leg — RESOLVED: console-web DIRECT (same as all 4), BUT finance has no count fan-out.** The
  "console-bff READ leg" framing was a premise error — finance already reaches its producer server-side via
  `getDomainFacingToken()` (§ 2.4.7 direct client), same model as ecommerce. **However**, the PC-FE-168 review found
  finance v1 has **NO list/search GET** (`getFinanceSectionState` is account-id-driven — account/balances/
  transactions are all keyed by an operator-supplied `accountId`; there is no "list accounts" endpoint). So there is
  **no `totalElements` to fan out over** and — per the money-sensitivity rule — **NO synthetic ₩/balance
  aggregation** is permitted either. **A wms-style count overview does NOT exist for finance.**
- **⚠️ Scope re-judgment required before ready (not just metric-set finalization).** Options for 160: (a) **drop/park
  160** — finance keeps its account-lookup landing (no overview snapshot); (b) surface only a minimal
  non-count band (e.g. the account-lookup + a recent-transactions glance *once an account is supplied* — still no
  aggregate). This needs a product decision; do NOT force a count grid. If (a), 160 becomes a no-op and PC-FE-162's
  "all 4 done" gate should treat finance as N/A.

## Scope (to be finalized)

- Add a finance overview state fan-out + presentational component (mirror PC-FE-156), wire into `finance/page.tsx`.
  Per-cell degrade + 401→whole-session redirect. Keep the existing aggregate finance domain-health card.

## Acceptance Criteria (draft — finalize before ready)

- Key counts + a distribution + recent activity render on `/finance` (eligible path); no synthetic money aggregation.
- Reuses existing consumed endpoints; no producer `/summary`, no new producer retrofit.
- Per-cell degrade cell-local; 401 → `redirect('/login')`; eligibility/degraded branches unchanged.
- Spec-first: `console-integration-contract.md` + `console-web/architecture.md` finance section updated.
- `pnpm lint` + `tsc` + `vitest` green.

## Dependencies

- **Blocked by:** TASK-PC-FE-168 (shared read-leg decision) — promote to `ready` only after 168 lands; follows the PC-FE-166 (wms) reference impl.
- **Blocks:** TASK-PC-FE-162 (cross-domain rename capstone).
- **Reference:** TASK-PC-FE-156 (ecommerce, DONE).

## Promotion note

Not ready to implement: data-source decision + metric set + spec/AC must be filled in before `backlog → ready`.
