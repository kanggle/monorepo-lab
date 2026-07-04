-- TASK-BE-477 / ADR-MONO-045 D2/D4 (§ 3.4 step 2) — seed the partnership.manage
-- permission key onto the TENANT_ADMIN role.
--
-- INERT / NET-ZERO: this adds ONE admin_role_permissions mapping row and assigns the
-- role to NO operator (admin_operator_roles untouched). An existing TENANT_ADMIN
-- holder gains the partnership MANAGEMENT surface (invite/accept/participant), but the
-- D2 TenantScopeGuard confines it to partnerships where the acting-side tenant is a
-- party, and with no partnership rows nothing derives. partnership.manage is
-- intentionally NOT granted to SUPER_ADMIN — a partnership is a relationship between
-- two real customer tenants; the platform is not a party (the D2-C broker gate is
-- deferred, mirroring tenant.admin.delegate ❌ on SUPER_ADMIN).
--
-- Idempotent (INSERT IGNORE) for Flyway repair / replay. Mirrors V0032/V0033.

INSERT IGNORE INTO admin_role_permissions (role_id, permission_key)
SELECT id, 'partnership.manage' FROM admin_roles WHERE name = 'TENANT_ADMIN';
