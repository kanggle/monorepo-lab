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

-- TASK-MONO-263 (ADR-032 D5 step 4): account_type column dropped (V0025) — the
-- operator's domain roles are derived at assume-tenant (BE-376), not from a stored type.
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
    -- TASK-MONO-298 (ADR-MONO-040 Phase 3 part A): oidc_subject = the matching
    -- seeded credentials.account_id (§ 2 above), NOT the login email. The dual-key
    -- resolver now hits account_id-first directly; the retained email fallback is
    -- no longer exercised for this operator. (login still uses the unchanged email.)
    '01928c4a-7e9f-7c00-9a40-d2b1f5e8c100',
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

-- ===========================================================================
-- TASK-MONO-154 — ADR-MONO-019 runtime activation capstone.
-- Real customer `acme-corp` operator (entitled_domains=[finance,wms]).
--
-- This block is ADD-ONLY — the SUPER_ADMIN rows above are NOT modified.
-- Purpose: prove the entitlement-trust gate at runtime. An acme-corp operator
-- logs into platform-console; keystone (BE-324) injects entitled_domains=
-- [finance,wms] into the OIDC token (reverse-looked-up by BE-325 from the
-- account_db `acme-corp` → [finance,wms] subscriptions, seeded by GAP Flyway
-- V0019/V0020 — NOT re-seeded here). The domain gates (step 3) then ACCEPT
-- finance/wms (entitled) and REJECT scm/erp (acme-corp is neither their slug
-- nor in their entitled set). The RBAC binding below is SUPER_ADMIN purely so
-- the operator can reach the console shell — the tenant ENTITLEMENT is what is
-- under test, NOT RBAC.
--
-- Re-runnable: INSERT IGNORE / ON DUPLICATE KEY UPDATE (same discipline as
-- the SUPER_ADMIN block).
--
-- NOTE on account_db: the `acme-corp` tenant + [finance,wms] subscriptions are
-- created by GAP Flyway V0019/V0020 (BE-322/BE-325) which run automatically in
-- the compose — they are deliberately NOT seeded here.
-- ===========================================================================

-- 6. auth_db — acme-corp operator credential row.
--    Same Argon2id hash as SUPER_ADMIN (same password 'devpassword123!').
--    tenant_id='acme-corp' (real customer slug, NOT the '*' platform sentinel).
USE `auth_db`;

-- TASK-MONO-263 (ADR-032 D5 step 4): account_type column dropped (V0025).
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
    'acme-corp',
    '01928c4a-7e9f-7c00-9a40-d2b1f5e8c200',
    'acme-operator@example.com',
    '$argon2id$v=16$m=65536,t=3,p=1$7u/kw4KcLt7/i1nTEzEfsH7kRIraSsh1w9qOB7BhxUMTJdk3Oqp6zBklBlcMzJ4jS0PpgLYN+MW+1HlJF3m7ew$OJzCJkqvkul/EbS2FejjcDPx7Htj2HkAiCz74xcGBeY',
    'argon2id',
    NOW(6),
    NOW(6),
    0
);

-- 7. admin_db — acme-corp operator row.
--    tenant_id='acme-corp'; finance_default_account_id points at the acme-corp
--    finance account seeded in seed-domains.sql (so the finance leg returns a
--    real 200 with balance data, NOT MISSING_PREREQUISITE).
USE `admin_db`;

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
    'acme-corp-operator',
    'acme-corp',
    'acme-operator@example.com',
    '$argon2id$v=16$m=65536,t=3,p=1$7u/kw4KcLt7/i1nTEzEfsH7kRIraSsh1w9qOB7BhxUMTJdk3Oqp6zBklBlcMzJ4jS0PpgLYN+MW+1HlJF3m7ew$OJzCJkqvkul/EbS2FejjcDPx7Htj2HkAiCz74xcGBeY',
    'Acme Operator',
    'ACTIVE',
    -- TASK-MONO-298 (ADR-MONO-040 Phase 3 part A): oidc_subject = matching seeded
    -- credentials.account_id (§ 6 above: acme-operator@example.com @ acme-corp).
    '01928c4a-7e9f-7c00-9a40-d2b1f5e8c200',
    '01928c4a-7e9f-7c00-9a40-d2b1f5e8a200',
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

-- 8. Bind acme-corp operator to SUPER_ADMIN role (console-shell reachability
--    only; the entitlement gate — NOT RBAC — is the discriminator under test).
INSERT IGNORE INTO admin_operator_roles (operator_id, role_id, tenant_id, granted_at, granted_by)
SELECT o.id, r.id, o.tenant_id, NOW(6), NULL
  FROM admin_operators o
  JOIN admin_roles r ON r.name = 'SUPER_ADMIN'
 WHERE o.operator_id = 'acme-corp-operator';

-- ===========================================================================
-- TASK-MONO-158 — ADR-MONO-020 § 3.3 step 3 (D4) active-tenant switcher →
-- assume-tenant flow. Multi-assignment demo operator + 2nd customer tenant.
--
-- This block is ADD-ONLY — the SUPER_ADMIN + acme-corp rows above are NOT
-- modified. Purpose: prove the A↔B switch re-scopes the SIGNED domain-facing
-- token (tenant_id + entitled_domains) so the federated domain entitlement
-- gates follow the selection. A multi-assignment operator is assigned to BOTH
-- acme-corp ([finance,wms]) AND globex-corp ([scm,erp], COMPLEMENTARY) via two
-- D1 `operator_tenant_assignment` rows (BE-326). On switcher selection the
-- console drives the D2 assume-tenant exchange (BE-327) → a token scoped to the
-- selected customer; switching A↔B flips finance/wms ↔ scm/erp entitlement.
--
-- The ConsoleRegistry effective-scope (BE-326 dual-read = assignment rows ∪
-- legacy home tenant) surfaces BOTH customers in this operator's switcher with
-- NO console change.
--
-- Re-runnable: INSERT IGNORE / ON DUPLICATE KEY UPDATE (same discipline).
-- ===========================================================================

-- 9. account_db — globex-corp customer tenant + [scm,erp] subscriptions.
--    MOVED to account-service Flyway db/migration-dev/V0021 (TASK-MONO-160):
--    account-service's per-tenant keystone query did NOT return globex rows
--    inserted HERE (post-startup, externally) — only Flyway-inserted rows
--    (acme-corp V0020) were returned. Seeding globex via Flyway-dev (loaded by
--    the e2e profile only) makes it present at account-service startup, exactly
--    like acme-corp, so the assume-tenant entitled_domains for globex resolves
--    to [scm,erp]. The multi-operator (auth_db/admin_db, below) stays here.

-- 10. auth_db — multi-assignment operator credential row.
--     Same Argon2id hash as the others (password 'devpassword123!').
--     Home tenant = 'acme-corp' (one of its assigned customers — the home
--     tenant is the login tenant_id; the D1 assignments grant the ADDITIONAL
--     scope. globex-corp is reachable purely via the assignment row + the
--     assume-tenant exchange, NOT the home tenant).
USE `auth_db`;

-- TASK-MONO-263 (ADR-032 D5 step 4): account_type column dropped (V0025).
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
    'acme-corp',
    '01928c4a-7e9f-7c00-9a40-d2b1f5e8c300',
    'multi-operator@example.com',
    '$argon2id$v=16$m=65536,t=3,p=1$7u/kw4KcLt7/i1nTEzEfsH7kRIraSsh1w9qOB7BhxUMTJdk3Oqp6zBklBlcMzJ4jS0PpgLYN+MW+1HlJF3m7ew$OJzCJkqvkul/EbS2FejjcDPx7Htj2HkAiCz74xcGBeY',
    'argon2id',
    NOW(6),
    NOW(6),
    0
);

-- 11. admin_db — multi-assignment operator row (home tenant = acme-corp).
USE `admin_db`;

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
    'multi-operator',
    'acme-corp',
    'multi-operator@example.com',
    '$argon2id$v=16$m=65536,t=3,p=1$7u/kw4KcLt7/i1nTEzEfsH7kRIraSsh1w9qOB7BhxUMTJdk3Oqp6zBklBlcMzJ4jS0PpgLYN+MW+1HlJF3m7ew$OJzCJkqvkul/EbS2FejjcDPx7Htj2HkAiCz74xcGBeY',
    'Multi Operator',
    'ACTIVE',
    -- TASK-MONO-298 (ADR-MONO-040 Phase 3 part A): oidc_subject = matching seeded
    -- credentials.account_id (§ 10 above: multi-operator@example.com @ acme-corp,
    -- the home/login tenant that owns the credential — one account regardless of
    -- the globex-corp/initech-corp ASSIGNMENTS).
    '01928c4a-7e9f-7c00-9a40-d2b1f5e8c300',
    '01928c4a-7e9f-7c00-9a40-d2b1f5e8a200',
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

-- 12. Two D1 operator_tenant_assignment rows (BE-326): the operator is assigned
--     to BOTH acme-corp AND globex-corp. permission_set_id NULL = inherit the
--     operator-level role grants. With these rows present, the BE-326 dual-read
--     effective scope = {acme-corp, globex-corp} ∪ {home=acme-corp} =
--     {acme-corp, globex-corp} — both surface in the switcher.
INSERT IGNORE INTO operator_tenant_assignment (operator_id, tenant_id, granted_at, granted_by, permission_set_id)
SELECT o.id, 'acme-corp', NOW(6), NULL, NULL
  FROM admin_operators o
 WHERE o.operator_id = 'multi-operator';

INSERT IGNORE INTO operator_tenant_assignment (operator_id, tenant_id, granted_at, granted_by, permission_set_id)
SELECT o.id, 'globex-corp', NOW(6), NULL, NULL
  FROM admin_operators o
 WHERE o.operator_id = 'multi-operator';

-- 13. Bind multi-operator to SUPER_ADMIN role (console-shell reachability only;
--     the tenant ENTITLEMENT re-scope — NOT RBAC — is the discriminator).
INSERT IGNORE INTO admin_operator_roles (operator_id, role_id, tenant_id, granted_at, granted_by)
SELECT o.id, r.id, o.tenant_id, NOW(6), NULL
  FROM admin_operators o
  JOIN admin_roles r ON r.name = 'SUPER_ADMIN'
 WHERE o.operator_id = 'multi-operator';

-- ===========================================================================
-- TASK-MONO-207 — ADR-MONO-023 D2 cross-service plane-separation proof.
--
-- This block is ADD-ONLY. It grants the existing multi-operator a THIRD
-- assignment — to `initech-corp`, the dedicated tenant the
-- subscription-plane-separation spec mutates at runtime. The matching
-- account_db side (initech-corp tenant + [finance,wms] subscriptions) is seeded
-- by account-service Flyway-dev V9002 (present at startup, like globex V9001).
--
-- WHY this is the IAM-plane half of the D2 proof: this operator_tenant_assignment
-- row is exactly what the assume-tenant exchange's D2 assignment gate checks. The
-- spec suspends initech's `finance` ENTITLEMENT (account_db, entitlement plane)
-- and proves the re-issued operator token drops `finance` from entitled_domains
-- WHILE this assignment row stays byte-unchanged — the operator can still assume
-- initech-corp (switch → 200), and `wms` stays entitled. Entitlement vanished;
-- the IAM binding survived (GCP billing↔IAM parity).
--
-- Re-runnable: INSERT IGNORE (same discipline as § 12).
-- ===========================================================================
INSERT IGNORE INTO operator_tenant_assignment (operator_id, tenant_id, granted_at, granted_by, permission_set_id)
SELECT o.id, 'initech-corp', NOW(6), NULL, NULL
  FROM admin_operators o
 WHERE o.operator_id = 'multi-operator';

-- ===========================================================================
-- TASK-MONO-210 — ADR-MONO-024 § 3.3 step 3 tenant-admin delegation proof.
--
-- This block is ADD-ONLY. It seeds, scoped to the DEDICATED `umbrella-corp`
-- tenant (account_db side = account-service Flyway-dev V9003), the two delegated
-- administrators the proof logs in as, plus one throwaway target operator they
-- manage:
--
--   (a) `tenant-admin-umbrella`         — role TENANT_ADMIN @ umbrella-corp
--       (holds {operator.manage, tenant.admin.delegate} confined to umbrella-corp).
--   (b) `tenant-billing-admin-umbrella` — role TENANT_BILLING_ADMIN @ umbrella-corp
--       (holds {subscription.manage} confined to umbrella-corp).
--   (c) `deleg-target-umbrella`         — role SUPPORT_READONLY @ umbrella-corp,
--       a dedicated operator the TENANT_ADMIN assigns / scopes / re-roles. It
--       NEVER logs in (no auth_db credential), so it has no console session and
--       is referenced by no other spec → mutating it is parallel-safe.
--
-- WHY the grant-row tenant_id is the crux: ADR-024 D2 confinement
-- (AdminGrantScopeEvaluator) reads each operator's effective admin-grant scope
-- as the set of `admin_operator_roles.tenant_id`s of the rows granting the
-- permission. Binding (a)/(b) at tenant_id='umbrella-corp' (NOT '*') makes their
-- scope = {umbrella-corp} — so they administer umbrella-corp (200) and are denied
-- globex-corp (403 TENANT_SCOPE_DENIED). SUPER_ADMIN ('*') stays unconstrained
-- (net-zero). This is exactly the runtime shape step 1/2a/2b built in-process.
--
-- WHY no auth_db credential for (c): the target is only ever the OBJECT of the
-- delegated admins' mutations (path {operatorId}); it presents no token, so it
-- needs only the admin_db operator + role rows.
--
-- Re-runnable: INSERT IGNORE / ON DUPLICATE KEY UPDATE (same discipline as
-- the SUPER_ADMIN / acme / multi-operator blocks above).
-- ===========================================================================

-- 15a. auth_db — credentials for the two delegated administrators (they log in
--      via the SAME production-identical OIDC PKCE flow). Same Argon2id hash
--      ('devpassword123!'). tenant_id='umbrella-corp'. (TASK-MONO-263: account_type dropped.)
USE `auth_db`;

INSERT IGNORE INTO credentials (
    tenant_id, account_id, email,
    credential_hash, hash_algorithm, created_at, updated_at, version
) VALUES
    ('umbrella-corp', '01928c4a-7e9f-7c00-9a40-d2b1f5e8c401',
     'tenant-admin-umbrella@example.com',
     '$argon2id$v=16$m=65536,t=3,p=1$7u/kw4KcLt7/i1nTEzEfsH7kRIraSsh1w9qOB7BhxUMTJdk3Oqp6zBklBlcMzJ4jS0PpgLYN+MW+1HlJF3m7ew$OJzCJkqvkul/EbS2FejjcDPx7Htj2HkAiCz74xcGBeY',
     'argon2id', NOW(6), NOW(6), 0),
    ('umbrella-corp', '01928c4a-7e9f-7c00-9a40-d2b1f5e8c402',
     'tenant-billing-admin-umbrella@example.com',
     '$argon2id$v=16$m=65536,t=3,p=1$7u/kw4KcLt7/i1nTEzEfsH7kRIraSsh1w9qOB7BhxUMTJdk3Oqp6zBklBlcMzJ4jS0PpgLYN+MW+1HlJF3m7ew$OJzCJkqvkul/EbS2FejjcDPx7Htj2HkAiCz74xcGBeY',
     'argon2id', NOW(6), NOW(6), 0);

-- 15b. admin_db — the three operators + their tenant-scoped role bindings.
USE `admin_db`;

INSERT INTO admin_operators (
    operator_id, tenant_id, email, password_hash, display_name, status,
    oidc_subject, finance_default_account_id, created_at, updated_at, version
) VALUES
    -- TASK-MONO-298 (ADR-MONO-040 Phase 3 part A): the two operators that LOG IN
    -- (have an auth_db credential, § 15a) get oidc_subject = their seeded
    -- credentials.account_id. `deleg-target-umbrella` has NO credential (object
    -- only — never logs in), so its oidc_subject stays email-shaped: the backfill
    -- endpoint leaves it unchanged (fail-soft) and it never resolves a token anyway.
    ('tenant-admin-umbrella', 'umbrella-corp', 'tenant-admin-umbrella@example.com',
     '$argon2id$v=16$m=65536,t=3,p=1$7u/kw4KcLt7/i1nTEzEfsH7kRIraSsh1w9qOB7BhxUMTJdk3Oqp6zBklBlcMzJ4jS0PpgLYN+MW+1HlJF3m7ew$OJzCJkqvkul/EbS2FejjcDPx7Htj2HkAiCz74xcGBeY',
     'Umbrella Tenant Admin', 'ACTIVE', '01928c4a-7e9f-7c00-9a40-d2b1f5e8c401',
     '01928c4a-7e9f-7c00-9a40-d2b1f5e8a401', NOW(6), NOW(6), 0),
    ('tenant-billing-admin-umbrella', 'umbrella-corp', 'tenant-billing-admin-umbrella@example.com',
     '$argon2id$v=16$m=65536,t=3,p=1$7u/kw4KcLt7/i1nTEzEfsH7kRIraSsh1w9qOB7BhxUMTJdk3Oqp6zBklBlcMzJ4jS0PpgLYN+MW+1HlJF3m7ew$OJzCJkqvkul/EbS2FejjcDPx7Htj2HkAiCz74xcGBeY',
     'Umbrella Billing Admin', 'ACTIVE', '01928c4a-7e9f-7c00-9a40-d2b1f5e8c402',
     '01928c4a-7e9f-7c00-9a40-d2b1f5e8a402', NOW(6), NOW(6), 0),
    ('deleg-target-umbrella', 'umbrella-corp', 'deleg-target-umbrella@example.com',
     '$argon2id$v=16$m=65536,t=3,p=1$7u/kw4KcLt7/i1nTEzEfsH7kRIraSsh1w9qOB7BhxUMTJdk3Oqp6zBklBlcMzJ4jS0PpgLYN+MW+1HlJF3m7ew$OJzCJkqvkul/EbS2FejjcDPx7Htj2HkAiCz74xcGBeY',
     'Umbrella Delegation Target', 'ACTIVE', 'deleg-target-umbrella@example.com',
     '01928c4a-7e9f-7c00-9a40-d2b1f5e8a403', NOW(6), NOW(6), 0)
ON DUPLICATE KEY UPDATE
    tenant_id    = VALUES(tenant_id),
    oidc_subject = VALUES(oidc_subject),
    status       = 'ACTIVE',
    updated_at   = NOW(6);

-- TENANT_ADMIN @ umbrella-corp (grant-row tenant_id = the confinement scope).
INSERT IGNORE INTO admin_operator_roles (operator_id, role_id, tenant_id, granted_at, granted_by)
SELECT o.id, r.id, 'umbrella-corp', NOW(6), NULL
  FROM admin_operators o
  JOIN admin_roles r ON r.name = 'TENANT_ADMIN'
 WHERE o.operator_id = 'tenant-admin-umbrella';

-- TENANT_BILLING_ADMIN @ umbrella-corp.
INSERT IGNORE INTO admin_operator_roles (operator_id, role_id, tenant_id, granted_at, granted_by)
SELECT o.id, r.id, 'umbrella-corp', NOW(6), NULL
  FROM admin_operators o
  JOIN admin_roles r ON r.name = 'TENANT_BILLING_ADMIN'
 WHERE o.operator_id = 'tenant-billing-admin-umbrella';

-- SUPPORT_READONLY @ umbrella-corp for the throwaway target (benign baseline so
-- the grant-menu ALLOW is a visible role *change*; SUPPORT_READONLY's read perms
-- are NOT a subset of TENANT_ADMIN's perms, which is irrelevant here — the target
-- is the object, not the actor).
INSERT IGNORE INTO admin_operator_roles (operator_id, role_id, tenant_id, granted_at, granted_by)
SELECT o.id, r.id, 'umbrella-corp', NOW(6), NULL
  FROM admin_operators o
  JOIN admin_roles r ON r.name = 'SUPPORT_READONLY'
 WHERE o.operator_id = 'deleg-target-umbrella';

-- ===========================================================================
-- TASK-MONO-221 — ADR-MONO-026 § D7 step 3 iam admin SOURCE_IP access-condition
-- proof.
--
-- This block is ADD-ONLY. It seeds ONE throwaway target operator
-- `ip-pilot-target`, scoped to the DEDICATED `ip-pilot-corp` tenant (account_db
-- side = account-service Flyway-dev V9004), which the proof assigns / unassigns
-- to exercise the SOURCE_IP access condition (the 4th authorization gate on the
-- admin mutation surface, ADR-026 D4 / TASK-BE-351).
--
-- WHY only a target (no new actor): the proof's actor is the persisted
-- SUPER_ADMIN (global-setup storageState) — the SOURCE_IP gate is service-level
-- and orthogonal to RBAC, so it gates even SUPER_ADMIN ('*'). The proof reads
-- SUPER_ADMIN's `console_operator_token` and drives the admin surface directly,
-- setting the perceived source IP per-request via X-Forwarded-For. No new login
-- helper / auth_db credential is needed.
--
-- WHY no auth_db credential for `ip-pilot-target`: identical to
-- `deleg-target-umbrella` (§ 15) — the target is only ever the OBJECT of the
-- assign/unassign mutation (path {operatorId}); it presents no token, so it needs
-- only the admin_db operator + role rows.
--
-- Re-runnable: INSERT IGNORE / ON DUPLICATE KEY UPDATE (same discipline as the
-- blocks above).
-- ===========================================================================

USE `admin_db`;

INSERT INTO admin_operators (
    operator_id, tenant_id, email, password_hash, display_name, status,
    oidc_subject, finance_default_account_id, created_at, updated_at, version
) VALUES
    ('ip-pilot-target', 'ip-pilot-corp', 'ip-pilot-target@example.com',
     '$argon2id$v=16$m=65536,t=3,p=1$7u/kw4KcLt7/i1nTEzEfsH7kRIraSsh1w9qOB7BhxUMTJdk3Oqp6zBklBlcMzJ4jS0PpgLYN+MW+1HlJF3m7ew$OJzCJkqvkul/EbS2FejjcDPx7Htj2HkAiCz74xcGBeY',
     'IP Pilot Target', 'ACTIVE', 'ip-pilot-target@example.com',
     '01928c4a-7e9f-7c00-9a40-d2b1f5e8a501', NOW(6), NOW(6), 0)
ON DUPLICATE KEY UPDATE
    tenant_id    = VALUES(tenant_id),
    oidc_subject = VALUES(oidc_subject),
    status       = 'ACTIVE',
    updated_at   = NOW(6);

-- SUPPORT_READONLY @ ip-pilot-corp — a benign baseline role so the operator
-- exists (the assign/unassign target is the OBJECT, not the actor; its own role
-- is irrelevant to the SOURCE_IP gate, which applies to the SUPER_ADMIN caller).
INSERT IGNORE INTO admin_operator_roles (operator_id, role_id, tenant_id, granted_at, granted_by)
SELECT o.id, r.id, 'ip-pilot-corp', NOW(6), NULL
  FROM admin_operators o
  JOIN admin_roles r ON r.name = 'SUPPORT_READONLY'
 WHERE o.operator_id = 'ip-pilot-target';

-- ===========================================================================
-- TASK-MONO-228 — ADR-MONO-029 § D6 step 4 RESOURCE_TAG access-condition
-- federation-e2e proof (deterministic — unlike the global-clock TIME_WINDOW).
--
-- Two throwaway target operators scoped to the DEDICATED `ip-pilot-corp` tenant
-- (account_db V9004), the OBJECTS of a SUPER_ADMIN role mutation:
--
--   (a) `rt-protected-target`  — tags='protected' → a role/status/profile mutation
--       on it is DENIED (403 ACCESS_CONDITION_UNMET) once the admin-service is
--       configured with ADMIN_ACCESS_RESOURCE_TAG_FORBIDDEN=protected (compose).
--   (b) `rt-untagged-target`   — tags=NULL → the SAME mutation is allowed (2xx).
--
-- The discriminant is the TARGET resource's tag — per-resource, so the gate is
-- net-zero for every other operator (no other seeded operator is tagged
-- `protected`), unlike a global TIME_WINDOW that would gate the whole suite. The
-- caller is the persisted SUPER_ADMIN; the gate is orthogonal to RBAC so it bites
-- even '*'. Neither target logs in (no auth_db credential — objects only).
--
-- The `tags` column is V0034 (admin-service Flyway); this seed runs after Flyway
-- (Phase 1.5), so it may set it. ON DUPLICATE re-applies tags for re-runnability.
-- ===========================================================================

INSERT INTO admin_operators (
    operator_id, tenant_id, email, password_hash, display_name, status,
    oidc_subject, tags, created_at, updated_at, version
) VALUES
    ('rt-protected-target', 'ip-pilot-corp', 'rt-protected-target@example.com',
     '$argon2id$v=16$m=65536,t=3,p=1$7u/kw4KcLt7/i1nTEzEfsH7kRIraSsh1w9qOB7BhxUMTJdk3Oqp6zBklBlcMzJ4jS0PpgLYN+MW+1HlJF3m7ew$OJzCJkqvkul/EbS2FejjcDPx7Htj2HkAiCz74xcGBeY',
     'RT Protected Target', 'ACTIVE', 'rt-protected-target@example.com',
     'protected', NOW(6), NOW(6), 0),
    ('rt-untagged-target', 'ip-pilot-corp', 'rt-untagged-target@example.com',
     '$argon2id$v=16$m=65536,t=3,p=1$7u/kw4KcLt7/i1nTEzEfsH7kRIraSsh1w9qOB7BhxUMTJdk3Oqp6zBklBlcMzJ4jS0PpgLYN+MW+1HlJF3m7ew$OJzCJkqvkul/EbS2FejjcDPx7Htj2HkAiCz74xcGBeY',
     'RT Untagged Target', 'ACTIVE', 'rt-untagged-target@example.com',
     NULL, NOW(6), NOW(6), 0)
ON DUPLICATE KEY UPDATE
    tags       = VALUES(tags),
    status     = 'ACTIVE',
    updated_at = NOW(6);

-- SUPPORT_READONLY @ ip-pilot-corp for both (benign baseline; the role the
-- SUPER_ADMIN re-sets — idempotent — to exercise the role-mutation gate).
INSERT IGNORE INTO admin_operator_roles (operator_id, role_id, tenant_id, granted_at, granted_by)
SELECT o.id, r.id, 'ip-pilot-corp', NOW(6), NULL
  FROM admin_operators o
  JOIN admin_roles r ON r.name = 'SUPPORT_READONLY'
 WHERE o.operator_id IN ('rt-protected-target', 'rt-untagged-target');

-- ===========================================================================
-- TASK-MONO-268 — ADR-MONO-036 § P4 (M3): born-unified demo seed rewrite.
--
-- ADR-036 M1/M2 made every NEW consumer registration born-unified — the central
-- `identities` row is minted at account creation (M1) and propagated to
-- `accounts.identity_id` + `credentials.identity_id` at birth (M1/M2). This block
-- makes the SEEDED demo principals look exactly as if they had been provisioned
-- through that unified path: each customer-tenant operator-person gets a real
-- `identities` registry row (account_db) and the SAME `identity_id` written onto
-- every store it occupies (accounts where it is also a consumer, credentials,
-- admin_operators). Before this block the demo was born-SPLIT — the live finding
-- that motivated ADR-036: `identities` was EMPTY, `credentials.identity_id` was
-- all NULL, and `accounts.identity_id` had no production/seed writer.
--
-- This is the SEED analog of the forward code; it is NOT the production backfill
-- (ADR-036 P4 = DESIGN-not-built: real users cannot be wiped). The opt-in audited
-- link surface (PATCH …/identity:link) stays the tool for reconciling pre-existing
-- split data — here we simply seed clean (rewrite), which the demo CAN do.
--
-- WHY same-origin, not email auto-merge (ADR-034 § 1.3): every identity below is
-- keyed by the operator's OWN (tenant_id, primary_email) — the exact key the M1
-- mint resolves. No two distinct persons are merged; an operator and its own
-- consumer account converge because they ARE the same (tenant, email) origin.
--
-- WHY `e2e-super-admin` is deliberately NOT born-unified (born unlinked, net-zero):
-- it is tenant_id='*' — the platform-scope SENTINEL, which is NOT a row in the
-- account_db `tenants` table, so an `identities` row (FK → tenants) cannot exist
-- for it. A platform super-admin is not a customer-tenant person and has no
-- central per-tenant identity. This mirrors the M1/M2 fail-soft "born unlinked"
-- outcome (identity_id stays NULL) — a legitimate, documented net-zero state, not
-- a gap. The same applies to any '*'-scoped operator.
--
-- Re-runnable: identities/accounts use INSERT IGNORE; the identity_id writers use
-- `UPDATE … WHERE identity_id IS NULL` — idempotent and never overwriting, exactly
-- mirroring the M1/M2 native `assignIdentityIdIfAbsent` writers (a second run with
-- a different id is a 0-row no-op).
-- ===========================================================================

-- ── M3.1 account_db — central identities registry + the co-holder's consumer account.
--    One identity per customer-tenant operator-person (fixed UUIDs in the 'd' band
--    paired with each principal's existing 'c'-band account_id). FK
--    fk_identities_tenant_id → tenants: acme-corp (V0020) and umbrella-corp (V9003)
--    are present at seed time (GAP/account-service Flyway done before phase 1.5).
USE `account_db`;

INSERT IGNORE INTO identities
    (identity_id, tenant_id, primary_email, status, created_at, updated_at, version)
VALUES
    ('01928c4a-7e9f-7c00-9a40-d2b1f5e8d200', 'acme-corp',     'acme-operator@example.com',                 'ACTIVE', NOW(6), NOW(6), 0),
    ('01928c4a-7e9f-7c00-9a40-d2b1f5e8d300', 'acme-corp',     'multi-operator@example.com',                'ACTIVE', NOW(6), NOW(6), 0),
    ('01928c4a-7e9f-7c00-9a40-d2b1f5e8d401', 'umbrella-corp', 'tenant-admin-umbrella@example.com',         'ACTIVE', NOW(6), NOW(6), 0),
    ('01928c4a-7e9f-7c00-9a40-d2b1f5e8d402', 'umbrella-corp', 'tenant-billing-admin-umbrella@example.com', 'ACTIVE', NOW(6), NOW(6), 0);

-- The headline co-holder: `multi-operator` is BOTH a consumer (account_db.accounts)
-- AND an operator (admin_db.admin_operators) under the same central identity — the
-- ADR-032 "one person, consumer + operator" scenario, born-unified across all THREE
-- stores. The account id == the operator's credential account_id ('…c300'), so the
-- credential below links to a real account. FK fk_accounts_identity_id → the '…d300'
-- identity inserted above (order: identity first, then account).
INSERT IGNORE INTO accounts
    (id, tenant_id, identity_id, email, status, created_at, updated_at, version)
VALUES
    ('01928c4a-7e9f-7c00-9a40-d2b1f5e8c300', 'acme-corp',
     '01928c4a-7e9f-7c00-9a40-d2b1f5e8d300', 'multi-operator@example.com',
     'ACTIVE', NOW(6), NOW(6), 0);

-- ── M3.2 auth_db — credentials.identity_id born-unified (the M2 writer's seed analog).
--    `IS NULL` guard = idempotent, no overwrite (mirrors assignIdentityIdIfAbsent).
USE `auth_db`;

UPDATE credentials SET identity_id = '01928c4a-7e9f-7c00-9a40-d2b1f5e8d200'
 WHERE account_id = '01928c4a-7e9f-7c00-9a40-d2b1f5e8c200' AND identity_id IS NULL;
UPDATE credentials SET identity_id = '01928c4a-7e9f-7c00-9a40-d2b1f5e8d300'
 WHERE account_id = '01928c4a-7e9f-7c00-9a40-d2b1f5e8c300' AND identity_id IS NULL;
UPDATE credentials SET identity_id = '01928c4a-7e9f-7c00-9a40-d2b1f5e8d401'
 WHERE account_id = '01928c4a-7e9f-7c00-9a40-d2b1f5e8c401' AND identity_id IS NULL;
UPDATE credentials SET identity_id = '01928c4a-7e9f-7c00-9a40-d2b1f5e8d402'
 WHERE account_id = '01928c4a-7e9f-7c00-9a40-d2b1f5e8c402' AND identity_id IS NULL;

-- ── M3.3 admin_db — admin_operators.identity_id born-unified (the operator extension's
--    link, the seed analog of the U3 audited link surface). `IS NULL` guard = idempotent.
USE `admin_db`;

UPDATE admin_operators SET identity_id = '01928c4a-7e9f-7c00-9a40-d2b1f5e8d200'
 WHERE operator_id = 'acme-corp-operator' AND identity_id IS NULL;
UPDATE admin_operators SET identity_id = '01928c4a-7e9f-7c00-9a40-d2b1f5e8d300'
 WHERE operator_id = 'multi-operator' AND identity_id IS NULL;
UPDATE admin_operators SET identity_id = '01928c4a-7e9f-7c00-9a40-d2b1f5e8d401'
 WHERE operator_id = 'tenant-admin-umbrella' AND identity_id IS NULL;
UPDATE admin_operators SET identity_id = '01928c4a-7e9f-7c00-9a40-d2b1f5e8d402'
 WHERE operator_id = 'tenant-billing-admin-umbrella' AND identity_id IS NULL;
