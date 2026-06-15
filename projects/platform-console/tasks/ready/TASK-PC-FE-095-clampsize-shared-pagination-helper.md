# TASK-PC-FE-095 — promote the duplicated `clampSize` to a shared pagination helper

**Status:** ready
**Area:** platform-console / console-web · **Refactor only** (Reduce Duplication — 0 behavior change, 0 contract change)
**Parent:** follow-on to TASK-PC-FE-094 (ecommerce-ops dedup); same "mirror-built slices accumulated copy-paste" root cause, now across **all** domain features.

## Goal

A structurally-identical page-size clamp is copy-pasted into **23** feature files
(across ecommerce-ops, plus other `*-ops` features). Every copy is byte-equivalent
modulo the per-feature `*_MAX_PAGE_SIZE` / `*_DEFAULT_PAGE_SIZE` constants:

```ts
function clampSize(size?: number): number {
  return Math.min(<X>_MAX_PAGE_SIZE, Math.max(1, size ?? <X>_DEFAULT_PAGE_SIZE));
}
```

Promote the shared **logic** to `shared/lib/` (domain-agnostic value → `shared/`
per the console-web architecture "promote shared value to `shared/`" rule), and
collapse each local copy to a thin one-line delegate that keeps the per-feature
constants where they live. **No behavior change** (identical arithmetic).

## Scope
1. **New** `src/shared/lib/pagination.ts`:
   ```ts
   /** Clamp a requested page size to [1, max], defaulting when unset. */
   export function clampPageSize(size: number | undefined, defaultSize: number, maxSize: number): number {
     return Math.min(maxSize, Math.max(1, size ?? defaultSize));
   }
   ```
   (Mirror the exact arithmetic — `Math.min(max, Math.max(1, size ?? default))` — do not "improve" it.)
2. In each of the 23 files that define a local `clampSize`, replace the **function
   body** with a delegate, preserving the local name + signature so **every call
   site stays unchanged**:
   ```ts
   import { clampPageSize } from '@/shared/lib/pagination';
   const clampSize = (size?: number): number =>
     clampPageSize(size, <X>_DEFAULT_PAGE_SIZE, <X>_MAX_PAGE_SIZE);
   ```
   Keep each file's existing `*_DEFAULT_PAGE_SIZE` / `*_MAX_PAGE_SIZE` imports.
   If a file's lint forbids an unused `function` → `const` swap, ensure the local
   is still referenced (it is — call sites unchanged).

## Discovery
Find the 23 files with: `grep -rl "function clampSize" src/features`. Cross-check
none were missed and none have a divergent body (all are the `Math.min/Math.max`
shape; if any differs, leave it and note it).

## Hard constraints
- No behavior change — identical clamp result for every input incl. `undefined`,
  `0`, negative, over-max.
- Do NOT change call sites, query-key builders, request paths, or schemas.
- Do NOT touch test files (the existing pagination tests must pass unmodified).
- The shared helper takes (size, default, max) as params — it must NOT bake in any
  feature's constants.
- `shared/lib/` only for the new helper — not `shared/api/` (it is pure arithmetic,
  no HTTP concern).

## Procedure
1. Baseline: `npx vitest run` (full console-web) → green.
2. Add the shared helper; rewire all 23 files.
3. `npx vitest run` → green; `npx tsc --noEmit` → 0 errors; `pnpm lint` → clean.

## Acceptance Criteria
- One shared `clampPageSize`; 0 remaining duplicated clamp **bodies** (each local
  `clampSize` is a one-line delegate or removed in favour of direct calls).
- Full `npx vitest run` green; `tsc --noEmit` 0; lint clean.
- 0 change to behavior, contracts, specs, call sites, or tests.

## Related Specs
- `platform/refactoring-policy.md` (Reduce Duplication)
- `specs/services/console-web/architecture.md` — "공유 가치는 `shared/` 로 승격"

## Edge Cases
- A file may `import` the constants under different names — preserve whatever it imports.
- Some files define `clampSize` in BOTH `api/*.ts` and `hooks/*.ts` of the same
  feature (e.g. ecommerce orders) — both get the delegate; both keep their own constant imports.

## Failure Scenarios
- Changing the arithmetic (e.g. `Math.max(0,…)` vs `Math.max(1,…)`) → off-by-one page-size regression.
- Skipping `pnpm lint` → CI "Frontend lint & build" + "Frontend unit tests" RED.
