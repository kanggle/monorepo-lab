# TASK-PC-FE-168 — console domain-overview **shared read-leg decision** (gating design task)

**Status:** backlog
**Area:** platform-console / console-web · **Type:** design decision (spec/architecture note — no feature code)
**Lead task of** the console domain-landing overview series (gates PC-FE-166/167/160/161; capstone PC-FE-162).

## Goal

Decide, **once and for all four bff-domains together**, how the wms / scm / finance / erp landing overview
snapshots (PC-FE-166/167/160/161) fetch their data — BEFORE any of them is implemented. All four share the
identical open question, so resolving it per-task would risk four divergent patterns and rework. The output is a
recorded decision (a short architecture note / ADR-lite in `console-web/architecture.md` + `console-integration-contract.md`),
not feature code.

## Why this is a separate lead task (the sequencing this records)

ecommerce (PC-FE-156, DONE) used a **console-web DIRECT fan-out** because it is a §2.4.10 direct-model domain. The
other four are **console-bff READ-leg domains** (§2.4.9.1/§2.4.9.2) — ecommerce's pattern does NOT transfer
verbatim. The read-leg approach must be chosen deliberately and uniformly.

## The decision to make (options)

For the 4 bff-domains, choose (and document the rationale + any per-domain exceptions):

1. **Reuse existing console-side section list reads** — the landing fans out over the domain's already-consumed
   list endpoints' `totalElements` (mirror the ecommerce shape but via each domain's existing console read path).
2. **Extend the existing console-bff read leg** — add overview counts to the domain's current bff read composition
   (server-side fan-in, per §2.4.9.1 pattern).
3. **New per-domain console-bff overview leg** — a dedicated composition endpoint per domain.

Constraints (fixed, not up for decision):
- **ADR-MONO-017 D3.B** — no producer `/summary` aggregation endpoint; counts derive from existing list
  `totalElements`. No producer retrofit.
- Per-cell degrade cell-local; a `401` in any leg → whole-session `redirect('/login')` (mirror PC-FE-156).
- Read-only, no auto-refetch.

## Deliverables

- A decision recorded in `console-web/architecture.md` (+ `console-integration-contract.md` if a new consumption
  pattern is introduced) covering: chosen read-leg approach, credential model per domain, the count / status /
  recent shapes, and any per-domain deviation (e.g. finance = no synthetic ₩ aggregation; erp = thin surface).
- Confirmation of **wms (PC-FE-166) as the first reference implementation** for the bff-domain pattern (the
  analogue of ecommerce PC-FE-156 for the direct-model).

## Acceptance Criteria (draft — finalize before ready)

- One read-leg approach chosen for all 4 bff-domains, rationale + exceptions documented in the spec.
- 166/167/160/161 can be promoted `backlog → ready` by filling their "Open design decision" from this decision.
- No feature code; docs/spec only.

## Dependencies

- **Blocks:** PC-FE-166 (wms), 167 (scm), 160 (finance), 161 (erp) — each promotes to `ready` only after this
  decision lands. Transitively gates the PC-FE-162 rename capstone.
- **Reference:** PC-FE-156 (ecommerce, DONE) — the direct-model reference; this task defines the bff-model analogue.

## Execution order (series)

1. **PC-FE-168 (this task)** — shared read-leg decision.
2. **PC-FE-166 (wms)** — first bff-domain reference implementation.
3. **PC-FE-167 (scm) / 160 (finance) / 161 (erp)** — follow the wms template.
4. **PC-FE-162** — cross-domain "운영 → 개요" rename capstone (after all 4 done).
