-- Seed account.read permission for SUPER_ADMIN and SUPPORT_READONLY roles.
-- Uses INSERT IGNORE for idempotent replay (Flyway repair / re-run scenarios).
INSERT IGNORE INTO admin_role_permissions (role_id, permission_key)
SELECT r.id, 'account.read'
FROM admin_roles r
WHERE r.name IN ('SUPER_ADMIN', 'SUPPORT_READONLY');
