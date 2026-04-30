-- TASK-BE-229
-- auth-service: add tenant_id to credentials, refresh_tokens, social_identities
-- per specs/features/multi-tenancy.md §Isolation Strategy.
--
-- Phase 1: add column with DEFAULT 'fan-platform' (backfills all existing rows).
-- Phase 2: drop the DEFAULT (NOT NULL is retained).
-- Phase 3: drop old unique indexes, create new composite ones.

-- ============================================================
-- credentials
-- ============================================================
ALTER TABLE credentials
    ADD COLUMN tenant_id VARCHAR(32) NOT NULL DEFAULT 'fan-platform' AFTER id;

-- Drop old single-column email unique index (created by V0006).
ALTER TABLE credentials
    DROP INDEX idx_credentials_email;

-- Drop DEFAULT (NOT NULL is kept).
ALTER TABLE credentials
    ALTER COLUMN tenant_id DROP DEFAULT;

-- New composite unique: (tenant_id, email) — allows same email across tenants.
CREATE UNIQUE INDEX uk_credentials_tenant_email ON credentials (tenant_id, email);

-- ============================================================
-- refresh_tokens
-- ============================================================
ALTER TABLE refresh_tokens
    ADD COLUMN tenant_id VARCHAR(32) NOT NULL DEFAULT 'fan-platform' AFTER account_id;

ALTER TABLE refresh_tokens
    ALTER COLUMN tenant_id DROP DEFAULT;

-- ============================================================
-- social_identities
-- ============================================================
ALTER TABLE social_identities
    ADD COLUMN tenant_id VARCHAR(32) NOT NULL DEFAULT 'fan-platform' AFTER account_id;

-- Drop old single-column unique index on (provider, provider_user_id).
ALTER TABLE social_identities
    DROP INDEX uk_social_provider_user;

ALTER TABLE social_identities
    ALTER COLUMN tenant_id DROP DEFAULT;

-- New composite unique: (tenant_id, provider, provider_user_id).
CREATE UNIQUE INDEX uk_social_tenant_provider_user ON social_identities (tenant_id, provider, provider_user_id);
