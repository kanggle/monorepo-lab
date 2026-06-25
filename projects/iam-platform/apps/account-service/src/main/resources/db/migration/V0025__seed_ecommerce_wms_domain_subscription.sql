-- Subscribe the ecommerce tenant to the wms domain so ecommerce operators can
-- see the WMS 출고(outbound) fulfillment section in the platform console.
--
-- The ecommerce platform's orders are fulfilled through WMS outbound, so an
-- ecommerce-scoped operator legitimately needs visibility into that domain.
-- This is net-POSITIVE (same shape as V0020's acme-corp -> wms subscription):
-- the console registry's tenants[] for the wms product will now include
-- 'ecommerce' alongside the wms-slug self-subscription seeded in V0019.
--
-- Downstream effect (ADR-MONO-019 D4 catalog derivation, status='ACTIVE' filter):
--   GET /api/admin/console/registry -> wms product -> tenants includes 'ecommerce'
--   console-web /wms and /wms/outbound eligibility gate (tenants.length > 0) passes.
--
-- The ecommerce tenant row already exists (V0014, B2C_CONSUMER, ACTIVE) and is
-- self-subscribed to ecommerce (V0022); only the wms subscription row is added.
--
-- Net-zero / idempotent: INSERT IGNORE + a WHERE-EXISTS guard against the tenants
-- table (same shape as V0022) keeps the migration safe to re-run and a no-op in
-- any environment where the 'ecommerce' tenant was not registered.
INSERT IGNORE INTO tenant_domain_subscription (tenant_id, domain_key, status, created_at, updated_at)
SELECT t.tenant_id, 'wms', 'ACTIVE', NOW(6), NOW(6)
FROM tenants t
WHERE t.tenant_id = 'ecommerce';
