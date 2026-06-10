# Task ID

TASK-BE-347

# Title

ADR-MONO-024 § 3.3 step 2b — the delegated-administration **behavioral surface**: (i) the assign/unassign `operator_tenant_assignment` surface (`POST`/`DELETE /api/admin/operators/{operatorId}/assignments/{tenantId}`, the missing core "grant my employee access to my tenant" operation) and (ii) the **grant-menu no-escalation confinement** on role grants (`PATCH .../roles` + `POST /operators`) — a non-platform admin may grant only roles whose permissions ⊆ its own and never `SUPER_ADMIN`; this admits in-tenant `TENANT_ADMIN` sub-delegation (D4-B) and `TENANT_BILLING_ADMIN` (D5-C) **only** when the actor holds the matching delegating permission. Closes the step-2a safety gap so `TENANT_ADMIN` is safe to hold. SUPER_ADMIN unconstrained ⇒ net-zero.

# Status

review

> **구현 완료 (2026-06-10)**: ADR-MONO-024 § 3.3 step 2b. **Part A(assign/unassign)**: `OperatorOrgScopeController` POST/DELETE `.../assignments/{tenantId}` + `ManageOperatorAssignmentUseCase` + 포트 `createAssignment`/`deleteAssignment`/`assignmentExists` + `ActionCode.OPERATOR_ASSIGNMENT_CREATE`/`DELETE` + `AssignmentAlreadyExistsException`(409); step-1 `TenantScopeGuard` 로 테넌트 confine. **Part B(grant-menu)**: 신규 `RoleGrantGuard`(플랫폼=unconstrained net-zero; 비-플랫폼=SUPER_ADMIN 금지 + ≤-own → ROLE_GRANT_FORBIDDEN), `PatchOperatorRoleUseCase`+`CreateOperatorUseCase` 배선, `RoleGrantForbiddenException`(403) + best-effort `recordRoleGrantForbidden`. `rbac.md`(grant-menu + assign surface) + `admin-api.md`(2 엔드포인트 + ROLE_GRANT_FORBIDDEN/ASSIGNMENT_ALREADY_EXISTS) 문서화. **검증**: admin-service 전체 unit+slice GREEN, 전체 integrationTest GREEN(87 tests). 신규 IT `TenantAdminDelegationIntegrationTest`(assign confine 201/403/409 · unassign 204/404/403 · grant-menu deny SUPER_ADMIN/SUPPORT_LOCK/TENANT_BILLING_ADMIN 403 · admit TENANT_ADMIN sub-delegation 200 · SUPER_ADMIN net-zero) + `RoleGrantGuardTest`. **함정**: MySQL 대소문자무시 collation → IT UUID 대소문자 충돌(소문자 hex가 타 IT 대문자 hex와 매칭→seed 미수행) → 전부-숫자 task-band UUID 로 회피. ⚠️ 2a 안전갭(grant-menu 부재) 폐쇄 — 이제 TENANT_ADMIN grant 안전. 후속=step 3(delegation proof federation-e2e). 분석=Opus 4.8 / 구현=Opus 4.8.

# Owner

backend

# Task Tags

- rbac
- multi-tenant
- iam
- security
- adr

---

# Dependency Markers

- **implements**: ADR-MONO-024 D3 (assign/unassign surface + grant-menu confinement) + D4-B (in-tenant `TENANT_ADMIN` sub-delegation admission) + D5-C (`TENANT_BILLING_ADMIN` admission) + D7 step 2b.
- **builds on**: TASK-BE-345 (step 1 D2 confinement — reused on the assign/unassign surface) + TASK-BE-346 (step 2a — the roles this menu admits).
- **closes**: the step-2a interim gap (a granted `TENANT_ADMIN` had no grant-menu protection). After this task, granting `TENANT_ADMIN` is safe.
- **prerequisite for**: ADR-024 step 3 (delegation proof federation-e2e).

# Goal

Supply the missing core delegation operation (assign/unassign) and the no-escalation grant-menu so a tenant-admin can manage its tenant's operators without a platform ticket and can never exceed or escape its grant. Both gated by the step-1 D2 confinement; SUPER_ADMIN net-zero.

# Scope

**Part A — assign/unassign surface** (`OperatorOrgScopeController`, base `/api/admin/operators/{operatorId}/assignments`):
- `POST /{tenantId}` — create an `operator_tenant_assignment` row (whole-tenant, inherit operator-level roles; `org_scope=null`, `permission_set_id=null`). `@RequiresPermission(operator.manage)` + step-1 `TenantScopeGuard`(target=path tenantId) + reason-gated + audited (`OPERATOR_ASSIGNMENT_CREATE`). Existing row → 409 `ASSIGNMENT_ALREADY_EXISTS`. 201 + assignment view.
- `DELETE /{tenantId}` — remove the row. Same gating + audited (`OPERATOR_ASSIGNMENT_DELETE`). Missing row → 404 `ASSIGNMENT_NOT_FOUND`. 204.
- Port: `createAssignment` / `deleteAssignment` / `assignmentExists` on `OperatorTenantAssignmentPort` (+ impl).
- New `ActionCode.OPERATOR_ASSIGNMENT_CREATE` / `OPERATOR_ASSIGNMENT_DELETE`; new `AssignmentAlreadyExistsException` (409).

**Part B — grant-menu no-escalation** (`RoleGrantGuard`, wired into `PatchOperatorRoleUseCase` + `CreateOperatorUseCase`):
- Platform-scope actor (`'*' ∈ effectiveAdminScope(actor, operator.manage)`) → unconstrained (net-zero, SUPER_ADMIN).
- Non-platform actor — for each role being granted: deny (`403 ROLE_GRANT_FORBIDDEN`, audited) if the role is `SUPER_ADMIN`, or if the actor does not hold ALL of the role's permissions (≤-own). This naturally admits `TENANT_ADMIN` (perms `{operator.manage, tenant.admin.delegate}`) only for an actor holding both (D4-B sub-delegation), and `TENANT_BILLING_ADMIN` (`{subscription.manage}`) only for an actor holding `subscription.manage` (D5-C); the tenant confinement (step-1) already binds the grant to the actor's tenant.
- New `RoleGrantForbiddenException` (403); best-effort DENIED audit row (mirrors cross-tenant deny).

**Spec/contract:** `rbac.md` (grant-menu rule + assign/unassign surface) + `admin-api.md` (the two endpoints + `ROLE_GRANT_FORBIDDEN` / `ASSIGNMENT_ALREADY_EXISTS`).

# Acceptance Criteria

- **AC-1 (assign)** `POST .../assignments/{tenantId}` by a tenant-x admin creates the row for tenant-x (201) and is denied (403 `TENANT_SCOPE_DENIED`) for tenant-y; a duplicate → 409; SUPER_ADMIN may assign any tenant (net-zero).
- **AC-2 (unassign)** `DELETE .../assignments/{tenantId}` removes the row (204), 404 when absent, tenant-confined like AC-1; audited.
- **AC-3 (grant-menu deny)** A non-platform `TENANT_ADMIN` (holds `{operator.manage, tenant.admin.delegate}`) patching/creating an operator with role `SUPER_ADMIN` → 403 `ROLE_GRANT_FORBIDDEN` (+ DENIED row); with role `TENANT_BILLING_ADMIN` (needs `subscription.manage`, not held) → 403; with `SUPPORT_LOCK` (needs `account.lock` etc., not held) → 403.
- **AC-4 (grant-menu admit — sub-delegation)** The same `TENANT_ADMIN` (holding `tenant.admin.delegate`) may grant `TENANT_ADMIN` to a peer in its own tenant (200/201) — perms ⊆ own. A `TENANT_BILLING_ADMIN` actor (holds `subscription.manage`) may grant `TENANT_BILLING_ADMIN`.
- **AC-5 (net-zero)** SUPER_ADMIN (`'*'`) grant menu is unconstrained — every existing operator create/patch IT for SUPER_ADMIN passes byte-identically; full unit+slice + integrationTest GREEN.
- **AC-6** `rbac.md` + `admin-api.md` document the surface + grant-menu rule + new error codes.

# Related Specs

- `docs/adr/ADR-MONO-024-tenant-admin-delegation.md` (D3/D4-B/D5-C/D7 step 2b)
- `projects/iam-platform/specs/services/admin-service/rbac.md`
- `docs/adr/ADR-MONO-020-operator-multitenant-assignment.md` (the assignment plane being mutated)

# Related Contracts

- `projects/iam-platform/specs/contracts/http/admin-api.md` (the two new endpoints + error codes)

# Edge Cases

- Net-zero: SUPER_ADMIN bypasses the grant-menu (unconstrained) and passes the assign confinement (`'*'`); no existing behavior changes.
- `≤-own` naturally encodes the delegated-role admission: `TENANT_ADMIN`'s perm set includes `tenant.admin.delegate`, so only an actor holding `tenant.admin.delegate` can grant `TENANT_ADMIN` (sub-delegation); only a `subscription.manage` holder can grant `TENANT_BILLING_ADMIN`.
- A role with an empty permission set is grantable by anyone (≤-own trivially) — guard must not deny on empty.
- Assign/unassign is tenant-confined (step-1), not role-menu-confined (that governs role grants only) — distinct concerns.
- Both DENIED audit rows (TENANT_SCOPE_DENIED on assign, ROLE_GRANT_FORBIDDEN on menu) are best-effort (A10 override), like the BE-249 cross-tenant deny.

# Failure Scenarios

- If the grant-menu were skipped, a `TENANT_ADMIN` could PATCH an operator to `SUPER_ADMIN` and escalate — the single most dangerous IAM bug. ≤-own + SUPER_ADMIN exclusion prevents it; AC-3 guards it.
- If the assign surface were not tenant-confined, a tenant-x admin could grant operators access to tenant-y — AC-1/AC-2 confine via step-1.
- If `≤-own` were computed against the operational scope rather than the actor's permission set, the check would be meaningless — it must compare role permissions to the actor's held permissions.
- If the grant-menu denied empty-permission roles, benign grants would break — empty perms must pass.
