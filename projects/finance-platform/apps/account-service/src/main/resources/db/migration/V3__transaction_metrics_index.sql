-- TASK-MONO-812 (ADR-MONO-084 P7) — index for the overview performance metrics
-- (GET /api/finance/accounts/metrics).
--
-- The metrics read is `tenant_id = ? AND settled_at >= ? AND settled_at < ?` across every
-- account of the tenant. The V1 indexes lead with (tenant_id, account_id, created_at) — a
-- per-account timeline — and (tenant_id, status), neither of which serves a range on
-- settled_at. MySQL has no partial index; rows never settled carry settled_at NULL and sort
-- at the start of the (tenant_id, settled_at) range, so a bounded range scan skips them.
CREATE INDEX idx_txn_tenant_settled ON transactions (tenant_id, settled_at);
