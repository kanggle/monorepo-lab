# TASK-PC-BE-013 — Correct stale "5 domains" claims to 6 across console-bff/web architecture + integration contract

**Status:** done

**Type:** TASK-PC-BE
**Analysis model:** Opus 4.8 / **Recommended impl model:** Sonnet 4.6 (doc/contract corrections, no behavior change)

> Filed from the 2026-07-21 reconciliation audit round-2 re-measurement (origin/main `aa535c22b`). Ground truth re-measured
> from code: console-bff fans out to **6** domains — `IAM, WMS, SCM, FINANCE, ERP, ECOMMERCE`
> (`DomainTarget.java:22-29`; `OperatorOverviewCompositionUseCase.java:38,58-74` `CARD_ORDER` 6-element; ecommerce added by
> TASK-MONO-243). `PROJECT.md:48` already says 6 (cross-check anchor). Several docs still say 5; the audit's line `~2193` claim
> is **REFUTED** (no count stated there) and `§2.4.9.1/.2` are already correct at 6.

---

## Goal

Bring the stale "5 domains" statements in three console spec/contract docs up to the shipped count of 6 (adding `ecommerce`),
so operator-console documentation stops describing a federation that predates TASK-MONO-241/243.

## Scope

**In scope (doc/contract):**

1. **`specs/services/console-bff/architecture.md`** — 5 stale lines: `:41` ("aggregates 5 backend domains (IAM + wms + scm +
   finance + erp)"), `:46`, `:58`, `:74`, `:103`. Update each to 6 domains including `ecommerce`.
2. **`specs/services/console-web/architecture.md:33`** — "iam · wms · scm + future erp · finance" → the 6 shipped domains
   `iam · wms · scm · erp · finance · ecommerce`; also drop the stale "future" label for erp/finance (the same file's status
   block `:15-19` already documents them as shipped).
3. **`console-integration-contract.md` § 2.4.9 credential-dispatch table (~`:2238-2244`)** — currently 5 rows (IAM, wms, scm,
   finance, erp), missing `ecommerce`, even though § 2.4.9.1 (`:2360-2412`) and § 2.4.9.2 (`:2644-2651`) in the same file
   already say 6. Add the `ecommerce` row (routed through the ecommerce gateway; credential = IAM OIDC access token; source =
   inbound Authorization), matching the § 2.4.9.1 producer table.

**Out of scope:** line `~2193` (no count — REFUTED); `§2.4.9.1/.2` (already 6); `PROJECT.md:48` (already correct); historical
task-record files and the pre-ecommerce-era tests (correct as point-in-time history). Low-priority stale *code comments*
(`console-web/e2e-smoke/root-redirect.spec.ts:5`) may be swept opportunistically but assert nothing and are not required here.

## Acceptance Criteria

- **AC-0 (gate — re-measure; code wins)** — Re-confirm from code that the count is still 6 (`DomainTarget` enum + `CARD_ORDER`)
  at current `main`, and that each listed doc location still says 5. Line numbers will shift — locate by content.
- **AC-1** — All 5 lines in `console-bff/architecture.md`, the `console-web/architecture.md:33` line, and the § 2.4.9 table row
  read 6 domains including `ecommerce`; no "future erp/finance" phrasing remains.
- **AC-2** — The § 2.4.9 credential table (a doc-declared "HARD INVARIANT, byte-verbatim" dispatch table) is internally
  consistent with § 2.4.9.1's 6-leg producer table.
- **AC-3** — Census: grep console-platform docs for `5 도메인` / `5 domains` / `5 backend domains` / "five domains" and
  correct every *living-spec* hit or explicitly mark it as historical. Sanity-check the grep against a known-positive
  (`console-bff/architecture.md:41`) so an empty result proves absence. **The three named docs are the starting point, not the population.**
- **AC-4** — No behavior change; docs/contract only.

## Related Specs
- `projects/platform-console/specs/services/console-bff/architecture.md`
- `projects/platform-console/specs/services/console-web/architecture.md`
- `projects/platform-console/specs/.../console-integration-contract.md` § 2.4.9

## Related Contracts
- `console-integration-contract.md` § 2.4.9 credential-dispatch table is the contract surface being reconciled.

## Edge Cases
- The § 2.4.9 table is labeled a byte-verbatim hard invariant — add the row in the exact column format of the existing rows,
  cross-checked against § 2.4.9.1 (do not paraphrase).
- console-web/architecture.md:33 has two defects in one line (count = 5 AND erp/finance mislabeled "future") — fix both.

## Failure Scenarios
- **F1 — fixing only the 3 audit-named spots and missing sibling lines** (bff has 5 separate stale lines, not 1). Guarded by AC-3's census.
- **F2 — "correcting" §2.4.9.1/.2 which are already right.** Guarded by Scope/out-of-scope.
