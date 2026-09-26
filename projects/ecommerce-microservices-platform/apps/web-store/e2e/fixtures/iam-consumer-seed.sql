-- =============================================================================
-- web-store GAP e2e — CONSUMER login seed (TASK-INT-023)
-- =============================================================================
-- Runtime data fixture (NOT a Flyway migration). Applied to the GAP MySQL
-- container AFTER auth-service Flyway has run (so auth_db.credentials exists)
-- and BEFORE the Playwright run. Mirrors the federation-e2e seed pattern
-- (tests/federation-hardening-e2e/fixtures/seed.sql § 2).
--
-- WHY only auth_db.credentials:
--   GAP's SAS form-login path (CredentialAuthenticationProvider) authenticates
--   purely against auth_db.credentials — it does NOT call account-service. So a
--   single credential row is sufficient for a CONSUMER to log in — no account_db
--   row, no account-service container.
--
--   account_type (TASK-MONO-263, ADR-032 D5 step 4): the account_type claim is no
--   longer emitted and the credentials.account_type column is dropped (V0025). The
--   consumer carries the CUSTOMER role (RoleSeedPolicy seeds it on platform=
--   ecommerce, BE-369); the web-store role-based guard (4b-1) admits on the role.
--
-- Password: 'devpassword123!' (same Argon2id hash as the federation-e2e seed).
-- tenant_id='ecommerce' matches the V0012-seeded ecommerce-web-store-client.
--
-- Re-runnable: INSERT IGNORE (composite UK is (tenant_id, email); account_id is
-- UNIQUE). Applying twice is a no-op.
-- =============================================================================

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
    'ecommerce',
    '01928c4a-7e9f-7c00-9a40-d2b1f5e8e001',
    'e2e-consumer@example.com',
    '$argon2id$v=16$m=65536,t=3,p=1$7u/kw4KcLt7/i1nTEzEfsH7kRIraSsh1w9qOB7BhxUMTJdk3Oqp6zBklBlcMzJ4jS0PpgLYN+MW+1HlJF3m7ew$OJzCJkqvkul/EbS2FejjcDPx7Htj2HkAiCz74xcGBeY',
    'argon2id',
    NOW(6),
    NOW(6),
    0
);

-- =============================================================================
-- TASK-MONO-381 — a CROSS-TENANT credential, so the role guard has something to bite
-- =============================================================================
-- The guard spec (account-type-guard.spec.ts) could not run because this stack was
-- physically unable to issue a CUSTOMER-less token: RoleSeedPolicy was keyed on the
-- CLIENT's platform alone, so every credential that authenticated through the web-store
-- client got CUSTOMER — even with account-service mocked out (roles 404 → fail-soft → seed).
--
-- MONO-381 narrowed the seed: it fires only when the principal's OWN tenant IS the client's
-- platform. So a credential in any other tenant now yields NO roles claim, and web-store's
-- signInCallback rejects it — which is exactly what the spec asserts.
--
-- tenant_id='*' is the platform scope (SUPER_ADMIN, ADR-002). It is chosen deliberately:
--   (a) it is the case MONO-381's Edge Cases flagged — the ecommerce gateway's
--       acceptAnyWellFormedTenant admits '*', so the ROLE guard is the only thing that
--       stops a platform-scope principal from entering the storefront;
--   (b) TenantTypeResolver short-circuits '*' with NO network call (TASK-BE-466), so the
--       login still works against this stack's mocked account-service — any other tenant
--       would need a real tenants row to resolve tenant_type.
--
-- NOTE this is NOT an operator in the ADR-035 sense. An *ecommerce* operator cannot be the
-- subject of this spec at all: TASK-MONO-334 requires an operator to hold a signed-up account
-- in their home tenant, and post-TASK-BE-507 that account is born in the tenant of the client
-- they registered through — so an ecommerce operator is, structurally, a registered shopper.
-- The guard is a cross-tenant guard, not an operator guard (MONO-381's measurement).
--
-- TASK-BE-604 (owner decision D, 2026-09-26): IAM's form-login cross-tenant fallback now
-- applies to the operator console client only. Through the web-store client this '*'
-- credential is refused by IAM itself (`/login?error`, same as a wrong password) and never
-- reaches the role guard — account-type-guard.spec.ts asserts that refusal now.
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
    '01928c4a-7e9f-7c00-9a40-d2b1f5e8e002',
    'e2e-platform-admin@example.com',
    '$argon2id$v=16$m=65536,t=3,p=1$7u/kw4KcLt7/i1nTEzEfsH7kRIraSsh1w9qOB7BhxUMTJdk3Oqp6zBklBlcMzJ4jS0PpgLYN+MW+1HlJF3m7ew$OJzCJkqvkul/EbS2FejjcDPx7Htj2HkAiCz74xcGBeY',
    'argon2id',
    NOW(6),
    NOW(6),
    0
);

-- =============================================================================
-- TASK-BE-605 — a SAME-tenant credential without CUSTOMER, so the role guard bites again
-- =============================================================================
-- After BE-604 the '*' credential above never authenticates through the web-store client, and
-- BE-605 closes the other road (reusing an IAM browser session of another tenant). So a
-- CUSTOMER-less storefront token can no longer come from a CROSS-tenant principal at all.
--
-- It still comes from an ecommerce account whose stored roles lack CUSTOMER: stored
-- account_roles take precedence over the platform seed (TenantClaimTokenCustomizer
-- populateRoles — the seed fires only when the stored list is empty). account-mock.nginx.conf
-- answers this account's roles lookup with ["ECOMMERCE_OPERATOR"] (the shape the storefront's
-- REQUIRED_CONSUMER_ROLE comment names for an operator without a shopper role), so IAM admits
-- the login and web-store's signInCallback bounces it to /login?error=account_type_mismatch.
-- Same password hash as the rows above.
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
    'ecommerce',
    '01928c4a-7e9f-7c00-9a40-d2b1f5e8e003',
    'e2e-no-customer-role@example.com',
    '$argon2id$v=16$m=65536,t=3,p=1$7u/kw4KcLt7/i1nTEzEfsH7kRIraSsh1w9qOB7BhxUMTJdk3Oqp6zBklBlcMzJ4jS0PpgLYN+MW+1HlJF3m7ew$OJzCJkqvkul/EbS2FejjcDPx7Htj2HkAiCz74xcGBeY',
    'argon2id',
    NOW(6),
    NOW(6),
    0
);
