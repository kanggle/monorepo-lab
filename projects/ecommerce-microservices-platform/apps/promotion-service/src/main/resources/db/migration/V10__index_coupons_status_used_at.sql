-- TASK-INT-028 — batch-worker's orphan-coupon reconciliation lists coupons that have been USED
-- for a while: WHERE status = 'USED' AND order_id IS NOT NULL AND used_at < :cutoff
-- ORDER BY used_at LIMIT :n. The existing (status, expires_at) index serves the expiry sweep, not
-- this ordering; without an index on used_at the sweep scans and sorts every coupon each run.

CREATE INDEX idx_coupons_status_used_at ON coupons (status, used_at);
