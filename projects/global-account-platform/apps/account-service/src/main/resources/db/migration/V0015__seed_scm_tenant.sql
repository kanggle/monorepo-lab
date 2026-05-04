-- TASK-MONO-042: register the 'scm' tenant for scm-platform bootstrap
-- (TASK-MONO-040). scm-platform v1 is backend-only — only service-to-service
-- (client_credentials) OAuth flow is registered in auth-service V0013;
-- user-flow PKCE client deferred to v2 when frontend joins.
--
-- TenantType: B2B_ENTERPRISE — supply chain platform is enterprise-facing,
-- not consumer-facing. Mirrors wms's B2B nature (V0010 oauth_clients tenant_type)
-- and differs from ecommerce (V0014) / fan-platform B2C_CONSUMER seeding.
--
-- INSERT IGNORE keeps the migration idempotent so re-running on environments
-- that may have hand-seeded the row stays safe.
INSERT IGNORE INTO tenants (tenant_id, display_name, tenant_type, status, created_at, updated_at)
VALUES ('scm', 'Supply Chain Management Platform', 'B2B_ENTERPRISE', 'ACTIVE', NOW(6), NOW(6));
