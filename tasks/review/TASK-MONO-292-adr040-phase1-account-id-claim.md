# TASK-MONO-292 — ADR-MONO-040 Phase 1: emit `account_id` claim + derive ecommerce `X-User-Id` from it

**Status:** review

**Type:** TASK-MONO (root — cross-project atomic: iam-platform `auth-service` + ecommerce `gateway-service` + `platform/contracts/`)

**Analysis model:** Opus 4.8 / **Recommended impl model:** Opus 4.8 (auth-service token-mint path; additive but security-sensitive)

> Implements **ADR-MONO-040 D2 (Phase 1)** — the ACCEPTED, gated child. Phase 2 (SAS `sub` = account UUID + operator assume-tenant migration) stays separately gated.

---

## Goal

Fix the latent production defect recorded in ADR-MONO-040 / TASK-MONO-291: the live SAS OIDC `sub` is the login **email** (the Spring principal), violating `platform/contracts/jwt-standard-claims.md` (`sub` = account UUID; `X-User-Id ← sub`). Since ecommerce consumer auth IS the SAS path (auth-service decommissioned, MONO-027), every ecommerce consumer browser-origin authed write 400s at the user-service's contract-compliant `@RequestHeader("X-User-Id") UUID userId` binding.

Phase 1 unblocks it **additively, operator-zero-risk**: the auth-service emits the account UUID as an `account_id` claim, and the ecommerce gateway derives `X-User-Id` from it (fallback `sub`). `sub` is untouched → operator assume-tenant (`resolveAssignment` ↔ `oidc_subject=email`) is unaffected.

## Scope

1. **iam-platform `auth-service`** — `TenantClaimTokenCustomizer.customizeForAuthorizationCode` (covers authorization_code + refresh) emits `account_id` (from the principal `details` map, already present) as an additive claim. Assume-tenant path untouched.
2. **ecommerce `gateway-service`** — `JwtHeaderEnrichmentFilter` sets `X-User-Id` from the `account_id` claim, falling back to `sub` when absent/blank.
3. **`platform/contracts/jwt-standard-claims.md`** — document the `account_id` Phase-1 transitional claim + the `X-User-Id ← account_id (fallback sub)` derivation + the `sub`=email known deviation (ADR-040).

**Out of scope:** Phase 2 (`sub`=account UUID + operator resolution migration + legacy `JwtTokenGenerator`/`OidcUserInfoMapper` reconciliation) — separately gated per ADR-040 D3.

## Acceptance Criteria

- **AC-1** — auth-service SAS access token carries `account_id` = the account UUID (additive; `sub` unchanged). Unit-tested (`TenantClaimTokenCustomizerTest`).
- **AC-2** — ecommerce gateway `X-User-Id` = `account_id` claim when present, else `sub`. Unit-tested (`JwtHeaderEnrichmentFilterTest`).
- **AC-3** — both modules' Docker-free `:test` GREEN; no regression to the existing customizer/filter tests or CI.
- **AC-4 (live)** — the web-store wishlist e2e passes **3/3** under `SKIP_GAP_E2E=0` against the federation demo stack (the TASK-FE-074/BE-394 spec's 3rd test, now unblocked). Requires rebuilding the auth-service + ecommerce-gateway images + redeploy (gated).
- **AC-5** — no operator regression: the federation assume-tenant e2e (`tenant-switch-rescope` / `entitlement-trust`) stay green (`sub` untouched).

## Related

- `docs/adr/ADR-MONO-040-oidc-subject-claim-account-id-contract-alignment.md` (ACCEPTED — D2 is this task).
- `tasks/done/` TASK-MONO-291 (AC-0 finding), TASK-BE-394 (CORS preflight — surfaced the 400), TASK-FE-074 (web-store consumer e2e).
- `platform/contracts/jwt-standard-claims.md` (the contract this restores).

## Edge Cases / Failure Scenarios

- **F1** — emitting `account_id` on operator/assume-tenant tokens is harmless (additive) but unnecessary; Phase 1 emits only on the authorization_code/refresh path to stay minimal.
- **F2** — a token without `account_id` (legacy/non-SAS) must still set `X-User-Id` from `sub` (fallback) — covered by AC-2.
