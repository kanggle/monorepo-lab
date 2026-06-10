# Task ID

TASK-BE-346

# Title

ADR-MONO-024 § 3.3 step 2a — seed the two delegated-administration roles (`TENANT_ADMIN`, `TENANT_BILLING_ADMIN`) + the new `tenant.admin.delegate` permission. Data/catalog layer only (Flyway seed + `Permission` constant + `rbac.md` Seed Roles/Matrix/Permission-Keys). **Inert / net-zero**: the migration adds role definitions + role→permission mappings but assigns the roles to no operator; existing roles/operators are byte-unchanged. The roles only take effect once granted (ADR-024 step 2b adds the assign/unassign surface + grant-menu that makes them grantable by a tenant-admin; until then a platform `SUPER_ADMIN` may grant the first one).

# Status

done

> **완료 (2026-06-10)**: impl PR #1256 (squash `af306f50f90da140f3423957e7bfdecbb7450b63`). 3차원 검증 ✓ (MERGED / origin/main tip=`af306f50` 일치 / CI 전부 pass — `Integration (iam, Testcontainers)` GREEN 으로 마이그레이션 + 신규 시드 IT 검증). 후속=step 2b(assign/unassign 표면 + grant-menu no-escalation).
>
> **구현 (2026-06-10)**: ADR-MONO-024 § 3.3 step 2a — 두 위임관리 역할 + `tenant.admin.delegate` 권한 시드 (inert/net-zero). `V0033__seed_tenant_admin_roles.sql`(INSERT IGNORE: `TENANT_ADMIN`→{operator.manage, tenant.admin.delegate}, `TENANT_BILLING_ADMIN`→{subscription.manage}, operator 배정 0) + `Permission.TENANT_ADMIN_DELEGATE` 상수 + `rbac.md`(Permission Keys/Seed Roles/Seed Matrix + net-zero 주석). **검증**: 신규 IT `TenantAdminRoleSeedIntegrationTest`(시드 정확성 + plane separation[subscription.manage∉TENANT_ADMIN, tenant.admin.delegate∉SUPER_ADMIN] + 실제 TENANT_ADMIN@tenant-x 의 step-1 D2 confinement: tenant-x 200 / tenant-y 403) PASS; net-zero 회귀(OperatorAdmin/OperatorAdminScopeConfinement) GREEN. 후속=step 2b(assign/unassign 표면 + grant-menu no-escalation). ⚠️ 2b 전까지 TENANT_ADMIN 을 어떤 operator 에도 grant 금지(grant-menu 보호 부재; 시드는 미배정이라 안전). 분석=Opus 4.8 / 구현=Opus 4.8.

# Owner

backend

# Task Tags

- rbac
- multi-tenant
- iam
- adr

---

# Dependency Markers

- **implements**: ADR-MONO-024 D1 (`TENANT_ADMIN` role model) + D4-B (`tenant.admin.delegate` permission) + D5-C (separate `TENANT_BILLING_ADMIN` role) + D7 step 2a (roles seed).
- **builds on**: TASK-BE-345 (step 1 D2 confinement) — the moment an operator is granted `TENANT_ADMIN @ acme`, the step-1 confinement already confines it to acme (proven here by an IT using the REAL role rather than step-1's simulated scoped grant).
- **prerequisite for**: ADR-024 step 2b (assign/unassign `operator_tenant_assignment` surface + grant-menu no-escalation confinement incl. in-tenant sub-delegation). **Until 2b lands, `TENANT_ADMIN` must not be granted to any operator** (no grant-menu protection yet) — safe because the seed assigns it to nobody.

# Goal

Make the two delegated-administration roles + the delegation permission exist as committed catalog data so step 2b can wire the surface/menu against real roles, and so a `TENANT_ADMIN` is immediately confined by step-1's evaluator. Data-only; no endpoints, no evaluator/menu logic.

# Scope

- **NEW `V0033__seed_tenant_admin_roles.sql`** (admin-service `db/migration`) — idempotent `INSERT IGNORE`:
  - `admin_roles`: `TENANT_ADMIN` ("Tenant-scoped delegated administrator — manages its tenant's operators"), `TENANT_BILLING_ADMIN` ("Tenant-scoped entitlement administrator — manages its tenant's domain subscriptions").
  - `admin_role_permissions`: `TENANT_ADMIN` → `operator.manage`, `tenant.admin.delegate`; `TENANT_BILLING_ADMIN` → `subscription.manage`.
- `Permission.java` — add `TENANT_ADMIN_DELEGATE = "tenant.admin.delegate"`.
- `rbac.md` — Permission Keys (add `tenant.admin.delegate`), Seed Roles (add the two roles + intent), Seed Matrix (add rows/columns; `'*'` net-zero note), reference ADR-024 D1/D4-B/D5-C.

**Out of scope (step 2b):** the assign/unassign endpoints, the grant-menu no-escalation confinement (`ROLE_GRANT_FORBIDDEN`), sub-delegation admission, `admin-api.md` surface changes.

# Acceptance Criteria

- **AC-1** After migration, `admin_roles` contains `TENANT_ADMIN` and `TENANT_BILLING_ADMIN`; `TENANT_ADMIN` maps to exactly `{operator.manage, tenant.admin.delegate}` and `TENANT_BILLING_ADMIN` to exactly `{subscription.manage}` (IT-verified).
- **AC-2 (net-zero)** Existing roles' permission sets are byte-unchanged; no operator is assigned either new role by the seed; the full admin-service unit+slice + integrationTest suites stay GREEN.
- **AC-3 (real-role confinement smoke)** An operator granted `TENANT_ADMIN` with an `admin_operator_roles` row `tenant_id='tenant-x'` may `PATCH .../roles` a tenant-x operator (200) and is denied (403 `TENANT_SCOPE_DENIED`) for a tenant-y operator — i.e. step-1 confinement governs the real role. (Grant-menu no-escalation is NOT yet enforced — that is 2b; this IT grants a benign role and asserts the tenant gate only.)
- **AC-4** `Permission.TENANT_ADMIN_DELEGATE` exists; `rbac.md` documents the permission + both roles + matrix. No endpoint/menu/contract change.

# Related Specs

- `docs/adr/ADR-MONO-024-tenant-admin-delegation.md` (D1/D4-B/D5-C/D7 step 2a)
- `projects/iam-platform/specs/services/admin-service/rbac.md` (Seed Roles + Matrix + Permission Keys)
- `docs/adr/ADR-MONO-023-entitlement-iam-plane-separation.md` (`subscription.manage` separability realized as `TENANT_BILLING_ADMIN`)

# Related Contracts

- `projects/iam-platform/specs/contracts/http/admin-api.md` (unchanged in 2a; the grant-menu/assign surface lands in 2b)

# Edge Cases

- Idempotent seed (`INSERT IGNORE`) — Flyway repair / replay safe; production migration band is gapless (next is V0033, after V0032).
- Net-zero: a role definition with no holders changes no behavior. The first `TENANT_ADMIN` holder appears only via an explicit grant (step 2b surface, or a `SUPER_ADMIN` PATCH in the interim).
- `tenant.admin.delegate` is seeded ONLY onto `TENANT_ADMIN` (not SUPER_ADMIN) — SUPER_ADMIN grants the first tenant-admin via its unconstrained authority, not via this permission; the permission gates *in-tenant sub-delegation* (2b grant-menu).

# Failure Scenarios

- If the migration assigned a role to an operator, it would no longer be net-zero (and could create an un-protected TENANT_ADMIN before 2b's grant-menu) — the seed MUST only define roles + mappings.
- If `tenant.admin.delegate` were seeded onto SUPER_ADMIN, it would imply SUPER_ADMIN delegates via that permission (it does not — it is platform-unconstrained); keep it TENANT_ADMIN-only.
- If `subscription.manage` were added to `TENANT_ADMIN`, the IAM and entitlement planes re-couple (ADR-023/ADR-024 D5-C violation) — it belongs only to `TENANT_BILLING_ADMIN`.
