-- TASK-BE-028b1: idempotent re-seed of the RBAC role + permission matrix.
-- V0006 performs the initial seed; this migration guarantees the matrix is
-- present even on environments where V0006 failed partway or drift occurred.
-- INSERT IGNORE makes each row a no-op when already present.

INSERT IGNORE INTO admin_roles (name, description, require_2fa, created_at) VALUES
    ('SUPER_ADMIN',       'Full platform administrator',                          FALSE, NOW(6)),
    ('SUPPORT_READONLY',  'CS L1. Read-only access to audit and security events', FALSE, NOW(6)),
    ('SUPPORT_LOCK',      'CS L2. Account control + audit read',                  FALSE, NOW(6)),
    ('SECURITY_ANALYST',  'Security team. Audit + security events + force logout',FALSE, NOW(6));

-- SUPER_ADMIN
INSERT IGNORE INTO admin_role_permissions (role_id, permission_key)
SELECT id, 'account.lock'          FROM admin_roles WHERE name = 'SUPER_ADMIN';
INSERT IGNORE INTO admin_role_permissions (role_id, permission_key)
SELECT id, 'account.unlock'        FROM admin_roles WHERE name = 'SUPER_ADMIN';
INSERT IGNORE INTO admin_role_permissions (role_id, permission_key)
SELECT id, 'account.force_logout'  FROM admin_roles WHERE name = 'SUPER_ADMIN';
INSERT IGNORE INTO admin_role_permissions (role_id, permission_key)
SELECT id, 'audit.read'            FROM admin_roles WHERE name = 'SUPER_ADMIN';
INSERT IGNORE INTO admin_role_permissions (role_id, permission_key)
SELECT id, 'security.event.read'   FROM admin_roles WHERE name = 'SUPER_ADMIN';

-- SUPPORT_READONLY
INSERT IGNORE INTO admin_role_permissions (role_id, permission_key)
SELECT id, 'audit.read'            FROM admin_roles WHERE name = 'SUPPORT_READONLY';
INSERT IGNORE INTO admin_role_permissions (role_id, permission_key)
SELECT id, 'security.event.read'   FROM admin_roles WHERE name = 'SUPPORT_READONLY';

-- SUPPORT_LOCK
INSERT IGNORE INTO admin_role_permissions (role_id, permission_key)
SELECT id, 'account.lock'          FROM admin_roles WHERE name = 'SUPPORT_LOCK';
INSERT IGNORE INTO admin_role_permissions (role_id, permission_key)
SELECT id, 'account.unlock'        FROM admin_roles WHERE name = 'SUPPORT_LOCK';
INSERT IGNORE INTO admin_role_permissions (role_id, permission_key)
SELECT id, 'account.force_logout'  FROM admin_roles WHERE name = 'SUPPORT_LOCK';
INSERT IGNORE INTO admin_role_permissions (role_id, permission_key)
SELECT id, 'audit.read'            FROM admin_roles WHERE name = 'SUPPORT_LOCK';

-- SECURITY_ANALYST
INSERT IGNORE INTO admin_role_permissions (role_id, permission_key)
SELECT id, 'audit.read'            FROM admin_roles WHERE name = 'SECURITY_ANALYST';
INSERT IGNORE INTO admin_role_permissions (role_id, permission_key)
SELECT id, 'security.event.read'   FROM admin_roles WHERE name = 'SECURITY_ANALYST';
INSERT IGNORE INTO admin_role_permissions (role_id, permission_key)
SELECT id, 'account.force_logout'  FROM admin_roles WHERE name = 'SECURITY_ANALYST';
