-- =============================================================================
-- Federation Hardening E2E — wms inbound-service read-model seed
-- (TASK-SCM-INT-004 leg 2, ADR-MONO-050 §D9).
--
-- Applied via psql into inbound_db AFTER wms-inbound-service is healthy (so
-- Flyway V1 has created the *_snapshot tables). Seeds the shared CODES the
-- injected scm.procurement.inbound-expected.v1 envelope carries, so the wms
-- consumer resolves them (findWarehouseByCode / findPartnerByCode / findSkuByCode)
-- and the loop closes. Mirrors what the wms.master.* projection would populate.
--
-- Codes must match the spec (specs/scm-inbound-expected-loop.spec.ts):
--   warehouse WH-FED-IE   (ACTIVE)
--   supplier  SUP-FED-IE  (ACTIVE SUPPLIER)
--   sku       SKU-FED-IE  (ACTIVE)
-- Idempotent (ON CONFLICT DO NOTHING) so a re-applied seed is harmless.
-- =============================================================================

INSERT INTO warehouse_snapshot (id, warehouse_code, status, cached_at, master_version)
VALUES ('11111111-1111-1111-1111-111111111111', 'WH-FED-IE', 'ACTIVE', now(), 1)
ON CONFLICT (id) DO NOTHING;

INSERT INTO partner_snapshot (id, partner_code, partner_type, status, cached_at, master_version)
VALUES ('22222222-2222-2222-2222-222222222222', 'SUP-FED-IE', 'SUPPLIER', 'ACTIVE', now(), 1)
ON CONFLICT (id) DO NOTHING;

INSERT INTO sku_snapshot (id, sku_code, tracking_type, status, cached_at, master_version)
VALUES ('33333333-3333-3333-3333-333333333333', 'SKU-FED-IE', 'NONE', 'ACTIVE', now(), 1)
ON CONFLICT (id) DO NOTHING;
