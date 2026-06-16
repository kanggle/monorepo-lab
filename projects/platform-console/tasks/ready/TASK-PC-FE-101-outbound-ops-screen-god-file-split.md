# TASK-PC-FE-101 — split the `wms-outbound-ops/components/OutboundOpsScreen.tsx` god-file (container/presentational)

**Status:** ready
**Area:** platform-console / console-web · **Refactor only** (Reduce Module Size / Long File — 0 behavior change, 0 contract change)
**Parent:** fourth and final in the erp-ops/wms-outbound-ops god-file split series (after TASK-PC-FE-098 `erp-api.ts`, 099 `use-erp-ops.ts`, 100 `ApprovalScreen.tsx`). `OutboundOpsScreen.tsx` was a single ~755-line stateful component (TASK-PC-FE-057 + 085 + the TMS-retry follow-up).

## Goal

Split the single ~755-line `features/wms-outbound-ops/components/OutboundOpsScreen.tsx`
**without changing any externally observable behavior** (every `data-testid`,
ARIA attribute, Tailwind class, Korean label, Idempotency-Key generation,
status/saga gating, 409-CONFLICT refetch-and-prompt flow preserved). Unlike the
ApprovalScreen split (three independent components), this is ONE big stateful
component, so the split follows the **container / presentational** pattern: the
container keeps ALL state, mutations, and gating; the two large JSX regions
become prop-driven presentational children. Pure structural move — no
logic/JSX edited.

The public surface is the single `OutboundOpsScreen` export (page.tsx + the
feature `index.ts` + the unit test import it). It is unchanged — **0 import-site
edits**; the unit test renders `<OutboundOpsScreen>` and asserts `data-testid`s,
all of which stay byte-identical.

### Split layout
- `components/outbound-ops-helpers.ts` — the pure helpers: `ActionKind` type,
  `STATUS_FILTER_OPTIONS`, `ACTION_COPY` (confirm-dialog copy), and the
  `cancelErrorMessage` / `retryTmsErrorMessage` producer-error → inline-message
  maps. No hooks, no JSX.
- `components/OutboundOrdersTable.tsx` — the orders-table region (status filter
  form + forbidden/degraded/empty notices + table + pagination nav).
  Presentational; all state/handlers arrive via props.
- `components/OutboundOrderDrill.tsx` — the order-drill region (detail header +
  lines + saga + the confirm-gated pick/pack/ship + cancel + TMS-retry
  actions). Presentational; the container passes the derived enabled flags +
  the `open*` callbacks via props (the container renders it only when a drill
  order is selected).
- `components/OutboundOpsScreen.tsx` — the container: owns the 8 state slices,
  the 7 mutations, the 6 `useMemo` gating flags, the 5 `confirm*`/`open*`
  handlers, and the 3 dialog mounts (`OutboundActionDialog` ×2 +
  `OutboundCancelDialog`); renders the two presentational children.

## Hard constraints (behavior preservation)
- **Public export set unchanged** — `OutboundOpsScreen` / `OutboundOpsScreenProps`
  remain importable from `./OutboundOpsScreen`. The two children + helpers stay
  feature-internal (NOT re-exported up to the feature `index.ts`).
- **All state stays in the container** — the children are pure functions of
  props (no `useState`/mutation/`useMemo` moved out). The 409-CONFLICT
  `drill.refetch()` + prompt flow, the fresh-per-attempt `crypto.randomUUID()`
  Idempotency-Key, and the seed/seeded logic are untouched in the container.
- Every `data-testid` / `aria-*` / class string / Korean label / status-saga
  gate (`canPick`/`canPack`/`canShip`/`canCancel`/`canRetryTms`/`cancelNeedsAdmin`)
  moved byte-for-byte.
- `'use client'` preserved on the container + both presentational children
  (`outbound-ops-helpers.ts` is pure TS — no directive needed).
- No change to `OutboundActionDialog` / `OutboundCancelDialog`, `shared/ui/*`,
  the hooks, the `api/types` module, the proxy routes, contracts, or specs.
- **Production code only — 0 test changes.** (The unit test renders the public
  component and asserts DOM `data-testid`s; the DOM is byte-identical.)

## Procedure
1. Baseline (green at authoring): `npx vitest run tests/unit/OutboundOpsScreen.test.tsx` → 30 pass.
2. Create `outbound-ops-helpers.ts` / `OutboundOrdersTable.tsx` /
   `OutboundOrderDrill.tsx` by moving the verbatim blocks; trim the container to
   state + handlers + the two child renders + the 3 dialogs.
3. `tsc --noEmit` (0) + `next lint` (0) + `npx vitest run` (full) green.

## Acceptance Criteria
- `npx tsc --noEmit` → 0 errors (proves the prop wiring is correct).
- `npx next lint` → 0 warnings/errors.
- `npx vitest run` → all green, **same test count as baseline** (162 files /
  1943 tests; outbound suite 30).
- `OutboundOpsScreen.tsx` drops ~755 → ~387 lines; the extracted modules are
  `OutboundOrderDrill.tsx` (~269) + `OutboundOrdersTable.tsx` (~192) +
  `outbound-ops-helpers.ts` (~75). Container drops 755 → 387 (−49%).
- 0 change to behavior, contracts, specs, route handlers, hooks, pages,
  `data-testid`/ARIA/class strings, label text.

## Related Specs / Contracts
- `platform/refactoring-policy.md` (Long File / Reduce Module Size,
  behavior-preserving; container/presentational decomposition).
- `specs/services/console-web/architecture.md` (Layered by Feature; the split
  stays feature-internal to `wms-outbound-ops/components/`).
- `specs/contracts/console-integration-contract.md` (wms outbound operator leg:
  confirm-gated lifecycle, async cancel saga, TMS retry).

## Edge Cases
- The drill child receives the derived `OutboundOrderDetail | null` / `OutboundSaga | null`
  + the `degraded || !detail || !saga` branch — the child re-checks `!detail || !saga`
  so TypeScript narrows them non-null in the success body (identical to the
  original inline conditional).
- `onDrill={setDrillOrderId}` / `onStatusFilterChange={setStatusFilter}` pass the
  `useState` dispatchers directly — `string` is assignable to their
  `SetStateAction` param, so the narrower `(v: string) => void` prop type holds.
- The pagination `onPrevPage` / `onNextPage` keep the exact
  `Math.max(0, page-1)` / `page+1` arithmetic (moved to inline arrow props on
  the container, not into the child).
- `submitStatusFilter(e: React.FormEvent)` keeps the global `React` namespace
  reference (no React import needed — same as the original).

## Failure Scenarios
- Moving any `useState`/mutation/`useMemo` into a child → behavior change (state
  identity / re-render timing); keep ALL state in the container.
- A changed `data-testid` / label string → the outbound unit test (or a
  Playwright selector) breaks; move every string verbatim.
- A missing `'use client'` on a presentational child → "hooks in server
  component" style build error (the child renders inside a client tree).
- Re-exporting the children / helpers from the feature `index.ts` →
  public-surface widening (keep them feature-internal).
