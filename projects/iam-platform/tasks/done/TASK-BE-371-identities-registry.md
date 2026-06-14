# Task ID

TASK-BE-371

# Title

Central `identities` registry + one-identity-per-account backfill (ADR-MONO-034 U6 **step 3a** / ADR-MONO-032 D5 step 3 / D6-A). Introduce the canonical per-person identity registry that the account/credential unification links the consumer account (and, in later steps, the operator extension) to. **Additive + net-zero**: backfill one fresh identity per existing account; `accounts.identity_id` is NULLABLE and not yet wired into any account-creation path (that lands in step 3d), and not mapped on `AccountJpaEntity` (so account updates preserve the backfilled value).

# Status

done

> **완료 (2026-06-14)**: PR #1547 squash `1d3072204` — 중앙 `identities` 레지스트리 + one-identity-per-account backfill (ADR-MONO-034 U6 step 3a / ADR-032 D5 step 3 / D6-A). account-service `account_db`: V0023(identities 테이블 + nullable `accounts.identity_id` + fresh-UUID backfill[U1-A=account.id 재사용 안 함] + FK) + 도메인 Identity/IdentityId/IdentityStatus(roles-free U5) + IdentityRepository + JPA. **net-zero**(creation 경로·AccountJpaEntity 미변경; identity_id unmapped→update 시 backfill 값 보존). 3-dim verified(state=MERGED·origin/main tip `1d3072204`·pre-merge 전건 pass incl. **Integration iam Testcontainers 2m25s**=V0023 적용+FK+backfill SQL+schema validate 권위). 로컬 Docker-free IdentityTest GREEN. 다음=step 3b(cross-store identity_id on credentials+admin_operators, oidc_subject backfill). 분석=Opus 4.8 / 구현=Opus 4.8.

# Owner

backend-engineer

# Task Tags

- backend
- iam
- account-service
- security
- flyway

---

# Dependency Markers

- **child of**: ADR-MONO-034 (ACCEPTED — account/credential unification model). This is the first execution step (U6 step 3a) of its § 3.3 roadmap.
- **parent ADR**: ADR-MONO-032 D5 step 3 (account/credential unify) / D6-A (one account = one credential = role-grant set, opt-in link).
- **followed by**: step 3b (cross-store `identity_id` on `auth_db.credentials` + `admin_db.admin_operators`, backfill from `oidc_subject`) → 3c (opt-in audited reversible link surface, U3) → 3d (unified new-operator provisioning, U4 — wires creation paths to populate `accounts.identity_id`).
- **keeps disjoint** (ADR-034 U5 / ADR-033): the `identities` registry holds NO roles; `account_roles` (JWT domain roles) and `admin_operator_roles` (admin-console RBAC) are unchanged. Identity unification ≠ authorization merge.
- **does NOT amend** `platform/contracts/jwt-standard-claims.md` — identity storage is IdP-internal; no claim shape change; token issuance unchanged.

# Goal

Stand up the central identity registry as an additive, net-zero foundation: the `identities` table exists, every existing account is backfilled to exactly one fresh identity (a NEW UUID, distinct from `accounts.id` per U1-A), and `accounts.identity_id` references it (FK, NULLABLE). No behavior change, no token/issuance change, no account-creation-path change.

# Scope

- `projects/iam-platform/apps/account-service/src/main/resources/db/migration/V0023__create_identities_registry.sql` (NEW) — CREATE TABLE `identities` (`identity_id` PK, `tenant_id` FK→tenants, `primary_email`, `status`, timestamps, `version`; `uk_identities_tenant_email`) + ALTER `accounts` ADD `identity_id` VARCHAR(36) NULL + one-identity-per-account backfill (fresh `UUID()`, 1:1 join on `(tenant_id, email)`) + FK `fk_accounts_identity_id`.
- `domain/identity/Identity.java`, `IdentityId.java`, `IdentityStatus.java` (NEW) — aggregate + value objects (roles-free, U5).
- `domain/repository/IdentityRepository.java` (NEW) — `save` / `findById` / `findByTenantAndEmail` (tenant-scoped; email-match is the necessary-not-sufficient pre-condition the 3c link surface will build on).
- `infrastructure/persistence/IdentityJpaEntity.java` + `IdentityJpaRepository.java` + `IdentityRepositoryImpl.java` (NEW).
- Tests: `domain/identity/IdentityTest.java` (Docker-free unit) + `infrastructure/persistence/IdentityJpaRepositoryTest.java` (Testcontainers, CI authoritative — schema applies + FK + uniqueness + cross-tenant + backfill-SQL replay).
- **`AccountJpaEntity` is deliberately NOT changed** — `accounts.identity_id` stays unmapped so Hibernate never overwrites the backfilled value on an account update (additive net-zero).

# Acceptance Criteria

- **AC-1** V0023 creates `identities`, adds NULLABLE `accounts.identity_id`, backfills one fresh identity per existing account (1:1), and adds the FK. `identity_id` is a NEW UUID, never `accounts.id` (U1-A).
- **AC-2** The migration applies cleanly on the Testcontainers MySQL Flyway run (CI), and `spring.jpa.hibernate.ddl-auto=validate` passes (entity ↔ schema match).
- **AC-3** `accounts.identity_id` is NULLABLE and the FK permits NULL (a new account created before step 3d carries NULL — no creation-path change in this task).
- **AC-4** The `identities` registry holds NO roles/permissions (U5); `account_roles` / `admin_operator_roles` untouched; no `jwt-standard-claims.md` change; no token/issuance change.
- **AC-5** `IdentityRepository` resolves by id and by (tenant, email), tenant-scoped (no cross-tenant leak — IT-verified).
- **AC-6** Net-zero: no account-creation path (`SignupUseCase` / `SocialSignupUseCase` / `ProvisionAccountUseCase` / `BulkProvisionAccountUseCase`) is modified; `AccountJpaEntity` is not modified.
- **AC-7** Docker-free `:test` (domain unit) GREEN locally; Testcontainers IT GREEN on CI (the authoritative schema/wiring verification).

# Related Specs

- `docs/adr/ADR-MONO-034-account-credential-unification-model.md` (U1-A, U6 step 3a)
- `docs/adr/ADR-MONO-032-unified-identity-roles-model.md` (D5 step 3 / D6-A)
- `projects/iam-platform/specs/services/account-service/data-model.md` (accounts / account_roles — the consumer anchor this layers above)

# Related Contracts

- `platform/contracts/jwt-standard-claims.md` — NOT amended (identity storage is IdP-internal).

# Edge Cases

- Zero existing accounts → backfill inserts/updates nothing (INSERT…SELECT + UPDATE…JOIN both no-op).
- `(tenant_id, email)` is unique on `accounts`, so the backfill join is strictly 1:1 — no account links to two identities, no identity shared by two accounts.
- Dev-seed accounts (`db/migration-dev/` V9xxx) run AFTER V0023 → they carry NULL `identity_id` (acceptable; nullable column, no consumer).
- `AccountJpaEntity` must stay unmapped for `identity_id` — mapping it without threading the value through `fromDomain` would NULL the column on every account update (the merge-overwrite hazard). It is intentionally left to a later step that actually reads it.

# Failure Scenarios

- If `identity_id` is backfilled as `accounts.id` (reuse) → violates U1-A (person-id ≠ consumer-account-id). Must be a fresh `UUID()`.
- If `accounts.identity_id` is made NOT NULL in this task → breaks new account creation (no creation path populates it until step 3d). Must be NULLABLE.
- If an account-creation path or `AccountJpaEntity` is modified → not net-zero; defer provisioning wiring to step 3d.
- If the registry is given roles/permissions → violates U5 (identity unification ≠ authorization merge).
