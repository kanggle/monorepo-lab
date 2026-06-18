# ADR-MONO-040 — Align the SAS OIDC access-token `sub` to the platform `jwt-standard-claims` contract (`sub` = account UUID), and unblock ecommerce consumer authed writes

**Status:** ACCEPTED

**Date:** 2026-06-17

**Accepted:** 2026-06-17 (TASK-MONO-291 — user-explicit *"accept"* after the PROPOSED D1–D4 were presented for the required ACCEPT gate; the gate was honored — the PROPOSED record was presented and review awaited before any flip, **NOT a self-ACCEPT**. D1–D4 **finalised byte-unchanged** — ACCEPTED *finalises*, does not re-decide; § 1 Context + § 2 Decision + § 3 Consequences + § 4 Alternatives + § 5 Verification + § 7 Provenance byte-identical to the PROPOSED draft; flip = Status + this clause + § 6 ACCEPTED row. Implementation is the gated child Phase 1 task — Phase 2 stays separately gated.)

**Decision driver:** `platform/contracts/jwt-standard-claims.md` mandates `sub` = **Account ID (UUID), immutable** and `X-User-Id ← sub`. But the live SAS OIDC path (`CredentialAuthenticationProvider`) emits `sub` = the **login email** (the Spring Security principal), and `TenantClaimTokenCustomizer` never overrides it. So every email/password-authenticated token **violates the contract**, and since ecommerce consumer auth IS the SAS/GAP OIDC path (ecommerce `auth-service` decommissioned, TASK-MONO-027 / TASK-BE-132), **every ecommerce consumer browser-origin authed write 400s** at the user-service's contract-compliant `@RequestHeader("X-User-Id") UUID userId` binding (`{"code":"VALIDATION_ERROR","message":"Invalid value for parameter: X-User-Id"}`). This is a **latent production defect**, not a demo-stack fixture issue — it was masked until the consumer authed-write path was first exercised E2E (TASK-FE-074 real-GAP login → TASK-BE-394 CORS-preflight unblock → TASK-MONO-291 AC-0).

**Supersedes:** none. **Superseded by:** none.

**Family:** [ADR-MONO-032](ADR-MONO-032-unified-identity-roles-model.md) (unified identity — IAM sole account authority, ecommerce `auth-service` decommissioned: the root cause that routed ecommerce consumers onto the SAS path), [ADR-MONO-035](ADR-MONO-035-operator-auth-unification-model.md) (operator auth — the operator assume-tenant model whose `oidc_subject=email` lookup is the cross-cutting constraint on changing `sub`), [ADR-MONO-036](ADR-MONO-036-born-unified-identity-provisioning.md) (born-unified `identity_id` — distinct from `sub`; this ADR clarifies that `sub` is the **account_id**, not the central `identity_id`).

**Related:** `platform/contracts/jwt-standard-claims.md` (§ `sub` = Account ID UUID, `X-User-Id ← sub` — the authority this ADR enforces); auth-service `CredentialAuthenticationProvider` (SAS principal = email), `TenantClaimTokenCustomizer` (injects tenant/roles/entitled_domains; does NOT set `sub`), `JwtTokenGenerator` + `OidcUserInfoMapper` (legacy/userinfo path that already uses `sub`=`accountId` — the *contradictory convention*), `AssumeTenantAuthenticationProvider` (`oidcSubject = subjectJwt.getSubject()` → `resolveAssignment` ↔ `admin_operators.oidc_subject` — the operator dependency on `sub`); ecommerce `JwtHeaderEnrichmentFilter` (`X-User-Id ← sub`), `WishlistController` (`@RequestHeader("X-User-Id") UUID userId`); TASK-MONO-291 (the driving task — AC-0 recorded this finding), TASK-BE-394 (CORS preflight, done — unblocked the request so the 400 surfaced), TASK-FE-074 (web-store consumer e2e login migration, done).

---

## 1. Context

### 1.1 The as-built `sub` inconsistency (code-verified 2026-06-17)

Two contradictory `sub` conventions live in the same auth-service:

| Path | Who uses it | `sub` value | Contract-compliant? |
|---|---|---|---|
| **SAS OIDC** (`/oauth2/authorize` → `CredentialAuthenticationProvider`) — the **live** web-store + console login | every email/password consumer **and** operator | the **email** (`new UsernamePasswordAuthenticationToken(credential.getEmail(), …)`; `account_id` only in the `details` map; customizer never overrides `sub`) | **NO** — violates `jwt-standard-claims.md` |
| **Legacy `JwtTokenGenerator`** (`TokenGeneratorPort`, BE-229) + `OidcUserInfoMapper` | legacy custom-JWT / `/userinfo` | `accountId` (UUID) | yes |

The SAS migration (ADR-032 / MONO-027) made SAS the live path but left its `sub` as the framework-default principal (email), silently changing the consumer `sub` from the contract's UUID to the email — without updating any consumer that reads it.

### 1.2 Why it 400s, and why it's production (not fixture)

- `jwt-standard-claims.md` § claims: **`sub` | UUID string | Account ID (immutable)**; § header propagation: **`X-User-Id ← sub`**.
- The ecommerce gateway honors the contract verbatim: `JwtHeaderEnrichmentFilter` sets `X-User-Id = jwt.getSubject()`. The user-service honors it: `WishlistController` (and `/me`, `/me/check`) bind `@RequestHeader("X-User-Id") UUID userId`.
- With the SAS `sub` = email, the `UUID` bind fails → `400 VALIDATION_ERROR`. **Every** browser-origin authed write (wishlist add/remove, profile mutation, any client `POST/PUT/DELETE/PATCH`) is affected — in any deployment using SAS OIDC consumer auth, i.e. production, not just the federation demo stack.
- Latent because the consumer authed-write path had never been exercised end-to-end with a real SAS token until FE-074 → BE-394 → MONO-291.

### 1.3 The cross-cutting constraint

`AssumeTenantAuthenticationProvider` extracts `oidcSubject = subjectJwt.getSubject()` and calls `resolveAssignment(oidcSubject, tenant)`, matched against `admin_operators.oidc_subject = email`. **Operators currently depend on `sub` = email** for tenant switching. A naive global flip of `sub` → account_id would break every operator's assume-tenant until their resolution key is migrated. This is precisely why the fix is an ADR-gated decision and a *phased* one.

## 2. Decision

> **CHOSEN-PROPOSED direction below. PROPOSED record only — implementation is a separate, user-explicit-ACCEPT-gated child task (staged-child pattern, ADR-019/020/021/023/024/032/034/035/036/037/038/039). Self-ACCEPT prohibited.**

- **D1 — The contract is authoritative; the SAS `sub=email` is a defect, not a design choice.** `jwt-standard-claims.md` (`sub` = account UUID, `X-User-Id ← sub`) stands. We do **not** weaken the ecommerce user-service binding to accept a non-UUID key — that would propagate the contract violation downstream. The fix targets the **producer** (auth-service) and the **propagation** (gateway), restoring contract compliance.

- **D2 — Phase 1 (unblock now, additive, operator-zero-risk): emit `account_id` as a claim; ecommerce derives `X-User-Id` from it.**
  - auth-service `TenantClaimTokenCustomizer` emits `account_id` (already present in the principal `details` map) as an **additive** access-token claim. No existing consumer breaks; `sub` is untouched, so operator assume-tenant is unaffected.
  - the ecommerce gateway `JwtHeaderEnrichmentFilter` sets `X-User-Id` from the `account_id` claim, **falling back to `sub`** when the claim is absent (legacy/non-SAS tokens). Consumer authed writes are unblocked immediately with the contract-intended value (the account UUID), at a deviation from the contract's *letter* (`X-User-Id ← sub`) but in service of its *intent* (`X-User-Id` = account UUID).

- **D3 — Phase 2 (full compliance, tracked follow-up): SAS `sub` = account_id; migrate operator resolution.**
  - make the SAS access-token `sub` = account_id (override the principal or the customizer), fully satisfying `jwt-standard-claims.md`, and restore `X-User-Id ← sub` (retire the Phase-1 claim fallback).
  - migrate operator assume-tenant resolution off `oidc_subject = email` to `account_id` (or carry account_id on `admin_operators` and key `resolveAssignment` on it), and align `OidcUserInfoMapper` / retire the legacy `JwtTokenGenerator` divergence.
  - Phase 2 is **gated separately** (its own ADR-acceptance-or-task) because of the operator blast radius; Phase 1 stands alone as the production-bug fix.

- **D4 — `sub` ≠ `identity_id`.** The contract `sub` is the **account_id**, not the ADR-036 central `identity_id`. The original TASK-MONO-291 seed premise (provision `identity_id` → UUID `sub`) is recorded as invalid; `identity_id` remains the cross-platform linkage key, orthogonal to `sub`.

## 3. Consequences

- **Positive** — restores `jwt-standard-claims` compliance for the user-key path; fixes a latent production defect (all ecommerce consumer browser-origin authed writes); Phase 1 is additive + operator-zero-risk and ships immediately; establishes `account_id` as the canonical cross-service user key with a clean Phase-2 path to full `sub` compliance.
- **Negative** — Phase 1 introduces a temporary `X-User-Id`-from-claim deviation from the contract's `X-User-Id ← sub` letter (documented, retired in Phase 2). Phase 2 carries real operator-migration cost (assume-tenant resolution key), deferred behind its own gate.
- **Neutral** — the legacy `JwtTokenGenerator`/`OidcUserInfoMapper` `sub=accountId` convention is documented as the (coincidentally contract-aligned) legacy path; full reconciliation is Phase 2.

## 4. Alternatives Considered

- **Alt A — ecommerce user-service relaxes the binding (accept email / resolve email→account_id).** Rejected: propagates the contract violation into ecommerce; `user_profiles`/`wishlist_items` are keyed by the account UUID, so the service would still need the UUID — an email key forces either a schema re-key (large) or a per-request account-service lookup (latency/coupling). D1 keeps the violation at its source.
- **Alt B — flip SAS `sub` → account_id globally, now (no phasing).** Rejected as the *immediate* move: breaks every operator's assume-tenant (`resolveAssignment` ↔ `oidc_subject=email`) until migrated. This is exactly Phase 2 — correct end-state, but gated behind the operator migration rather than shipped in the unblock.
- **Alt C — gateway-only resolution without an `account_id` claim.** Rejected: `account_id` is not on the SAS access token today (it lives in the principal `details`, server-side only), so the gateway has nothing to map from without the D2 additive claim.

## 5. Verification

- **Phase 1 acceptance** = the federation demo-stack web-store **wishlist e2e passes 3/3** under `SKIP_GAP_E2E=0` (the TASK-FE-074/BE-394 spec's 3rd test — currently blocked by this defect), plus an auth-service test asserting the `account_id` claim is emitted and a gateway test asserting `X-User-Id` = account_id (fallback to `sub`). This is the live proof TASK-MONO-291 AC-2 specified.
- **No operator regression** — the federation `tenant-switch-rescope` / `entitlement-trust` e2e (assume-tenant) stay green (Phase 1 does not touch `sub`).

## 6. Decision log

| Date | Status | Task | Note |
|---|---|---|---|
| 2026-06-17 | PROPOSED | TASK-MONO-291 | D1–D4 recorded; CHOSEN-PROPOSED = contract-compliant fix at the producer, phased (P1 additive `account_id` claim + gateway derive; P2 `sub`=account_id + operator migration). Awaiting explicit ACCEPT gate (self-ACCEPT prohibited). |
| 2026-06-17 | ACCEPTED | TASK-MONO-291 | User-explicit *"accept"*. D1–D4 finalised byte-unchanged (no re-decide); flip = Status + Accepted clause + this row. Phase 1 implementation child gated-open; Phase 2 stays separately gated. |
| 2026-06-18 | EXECUTED (P1) | TASK-MONO-292 | **Phase 1 (D2) shipped.** Additive `account_id` claim emitted by `TenantClaimTokenCustomizer`; ecommerce gateway derives `X-User-Id` from it (fallback `sub`). Operator-zero-risk (`sub` untouched). |
| 2026-06-18 | EXECUTED (P2) | TASK-MONO-295 | **Phase 2 (D3) shipped.** SAS access-token `sub` overridden to the account UUID (`TenantClaimTokenCustomizer`); ecommerce gateway restored to `X-User-Id ← sub` (Phase-1 `account_id` derive demoted to a transitional in-flight-token fallback). Operator assume-tenant resolution migrated to a **DUAL-KEY** transition (account_id `sub` first, legacy email fallback) across `AssumeTenantAuthenticationProvider` → `OperatorAssignmentPort`/`AdminAssignmentClient` → admin `/internal/operator-assignments/check` (additive `subjectEmail`) → `OperatorAssignmentCheckUseCase` — no operator regresses. The cross-DB `oidc_subject` email→account_id backfill (auth_db ⟂ admin_db, so NOT a Flyway step) + removal of the email fallback + the transitional `account_id` claim + the gateway fallback are the deferred **Phase-3** follow-up. `JwtTokenGenerator`/`OidcUserInfoMapper` documented as already `sub`=accountId-aligned (no change). Unit `:test` GREEN (auth/admin/ecommerce-gateway); operator-no-regression live authority = federation `tenant-switch-rescope`/`entitlement-trust` e2e (Testcontainers ITs host-blocked). |
| 2026-06-18 | EXECUTED (P3 part A) | TASK-MONO-298 | **Phase 3 part A — `oidc_subject` email→account_id backfill mechanism + seed shipped; dual-key fallback RETAINED.** Production-shaped runnable backfill (not a doc-only SQL recipe): a NEW auth-service internal endpoint `POST /internal/auth/credentials/account-id-by-email` (POST + body — email is PII, no PII in URL; **tenant-scoped** because `credentials.email` is unique only per `(tenant_id, email)` — `uk_credentials_tenant_email`, V0007 — so a global lookup could mis-resolve across tenants; fail-soft `accountId=null` on no-match/ambiguous) + an idempotent admin-service maintenance endpoint `POST /internal/admin/operator-oidc-subject-backfill` (processes only email-shaped `oidc_subject` — contains `@`, not UUID-parseable; resolves account_id via the auth endpoint passing the operator's tenant; UPDATE in place; fail-soft leaves unresolved rows unchanged + counted so no operator regresses; returns `{scanned,updated,skippedAlreadyUuid,skippedNull,unresolved}`; PII-safe audit — logs the key-shape transition, never the email value) + federation/platform-console e2e seeds updated so `admin_operators.oidc_subject` = the matching seeded `credentials.account_id` (operators WITH a credential; object-only operators with no credential stay email-shaped and are left untouched by the fail-soft backfill — they never log in). The **dual-key fallback, the transitional `account_id` claim (`TenantClaimTokenCustomizer`), and the 4 gateways' legacy-email fallback are all RETAINED/untouched** — their removal is the gated **Part B (MONO-299)**, ordered AFTER this is merged + verified (ADR-019 D6 dual-accept→remove discipline: backfill+verify FIRST, then remove the fallback). No decision reversed (Phase 3 was already named deferred in the P2 row). Unit `:test` GREEN (auth/admin); live authority = federation operator-login/assume-tenant e2e (Testcontainers ITs host-blocked). |
| 2026-06-18 | EXECUTED (P2 fix) | TASK-MONO-295 | **Login-time operator-token-exchange DUAL-KEY (fed-e2e `not_provisioned` fix).** The federation-hardening-e2e gate caught that Phase 2 broke operator **login** (not assume-tenant): `loginAsSuperAdmin` redirected to `/login?error=not_provisioned`. Root cause = a SECOND, distinct operator-resolution-by-`sub` path missed in the first P2 commit — the **login-time** RFC 8693 exchange in **admin-service** `TokenExchangeService` (`POST /api/admin/auth/token-exchange`, reached *directly* by console-web, NOT via auth-service), which resolved `findByOidcSubject(account_id)` single-key → miss (oidc_subject=email) → 401 → `not_provisioned` → every operator login broke. Fix: same DUAL-KEY as assume-tenant, now via a SHARED `OperatorOidcSubjectResolver` (both `TokenExchangeService` + `OperatorAssignmentCheckUseCase` delegate to it — a third sub-keyed path can't silently miss the migration). The login-time path resolves the email fallback **server-side** from the validated `sub` via a new internal auth-service endpoint `GET /internal/auth/credentials/{accountId}/email` (same `CredentialRepository.findByAccountId` source the assume-tenant provider reads locally — admin-service can't reach `auth_db.credentials`); the lookup is **fail-soft** (auth down → account_id-only), the operator-resolution fail-closed invariant unchanged. No PII on any token. console-web NOT modified (its callback correctly fail-closes on a real 401). Unit `:test` GREEN (auth/admin); live authority = federation operator-login e2e (Testcontainers ITs host-blocked). |

## 7. Provenance

Grounded in the 2026-06-17 TASK-MONO-291 AC-0 code investigation: `CredentialAuthenticationProvider` (SAS principal=email), `TenantClaimTokenCustomizer` (no `sub` override), `JwtTokenGenerator`/`OidcUserInfoMapper` (`sub`=accountId), `AssumeTenantAuthenticationProvider` (operator `sub`=email dependency), ecommerce `JwtHeaderEnrichmentFilter`/`WishlistController`, and `platform/contracts/jwt-standard-claims.md` (`sub`=account UUID, `X-User-Id ← sub`). No code changed by this ADR.
