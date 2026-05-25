-- =============================================================================
-- TASK-MONO-139 — Phase 8 Federation Hardening e2e harness seed (phase 1.5)
-- =============================================================================
-- Runtime data fixture (NOT a Flyway migration). Executed against the
-- docker-compose.federation-e2e.yml MySQL container AFTER admin-service is
-- healthy (GAP Flyway done) and BEFORE phase 2 services start.
--
-- Mirrors projects/platform-console/apps/console-web/tests/e2e/fixtures/seed.sql
-- (platform-console e2e pattern) + extends it with 5 producer schema CREATE
-- DATABASE statements for the cross-product cohort.
--
-- Strategy (phase 1.5 — safe to apply before producer services start):
--   1. 5 producer databases — wms_db / scm_procurement_db / finance_db /
--      erp_db / admin_db already created by GAP init.sh (auth_db/account_db/
--      admin_db/security_db). finance_db + producer schemas added here.
--   2. auth_db.credentials — SUPER_ADMIN credential row (same as platform-
--      console seed.sql, same Argon2id hash of 'devpassword123!').
--   3. admin_db.admin_operators — SUPER_ADMIN row with tenant_id='*'.
--   4. admin_db.admin_operator_roles — bind to SUPER_ADMIN role.
--   5. admin_db.admin_roles — relax require_2fa for e2e environment.
--   6. seed-domains.sql at phase 2.5 (post-Flyway per-domain data).
--
-- Re-runnable: every statement is idempotent (INSERT IGNORE /
-- ON DUPLICATE KEY UPDATE / UPDATE-with-WHERE / CREATE IF NOT EXISTS).
-- =============================================================================

-- ---------------------------------------------------------------------------
-- 1. Producer databases + service users
--    Created here since GAP init.sh only knows auth_db/account_db/
--    security_db/admin_db/community_db/membership_db. finance_db + 3
--    producer schemas added for this cross-product overlay.
-- ---------------------------------------------------------------------------
CREATE DATABASE IF NOT EXISTS `finance_db`
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

CREATE USER IF NOT EXISTS 'finance'@'%' IDENTIFIED BY 'finance';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, DROP, INDEX, REFERENCES,
      TRIGGER, CREATE ROUTINE, ALTER ROUTINE, EXECUTE, CREATE TEMPORARY TABLES
  ON `finance_db`.* TO 'finance'@'%';

-- wms_db — wms-master-service uses PostgreSQL (application.yml default
-- jdbc:postgresql://localhost:5432/master_db). For the cross-product overlay,
-- master-service is wired with its own PostgreSQL sidecar — NOT MySQL.
-- This placeholder CREATE is skipped (wms is PostgreSQL, not MySQL).
-- See docker-compose.federation-e2e.yml for wms-postgres sidecar.

-- erp_db — erp-masterdata-service uses MySQL (application.yml confirms:
-- jdbc:mysql://${MYSQL_HOST:mysql}:${MYSQL_PORT:3306}/${MYSQL_DB:erp_db}).
CREATE DATABASE IF NOT EXISTS `erp_db`
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

CREATE USER IF NOT EXISTS 'erp'@'%' IDENTIFIED BY 'erp';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, DROP, INDEX, REFERENCES,
      TRIGGER, CREATE ROUTINE, ALTER ROUTINE, EXECUTE, CREATE TEMPORARY TABLES
  ON `erp_db`.* TO 'erp'@'%';

FLUSH PRIVILEGES;

-- ---------------------------------------------------------------------------
-- 2. auth_db — SUPER_ADMIN credential row.
--    Exact mirror of platform-console seed.sql section 2.
--    tenant_id='*' (platform-scope sentinel per TASK-BE-312).
-- ---------------------------------------------------------------------------
USE `auth_db`;

INSERT IGNORE INTO credentials (
    tenant_id,
    account_id,
    email,
    credential_hash,
    hash_algorithm,
    created_at,
    updated_at,
    version
) VALUES (
    '*',
    '01928c4a-7e9f-7c00-9a40-d2b1f5e8c100',
    'e2e-super-admin@example.com',
    '$argon2id$v=16$m=65536,t=3,p=1$7u/kw4KcLt7/i1nTEzEfsH7kRIraSsh1w9qOB7BhxUMTJdk3Oqp6zBklBlcMzJ4jS0PpgLYN+MW+1HlJF3m7ew$OJzCJkqvkul/EbS2FejjcDPx7Htj2HkAiCz74xcGBeY',
    'argon2id',
    NOW(6),
    NOW(6),
    0
);

-- ---------------------------------------------------------------------------
-- 3 + 4 + 5. admin_db — operators + role binding + 2FA relaxation.
--    Exact mirror of platform-console seed.sql sections 3/4/5.
-- ---------------------------------------------------------------------------
USE `admin_db`;

-- 5. Relax SUPER_ADMIN require_2fa for the e2e environment ONLY.
UPDATE admin_roles
   SET require_2fa = FALSE
 WHERE name = 'SUPER_ADMIN';

-- 3. Operator — SUPER_ADMIN caller. tenant_id='*'.
INSERT INTO admin_operators (
    operator_id,
    tenant_id,
    email,
    password_hash,
    display_name,
    status,
    oidc_subject,
    finance_default_account_id,
    created_at,
    updated_at,
    version
) VALUES (
    'e2e-super-admin',
    '*',
    'e2e-super-admin@example.com',
    '$argon2id$v=16$m=65536,t=3,p=1$7u/kw4KcLt7/i1nTEzEfsH7kRIraSsh1w9qOB7BhxUMTJdk3Oqp6zBklBlcMzJ4jS0PpgLYN+MW+1HlJF3m7ew$OJzCJkqvkul/EbS2FejjcDPx7Htj2HkAiCz74xcGBeY',
    'E2E Super Admin',
    'ACTIVE',
    'e2e-super-admin@example.com',
    '01928c4a-7e9f-7c00-9a40-d2b1f5e8a000',
    NOW(6),
    NOW(6),
    0
)
ON DUPLICATE KEY UPDATE
    tenant_id    = VALUES(tenant_id),
    oidc_subject = VALUES(oidc_subject),
    finance_default_account_id = VALUES(finance_default_account_id),
    status       = 'ACTIVE',
    updated_at   = NOW(6);

-- 4. Bind operator to SUPER_ADMIN role.
INSERT IGNORE INTO admin_operator_roles (operator_id, role_id, tenant_id, granted_at, granted_by)
SELECT o.id, r.id, o.tenant_id, NOW(6), NULL
  FROM admin_operators o
  JOIN admin_roles r ON r.name = 'SUPER_ADMIN'
 WHERE o.operator_id = 'e2e-super-admin';
