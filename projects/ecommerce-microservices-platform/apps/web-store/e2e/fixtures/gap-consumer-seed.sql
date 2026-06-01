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
--   account_type (TASK-BE-329/330, ADR-MONO-021): as of BE-329 the GAP token
--   pipeline DOES emit the account_type claim (CONSUMER|OPERATOR), denormalized
--   on credentials.account_type (V0022, NOT NULL DEFAULT 'CONSUMER'). This seed
--   sets it explicitly to CONSUMER (BE-330 D2 semantics: the provisioning path
--   decides the type) so the web-store session carries account_type=CONSUMER.
--   TASK-INT-024 asserts this end-to-end.
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
    account_type,
    email,
    credential_hash,
    hash_algorithm,
    created_at,
    updated_at,
    version
) VALUES (
    'ecommerce',
    '01928c4a-7e9f-7c00-9a40-d2b1f5e8e001',
    'CONSUMER',
    'e2e-consumer@example.com',
    '$argon2id$v=16$m=65536,t=3,p=1$7u/kw4KcLt7/i1nTEzEfsH7kRIraSsh1w9qOB7BhxUMTJdk3Oqp6zBklBlcMzJ4jS0PpgLYN+MW+1HlJF3m7ew$OJzCJkqvkul/EbS2FejjcDPx7Htj2HkAiCz74xcGBeY',
    'argon2id',
    NOW(6),
    NOW(6),
    0
);
