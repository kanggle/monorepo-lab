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
--   purely against auth_db.credentials — it does NOT call account-service. The
--   issued OIDC token carries tenant_id/tenant_type (no account_type claim is
--   emitted by the current pipeline), and web-store's NextAuth signIn callback
--   ACCEPTS a token with an absent account_type (the guard only rejects an
--   explicit non-CONSUMER value). So a single credential row is sufficient for
--   a CONSUMER to log in — no account_db row, no account-service container.
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
