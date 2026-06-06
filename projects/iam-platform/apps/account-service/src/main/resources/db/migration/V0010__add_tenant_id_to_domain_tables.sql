-- TASK-BE-228: Add tenant_id column to domain tables and reconfigure unique indexes.
-- Phase 1: Add tenant_id with DEFAULT so existing rows are backfilled automatically.
-- Phase 2: Drop old single-column unique index on accounts.email.
-- Phase 3: Add composite unique index (tenant_id, email) on accounts.
-- Phase 4: Add FK from accounts.tenant_id → tenants.tenant_id.

-- accounts
ALTER TABLE accounts
    ADD COLUMN tenant_id VARCHAR(32) NOT NULL DEFAULT 'fan-platform' AFTER id;

ALTER TABLE accounts
    DROP INDEX idx_accounts_email;

ALTER TABLE accounts
    ADD UNIQUE INDEX idx_accounts_tenant_email (tenant_id, email),
    ADD CONSTRAINT fk_accounts_tenant_id FOREIGN KEY (tenant_id) REFERENCES tenants(tenant_id);

-- profiles
ALTER TABLE profiles
    ADD COLUMN tenant_id VARCHAR(32) NOT NULL DEFAULT 'fan-platform' AFTER id;

-- account_status_history
ALTER TABLE account_status_history
    ADD COLUMN tenant_id VARCHAR(32) NOT NULL DEFAULT 'fan-platform' AFTER id;

-- outbox (table is named 'outbox' per V0003)
ALTER TABLE outbox
    ADD COLUMN tenant_id VARCHAR(32) NOT NULL DEFAULT 'fan-platform' AFTER id;
