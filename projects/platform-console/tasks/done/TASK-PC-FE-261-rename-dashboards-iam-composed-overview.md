# Task ID

TASK-PC-FE-261

# Title

console-web rename features/dashboards' OperatorOverviewScreen -> IamComposedOverviewScreen (naming-collision follow-up to TASK-PC-FE-260)

# Status

done

# Owner

frontend

# Task Tags

- code

---

# Required Sections (must exist)

- Goal
- Scope (in/out)
- Acceptance Criteria
- Related Specs
- Related Contracts
- Edge Cases
- Failure Scenarios

If any section is missing or incomplete, this task must not be implemented.

---

# Goal

Resolve the `OperatorOverviewScreen` component-name collision flagged (but deliberately left out of scope) by the TASK-PC-FE-260 naming scan: `features/dashboards` and `features/operator-overview` each export a component named `OperatorOverviewScreen` from their own barrel, used on two different live routes. This is not dead code on either side — re-reading `architecture.md`'s `app/` tree confirms both are real, distinct-scope components that happened to collide because TASK-PC-FE-034 promoted `features/operator-overview`'s screen to be the new 5-domain console landing (`/dashboards/overview`) and renamed nothing on the older, now-demoted `features/dashboards` screen (`/dashboards`, now an IAM-only 3-leg drill-down detail reached from the new landing's IAM card). Fix: rename ONLY the `features/dashboards` side (the narrower-scope, demoted one) to `IamComposedOverviewScreen`, matching its actual role ("IAM 상세 (계정 · 감사 · 운영자)"). `features/operator-overview`'s `OperatorOverviewScreen` is untouched — it already matches the dominant naming pattern.

Also renames the accompanying state loader `getOverviewState` -> `getIamComposedOverviewState` (+ its `OverviewState` type -> `IamComposedOverviewState`), which was TASK-PC-FE-260 scan finding #2 (state-loader missing its domain prefix, unlike the 7 sibling `get<Domain>OverviewState` implementations) — deferred there because it shares this same rename unit.

Pure rename — no behavior, route, prop, testid, or wire-shape change.

---

# Scope

## In Scope

All changes confined to `features/dashboards` (+ its two consuming test files) — `features/operator-overview` is NOT touched:

1. **File + component rename**: `features/dashboards/components/OperatorOverviewScreen.tsx` → `IamComposedOverviewScreen.tsx`.
   - `export function OperatorOverviewScreen(...)` → `export function IamComposedOverviewScreen(...)`
   - `export interface OperatorOverviewScreenProps` → `export interface IamComposedOverviewScreenProps`
2. **State loader rename**: `features/dashboards/api/overview-state.ts`.
   - `export interface OverviewState` → `export interface IamComposedOverviewState`
   - `export async function getOverviewState()` → `export async function getIamComposedOverviewState()`
3. **Barrel update**: `features/dashboards/index.ts` — update the 3 re-exports (`OperatorOverviewScreen`, `getOverviewState`, `type OverviewState`) to the new names.
4. **Route consumer**: `app/(console)/dashboards/page.tsx` — update the import + both call sites (`getOverviewState()` → `getIamComposedOverviewState()`, `<OperatorOverviewScreen .../>` → `<IamComposedOverviewScreen .../>`) + the doc-comment mention of `getOverviewState()`.
5. **Same-feature doc-comment references** (JSDoc `{@link OperatorOverviewScreen}` / plain-text mentions, all inside `features/dashboards`, all referring to the component being renamed — not `operator-overview`'s):
   - `features/dashboards/components/OverviewCard.tsx`
   - `features/dashboards/components/OverviewMetric.tsx`
   - `features/dashboards/hooks/use-overview.ts`
6. **Test file rename + update**: `tests/unit/OperatorOverviewScreen.test.tsx` → `tests/unit/IamComposedOverviewScreen.test.tsx` — update the import and all `describe(...)` labels + JSX usages to the new component name (testids / assertions / heading text UNCHANGED — this is a rename, not a behavior test change).
7. **Test consumer update**: `tests/unit/dashboards-nav.test.tsx` — update the import + both `<OperatorOverviewScreen .../>` usages to `<IamComposedOverviewScreen .../>`.

## Out of Scope

- `features/operator-overview`'s `OperatorOverviewScreen` / `OperatorOverviewScreenProps` / `getOperatorOverviewState` — already matches the dominant naming pattern, not touched.
- `tests/unit/features/operator-overview/OperatorOverviewScreen.test.tsx` and `tests/unit/overview-page-parallel.test.tsx` — both reference `features/operator-overview`'s component (verified by reading each file), not `features/dashboards`'s. Not touched.
- `app/(console)/dashboards/overview/page.tsx` — imports `operator-overview`'s `OperatorOverviewScreen` (prop `overview=`, not `initial=` — confirmed distinct from the dashboards one). Not touched.
- `features/dashboards/api/overview-api.ts`'s `getOperatorOverview()` function and the `OperatorOverview` type (`features/dashboards/api/types.ts`) — these do NOT collide by exact name with anything in `operator-overview` (`fetchOperatorOverview` / `OperatorOverviewSchema` there are different identifiers) and were not flagged by the original scan. Renaming them is a separate, broader judgment call, not part of this fix.
- `features/finance-overview/components/FinanceOverviewScreen.tsx`'s doc-comment mention of "mirrors `OperatorOverviewScreen`" — ambiguous which sibling it refers to (a generic pattern citation, not an import), left as-is.
- Any behavior, route, prop shape, testid, or wire-format change.

---

# Acceptance Criteria

- [ ] `features/dashboards/components/IamComposedOverviewScreen.tsx` exists; `OperatorOverviewScreen.tsx` no longer exists under `features/dashboards/components/`.
- [ ] `grep -r "OperatorOverviewScreen" features/dashboards/` (excluding `.test.tsx` under `tests/`) returns 0 matches.
- [ ] `features/operator-overview`'s `OperatorOverviewScreen` export and all its consumers are byte-unchanged.
- [ ] `getIamComposedOverviewState` / `IamComposedOverviewState` exported from `features/dashboards`; `getOverviewState` / `OverviewState` no longer exist in that feature.
- [ ] `/dashboards` route (`app/(console)/dashboards/page.tsx`) renders identically (same heading, same testids, same back-link) — verified by the renamed test file passing unmodified in assertions.
- [ ] `tsc --noEmit` — 0 errors.
- [ ] `next lint` — 0 warnings/errors.
- [ ] `vitest run` — same pass count as TASK-PC-FE-260's post-merge baseline (280 files / 2909 tests), since this is a pure rename with no new/removed tests.

---

# Related Specs

> **Before reading Related Specs**: Follow `platform/entrypoint.md` Step 0 — read `PROJECT.md`, then load `rules/common.md` plus any `rules/domains/<domain>.md` and `rules/traits/<trait>.md` matching the declared classification (platform-console: domain=saas, traits=[multi-tenant, integration-heavy, audit-heavy]). Unknown tags are a Hard Stop per `CLAUDE.md`.

- `platform/refactoring-policy.md` § Allowed Refactoring Categories → **Rename** (Low risk); this task performs Rename only, one category, no behavior change.
- `projects/platform-console/specs/services/console-web/architecture.md` § Internal Structure Rule — documents `dashboards/overview/` as the 5-domain landing (`operator-overview` feature) and `dashboards/(index page.tsx)` as the "IAM-only 합성 개요 ... drill-down detail" (the `features/dashboards` screen this task renames) — the source that confirms these are two distinct, both-live components, not a duplicate to merge.
- `projects/platform-console/tasks/done/TASK-PC-FE-260-naming-convention-cleanup.md` — the naming scan that surfaced this collision as finding #1 (deferred pending product judgment, now resolved as rename-only).

# Related Skills

- `.claude/skills/frontend/architecture/layered-by-feature/SKILL.md`

---

# Related Contracts

None — no API or event contract is touched (pure internal identifier/file rename).

---

# Target App

- `apps/console-web`

---

# Implementation Notes

- Rename the file with `mv`/`git mv`, then apply symbol renames inside it and in each of the 6 consumer files listed in Scope. Grep for the old names across the FULL `apps/console-web` tree (not just `features/dashboards/`) before considering the rename complete, since `Related Specs` already identified two look-alike names in `operator-overview` that must NOT be touched — double-check every match belongs to `features/dashboards` before editing it.
- The renamed test file's `describe(...)` string labels and any comment text referencing the old component name should be updated for accuracy, but do not alter what any test asserts (testids, heading text, prop values) — this is TASK-PC-FE-260's Prohibited-list precedent ("Refactoring production code and test code in the same change" does not apply here since both are pure renames of the same identifier, but keep the diff mechanical).

---

# Edge Cases

- The two doc-comment `{@link OperatorOverviewScreen}` references in `OverviewCard.tsx` / `OverviewMetric.tsx` must resolve to the renamed component, not silently point at a name that no longer exists in this feature.
- Confirm no dynamic/computed import string (e.g. `require('./OperatorOverviewScreen')` or a route-manifest reference) exists outside the grep-visible static imports already enumerated in Scope.

---

# Failure Scenarios

- `tsc --noEmit` fails after the rename due to a missed import path → fix the import; do not leave a re-export shim under the old name (defeats the purpose of the rename).
- `next lint` flags an unused import or ordering issue from the renamed imports → fix inline, in-scope for a rename task.
- Vitest suite fails post-rename → indicates a missed reference or an accidental behavior change (e.g. a testid or heading text was altered instead of just the identifier) → revert and re-diagnose rather than editing test assertions to match.
- Any match of `OperatorOverviewScreen` found inside `features/operator-overview/` during the grep sweep → do NOT touch it; that is the correctly-named sibling, not this task's target.

---

# Test Requirements

- No new tests required — pure rename/relocation with zero behavior change; the renamed `IamComposedOverviewScreen.test.tsx` (ex `OperatorOverviewScreen.test.tsx`) and updated `dashboards-nav.test.tsx` must pass unmodified in what they assert.

---

# Definition of Done

- [ ] All 7 scope items completed
- [ ] `tsc --noEmit` clean
- [ ] `next lint` clean
- [ ] `vitest run` — same pass count as baseline, 0 failures
- [ ] `features/operator-overview` byte-unchanged (diff review)
- [ ] Ready for review
