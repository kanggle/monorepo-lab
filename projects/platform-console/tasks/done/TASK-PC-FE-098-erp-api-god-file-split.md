# TASK-PC-FE-098 — split the `erp-ops/api/erp-api.ts` god-file into cohesive modules

**Status:** done
**Area:** platform-console / console-web · **Refactor only** (Reduce Module Size / Long File — 0 behavior change, 0 contract change)
**Closure:** PR #1691 squash `d4e99785175173765f9767d9ac53d6ba4556c40b`, 3-dim verified (state=MERGED · origin/main tip match · pre-merge all checks pass incl. Frontend lint/build + unit + E2E smoke + Build & Test). 1,113-line monolith → erp-client.ts (348) + erp-masters-api.ts (628) + erp-orgview-api.ts (77) + erp-delegation-facts-api.ts (73) + barrel (27); 30 exports import-stable; tsc 0 / lint 0 / vitest 162 files · 1943 tests green (baseline count; erp suite 96 incl. relocated 429-guard).
**Parent:** the `features/erp-ops` surface accreted across TASK-PC-FE-010/046/048/049/055 by appending each new master / read-model binding to a single `erp-api.ts`, which grew to ~1,113 lines (the largest non-generated source in console-web).

## Goal

Split the single ~1,113-line `features/erp-ops/api/erp-api.ts` into cohesive
units **without changing any externally observable behavior** (HTTP calls,
headers, error semantics, log-event strings, Zod-validated shapes, `asOf`
thread-through all preserved). Pure structural move — no logic edited.

The module path `@/features/erp-ops/api/erp-api` stays the **stable public
surface**: `erp-api.ts` becomes a barrel that `export *`s the new units, so all
30 exported functions resolve unchanged for every importer (20 `app/api/erp/**`
route handlers + `erp-state.ts` + 4 test files) — **0 import-site edits**.

### Split layout
- `api/erp-client.ts` — the hardened `callErp<T>()` HTTP core + the erp FLAT
  error-envelope parser (`parseErpError`) + the `CallOptions` shape + the
  per-domain-credential / resilience / **"NO 429 handling"** module narrative.
  This is the file the `erp-api.test.ts` grep-guard pins (see Hard constraints).
- `api/erp-masters-api.ts` — the 5 masters' read + write functions
  (departments / employees / job-grades / cost-centers / business-partners),
  the `pageParams` / `listQs` / `detailQs` query-string helpers, the
  `parse*Data` mutation-envelope parsers, and `compact`.
- `api/erp-orgview-api.ts` — read-model employee org-view reads
  (`orgViewListQs` + `listEmployeeOrgViews` + `getEmployeeOrgView`).
- `api/erp-delegation-facts-api.ts` — read-model delegation-fact reads
  (`delegationListQs` + `listDelegationFacts` + `getDelegationFact`).
- `api/erp-api.ts` — barrel only (`export *` × 3).

`erp-client.ts` is a leaf (imports no sibling erp module); the three entity
modules import `callErp` from it. No circular import.

## Hard constraints (behavior preservation)
- **Public export set unchanged** — the 30 `export async function`s remain
  importable from `@/features/erp-ops/api/erp-api`. `callErp` / `parseErpError`
  / `CallOptions` / the qs + parse helpers stay **non-exported** (internal),
  exactly as before (callErp is `export`ed only from `erp-client.ts` for the
  three entity modules; it was never part of the `erp-api.ts` public surface
  and the barrel does NOT re-export it).
- **Log-event strings byte-identical** — `erp_no_gap_session` / `erp_ok` /
  `erp_unauthorized` / `erp_forbidden` / `erp_degraded` / `erp_request_error`
  / `erp_timeout` / `erp_error`, all sanitised `logPath` route shapes, and the
  redaction discipline are moved verbatim.
- **No 429 handling stays absent** — the client carries no Retry-After /
  backoff / RateLimited branch. `erp-api.test.ts`'s grep-guard reads the
  client **source text**; since `callErp` moved to `erp-client.ts`, the guard's
  `readFileSync` path is repointed `erp-api.ts` → `erp-client.ts` (1 line) so
  it keeps protecting the real client rather than vacuously passing on the
  empty barrel. This is the only test edit (intent-preserving path follow).
- No change to `shared/api/*`, `shared/lib/*`, route handlers, hooks,
  components, pages, the producer contracts, or any spec.
- Production code structure only — no test-logic change beyond the 1-line
  guard path follow above.

## Procedure
1. Baseline (green at authoring): `npx vitest run tests/unit/erp-*.test.ts` +
   credential tests → pass.
2. Create `erp-client.ts` / `erp-masters-api.ts` / `erp-orgview-api.ts` /
   `erp-delegation-facts-api.ts` by moving the verbatim code blocks; convert
   `erp-api.ts` to the barrel.
3. Repoint the `erp-api.test.ts` 429-guard `readFileSync` path to
   `erp-client.ts`.
4. `tsc --noEmit` (0) + `next lint` (0) + `npx vitest run` (full) green.

## Acceptance Criteria
- `npx tsc --noEmit` → 0 errors (proves all 20 route handlers + state + tests
  still resolve the moved exports).
- `npx next lint` → 0 warnings/errors.
- `npx vitest run` → all green, **same test count as baseline** (162 files /
  1943 tests), including the relocated 429-guard.
- `erp-api.ts` is a barrel (27 lines; no logic remains in it). The former
  ~1,113-line monolith splits into `erp-client.ts` (348) + `erp-masters-api.ts`
  (628, the 5-masters CRUD cohesive unit) + `erp-orgview-api.ts` (77) +
  `erp-delegation-facts-api.ts` (73). Largest erp module drops 1,113 → 628
  (−44%). (A further masters read/write split is a possible follow-up but kept
  as one cohesive unit here per the 1-file-1-task discipline.)
- 0 change to behavior, contracts, specs, route handlers, hooks, components,
  pages, log-event strings.

## Related Specs / Contracts
- `platform/refactoring-policy.md` (Long File / Reduce Module Size,
  behavior-preserving).
- `specs/services/console-web/architecture.md` (Layered by Feature; the split
  stays **feature-internal** to `erp-ops/api/` — no promotion to `shared/`,
  since `callErp` is erp-credential/envelope specific).
- `specs/contracts/console-integration-contract.md` § 2.4.8 (the preserved erp
  behavior: per-domain credential reuse of § 2.4.5, E3 asOf thread-through,
  FLAT error envelope, no-429).

## Edge Cases
- The masters list reads use the manual `pageParams` clamp; the two read-model
  qs builders use `clampPageSize` — keep each with its only consumer (masters
  vs orgview/delegation), do NOT unify (different clamp call shapes preserved).
- `erp-client.ts` must NOT import `clampPageSize` / the Zod schemas (it never
  used them) — only `getServerEnv` / `getDomainFacingToken` / `logger` /
  `newRequestId` / `ApiError` / `ErpUnavailableError`.
- The barrel must `export *` only — re-exporting `callErp` would widen the
  public surface (a behavior change to the module contract).

## Failure Scenarios
- A changed log-event string → silent observability regression (review the
  diff; tests assert several but not all).
- Re-exporting `callErp` / helpers from the barrel → public-surface widening.
- Leaving the 429-guard pointed at the barrel → vacuous pass (guard protects
  nothing); repoint to `erp-client.ts`.
- A circular import (entity module ↔ barrel) → keep `erp-client.ts` a leaf;
  entity modules import the client, never the barrel.
