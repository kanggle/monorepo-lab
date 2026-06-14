# Task ID

TASK-BE-376

# Title

ADR-MONO-035 **O1 / step 4a** — operator JWT domain-role issuance: derive the operator's domain `roles` at **assume-tenant** issuance time from the **selected tenant's entitled domains** (the operator-role mirror of `RoleSeedPolicy`, keyed on the assumed tenant's domains). Replaces BE-370's preserve-from-base for the operator case (the base operator token — `aud=platform-console`, `tenant_id='gap'` — structurally has no domain-role set to preserve). Makes every operator's assumed token carry domain roles so the domain gateways admit operators via the **`roles` leg** — the prerequisite (step 4a) that **unblocks the `account_type` drop (step 4b)**. Additive / net-zero (the `account_type` leg is untouched).

# Status

done

> **완료 (2026-06-14, close-chore TASK-MONO-264)**: PR #1574 squash `ef5a44bf3`, 3-dim verified (state=MERGED, mergeCommit=origin/main tip, Build & Test + iam Testcontainers IT pass 3m2s). Operator assumed-token roles derived from selected tenant's entitled_domains; unblocked the account_type drop (4b).

# Owner

backend

# Task Tags

- iam
- auth-service
- security
- adr-035

---

# Dependency Markers

- **executes**: ADR-MONO-035 (ACCEPTED) **O1** + **O5 step 4a**. Child of ADR-032 D5 step 4; sibling refinement of ADR-033 S4.
- **refines**: ADR-MONO-033 S4 / TASK-BE-370 — the assume-tenant role handling changes from *preserve-the-operator's-roles-from-the-base-token* to *derive-from-the-selected-tenant's-entitled-domains* (for operators the base token has no domain context to preserve; domain roles are meaningful only per assumed tenant).
- **must precede**: step 4b (`account_type` drop, TASK-BE-377). The 4a-before-4b ordering invariant = zero mis-auth window: operators must carry `roles` BEFORE the `account_type` dual-read leg is removed.
- **keeps disjoint**: `admin_operator_roles` (admin-console RBAC) — the derived domain role comes from the assignment + selected-tenant domain, NEVER from admin RBAC (ADR-033 S2 / ADR-034 U5).
- **reuses**: the existing fail-closed assignment gate (`OperatorAssignmentCheckUseCase`) + the `entitled_domains` fetch (`AccountServicePort.listEntitledDomains`, ADR-019 keystone) — no new admin/account call.

# Goal

On the assume-tenant (RFC 8693 token-exchange) path, the assumed token's `roles` claim is **derived from the selected tenant's ACTIVE entitled domains**: each entitled domain maps to its operator role (`wms→WMS_OPERATOR`, `ecommerce→ADMIN`, `scm→SCM_OPERATOR`, `erp→ERP_OPERATOR`, `finance→FINANCE_OPERATOR`, `fan`/`fan-platform→FAN_OPERATOR`, `mes→MES_OPERATOR`; `gap`/unknown → no role). The operator (already fail-closed-verified as assigned to the selected tenant) gets the operator role(s) for everything that tenant is entitled to, so the domain gateway's `roles` leg admits them.

# Scope

- **NEW** `projects/iam-platform/apps/auth-service/.../infrastructure/oauth2/OperatorRoleDerivation.java` — pure, framework-free, package-private (mirror of `RoleSeedPolicy`): `static List<String> fromEntitledDomains(List<String> domainKeys)` → the ordered, de-duplicated operator-role list (immutable, never null; empty for null/empty input or all-unknown domains). Domain-key → operator-role table as above.
- **MODIFY** `TenantClaimTokenCustomizer.java`:
  - `customizeForAssumeTenant`: **replace** the BE-370 preserve-from-base `roles` block with derive-from-entitled-domains. Refactor `populateEntitledDomains` to **return** the fetched entitled-domains list (so the fetch happens once — inject `entitled_domains` + derive `roles` from the same list); on the assume-tenant branch, after injecting `entitled_domains`, compute `OperatorRoleDerivation.fromEntitledDomains(entitled)` and inject the `roles` claim when non-empty. **fail-soft** (ADR-033 S5): an account-service failure / empty entitled set → `entitled_domains` omitted AND `roles` omitted (the gateway then 403s = net-zero); never throw. The `account_type`/`org_scope`/`tenant_id` injection is **unchanged**.
  - The `authorization_code`/`refresh_token` path (`populateRoles` — consumer seed + stored `account_roles`) is **UNCHANGED** (4a touches only the operator assume-tenant path).
- **MODIFY** `AssumeTenantAuthenticationProvider.java` + `AssumeTenantAuthenticationToken.java`: the `operatorRoles` (subject-token roles) plumbing is **no longer the role source**. Keep the change minimal — stop threading the preserved roles into the customizer's role decision; if the field is left in place it must be unused-by-issuance (prefer removing the now-dead preserve path to avoid confusion, but do NOT change the `operatorAccountType`/`orgScope` plumbing — those are still live for BE-329/BE-338).
- **TESTS** — `TenantClaimTokenCustomizerTest`: update the assume-tenant `roles` cases from preserve-from-subject to derive-from-entitled-domains (e.g. selected tenant entitled `[finance, wms]` → assumed `roles=[FINANCE_OPERATOR, WMS_OPERATOR]`; entitled `[ecommerce]` → `[ADMIN]`; empty/failed fetch → `roles` absent). NEW `OperatorRoleDerivationTest` (pure unit: known/unknown/empty/duplicate domains). `AssumeTenantAuthenticationProviderTest`: adjust any preserve-roles assertions.
- NO migration (pure issuance logic). NO contract change. NO gateway change. `account_type` emission + dual-read legs **untouched** (that is 4b).

# Acceptance Criteria

- **AC-1** On assume-tenant, the assumed token's `roles` = the operator roles derived from the selected tenant's ACTIVE entitled domains (verified by a `TenantClaimTokenCustomizer` assume-tenant test: entitled `[finance, wms]` → `roles` contains `WMS_OPERATOR`).
- **AC-2** `OperatorRoleDerivation.fromEntitledDomains` maps each known domain key to its operator role, de-duplicates, preserves a stable order, and returns an immutable empty list for null/empty/all-unknown input (pure unit test).
- **AC-3** fail-soft: an account-service failure or empty entitled set → both `entitled_domains` and `roles` omitted from the assumed token; issuance never throws (test).
- **AC-4** Net-zero / additive: the `account_type` claim, `org_scope`, `tenant_id`/`tenant_type`, and the `authorization_code` consumer roles path are byte-behavior-unchanged (existing BE-329/BE-338/BE-369 tests still pass).
- **AC-5** The derived role comes ONLY from the entitled domains, never from `admin_operator_roles` (no admin-RBAC read added).
- **AC-6** auth-service Docker-free `:test` GREEN locally; CI `Integration (iam, Testcontainers)` is the authoritative wiring gate (local Docker IT blocked by the host's Testcontainers version regression).

# Related Specs

- `docs/adr/ADR-MONO-035-operator-auth-unification-model.md` (§ O1 + § O5 4a + § 6)
- `docs/adr/ADR-MONO-033-roles-issuance-resolution-model.md` (§ S4 — the assume-tenant role handling this refines; § S5 fail-soft)
- `docs/adr/ADR-MONO-020-operator-multitenant-assignment.md` (the assume-tenant exchange + assignment gate)
- `projects/iam-platform/specs/services/auth-service/architecture.md` (issuance / assume-tenant exchange)

# Related Contracts

- `platform/contracts/jwt-standard-claims.md` — `roles` claim shape unchanged (additive population only); NOT amended in 4a.

# Edge Cases

- Selected tenant entitled to multiple domains → union of operator roles (de-duplicated, e.g. `[finance, wms]` → `[FINANCE_OPERATOR, WMS_OPERATOR]`).
- Selected tenant entitled only to `gap`/unknown domains → empty derived roles → `roles` omitted → gateway 403 (net-zero least-privilege; correct).
- account-service down / circuit-open → fail-soft (both claims omitted); the assume-tenant **assignment** gate stays fail-CLOSED (unchanged — an unassigned operator still gets no token).
- assume-tenant branch must never run on `client_credentials` (recursion guard — unchanged; the derivation is only on the token-exchange branch).

# Failure Scenarios

- If the derivation reads `admin_operator_roles` → violates ADR-033 S2 / ADR-034 U5 disjointness; it must derive only from entitled domains.
- If a fetch failure throws instead of fail-soft → breaks issuance availability (ADR-033 S5); the derivation/fetch must swallow and omit.
- If the `account_type` leg or the consumer `authorization_code` roles path is changed → out of 4a scope (4a is additive/net-zero; the `account_type` drop is 4b).
- If `roles` are derived but `entitled_domains` injection regresses → both ride the same fetch; keep them consistent.
