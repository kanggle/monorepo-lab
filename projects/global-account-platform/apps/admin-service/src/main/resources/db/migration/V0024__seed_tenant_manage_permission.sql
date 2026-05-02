-- TASK-BE-250: seed tenant.manage permission for SUPER_ADMIN.
-- Per specs/contracts/http/admin-api.md §Tenant Lifecycle (TASK-BE-256),
-- only SUPER_ADMIN may call /api/admin/tenants endpoints.
-- Uses INSERT IGNORE for idempotent replay (Flyway repair / re-run scenarios).
INSERT IGNORE INTO admin_role_permissions (role_id, permission_key)
SELECT r.id, 'tenant.manage'
FROM admin_roles r
WHERE r.name = 'SUPER_ADMIN';
