-- =============================================================================
-- TASK-PC-FE-019 (initial) / TASK-PC-FE-022 (fixture migration to OIDC PKCE)
-- platform-console e2e harness seed
-- =============================================================================
-- Runtime data fixture (NOT a Flyway migration). Executed against the docker-
-- compose.e2e.yml MySQL container AFTER all services have run their Flyway
-- migrations and BEFORE Playwright launches.
--
-- AC-3 / AC-5 byte-diff invariant: this file lives in the platform-console
-- project and does runtime INSERTs / UPDATEs against the GAP + finance-platform
-- schemas. Neither `projects/global-account-platform/` nor
-- `projects/finance-platform/` is touched on disk.
--
-- Strategy:
--   1. finance_db database / user — created here since GAP's init.sh only
--      knows about the 4 GAP schemas. finance-account-service expects
--      finance_db on first boot (Hibernate validates against Flyway-applied
--      V1__init.sql); this must complete before the finance container starts.
--   2. auth_db.credentials — INSERT the SUPER_ADMIN credential row keyed by
--      `e2e-super-admin@example.com` with a fixed Argon2id hash of the
--      plaintext `devpassword123!` (reused from GAP V0014 dev seed and the
--      admin_operators password_hash below). This row is the AuthN source the
--      auth-service `/login` form (TASK-BE-309 `CredentialAuthenticationProvider`)
--      verifies during the e2e OIDC PKCE flow. The production V0015 PUBLIC
--      OAuth client definition is untouched (no `client_credentials` grant
--      extension; TASK-PC-FE-022 migrated the fixture to true browser-driven
--      form-fill — see fixtures/login.ts).
--   3. admin_db.admin_operators — 2 rows:
--        (a) `e2e-super-admin`  — SUPER_ADMIN, tenant_id='*', no
--            finance_default_account_id (so the PC-FE-016 self-set spec can
--            assert the post-Save flip from MISSING_PREREQUISITE → ok).
--            `oidc_subject='e2e-super-admin@example.com'` so the admin
--            token-exchange resolves the user-based authorization_code JWT
--            (sub=email) to THIS row.
--        (b) `e2e-target-operator` — vanilla operator, tenant_id='fan-platform',
--            no finance_default_account_id (so the PC-FE-017 + PC-FE-018
--            admin-on-behalf-of spec can set + later verify the value).
--   4. admin_db.admin_operator_roles — bind e2e-super-admin to the
--      SUPER_ADMIN role row (V0006 seeded), copying tenant_id='*'.
--   5. admin_db.admin_roles — relax `require_2fa` on SUPER_ADMIN to FALSE so
--      the token-exchange path (which does NOT enforce 2FA — security.md
--      §GAP OIDC subject-token flow) can mint operator tokens without TOTP
--      enrollment. This is a per-environment runtime tweak; the production
--      profile keeps require_2fa=TRUE (V0013 default).
--   6. (moved to seed-finance.sql per TASK-MONO-132) — the finance schema
--      row for tenant `fan-platform` (PC-FE-016 click sequence target) is
--      now applied at workflow phase 2.5 after finance-account-service
--      Flyway runs. See sibling fixture seed-finance.sql.
--
-- Re-runnable: every statement is idempotent (INSERT IGNORE / ON DUPLICATE
-- KEY UPDATE / UPDATE-with-WHERE). The CI workflow runs seed.sql exactly
-- once per spin-up; idempotency is defensive against re-attempts.
-- =============================================================================

-- ---------------------------------------------------------------------------
-- 1. finance_db — create the database + service user. finance-platform's
--    docker-compose.yml normally relies on MySQL's MYSQL_DATABASE / MYSQL_USER
--    env, but this overlay's MySQL container is shared with GAP; the GAP
--    init.sh owns the 4 GAP schemas and we add finance_db here.
-- ---------------------------------------------------------------------------
CREATE DATABASE IF NOT EXISTS `finance_db`
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

CREATE USER IF NOT EXISTS 'finance'@'%' IDENTIFIED BY 'finance';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, DROP, INDEX, REFERENCES,
      TRIGGER, CREATE ROUTINE, ALTER ROUTINE, EXECUTE, CREATE TEMPORARY TABLES
  ON `finance_db`.* TO 'finance'@'%';
FLUSH PRIVILEGES;

-- ---------------------------------------------------------------------------
-- 2. auth_db — insert the SUPER_ADMIN credential row that the auth-service
--    `/login` form (BE-309) authenticates against. The Argon2id hash encodes
--    the plaintext `devpassword123!` — same value GAP V0014 dev seed uses
--    (so the hash is BcryptHashPinTest-equivalent stable; no drift).
--
--    TASK-BE-312: tenant_id='*' (platform-scope sentinel — matches the
--    admin_operators row below). `CredentialAuthenticationProvider` (BE-309)
--    propagates this onto the JWT `tenant_id` claim during the
--    authorization_code grant. finance-account-service `TenantClaimValidator`
--    accepts only `tenant_id='*'` (wildcard) OR `tenant_id='finance'` on
--    inbound JWTs (multi-tenancy.md §Tenant Model + finance-platform
--    architecture.md §Service-level OAuth2 tenant gate); seeding 'gap' here
--    surfaced as a 403 TENANT_FORBIDDEN on the Operator Overview finance leg.
--    The wildcard preserves the OIDC client/registration scope ('gap')
--    independently — those are V0015 PUBLIC client artifacts and unrelated to
--    the runtime tenant claim. account_id is a fresh UUID — auth-service does
--    not back-reference the account-service row for this e2e environment
--    because LoginUseCase's account-status call is bypassed by the BE-309
--    form-login path (architectural divergence documented in BE-309 spec).
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
-- ---------------------------------------------------------------------------
USE `admin_db`;

-- 5. Relax SUPER_ADMIN require_2fa for the e2e environment ONLY. Per
--    `security.md §GAP OIDC subject-token flow` the token-exchange path
--    bypasses 2FA — but the role's require_2fa flag also gates other login
--    paths. Relaxing it here for the test container keeps both paths green.
UPDATE admin_roles
   SET require_2fa = FALSE
 WHERE name = 'SUPER_ADMIN';

-- 3. Operator (a) — SUPER_ADMIN caller. tenant_id='*' (ADR-002 platform-scope
--    sentinel). password_hash is a fixed Argon2id hash of 'devpassword123!'
--    (same value V0014 dev seed uses) so any password-based path still works
--    if a test ever needs it. oidc_subject='e2e-super-admin@example.com'
--    matches the JWT `sub` claim issued by the authorization_code grant
--    (CredentialAuthenticationProvider sets principal=email) — so
--    TokenExchangeService.findByOidcSubject resolves THIS row.
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
    NULL,
    NOW(6),
    NOW(6),
    0
)
ON DUPLICATE KEY UPDATE
    tenant_id    = VALUES(tenant_id),
    oidc_subject = VALUES(oidc_subject),
    status       = 'ACTIVE',
    updated_at   = NOW(6);

-- Bind operator (a) to SUPER_ADMIN role.
INSERT IGNORE INTO admin_operator_roles (operator_id, role_id, tenant_id, granted_at, granted_by)
SELECT o.id, r.id, o.tenant_id, NOW(6), NULL
  FROM admin_operators o
  JOIN admin_roles r ON r.name = 'SUPER_ADMIN'
 WHERE o.operator_id = 'e2e-super-admin';

-- 3. Operator (b) — non-self target. tenant_id='fan-platform'. oidc_subject is
--    a DIFFERENT value so the SUPER_ADMIN's token-exchange does not resolve
--    to this row (only one admin_operators row may match a given
--    oidc_subject per `data-model.md §OIDC Subject <-> Operator Link`).
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
    'e2e-target-operator',
    'fan-platform',
    'e2e-target@example.com',
    '$argon2id$v=16$m=65536,t=3,p=1$7u/kw4KcLt7/i1nTEzEfsH7kRIraSsh1w9qOB7BhxUMTJdk3Oqp6zBklBlcMzJ4jS0PpgLYN+MW+1HlJF3m7ew$OJzCJkqvkul/EbS2FejjcDPx7Htj2HkAiCz74xcGBeY',
    'E2E Target Operator',
    'ACTIVE',
    'e2e-target-oidc-sub',
    NULL,
    NOW(6),
    NOW(6),
    0
)
ON DUPLICATE KEY UPDATE
    tenant_id    = VALUES(tenant_id),
    oidc_subject = VALUES(oidc_subject),
    status       = 'ACTIVE',
    updated_at   = NOW(6);

-- ---------------------------------------------------------------------------
-- (section 6 moved to seed-finance.sql by TASK-MONO-132)
--
-- Earlier versions of this fixture also INSERTed into the finance schema
-- tables here. Those statements require the finance Flyway V1__init.sql
-- to have run, which only happens at finance-account-service boot
-- (workflow phase 2). Applying them at phase 1.5 produced
-- `Table doesn't exist` errors (run 26319887335 step `Apply seed.sql`).
-- The data now lives in seed-finance.sql, applied by the workflow's new
-- phase 2.5 step `Apply seed-finance.sql` after finance-account-service is
-- healthy. Section 1 above still creates the finance_db database +
-- service user at phase 1.5 — that part of the contract is unchanged.
-- ---------------------------------------------------------------------------
