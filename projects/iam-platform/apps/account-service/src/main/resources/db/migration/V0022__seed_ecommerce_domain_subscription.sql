-- TASK-MONO-240: seed ecommerce as a self-subscribed domain (ADR-MONO-030 Step 4 facet a).
-- Mirrors the V0019 self-subscription seed for wms/scm/erp/finance. The ecommerce
-- tenant row already exists (V0014, tenant_type=B2C_CONSUMER, ACTIVE) -- only the
-- subscription row is added so the console catalog binds the ecommerce product to it.
--
-- Net-zero / idempotent: INSERT IGNORE + a WHERE-EXISTS guard against the tenants
-- table (same shape as V0019) keeps the migration safe to re-run and a no-op in any
-- environment where the 'ecommerce' tenant was not registered (AC-7 degrade: the
-- catalog simply omits ecommerce / renders tenants:[] rather than failing). status
-- must be 'ACTIVE' (the chk_tds_status CHECK in V0021 allows PENDING|ACTIVE|
-- SUSPENDED|CANCELLED) so the ADR-019 D4 catalog derivation, which filters
-- status='ACTIVE', binds the ecommerce product to the ecommerce tenant.
INSERT IGNORE INTO tenant_domain_subscription (tenant_id, domain_key, status, created_at, updated_at)
SELECT t.tenant_id, 'ecommerce', 'ACTIVE', NOW(6), NOW(6)
FROM tenants t
WHERE t.tenant_id = 'ecommerce';
