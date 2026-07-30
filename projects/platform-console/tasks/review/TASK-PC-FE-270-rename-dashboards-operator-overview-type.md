# Task ID

TASK-PC-FE-270

# Title

console-web rename `features/dashboards`' colliding `OperatorOverview` type/schema/hook to `IamComposedOverview*`

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

`features/dashboards/api/types.ts` (the IAM-only 3-card overview: accounts/audit/operators) and `features/operator-overview/api/operator-overview-types.ts` (the 6-domain cross-domain BFF-composed overview: gap/wms/scm/finance/erp/ecommerce) both export a `OperatorOverviewSchema` zod schema and an `OperatorOverview` type — genuinely different shapes for genuinely different concepts, sharing only a name. `features/dashboards/hooks/use-overview.ts` also exports a hook literally named `useOperatorOverview`, colliding with `features/operator-overview/hooks/use-operator-overview.ts`'s `useOperatorOverview`.

TASK-PC-FE-261 already disambiguated the screen component in this exact pair (`OperatorOverviewScreen` → `IamComposedOverviewScreen`, in `features/dashboards`) and the state helper (`getIamComposedOverviewState`), but explicitly deferred the sibling type/schema/hook rename as "a separate, broader judgment call." This task completes that already-started disambiguation for the remaining type/schema/hook layer, applying the SAME `IamComposedOverview` naming TASK-PC-FE-261 already chose for the screen — not inventing a new name.

**This collision is not cosmetic — it already caused a real production-shaped bug.** `features/dashboards/hooks/use-overview.ts`'s own code comment (citing TASK-PC-FE-071) documents that colliding React Query cache keys between this hook and `features/operator-overview`'s hook let a stale `{ cards, asOf }` cache entry satisfy this hook's `initialData` on a client-side soft-navigation, crashing `IamComposedOverviewScreen` with `Cannot read properties of undefined (reading 'status')`. That specific incident was worked around via distinct query keys (`['iam-detail-overview']` vs `['operator-overview']`), not by disambiguating the type/hook names — the identical names remain a standing source of exactly this kind of confusion for any future reader or refactor.

---

# Scope

## In Scope

1. **`features/dashboards/api/types.ts`** — rename `OperatorOverviewSchema` → `IamComposedOverviewSchema`, `OperatorOverview` (type) → `IamComposedOverview`. No shape change.
2. **`features/dashboards/api/overview-api.ts`** — update the type import/usage (`Promise<OperatorOverview>` → `Promise<IamComposedOverview>`). **Do NOT rename the exported function `getOperatorOverview` itself** — it is a guarded contract-attestation surface: `console-integration-contract.md` § 3.1 row 16 pins `features/dashboards` `getOperatorOverview` as the console binding for the "dashboards" parity capability, and `tests/unit/parity-matrix.ts`/`parity-verification.test.ts` track it by that exact name (`clientExport: 'getOperatorOverview'`). Only the TYPE name changes; the function name is untouched.
3. **`features/dashboards/api/overview-state.ts`** — update `IamComposedOverviewState.overview`'s type from `OperatorOverview | null` to `IamComposedOverview | null`.
4. **`features/dashboards/hooks/use-overview.ts`** — rename the hook `useOperatorOverview` → `useIamComposedOverview`; update the type import/usage. Leave `OVERVIEW_KEY` and all query-key/refetch behavior untouched (TASK-PC-FE-071's fix stays exactly as-is — this task only renames identifiers, not behavior).
5. **`features/dashboards/components/IamComposedOverviewScreen.tsx`** — update the hook call site and the `OperatorOverview` type import/usage (the `initial: OperatorOverview` prop type).
6. **`features/dashboards/index.ts`** (barrel) — update the exported type name (`OperatorOverview` → `IamComposedOverview` in the `export type { ... } from './api/types'` block). This is a rename of the barrel's own re-exported identifier, not a contract-surface change (no `console-integration-contract.md` row pins the TYPE name, only the `getOperatorOverview` function).
7. **Test files**: `tests/unit/dashboards-nav.test.tsx` and `tests/unit/IamComposedOverviewScreen.test.tsx` — update their `import type { OperatorOverview } from '@/features/dashboards'` to `IamComposedOverview` and every local usage of that type annotation (fixture object type annotations only — the fixture VALUES/shapes are unchanged).

## Out of Scope

- `features/operator-overview/*` — untouched. Its `OperatorOverview`/`OperatorOverviewSchema`/`useOperatorOverview` names are correct as-is (the feature directory is literally named `operator-overview`, and it is genuinely the cross-domain "operator overview" concept).
- The `getOperatorOverview` function name in `features/dashboards/api/overview-api.ts` and its callers (`app/api/dashboards/route.ts`) — contract-pinned, must not change (see Scope item 2).
- The byte-identical `CARD_STATUSES`/`CardStatus` duplication that also exists between `features/dashboards/api/types.ts` and `features/operator-overview/api/operator-overview-types.ts` (both `['ok', 'degraded', 'forbidden']`) — a genuine duplication-not-collision, structurally similar to TASK-PC-FE-264's `OverviewCellStatus` extraction, but a distinct judgment call (is it the same concept in both contexts, or coincidentally identical?) not requested by this task. Noted here as a possible future candidate, not investigated further.
- Any change to `TASK-PC-FE-071`'s query-key fix or refetch behavior — this task is a pure rename.

---

# Acceptance Criteria

- [ ] `features/dashboards/api/types.ts` exports `IamComposedOverviewSchema`/`IamComposedOverview`; `OperatorOverviewSchema`/`OperatorOverview` no longer exist in this file.
- [ ] `getOperatorOverview` (the function) is byte-unchanged in name, signature shape (still returns the same runtime shape, just typed as `IamComposedOverview`), and behavior; `app/api/dashboards/route.ts` and `tests/unit/overview-api.test.ts` (which call/import `getOperatorOverview` by name) require zero changes.
- [ ] `features/dashboards/hooks/use-overview.ts` exports `useIamComposedOverview`; `useOperatorOverview` no longer exists in this file. `features/operator-overview/hooks/use-operator-overview.ts`'s `useOperatorOverview` is unaffected.
- [ ] `grep -rn "OperatorOverview\b" features/dashboards/ tests/` (excluding `getOperatorOverview`/`getIamComposedOverviewState` function names, which are unaffected) returns 0 matches.
- [ ] `features/operator-overview/**` is byte-unchanged (0 diff).
- [ ] `tsc --noEmit` passes with zero new errors.
- [ ] `pnpm lint` passes with zero new errors.
- [ ] Full `console-web` unit test suite (vitest) passes unchanged — same pass count.

---

# Related Specs

> **Before reading Related Specs**: Follow `platform/entrypoint.md` Step 0 — read `PROJECT.md`, then load `rules/common.md` plus any `rules/domains/<domain>.md` and `rules/traits/<trait>.md` matching the declared classification (platform-console: domain=saas, traits=[multi-tenant, integration-heavy, audit-heavy]). Unknown tags are a Hard Stop per `CLAUDE.md`.

- `platform/refactoring-policy.md` § Allowed Refactoring Categories → **Rename** (naming convention / disambiguation).
- TASK-PC-FE-261 (`OperatorOverviewScreen` → `IamComposedOverviewScreen`, `getOperatorOverviewState` → `getIamComposedOverviewState`) — this task's direct precedent and the source of the `IamComposedOverview*` naming convention adopted here. TASK-PC-FE-261 explicitly deferred this exact type/schema/hook rename.
- TASK-PC-FE-071 (`use-overview.ts`'s distinct query-key fix) — the documented prior incident this collision caused; this task does not touch that fix's behavior, only removes the standing name-collision risk factor.
- TASK-PC-FE-259 (`console-integration-contract.md` § 3.1 row 16 `getOperatorOverview` parity attestation) — the reason the function name (not the type name) must stay untouched.

# Related Skills

- `.claude/skills/frontend/architecture/layered-by-feature/SKILL.md`

---

# Related Contracts

None — `console-integration-contract.md` § 3.1 row 16 pins the `getOperatorOverview` FUNCTION name only, which this task does not change. No API/event contract touched.

---

# Target App

- `apps/console-web`

---

# Implementation Notes

- Grep-verify `OperatorOverview` usages BEFORE starting (to capture the exact baseline) and AFTER finishing (to confirm the rename is complete and `getOperatorOverview`/`getIamComposedOverviewState` are untouched), per TASK-PC-FE-262/268/269's verification method.
- Do a plain string rename (`OperatorOverviewSchema`→`IamComposedOverviewSchema`, `OperatorOverview`→`IamComposedOverview`) — do not also touch `OVERVIEW_QUICK_LINKS`, `CARD_STATUSES`, `CardStatus`, `AccountsSummary*`, `AuditActivitySummary*`, `OperatorsSummary*` in the same file; those names are unambiguous (no collision) and out of scope.

---

# Edge Cases

- `IamComposedOverviewScreen.tsx` imports BOTH the hook (`useOperatorOverview` from `use-overview.ts`) AND the type (`OperatorOverview` from `api/types.ts`) — both need updating in the same file; do not miss one while fixing the other.
- The barrel `features/dashboards/index.ts` re-exports the type under the SAME name it has internally (no aliasing today) — after the rename, the barrel's exported name naturally becomes `IamComposedOverview` too; do not add a re-export alias to preserve the old `OperatorOverview` name (no external consumer needs backward compatibility — this task's own AC requires 0 remaining references).

---

# Failure Scenarios

- `tsc --noEmit` fails after the rename → a usage site was missed; grep again for the literal string `OperatorOverview` (word-boundary) across `features/dashboards/` and `tests/` — do not leave a partial rename.
- Any test in `tests/unit/parity-verification.test.ts` or `tests/unit/parity-matrix.ts` fails → this means the `getOperatorOverview` FUNCTION name was accidentally touched; revert that change specifically (the function name must never change under this task).
- `IamComposedOverviewScreen.test.tsx` or `dashboards-nav.test.tsx` fail → a fixture's type annotation was renamed but the hook/hoisted-state wiring wasn't (or vice versa) — check both files' full diff, not just the type-annotation lines.

---

# Test Requirements

- No new tests required — existing coverage (`tests/unit/dashboards-nav.test.tsx`, `tests/unit/IamComposedOverviewScreen.test.tsx`, `tests/unit/overview-api.test.ts`, `tests/unit/parity-verification.test.ts`) is the verification mechanism and must pass unmodified (only type-annotation identifiers change in the first two, no assertion values change anywhere).

---

# Definition of Done

- [ ] All 7 scope items completed
- [ ] `tsc --noEmit` clean
- [ ] `pnpm lint` clean
- [ ] Full `console-web` vitest suite passing, zero test assertion values modified
- [ ] Ready for review
