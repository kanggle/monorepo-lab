---
id: TASK-MONO-299
title: "ADR-MONO-040 Phase 3 (part B) — remove dual-key/email fallback + transitional account_id claim + gateway fallbacks"
status: done
scope: cross-project
projects: [iam-platform, ecommerce-microservices-platform, wms-platform, scm-platform, fan-platform]
tags: [code, test, identity, cleanup, adr-040, cross-project]
analysis_model: "Opus 4.8"
impl_model: "Opus 4.8"
created: 2026-06-18
---

# TASK-MONO-299 — ADR-040 Phase 3 (part B): retire the dual-key transition

## Goal

Complete ADR-MONO-040 Phase 3 by removing the transitional **DUAL-KEY** machinery now that part A
(TASK-MONO-298, MERGED `d7dc2e763`) shipped the email→account_id backfill mechanism and migrated the
seeds. Operator resolution becomes **account_id-only**; the contract `X-User-Id ← sub` letter is
fully restored; jwt-standard-claims compliance is complete.

**Ordering prerequisite (satisfied):** part A is merged + CI-verified, and the federation/console
seeds now carry `admin_operators.oidc_subject = account_id`. For REAL deployments, the part-A
backfill endpoint (`POST /internal/admin/operator-oidc-subject-backfill`) MUST have been run before
deploying this change — **document this operational prerequisite** (removing the fallback before the
data is migrated would break every not-yet-migrated operator).

## Scope (atomic cross-project PR — iam auth+admin + 4 gateways)

### A. admin-service — account_id-only operator resolution
1. `OperatorOidcSubjectResolver` — remove the email-fallback supplier path; resolve by account_id
   (`oidc_subject`) only.
2. `TokenExchangeService` (login-time exchange) — remove the
   `authServiceClient.resolveOperatorEmail(...)` call and the dual-key; resolve by `sub`
   (account_id) only.
3. `OperatorAssignmentCheckUseCase` (assume-tenant) — remove the `subjectEmail` fallback
   parameter/usage; account_id-only.
4. `AuthServiceClient` — remove the now-unused `resolveOperatorEmail(...)` method. **KEEP
   `resolveOperatorAccountId(...)`** (part-A backfill — still needed).
5. Internal `POST /internal/operator-assignments/check` — remove the additive `subjectEmail`
   field (both producer admin + consumer auth are in THIS atomic PR, so a clean removal is fine).

### B. auth-service — drop server-side email resolution + account_id→email endpoint
6. `AssumeTenantAuthenticationProvider` — remove the `credentialRepository.findByAccountId(...)`
   email resolution and stop passing `subjectEmail`; call `resolveAssignment(oidcSubject, tenantId)`
   account_id-only.
7. `OperatorAssignmentPort` / `AdminAssignmentClient` — remove the `subjectEmail` param.
8. `InternalCredentialController` — remove the `GET /internal/auth/credentials/{accountId}/email`
   endpoint (account_id→email — only the removed fallback consumed it; VERIFY no other consumer
   before deleting) + its `ResolveCredentialEmailResponse` DTO. **KEEP the email→account_id endpoint
   `POST /internal/auth/credentials/account-id-by-email`** (part-A backfill tool — retained).
9. `TenantClaimTokenCustomizer` — remove the transitional `.claim("account_id", accountId)`
   emission. **KEEP `sub` = account_id** (that is the Phase-2 end-state, NOT transitional).

### C. gateways — restore X-User-Id ← sub (4 projects)
10. `JwtHeaderEnrichmentFilter` in **ecommerce, wms, scm, fan** gateway-service — remove the
    legacy-email fallback (`subjectIsLegacyEmail ? accountId-claim : sub`); set `X-User-Id = sub`
    directly (sub is now always the account UUID). Restores the `jwt-standard-claims.md` letter.
    Check each of the 4 for the exact Phase-1/2 fallback block.

### D. tests
11. Remove dual-key/email-fallback test cases (admin resolver/exchange/assignment, auth assume-tenant,
    auth internal email endpoint, gateway email-shape fallback). Replace with account_id-only
    assertions: operator resolves by account_id; a request whose `sub` is NOT a provisioned
    account_id fails-closed (401/not-provisioned); gateways set `X-User-Id = sub` with no
    account_id-claim dependency.

### E. docs
12. `docs/adr/ADR-MONO-040-...md` §6 — append a row: **Phase 3 part B (MONO-299) — dual-key/email
    fallback + transitional `account_id` claim + 4 gateway fallbacks removed; operator resolution is
    account_id-only; `X-User-Id ← sub` contract letter restored; jwt-standard-claims compliance
    complete. Phase 3 DONE.** Document the operational backfill-before-deploy prerequisite.
13. Update the relevant internal contract docs (remove the account_id→email endpoint + `subjectEmail`
    field from the operator-assignments-check contract; note X-User-Id←sub restored).

## Acceptance Criteria

- AC-1: operator resolution is **account_id-only** at all sites (resolver, TokenExchangeService,
  OperatorAssignmentCheckUseCase, AssumeTenantAuthenticationProvider) — no email fallback remains.
- AC-2: the transitional `account_id` claim is removed from `TenantClaimTokenCustomizer`; `sub`
  remains account_id (Phase-2 end-state, unchanged).
- AC-3: all 4 gateways set `X-User-Id ← sub` directly (no email-shape branch, no account_id-claim
  dependency).
- AC-4: `resolveOperatorEmail` + the `GET .../credentials/{accountId}/email` endpoint +
  `ResolveCredentialEmailResponse` + the `subjectEmail` internal field are removed (verified no other
  consumer); the **email→account_id backfill endpoint is RETAINED**.
- AC-5: builds + unit tests GREEN —
  `:projects:iam-platform:apps:auth-service:test`, `:projects:iam-platform:apps:admin-service:test`,
  and the 4 gateway `:test` targets (ecommerce/wms/scm/fan gateway-service).
- AC-6: ADR-040 §6 Phase 3 **COMPLETE** row; operational backfill-before-deploy prerequisite
  documented; jwt-standard-claims `X-User-Id ← sub` compliance restored.
- AC-7 (**CRITICAL — operator-path authority**): PR CI alone is INSUFFICIENT for operator-path
  changes (Phase-2 precedent: the `not_provisioned` operator-login regression was caught ONLY by
  federation-hardening-e2e, not by PR CI). The iam Testcontainers IT (which now uses account_id
  seeds) must pass, AND the **federation-hardening-e2e operator scenarios** (operator login +
  `tenant-switch-rescope` / `entitlement-trust` assume-tenant) must be GREEN before merge. (The
  orchestrator will trigger fed-e2e on this branch via workflow_dispatch and gate the merge on it —
  the implementing agent must ensure these paths COMPILE and the ITs are sound.)

## Related Specs / Contracts

- `docs/adr/ADR-MONO-040-...md` §D3 (Phase 3), §6 (log — part A row names part B as the fallback removal)
- `platform/contracts/jwt-standard-claims.md` (`sub` = account UUID, `X-User-Id ← sub` — the letter
  this restores)
- admin-service `OperatorOidcSubjectResolver`, `TokenExchangeService`, `OperatorAssignmentCheckUseCase`,
  `AuthServiceClient`; auth-service `AssumeTenantAuthenticationProvider`, `InternalCredentialController`,
  `TenantClaimTokenCustomizer`, `OperatorAssignmentPort`/`AdminAssignmentClient`
- the 4 gateway `JwtHeaderEnrichmentFilter`s (ecommerce/wms/scm/fan)
- `specs/contracts/http/internal/` (operator-assignments-check — remove `subjectEmail`; credentials —
  remove account_id→email)

## Edge Cases

- **Operator whose `oidc_subject` is still email** (backfill not run / failed in a real deploy):
  after this change they fail-closed (401/not-provisioned) — this is WHY the backfill-before-deploy
  prerequisite is mandatory and documented.
- **Credential-less seeded operators stay email-shaped EVEN after backfill** (MONO-298 fail-soft —
  verified live, read-only, against the running federation demo 2026-06-18): the part-A backfill only
  migrates operators that HAVE a matching `auth_db.credentials` row (tenant-scoped). The demo seed has
  **4 credential-less object-only operators that REMAIN email-shaped after backfill** —
  `deleg-target-umbrella`, `ip-pilot-target`, `rt-protected-target`, `rt-untagged-target` (delegation /
  refresh-token targets). So "demo/e2e seeds are all account_id" is **NOT fully true**: 7 credentialed
  operators migrate, these 4 do not (+ 2 mock-OIDC rows `…devOID`/`…adminOIDC` that are neither email
  nor UUID). **Before removing the fallback, CONFIRM these 4 are genuinely never-login** (grep
  fed-e2e/e2e for any login or assume-tenant that exercises them). If any is reachable, seed it a
  credential + re-run backfill (or otherwise keep it out of the operator resolution path) FIRST —
  removing the fallback would strand it (fail-closed). AC-7's fed-e2e gate is the authoritative
  catch, but this must be checked deliberately, not left to chance. The 2 mock-OIDC rows are not
  email-shaped (fallback removal does not touch them) — verify they resolve via their seeded
  account_id path.
- **In-flight tokens issued before deploy** carrying the old `account_id` claim: harmless — gateways
  now read `sub` (which was already account_id since Phase 2); the dropped claim was redundant.
- **account_id→email endpoint other consumers**: VERIFY none before deleting (grep). If something
  else uses it, keep it or migrate that consumer (do not break an unrelated path).

## Failure Scenarios

- **Removing fallback breaks operator login** (the Phase-2 cross-path risk): the login-time
  `TokenExchangeService` path was historically caught only by fed-e2e. AC-7 mandates fed-e2e
  validation before merge — do NOT rely on PR CI alone.
- **A gateway still depends on the account_id claim** after removal → `X-User-Id` wrong: ensure each
  gateway reads `sub` directly and the gateway tests assert it without the claim.
- **Deleting an endpoint with a live consumer**: AC-4 verify-no-consumer guard.
- **Non-atomic contract change**: the `subjectEmail` internal field removal must land producer+consumer
  together in this one PR (they do).
