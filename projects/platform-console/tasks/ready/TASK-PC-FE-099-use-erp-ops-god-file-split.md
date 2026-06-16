# TASK-PC-FE-099 — split the `erp-ops/hooks/use-erp-ops.ts` god-file into cohesive modules

**Status:** ready
**Area:** platform-console / console-web · **Refactor only** (Reduce Module Size / Long File — 0 behavior change, 0 contract change)
**Parent:** sibling of TASK-PC-FE-098 (the `erp-api.ts` split). The client-side `use-erp-ops.ts` accreted the same way across TASK-PC-FE-010/046/048/049/051/054/055 — one file grew to ~1,250 lines (the largest hooks module in console-web).

## Goal

Split the single ~1,250-line `features/erp-ops/hooks/use-erp-ops.ts` into
cohesive React-Query hook modules **without changing any externally observable
behavior** (proxy URLs, query keys, invalidation prefixes, `retry: false`,
seeding/staleTime, `asOf` thread-through, Zod-validated shapes all preserved).
Pure structural move — no hook logic edited.

The module path `@/features/erp-ops/hooks/use-erp-ops` stays the **stable public
surface** via a barrel, so every importer (the erp-ops components + the feature
`index.ts` + `AsOfPicker`) resolves the same hooks unchanged — **0 import-site
edits**.

### Split layout
- `hooks/use-erp-shared.ts` — the E3 asOf machinery (`useAsOf` URL-param source
  of truth + `useThreadedAsOf`), the masterdata list/detail query-string
  builders (`buildListQs` / `buildDetailQs`), and `clampSize`. Exports the
  internal helpers so the sibling modules can import them; only `useAsOf` /
  `UseAsOfResult` are re-exported from the barrel (the rest stay
  feature-internal).
- `hooks/use-erp-masters.ts` — the 5 masters' read hooks + the read-model
  employee org-view read + all masters write mutations (department write pilot
  + the other-four-masters writes) + the prefix-invalidation helpers.
- `hooks/use-erp-approval.ts` — approval-workflow reads (list / inbox / detail)
  + 5 mutations (create + 4 state-machine transitions) + `clampApprovalSize`.
- `hooks/use-erp-delegation.ts` — delegation grant hooks (list + create +
  revoke) + the read-model delegation-fact reads.
- `hooks/use-erp-ops.ts` — barrel: the module narrative + selective re-export
  (`useAsOf`/`UseAsOfResult` from shared, `export *` from the three hook
  modules).

`use-erp-shared.ts` is a leaf (imports no sibling hook module); masters +
delegation import from it. No circular import.

## Hard constraints (behavior preservation)
- **Public export set unchanged** — every `use*` hook + the `*Args`/`UseAsOfResult`
  interfaces remain importable from `@/features/erp-ops/hooks/use-erp-ops`.
  The internal helpers (`clampSize`, `buildListQs`, `buildDetailQs`,
  `useThreadedAsOf`, the `invalidate*` / `fetch*` / `clampApprovalSize` /
  `useApprovalTransition` / `parseApprovalDetail` functions) stay non-public:
  helpers used only inside one module stay non-exported there; the shared
  helpers are exported from `use-erp-shared.ts` ONLY so siblings can import them,
  and the barrel does NOT re-export them (no public-surface widening).
- **`'use client'` preserved** — every module that defines hooks carries the
  `'use client'` directive (shared / masters / approval / delegation), and the
  barrel keeps it too.
- **No 429 / retry behavior change** — `retry: false`, no `refetchInterval`, no
  `refetchOnWindowFocus`; the `READ_QUERY_REFETCH` spread is moved verbatim.
- No change to `shared/api/*`, the `erp-keys` / `*-types` modules, proxy route
  handlers, components, pages, contracts, or specs.
- **Production code only — 0 test changes.** (Unlike the FE-098 erp-api split,
  no test reads this file's source text and no test imports its internal
  helpers, so no test edit is needed.)

## Procedure
1. Baseline (green at authoring): `npx vitest run` (full console-web) → pass.
2. Create the four hook modules by moving the verbatim code blocks; convert
   `use-erp-ops.ts` to the barrel.
3. `tsc --noEmit` (0) + `next lint` (0) + `npx vitest run` (full) green.

## Acceptance Criteria
- `npx tsc --noEmit` → 0 errors (proves all importers still resolve the moved
  hooks).
- `npx next lint` → 0 warnings/errors.
- `npx vitest run` → all green, **same test count as baseline** (162 files /
  1943 tests).
- `use-erp-ops.ts` is a barrel (~52 lines; no hook logic remains). The former
  ~1,250-line monolith splits into `use-erp-shared.ts` (108) +
  `use-erp-masters.ts` (755) + `use-erp-approval.ts` (197) +
  `use-erp-delegation.ts` (181). Largest hooks module drops 1,250 → 755 (−40%).
  (A further masters read/write split is a possible follow-up but kept as one
  cohesive unit here per the 1-file-1-task discipline.)
- 0 change to behavior, contracts, specs, route handlers, components, pages,
  query keys, invalidation prefixes.

## Related Specs / Contracts
- `platform/refactoring-policy.md` (Long File / Reduce Module Size,
  behavior-preserving).
- `specs/services/console-web/architecture.md` (Layered by Feature; Server vs
  Client Components — React Query is client-only; the split stays
  feature-internal to `erp-ops/hooks/`).
- `specs/contracts/console-integration-contract.md` § 2.4.8 (the preserved
  client behavior: per-domain credential via proxy, E3 asOf thread-through,
  no-429, department write pilot).

## Edge Cases
- `clampSize` is used by masters AND `useDelegationFacts`; it lives in
  `use-erp-shared.ts` so both import it. The org-view + delegation-fact list
  fetchers also call `clampPageSize` inline — keep those exactly as-is.
- `useThreadedAsOf` is used only by masters/org-view hooks (approval +
  delegation grants have no asOf); approval/delegation modules must NOT import
  it (would be an unused import → lint failure).
- The barrel uses `export { useAsOf, type UseAsOfResult }` (named) from shared,
  NOT `export *` (which would leak the internal qs/clamp/threading helpers into
  the public surface).
- `'use client'` must be the first statement in each hook module (before the
  imports), else Next.js treats it as a server module and the hooks break.

## Failure Scenarios
- `export *` from `use-erp-shared` in the barrel → public-surface widening
  (internal helpers leak); use a named re-export.
- A missing `'use client'` in a hook module → runtime "hooks in server
  component" error (build/E2E RED).
- Importing `useThreadedAsOf` into approval/delegation → unused-import lint
  failure (CI Frontend lint gate).
- A circular import (sibling ↔ barrel) → keep `use-erp-shared.ts` a leaf;
  hook modules import shared, never the barrel.
