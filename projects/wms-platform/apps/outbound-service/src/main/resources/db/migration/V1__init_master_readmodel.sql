-- Local read-model snapshots fed by master.* events. This service never writes
-- to these tables from REST or use-case paths — only the master consumers do.
--
-- Authoritative reference: specs/services/outbound-service/domain-model.md §11
--
-- Six snapshot tables: warehouse, zone, location, sku, lot, partner.

CREATE TABLE warehouse_snapshot (
    id              UUID         PRIMARY KEY,
    warehouse_code  VARCHAR(20)  NOT NULL,
    status          VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    cached_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    master_version  BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT ck_warehouse_snapshot_status
        CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

CREATE INDEX idx_warehouse_snapshot_code ON warehouse_snapshot (warehouse_code);

CREATE TABLE zone_snapshot (
    id              UUID         PRIMARY KEY,
    warehouse_id    UUID         NOT NULL,
    zone_code       VARCHAR(20)  NOT NULL,
    zone_type       VARCHAR(50)  NOT NULL,
    status          VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    cached_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    master_version  BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT ck_zone_snapshot_status
        CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

CREATE INDEX idx_zone_snapshot_warehouse ON zone_snapshot (warehouse_id);

CREATE TABLE location_snapshot (
    id              UUID         PRIMARY KEY,
    location_code   VARCHAR(40)  NOT NULL,
    warehouse_id    UUID         NOT NULL,
    zone_id         UUID         NOT NULL,
    location_type   VARCHAR(50)  NOT NULL,
    status          VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    cached_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    master_version  BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT ck_location_snapshot_status
        CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

CREATE INDEX idx_location_snapshot_warehouse ON location_snapshot (warehouse_id);

CREATE TABLE sku_snapshot (
    id              UUID         PRIMARY KEY,
    sku_code        VARCHAR(40)  NOT NULL,
    tracking_type   VARCHAR(10)  NOT NULL,
    status          VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    cached_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    master_version  BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT ck_sku_snapshot_tracking_type
        CHECK (tracking_type IN ('NONE', 'LOT')),
    CONSTRAINT ck_sku_snapshot_status
        CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

CREATE INDEX idx_sku_snapshot_code ON sku_snapshot (sku_code);

CREATE TABLE lot_snapshot (
    id              UUID         PRIMARY KEY,
    sku_id          UUID         NOT NULL,
    lot_no          VARCHAR(40)  NOT NULL,
    expiry_date     DATE,
    status          VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    cached_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    master_version  BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT ck_lot_snapshot_status
        CHECK (status IN ('ACTIVE', 'INACTIVE', 'EXPIRED'))
);

CREATE INDEX idx_lot_snapshot_sku ON lot_snapshot (sku_id);

CREATE TABLE partner_snapshot (
    id              UUID         PRIMARY KEY,
    partner_code    VARCHAR(40)  NOT NULL,
    partner_type    VARCHAR(50)  NOT NULL,
    status          VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    cached_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    master_version  BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT ck_partner_snapshot_partner_type
        CHECK (partner_type IN ('SUPPLIER', 'CARRIER', 'CUSTOMER', 'BOTH')),
    CONSTRAINT ck_partner_snapshot_status
        CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

CREATE INDEX idx_partner_snapshot_code ON partner_snapshot (partner_code);
