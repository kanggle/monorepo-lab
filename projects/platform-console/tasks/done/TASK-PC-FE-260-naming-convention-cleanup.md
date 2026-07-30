# Task ID

TASK-PC-FE-260

# Title

console-web naming convention cleanup — hook placement, status-tone dedup, query-key placement, types barrel

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

Fix four low-risk naming/placement inconsistencies in `console-web` found by a full `features/*`/`shared/*` naming-convention scan (2026-07-30). Each item is a file-move or a duplicate-function hoist — pure rename/relocation, zero behavior change. `platform/naming-conventions.md` has no TypeScript/frontend section; the "convention" being enforced here is the pattern the codebase has already converged on across ~30-40 sibling `features/*` folders (cited per item below), not a written platform rule.

---

# Scope

## In Scope

1. **Hook files misplaced under `components/` instead of `hooks/`** (5 files):
   - `features/erp-ops/components/use-approval-detail.ts` → `features/erp-ops/hooks/use-approval-detail.ts`
   - `features/erp-ops/components/use-delegation-screen.ts` → `features/erp-ops/hooks/use-delegation-screen.ts`
   - `features/erp-ops/components/use-department-write.ts` → `features/erp-ops/hooks/use-department-write.ts`
   - `features/erp-ops/components/use-master-write.ts` → `features/erp-ops/hooks/use-master-write.ts`
   - `features/ledger-ops/components/use-ledger-ops-state.ts` → `features/ledger-ops/hooks/use-ledger-ops-state.ts`
   - Sibling pattern: all other 55 `use-*.ts` hook files in the codebase live under their feature's `hooks/` directory (e.g. `erp-ops/hooks/use-erp-approval.ts`, `ledger-ops/hooks/use-ledger-entries.ts`); both target `hooks/` directories already exist.
   - Update all internal import paths referencing the moved files (component files, index.ts barrels, tests).

2. **`tenantStatusTone` duplicated instead of centralized** (1 function, 2 call sites):
   - Currently defined identically and unexported in both `features/tenants/components/TenantsTable.tsx` and `features/tenants/components/TenantDetail.tsx`.
   - Hoist to `features/tenants/api/types.ts` as `export function tenantStatusTone(...)`, import from both components, delete the duplicates.
   - Sibling pattern: all ~20 other `<domain>StatusTone` functions live in a single source module per domain (dominantly `api/*-types.ts` — 17 instances; a `components/*-helpers.ts` secondary pattern for table-heavy features). `features/tenants/api/types.ts` currently has no `tenantStatusTone` at all — tenants is the only domain where the tone mapper is copy-pasted rather than centralized.

3. **Query-key factory misplaced under `hooks/` instead of `api/`** (1 file):
   - `features/operators/hooks/operators-keys.ts` → `features/operators/api/operators-keys.ts`
   - Sibling pattern: `features/erp-ops/api/erp-keys.ts` and `features/notifications/api/notification-keys.ts` both place their React-Query key-factory module under `api/`.
   - Update all internal import paths (hooks in `features/operators/hooks/*` that import the key factory).

4. **`types` barrel filename inconsistency between two features**:
   - `features/erp-ops/api/types.ts` (a **file**, re-exporting `./types/{common,department,employee,job-grade,cost-center,business-partner,employee-org-view,delegation-fact}.ts`) vs. `features/ledger-ops/api/types/index.ts` (a folder-**internal** barrel, no sibling `types.ts` file).
   - Standardize on the `types/index.ts` folder-barrel convention (avoids the file/folder same-basename oddity in `erp-ops`): move `erp-ops/api/types.ts`'s content into `erp-ops/api/types/index.ts`, delete the old `types.ts` file, update all import paths that reference `features/erp-ops/api/types` (path resolution is unaffected by this move — `.../types` still resolves to `.../types/index.ts` — but verify no import explicitly appends `.ts` or bypasses barrel resolution).

## Out of Scope

- `OperatorOverviewScreen` duplicate-component-name collision between `features/dashboards` and `features/operator-overview`, and the related `getOverviewState` domain-prefix fix — these require a product decision (merge vs. rename-and-keep-both) beyond a mechanical rename; tracked separately, not part of this task.
- `<Domain>ConfirmDialog` three-way naming split (`ConfirmDialog`/`ConfirmActionDialog`/`<Domain>ConfirmDialog`) — lowest-priority finding from the same scan, deferred.
- Any StatusBadge/DetailHeader UI-pattern residue (already closed by TASK-PC-FE-242) or cross-feature import boundaries (already closed by TASK-PC-FE-259).
- Any behavior, UI, or API contract change. This is a pure rename/relocation task.

---

# Acceptance Criteria

- [ ] The 5 hook files listed in Scope item 1 live under their feature's `hooks/` directory; no `use-*.ts` files remain directly under `erp-ops/components/` or `ledger-ops/components/`.
- [ ] `tenantStatusTone` exists exactly once, exported from `features/tenants/api/types.ts`; both `TenantsTable.tsx` and `TenantDetail.tsx` import it rather than defining it locally.
- [ ] `features/operators/api/operators-keys.ts` exists; `features/operators/hooks/operators-keys.ts` no longer exists.
- [ ] `features/erp-ops/api/types/index.ts` exists as the sole barrel; the sibling `features/erp-ops/api/types.ts` file no longer exists.
- [ ] `tsc --noEmit` (or the project's configured type-check script) passes with zero new errors.
- [ ] `pnpm lint` passes with zero new errors (per `env_console_web_local_verify_needs_lint` — tsc/vitest alone do not catch frontend CI lint failures).
- [ ] Full `console-web` unit test suite (vitest) passes unchanged — no test assertions modified, since this is a pure move/hoist with zero behavior change.
- [ ] No new circular-import or cross-feature-boundary violation is introduced by the file moves (spot-check the moved files' new import graph against `features/erp-ops`'s existing `_eligibility.ts`-style boundary, per TASK-PC-FE-259's precedent).

---

# Related Specs

> **Before reading Related Specs**: Follow `platform/entrypoint.md` Step 0 — read `PROJECT.md`, then load `rules/common.md` plus any `rules/domains/<domain>.md` and `rules/traits/<trait>.md` matching the declared classification (platform-console: domain=saas, traits=[multi-tenant, integration-heavy, audit-heavy]). Unknown tags are a Hard Stop per `CLAUDE.md`.

- `platform/naming-conventions.md` — has no TypeScript/frontend section; not directly applicable, cited for completeness.
- `platform/refactoring-policy.md` § Allowed Refactoring Categories → **Rename** (Low risk); this task performs Rename only, one category, no behavior change.
- `projects/platform-console/specs/services/console-web/architecture.md` — Layered by Feature architecture; `features/<name>/{api,hooks,components,index.ts}` folder shape this task aligns files to.
- `projects/platform-console/docs/conventions/frontend-ui.md` § 3 (`StatusBadge`/tone-mapper convention — the `<domain>StatusTone` pattern this task's item 2 aligns `tenantStatusTone` to).

# Related Skills

- `.claude/skills/frontend/architecture/layered-by-feature/SKILL.md`

---

# Related Contracts

None — no API or event contract is touched by this task (pure internal rename/relocation).

---

# Target App

- `apps/console-web`

---

# Implementation Notes

- Do each of the 4 scope items as its own isolated commit/step within the task (per `platform/refactoring-policy.md` Rule 3 "One category at a time" — all four are the same category, **Rename**, but keep them separable in the diff so a review can verify each independently).
- After each move, grep the whole `apps/console-web/src/` tree for the old import path/specifier before deleting the old file, to catch any import the IDE/tsc might not flag (e.g. dynamic imports, test mocks).
- Item 4 (`erp-ops/api/types.ts` → `types/index.ts`): double-check no file imports `features/erp-ops/api/types.ts` with an explicit `.ts` extension or via a path that would break when the file becomes a directory.

---

# Edge Cases

- A moved hook file has a co-located `.test.ts`/`.test.tsx` file that must move with it (check `erp-ops`/`ledger-ops` `components/`/`hooks/` dirs for test files alongside the 5 hook files).
- `operators-keys.ts` may be imported by files outside `features/operators/hooks/` (e.g. a test file directly importing the key factory) — grep before moving.
- `erp-ops/api/types.ts` → `types/index.ts`: verify no naming collision with an existing unrelated export inside the `types/` directory's own barrel expectations.

---

# Failure Scenarios

- `tsc --noEmit` fails after a move due to a missed import path → fix the import, do not leave the old file as a re-export shim (this task's goal is to remove the inconsistency, not paper over it).
- `pnpm lint` flags a new violation (e.g. import-order) introduced by the moved files' new relative import paths → fix inline, it is in-scope for a rename task to correct the resulting import statements.
- Vitest suite fails post-move → the failure indicates a missed import or an accidental behavior change; revert and re-diagnose rather than modifying test assertions (per `platform/refactoring-policy.md` Prohibited: "Refactoring production code and test code in the same change").

---

# Test Requirements

- No new tests required — this is a pure rename/relocation with zero behavior change; existing test coverage is the verification mechanism (must pass unmodified after each move).

---

# Definition of Done

- [ ] All 4 scope items completed
- [ ] `tsc --noEmit` clean
- [ ] `pnpm lint` clean
- [ ] Full `console-web` vitest suite passing, zero test assertions modified
- [ ] Ready for review
