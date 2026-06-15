# TASK-PC-FE-096 — convert the remaining inline page-size clamps to `clampPageSize`

**Status:** done
**Area:** platform-console / console-web · **Refactor only** (Reduce Duplication — 0 behavior change, 0 contract change)
**Closure:** PR #1679 squash `5578b64f8`, 3-dim verified (state=MERGED · origin/main tip match · pre-merge all checks pass incl. Frontend unit tests/lint/E2E). 0 inline clamps remain; clampSize duplication now 0 across console-web. (Local full-suite showed load-induced flaky jsdom timeouts in unrelated files; CI isolated runner green.)
**Parent:** completes TASK-PC-FE-095 (which extracted `shared/lib/pagination.ts#clampPageSize` and converted the `function clampSize` copies, but left the **inline** `Math.min(MAX, Math.max(1, size ?? DEFAULT))` expressions untouched).

## Goal

9 inline page-size clamp expressions across 7 files still re-implement
`Math.min(MAX, Math.max(1, size ?? DEFAULT))` instead of calling the shared
`clampPageSize`. Convert them — finishing the clamp dedup (0 remaining inline
clamps). Identical arithmetic → **no behavior change**.

## Scope (7 files, 9 sites)
- `features/erp-ops/api/erp-api.ts` (2) — `clampPageSize(params.size, ERP_DEFAULT_PAGE_SIZE, ERP_MAX_PAGE_SIZE)`
- `features/erp-ops/hooks/use-erp-ops.ts` (2) — same (import already present from FE-095 delegate)
- `features/operators/api/operators-api.ts` (1) — literals → `clampPageSize(params.size, 20, 100)`
- `features/operators/hooks/use-operators.ts` (1) — `clampPageSize(params.size, 20, 100)`
- `features/scm-ops/api/scm-api.ts` (1) — `clampPageSize(size, SCM_DEFAULT_PAGE_SIZE, SCM_MAX_PAGE_SIZE)`
- `features/scm-replenishment/api/demand-planning-api.ts` (1) — `clampPageSize(size, REPL_DEFAULT_PAGE_SIZE, REPL_MAX_PAGE_SIZE)`
- `features/wms-ops/api/wms-api.ts` (1) — `clampPageSize(size, WMS_DEFAULT_PAGE_SIZE, WMS_MAX_PAGE_SIZE)`

Add `import { clampPageSize } from '@/shared/lib/pagination';` where missing
(use-erp-ops already imports it). Keep the per-feature `*_PAGE_SIZE` constants
(still passed as args).

## Hard constraints
- Identical clamp result for every input; argument order is (size, default, max).
- Do NOT touch call sites' surrounding logic, query keys, schemas, or tests.
- `100`/`20` literals in the operators slice map to `(params.size, 20, 100)`.

## Acceptance Criteria
- `grep -r "Math.min(.*Math.max(1," src/features | grep -v test` → **0** results.
- `npx vitest run` green; `npx tsc --noEmit` 0; `next lint` clean.
- 0 behavior / contract / test change.

## Related
- `shared/lib/pagination.ts#clampPageSize` (TASK-PC-FE-095)
- `platform/refactoring-policy.md` (Reduce Duplication)

## Failure Scenarios
- Wrong arg order (max/default swapped) → page-size regression.
- Leaving the constants imported-but-unused → CI lint RED (they remain used as args, so verify lint).
