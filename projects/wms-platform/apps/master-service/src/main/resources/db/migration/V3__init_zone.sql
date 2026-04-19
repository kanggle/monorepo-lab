-- Zone master data table. Zones are nested under warehouses; see
-- specs/services/master-service/domain-model.md §2 Zone and
-- rules/domains/wms.md for the authoritative invariants.
--
-- Note on version numbering: V2 is reserved for the shared outbox schema
-- (V2__init_outbox.sql), so this Zone migration is V3 rather than V2 despite
-- TASK-BE-002's original wording.
--
-- Invariants enforced at schema level:
--   - (warehouse_id, zone_code) is unique (compound, NOT zone_code alone)
--   - warehouse_id references warehouses(id) — local FK is acceptable because
--     both aggregates live in the master-service DB; cross-aggregate consistency
--     is otherwise enforced at the application layer
--   - status restricted to ACTIVE / INACTIVE (soft deactivation only)
--   - zone_type restricted to the enum values declared in ZoneType.java
--   - all audit columns are NOT NULL (created_at/by + updated_at/by always set)

CREATE TABLE zones (
    id              UUID         PRIMARY KEY,
    warehouse_id    UUID         NOT NULL,
    zone_code       VARCHAR(20)  NOT NULL,
    name            VARCHAR(100) NOT NULL,
    zone_type       VARCHAR(20)  NOT NULL,
    status          VARCHAR(20)  NOT NULL,
    version         BIGINT       NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ  NOT NULL,
    created_by      VARCHAR(100) NOT NULL,
    updated_at      TIMESTAMPTZ  NOT NULL,
    updated_by      VARCHAR(100) NOT NULL,
    CONSTRAINT fk_zones_warehouse
        FOREIGN KEY (warehouse_id) REFERENCES warehouses (id),
    CONSTRAINT uq_zones_warehouse_code
        UNIQUE (warehouse_id, zone_code),
    CONSTRAINT ck_zones_status
        CHECK (status IN ('ACTIVE', 'INACTIVE')),
    CONSTRAINT ck_zones_zone_type
        CHECK (zone_type IN ('AMBIENT', 'CHILLED', 'FROZEN', 'RETURNS', 'BULK', 'PICK'))
);

-- List queries are filtered by warehouse_id + status and sorted by updated_at
-- desc (per specs/contracts/http/master-service-api.md §2.3 default sort).
CREATE INDEX idx_zones_warehouse_status_updated_at
    ON zones (warehouse_id, status, updated_at DESC);
