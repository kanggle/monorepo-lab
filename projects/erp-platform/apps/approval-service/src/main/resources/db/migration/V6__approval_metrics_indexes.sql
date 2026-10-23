-- TASK-MONO-809 (ADR-MONO-084 P4) — overview performance metrics.
-- GET /api/erp/approval/metrics counts requests by their own transition timestamp:
--   submitted = submitted_at in window
--   approved / rejected = status + finalized_at in window
-- Neither column was covered by an index. Every key prefixes tenant_id (multi-tenant
-- defense-in-depth, same as the V1 indexes).
CREATE INDEX idx_approval_tenant_submitted_at ON approval_request (tenant_id, submitted_at);
CREATE INDEX idx_approval_tenant_status_finalized_at ON approval_request (tenant_id, status, finalized_at);
