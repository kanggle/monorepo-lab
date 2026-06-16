# TASK-PC-FE-105 — split the `operators/components/OperatorsScreen.tsx` god-file (container/presentational)

**Status:** ready
**Area:** platform-console / console-web · **Refactor only** (Reduce Module Size / Long File — 0 behavior change, 0 contract change)
**Parent:** continuation of the console-web god-file split series (098 erp-api, 099 use-erp-ops, 100 ApprovalScreen, 101 OutboundOpsScreen, 102 ledger-api, 103 WmsOpsScreen). `OperatorsScreen.tsx` is the IAM operators-management surface (~634 lines, the most privilege-sensitive slice). Note: PC-FE-104 was taken by a concurrent session (`ledger-fx-history-tab`); this task is numbered 105.

## Goal

Slim the ~634-line `features/operators/components/OperatorsScreen.tsx`
**without changing any externally observable behavior** (every `data-testid`,
ARIA attribute, Tailwind class, Korean label, reason/confirm gating, elevated
copy, fresh-per-confirmed-create `crypto.randomUUID()` idempotency-key
preserved). Single stateful container → **container / presentational** (the
PC-FE-101 playbook): the container keeps ALL state + mutations + gating; the two
biggest remaining chunks become extracted units. (The badges / create-form /
confirm / profile / org-scope dialogs were already separate components.)

The public surface is the single `OperatorsScreen` export (page.tsx + the
feature `index.ts` + the operators-nav test import it). It is unchanged — **0
import-site edits**; the unit tests render `<OperatorsScreen>` and assert
`data-testid`s, all byte-identical.

### Split layout
- `components/operators-confirm-copy.tsx` — the pending-action model
  (`PendingKind` / `PendingAction` / `newIdemKey`) + the three
  `OperatorConfirmDialog` copy builders `operatorConfirmTitle` /
  `operatorConfirmDescription` (returns rich `ReactNode`) /
  `operatorConfirmLabel` (pure functions of the pending action).
- `components/OperatorsTable.tsx` — the list region: the status-filter form,
  the transient list-error notice, the operators table (with the per-row
  privilege-sensitive action buttons), and the pagination nav. Presentational.
- `components/OperatorsScreen.tsx` — the container: owns the filter/query state,
  the 4 mutations + the pending/profile-edit/org-scope state, the derived
  reactive dialog targets, the error derivations + gating, and the
  `CreateOperatorForm` + 3 dialog mounts; renders `OperatorsTable`.

## Hard constraints (behavior preservation)
- **Public export set unchanged** — `OperatorsScreen` / `OperatorsScreenProps`
  remain importable from `./OperatorsScreen`. The extracted table + copy module
  stay feature-internal (NOT re-exported up to the feature `index.ts`).
- **All state stays in the container** — `OperatorsTable` is a pure function of
  props (no `useState`/query/mutation/`useMemo` moved out). The reactive
  `profileEditFor` / `orgScopeFor` `useMemo`s, the fresh-per-create idempotency
  key, and the confirm/onConfirm flow are untouched in the container.
- Every `data-testid` / `aria-*` / class string / Korean label / elevated-copy
  string moved byte-for-byte (the confirm-copy builders preserve every word).
- `'use client'` preserved on the container + `OperatorsTable`
  (`operators-confirm-copy.tsx` is a `.tsx` because the description builder
  returns JSX, but defines no hooks).
- No change to `OperatorBadges` / `CreateOperatorForm` / `OperatorConfirmDialog`
  / `OperatorProfileEditDialog` / `OrgScopeDialog`, `shared/ui/*`, the hooks, the
  `api/types` module, the proxy routes, contracts, or specs.
- **Production code only — 0 test changes.**

## Procedure
1. Baseline (green at authoring): `npx vitest run <operators suites>` → 229 pass (26 files).
2. Create `operators-confirm-copy.tsx` + `OperatorsTable.tsx` by moving the
   verbatim blocks; trim the container to state + handlers + gate + the
   `OperatorsTable` render + the dialog mounts (confirm uses the copy builders).
3. `tsc --noEmit` (0) + `next lint` (0) + `npx vitest run` (full) green.

## Acceptance Criteria
- `npx tsc --noEmit` → 0 errors (proves the prop wiring is correct).
- `npx next lint` → 0 warnings/errors.
- `npx vitest run` → all green, **same test count as baseline** (162 files /
  1943 tests; operators suites 229).
- `OperatorsScreen.tsx` drops ~634 → ~416 lines; the extracted modules are
  `OperatorsTable.tsx` (~256) + `operators-confirm-copy.tsx` (~93). Container
  drops 634 → 416 (−34%). (The container legitimately retains the 4 mutations +
  reactive dialog-target state + 3 dialog mounts — those belong in the
  container.)
- 0 change to behavior, contracts, specs, route handlers, hooks, pages,
  `data-testid`/ARIA/class strings, label text.

## Related Specs / Contracts
- `platform/refactoring-policy.md` (Long File / Reduce Module Size,
  behavior-preserving; container/presentational decomposition).
- `specs/services/console-web/architecture.md` (Layered by Feature; the split
  stays feature-internal to `operators/components/`).
- `console-integration-contract` § 2.4.3 (operator-management header matrix:
  reason-gated mutations, create-only idempotency key).

## Edge Cases
- `onStatusFilterChange={setStatusFilter}` passes the `useState` dispatcher;
  `'' | OperatorStatus` is assignable to its `SetStateAction` param, so the
  narrower prop type holds.
- The per-row "프로파일 편집" button's two-step click (`setProfile.reset()` then
  `setProfileEditOperatorId(...)`) is wrapped into the container's
  `openProfileEdit(op)` handler passed as `onEditProfile` — the reset ordering
  is preserved.
- The pagination `onPrevPage` / `onNextPage` keep the exact
  `Math.max(0, page-1)` / `page+1` arithmetic (inline arrow props on the
  container); the child receives `currentPage={query.page}` for the prev-disable.
- `operatorConfirmDescription` returns `ReactNode` (rich JSX with `<strong>`s and
  the conditional SUPER_ADMIN call-out) — the file is `.tsx`; every word of the
  four-branch copy is preserved verbatim.

## Failure Scenarios
- Moving any `useState`/query/mutation/`useMemo` into `OperatorsTable` →
  behavior change; keep ALL state in the container.
- A changed `data-testid` / label / elevated-copy string → the operators unit
  test (or a Playwright selector) breaks; move every string verbatim.
- A missing `'use client'` on `OperatorsTable` → "hooks in server component"
  style build error (it renders inside a client tree).
- Re-exporting the table / copy module from the feature `index.ts` →
  public-surface widening (keep them feature-internal).
