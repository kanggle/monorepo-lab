-- Local read-model snapshots fed by master.* events. This service never writes
-- to these tables from REST or use-case paths — only the master consumers do.
--
-- Authoritative reference: specs/services/inventory-service/domain-model.md §8
--
-- Out-of-order delivery handling is enforced at upsert time by checking
-- master_version (see consumer code). The schema only declares the shape.

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

CREATE TABLE sku_snapshot (
    id              UUID         PRIMARY KEY,
    sku_code        VARCHAR(40)  NOT NULL,
    tracking_type   VARCHAR(10)  NOT NULL,
    base_uom        VARCHAR(10)  NOT NULL,
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
