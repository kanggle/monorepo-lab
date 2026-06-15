# TASK-INT-025 — Remove web-store `account_type` residue (vestigial session field + stale e2e) post ADR-035

**Status:** review

**Type:** TASK-INT
**Analysis model:** Opus 4.8 / **Recommended impl model:** Opus (cross-cutting frontend/e2e cleanup with a retire-vs-re-point decision; net-zero but the consumer-role guard contract must be preserved exactly)

---

## Goal

Finish the `account_type → roles` transition (ADR-MONO-032 D5 step 4 + ADR-MONO-035, COMPLETE 2026-06-15) on the **web-store frontend + its e2e suite**, the last ecommerce surface still carrying `account_type` residue after the spec sweep (PR #1603) and the admin-controller javadoc alignment (TASK-BE-382, PR #1606).

Post-ADR-035 the IAM IdP **no longer emits the `account_type` claim**; `roles` is the sole authorization axis. The web-store storefront guard was already rewritten to role-based (`roles ∋ CUSTOMER`, ADR-035 4b-1), but two kinds of residue remain:

1. **Vestigial `accountType` session field** — `auth.ts` / `types.d.ts` / `session.ts` still thread an `accountType` field through `profile → jwt → session`. Post-ADR-035 it is **always null** (the claim is gone) and **no code reads it for any decision** (the guard keys on `roles`). Dead weight that misleads a reader into thinking `account_type` is still meaningful.
2. **Stale e2e** — `e2e/account-type-claim.spec.ts` (TASK-INT-024) asserts `session.accountType === 'CONSUMER'`, i.e. it gates the **removed** account_type-claim contract; it would **fail** against a post-ADR-035 GAP build (the claim is no longer emitted). `account-type-guard.spec.ts` + `helpers/auth.ts` describe the cross-app guard via the obsolete `account_type` mechanism though the guard itself persists (now role-based).

This is **net-zero**: the consumer-role guard behavior and the `account_type_mismatch` **error-code string** (a stable UI contract read by `LoginForm`) are preserved exactly. The decisions (account_type removal, roles-only) live in ADR-032/035 — no new architecture decision here (HARDSTOP-09 clean).

## Decision (retire vs re-point)

- **`account-type-claim.spec.ts` → RETIRE (delete).** Its entire premise (TASK-INT-024: "the `account_type=CONSUMER` claim survives the GAP→NextAuth round-trip", on top of BE-329/BE-330 which *emitted* the claim) is a contract ADR-035 4b **removed**. Re-pointing it to assert `roles` would be a different test under a misleading name; the consumer-role round-trip is already exercised by the guard specs (a non-`CUSTOMER` token is anonymized → bounced).
- **`account-type-guard.spec.ts` + `helpers/auth.ts` → RE-POINT (keep, reframe).** The cross-app guard still exists (an operator whose web-store token lacks `CUSTOMER` is rejected); only the *mechanism* changed (role absence, not an `account_type` claim). Update the docstrings/`describe` to roles-based framing; keep the test flow + the `account_type_mismatch` redirect assertion. The skip-gated e2e `TestUser.accountType` fixture hint is left in place (e2e-only, `SKIP_GAP_E2E`-gated, not production/CI) to minimize churn.

## Scope

**In scope:**

1. **`src/shared/auth/auth.ts`** — remove the `accountType` field from `IamOidcProfile`, the `profile()` return, and the `jwt()` / `session()` callbacks (incl. the degraded-session branch). Keep the role-based `hasConsumerRole` guard and the `/login?error=account_type_mismatch` redirect. Update field-list comments.
2. **`src/shared/auth/types.d.ts`** — drop `accountType` from the augmented `Session` / `User` / `JWT` interfaces + the header comment.
3. **`src/shared/auth/session.ts`** — drop `accountType` from `WebStoreSession` / `EMPTY` / `getWebStoreSession()`.
4. **`src/__tests__/auth-context.test.tsx`, `src/__tests__/logout-cart-integration.test.tsx`** — remove the `accountType: 'CONSUMER'` lines from the mock-session fixtures (no assertion targets it).
5. **`e2e/account-type-claim.spec.ts`** — delete (retire).
6. **`e2e/account-type-guard.spec.ts`, `e2e/helpers/auth.ts`** — re-point comments/`describe` to roles-based framing.

**Out of scope (unchanged):**

- The `account_type_mismatch` **error-code string** + `LoginForm` mismatch banner (`features/auth/ui/LoginForm.tsx`, `login-form.test.tsx`) — a stable UI contract; retained verbatim.
- The gateway / IdP / backend — handled by the merged ADR-035 step-4 tasks (MONO-261/262/263, BE-376/377/378) + TASK-BE-382 (javadoc).
- `e2e/fixtures/iam-consumer-seed.sql` — already roles-aligned (TASK-MONO-263 dropped the `account_type` column from the seed). No change.
- Explanatory comments that name `account_type` solely to record it was *removed* — kept (the irreducible way to document a migration).

## Acceptance Criteria

- **AC-1** — No `accountType` **identifier** remains in `web-store/src/**` (session field fully removed). `grep -rn accountType web-store/src` returns zero. The only residual `account_type` mentions in `src/**` are the `account_type_mismatch` error-code string + explanatory "removed" comments.
- **AC-2** — The role-based consumer guard is byte-unchanged in behavior: `signIn()`/`session()` still reject `roles ∌ CUSTOMER` and redirect to `/login?error=account_type_mismatch`; the `LoginForm` banner still renders for that code.
- **AC-3** — `account-type-claim.spec.ts` is deleted; `account-type-guard.spec.ts` + `helpers/auth.ts` describe the guard in roles terms (no claim that the *current* guard keys on an `account_type` claim).
- **AC-4 (verification)** — `pnpm --filter web-store lint` + `pnpm --filter web-store typecheck` (tsc) + `pnpm --filter web-store test` (vitest) all GREEN. (The CI two-frontend lint/tsc jobs are the gate; vitest covers the mutated unit fixtures.)
- **AC-5** — No backend/contract/seed change: `git diff origin/main` under this task touches only `web-store/src/**`, `web-store/e2e/**`, and the task lifecycle file.

## Related Specs

- `projects/ecommerce-microservices-platform/specs/services/web-store/architecture.md` (§ Authentication — consumer-role guard, corrected in PR #1603)
- `projects/ecommerce-microservices-platform/specs/integration/iam-integration.md` (roles-only, no `X-Account-Type`)

## Related Contracts

- `platform/contracts/jwt-standard-claims.md` — the `account_type` removal authority (ADR-035 4b).
- `docs/adr/ADR-MONO-032-unified-identity-roles-model.md` § D5 step 4.
- `docs/adr/ADR-MONO-035-operator-auth-unification-model.md` § O5 4b-1 (web-store role-based SSO guard).

## Edge Cases

- **Error-code string is a UI contract, not a claim** — `account_type_mismatch` must survive verbatim (LoginForm + login-form.test.tsx key on it). Removing it would break the mismatch banner. Guarded by AC-2.
- **Degraded-session branch** — `session()` returns an anonymized session for `roles ∌ CUSTOMER`; removing `accountType` from that return object must not change the anonymization (still `user: undefined`, `accountId: null`). Guarded by AC-2.
- **Public session shape change** — `WebStoreSession`/augmented `Session` lose a field; verified no RSC/component reads `session.accountType` (grep clean) so tsc stays GREEN. Guarded by AC-1/AC-4.
- **e2e fixture hint retained** — `TestUser.accountType` in `helpers/auth.ts` is an e2e-only, `SKIP_GAP_E2E`-gated fixture hint; left in place intentionally (not a production type) to avoid churn in a non-running suite.

## Failure Scenarios

- **F1 — stale e2e silently rots** — `account-type-claim.spec.ts` would fail the moment someone runs the GAP-backed suite (`SKIP_GAP_E2E=0`), asserting a claim that no longer exists. Guarded by AC-3 (retire).
- **F2 — vestigial field misleads** — leaving `accountType` suggests `account_type` is still a live axis; a future change might re-key authorization on it. Guarded by AC-1.
- **F3 — accidentally breaking the guard or the error banner** — removing the error-code string or altering the role check. Guarded by AC-2 + AC-4 (vitest/tsc/lint GREEN).
