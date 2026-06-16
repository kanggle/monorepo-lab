# TASK-PC-FE-103 — split the `wms-ops/components/WmsOpsScreen.tsx` god-file (container/presentational)

**Status:** ready
**Area:** platform-console / console-web · **Refactor only** (Reduce Module Size / Long File — 0 behavior change, 0 contract change)
**Parent:** continuation of the console-web god-file split series (098 erp-api, 099 use-erp-ops, 100 ApprovalScreen, 101 OutboundOpsScreen, 102 ledger-api). `WmsOpsScreen.tsx` is the direct twin of `OutboundOpsScreen.tsx` (a single stateful wms screen) and was ~667 lines (TASK-PC-FE-007 + the shipments + alert-ack slices).

## Goal

Split the single ~667-line `features/wms-ops/components/WmsOpsScreen.tsx`
**without changing any externally observable behavior** (every `data-testid`,
ARIA attribute, Tailwind class, Korean label, seed/seeded logic, the
fresh-per-attempt `crypto.randomUUID()` ack key, and the eventual-consistency
lag banner preserved). Single stateful component → **container / presentational**
(the PC-FE-101 playbook): the container keeps ALL state + the mutation + the lag
banner; the three read regions become prop-driven presentational children.

The public surface is the single `WmsOpsScreen` export (page.tsx + the feature
`index.ts` + two tests import it). It is unchanged — **0 import-site edits**; the
unit tests render `<WmsOpsScreen>` and assert `data-testid`s, all byte-identical.

### Split layout
- `components/wms-ops-helpers.ts` — the pure helpers: `InvFilterState` /
  `EMPTY_INV_FILTERS`, `ShipFilterState` / `EMPTY_SHIP_FILTERS`, and the
  `alertLabel` formatter. No hooks, no JSX.
- `components/WmsInventoryTable.tsx` — the inventory-snapshot region (filter
  form + forbidden/degraded/empty notices + table + pagination). Presentational.
- `components/WmsShipmentsTable.tsx` — the shipments (택배/출고) region (filter
  form + notices + table + pagination). Presentational, read-only.
- `components/WmsAlertsTable.tsx` — the alerts region (table + per-row
  confirm-gated acknowledge affordance; the confirm dialog stays in the
  container). Presentational.
- `components/WmsOpsScreen.tsx` — the container: owns the inv/ship filter +
  query state, the 5 `useId`s, the ack target/error state, the seed/seeded
  logic, the `useWms*` queries + `useAcknowledgeAlert` mutation, the lag banner,
  and the `AcknowledgeAlertDialog` mount; renders the three children.

## Hard constraints (behavior preservation)
- **Public export set unchanged** — `WmsOpsScreen` / `WmsOpsScreenProps` remain
  importable from `./WmsOpsScreen`. The three children + helpers stay
  feature-internal (NOT re-exported up to the feature `index.ts`).
- **All state stays in the container** — the children are pure functions of
  props (no `useState`/query/mutation/`useMemo` moved out). The filter-submit
  handlers, the pagination arithmetic (`Math.max(0, page-1)` / `page+1`), and
  the ack flow are untouched in the container.
- Every `data-testid` / `aria-*` / class string / Korean label moved
  byte-for-byte.
- `'use client'` preserved on the container + all three presentational children
  (`wms-ops-helpers.ts` is pure TS — no directive needed).
- No change to `AcknowledgeAlertDialog`, `shared/ui/*`, the hooks, the
  `api/types` module, the proxy routes, contracts, or specs.
- **Production code only — 0 test changes.** (No test reads this file's source;
  the unit tests render the public component and assert DOM `data-testid`s.)

## Procedure
1. Baseline (green at authoring): `npx vitest run tests/unit/WmsOpsScreen.test.tsx tests/unit/wms-nav.test.tsx` → 25 pass.
2. Create `wms-ops-helpers.ts` / `WmsInventoryTable.tsx` / `WmsShipmentsTable.tsx`
   / `WmsAlertsTable.tsx` by moving the verbatim blocks; trim the container to
   state + handlers + the three child renders + the dialog.
3. `tsc --noEmit` (0) + `next lint` (0) + `npx vitest run` (full) green.

## Acceptance Criteria
- `npx tsc --noEmit` → 0 errors (proves the prop wiring is correct).
- `npx next lint` → 0 warnings/errors.
- `npx vitest run` → all green, **same test count as baseline** (162 files /
  1943 tests; wms suite 25).
- `WmsOpsScreen.tsx` drops ~667 → ~281 lines; the extracted modules are
  `WmsInventoryTable.tsx` (~239) + `WmsShipmentsTable.tsx` (~208) +
  `WmsAlertsTable.tsx` (~99) + `wms-ops-helpers.ts` (~33). Container drops
  667 → 281 (−58%).
- 0 change to behavior, contracts, specs, route handlers, hooks, pages,
  `data-testid`/ARIA/class strings, label text.

## Related Specs / Contracts
- `platform/refactoring-policy.md` (Long File / Reduce Module Size,
  behavior-preserving; container/presentational decomposition).
- `specs/services/console-web/architecture.md` (Layered by Feature; the split
  stays feature-internal to `wms-ops/components/`).
- `specs/contracts/console-integration-contract.md` § 2.4.5 (wms read surface:
  eventual-consistency lag honesty, confirm-gated reason-free acknowledge).

## Edge Cases
- The inventory + shipment filter children receive the `useState` dispatcher
  (`onFiltersChange: Dispatch<SetStateAction<…>>`) so the per-field functional
  updates (`(f) => ({ ...f, warehouseId })`) are preserved exactly.
- The alerts child takes `rows={alertsData.content}` (not the whole page — the
  alerts list is not paginated in this slice) + the `onAck` open callback; the
  `alertLabel` formatting + the confirm dialog stay in the container.
- The pagination `onPrevPage` / `onNextPage` keep the exact `Math.max(0, page-1)`
  / `page+1` arithmetic (inline arrow props on the container).
- `submitInvFilters` / `submitShipFilters` keep the global `React.FormEvent`
  reference in the container (no React import needed — same as the original).

## Failure Scenarios
- Moving any `useState`/query/mutation/`useMemo` into a child → behavior change;
  keep ALL state in the container.
- A changed `data-testid` / label string → the wms unit test (or a Playwright
  selector) breaks; move every string verbatim.
- A missing `'use client'` on a presentational child → "hooks in server
  component" style build error (the child renders inside a client tree).
- Re-exporting the children / helpers from the feature `index.ts` →
  public-surface widening (keep them feature-internal).
