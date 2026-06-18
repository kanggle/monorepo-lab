-- TASK-BE-403 (ADR-MONO-030 Step 4 facet c — outer tenant axis M1).
--
-- Row-level tenant_id on the review-service persistent entity (reviews).
-- Backfills all pre-existing rows to 'ecommerce' (default-tenant, D8 net-zero):
-- existing single-store data maps to the one implicit store that has always existed.
--
-- Zero-downtime 3-step: ADD nullable -> backfill 'ecommerce' -> SET NOT NULL.

-- ---- reviews -----------------------------------------------------------------
ALTER TABLE reviews ADD COLUMN tenant_id VARCHAR(64);
UPDATE reviews SET tenant_id = 'ecommerce' WHERE tenant_id IS NULL;
ALTER TABLE reviews ALTER COLUMN tenant_id SET NOT NULL;
-- tenant_id is the primary read-scoping axis.
CREATE INDEX idx_reviews_tenant_id ON reviews (tenant_id);
