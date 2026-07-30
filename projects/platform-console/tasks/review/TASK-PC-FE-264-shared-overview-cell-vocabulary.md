# Task ID

TASK-PC-FE-264

# Title

console-web shared overview-cell vocabulary extraction — dedup `cellPlaceholder`/status-dot maps across 5 domains

# Status

review

# Owner

frontend

# Task Tags

- code

---

# Required Sections (must exist)

- Goal
- Scope
- Acceptance Criteria
- Related Specs
- Related Contracts
- Edge Cases
- Failure Scenarios

If any section is missing or incomplete, this task must not be implemented.

---

# Goal

Found by the same fresh `console-web` naming-convention re-scan (2026-07-30) as TASK-PC-FE-263, finding #1 — the highest-value item. `cellPlaceholder(status)` (`status === 'forbidden' ? '권한 없음' : '점검 필요'`), and the matching per-cell status-dot/status-label maps (`{ ok: 'bg-green-500', degraded: 'bg-red-500', forbidden: 'bg-muted-foreground/40' }` / `{ ok: '정상', degraded: '점검 필요', forbidden: '권한 없음' }`), are independently reimplemented **verbatim** in all 5 domain-overview features: `ecommerce-ops`, `iam-overview`, `erp-ops`, `scm-ops`, `wms-ops`. Per `platform/refactoring-policy.md` § Prioritization duplication outranks naming — this is a **Reduce Duplication** task (same framing as TASK-PC-FE-262/263), not a rename. Direct precedent: TASK-PC-FE-263 (`shared/ui/RetryButton`/`DegradeBanner`, same conversation, same day).

Each domain's own `api/overview-state.ts` independently declares `export type CellStatus = 'ok' | 'forbidden' | 'degraded';` — structurally identical across all 5, but not literally the same type (5 separate declarations). This task does NOT unify that type declaration (would ripple into every domain's `AreaCount`/state-shape types, a much larger blast radius than the vocabulary duplication it's not needed for) — the new shared functions/consts are typed against a new canonical `OverviewCellStatus` union in the shared module; each domain's own `CellStatus` is structurally assignable to it (TypeScript structural typing), so no domain file needs to import or alias the shared type.

The 5 domains organize the duplicated code differently today (not itself a defect to "fix" beyond the delegation below):
- `wms-ops`: dedicated file `components/wms-overview-cell.ts`, both maps exported (`SERVICE_STATUS_DOT`/`SERVICE_STATUS_LABEL`), 4 importers (`WmsOperationsScreen.tsx`, `WmsOverviewCountTile.tsx`, `WmsRecentShipments.tsx`, `WmsRecentAdjustments.tsx`).
- `iam-overview`: `components/overview-labels.ts` (shared with unrelated `AUDIT_SOURCE_LABEL`), maps exported as `STATUS_DOT`/`STATUS_LABEL`, 2 importers (`IamOverviewAuditCard.tsx`, `IamOverviewPrimitives.tsx`).
- `ecommerce-ops`: `cellPlaceholder` lives in `components/overview-labels.ts` (shared with unrelated `ORDER_STATUS_LABELS`), exported, 2 importers (`EcommerceCountCard.tsx`, `EcommerceRecentPanels.tsx`); the dot/label maps are **module-private consts inside `EcommerceCountCard.tsx` itself** (not exported, not in `overview-labels.ts` — the most fragmented of the 5).
- `erp-ops`: all three (function + 2 maps) are module-private inside `components/ErpOverviewScreen.tsx`, no separate export, single internal consumer (`CountTile` in the same file).
- `scm-ops`: same shape as `erp-ops` — module-private inside `components/ScmOverview.tsx`.

---

# Scope

## In Scope

1. **New `shared/lib/overview-cell.ts`** — pure module, no JSX, no feature coupling. Exports:
   - `export type OverviewCellStatus = 'ok' | 'forbidden' | 'degraded';`
   - `export function overviewCellPlaceholder(status: OverviewCellStatus): string` — body verbatim from the 5 originals.
   - `export const OVERVIEW_STATUS_DOT: Record<OverviewCellStatus, string>` — verbatim values.
   - `export const OVERVIEW_STATUS_LABEL: Record<OverviewCellStatus, string>` — verbatim values.

2. **`features/wms-ops/components/wms-overview-cell.ts` becomes a re-export shim** — `export { overviewCellPlaceholder as cellPlaceholder, OVERVIEW_STATUS_DOT as SERVICE_STATUS_DOT, OVERVIEW_STATUS_LABEL as SERVICE_STATUS_LABEL } from '@/shared/lib/overview-cell';`. All 4 existing importers keep importing from `'./wms-overview-cell'` with the same names — zero call-site changes.

3. **`features/iam-overview/components/overview-labels.ts`** — replace the local `cellPlaceholder`/`STATUS_DOT`/`STATUS_LABEL` definitions with an aliased re-export from `@/shared/lib/overview-cell` (same names: `cellPlaceholder`, `STATUS_DOT`, `STATUS_LABEL`); keep the unrelated `AUDIT_SOURCE_LABEL` export untouched in the same file. Both existing importers (`IamOverviewAuditCard.tsx`, `IamOverviewPrimitives.tsx`) unchanged.

4. **`features/ecommerce-ops/components/overview-labels.ts`** — replace the local `cellPlaceholder` definition with an aliased re-export from `@/shared/lib/overview-cell`; keep the unrelated `ORDER_STATUS_LABELS` export untouched. Both existing importers (`EcommerceCountCard.tsx`, `EcommerceRecentPanels.tsx`) unchanged.

5. **`features/ecommerce-ops/components/EcommerceCountCard.tsx`** — replace the module-private `SERVICE_STATUS_DOT`/`SERVICE_STATUS_LABEL` const declarations with an import of `OVERVIEW_STATUS_DOT as SERVICE_STATUS_DOT, OVERVIEW_STATUS_LABEL as SERVICE_STATUS_LABEL` from `@/shared/lib/overview-cell`; drop the now-unused `CellStatus` type import from `../api/overview-state` if nothing else in the file uses it (verify via lint).

6. **`features/erp-ops/components/ErpOverviewScreen.tsx`** — replace the module-private `cellPlaceholder`/`SERVICE_STATUS_DOT`/`SERVICE_STATUS_LABEL` definitions with an import from `@/shared/lib/overview-cell` (aliased to the same local names). Keep the `CellStatus` type import from `../api/overview-state` if still used elsewhere in the file (it is — `ErpAreaCount`/tile prop typing); verify via lint whether it remains needed.

7. **`features/scm-ops/components/ScmOverview.tsx`** — same treatment as `erp-ops`: replace the module-private `cellPlaceholder`/`SERVICE_STATUS_DOT`/`SERVICE_STATUS_LABEL` definitions with an aliased import from `@/shared/lib/overview-cell`.

## Out of Scope

- Unifying the 5 domains' own `CellStatus` type declarations in each `api/overview-state.ts` into one shared type — structurally identical today but a much larger blast radius (touches every `AreaCount`/state-shape type per domain) than the vocabulary duplication this task targets; a separate future task if still wanted.
- Any file reorganization beyond the minimum needed to delegate (e.g. NOT extracting `erp-ops`'s/`scm-ops`'s inline definitions into their own dedicated files before delegating — that would be an unrequested extra move on top of the dedup).
- The other 3 findings from the same fresh scan (`accountStatusTone` IAM/Finance name collision; `AccountDetail`/`AccountLookup` Finance/Ledger name collision; `ecommerce-ops/api/types.ts` internal products/promotions split) — tracked as separate future candidates.
- Any behavior, UI, testid, or API contract change. Every rendered string, CSS class, and testid must be byte-identical before/after.

---

# Acceptance Criteria

- [ ] `shared/lib/overview-cell.ts` exists with `OverviewCellStatus`, `overviewCellPlaceholder`, `OVERVIEW_STATUS_DOT`, `OVERVIEW_STATUS_LABEL`.
- [ ] All 5 domain files (`wms-overview-cell.ts`, `iam-overview/overview-labels.ts`, `ecommerce-ops/overview-labels.ts`, `ecommerce-ops/EcommerceCountCard.tsx`, `erp-ops/ErpOverviewScreen.tsx`, `scm-ops/ScmOverview.tsx`) delegate to the shared module instead of defining their own copy.
- [ ] Every pre-existing exported/local name (`cellPlaceholder`, `SERVICE_STATUS_DOT`, `SERVICE_STATUS_LABEL`, `STATUS_DOT`, `STATUS_LABEL`) is preserved exactly where it existed before — grep-verify the full importer list for each name before and after, diff = zero drift (per TASK-PC-FE-263's verification method).
- [ ] `tsc --noEmit` passes with zero new errors.
- [ ] `pnpm lint` passes with zero new errors (in particular: no unused-import warnings left behind by the delegation).
- [ ] Full `console-web` unit test suite (vitest) passes unchanged — no test assertions modified.
- [ ] Net LOC decreases (duplication removed, not just moved).

---

# Related Specs

> **Before reading Related Specs**: Follow `platform/entrypoint.md` Step 0 — read `PROJECT.md`, then load `rules/common.md` plus any `rules/domains/<domain>.md` and `rules/traits/<trait>.md` matching the declared classification (platform-console: domain=saas, traits=[multi-tenant, integration-heavy, audit-heavy]). Unknown tags are a Hard Stop per `CLAUDE.md`.

- `platform/refactoring-policy.md` § Allowed Refactoring Categories → **Reduce Duplication** (Medium risk) and § Prioritization (duplication outranks naming).
- `projects/platform-console/docs/conventions/frontend-ui.md` — `StatusBadge`/`DetailHeader`/`ConfirmDialog`/`RetryButton`+`DegradeBanner` shared-component promotion precedent (delegate-internally-preserve-every-call-site pattern; this task is the `shared/lib` analogue of those `shared/ui` promotions — a pure-function/const module, no JSX).

# Related Skills

- `.claude/skills/frontend/architecture/layered-by-feature/SKILL.md`

---

# Related Contracts

None — no API or event contract touched; pure client-side presentational-vocabulary dedup.

---

# Target App

- `apps/console-web`

---

# Implementation Notes

- Grep-verify the full importer list for `cellPlaceholder`, `SERVICE_STATUS_DOT`, `SERVICE_STATUS_LABEL`, `STATUS_DOT`, `STATUS_LABEL` across all 5 `features/<domain>` directories BEFORE starting and again AFTER finishing, diffing the two lists to confirm the only delta is the new `shared/lib/overview-cell.ts` file itself.
- Do NOT rename any domain-local export (e.g. do not rename `wms-ops`'s `SERVICE_STATUS_DOT` to match `iam-overview`'s `STATUS_DOT`, or vice versa) — this task's scope is duplication removal via delegation, not naming standardization across domains. A follow-on naming-consistency pass, if still wanted, is separate.
- `erp-ops`/`scm-ops` currently have NO separate labels/helpers file — do not create one as part of this delegation; import `@/shared/lib/overview-cell` directly into `ErpOverviewScreen.tsx`/`ScmOverview.tsx` and keep the local aliased const/function names at the top of those files (minimal-diff delegation, matching the "do not add unrequested structure" scope note above).

---

# Edge Cases

- `EcommerceCountCard.tsx`'s local `CellStatus` type import may become unused after this change (it was only used to type the two now-removed local `Record<CellStatus, string>` declarations) — remove it if `pnpm lint` flags it unused; but `AreaCount` (the other type import on the same line) is still used, so edit the import list surgically, do not remove the whole import statement.
- `ErpOverviewScreen.tsx`/`ScmOverview.tsx`'s `CellStatus` type import is still needed elsewhere in each file (state/prop typing) — do NOT remove those.

---

# Failure Scenarios

- `tsc --noEmit` fails after delegation → fix the import/alias wiring, do not leave a duplicated definition in place as a fallback.
- `pnpm lint` flags an unused import in a domain file post-delegation → remove it; in-scope for a dedup task to clean up its own now-dead imports.
- Vitest suite fails post-change → indicates a missed export/alias, not a false positive; revert and re-diagnose rather than modifying test assertions (per `platform/refactoring-policy.md` Prohibited: "Refactoring production code and test code in the same change").

---

# Test Requirements

- No new tests required — this is a pure duplication-reduction with zero behavior change; existing test coverage is the verification mechanism and must pass unmodified.

---

# Definition of Done

- [ ] All 7 scope items completed
- [ ] `tsc --noEmit` clean
- [ ] `pnpm lint` clean
- [ ] Full `console-web` vitest suite passing, zero test assertions modified
- [ ] Ready for review
