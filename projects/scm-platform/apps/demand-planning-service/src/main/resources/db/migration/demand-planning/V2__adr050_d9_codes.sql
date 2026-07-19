-- ADR-MONO-050 D9 (Option A) — cross-service identifiers are CODES.
-- The scm→wms inbound-expected loop resolves both warehouse and supplier by CODE
-- on the wms side (findWarehouseByCode / findPartnerByCode). scm previously emitted
-- UUIDs (locationId + an scm supplier UUID) which fail-closed to the wms DLT.
--
-- 1. supplier_id becomes a supplier CODE (was UUID). sku_supplier_map.supplier_id is
--    now the authoritative supplier code (v1 stand-in for the deferred supplier-service).
-- 2. reorder_suggestion carries the warehouse CODE alongside the internal warehouse
--    UUID (kept as the stable open-suggestion dedup-key dimension). NULL for BATCH
--    suggestions (the IVS read-model carries no warehouse code — follow-up).

-- supplier_id: UUID → VARCHAR (code). USING cast is explicit (uuid → text).
ALTER TABLE sku_supplier_map
    ALTER COLUMN supplier_id TYPE VARCHAR(64) USING supplier_id::text;

ALTER TABLE reorder_suggestion
    ALTER COLUMN supplier_id TYPE VARCHAR(64) USING supplier_id::text;

-- warehouse CODE carried alongside the internal warehouse_id (dedup dimension).
-- Nullable: BATCH-sourced suggestions have no code and never emit inbound-expected.
ALTER TABLE reorder_suggestion
    ADD COLUMN warehouse_code VARCHAR(64);
