-- Location master data table. Locations are nested under warehouses + zones;
-- see specs/services/master-service/domain-model.md §3 Location and
-- rules/domains/wms.md (W3, W6) for the authoritative invariants.
--
-- Invariants enforced at schema level:
--   - location_code is GLOBALLY unique (W3) — not scoped to warehouse or zone.
--     Constraint name: uq_locations_location_code
--   - warehouse_id references warehouses(id) — denormalized onto the Location
--     for fast scoping; parent-zone-matches-warehouse consistency is enforced
--     at the application layer (leak-safe: mismatch surfaces as ZONE_NOT_FOUND)
--   - zone_id references zones(id)
--   - status restricted to ACTIVE / INACTIVE (soft deactivation only)
--   - location_type restricted to the enum values declared in LocationType.java
--   - all audit columns are NOT NULL

CREATE TABLE locations (
    id              UUID         PRIMARY KEY,
    warehouse_id    UUID         NOT NULL,
    zone_id         UUID         NOT NULL,
    location_code   VARCHAR(40)  NOT NULL,
    aisle           VARCHAR(10),
    rack            VARCHAR(10),
    level           VARCHAR(10),
    bin             VARCHAR(10),
    location_type   VARCHAR(20)  NOT NULL,
    capacity_units  INTEGER,
    status          VARCHAR(20)  NOT NULL,
    version         BIGINT       NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ  NOT NULL,
    created_by      VARCHAR(100) NOT NULL,
    updated_at      TIMESTAMPTZ  NOT NULL,
    updated_by      VARCHAR(100) NOT NULL,
    CONSTRAINT fk_locations_warehouse
        FOREIGN KEY (warehouse_id) REFERENCES warehouses (id),
    CONSTRAINT fk_locations_zone
        FOREIGN KEY (zone_id) REFERENCES zones (id),
    CONSTRAINT uq_locations_location_code
        UNIQUE (location_code),
    CONSTRAINT ck_locations_status
        CHECK (status IN ('ACTIVE', 'INACTIVE')),
    CONSTRAINT ck_locations_location_type
        CHECK (location_type IN (
            'STORAGE', 'STAGING_INBOUND', 'STAGING_OUTBOUND', 'DAMAGED', 'QUARANTINE')),
    CONSTRAINT ck_locations_capacity_units_positive
        CHECK (capacity_units IS NULL OR capacity_units >= 1)
);

-- List queries commonly filter by parent scoping (warehouse_id, status) or
-- (zone_id, status) — index both access patterns.
CREATE INDEX idx_locations_warehouse_status
    ON locations (warehouse_id, status);
CREATE INDEX idx_locations_zone_status
    ON locations (zone_id, status);
