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
--   6. finance_db.accounts + balances — 1 finance account row for tenant
--      `fan-platform` (matching the PC-FE-016 click sequence: SUPER_ADMIN
--      operating in fan-platform tenant). The PC-FE-016 spec's Save step
--      uses the same UUID as `admin_operators.finance_default_account_id`.
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
--    (so the hash is BcryptHashPinTest-equivalent stable; no drift). The
--    tenant_id='gap' matches the V0015 platform-console-web OAuth client's
--    tenant scope. account_id is a fresh UUID — auth-service does not back-
--    reference the account-service row for this e2e environment because
--    LoginUseCase's account-status call is bypassed by the BE-309 form-login
--    path (architectural divergence documented in BE-309 spec).
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
    'gap',
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
-- 6. finance_db — single account + balance row. Tenant 'fan-platform' so
--    the SUPER_ADMIN operating in fan-platform tenant (TENANT_COOKIE) can
--    read it via the finance ActorContext tenant gate. UUID matches the
--    value the 2 specs Save into MyProfileForm / OperatorProfileEditDialog.
--
--    Note: account-service Flyway V1__init.sql runs at finance container
--    boot, which depends on finance_db existing — section 1 above creates
--    it BEFORE finance-account-service is started (see workflow ordering).
-- ---------------------------------------------------------------------------
USE `finance_db`;

INSERT IGNORE INTO accounts (
    id, tenant_id, owner_ref, status, kyc_level, currency,
    created_at, updated_at, version
) VALUES (
    '01928c4a-7e9f-7c00-9a40-d2b1f5e8a000',
    'fan-platform',
    'e2e-test-owner-ref',
    'ACTIVE',
    'FULL',
    'KRW',
    NOW(6), NOW(6), 0
);

INSERT IGNORE INTO balances (
    id, account_id, tenant_id, currency,
    ledger_minor, held_minor,
    created_at, updated_at, version
) VALUES (
    '01928c4a-7e9f-7c00-9a40-d2b1f5e8b001',
    '01928c4a-7e9f-7c00-9a40-d2b1f5e8a000',
    'fan-platform',
    'KRW',
    1000000, 0,
    NOW(6), NOW(6), 0
);
