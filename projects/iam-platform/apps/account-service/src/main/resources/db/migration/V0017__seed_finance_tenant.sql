-- TASK-MONO-114: register the 'finance' tenant for finance-platform bootstrap
-- (ADR-MONO-008 ACCEPTED 2026-05-18, Option C). finance-platform v1 is
-- backend-only — only service-to-service (client_credentials) OAuth flow is
-- registered in auth-service V0017; user-flow PKCE client deferred (finance
-- UI is rendered by the unified platform console per ADR-MONO-013 §3.3).
--
-- TenantType: B2B_ENTERPRISE — fintech platform v1 is internal-services
-- facing, not consumer-facing. Mirrors scm's B2B_ENTERPRISE (V0015) and wms
-- (V0016); differs from ecommerce (V0014) / fan-platform (V0009)
-- B2C_CONSUMER seeding.
--
-- INSERT IGNORE keeps the migration idempotent so re-running on environments
-- that may have hand-seeded the row stays safe.
INSERT IGNORE INTO tenants (tenant_id, display_name, tenant_type, status, created_at, updated_at)
VALUES ('finance', 'Finance Platform', 'B2B_ENTERPRISE', 'ACTIVE', NOW(6), NOW(6));
