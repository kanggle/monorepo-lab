-- TASK-MONO-811 (ADR-MONO-084 P6): indexes for the IAM overview performance metrics
-- (security-query-api.md § GET /internal/security/metrics/daily).
--
-- The aggregate reads only (outcome, occurred_at) of login_history and occurred_at of
-- account_lock_history over a half-open window [from, to), for one tenant or — for a
-- platform-scope caller ('*') — for every tenant. Days are cut in Java
-- (KstPeriodBounds.dateOf), never in SQL, so no expression index is needed.
--
--   * login_history (tenant_id, outcome, occurred_at): the per-tenant query is an equality
--     on tenant_id, an IN on outcome and a range on occurred_at. The existing
--     (tenant_id, account_id[, outcome]) indexes cannot serve it (account_id sits between).
--     The '*' query is served by the existing idx_login_history_occurred_at (V0001).
--   * account_lock_history (tenant_id, occurred_at): the existing V0008 index leads with
--     (tenant_id, account_id), which a tenant-wide range cannot use.
--   * account_lock_history (occurred_at): the '*' query.
--
-- Index DDL only. The append-only triggers on login_history (V0002, V0010) fire on
-- UPDATE/DELETE of rows and are not affected by CREATE INDEX.

CREATE INDEX idx_login_history_tenant_outcome_occurred
    ON login_history (tenant_id, outcome, occurred_at);

CREATE INDEX idx_account_lock_history_tenant_occurred
    ON account_lock_history (tenant_id, occurred_at);

CREATE INDEX idx_account_lock_history_occurred
    ON account_lock_history (occurred_at);
