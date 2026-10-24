-- TASK-BE-653: the site a consumer-pool account logged in to.
--
-- auth.login.attempted / failed / succeeded carry an optional, additive siteTenantId
-- (auth-events.md): the initiating client's site tenant, set ONLY when the account's own
-- tenant is 'consumer-pool' and a real consumer-site client initiated the login. tenant_id
-- keeps its meaning (the account's own tenant — 'consumer-pool' for a pool account), so the
-- detection rules, PII masking and every existing (tenant_id, ...) index are untouched.
--
--   * site_tenant_id VARCHAR(32) NULL — same width as tenant_id. NULL for every non-pool
--     login, for a pool login with no site (operator-invitation acceptance, no client, the
--     console client), and for every row written before this migration. No backfill
--     (owner decision): the old rows carry no site, and the metrics contract
--     (admin-api.md § GET /api/admin/metrics) states from when pool logins count by site.
--   * idx_login_history_site_outcome_occurred (site_tenant_id, outcome, occurred_at) — the
--     mirror of V0012's idx_login_history_tenant_outcome_occurred. The per-tenant metrics
--     query becomes `tenant_id = X OR site_tenant_id = X`; with both indexes MySQL can serve
--     each side by its own index (index_merge union) instead of a scan.
--
-- ADD COLUMN with a NULL default rewrites no row value; the append-only triggers on
-- login_history (V0002, V0010) fire on UPDATE/DELETE of rows and are not affected by DDL.

ALTER TABLE login_history
    ADD COLUMN site_tenant_id VARCHAR(32) NULL AFTER tenant_id;

CREATE INDEX idx_login_history_site_outcome_occurred
    ON login_history (site_tenant_id, outcome, occurred_at);
