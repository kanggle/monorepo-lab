-- TASK-BE-028a: seed RBAC roles + permission matrix per rbac.md Seed Matrix.
-- Operator seeding is environment-specific and handled outside Flyway.

INSERT INTO admin_roles (name, description, require_2fa, created_at) VALUES
    ('SUPER_ADMIN',       'Full platform administrator',                          FALSE, NOW(6)),
    ('SUPPORT_READONLY',  'CS L1. Read-only access to audit and security events', FALSE, NOW(6)),
    ('SUPPORT_LOCK',      'CS L2. Account control + audit read',                  FALSE, NOW(6)),
    ('SECURITY_ANALYST',  'Security team. Audit + security events + force logout',FALSE, NOW(6));

-- SUPER_ADMIN: all permissions
INSERT INTO admin_role_permissions (role_id, permission_key)
SELECT id, 'account.lock'          FROM admin_roles WHERE name = 'SUPER_ADMIN';
INSERT INTO admin_role_permissions (role_id, permission_key)
SELECT id, 'account.unlock'        FROM admin_roles WHERE name = 'SUPER_ADMIN';
INSERT INTO admin_role_permissions (role_id, permission_key)
SELECT id, 'account.force_logout'  FROM admin_roles WHERE name = 'SUPER_ADMIN';
INSERT INTO admin_role_permissions (role_id, permission_key)
SELECT id, 'audit.read'            FROM admin_roles WHERE name = 'SUPER_ADMIN';
INSERT INTO admin_role_permissions (role_id, permission_key)
SELECT id, 'security.event.read'   FROM admin_roles WHERE name = 'SUPER_ADMIN';

-- SUPPORT_READONLY: read-only (audit + security events)
INSERT INTO admin_role_permissions (role_id, permission_key)
SELECT id, 'audit.read'            FROM admin_roles WHERE name = 'SUPPORT_READONLY';
INSERT INTO admin_role_permissions (role_id, permission_key)
SELECT id, 'security.event.read'   FROM admin_roles WHERE name = 'SUPPORT_READONLY';

-- SUPPORT_LOCK: account control + audit.read only (no security events)
INSERT INTO admin_role_permissions (role_id, permission_key)
SELECT id, 'account.lock'          FROM admin_roles WHERE name = 'SUPPORT_LOCK';
INSERT INTO admin_role_permissions (role_id, permission_key)
SELECT id, 'account.unlock'        FROM admin_roles WHERE name = 'SUPPORT_LOCK';
INSERT INTO admin_role_permissions (role_id, permission_key)
SELECT id, 'account.force_logout'  FROM admin_roles WHERE name = 'SUPPORT_LOCK';
INSERT INTO admin_role_permissions (role_id, permission_key)
SELECT id, 'audit.read'            FROM admin_roles WHERE name = 'SUPPORT_LOCK';

-- SECURITY_ANALYST: audit + security events + force_logout
INSERT INTO admin_role_permissions (role_id, permission_key)
SELECT id, 'audit.read'            FROM admin_roles WHERE name = 'SECURITY_ANALYST';
INSERT INTO admin_role_permissions (role_id, permission_key)
SELECT id, 'security.event.read'   FROM admin_roles WHERE name = 'SECURITY_ANALYST';
INSERT INTO admin_role_permissions (role_id, permission_key)
SELECT id, 'account.force_logout'  FROM admin_roles WHERE name = 'SECURITY_ANALYST';
