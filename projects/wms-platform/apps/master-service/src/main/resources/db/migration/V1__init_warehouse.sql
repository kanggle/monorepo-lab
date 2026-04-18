-- Warehouse master data table.
-- See specs/services/master-service/domain-model.md §1 Warehouse and
-- rules/domains/wms.md for the authoritative invariants.
--
-- Invariants enforced at schema level:
--   - warehouse_code is globally unique (W3 derived)
--   - status restricted to ACTIVE / INACTIVE (soft deactivation only; no hard delete)
--   - all audit columns are NOT NULL (created_at/by + updated_at/by always set by domain)
--
-- Cross-aggregate referential checks (Zone → Warehouse, etc.) live in the domain
-- layer of subsequent services; this schema does not express them yet.

CREATE TABLE warehouses (
    id              UUID         PRIMARY KEY,
    warehouse_code  VARCHAR(10)  NOT NULL,
    name            VARCHAR(100) NOT NULL,
    address         VARCHAR(200),
    timezone        VARCHAR(40)  NOT NULL,
    status          VARCHAR(20)  NOT NULL,
    version         BIGINT       NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ  NOT NULL,
    created_by      VARCHAR(100) NOT NULL,
    updated_at      TIMESTAMPTZ  NOT NULL,
    updated_by      VARCHAR(100) NOT NULL,
    CONSTRAINT uq_warehouses_warehouse_code UNIQUE (warehouse_code),
    CONSTRAINT ck_warehouses_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

-- List queries are typically filtered by status and sorted by updated_at desc
-- (per specs/contracts/http/master-service-api.md default sort).
CREATE INDEX idx_warehouses_status_updated_at
    ON warehouses (status, updated_at DESC);
