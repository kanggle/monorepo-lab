# TASK-PC-FE-160 — finance landing **operator overview snapshot**

**Status:** backlog
**Area:** platform-console / console-web · **Route:** `app/(console)/finance/page.tsx`
**Reference impl (template):** TASK-PC-FE-156 (ecommerce overview snapshot) — mirror the pattern, adapt the data source.

## Goal

Elevate the `/finance` section landing into an **operator overview snapshot** — key counts + a distribution + recent
activity — matching the ecommerce landing (PC-FE-156). One of the 4 per-domain overview tasks that must land
**before** the cross-domain "운영 → 개요" rename (TASK-PC-FE-162).

## Open design decision (resolve at backlog → ready)

- **Data source / read leg.** finance is a **console-bff READ leg** domain (§2.4.9.1/§2.4.9.2). The `finance/page.tsx`
  landing already surfaces some `count`-shaped data (accounts/balances/transactions read-only) — inventory what is
  already fetched and reuse it before adding any new read. Prefer consumed endpoints' totals (ADR-MONO-017 D3.B —
  no producer `/summary`).
- **Which metrics** for finance (e.g. account count, ledger/transaction volume, reconciliation-pending count +
  status distribution + recent transactions). Care: finance is money-sensitive — counts/volumes only, NO synthetic
  balance/₩ aggregation on the overview (mirror the operator-overview "no synthetic aggregation" discipline).

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

- **Blocks:** TASK-PC-FE-162 (cross-domain rename capstone).
- **Reference:** TASK-PC-FE-156 (ecommerce, DONE).

## Promotion note

Not ready to implement: data-source decision + metric set + spec/AC must be filled in before `backlog → ready`.
