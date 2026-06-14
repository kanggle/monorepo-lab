# Task ID

TASK-BE-372

# Title

account-service internal **identity resolve** read EP (`GET /internal/tenants/{tenantId}/accounts/{accountId}/identity` → `{identityId}`) — ADR-MONO-034 U6 **step 3b** / ADR-MONO-032 D5 step 3. Cross-store resolution foundation: resolve an `account_id` (e.g. an operator's `oidc_subject`) to the central `identity_id` (the registry from step 3a). **Additive + net-zero** (no caller yet — the operator-link surface in step 3c consumes it).

# Status

done

> **완료 (2026-06-14)**: PR #1550 squash `37a5bf6b1` — account-service identity resolve EP `GET /internal/tenants/{tid}/accounts/{aid}/identity` (ADR-MONO-034 U6 step 3b). AccountJpaRepository native projection(`identity_id` unmapped→merge-overwrite 0) + AccountRepository.findIdentityId + GetAccountIdentityUseCase + AccountIdentityController/Response(enumeration-safe 200 null) + 계약 문서화. **net-zero**(caller 0=3c가 첫 소비). **re-sequencing**: credentials.identity_id→step4 이동(U2 충실, login-path 작업). 3-dim verified(MERGED·`37a5bf6b1`·origin/main tip 일치·**Integration iam Testcontainers 2m16s**=native 쿼리 V0023 검증). Docker-free GetAccountIdentityUseCaseTest GREEN. 다음=step 3c(admin_operators.identity_id + opt-in 링크 surface). 분석=Opus 4.8 / 구현=Opus 4.8.

# Owner

backend-engineer

# Task Tags

- backend
- iam
- account-service
- security

---

# Dependency Markers

- **child of**: ADR-MONO-034 (ACCEPTED — account/credential unification). Second execution step (U6 step 3b).
- **depends on**: TASK-BE-371 (step 3a — the `identities` registry + `accounts.identity_id` this EP reads). Must be in `main` first (it is: PR #1547 squash `1d3072204`).
- **followed by**: step 3c (`admin_operators.identity_id` column + opt-in audited reversible link surface, U3 — the first **caller** of this EP, resolving `oidc_subject` → identity) → step 3d (unified provisioning, U4).
- **re-sequencing note (U2-faithful)**: ADR-034 U6 step 3b also lists `credentials.identity_id`. It is **moved to ADR-032 step 4** (login/credential consolidation, U2): the consumer credential's identity is already resolvable via `account_id` → `accounts.identity_id` (step 3a), nothing consumes `credentials.identity_id` in link-first step 3, and its only backfill paths are cross-DB or login-path work (= step 4). This is a sequencing refinement consistent with U2, not a decision reversal — recorded here and in the ADR-034 execution notes.
- **keeps disjoint** (U5): the EP returns only `identity_id` — no roles, no PII. `account_roles` / `admin_operator_roles` untouched.
- **does NOT amend** `jwt-standard-claims.md` — identity storage is IdP-internal; token issuance unchanged.

# Goal

Provide the read surface that resolves an account to its central identity, so the operator-link (3c) and provisioning (3d) steps can connect the disjoint operator store to the central registry. Additive, net-zero, no behavior change.

# Scope

- `infrastructure/persistence/AccountJpaRepository.java` — `findIdentityIdByTenantIdAndId` **native column projection** (`SELECT identity_id FROM accounts WHERE tenant_id=? AND id=?`). Native so `identity_id` stays **UNMAPPED** on `AccountJpaEntity` (no merge-overwrite of the step-3a backfilled value).
- `domain/repository/AccountRepository.java` + `infrastructure/persistence/AccountRepositoryImpl.java` — `findIdentityId(TenantId, accountId)` port method.
- `application/service/GetAccountIdentityUseCase.java` — read-only resolve (returns `Optional<String>`; empty = missing/foreign/unlinked, enumeration-safe).
- `presentation/internal/identity/AccountIdentityController.java` + `presentation/dto/response/AccountIdentityResponse.java` — `GET /internal/tenants/{tenantId}/accounts/{accountId}/identity`, `X-Tenant-Id` defense-in-depth, 200 `{identityId: null}` for foreign/missing/unlinked (no 404).
- `specs/contracts/http/internal/account-internal-provisioning.md` — document the new EP (contract-first).
- Tests: `GetAccountIdentityUseCaseTest` (Docker-free unit) + `AccountJpaRepositoryTest` new methods (Testcontainers — native resolve: linked → id, missing/cross-tenant/null → empty).

# Acceptance Criteria

- **AC-1** `GET /internal/tenants/{tenantId}/accounts/{accountId}/identity` returns `200 {accountId, tenantId, identityId}` where `identityId` is the account's central identity or `null`.
- **AC-2** Foreign/missing account OR account with NULL `identity_id` → `200 {identityId: null}` (no 404, enumeration-safe).
- **AC-3** `X-Tenant-Id` ≠ path `{tenantId}` → 403 `TENANT_SCOPE_DENIED` (defense-in-depth, mirrors the roles EP).
- **AC-4** `identity_id` is read via a **native projection** — `AccountJpaEntity` is NOT modified (the column stays unmapped; the step-3a backfilled value is never overwritten on account update).
- **AC-5** Net-zero: no caller wired in this task; no mutation/audit/outbox; `account_roles` / `admin_operator_roles` / token issuance / `jwt-standard-claims.md` unchanged.
- **AC-6** Contract documented in `account-internal-provisioning.md` (contract-first).
- **AC-7** Docker-free unit GREEN locally; Testcontainers IT GREEN on CI (native query against the real V0023 schema).

# Related Specs

- `docs/adr/ADR-MONO-034-account-credential-unification-model.md` (U6 step 3b, U1-A)
- `projects/iam-platform/specs/contracts/http/internal/account-internal-provisioning.md` (the internal EP catalog this extends)
- `projects/iam-platform/specs/services/account-service/architecture.md`

# Related Contracts

- `projects/iam-platform/specs/contracts/http/internal/account-internal-provisioning.md` — new `GET .../identity` EP (documented in this task).
- `platform/contracts/jwt-standard-claims.md` — NOT amended.

# Edge Cases

- Account exists but `identity_id` is NULL (new-account window before step 3d) → `null` (same as missing — caller fail-softs).
- Cross-tenant: `accountId` belongs to another tenant → `null` (the native query is `(tenant_id, id)`-scoped).
- A native single-column projection returning a NULL value wraps to `Optional.empty()` — so "missing row" and "row with NULL identity_id" both resolve to empty; acceptable (the caller treats both as "no resolvable identity").

# Failure Scenarios

- If `identity_id` is mapped on `AccountJpaEntity` to read it (instead of a native projection) → risks NULLing the backfilled value on account update (merge-overwrite). Must use the native projection.
- If the EP 404s on a missing account → enumeration leak; must return `200 {identityId: null}`.
- If `credentials.identity_id` is added here → out of scope (moved to step 4 per the re-sequencing note); this task is account-service-only.
- If a caller is wired in this task → not net-zero; the first caller is step 3c.
