# Task ID

TASK-BE-345

# Title

ADR-MONO-024 § 3.3 step 1 — D2 target-tenant scope confinement in admin-service (net-zero). Add a central `effectiveAdminScope(operator, permission)` (the set of `tenant_id`s of the actor's `admin_operator_roles` rows that grant the permission; `'*'` = platform-all) + a single confinement gate that denies (`403 TENANT_SCOPE_DENIED`, audited) when an `operator.manage` / `subscription.manage` mutation targets a tenant outside that scope. Wire the gate onto the existing operator/assignment/subscription admin mutation surfaces. **Net-zero**: the only seeded holder of these permissions is `SUPER_ADMIN` (`tenant_id='*'`), which passes the gate for every tenant — every existing endpoint is byte-identical for `SUPER_ADMIN`.

# Status

done

> **완료 (2026-06-10)**: impl PR #1254 (squash `6f999c7d688a2b90a7d6b30e5ab49f761d38ac50`). 3차원 검증 ✓ (MERGED / origin/main tip=`6f999c7d` 일치 / CI 전부 pass — 특히 `Integration (iam, Testcontainers)` GREEN 으로 신규 confinement IT + net-zero 회귀를 CI 가 직접 검증). 후속=ADR-024 step 2(roles+surface+menu) / step 3(delegation proof e2e).
>
> **구현 (2026-06-10)**: ADR-MONO-024 § 3.3 step 1 — D2 target-tenant scope confinement (net-zero). 신규 `AdminGrantScopeEvaluator`(permission별 `admin_operator_roles.tenant_id` grant 스코프, `'*'`=플랫폼, fail-closed) + 중앙 `TenantScopeGuard`(단일 결정 지점: `target ∈ scope` || `'*'` → 통과, else best-effort DENIED row + 403 `TENANT_SCOPE_DENIED`). 5개 변이 use-case 배선(create/roles/status/org-scope=`operator.manage`, subscribe/changeStatus=`subscription.manage` D5-C). `rbac.md` Permission Evaluation Algorithm 에 confinement 단계 문서화. **net-zero 검증**: admin-service 전체 unit+slice GREEN, 전체 integrationTest GREEN (SUPER_ADMIN `'*'` 모든 기존 엔드포인트 byte-identical). 신규 IT `OperatorAdminScopeConfinementIntegrationTest`: net-zero(SA 양 테넌트 200) + in-scope(tenant-x→tenant-x 200) + confinement(tenant-x→tenant-y 403+DENIED row, 무변이) + create confinement(tenant-y 403 / tenant-x 201) 전부 PASS. 신규 unit `AdminGrantScopeEvaluatorTest`(7) + `TenantScopeGuardTest`(2). 후속=ADR-024 step 2(roles+surface+menu) / step 3(delegation proof e2e). 분석=Opus 4.8 / 구현=Opus 4.8.

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

- **implements**: ADR-MONO-024 D2 (the crux — central target-tenant confinement) + D7 step 1 (net-zero confinement evaluation), authorized ACCEPTED by TASK-MONO-209 (`docs/adr/ADR-MONO-024-tenant-admin-delegation.md`).
- **prerequisite for**: ADR-024 step 2 (the two seed roles `TENANT_ADMIN`/`TENANT_BILLING_ADMIN` + `tenant.admin.delegate` + assign/unassign surface + grant-menu) and step 3 (delegation proof e2e). Those land in later tasks.
- **extends, does NOT modify**: the BE-249/BE-326 *operational* tenant scope (`TenantScopeResolver` = home ∪ `operator_tenant_assignment`) which gates assume-tenant. ADR-024 D2 introduces a **distinct** admin-grant scope (per-permission `admin_operator_roles.tenant_id`) for the *administration* surface — the two axes are independent and both remain in force.

# Goal

Make the "which tenant may this operator administer" decision a single, central, fail-closed rule grounded in the role-grant tenant scope, so that when ADR-024 step 2 seeds a non-platform `TENANT_ADMIN`/`TENANT_BILLING_ADMIN`, the confinement is already enforced and proven. This task ships the **mechanism + wiring + net-zero proof only** — no new roles, no new endpoints, no new permission.

# Scope

**New (admin-service):**

- `AdminGrantScopeEvaluator` (`infrastructure/persistence/rbac`) — `effectiveAdminScope(operatorId, permission) → Set<String>`: load the ACTIVE operator → its `admin_operator_roles` rows (each carries `tenant_id`, V0025/26) → the subset whose role grants `permission` (via `admin_role_permissions`) → collect those rows' `tenant_id`. `'*'` included verbatim. Fail-closed (empty set on any error). Plus `isTenantInAdminScope(operatorId, permission, targetTenantId)`: `true` iff scope contains `'*'` (net-zero for SUPER_ADMIN) or `targetTenantId`; `null` target → deny.
- `TenantScopeGuard` (`application`) — `requireTenantInScope(actor, permission, targetTenantId, actionCode)`: returns silently when in scope; otherwise writes the best-effort cross-tenant DENIED row (`AdminActionAuditor.recordCrossTenantDenied`) and throws `TenantScopeDeniedException` (→ 403 `TENANT_SCOPE_DENIED`). The single confinement decision site.

**New repository method:** `AdminRolePermissionJpaRepository.findRoleIdsGrantingPermission(permission, roleIds)`.

**Wired (each resolves its target tenant, then calls the guard — the rule is NOT re-implemented per site):**

- `CreateOperatorUseCase` — target = `tenantId` (created operator's home tenant); permission `operator.manage`. (After the existing platform-scope defense.)
- `PatchOperatorRoleUseCase` — target = managed operator's home `tenant_id`; permission `operator.manage`.
- `PatchOperatorStatusUseCase` — target = managed operator's home `tenant_id`; permission `operator.manage`.
- `ManageOperatorOrgScopeUseCase#setOrgScope` — target = path `tenantId`; permission `operator.manage`. (In addition to the existing `pathTenantId == activeTenant` mismatch check.)
- `ManageSubscriptionUseCase#subscribe`/`#changeStatus` — target = `tenantId`; permission `subscription.manage` (D5-C — the gate now covers the entitlement admin surface). Guard runs before the account-service delegation.

**Spec:** `projects/iam-platform/specs/services/admin-service/rbac.md` — add the confinement step to the Permission Evaluation Algorithm (post-union target-tenant gate), the `effectiveAdminScope` definition, the `'*'` net-zero note, and `TENANT_SCOPE_DENIED`. Reference ADR-MONO-024 D2.

**Out of scope (later steps):** `TENANT_ADMIN`/`TENANT_BILLING_ADMIN` roles, `tenant.admin.delegate` permission, assign/unassign endpoints, grant-menu confinement, sub-delegation — all ADR-024 step 2/3.

# Acceptance Criteria

- **AC-1 (effectiveAdminScope)** For an operator whose only `admin_operator_roles` row granting `operator.manage` has `tenant_id='acme'`, `effectiveAdminScope(op, operator.manage) == {"acme"}`; for `SUPER_ADMIN` (row `tenant_id='*'`) it is `{"*"}`; for an operator without the permission it is empty. Inactive/unknown operator → empty (fail-closed).
- **AC-2 (gate)** `isTenantInAdminScope` returns `true` when the scope contains `'*'` (any target) or the exact target; `false` for an out-of-scope target and for a `null` target (unless `'*'`).
- **AC-3 (net-zero)** Every existing operator/subscription admin mutation IT for `SUPER_ADMIN` passes byte-identically (200/201/expected) after the gate is added — no behavioral change for the `'*'` holder.
- **AC-4 (confinement, IT)** A non-platform operator holding `operator.manage` scoped to `tenant-x` (an `admin_operator_roles` row with `tenant_id='tenant-x'`) may `PATCH .../roles` an operator whose home tenant is `tenant-x` (200) but is denied (403 `TENANT_SCOPE_DENIED` + a DENIED `admin_actions` row whose detail records the attempted tenant) for an operator whose home tenant is `tenant-y`; `SUPER_ADMIN` is unaffected for both.
- **AC-5 (subscription confinement)** `ManageSubscriptionUseCase` invokes the guard with `subscription.manage` + the target `tenantId` before delegating to account-service (unit-verified); an out-of-scope target denies before any remote call.
- **AC-6 (single decision site)** The `target ∈ scope` rule, the `'*'` net-zero short-circuit, the 403, and the DENIED audit row exist in exactly one place (`TenantScopeGuard` + `AdminGrantScopeEvaluator`); the five call sites only resolve their target tenant. No per-endpoint re-implementation of the rule.
- **AC-7** `rbac.md` documents the confinement step + `effectiveAdminScope` + net-zero; no other spec/contract changed. No new role/permission/endpoint.

# Related Specs

- `docs/adr/ADR-MONO-024-tenant-admin-delegation.md` (D2 / D7 step 1 — the authority for this task)
- `projects/iam-platform/specs/services/admin-service/rbac.md` (Permission Evaluation Algorithm — extended)
- `docs/adr/ADR-MONO-020-operator-multitenant-assignment.md` (the assignment plane; operational scope stays independent)
- `docs/adr/ADR-MONO-023-entitlement-iam-plane-separation.md` (`subscription.manage` — the entitlement surface D5-C now confines)

# Related Contracts

- `projects/iam-platform/specs/contracts/http/admin-api.md` (403 response shape — `TENANT_SCOPE_DENIED` already defined; no contract change)

# Edge Cases

- **Net-zero is the whole point of step 1**: only `SUPER_ADMIN` (`'*'`) holds `operator.manage`/`subscription.manage` today, so the gate denies nobody currently — it must change zero existing behavior. The confinement only bites once step 2 seeds a non-platform admin role.
- Admin-grant scope ≠ operational (assume-tenant) scope: an operator assigned to *operate* {acme, globex} but granted `operator.manage` only @ acme must be denied administering globex operators. The gate reads the role-grant scope, never the assignment scope.
- `null` target tenant → deny (fail-closed): every gated mutation resolves a concrete target; a null indicates a wiring bug and must not silently pass.
- The cross-tenant DENIED row is best-effort (architecture.md A10 override, matching BE-249/BE-262) — a failed audit bumps `admin.audit.cross_tenant_deny_failure`; the 403 always stands.

# Failure Scenarios

- If the gate read the operational scope (`TenantScopeResolver`) instead of the per-permission grant scope, a multi-tenant-assigned operator could administer tenants it was never made admin of — the exact escalation D2 prevents. The evaluator MUST read `admin_operator_roles.tenant_id` filtered by the permission.
- If the rule were re-implemented per endpoint (D2-B, rejected), inconsistency breeds escalation holes — AC-6 requires a single decision site.
- If a `null` target were treated as "operator's own tenant" (legacy `isTenantAllowed` behavior), an admin mutation with an unresolved target could silently pass — the gate denies null.
- If the gate were not net-zero (e.g. read a stricter scope than `'*'` for SUPER_ADMIN), existing platform onboarding breaks — AC-3 guards this.
