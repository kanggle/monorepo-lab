-- TASK-MONO-771 S6 (ADR-MONO-080 D4, ticket Edge Case 2, owner decision OD-6) — seed the `account.2fa_reset`
-- permission key: reset ANOTHER account's account-plane second factor (auth-service `account_totp`) for a person
-- who lost both the authenticator app and the recovery codes (admin-api.md § POST
-- /api/admin/accounts/{accountId}/2fa/reset).
--
-- INERT / NET-ZERO: role→permission mappings ONLY. No operator is newly assigned a role
-- (`admin_operator_roles` untouched) and nothing is reset by this migration. Same discipline as
-- V0033 / V0040 / V0041 / V0045 / V0048.
--
-- Holder set = {SUPER_ADMIN, SECURITY_ANALYST} (rbac.md § Seed Matrix). Platform roles only — OD-6:
-- accounts are personal pool accounts after ADR-MONO-080, so a reset changes that person's shopping / fan
-- login security too; a company admin is not given it. TENANT_ADMIN, TENANT_BILLING_ADMIN, ORG_ADMIN,
-- SUPPORT_READONLY and SUPPORT_LOCK are deliberately ❌. Holding the key is not enough on its own: the
-- endpoint additionally requires the caller's grant to be platform scope ('*') — a SECURITY_ANALYST
-- provisioned under a customer tenant gets 403 TENANT_SCOPE_DENIED.
--
-- Shipped in the SAME change as the key constant (Permission.ACCOUNT_2FA_RESET) and its endpoint, so
-- GET /api/admin/roles never lists a key GET /api/admin/permissions (the code catalog) does not — the reason
-- S5 kept it out of V0048.
--
-- Idempotent (INSERT IGNORE) for Flyway repair / replay. Highest existing version is V0048; migration-dev
-- holds only V0014 / V0023 / V0028 + repeatables, so V0049 collides with nothing.

-- SUPER_ADMIN → account.2fa_reset.
INSERT IGNORE INTO admin_role_permissions (role_id, permission_key)
SELECT id, 'account.2fa_reset' FROM admin_roles WHERE name = 'SUPER_ADMIN';

-- SECURITY_ANALYST → account.2fa_reset.
INSERT IGNORE INTO admin_role_permissions (role_id, permission_key)
SELECT id, 'account.2fa_reset' FROM admin_roles WHERE name = 'SECURITY_ANALYST';
