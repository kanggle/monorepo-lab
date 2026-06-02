-- =============================================================================
-- TASK-MONO-170 — platform-console full-stack local-dev DEMO seed — GAP
-- =============================================================================
-- Applied against the per-project `gap-mysql` container (auth_db + admin_db)
-- AFTER GAP auth/account/admin services are healthy (GAP Flyway done).
--
--   docker exec -i gap-mysql mysql -uroot -prootpass < 01-gap.sql
--
-- Row content is the demo subset of tests/federation-hardening-e2e/fixtures/
-- seed.sql (the proven entitlement-trust demo data model), RE-TARGETED to the
-- per-project gap-mysql container instead of the fed-e2e shared mysql. The
-- fed-e2e CREATE DATABASE finance_db/erp_db clutter is intentionally OMITTED
-- here (finance/erp run their own MySQL containers in this topology).
--
-- Customers + entitlements come from GAP account-service Flyway (NOT this file):
--   - acme-corp  [finance,wms]  — db/migration/V0020 (loads in ALL profiles)
--   - globex-corp [scm,erp]     — db/migration-dev/V0021 (loads ONLY under the
--                                 `e2e` profile — the orchestration brings GAP
--                                 up with SPRING_PROFILES_ACTIVE=e2e for this)
--
-- Demo operator: `multi-operator@example.com` / devpassword123!  (home tenant
-- acme-corp; D1-assigned to BOTH acme-corp AND globex-corp). Logging in and
-- switching the active tenant A↔B re-scopes the signed domain-facing token
-- (entitled_domains flips finance/wms ↔ scm/erp) — the live ADR-MONO-020 proof.
--
-- Re-runnable: INSERT IGNORE / ON DUPLICATE KEY UPDATE.
-- =============================================================================

-- ---------------------------------------------------------------------------
-- auth_db — operator credential rows (Argon2id hash of 'devpassword123!').
--   account_type='OPERATOR' (ADR-MONO-021 D4 — without it the V0022 column
--   DEFAULT 'CONSUMER' mis-types these as CONSUMER).
-- ---------------------------------------------------------------------------
USE `auth_db`;

INSERT IGNORE INTO credentials (
    tenant_id, account_type, account_id, email,
    credential_hash, hash_algorithm, created_at, updated_at, version
) VALUES
(
    '*', 'OPERATOR', '01928c4a-7e9f-7c00-9a40-d2b1f5e8c100',
    'e2e-super-admin@example.com',
    '$argon2id$v=16$m=65536,t=3,p=1$7u/kw4KcLt7/i1nTEzEfsH7kRIraSsh1w9qOB7BhxUMTJdk3Oqp6zBklBlcMzJ4jS0PpgLYN+MW+1HlJF3m7ew$OJzCJkqvkul/EbS2FejjcDPx7Htj2HkAiCz74xcGBeY',
    'argon2id', NOW(6), NOW(6), 0
),
(
    'acme-corp', 'OPERATOR', '01928c4a-7e9f-7c00-9a40-d2b1f5e8c300',
    'multi-operator@example.com',
    '$argon2id$v=16$m=65536,t=3,p=1$7u/kw4KcLt7/i1nTEzEfsH7kRIraSsh1w9qOB7BhxUMTJdk3Oqp6zBklBlcMzJ4jS0PpgLYN+MW+1HlJF3m7ew$OJzCJkqvkul/EbS2FejjcDPx7Htj2HkAiCz74xcGBeY',
    'argon2id', NOW(6), NOW(6), 0
);

-- ---------------------------------------------------------------------------
-- admin_db — operators + role binding + 2FA relaxation + N:M assignments.
-- ---------------------------------------------------------------------------
USE `admin_db`;

-- Relax SUPER_ADMIN require_2fa for the local demo ONLY.
UPDATE admin_roles SET require_2fa = FALSE WHERE name = 'SUPER_ADMIN';

-- SUPER_ADMIN caller (tenant_id='*') — convenience login (sees GAP ops; '*'
-- has empty entitled_domains so the per-domain ops pages 403 for it — use the
-- multi-operator for the domain demo).
INSERT INTO admin_operators (
    operator_id, tenant_id, email, password_hash, display_name, status,
    oidc_subject, finance_default_account_id, created_at, updated_at, version
) VALUES (
    'e2e-super-admin', '*', 'e2e-super-admin@example.com',
    '$argon2id$v=16$m=65536,t=3,p=1$7u/kw4KcLt7/i1nTEzEfsH7kRIraSsh1w9qOB7BhxUMTJdk3Oqp6zBklBlcMzJ4jS0PpgLYN+MW+1HlJF3m7ew$OJzCJkqvkul/EbS2FejjcDPx7Htj2HkAiCz74xcGBeY',
    'E2E Super Admin', 'ACTIVE', 'e2e-super-admin@example.com',
    '01928c4a-7e9f-7c00-9a40-d2b1f5e8a000', NOW(6), NOW(6), 0
)
ON DUPLICATE KEY UPDATE
    tenant_id = VALUES(tenant_id), oidc_subject = VALUES(oidc_subject),
    finance_default_account_id = VALUES(finance_default_account_id),
    status = 'ACTIVE', updated_at = NOW(6);

-- Multi-assignment demo operator (home tenant = acme-corp). The D1 assignment
-- rows below grant the ADDITIONAL globex-corp scope; the assume-tenant exchange
-- (BE-327) mints a selected-tenant-scoped domain-facing token on switch.
INSERT INTO admin_operators (
    operator_id, tenant_id, email, password_hash, display_name, status,
    oidc_subject, finance_default_account_id, created_at, updated_at, version
) VALUES (
    'multi-operator', 'acme-corp', 'multi-operator@example.com',
    '$argon2id$v=16$m=65536,t=3,p=1$7u/kw4KcLt7/i1nTEzEfsH7kRIraSsh1w9qOB7BhxUMTJdk3Oqp6zBklBlcMzJ4jS0PpgLYN+MW+1HlJF3m7ew$OJzCJkqvkul/EbS2FejjcDPx7Htj2HkAiCz74xcGBeY',
    'Multi Operator', 'ACTIVE', 'multi-operator@example.com',
    '01928c4a-7e9f-7c00-9a40-d2b1f5e8a200', NOW(6), NOW(6), 0
)
ON DUPLICATE KEY UPDATE
    tenant_id = VALUES(tenant_id), oidc_subject = VALUES(oidc_subject),
    finance_default_account_id = VALUES(finance_default_account_id),
    status = 'ACTIVE', updated_at = NOW(6);

-- Bind both operators to SUPER_ADMIN (console-shell reachability; the tenant
-- ENTITLEMENT — not RBAC — is what gates the per-domain ops pages).
INSERT IGNORE INTO admin_operator_roles (operator_id, role_id, tenant_id, granted_at, granted_by)
SELECT o.id, r.id, o.tenant_id, NOW(6), NULL
  FROM admin_operators o JOIN admin_roles r ON r.name = 'SUPER_ADMIN'
 WHERE o.operator_id IN ('e2e-super-admin', 'multi-operator');

-- Two D1 operator_tenant_assignment rows (BE-326): multi-operator → BOTH
-- acme-corp AND globex-corp. permission_set_id NULL = inherit operator roles.
-- BE-326 dual-read effective scope = {acme-corp, globex-corp} ∪ {home} →
-- both surface in the switcher.
INSERT IGNORE INTO operator_tenant_assignment (operator_id, tenant_id, granted_at, granted_by, permission_set_id)
SELECT o.id, 'acme-corp', NOW(6), NULL, NULL
  FROM admin_operators o WHERE o.operator_id = 'multi-operator';

INSERT IGNORE INTO operator_tenant_assignment (operator_id, tenant_id, granted_at, granted_by, permission_set_id)
SELECT o.id, 'globex-corp', NOW(6), NULL, NULL
  FROM admin_operators o WHERE o.operator_id = 'multi-operator';
