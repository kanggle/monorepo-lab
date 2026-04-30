-- TASK-BE-081: seed account.read permission for SUPER_ADMIN and SUPPORT_READONLY.
-- This permission was added to rbac.md Seed Matrix but was not present in V0009.

INSERT IGNORE INTO admin_role_permissions (role_id, permission_key)
SELECT id, 'account.read' FROM admin_roles WHERE name = 'SUPER_ADMIN';

INSERT IGNORE INTO admin_role_permissions (role_id, permission_key)
SELECT id, 'account.read' FROM admin_roles WHERE name = 'SUPPORT_READONLY';
