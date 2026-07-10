-- TASK-BE-492 / ADR-MONO-047 D5 (§ 4 step 2b) — seed the `org.manage` permission key
-- and the `ORG_ADMIN` node-scoped seed role.
--
-- INERT / NET-ZERO: this defines one role row + role→permission mappings only. It
-- assigns ORG_ADMIN to NO operator (`admin_operator_roles` is untouched), so no
-- operator's effective permissions change and the `effectiveAdminScope` org-node
-- subtree branch (V0042 + AdminGrantScopeEvaluator) stays UNREACHABLE until an
-- operator is explicitly granted `ORG_ADMIN @ node`. Same discipline as
-- V0033__seed_tenant_admin_roles.sql and V0040__seed_partnership_manage_permission.sql.
--
-- SUPER_ADMIN additionally gains `org.manage` — it is the only principal that may
-- create a ROOT org-node (rbac.md § Org-Node Scope Confinement). This widens
-- SUPER_ADMIN, which is already platform-unconstrained (`tenant_id='*'`), so it is
-- not an escalation.
--
-- Plane separation (ADR-023 / ADR-047 D5): `subscription.manage` is deliberately NOT
-- granted to ORG_ADMIN in v1 — the entitlement plane stays with TENANT_BILLING_ADMIN.
-- `partnership.manage` is likewise NOT granted: a partnership is a relationship between
-- two customer tenants (ADR-045 D2) and even SUPER_ADMIN does not hold that key.
-- The consequence — an ORG_ADMIN cannot mint a TENANT_ADMIN, because RoleGrantGuard's
-- ≤-own rule (ADR-024 D3, reused unchanged) requires actor.permissions ⊇ rolePerms and
-- TENANT_ADMIN additionally holds `partnership.manage` — is a DELIBERATE v1 limitation
-- recorded as a follow-up ADR candidate. Do NOT widen this seed set to make a test pass.
--
-- Idempotent (INSERT IGNORE) for Flyway repair / replay. Highest existing version is
-- V0040; migration-dev holds no colliding version.

INSERT IGNORE INTO admin_roles (name, description, require_2fa, created_at) VALUES
    ('ORG_ADMIN', 'Org-node-scoped company-wide delegated administrator — manages the operators of every tenant in its node subtree, sub-delegates within it, and administers the org-node tree below it (ADR-MONO-047 D5)', FALSE, NOW(6));

-- SUPER_ADMIN → org.manage (sole ROOT-node creator).
INSERT IGNORE INTO admin_role_permissions (role_id, permission_key)
SELECT id, 'org.manage'            FROM admin_roles WHERE name = 'SUPER_ADMIN';

-- ORG_ADMIN → {org.manage, operator.manage, tenant.admin.delegate}. IAM plane only.
INSERT IGNORE INTO admin_role_permissions (role_id, permission_key)
SELECT id, 'org.manage'            FROM admin_roles WHERE name = 'ORG_ADMIN';
INSERT IGNORE INTO admin_role_permissions (role_id, permission_key)
SELECT id, 'operator.manage'       FROM admin_roles WHERE name = 'ORG_ADMIN';
INSERT IGNORE INTO admin_role_permissions (role_id, permission_key)
SELECT id, 'tenant.admin.delegate' FROM admin_roles WHERE name = 'ORG_ADMIN';
