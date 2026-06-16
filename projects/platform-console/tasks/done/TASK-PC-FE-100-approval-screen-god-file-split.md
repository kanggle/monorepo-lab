# TASK-PC-FE-100 — split the `erp-ops/components/ApprovalScreen.tsx` god-file into cohesive components

**Status:** done
**Area:** platform-console / console-web · **Refactor only** (Reduce Module Size / Long File — 0 behavior change, 0 contract change)
**Closure:** PR #1707 squash `99abbe97436906db72dc43e8e156ba07c0d69a95`, 3-dim verified (state=MERGED · origin/main tip match · pre-merge all checks pass incl. Frontend lint/build + unit + E2E smoke + Build & Test). 944-line monolith → approval-common.tsx (52) + ApprovalDetail.tsx (451) + ApprovalCreateDialog.tsx (251) + ApprovalScreen.tsx (230); public component surface import-stable (dialogs re-exported); 0 test changes; tsc 0 / lint 0 / vitest 162 files · 1943 tests green (baseline count).
**Parent:** third in the erp-ops god-file split series (sibling of TASK-PC-FE-098 `erp-api.ts` + TASK-PC-FE-099 `use-erp-ops.ts`). `ApprovalScreen.tsx` accreted three full components in one file (TASK-PC-FE-051 + 053) — ~944 lines.

## Goal

Split the single ~944-line `features/erp-ops/components/ApprovalScreen.tsx`
(three components + shared presentation) into cohesive component modules
**without changing any externally observable behavior** (every `data-testid`,
ARIA attribute, Tailwind class, state-machine gating, Idempotency-Key
generation, label string preserved). Pure structural move — no JSX/logic edited.

The module path `./ApprovalScreen` stays the **stable public surface**: the
feature `index.ts` re-exports `ApprovalScreen` / `ApprovalDetail` /
`ApprovalCreateDialog` from it, and the unit test imports `ApprovalScreen` /
`ApprovalDetail` via the feature index — so `ApprovalScreen.tsx` keeps
exporting all three (re-exporting the two extracted dialogs) — **0 import-site
edits**.

### Split layout
- `components/approval-common.tsx` — the shared presentation used by all three
  components: `SUBJECT_LABEL` / `STATUS_LABEL` / `statusLabel` / `StatusBadge`
  (pure, no hooks).
- `components/ApprovalDetail.tsx` — `ApprovalDetail` (the detail dialog: status
  badge + multi-stage timeline + immutable history + state-gated transition
  actions) + the internal `ApprovalReasonDialog` (reject/withdraw reason) + the
  `fmt` date helper.
- `components/ApprovalCreateDialog.tsx` — `ApprovalCreateDialog` (the DRAFT
  create dialog: subjectType/subjectId/title + ordered approver route 1~N).
- `components/ApprovalScreen.tsx` — `ApprovalScreen` (list + inbox + status
  filter) + `ApprovalScreenProps` + the module narrative; imports the two
  dialogs for its JSX and **re-exports** them.

The three components share NO mutable state — they communicate only via props
(`id` / `onClose` / seed snapshots) and the React Query cache. The only coupling
is the presentational `StatusBadge` + label maps → extracted to
`approval-common.tsx`. `StatusBadge` is feature-internal (the other features'
`StatusBadge` symbols are their own local definitions, not imports of this one).

## Hard constraints (behavior preservation)
- **Public export set unchanged** — `ApprovalScreen` / `ApprovalScreenProps` /
  `ApprovalDetail` / `ApprovalCreateDialog` remain importable from
  `./ApprovalScreen` (the latter two via re-export). `StatusBadge` /
  `statusLabel` / the label maps / `ApprovalReasonDialog` / `fmt` stay
  feature-internal (exported from `approval-common.tsx` only so siblings import
  them; NOT re-exported up to the feature index).
- **`'use client'` preserved** — every component module carries the directive
  (the original was a client module).
- **`newIdemKey` duplication kept** — it was defined locally in both
  `ApprovalDetail` and `ApprovalCreateDialog`; the structural move keeps both
  copies verbatim (de-duping is a separate refactor, out of scope).
- Every `data-testid` / `aria-*` / class string / Korean label / state-machine
  gate (`allowedTransitionsFor` / `transitionRequiresReason` /
  `isTerminalApprovalStatus`) moved byte-for-byte.
- No change to `shared/ui/*`, the `approval-types` / `approval-error` /
  `format-datetime` modules, hooks, the proxy routes, contracts, or specs.
- **Production code only — 0 test changes.** (No test reads this file's source
  text; the unit test imports the public components via the feature index,
  which keeps resolving them.)

## Procedure
1. Baseline (green at authoring): `npx vitest run tests/unit/features/erp-ops/ApprovalScreen.test.tsx` → 20 pass.
2. Create `approval-common.tsx` / `ApprovalDetail.tsx` / `ApprovalCreateDialog.tsx`
   by moving the verbatim blocks; trim `ApprovalScreen.tsx` to the list/inbox
   screen + re-exports.
3. `tsc --noEmit` (0) + `next lint` (0) + `npx vitest run` (full) green.

## Acceptance Criteria
- `npx tsc --noEmit` → 0 errors.
- `npx next lint` → 0 warnings/errors.
- `npx vitest run` → all green, **same test count as baseline** (162 files /
  1943 tests; approval suite 20).
- `ApprovalScreen.tsx` drops ~944 → ~230 lines; the extracted modules are
  `ApprovalDetail.tsx` (~451) + `ApprovalCreateDialog.tsx` (~251) +
  `approval-common.tsx` (~52). Largest approval module drops 944 → 451 (−52%).
- 0 change to behavior, contracts, specs, route handlers, hooks, pages,
  `data-testid`/ARIA/class strings, label text.

## Related Specs / Contracts
- `platform/refactoring-policy.md` (Long File / Reduce Module Size,
  behavior-preserving).
- `specs/services/console-web/architecture.md` (Layered by Feature; the split
  stays feature-internal to `erp-ops/components/`).
- `specs/contracts/console-integration-contract.md` § 2.4.8 (the preserved
  approval surface: state machine, multi-stage route, reason-gated transitions).

## Edge Cases
- `StatusBadge` is rendered by all three components — it lives in
  `approval-common.tsx` and is imported by each. Its `data-testid`
  (`approval-status-badge`) + `data-terminal` derivation must be byte-identical.
- `ApprovalScreen.tsx` both imports the two dialogs (for JSX) and re-exports
  them (`export { ApprovalDetail } from './ApprovalDetail'`) — the re-export is
  a separate statement, not a duplicate binding.
- `approval-common.tsx` carries `'use client'` even though `StatusBadge` uses no
  hooks (consistency + it is bundled into client components anyway).

## Failure Scenarios
- Re-exporting `StatusBadge` / `statusLabel` from `ApprovalScreen.tsx` or the
  feature index → public-surface widening (keep them feature-internal).
- A missing `'use client'` in a component module → "hooks in server component"
  runtime error (build/E2E RED).
- A changed `data-testid` / label string → the approval unit test (or a
  Playwright selector) breaks; move every string verbatim.
- Forgetting the `ApprovalDetail` / `ApprovalCreateDialog` re-export from
  `ApprovalScreen.tsx` → the feature `index.ts` re-export + the unit test
  import break.
