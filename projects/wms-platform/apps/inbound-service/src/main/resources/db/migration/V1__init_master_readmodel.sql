-- Local read-model snapshots fed by master.* events. This service never writes
-- to these tables from REST or use-case paths — only the master consumers do.
--
-- Authoritative reference: specs/services/inbound-service/domain-model.md §9
--
-- Out-of-order delivery handling is enforced at upsert time by checking
-- master_version (see consumer code). The schema only declares the shape.
--
-- Six snapshot tables: warehouse, zone, location, sku, lot, partner.

-- ---------------------------------------------------------------------------
-- Warehouse snapshot
-- ---------------------------------------------------------------------------
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

-- ---------------------------------------------------------------------------
-- Zone snapshot
-- ---------------------------------------------------------------------------
CREATE TABLE zone_snapshot (
    id              UUID         PRIMARY KEY,
    warehouse_id    UUID         NOT NULL,
    zone_code       VARCHAR(20)  NOT NULL,
    zone_type       VARCHAR(20)  NOT NULL,
    status          VARCHAR(20)  NOT NULL,
    cached_at       TIMESTAMPTZ  NOT NULL,
    master_version  BIGINT       NOT NULL,
    CONSTRAINT ck_zone_snapshot_status
        CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

CREATE INDEX idx_zone_snapshot_warehouse
    ON zone_snapshot (warehouse_id);

CREATE INDEX idx_zone_snapshot_code
    ON zone_snapshot (zone_code);

-- ---------------------------------------------------------------------------
-- Location snapshot
-- ---------------------------------------------------------------------------
CREATE TABLE location_snapshot (
    id              UUID         PRIMARY KEY,
    location_code   VARCHAR(40)  NOT NULL,
    warehouse_id    UUID         NOT NULL,
    zone_id         UUID         NOT NULL,
    location_type   VARCHAR(20)  NOT NULL,
    status          VARCHAR(20)  NOT NULL,
    cached_at       TIMESTAMPTZ  NOT NULL,
    master_version  BIGINT       NOT NULL,
    CONSTRAINT ck_location_snapshot_status
        CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

CREATE INDEX idx_location_snapshot_warehouse
    ON location_snapshot (warehouse_id);

CREATE INDEX idx_location_snapshot_code
    ON location_snapshot (location_code);

-- ---------------------------------------------------------------------------
-- SKU snapshot
-- ---------------------------------------------------------------------------
CREATE TABLE sku_snapshot (
    id              UUID         PRIMARY KEY,
    sku_code        VARCHAR(40)  NOT NULL,
    tracking_type   VARCHAR(10)  NOT NULL,
    status          VARCHAR(20)  NOT NULL,
    cached_at       TIMESTAMPTZ  NOT NULL,
    master_version  BIGINT       NOT NULL,
    CONSTRAINT ck_sku_snapshot_tracking_type
        CHECK (tracking_type IN ('NONE', 'LOT')),
    CONSTRAINT ck_sku_snapshot_status
        CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

CREATE INDEX idx_sku_snapshot_code
    ON sku_snapshot (sku_code);

-- ---------------------------------------------------------------------------
-- Lot snapshot
-- ---------------------------------------------------------------------------
CREATE TABLE lot_snapshot (
    id              UUID         PRIMARY KEY,
    sku_id          UUID         NOT NULL,
    lot_no          VARCHAR(40)  NOT NULL,
    expiry_date     DATE,
    status          VARCHAR(20)  NOT NULL,
    cached_at       TIMESTAMPTZ  NOT NULL,
    master_version  BIGINT       NOT NULL,
    CONSTRAINT ck_lot_snapshot_status
        CHECK (status IN ('ACTIVE', 'INACTIVE', 'EXPIRED'))
);

CREATE INDEX idx_lot_snapshot_sku
    ON lot_snapshot (sku_id);

-- ---------------------------------------------------------------------------
-- Partner snapshot
-- ---------------------------------------------------------------------------
CREATE TABLE partner_snapshot (
    id              UUID         PRIMARY KEY,
    partner_code    VARCHAR(40)  NOT NULL,
    partner_type    VARCHAR(20)  NOT NULL,
    status          VARCHAR(20)  NOT NULL,
    cached_at       TIMESTAMPTZ  NOT NULL,
    master_version  BIGINT       NOT NULL,
    CONSTRAINT ck_partner_snapshot_partner_type
        CHECK (partner_type IN ('SUPPLIER', 'CARRIER', 'CUSTOMER', 'BOTH')),
    CONSTRAINT ck_partner_snapshot_status
        CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

CREATE INDEX idx_partner_snapshot_code
    ON partner_snapshot (partner_code);
