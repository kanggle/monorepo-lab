# TASK-PC-FE-145 — split the `scm-ops/api/scm-api.ts` god-file (endpoint-group barrel)

**Status:** done
**Area:** platform-console / console-web · **Refactor only** (Reduce Module Size / Long File — 0 behavior change, 0 contract change)
**Owner:** frontend-engineer
**Task Tags:** refactoring, console-web, scm-ops, behavior-preserving
**Dependency Markers:** continuation of the console-web god-file split series (PC-FE-098 erp-api, PC-FE-102 ledger-api). `scm-api.ts` is the direct twin of `erp-api.ts` / `ledger-client.ts` (a single server-side hardened-client module) and was 413 lines (TASK-PC-FE-008).

## Goal

Split the single 413-line `features/scm-ops/api/scm-api.ts`
**without changing any externally observable behavior** (every wire path,
header set, error-envelope parse, 429 bounded-backoff, `X-Cache` read, and
structured log event preserved). The former single module → a hardened HTTP
**core leaf** + two **endpoint-group** sub-modules, with `scm-api.ts` kept as
the stable public **barrel** (the PC-FE-098 erp-api playbook).

The public surface is the set of exported endpoint functions imported from
`@/features/scm-ops/api/scm-api` (the `/api/scm/**` route handlers +
`scm-state.ts` + `scm-api.test.ts`). It is unchanged — **0 import-site edits**.

## Scope

### In
- `api/scm-client.ts` — the hardened `callScm` HTTP core + the scm FLAT
  error-envelope parser (`parseScmError`) + the 429 `Retry-After` reader
  (`parseRetryAfter`) + the `X-Cache` reader (`readCacheHeader`) + the
  `pageParams` helper + the per-domain-credential / resilience / read-only
  narrative. Barrel-INTERNAL (NOT re-exported through `scm-api.ts`).
- `api/scm-procurement-api.ts` — the procurement PO reads
  (`listPurchaseOrders` / `getPurchaseOrder`).
- `api/scm-inventory-visibility-api.ts` — the inventory-visibility reads
  (`getSnapshot` / `getSkuBreakdown` / `getStaleness` / `getNodes`), each
  surfacing the REQUIRED S5 `meta.warning`; the per-SKU read surfaces `X-Cache`.
- `api/scm-api.ts` — the public barrel: `export *` from the two endpoint
  sub-modules ONLY (so the public surface is EXACTLY the 6 endpoint functions).

### Out
- No change to `api/types.ts`, `api/scm-state.ts`, the hooks, the proxy routes,
  `shared/*`, contracts, or specs.
- The core leaf (`callScm` + helpers) is NOT exported through the barrel —
  re-exporting it would widen the public surface and break the
  `scm-api.test.ts` `Object.keys(mod)` guard.
- No test changes (the test imports the 6 endpoint functions by name from
  `@/features/scm-ops/api/scm-api` and pins the exact export set).

## Acceptance Criteria
- `npx tsc --noEmit` → 0 errors.
- `npx next lint` → 0 warnings/errors.
- `npx vitest run scm` → all green (5 files / 52 tests; scm-api suite 21,
  including the `Object.keys(mod).sort()` public-surface guard that pins exactly
  `getNodes` / `getPurchaseOrder` / `getSkuBreakdown` / `getSnapshot` /
  `getStaleness` / `listPurchaseOrders`).
- `scm-api.ts` drops 413 → 32 lines (barrel); extracted modules are
  `scm-client.ts` (336) + `scm-procurement-api.ts` (57) +
  `scm-inventory-visibility-api.ts` (87).
- 0 change to behavior, wire paths, headers, error parsing, log events,
  contracts, specs, route handlers, hooks.

## Related Specs
- `platform/refactoring-policy.md` (Long File / Reduce Module Size,
  behavior-preserving; cohesive endpoint-group decomposition + stable barrel).
- `specs/services/console-web/architecture.md` (Layered by Feature; the split
  stays feature-internal to `scm-ops/api/`).

## Related Contracts
- `specs/contracts/console-integration-contract.md` § 2.4.6 (reuse of § 2.4.5
  per-domain credential — IAM OIDC access token via `getDomainFacingToken()`,
  NEVER `getOperatorToken()`; tenant from JWT claim, NO `X-Tenant-Id`; STRICTLY
  READ-ONLY pure GETs; scm FLAT error envelope; 429 ONE bounded backoff; S5
  `meta.warning` REQUIRED + surfaced; `X-Cache` freshness honesty).

## Target Service
console-web (platform-console).

## Architecture
Layered by Feature — the split is internal to `scm-ops/api/`; route handlers +
`scm-state.ts` import the unchanged `scm-api.ts` barrel. The endpoint
sub-modules import the shared core leaf (`scm-client.ts`); the barrel ↔ leaf
relationship is acyclic (leaf imports only `types.ts` + `shared/*`; the barrel
never imports the leaf directly — only the endpoint modules do).

## Edge Cases
- The `scm-api.test.ts` `Object.keys(mod)` guard asserts the EXACT public export
  set (6 endpoint functions). The barrel `export *` covers ONLY the two endpoint
  sub-modules; the core leaf's `callScm` / `readCacheHeader` / `pageParams` /
  `parseScmError` are NOT re-exported (no surface widening, no vacuous pass).
- `parseRetryAfter` and `MAX_RETRY_AFTER_SECONDS` stay module-private in
  `scm-client.ts` (only `callScm` / `readCacheHeader` / `pageParams` are
  `export`ed for the endpoint sub-modules to consume).
- The two PO endpoints' `{ data?: unknown }` envelope unwrap + the
  inventory-visibility endpoints' top-level schema parse are moved verbatim (the
  per-SKU `readCacheHeader(res)` call still threads the second `callScm` return
  value `res`).

## Failure Scenarios
- Re-exporting the core leaf through the barrel → the `Object.keys(mod)` guard
  fails (public-surface widening) — keep the leaf barrel-internal.
- A changed wire path / header / error-parse / log-event → the scm-api unit /
  scm-proxy / scm-state suites break; move every line verbatim.
- A barrel that imports the leaf AND the leaf importing the barrel → circular
  import; the leaf imports only `types.ts` + `shared/*`.
- Splitting the per-SKU read without threading `res` → `X-Cache` lost (freshness
  honesty breach); keep the `{ raw, res }` destructure + `readCacheHeader(res)`.

## Definition of Done
- tsc 0 / lint 0 / `vitest run scm` green (5 files / 52 tests, incl. the
  `Object.keys` public-surface guard).
- `scm-api.ts` barrel import-stable; 0 test changes; 0 contract/spec change.
- Committed + pushed; PR opened by the orchestrator.
