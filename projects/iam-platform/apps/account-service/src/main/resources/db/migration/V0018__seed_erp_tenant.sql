-- TASK-MONO-119: register the 'erp' tenant for erp-platform bootstrap
-- (ADR-MONO-016 ACCEPTED 2026-05-19, Option C). erp-platform v1 is
-- backend-only — only service-to-service (client_credentials) OAuth flow is
-- registered in auth-service V0018; user-flow PKCE client deferred (erp
-- UI is rendered by the unified platform console per ADR-MONO-013 §3.3).
--
-- TenantType: B2B_ENTERPRISE — erp platform v1 is internal-services
-- facing, not consumer-facing (internal-system domain — no external public
-- traffic). Mirrors scm's B2B_ENTERPRISE (V0015), wms (V0016) and finance
-- (V0017); differs from ecommerce (V0014) / fan-platform (V0009)
-- B2C_CONSUMER seeding.
--
-- INSERT IGNORE keeps the migration idempotent so re-running on environments
-- that may have hand-seeded the row stays safe.
INSERT IGNORE INTO tenants (tenant_id, display_name, tenant_type, status, created_at, updated_at)
VALUES ('erp', 'ERP Platform', 'B2B_ENTERPRISE', 'ACTIVE', NOW(6), NOW(6));
