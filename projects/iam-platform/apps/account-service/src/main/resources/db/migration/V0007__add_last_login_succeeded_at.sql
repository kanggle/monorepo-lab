-- TASK-BE-092: dormant scheduler — add last_login_succeeded_at column to accounts
-- retention.md §1.4: column is added in this migration; existing rows remain NULL
-- and are handled at query time via COALESCE(last_login_succeeded_at, created_at).
ALTER TABLE accounts ADD COLUMN last_login_succeeded_at DATETIME(6) NULL;

-- Index supports the dormant scheduler query (status = 'ACTIVE' AND threshold filter).
-- Composite (status, last_login_succeeded_at) is selective for ACTIVE-only candidates.
CREATE INDEX idx_accounts_status_last_login ON accounts (status, last_login_succeeded_at);
