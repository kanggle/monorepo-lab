# TASK-MONO-295 — ADR-MONO-040 Phase 2: SAS `sub` = account_id + operator assume-tenant resolution migration

**Status:** done

**Type:** TASK-MONO (root — cross-project atomic: iam-platform `auth-service` + `admin-service` + ecommerce `gateway-service` + `platform/contracts/`)

**Analysis model:** Opus 4.8 / **Recommended impl model:** Opus 4.8 (operator auth hot-path migration — HIGH blast radius)

> Implements **ADR-MONO-040 D3 (Phase 2)** — the separately-gated full-compliance follow-up to TASK-MONO-292 (Phase 1). Phase 1 shipped the additive `account_id` claim + ecommerce gateway derive (operator-zero-risk). Phase 2 makes the SAS access-token `sub` itself the account UUID (full `jwt-standard-claims.md` compliance), retires the Phase-1 `account_id`-first derivation in the ecommerce gateway, and — the blast-radius dependency — migrates operator assume-tenant resolution so existing operators do NOT break.

---

## Goal

Restore full `platform/contracts/jwt-standard-claims.md` compliance: the SAS OIDC access-token `sub` must be the **account UUID** (immutable), and `X-User-Id` from `sub` (retire the Phase-1 `account_id`-claim derivation). The cross-cutting constraint (ADR-040 section 1.3): operator assume-tenant resolution today reads `subjectJwt.getSubject()` and matches it against `admin_operators.oidc_subject`, which is **seeded with the login email** (federation `seed.sql`: `oidc_subject = 'acme-operator@example.com'`). Flipping `sub` to account_id makes `findByOidcSubject(account_id)` miss, so every operator's tenant switch fails with `invalid_grant`. Phase 2 must migrate that resolution WITHOUT breaking a single existing operator.

## Scope (dependency-correct order — operator migration FIRST to de-risk)

1. **Operator resolution migration (admin-service + auth-service) — DUAL-KEY transition.**
   - The account_id-to-email mapping lives in `auth_db.credentials`; `admin_operators` is in `admin_db`. A **single-migration cross-DB backfill is impossible** (repo precedent: V0036 explicitly refuses a cross-DB read at migrate time; ADR-034 U3). Therefore: **dual-key transition** — the resolution accepts BOTH the account_id (`sub`, the target end-state key) AND the legacy email (the seed value), defaulting to account_id.
   - auth-service `AssumeTenantAuthenticationProvider`: extract BOTH the subject token's `sub` (account_id post-Phase-2) AND its `email` claim; pass both to admin-service.
   - `OperatorAssignmentPort.resolveAssignment` + `AdminAssignmentClient`: thread the additive `subjectEmail` (legacy fallback key) alongside `oidcSubject`.
   - admin-service `/internal/operator-assignments/check`: additive `subjectEmail` query param; `OperatorAssignmentCheckUseCase.check(oidcSubject, subjectEmail, tenantId)` resolves `findByOidcSubject(oidcSubject)` (account_id) first; **on miss, fall back to `findByOidcSubject(subjectEmail)`** (the legacy seed value). Account_id is the default/preferred key.
   - **AC-0 (operator-no-regression gate):** an operator whose `oidc_subject` is the legacy EMAIL still resolves via the email fallback when the subject token's `sub` is now the account UUID. Unit-tested both ways (account_id-keyed row resolves on `sub`; email-keyed row resolves on the fallback).
2. **SAS access-token `sub` = account_id (auth-service `TenantClaimTokenCustomizer`).** On the `authorization_code`/`refresh_token` path, override the `sub` claim to the principal's `account_id` (already on the principal `details` map). Keep `account_id` claim emission for one transition window (gateways still on the Phase-1 derive during redeploy). The assume-tenant token's `sub` follows from the operator's base token's `sub` (now account_id by this same override).
3. **ecommerce gateway `JwtHeaderEnrichmentFilter`: `X-User-Id` from `sub`.** Retire the Phase-1 `account_id`-claim-first derivation (sub is now the account UUID). Retain a defensive `account_id` fallback ONLY for in-flight legacy tokens issued before redeploy, documented for Phase-3 removal (avoids a redeploy-ordering 400 window).
4. **Legacy reconciliation.** `JwtTokenGenerator` (legacy `TokenGeneratorPort`) already emits `sub`=accountId — it is *already* contract-compliant; document it as reconciled (no behavior change). `OidcUserInfoMapper` already maps `sub` from the JWT `sub` (now account_id) — aligned by construction; add a clarifying note.
5. **Contracts/ADR.** `platform/contracts/jwt-standard-claims.md`: update the `sub` row + section Post-Validation Injection to reflect Phase-2 executed (`sub` = account UUID, `X-User-Id` from `sub`; `account_id` claim now transitional-legacy). ADR-040 decision log: add the Phase-2-executed row.

**Out of scope (documented Phase-3 follow-up):** the eventual `admin_operators.oidc_subject` email-to-account_id backfill (a coordinated cross-DB data job, NOT a Flyway step) + removal of the email fallback key + removal of the `account_id` transitional claim + the gateway `account_id` fallback. These are safe to defer because the dual-key transition keeps every operator working indefinitely.

## Acceptance Criteria

- **AC-0 (operator-no-regression gate)** — existing operators' assume-tenant MUST NOT break. Proven by unit tests: (a) an `admin_operators` row keyed by account_id resolves when the subject `sub` is the account UUID; (b) a row keyed by the legacy EMAIL resolves via the `subjectEmail` fallback when `sub` is the account UUID and the account_id key misses; (c) the auth-service provider threads both `sub` and `email` to the port. **Live authority:** the federation `tenant-switch-rescope` / `entitlement-trust` e2e (assume-tenant) must stay GREEN post-deploy — Testcontainers ITs are blocked on the dev host, so CI/federation-e2e is the operator-regression authority.
- **AC-1** — SAS access token `sub` = account_id on the `authorization_code`/`refresh_token` path. Unit-tested (`TenantClaimTokenCustomizerTest`).
- **AC-2** — ecommerce gateway `X-User-Id` from `sub` (sub is now the account UUID); transitional `account_id` fallback retained for in-flight tokens. Unit-tested (`JwtHeaderEnrichmentFilterTest`).
- **AC-3** — admin-service `check` resolves on account_id first, email second (dual-key). Unit-tested (`OperatorAssignmentCheckUseCaseTest`).
- **AC-4** — all affected modules' Docker-free `:test` GREEN; no regression to existing customizer/filter/assignment tests.
- **AC-5** — contract + ADR updated (sub=account UUID, X-User-Id from sub, Phase-2-executed log row).

## Related Specs

- `docs/adr/ADR-MONO-040-oidc-subject-claim-account-id-contract-alignment.md` (ACCEPTED — D3 is this task).
- `platform/contracts/jwt-standard-claims.md` (the contract this fully restores).
- `projects/iam-platform/specs/services/admin-service/data-model.md` (section OIDC Subject to Operator Link Key).
- `tasks/done/TASK-MONO-292-adr040-phase1-account-id-claim.md` (Phase 1 — what this builds on/retires).

## Related Contracts

- `platform/contracts/jwt-standard-claims.md` — `sub` = account UUID; `X-User-Id` from `sub`.
- `projects/iam-platform/specs/contracts/http/internal/auth-to-admin.md` (the assignment-check internal contract — gains additive `subjectEmail`).

## Edge Cases / Failure Scenarios

- **F1 (the AC-0 case)** — operator `oidc_subject` = legacy email; subject `sub` now account_id, so the account_id key misses and the email fallback resolves. Net-zero, no operator breaks.
- **F2** — operator already provisioned with `oidc_subject` = account_id (target end-state) resolves on the primary key; email fallback never consulted.
- **F3** — redeploy ordering: a token minted BEFORE the auth-service redeploy still has `sub`=email; the gateway's transitional `account_id` fallback (Phase-1) keeps `X-User-Id` correct until the token expires. No 400 window.
- **F4** — subject token missing the `email` claim (non-SAS/legacy) means the fallback key is null; resolution proceeds on account_id only (graceful — an account_id-keyed row still resolves; an email-only-keyed row 403s, the correct fail-closed for a malformed token).
- **F5** — the dual-key fallback is order-sensitive: account_id MUST be tried first so that once the backfill (Phase-3) sets `oidc_subject`=account_id, behavior is already correct and the email fallback silently stops being consulted.
- **F6** — fail-closed contract preserved end-to-end: any admin-service failure still throws `AssumeTenantDeniedException` to `invalid_grant` (the dual-key change is resolution-key only, never relaxes the fail-closed gate).
