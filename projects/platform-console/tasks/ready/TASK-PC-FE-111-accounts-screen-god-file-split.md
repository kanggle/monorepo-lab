# TASK-PC-FE-111 — split the `accounts/components/AccountsScreen.tsx` god-file (container/presentational)

**Status:** ready
**Area:** platform-console / console-web · **Refactor only** (Reduce Module Size / Long File — 0 behavior change, 0 contract change)
**Parent:** continuation of the console-web god-file split series (098 erp-api, 099 use-erp-ops, 100 ApprovalScreen, 101 OutboundOpsScreen, 102 ledger-api, 103 WmsOpsScreen, 105 OperatorsScreen, 106 LedgerOpsScreen, 107/108/109 erp-masters hook/api/types). `AccountsScreen.tsx` is the IAM accounts operator surface (~513 lines, the destructive-op privilege slice: lock/unlock/revoke-session/gdpr-delete/bulk-lock). Note: PC-FE-110 (operators-api split) is owned by a concurrent session; this task is numbered 111 and touches a disjoint feature (`accounts/`).

## Goal

Slim the ~513-line `features/accounts/components/AccountsScreen.tsx`
**without changing any externally observable behavior** (every `data-testid`,
ARIA attribute, Tailwind class, Korean label, reason/confirm gating, typed-phrase
GDPR double-confirm, fresh-per-confirmed-action `crypto.randomUUID()` idempotency
key preserved). Single stateful container → **container / presentational** (the
PC-FE-101/103/105 playbook): the container keeps ALL state + mutations + gating;
the two biggest remaining chunks become extracted units. (The status badge +
confirm dialog were already separate components.)

The public surface is the single `AccountsScreen` export (page.tsx + the feature
`index.ts` + the `AccountsScreen` unit test import it via `@/features/accounts`).
It is unchanged — **0 import-site edits**; the unit tests render `<AccountsScreen>`
and assert `data-testid`s, all byte-identical.

### Split layout
- `components/accounts-screen-helpers.tsx` — the pending-action model
  (`ActionKind` / `PendingAction` / `AccountsQuery`) + `ACTION_META` + the pure
  helpers `isForbidden` / `newIdemKey` + the confirm-dialog copy builder
  `accountActionDescription(pending)` (returns rich `ReactNode`). `.tsx` because
  the description builder returns JSX; defines no hooks.
- `components/AccountsTable.tsx` — the result region: the accounts table (the
  per-row select checkbox + lock/unlock/revoke/export/GDPR action buttons + the
  status badge) and the pagination nav. Presentational, prop-driven.
- `components/AccountsScreen.tsx` — the container: owns the email/query/selected/
  pending/bulkResult state, the 5 mutations, the `activeMutation`/`dialogError`
  derivations, the open/close/confirm/toggle/submit/export handlers, the search
  form + degraded banner + bulk-result panel + empty-state + `ConfirmActionDialog`
  mount; renders `AccountsTable` for the table region.

## Hard constraints (behavior preservation)
- **Public export set unchanged** — `AccountsScreen` remains importable from
  `./components/AccountsScreen` (re-exported by the feature `index.ts`). The
  extracted table + helpers module stay feature-internal (NOT re-exported up to
  the feature `index.ts`).
- **All state stays in the container** — `AccountsTable` is a pure function of
  props (no `useState`/query/mutation/`useMemo` moved out). The fresh-per-action
  idempotency key and the confirm/onConfirm flow are untouched in the container.
- Every `data-testid` / `aria-*` / class string / Korean label moved
  byte-for-byte (the confirm-copy builder preserves every word).
- `'use client'` preserved on the container + `AccountsTable`
  (`accounts-screen-helpers.tsx` is a `.tsx` because the description builder
  returns JSX, but defines no hooks).
- No change to `AccountStatusBadge` / `ConfirmActionDialog`, `shared/ui/*`, the
  hooks, the `api/types` / `lib/classify-empty` modules, the proxy routes,
  contracts, or specs.
- **Production code only — 0 test changes.**

## Procedure
1. Baseline (green at authoring): `npx vitest run` → record file/test count.
2. Create `accounts-screen-helpers.tsx` + `AccountsTable.tsx` by moving the
   verbatim blocks; trim the container to state + handlers + gate + the
   `AccountsTable` render + the dialog mount (confirm uses the copy builder).
3. `tsc --noEmit` (0) + `next lint` (0) + `npx vitest run` (full) green.

## Acceptance Criteria
- `npx tsc --noEmit` → 0 errors (proves the prop wiring is correct).
- `npx next lint` → 0 warnings/errors.
- `npx vitest run` → all green, **same test count as baseline**.
- `AccountsScreen.tsx` drops ~513 → ~270 lines; the extracted modules are
  `AccountsTable.tsx` (~175) + `accounts-screen-helpers.tsx` (~90). (The
  container legitimately retains the 5 mutations + pending/bulk state + the
  dialog mount — those belong in the container.)
- 0 change to behavior, contracts, specs, route handlers, hooks, pages,
  `data-testid`/ARIA/class strings, label text.

## Related Specs / Contracts
- `platform/refactoring-policy.md` (Long File / Reduce Module Size,
  behavior-preserving; container/presentational decomposition).
- `specs/services/console-web/architecture.md` (Layered by Feature; the split
  stays feature-internal to `accounts/components/`).
- `console-integration-contract` § 2.4 (accounts-management header matrix:
  reason-gated + idempotency-keyed destructive mutations).

## Edge Cases
- `onPageChange={setQuery}` passes the `useState` dispatcher; the inline
  `setQuery((q) => ...)` functional updates stay in the table's prev/next
  handlers, preserving the exact `Math.max(0, page-1)` / `page+1` arithmetic and
  the `query.email`-gated disable logic.
- `onToggleSelect` / `onAction(kind, account)` / `onExport(account)` forward to
  the container's `toggleSelect` / `openAction(kind, account)` / `exportAccount`
  verbatim (single-account ops only from the table; bulk-lock stays triggered
  from the container's search form).
- `accountActionDescription` returns `ReactNode` (the three-branch JSX with
  `<strong>`s and the GDPR irreversible call-out) — the file is `.tsx`; every
  word of the copy is preserved verbatim.
- The empty-state `classifyAccountsEmpty(...)` IIFE and the degraded-banner
  `isForbidden(...)` suppression stay in the container (they read `search.*`).

## Failure Scenarios
- Moving any `useState`/query/mutation into `AccountsTable` → behavior change;
  keep ALL state in the container.
- A changed `data-testid` / label string → the `AccountsScreen` unit test breaks;
  move every string verbatim.
- A missing `'use client'` on `AccountsTable` → "hooks in server component" style
  build error (it renders inside a client tree).
- Re-exporting the table / helpers module from the feature `index.ts` →
  public-surface widening (keep them feature-internal).
