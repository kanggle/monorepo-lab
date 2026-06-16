# TASK-FE-074 — Retire the dead GAP signup auth-path in web-store e2e; converge on the seeded-consumer login

**Status:** ready

**Type:** TASK-FE (project-internal — `projects/ecommerce-microservices-platform/apps/web-store/e2e/` only)

**Analysis model:** Opus 4.8 / **Recommended impl model:** Sonnet 4.6 (test-helper cleanup; requires a full-stack `SKIP_GAP_E2E=0` run to verify the behavior change)

---

## Goal

The cross-suite e2e diagnosis found that web-store's `e2e/helpers/auth.ts` carries a **dead login path**: `signupAndLogin()` → `completeGapSignIn()` drives a GAP "create-or-login" page that — per the helper's own inline comment (`auth.ts` ~line 65) — **GAP no longer renders**. The three golden specs that use it (`golden-flow.spec.ts`, `cart-management.spec.ts`, `wishlist.spec.ts`) are therefore broken against the current GAP whenever `SKIP_GAP_E2E=0`; in CI they are **skipped by default** (`SKIP_GAP_E2E` defaults to `'1'`), so the rot is invisible. The working real-GAP path is `loginAsSeededConsumer()` + `SEEDED_CONSUMER` (used only by `rp-initiated-logout.spec.ts`).

## Scope

**In scope** — `apps/web-store/e2e/` only:

1. Migrate `golden-flow.spec.ts`, `cart-management.spec.ts`, `wishlist.spec.ts` from `signupAndLogin()` to `loginAsSeededConsumer()` (the proven Spring-Security-default-form path), using the existing `SEEDED_CONSUMER` fixture (or a per-spec seeded consumer if cart/wishlist state must be isolated between specs — decide during impl, document).
2. Delete `completeGapSignIn()` and, if no caller remains, `signupAndLogin()` + `uniqueUser()` (confirm zero references first).
3. Normalize the `shouldSkipGap` invocation: `rp-initiated-logout.spec.ts` calls `shouldSkipGap()` (invoked) while the others pass `shouldSkipGap` (reference) — pick one form.
4. Replace the silent `.catch(() => undefined)` on the IdP-host `waitForURL` in `rp-initiated-logout.spec.ts` with an explicit assertion (or a documented tolerance) so a missing GAP round-trip fails loudly rather than relying on the downstream `#username` check.

**Folded sub-item (config, low priority — decide, do not blindly "align"):** the diagnosis flagged web-store using `PLAYWRIGHT_BASE_URL` while the console suites use `CONSOLE_BASE_URL`, plus `retries: 1` vs `2` and `trace: retain-on-failure` vs `on`. These are arguably **correct per-app choices** (web-store ≠ console; serial `workers:1` web-store wants fewer retries). Only change one if a concrete reason emerges — otherwise record "intentional divergence" in the config comment and close the item.

**Out of scope:** the shared OIDC-PKCE login extraction across suites (TASK-MONO-281 gate); seed SQL credential de-duplication.

## Acceptance Criteria

- **AC-1** — `golden-flow`, `cart-management`, `wishlist` log in via `loginAsSeededConsumer()`; `completeGapSignIn()` is deleted; `signupAndLogin()`/`uniqueUser()` deleted iff unreferenced (else kept with a justifying comment).
- **AC-2 (behavior verified, not assumed)** — a full-stack run with `SKIP_GAP_E2E=0` (GAP + web-store + gateway up) passes all three migrated specs end-to-end. This is the authoritative gate; `playwright test --list` alone is insufficient (the change alters the executed login).
- **AC-3** — `pnpm lint` + `tsc` + `vitest` (web-store) green locally before push (per memory `env_console_web_local_verify_needs_lint` — CI fails on unused-vars that tsc/vitest miss).
- **AC-4** — `shouldSkipGap` usage is consistent across all specs; the `rp-initiated-logout` silent-catch is replaced or explicitly justified.

## Related Specs

- `projects/ecommerce-microservices-platform/specs/` web-store auth/login feature (the seeded-consumer flow this converges on).
- `apps/web-store/e2e/helpers/auth.ts` — the helper under cleanup; its inline comments document the GAP page retirement.

## Related Contracts

None — no API/event change; the login form + `/login` redirect contract is unchanged.

## Edge Cases

- **Per-spec cart/wishlist isolation** — if all three specs share one `SEEDED_CONSUMER`, a leftover cart/wishlist row from a prior failed run could bleed across specs; decide between a clean-up step and a per-spec seeded user.
- **`SEEDED_CONSUMER` seed presence** — the migrated specs now hard-depend on `e2e/fixtures/iam-consumer-seed.sql` being applied; a skipped seed turns them from "skipped" to "failing" (intended — that is the point).

## Failure Scenarios

- Deleting `signupAndLogin` while a spec still imports it → tsc/lint break (AC-3).
- Migrating without a real `SKIP_GAP_E2E=0` run → AC-2 unmet; the "fix" could itself be broken and invisibly skipped again.
