-- demand-planning-service initial schema (PostgreSQL).
-- ADR-027: wms→scm stock-replenishment loop, Phase 1 decisioning.
-- All tables carry tenant_id (always "scm" in v1).
-- TASK-SCM-BE-024.

-- ---------------------------------------------------------------------------
-- reorder_policy — scm-owned reorder decision per SKU (D4)
-- Distinct from the wms alert threshold.
-- ---------------------------------------------------------------------------
CREATE TABLE reorder_policy (
    tenant_id       VARCHAR(64)     NOT NULL,
    sku_code        VARCHAR(100)    NOT NULL,
    reorder_point   INT             NOT NULL,
    safety_stock    INT             NOT NULL,
    reorder_qty     INT             NOT NULL,
    version         INT             NOT NULL DEFAULT 0,
    updated_at      TIMESTAMPTZ     NOT NULL,
    CONSTRAINT pk_reorder_policy PRIMARY KEY (tenant_id, sku_code),
    CONSTRAINT ck_reorder_policy_point CHECK (reorder_point >= 0),
    CONSTRAINT ck_reorder_policy_safety CHECK (safety_stock >= 0),
    CONSTRAINT ck_reorder_policy_qty CHECK (reorder_qty > 0)
);

-- ---------------------------------------------------------------------------
-- sku_supplier_map — minimal SKU→supplier mapping (D3, stand-in for v2 supplier-service)
-- ---------------------------------------------------------------------------
CREATE TABLE sku_supplier_map (
    tenant_id           VARCHAR(64)     NOT NULL,
    sku_code            VARCHAR(100)    NOT NULL,
    supplier_id         UUID            NOT NULL,
    default_order_qty   INT             NOT NULL,
    lead_time_days      INT             NOT NULL,
    currency            VARCHAR(3)      NOT NULL,
    CONSTRAINT pk_sku_supplier_map PRIMARY KEY (tenant_id, sku_code),
    CONSTRAINT ck_sku_supplier_default_qty CHECK (default_order_qty > 0),
    CONSTRAINT ck_sku_supplier_lead_days CHECK (lead_time_days >= 0)
);

-- ---------------------------------------------------------------------------
-- reorder_suggestion — the aggregate (status machine SUGGESTED→APPROVED→MATERIALIZED/DISMISSED)
-- ---------------------------------------------------------------------------
CREATE TABLE reorder_suggestion (
    id                      UUID            PRIMARY KEY,
    sku_code                VARCHAR(100)    NOT NULL,
    warehouse_id            UUID            NOT NULL,
    supplier_id             UUID            NOT NULL,
    suggested_qty           INT             NOT NULL,
    status                  VARCHAR(20)     NOT NULL,
    source                  VARCHAR(10)     NOT NULL,
    trigger_event_id        UUID,
    trigger_available_qty   INT,
    materialized_po_id      UUID,
    tenant_id               VARCHAR(64)     NOT NULL,
    version                 INT             NOT NULL DEFAULT 0,
    created_at              TIMESTAMPTZ     NOT NULL,
    updated_at              TIMESTAMPTZ     NOT NULL,
    CONSTRAINT ck_reorder_suggestion_status
        CHECK (status IN ('SUGGESTED', 'APPROVED', 'MATERIALIZED', 'DISMISSED')),
    CONSTRAINT ck_reorder_suggestion_source
        CHECK (source IN ('ALERT', 'BATCH')),
    CONSTRAINT ck_reorder_suggestion_qty CHECK (suggested_qty > 0)
);

-- Operator filtering index
CREATE INDEX idx_reorder_suggestion_tenant_status
    ON reorder_suggestion (tenant_id, status);

-- Warehouse-dimension filtering
CREATE INDEX idx_reorder_suggestion_tenant_sku_warehouse
    ON reorder_suggestion (tenant_id, sku_code, warehouse_id);

-- D6: partial-unique open-suggestion guard.
-- Prevents a second SUGGESTED/APPROVED row for the same (tenant, sku, warehouse).
-- MATERIALIZED and DISMISSED are terminal and do NOT block a future re-suggestion.
CREATE UNIQUE INDEX uq_reorder_suggestion_open_guard
    ON reorder_suggestion (tenant_id, sku_code, warehouse_id)
    WHERE status IN ('SUGGESTED', 'APPROVED');

-- ---------------------------------------------------------------------------
-- dp_processed_events — consumer idempotency for the demand-planning consumer (T8)
-- Prefixed "dp_" to avoid collision with libs/java-messaging processed_events if
-- the module is ever deployed alongside a service that uses the shared outbox.
-- ---------------------------------------------------------------------------
CREATE TABLE dp_processed_events (
    event_id        UUID            PRIMARY KEY,
    tenant_id       VARCHAR(64)     NOT NULL,
    processed_at    TIMESTAMPTZ     NOT NULL,
    source_topic    VARCHAR(200)    NOT NULL
);
CREATE INDEX idx_dp_processed_events_tenant_processed
    ON dp_processed_events (tenant_id, processed_at);

-- ---------------------------------------------------------------------------
-- shedlock — distributed scheduler lock (batch-heavy trait, ShedLock provider)
-- ---------------------------------------------------------------------------
CREATE TABLE shedlock (
    name        VARCHAR(64)     NOT NULL,
    lock_until  TIMESTAMP       NOT NULL,
    locked_at   TIMESTAMP       NOT NULL,
    locked_by   VARCHAR(255)    NOT NULL,
    PRIMARY KEY (name)
);
