-- TASK-BE-400 (ADR-MONO-030 Step 4 facet c — outer tenant axis M1).
--
-- Row-level tenant_id on the payment-service persistent entity (payments).
-- Backfills all pre-existing rows to 'ecommerce' (default-tenant, D8 net-zero):
-- existing single-store data maps to the one implicit store that has always existed.
--
-- Zero-downtime 3-step: ADD nullable -> backfill 'ecommerce' -> SET NOT NULL.
-- All pre-existing rows belong to the single implicit store, mapped to default
-- tenant 'ecommerce'.

-- ---- payments ----------------------------------------------------------------
ALTER TABLE payments ADD COLUMN tenant_id VARCHAR(64);
UPDATE payments SET tenant_id = 'ecommerce' WHERE tenant_id IS NULL;
ALTER TABLE payments ALTER COLUMN tenant_id SET NOT NULL;
-- tenant_id is the primary read-scoping axis; lead with it on the supporting index.
CREATE INDEX idx_payments_tenant_id ON payments (tenant_id);
