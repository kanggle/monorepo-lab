# TASK-MONO-291 — fed-e2e consumer token `sub` is not a UUID, blocking web-store wishlist (browser-origin authed writes) e2e

**Status:** ready

**Type:** TASK-MONO (root — `tests/federation-hardening-e2e/` fixtures; cross-cutting IAM identity ↔ ecommerce user-service)

**Analysis model:** Opus 4.8 / **Recommended impl model:** Sonnet 4.6 (fixture/seed provisioning once AC-0 confirms the sub-derivation)

> **Priority: LOW / fixture-only.** Production is unaffected — real born-unified (ADR-MONO-036) consumers get a UUID `identity_id` `sub`. This only unblocks the web-store wishlist e2e (the FE-074/BE-394 spec's 3rd test) against the **federation demo stack**. File-and-defer is acceptable; pick up when the federation demo is being exercised.

---

## Goal

Surfaced by **TASK-BE-394**'s AC-2 run (gateway CORS preflight fix, merged PR #1766) on top of **TASK-FE-074** (web-store consumer e2e login migration, merged #1764):

After BE-394 unblocked the CORS preflight, the web-store wishlist write now **reaches** the ecommerce `user-service`, which then rejects it with **`400`**:

```json
{"code":"VALIDATION_ERROR","message":"Invalid value for parameter: X-User-Id"}
```

**Root cause chain:**
- The gateway's `JwtHeaderEnrichmentFilter` forwards `X-User-Id ← jwt.sub` verbatim.
- The ecommerce `WishlistController` binds `@RequestHeader("X-User-Id") UUID userId` (so do `/me/check` and `/me`).
- But the fed-e2e **hand-seeded `e2e-consumer`'s IAM token `sub` is the email** (`e2e-consumer@example.com`), **not a UUID**. (Confirmed in the PC-FE-113 run: the federation operator tokens carry `sub=<email>`; the consumer is seeded the same way with `account_db.accounts.identity_id` NULL.)
- → every wishlist endpoint 400s. `productId` is a valid UUID; the failure is purely the `X-User-Id` (`sub`) binding.

In the ecommerce **standalone** stack a consumer's `sub` is its account UUID, so the `UUID userId` binding holds; in **fed-e2e** the consumer authenticates through the IAM SAS where these dev-seeded accounts mint `sub=email`. The diagnosis is captured in project memory `env_webstore_standalone_bringup_against_fed_e2e`.

## Scope

**In scope (only after AC-0):**

1. **Provision the fed-e2e `e2e-consumer`** (and any other consumer persona the web-store e2e uses) so its IAM token `sub` is a **UUID** — most likely by minting a central identity (ADR-MONO-036 born-unified) and setting `auth_db.credentials.identity_id` / `account_db.accounts.identity_id` so the auth-service emits `sub=<identity_id>`.
2. Ensure `user_db.user_profiles.user_id` equals that UUID `sub` (the wishlist `wishlist_items` FK target). The existing row (`01928c4a-…e001`) was keyed to the account_id and will likely need re-keying to the new `sub`.
3. **Commit the consumer provisioning as a reproducible fixture** under `tests/federation-hardening-e2e/fixtures/` (consolidating the currently-uncommitted hand-seed), consistent with the `env_webstore_standalone_bringup_against_fed_e2e` recipe.

**Out of scope:** the gateway CORS fix (BE-394, done); the web-store frontend (FE-074, done); any production ecommerce/IAM code change unless AC-0 reveals the `sub` is *not* identity-derived (then re-scope).

## Acceptance Criteria

- **AC-0 (gate — investigate first)** — Confirm how the federation `auth-service` derives the OIDC `sub` claim: does `sub=identity_id` (UUID) when a central identity exists, with an email/oidc_subject fallback when `identity_id` is NULL? Confirm by reading the auth-service token-mint path or decoding a freshly-minted token. **Do not change the seed until the derivation is confirmed** — if `sub` is hardcoded to email for credential auth, the fix is different (gateway-side mapping or a distinct credential setup) and the task must be re-scoped.
- **AC-1** — the fed-e2e `e2e-consumer`'s IAM token carries a **UUID** `sub`; the gateway-forwarded `X-User-Id` is that UUID.
- **AC-2** — the web-store wishlist e2e passes **3/3** under `SKIP_GAP_E2E=0` against the federation demo stack (the BE-394/FE-074 spec's 3rd test, now unblocked). **Verified live.**
- **AC-3** — the consumer provisioning is committed as a reproducible fixture (no hand-typed seed); `user_db.user_profiles.user_id` == the consumer's `sub`.

## Related Specs / Tasks

- `tasks/done/` — TASK-BE-394 (gateway CORS preflight — surfaced this), TASK-FE-074 (web-store consumer e2e login migration).
- `docs/adr/ADR-MONO-036-*` (born-unified identity provisioning) + `ADR-MONO-037-*` (ecommerce account-lifecycle projection) — the identity model the consumer should follow.
- project memory `env_webstore_standalone_bringup_against_fed_e2e` (precise diagnosis + the 5-step bring-up recipe).

## Related Contracts

- `platform/contracts/jwt-standard-claims.md` (`sub` claim authority — informational).

## Edge Cases

- **`sub` may not be identity-derived** — if the auth-service emits `sub=email` for credential auth regardless of `identity_id`, AC-1 isn't reachable by seeding alone; AC-0 catches this and forces a re-scope.
- **`user_profiles` FK mismatch** — the wishlist FK targets `user_profiles.user_id`; if it doesn't equal the new UUID `sub`, the write still fails even after the `sub` is a UUID.

## Failure Scenarios

- **F1 — premature seed change** — re-keying the consumer before confirming the `sub` derivation (AC-0) wastes a gated DB re-seed and may not fix the 400. Guarded by AC-0.
- **F2 — partial fix** — making `sub` a UUID but leaving `user_profiles.user_id` on the old key → wishlist FK still fails. Guarded by AC-3.
