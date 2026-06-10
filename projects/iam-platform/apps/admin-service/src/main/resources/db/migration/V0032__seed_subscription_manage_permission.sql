-- TASK-BE-343 (ADR-MONO-023 § 3.3 step 2b — D3): seed subscription.manage for SUPER_ADMIN.
--
-- Per specs/services/admin-service/rbac.md §Seed Matrix, subscription.manage is
-- granted only to SUPER_ADMIN. It is DISTINCT from operator.manage — the
-- entitlement plane (tenant↔domain subscription lifecycle) and the IAM plane
-- (operator management) are separately delegable (ADR-023 D2/D3); a future
-- tenant-admin delegation may grant one without the other.
--
-- Uses INSERT IGNORE for idempotent replay (mirrors V0022 operator.manage seed).
INSERT IGNORE INTO admin_role_permissions (role_id, permission_key)
SELECT r.id, 'subscription.manage'
FROM admin_roles r
WHERE r.name = 'SUPER_ADMIN';
