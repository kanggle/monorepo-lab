# Task ID

TASK-MONO-265

# Title

ADR-MONO-032 **D5 step 5** (the e2e/verification close-out of the unified-identity roadmap) — assert, at the IdP issuance authority, the converged end state after 4a/4b/4c/4d: a single account obtains BOTH a consumer login token and an operator domain token in one session, **roles-only, with NO `account_type` claim on either**. Plus the ADR-MONO-032 / ADR-MONO-035 step-4+step-5 closure notes and the close-chore for TASK-BE-377 / TASK-BE-378 (ready → done).

# Status

done

> **완료 (2026-06-15)**: PR #<this>. step-5 issuance e2e (`AssumeTenantExchangeIntegrationTest.step5_unifiedIdentity_...`) + ADR-032 D5 / ADR-035 §3.3 closure + BE-377/BE-378 ready→done. 3-dim verified at merge.

# Owner

backend

# Task Tags

- iam
- auth-service
- adr-032
- adr-035
- close-chore

---

# Dependency Markers

- **closes**: ADR-MONO-032 **D5 step 5** (the verification step) + the whole ADR-MONO-035 execution roadmap (4a BE-376 → 4b MONO-261/262/263 → 4c BE-377 → 4d BE-378 → step 5 here).
- **depends on**: 4a/4b/4c/4d all merged on `main` (operators carry derived domain roles; `account_type` fully dropped; operator credential converged; `credentials.identity_id` added).
- **verification split**: the operator gateway roles-only ADMISSION is asserted by the gateway ITs (MONO-262); the ADR-034 operator↔central-identity LINK by the admin-service operator-identity-link ITs (BE-373/374). This task asserts the **issuance** authority (the IdP emits exactly the converged token shapes).
- **close-chore**: TASK-BE-377 + TASK-BE-378 `ready → done` (their impl PRs #1585/#1586 merged + 3-dim verified).

# Goal

A single Testcontainers IT in the auth-service issuance authority proves the converged unified-identity end state: one account → (1) a consumer login token (authorization_code, `demo-spa-client` = `fan-platform`) carrying the seeded consumer role `FAN`, NO `account_type`; and (2) by assume-tenant, an operator domain token carrying the operator domain roles DERIVED from the selected tenant's entitled domains (`WMS_OPERATOR` + `ADMIN`), NO `account_type` — both tokens belonging to the same subject, obtained in one session. `roles` is the sole authorization axis for both consumer and operator capability.

# Scope

- **TEST** `projects/iam-platform/apps/auth-service/.../integration/AssumeTenantExchangeIntegrationTest.java` — NEW `step5_unifiedIdentity_oneAccount_consumerAndOperatorTokens_rolesOnly`: mint a base login token (consumer role `FAN`, no `account_type`) + assume-tenant for an operator-assigned tenant entitled to `wms`+`ecommerce` → operator domain token (`roles` ⊇ {`WMS_OPERATOR`,`ADMIN`}, no `account_type`); same `sub`.
- **DOCS** `docs/adr/ADR-MONO-032-...md` (D5 ROADMAP COMPLETE closure note — steps 0-5 landed) + `docs/adr/ADR-MONO-035-...md` (§ 3.3 roadmap items 1-5 marked DONE with PR refs + STEP 4 + STEP 5 COMPLETE note).
- **CLOSE-CHORE** `projects/iam-platform/tasks/ready/TASK-BE-377-*.md` + `TASK-BE-378-*.md` → `tasks/done/` (Status `ready → done` + 3-dim completion notes).
- NO production-code change (verification + closure only).

# Acceptance Criteria

- **AC-1** The step-5 IT asserts: one account's base login token carries `roles` ⊇ {`FAN`} and NO `account_type`; the same account's assume-tenant operator token carries `roles` ⊇ {`WMS_OPERATOR`,`ADMIN`} (derived from entitled domains) and NO `account_type`; both share the same `sub`.
- **AC-2** ADR-MONO-032 D5 + ADR-MONO-035 §3.3 carry the step-4+step-5 completion closure notes with the executing task/PR references.
- **AC-3** TASK-BE-377 + TASK-BE-378 are moved `ready → done` with their 3-dim merge notes.
- **AC-4** auth-service Docker-free `:test` GREEN locally; CI `Integration (iam, Testcontainers)` runs the step-5 IT (authoritative). 0 failing required checks at merge.

# Related Specs

- `docs/adr/ADR-MONO-032-unified-identity-roles-model.md` (D5 step 5)
- `docs/adr/ADR-MONO-035-operator-auth-unification-model.md` (§ O5 step 5, § 3.3)
- `docs/adr/ADR-MONO-033` / `ADR-MONO-034` (roles issuance + account/credential unify the e2e ties together)

# Related Contracts

- `platform/contracts/jwt-standard-claims.md` — the converged claim shape the IT asserts (`roles` sole axis, `account_type` absent). Unchanged in this task (finalized at 4b/MONO-263).

# Edge Cases

- The base token's consumer role is seeded fail-soft (`RoleSeedPolicy`, `fan-platform → FAN`) when no stored `account_roles` exist — the IT account is fresh, so the seed path is exercised.
- The operator role derivation is fail-soft on entitled-domains fetch failure (omit roles → gateway 403) — the IT stubs a successful entitled-domains response so the derivation fires.
- The assignment gate stays fail-CLOSED (an unassigned operator gets no token) — unchanged; the IT stubs an assigned operator.

# Failure Scenarios

- If either token carries an `account_type` claim → 4b regression (the drop did not fully land); the IT fails.
- If the operator token carries no `roles` (derivation regressed) → 4a regression; the IT fails.
- If the two tokens have different `sub` → the unified-identity invariant is broken.
