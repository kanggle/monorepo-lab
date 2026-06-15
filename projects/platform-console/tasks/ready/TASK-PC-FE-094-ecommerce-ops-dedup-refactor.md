# TASK-PC-FE-094 — `features/ecommerce-ops` duplication refactor (api call-core + section-state)

**Status:** ready
**Area:** platform-console / console-web · **Refactor only** (Reduce Duplication — 0 behavior change, 0 contract change)
**Parent:** ADR-MONO-031 ecommerce-ops console consolidation (TASK-PC-FE-081…090 shipped the 8 slices by mirroring each other → accumulated near-identical boilerplate).

## Goal

Collapse the copy-paste duplication left by the mirror-built `features/ecommerce-ops`
slices, **without changing any externally observable behavior** (HTTP calls, error
semantics, log-event strings, Zod-validated shapes all preserved). Two units, both
category **Reduce Duplication**:

### Unit 1 — shared ecommerce call core (`api/ecommerce-client.ts`)
Each of the 8 `api/*-api.ts` files defines its own near-identical
`call<Slice><T>()` + `parse<Slice>Error()` (token via `getDomainFacingToken()`,
headers with **no** `X-Tenant-Id`/`Idempotency-Key`, `AbortController` timeout on
`ECOMMERCE_TIMEOUT_MS`, status branch 401→`ApiError`/403→`ApiError`/503→
`EcommerceUnavailableError`/!ok→`ApiError`/AbortError→timeout/else→network, flat
`{code,message,timestamp}` parser). Extract a single feature-internal
`callEcommerce<T>(opts, parse?, label)` + `parseEcommerceError(res, label)` and
rewire all 8 slices to thin typed wrappers.

### Unit 2 — shared section-state error mapping (`api/section-state.ts`)
Each of the `api/*-state.ts` files repeats an identical `mapSectionError`
(401→`redirect('/login')`, 403→forbidden, `EcommerceUnavailableError`→degraded,
else→degraded) and the detail-state 404→notFound + forbidden/degraded mapping.
Extract generic helpers and rewire each state file.

## Hard constraints (behavior preservation)
- **Log-event strings unchanged**: `ecommerce_<label>_ok` / `_unauthorized` /
  `_forbidden` / `_degraded` / `_request_error` / `_no_gap_session` / `_timeout` /
  `_error` must be emitted with the **exact same** `<label>` each slice uses today
  (seller/user/product/order/promotion/shipping/notification/image — read each
  file to capture its exact prefix; do NOT guess). Pass the label as a parameter.
- **`EcommerceUnavailableError` reason/code/message** strings preserved per slice.
- No change to `Method` unions actually used, request paths, query building,
  `encodeURIComponent`, clamp helpers, or Zod schemas/`.parse()` call sites.
- No change to `shared/api/*`, route handlers (`app/api/ecommerce/**`), hooks,
  components, pages, or any contract/spec.
- Production code only — do NOT edit test files (adjust separately only if a test
  asserts an internal symbol that moved; prefer keeping public exports stable).

## Procedure
1. Baseline (already green at task authoring): `npx vitest run tests/unit/ecommerce` → 31 files / 360 tests pass.
2. Implement Unit 1; re-run the ecommerce suite → green; `pnpm lint` + `tsc --noEmit` clean. Commit.
3. Implement Unit 2; re-run; lint + tsc clean. Commit.
4. Full `npx vitest run` (whole console-web) → green.

## Acceptance Criteria
- `npx vitest run` → all green (no test-logic changes).
- `pnpm --filter console-web lint` clean (no-unused-vars etc. — mandatory; CI gate).
- `tsc --noEmit` → 0 errors.
- Net line reduction in `features/ecommerce-ops/api/` (duplication removed).
- 0 change to behavior, contracts, specs, route handlers, hooks, components, pages.

## Related Specs / Contracts
- `platform/refactoring-policy.md` (Reduce Duplication, behavior-preserving)
- `specs/services/console-web/architecture.md` (Layered by Feature; `shared/` promotion rule — here the shared core stays **feature-internal** to `ecommerce-ops`, not promoted to `shared/`, since it is ecommerce-credential/envelope specific)
- `console-integration-contract.md` § 2.4.10–§ 2.4.10.5 (the preserved behavior)

## Edge Cases
- `images-api.ts` presigned upload-url / S3 PUT path may diverge from the plain
  call core — keep its special steps; only share the parts that are identical.
- Void-return mutations (DELETE) call the core with `parse===undefined` — keep that path.
- `notFound` codes differ per slice (SELLER_NOT_FOUND / USER_PROFILE_NOT_FOUND / …)
  — the detail helper maps by HTTP 404 status, not by code, so this is uniform.

## Failure Scenarios
- A changed log-event string → silent observability regression (tests may not catch; review the diff).
- Promoting the shared core to `shared/api/` → wrong layer (it is ecommerce-specific; keep feature-internal).
- Skipping `pnpm lint` → CI "Frontend lint & build" + "Frontend unit tests" RED.
