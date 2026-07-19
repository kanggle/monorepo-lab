-- Local read-model snapshot of master Warehouses, fed by wms.master.warehouse.v1.
-- This service never writes to this table from REST or use-case paths — only the
-- MasterWarehouseConsumer does.
--
-- Motivation (ADR-MONO-050 D9): LowStockDetectionService resolves a warehouse's
-- business warehouseCode from its uuid to enrich the inventory.low-stock-detected
-- alert with warehouseCode, so the cross-project scm demand-planning consumer can
-- address a replenishment PO by code rather than by uuid.
--
-- Out-of-order delivery handling is enforced at upsert time by checking
-- master_version (see MasterReadModelRepositoryImpl#upsertWarehouse). The schema
-- only declares the shape.

CREATE TABLE warehouse_snapshot (
    id              UUID         PRIMARY KEY,
    warehouse_code  VARCHAR(20)  NOT NULL,
    status          VARCHAR(20)  NOT NULL,
    cached_at       TIMESTAMPTZ  NOT NULL,
    master_version  BIGINT       NOT NULL,
    CONSTRAINT ck_warehouse_snapshot_status
        CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

CREATE INDEX idx_warehouse_snapshot_code
    ON warehouse_snapshot (warehouse_code);
