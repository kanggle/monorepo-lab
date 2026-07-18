-- TASK-BE-520 / ADR-MONO-046 D6 (§ 4 step 2) — seed the `group.manage` permission key onto
-- the roles that already manage operators.
--
-- INERT / NET-ZERO: this adds role→permission mappings ONLY. It assigns `group.manage` to NO
-- new operator (`admin_operator_roles` is untouched) and creates NO group, so no operator's
-- effective permissions change and no fan-out row is materialised — the substrate stays
-- byte-identical until the first group is created + granted. Same discipline as
-- V0033__seed_tenant_admin_roles.sql / V0040__seed_partnership_manage_permission.sql /
-- V0041__seed_org_manage_permission_and_org_admin_role.sql.
--
-- Holder set = {SUPER_ADMIN, TENANT_ADMIN, ORG_ADMIN} — it exactly mirrors the
-- `operator.manage` holder set, deliberately: an operator group is a bulk-grant convenience
-- ON TOP of the operators/assignments these roles ALREADY manage, so the holder set is the
-- same. SUPPORT_READONLY / SUPPORT_LOCK / SECURITY_ANALYST (read/support only, manage no
-- operator) and TENANT_BILLING_ADMIN (entitlement plane only — no `operator.manage`) are ❌.
--
-- v1 is fan-out (D2-A): group membership is NOT an evaluation-time edge; PermissionEvaluator
-- / perm-cache / every confinement axis is byte-unchanged (rbac.md § Operator Group Fan-Out).
--
-- Idempotent (INSERT IGNORE) for Flyway repair / replay. Highest existing version is V0044
-- (this series); migration-dev holds no colliding version.

-- SUPER_ADMIN → group.manage (all tenants' groups; platform-unconstrained).
INSERT IGNORE INTO admin_role_permissions (role_id, permission_key)
SELECT id, 'group.manage' FROM admin_roles WHERE name = 'SUPER_ADMIN';

-- TENANT_ADMIN → group.manage (own tenant's groups; D3 TenantScopeGuard confinement).
INSERT IGNORE INTO admin_role_permissions (role_id, permission_key)
SELECT id, 'group.manage' FROM admin_roles WHERE name = 'TENANT_ADMIN';

-- ORG_ADMIN → group.manage (subtree tenants' groups; mirrors its operator.manage reach).
INSERT IGNORE INTO admin_role_permissions (role_id, permission_key)
SELECT id, 'group.manage' FROM admin_roles WHERE name = 'ORG_ADMIN';
