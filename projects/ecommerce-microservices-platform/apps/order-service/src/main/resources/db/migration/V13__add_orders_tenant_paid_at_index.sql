-- TASK-MONO-807 (ADR-MONO-084 P2) — the overview performance metrics and the insights
-- rankings now select realised sales by `tenant_id` + a `paid_at` window
-- (`GET /api/admin/orders/metrics`, `GET /api/admin/orders/insights?period=`), and the
-- operator overview runs them on every load. No existing index leads with
-- (tenant_id, paid_at): idx_orders_tenant_user is (tenant_id, user_id) and
-- idx_orders_status_created_at is (status, created_at).
CREATE INDEX idx_orders_tenant_paid_at ON orders (tenant_id, paid_at);
