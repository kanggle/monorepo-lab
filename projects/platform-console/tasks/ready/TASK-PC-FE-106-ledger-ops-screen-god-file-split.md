# TASK-PC-FE-106 — split the `ledger-ops/components/LedgerOpsScreen.tsx` god-file (state-hook + tab-strip extraction)

**Status:** ready
**Area:** platform-console / console-web · **Refactor only** (Reduce Module Size / Long File — 0 behavior change, 0 contract change)
**Parent:** continuation of the console-web god-file split series (098 erp-api, 099 use-erp-ops, 100 ApprovalScreen, 101 OutboundOpsScreen, 102 ledger-api, 103 WmsOpsScreen, 105 OperatorsScreen). `LedgerOpsScreen.tsx` is the finance-ledger tabbed shell (~622 lines after PC-FE-104 added the FX-rate-history drill).

## Goal

Slim the ~622-line `features/ledger-ops/components/LedgerOpsScreen.tsx`
**without changing any externally observable behavior** (every `data-testid`,
ARIA `tablist`/`tabpanel` wiring, roving keyboard nav, query gating on the
active tab, seed reconciliation, F5 string-money rendering preserved). This is a
7-tab shell already decomposed into ~17 view sub-components; the remaining bulk
is (a) ~160 lines of per-tab state/query/derived-flag logic and (b) the
tab-strip widget. Extract those two concerns; the seven `tabpanel` JSX blocks
stay in the view (that is where the readable tab structure belongs).

The public surface is the single `LedgerOpsScreen` export (page.tsx + the
feature `index.ts` + the LedgerOpsScreen / LedgerLots / LedgerFxRates tests
import it). It is unchanged — **0 import-site edits**; the unit tests render
`<LedgerOpsScreen>` and assert `data-testid`s, all byte-identical.

### Split layout
- `components/use-ledger-ops-state.ts` — the `useLedgerOpsState(props)` custom
  hook: the active-tab state (queries gate on it), the entry / account /
  statement / lots / fx-rates / fx-history id-driven queries + their seed
  reconciliation + derived not-found / forbidden / bad-request flags, and the
  cross-tab drill handlers. Also owns the `LedgerOpsScreenProps` interface
  (re-exported by the component for the stable public type).
- `components/LedgerOpsTabs.tsx` — the ARIA `tablist` widget: the `TABS` model
  + `TabKey` type + the roving keyboard nav (ArrowLeft/Right/Home/End + focus),
  owning `tabRefs` internally. Props: `active` + `onSelect`.
- `components/LedgerOpsScreen.tsx` — the pure view: calls the hook, destructures
  the bag, renders `<LedgerOpsTabs>` + the seven `tabpanel`s (JSX byte-identical
  to before).

## Hard constraints (behavior preservation)
- **Public export set unchanged** — `LedgerOpsScreen` / `LedgerOpsScreenProps`
  remain importable from `./LedgerOpsScreen` (props re-exported from the hook
  module). The hook + tab widget stay feature-internal (NOT re-exported up to
  the feature `index.ts`).
- **Hook order preserved** — `useLedgerOpsState` calls every `useState` /
  `use*Query` hook unconditionally in the SAME order as the original component
  body; the component calls the hook once at top. React hook rules hold;
  behavior identical.
- The seven panel JSX blocks are moved byte-identical (every `data-testid` /
  ARIA / class / Korean label / `messageForCode(...)` call preserved); the tab
  strip JSX is moved verbatim into `LedgerOpsTabs`.
- `'use client'` preserved on all three modules (the hook uses client hooks; the
  tab widget uses `useRef`; the view composes them).
- **F5 directory-walk guards naturally cover the new files** — three ledger
  tests walk `features/ledger-ops/` asserting NO `Number()`/`parseFloat()`/
  `parseInt()` on `amount`/`exchangeRate` lines. The new files are in the same
  tree (verbatim move) → guards include them automatically and stay green —
  **no test edit needed**.
- No change to the ~17 view sub-components, `shared/api/*`, the hooks/`api`
  modules, the proxy routes, contracts, or specs.
- **Production code only — 0 test changes.**

## Procedure
1. Baseline (green at authoring): `npx vitest run <ledger suites>` → pass.
2. Create `LedgerOpsTabs.tsx` + `use-ledger-ops-state.ts` by moving the verbatim
   tab-strip + state blocks; trim the component to the hook call + the view JSX.
3. `tsc --noEmit` (0) + `next lint` (0) + `npx vitest run` (full) green.

## Acceptance Criteria
- `npx tsc --noEmit` → 0 errors.
- `npx next lint` → 0 warnings/errors.
- `npx vitest run` → all green, **same test count as the current baseline**
  (165 files / 1980 tests, incl. the F5 guards; baseline rose from 162/1943
  because the concurrent PC-FE-104 FX-history drill is already on origin/main).
- `LedgerOpsScreen.tsx` drops ~622 → ~453 lines (pure view); the extracted
  modules are `use-ledger-ops-state.ts` (~249) + `LedgerOpsTabs.tsx` (~80).
  (The view legitimately retains the seven `tabpanel` JSX blocks — that is the
  readable tab-shell structure.)
- 0 change to behavior, contracts, specs, route handlers, hooks, pages,
  `data-testid`/ARIA/class strings, label text.

## Related Specs / Contracts
- `platform/refactoring-policy.md` (Long File / Reduce Module Size,
  behavior-preserving; the "extract container logic to a custom hook" idiom).
- `specs/services/console-web/architecture.md` (Layered by Feature; the split
  stays feature-internal to `ledger-ops/components/`).
- `console-integration-contract` § 2.4.7.1 (ledger read surface: tabbed views,
  inline not-found, F5 string-money, no-429).

## Edge Cases
- `active` / `setActive` live in the HOOK (not in `LedgerOpsTabs`) because the
  fx-rates + fx-history queries gate on `active === 'fx-rates'`; `LedgerOpsTabs`
  receives `active` + `onSelect={setActive}` and owns only `tabRefs` + the
  keydown handler.
- `LedgerOpsScreenProps` moves to the hook file to avoid a component↔hook type
  cycle; the component re-exports it (`export type { LedgerOpsScreenProps }`) so
  the public type stays importable from `./LedgerOpsScreen`.
- `trialBalance` / `periods` / `discrepancies` are raw props used directly in
  the view JSX — the component destructures them from `props`; the rest comes
  from the hook bag (same variable names → JSX unchanged).
- The hook returns a flat bag whose field names match the original local
  variables exactly, so the seven panel JSX blocks need no edits.

## Failure Scenarios
- Re-ordering / conditionalizing the hook calls when moving them → React
  hook-order violation; move them verbatim, unconditional, same order.
- A changed `data-testid` / label / ARIA id → the ledger unit tests (or a
  Playwright selector) break; move every string verbatim.
- A `Number()`/`parseFloat()` slip on an `amount`/`exchangeRate` line in any new
  file → the F5 directory-walk guards turn RED.
- Re-exporting the hook / tab widget from the feature `index.ts` →
  public-surface widening (keep them feature-internal).
