-- TASK-BE-083: seed operator.manage permission for SUPER_ADMIN.
-- Per specs/services/admin-service/rbac.md §Seed Matrix, operator.manage is
-- granted only to SUPER_ADMIN. Uses INSERT IGNORE for idempotent replay
-- (Flyway repair / re-run scenarios), matching V0020/V0021.
INSERT IGNORE INTO admin_role_permissions (role_id, permission_key)
SELECT r.id, 'operator.manage'
FROM admin_roles r
WHERE r.name = 'SUPER_ADMIN';
