-- TASK-MONO-811 (ADR-MONO-084 P6): indexes for the IAM overview `newAccount` metric
-- (admin-to-account.md § GET /internal/account-metrics/daily).
--
-- The aggregate reads only a timestamp column over a half-open window [from, to); days are cut
-- in Java (KstPeriodBounds.dateOf), never in SQL.
--
--   * accounts (tenant_id, created_at): one tenant's accounts created in the window. The existing
--     (tenant_id, email) / (tenant_id, id) uniques cannot serve a range on created_at.
--   * accounts (created_at): the platform-scope ('*') query across every tenant.
--   * consumer_site_memberships (site_tenant_id, consented_at): a consumer site's pool members
--     who joined in the window. The existing (site_tenant_id, status) index has status between.
--
-- Index DDL only — no column, constraint or row is touched.

CREATE INDEX idx_accounts_tenant_created
    ON accounts (tenant_id, created_at);

CREATE INDEX idx_accounts_created_at
    ON accounts (created_at);

CREATE INDEX idx_consumer_site_memberships_site_consented
    ON consumer_site_memberships (site_tenant_id, consented_at);
