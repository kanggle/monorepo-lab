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
-- schemas. Neither `projects/iam-platform/` nor
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
--            `oidc_subject` = the seeded credentials.account_id (TASK-MONO-298,
--            ADR-040 Phase 3 part A) so the dual-key token-exchange resolves the
--            SAS JWT (Phase-2 `sub`=account_id) to THIS row account_id-first.
--        (b) `e2e-target-operator` — vanilla operator, tenant_id='fan-platform',
--            no finance_default_account_id (so the PC-FE-017 + PC-FE-018
--            admin-on-behalf-of spec can set + later verify the value).
--   4. admin_db.admin_operator_roles — bind e2e-super-admin to the
--      SUPER_ADMIN role row (V0006 seeded), copying tenant_id='*'.
--   5. admin_db.admin_roles — relax `require_2fa` on SUPER_ADMIN to FALSE so
--      the e2e SUPER_ADMIN logs in WITHOUT enrolling a second factor. Since
--      TASK-MONO-771 S4 the token exchange AND assume-tenant DO enforce the
--      flag on the primary (OIDC) path (admin-service security.md
--      § Second-Factor Requirement: a `require_2fa` role without `amr ∋ mfa`
--      → 403 MFA_REQUIRED / invalid_grant insufficient_user_authentication);
--      this harness's form-fill login (fixtures/login.ts) has no TOTP step, so
--      this relaxation is what keeps it green. Per-environment runtime tweak;
--      the production profile keeps require_2fa=TRUE (V0013 default). Tenant
--      entry policies stay off here (V0047 `tenant_entry_policy` is empty).
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
-- ---------------------------------------------------------------------------
USE `admin_db`;

-- 5. Relax SUPER_ADMIN require_2fa for the e2e environment ONLY. The flag
--    gates the break-glass password login AND — since TASK-MONO-771 S4 — the
--    OIDC token exchange and assume-tenant (security.md § Second-Factor
--    Requirement). The harness logs in without a TOTP step, so relaxing it
--    here for the test container keeps every path green.
UPDATE admin_roles
   SET require_2fa = FALSE
 WHERE name = 'SUPER_ADMIN';

-- 3. Operator (a) — SUPER_ADMIN caller. tenant_id='*' (ADR-002 platform-scope
--    sentinel). password_hash is a fixed Argon2id hash of 'devpassword123!'
--    (same value V0014 dev seed uses) so any password-based path still works
--    if a test ever needs it. oidc_subject = the seeded credentials.account_id
--    (TASK-MONO-298): Phase 2 (ADR-040) made the SAS access-token `sub` =
--    account_id, so TokenExchangeService's dual-key resolver hits THIS row on the
--    account_id-first lookup (the retained email fallback is no longer exercised).
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
    -- seeded credentials.account_id (§ 2 above), NOT the login email. Phase 2 made
    -- the SAS access-token `sub` = account_id; this seeds the end-state key so the
    -- dual-key resolver's account_id-first lookup hits directly (the retained email
    -- fallback is no longer exercised). Login still uses the unchanged email.
    '01928c4a-7e9f-7c00-9a40-d2b1f5e8c100',
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

-- 3b. TASK-MONO-784 (ADR-MONO-082 b · rider R1): the target's fan-platform membership row.
--     Since S2 the operators list for a tenant is its operator_tenant_assignment rows — the
--     home tenant_id above is kept but no longer read. admin-service V0051 backfills this row
--     only for operators that exist when Flyway runs; this seed runs after Flyway, so without
--     it `e2e-target-operator` is absent from /operators (nightly console e2e red since #4312).
--     Same block as tests/federation-hardening-e2e/fixtures/seed.sql § 8b.
INSERT IGNORE INTO operator_tenant_assignment (operator_id, tenant_id, granted_at, granted_by, permission_set_id)
SELECT o.id, 'fan-platform', NOW(6), NULL, NULL
  FROM admin_operators o
 WHERE o.operator_id = 'e2e-target-operator';

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

-- ---------------------------------------------------------------------------
-- TASK-BE-632 (ADR-MONO-083 P3) — the «이전 호환» (legacy-compat) business permission set rows
-- of the operators this file seeds.
--
-- From P3 an operator's assume-tenant `roles` = the business permission sets assigned to them in
-- that tenant ∩ what the tenant's subscriptions allow — no longer «everything the tenant is
-- subscribed to». admin-service V0052 backfills legacy-compat (= the whole catalog, so `roles`
-- stay byte-identical) for every membership that exists WHEN V0052 RUNS. This file runs AFTER
-- Flyway, so without this block every operator it seeds would assume with NO domain role and
-- every domain call (wms INBOUND_READ, …) would 403. Same rows the backfill would have written —
-- no more. Idempotent (the PK includes source_key; INSERT IGNORE).
-- ---------------------------------------------------------------------------
USE `admin_db`;

INSERT IGNORE INTO operator_domain_set_grant
    (operator_id, tenant_id, set_id, source_key, group_origin, granted_by, granted_at)
SELECT a.operator_id, a.tenant_id, s.id, 'MIGRATION', NULL, NULL, NOW(6)
  FROM operator_tenant_assignment a
  JOIN domain_permission_set s ON s.system_key = 'legacy-compat'
 WHERE a.tenant_id <> '*';

-- A platform ('*') operator: one «all tenants» row (ADR-MONO-083 OD-3).
INSERT IGNORE INTO operator_domain_set_grant
    (operator_id, tenant_id, set_id, source_key, group_origin, granted_by, granted_at)
SELECT o.id, '*', s.id, 'MIGRATION', NULL, NULL, NOW(6)
  FROM admin_operators o
  JOIN domain_permission_set s ON s.system_key = 'legacy-compat'
 WHERE o.tenant_id = '*';

-- ---------------------------------------------------------------------------
-- TASK-MONO-773 S6 — three PERSONAL (consumer-pool) accounts with NO operator facet, for
-- `non-operator-tenant-creation.spec.ts` (AC-1 · AC-2 · AC-2 roles · AC-5 · AC-7).
--
-- Why these rows, and only these:
--   • auth_db.credentials, tenant `consumer-pool` — the console client's form login falls back across
--     tenants (console client only, TASK-BE-604) and finds exactly ONE row for these emails, so the
--     principal is the pool account; IAM 773 S5 then issues the console SHELL token (no operator facet →
--     still a token, `tenant_id=iam`, an `id_token` for the logout hint). No `iam` row for these emails —
--     a second row would make the cross-tenant fallback ambiguous (fail-closed).
--   • account_db identities + accounts in `consumer-pool`, `email_verified_at` SET — the onboarding trust
--     gate (IAM 773 S3) reads the account (`GET /internal/accounts/{sub}`) and asks
--     `verified-email:match`; an unverified email would answer `403 EMAIL_NOT_VERIFIED` and AC-2 could not
--     create anything. The account id IS the credential's account_id (= the token `sub`).
--   • NO admin_db row — that is the point: the operator exchange must answer `401`.
-- Two accounts, not one: the specs run in parallel (`fullyParallel`), and the AC-2 account stops being a
-- non-operator the moment it creates a tenant — the shell / logout specs need one that never does.
-- A third (`e2e-pool-roles`, TASK-MONO-773 AC-2 «assume roles» test — owner decision 2026-10-15): it also
-- creates a tenant, in parallel with the AC-2 spec; its own account keeps the two creations from sharing
-- one owner's tenant cap (3, retries included) and one landing.
-- Same Argon2id hash (`devpassword123!`) as every row above. Idempotent (INSERT IGNORE).
-- ---------------------------------------------------------------------------
USE `auth_db`;

INSERT IGNORE INTO credentials (
    tenant_id, account_id, email, credential_hash, hash_algorithm, created_at, updated_at, version
) VALUES
(
    'consumer-pool',
    '01928c4a-7e9f-7c00-9a40-d2b1f5e8c773',
    'e2e-pool-shell@example.com',
    '$argon2id$v=16$m=65536,t=3,p=1$7u/kw4KcLt7/i1nTEzEfsH7kRIraSsh1w9qOB7BhxUMTJdk3Oqp6zBklBlcMzJ4jS0PpgLYN+MW+1HlJF3m7ew$OJzCJkqvkul/EbS2FejjcDPx7Htj2HkAiCz74xcGBeY',
    'argon2id', NOW(6), NOW(6), 0
),
(
    'consumer-pool',
    '01928c4a-7e9f-7c00-9a40-d2b1f5e8c774',
    'e2e-pool-creator@example.com',
    '$argon2id$v=16$m=65536,t=3,p=1$7u/kw4KcLt7/i1nTEzEfsH7kRIraSsh1w9qOB7BhxUMTJdk3Oqp6zBklBlcMzJ4jS0PpgLYN+MW+1HlJF3m7ew$OJzCJkqvkul/EbS2FejjcDPx7Htj2HkAiCz74xcGBeY',
    'argon2id', NOW(6), NOW(6), 0
),
(
    'consumer-pool',
    '01928c4a-7e9f-7c00-9a40-d2b1f5e8c775',
    'e2e-pool-roles@example.com',
    '$argon2id$v=16$m=65536,t=3,p=1$7u/kw4KcLt7/i1nTEzEfsH7kRIraSsh1w9qOB7BhxUMTJdk3Oqp6zBklBlcMzJ4jS0PpgLYN+MW+1HlJF3m7ew$OJzCJkqvkul/EbS2FejjcDPx7Htj2HkAiCz74xcGBeY',
    'argon2id', NOW(6), NOW(6), 0
);

USE `account_db`;

-- The identity lives in the pool like its account (multi-tenancy.md § 소비자 계정 풀 § 3 — same shape as
-- account-service migration-dev R__05). identity_id is its own UUID, not the account id.
INSERT IGNORE INTO identities (identity_id, tenant_id, primary_email, status, created_at, updated_at, version)
VALUES
    ('01928c4b-7e9f-7c00-9a40-d2b1f5e8c773', 'consumer-pool', 'e2e-pool-shell@example.com', 'ACTIVE', NOW(6), NOW(6), 0),
    ('01928c4b-7e9f-7c00-9a40-d2b1f5e8c774', 'consumer-pool', 'e2e-pool-creator@example.com', 'ACTIVE', NOW(6), NOW(6), 0),
    ('01928c4b-7e9f-7c00-9a40-d2b1f5e8c775', 'consumer-pool', 'e2e-pool-roles@example.com', 'ACTIVE', NOW(6), NOW(6), 0);

INSERT IGNORE INTO accounts (id, identity_id, tenant_id, email, status, email_verified_at, created_at, updated_at, version)
VALUES
    ('01928c4a-7e9f-7c00-9a40-d2b1f5e8c773', '01928c4b-7e9f-7c00-9a40-d2b1f5e8c773',
     'consumer-pool', 'e2e-pool-shell@example.com', 'ACTIVE', NOW(6), NOW(6), NOW(6), 0),
    ('01928c4a-7e9f-7c00-9a40-d2b1f5e8c774', '01928c4b-7e9f-7c00-9a40-d2b1f5e8c774',
     'consumer-pool', 'e2e-pool-creator@example.com', 'ACTIVE', NOW(6), NOW(6), NOW(6), 0),
    ('01928c4a-7e9f-7c00-9a40-d2b1f5e8c775', '01928c4b-7e9f-7c00-9a40-d2b1f5e8c775',
     'consumer-pool', 'e2e-pool-roles@example.com', 'ACTIVE', NOW(6), NOW(6), NOW(6), 0);
