-- TASK-BE-369 (ADR-MONO-030 Step 4 / ADR-MONO-031 Phase 4a precondition, outer
-- tenant axis — M1).
--
-- Row-level tenant_id on the shipping-service root aggregate table (shippings).
-- The outbox / processed_events / processed_carrier_webhooks / shedlock tables are
-- messaging / infrastructure tables and carry tenant on the event envelope payload,
-- not as a column (ADR-030 §2.3 M5, messaging standard) — intentionally unchanged.
--
-- Zero-downtime 3-step: ADD nullable -> backfill 'ecommerce' (default-tenant, D8
-- net-zero) -> SET NOT NULL. All pre-existing rows belong to the single implicit
-- store, mapped to default tenant 'ecommerce'.

-- ---- shippings --------------------------------------------------------------
ALTER TABLE shippings ADD COLUMN tenant_id VARCHAR(64);
UPDATE shippings SET tenant_id = 'ecommerce' WHERE tenant_id IS NULL;
ALTER TABLE shippings ALTER COLUMN tenant_id SET NOT NULL;
-- Backs the admin/operator list path (findByStatus / findAll). Lead with tenant_id
-- (every admin read is tenant-scoped), then status (the optional filter) and
-- updated_at (the in-flight ordering key the auto-collect sweep / list share).
CREATE INDEX idx_shippings_tenant_status ON shippings (tenant_id, status, updated_at);

-- ---- shipping_status_history (NO tenant_id column — deliberate) --------------
-- shipping_status_history is NOT given a tenant_id column. It is never queried
-- independently: it is loaded ONLY through its parent shippings row via the EAGER
-- @OneToMany (ShippingJpaEntity.statusHistory), so the parent's tenant scoping
-- already isolates every history read. Adding a redundant column would only invite
-- drift between parent and child tenant. (Contrast user-service, which added
-- tenant_id to all of its tables because each is queried on its own.)
