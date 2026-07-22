# TASK-PC-FE-249 — 3 console menu screens lack render coverage + stale "stub" nav comments

- **Type**: TASK-PC-FE (frontend test coverage + doc accuracy)
- **Status**: review
- **Service**: platform-console `console-web`
- **Analysis model**: Opus 4.8 · **Impl model**: Sonnet (vitest render tests + comment fixes — mechanical)

## Goal

Close two accuracy gaps surfaced by the 2026-07-19 console menu verification
sweep (47-menu coverage matrix):

1. **Three real-feature menu screens have NO screen-render test** — only
   state/api/proxy/nav unit tests exist; the screen component is never mounted in
   a test, so a render regression (e.g. a crash on mount) would ship green:
   - `/partnerships` — `partnerships-proxy` + `partnerships-client` tested; the
     screen is never rendered.
   - `/ecommerce/users` — state/api/proxy/nav tested; no screen render.
   - `/ecommerce/notifications/templates` — state/api/proxy/nav tested; no screen render.
2. **Stale nav "stub" comments** in `src/shared/ui/console-nav-config.ts`: the
   comments label `/tenants`, `/permissions`, `/permission-sets` as
   "TASK-PC-FE-225 stubs," but all three are now **real feature screens**
   (FE-226/227/228: `TenantsScreen` / `PermissionsScreen` / `PermissionSetsScreen`).
   The **only** genuine stub is `/operator-groups` (renders a "준비 중입니다"
   placeholder). The stale comments mislead the next reader.

**All three screens were live-verified rendering OK** (SSR sweep as super-admin,
2026-07-19) — this task adds regression guards + corrects docs, it is NOT a fix
for a broken screen.

## Scope

- **In**:
  - Add a vitest render test per screen (`/partnerships`, `/ecommerce/users`,
    `/ecommerce/notifications/templates`) that mounts the screen component with a
    representative data fixture and asserts its primary structure renders (heading
    / key testid / table or empty-state), mirroring the existing screen render
    tests for sibling menus (e.g. the ecommerce products/promotions screen tests).
  - Correct the `console-nav-config.ts` comments: drop the "stub" framing from
    `/tenants` `/permissions` `/permission-sets` (point at FE-226/227/228 real
    features); keep `/operator-groups` correctly marked as the lone stub.
- **Out**: e2e coverage (that is `TASK-PC-FE-248`); changing any screen behavior
  or nav placement; the guide/static pages (already low-risk).

## Acceptance Criteria

- **AC-1**: each of the 3 screens has a vitest render test that mounts the actual
  screen component and asserts its primary rendered structure; the tests are in
  the console-web vitest suite (Frontend unit CI lane) and pass.
- **AC-2**: `console-nav-config.ts` no longer calls `/tenants` `/permissions`
  `/permission-sets` stubs; `/operator-groups` remains marked as the genuine stub.
- **AC-3**: `pnpm test` (vitest) + `pnpm lint` + `pnpm build` green; no behavior
  change (render tests only, comment-only nav edit).

## Related

- Provenance: 2026-07-19 console menu verification sweep (matrix verdicts
  UNVERIFIED = partnerships / ecommerce-users / ecommerce-notifications; stale
  stub comments flagged during the same sweep).
- Sibling: `TASK-PC-FE-248` (dormant federation e2e specs).
- Canonical console UI conventions: `platform-console/docs/conventions/frontend-ui.md`.

## Edge Cases / Failure Scenarios

- A render test that mocks too much proves nothing — mount the real screen with a
  realistic fixture (follow the sibling screens' render-test pattern, not a
  shallow smoke).
- The nav comment edit is comment-only — do NOT change `href`/`testid`/`label`
  (those are asserted by `overview-consolidation.spec.ts` + sidebar unit tests).
