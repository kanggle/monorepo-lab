-- TASK-MONO-771 S5 (ADR-MONO-080 D4 · R2, owner decision OD-1) — seed the `tenant.security.manage`
-- permission key: read / turn on / turn off a tenant's ENTRY POLICY («entering this tenant as an operator
-- requires a second factor», admin-api.md § Tenant Entry Policy, table V0047).
--
-- INERT / NET-ZERO: role→permission mappings ONLY. No operator is newly assigned a role
-- (`admin_operator_roles` untouched) and no policy row is written, so no entry decision changes — the
-- `tenant_entry_policy` table stays empty until someone uses the new API. Same discipline as
-- V0033 / V0040 / V0041 / V0045.
--
-- Holder set = {SUPER_ADMIN, TENANT_ADMIN} (rbac.md § Seed Matrix). SUPER_ADMIN reaches every tenant
-- ('*' grant); TENANT_ADMIN only its grant's tenant (D2 TenantScopeGuard, target = path tenantId).
-- ORG_ADMIN is deliberately ❌ — OD-1 chose «that tenant's TENANT_ADMIN + SUPER_ADMIN», not org-node
-- subtree delegation (additive later if needed). `account.2fa_reset` (rbac.md, same TASK) is NOT seeded
-- here — it belongs to S6 together with its endpoint; seeding a key with no endpoint and no code constant
-- would make GET /api/admin/roles list a key GET /api/admin/permissions does not.
--
-- Idempotent (INSERT IGNORE) for Flyway repair / replay. Highest existing version is V0047; migration-dev
-- holds only V0014 / V0023 / V0028 + repeatables, so V0048 collides with nothing.

-- SUPER_ADMIN → tenant.security.manage (every tenant).
INSERT IGNORE INTO admin_role_permissions (role_id, permission_key)
SELECT id, 'tenant.security.manage' FROM admin_roles WHERE name = 'SUPER_ADMIN';

-- TENANT_ADMIN → tenant.security.manage (its grant's tenant only).
INSERT IGNORE INTO admin_role_permissions (role_id, permission_key)
SELECT id, 'tenant.security.manage' FROM admin_roles WHERE name = 'TENANT_ADMIN';
