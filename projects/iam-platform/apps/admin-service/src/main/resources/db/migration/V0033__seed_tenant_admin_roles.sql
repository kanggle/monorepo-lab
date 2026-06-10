-- TASK-BE-346 (ADR-MONO-024 § 3.3 step 2a — D1 / D4-B / D5-C): seed the two
-- delegated-administration roles + the tenant.admin.delegate permission.
--
-- INERT / NET-ZERO: this defines role rows + role→permission mappings only. It
-- assigns the roles to NO operator (admin_operator_roles is untouched), so no
-- operator's effective permissions change. The roles take effect only once an
-- operator is granted one (ADR-024 step 2b adds the grant-menu + assign/unassign
-- surface; until then a platform SUPER_ADMIN may grant the first TENANT_ADMIN).
--
-- The moment an operator holds TENANT_ADMIN via an admin_operator_roles row with
-- tenant_id='acme', the TASK-BE-345 (step 1) D2 confinement already confines its
-- operator.manage authority to acme.
--
-- Plane separation (ADR-023 / ADR-024 D5-C): subscription.manage lives on the
-- SEPARATE TENANT_BILLING_ADMIN role, NOT bundled into TENANT_ADMIN.
--
-- Idempotent (INSERT IGNORE) for Flyway repair / replay. Mirrors V0009/V0022/V0032.

INSERT IGNORE INTO admin_roles (name, description, require_2fa, created_at) VALUES
    ('TENANT_ADMIN',         'Tenant-scoped delegated administrator — manages its tenant''s operators (ADR-MONO-024 D1/D4-B)', FALSE, NOW(6)),
    ('TENANT_BILLING_ADMIN', 'Tenant-scoped entitlement administrator — manages its tenant''s domain subscriptions (ADR-MONO-024 D5-C)', FALSE, NOW(6));

-- TENANT_ADMIN → operator.manage + tenant.admin.delegate (IAM plane only).
INSERT IGNORE INTO admin_role_permissions (role_id, permission_key)
SELECT id, 'operator.manage'        FROM admin_roles WHERE name = 'TENANT_ADMIN';
INSERT IGNORE INTO admin_role_permissions (role_id, permission_key)
SELECT id, 'tenant.admin.delegate'  FROM admin_roles WHERE name = 'TENANT_ADMIN';

-- TENANT_BILLING_ADMIN → subscription.manage (entitlement plane only).
INSERT IGNORE INTO admin_role_permissions (role_id, permission_key)
SELECT id, 'subscription.manage'    FROM admin_roles WHERE name = 'TENANT_BILLING_ADMIN';
