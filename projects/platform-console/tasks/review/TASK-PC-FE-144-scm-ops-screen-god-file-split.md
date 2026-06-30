# TASK-PC-FE-144 — split the `scm-ops/components/ScmOpsScreen.tsx` god-file (container/presentational)

**Status:** review
**Area:** platform-console / console-web · **Refactor only** (Reduce Module Size / Long File — 0 behavior change, 0 contract change)
**Owner:** frontend-engineer
**Task Tags:** refactoring, console-web, scm-ops, behavior-preserving
**Dependency Markers:** continuation of the console-web god-file split series (PC-FE-098 erp-api, PC-FE-102 ledger-api, PC-FE-103 WmsOpsScreen, PC-FE-139 ProductForm). `ScmOpsScreen.tsx` is the direct twin of `WmsOpsScreen.tsx` (a single stateful read-only federated-domain screen) and was 536 lines (TASK-PC-FE-008).

## Goal

Split the single 536-line `features/scm-ops/components/ScmOpsScreen.tsx`
**without changing any externally observable behavior** (every `data-testid`,
ARIA attribute, Tailwind class, Korean label, seed/seeded logic, the S5
`meta.warning` surfacing, and the tolerant snapshot-row normalisation
preserved). Single stateful component → **container / presentational**
(the PC-FE-103 playbook): the container keeps ALL state + the queries; the
four read regions become prop-driven presentational children.

The public surface is the single `ScmOpsScreen` export (page.tsx + the feature
`index.ts` + the unit test import it). It is unchanged — **0 import-site edits**;
the unit test renders `<ScmOpsScreen>` and asserts `data-testid`s, all
byte-identical.

## Scope

### In
- `components/scm-ops-helpers.ts` — the pure helpers: `PoFilterState` /
  `EMPTY_PO_FILTERS`, the tolerant `KNOWN_PO_STATUSES` list, and the
  `snapshotRows` row-shape normaliser. No hooks, no JSX.
- `components/ScmPoTable.tsx` — the procurement PO list region (filter form +
  forbidden / rate-limited / degraded / empty notices + table + per-row
  read-only detail affordance + pagination). Presentational.
- `components/ScmSnapshotTable.tsx` — the inventory-visibility snapshot region
  (S5 warning + table / empty). Presentational.
- `components/ScmSkuBreakdown.tsx` — the per-SKU breakdown region (section S5
  warning + lookup form + error / result-with-its-own-S5 / prompt).
  Presentational.
- `components/ScmStalenessTable.tsx` — the node staleness panel (S5 warning +
  honest STALE/UNREACHABLE table / empty). Presentational.
- `components/ScmOpsScreen.tsx` — the container: owns the PO filter + query
  state, the 3 `useId`s, the SKU input/query state, the PO detail target, the
  `useScm*` queries + the seed/seeded logic; renders the four children + the
  `PoDetailDialog`.

### Out
- No change to `PoDetailDialog`, `S5Warning`, `shared/ui/*`, the hooks
  (`use-scm-ops.ts`), the `api/*` modules, the proxy routes, contracts, or specs.
- The four region children + helpers stay feature-internal (NOT re-exported up
  to the feature `index.ts` — public surface stays exactly `ScmOpsScreen` /
  `S5Warning` / `PoDetailDialog` / `getScmSectionState` + types).
- No test changes (no test reads this file's source; the unit test renders the
  public component and asserts DOM `data-testid`s).

## Acceptance Criteria
- `npx tsc --noEmit` → 0 errors (proves the prop wiring is correct).
- `npx next lint` → 0 warnings/errors.
- `npx vitest run scm` → all green (5 files / 52 tests; ScmOpsScreen suite 11).
- `ScmOpsScreen.tsx` drops 536 → 180 lines; extracted modules are
  `ScmPoTable.tsx` (231) + `ScmSnapshotTable.tsx` (77) + `ScmSkuBreakdown.tsx`
  (138) + `ScmStalenessTable.tsx` (90) + `scm-ops-helpers.ts` (35). Container
  drops 536 → 180 (−66%).
- 0 change to behavior, contracts, specs, route handlers, hooks, pages,
  `data-testid`/ARIA/class strings, label text.

## Related Specs
- `platform/refactoring-policy.md` (Long File / Reduce Module Size,
  behavior-preserving; container/presentational decomposition).
- `specs/services/console-web/architecture.md` (Layered by Feature; the split
  stays feature-internal to `scm-ops/components/`).

## Related Contracts
- `specs/contracts/console-integration-contract.md` § 2.4.6 (scm read surface:
  STRICTLY READ-ONLY; the S5 `meta.warning` is a REQUIRED, surfaced field of
  every inventory-visibility view; freshness honesty for STALE/UNREACHABLE
  nodes).

## Target Service
console-web (platform-console).

## Architecture
Layered by Feature — the split is internal to `scm-ops/components/`; app/
imports only the feature `index.ts` barrel (unchanged public surface). React
Query hooks remain client-only; the container holds all state and the
presentational children are pure functions of props.

## Edge Cases
- The PO filter child receives the `useState` dispatcher
  (`onFiltersChange: Dispatch<SetStateAction<PoFilterState>>`) so the per-field
  functional updates (`(f) => ({ ...f, status })`) are preserved exactly.
- The pagination `onPrevPage` / `onNextPage` keep the exact
  `Math.max(0, page-1)` / `page+1` arithmetic (inline arrow props on the
  container).
- The snapshot child takes the already-normalised `rows` (`snapshotRows()` runs
  in the container `useMemo`, tolerant of the paginated cross-node OR the
  single-node array form).
- The per-SKU child takes `headerWarning` (the snapshot view-model's S5 warning,
  shown above the lookup form) AND surfaces the result's OWN `result.meta.warning`
  — both byte-identical to the original; the inline submit handler
  (`setSkuQuery(skuInput.trim() || null)`) stays in the container.
- `submitPoFilters` keeps the global `React.FormEvent` reference in the
  container (no React import needed — same as the original).

## Failure Scenarios
- Moving any `useState`/query/`useMemo` into a child → behavior change; keep ALL
  state in the container.
- A changed `data-testid` / label string → the scm unit test (or a Playwright
  selector) breaks; move every string verbatim.
- A missing `'use client'` on a presentational child → "hooks in server
  component" style build error (the child renders inside a client tree).
- Stripping / de-emphasising any S5 `meta.warning` (snapshot / per-SKU section /
  per-SKU result / staleness) → § 2.4.6 contract breach; every `<S5Warning>` is
  surfaced verbatim.
- Re-exporting the children / helpers from the feature `index.ts` →
  public-surface widening (keep them feature-internal).

## Definition of Done
- tsc 0 / lint 0 / `vitest run scm` green (5 files / 52 tests).
- `ScmOpsScreen` public export import-stable; 0 test changes; 0 contract/spec
  change.
- Committed + pushed; PR opened by the orchestrator.
