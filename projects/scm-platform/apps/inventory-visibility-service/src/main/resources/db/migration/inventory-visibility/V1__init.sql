-- scm-platform inventory-visibility-service initial schema (PostgreSQL).
-- Read-model: cross-node inventory snapshot from wms-platform events.
-- Multi-tenant: every table carries tenant_id; key indexes prefix tenant_id.
-- TASK-SCM-BE-003 § In Scope #4.

-- ---------------------------------------------------------------------------
-- inventory_nodes — registered source nodes (wms warehouse / supplier / 3PL)
-- ---------------------------------------------------------------------------
CREATE TABLE inventory_nodes (
    id                  VARCHAR(36)     PRIMARY KEY,
    tenant_id           VARCHAR(64)     NOT NULL,
    node_type           VARCHAR(30)     NOT NULL,
    node_external_id    VARCHAR(100)    NOT NULL,
    name                VARCHAR(200)    NOT NULL DEFAULT '',
    status              VARCHAR(20)     NOT NULL,
    contact_info        JSONB,
    created_at          TIMESTAMPTZ     NOT NULL,
    updated_at          TIMESTAMPTZ     NOT NULL,
    CONSTRAINT ck_inventory_nodes_type
        CHECK (node_type IN ('WMS_WAREHOUSE', 'SUPPLIER', 'THIRD_PARTY_LOGISTICS', 'IN_TRANSIT')),
    CONSTRAINT ck_inventory_nodes_status
        CHECK (status IN ('ACTIVE', 'SUSPENDED', 'DECOMMISSIONED')),
    CONSTRAINT uq_inventory_nodes_tenant_external
        UNIQUE (tenant_id, node_external_id)
);
CREATE INDEX idx_inventory_nodes_tenant_type_status
    ON inventory_nodes (tenant_id, node_type, status);

-- ---------------------------------------------------------------------------
-- inventory_snapshots — SKU × Node quantity read-model
-- ---------------------------------------------------------------------------
CREATE TABLE inventory_snapshots (
    id              VARCHAR(36)     PRIMARY KEY,
    node_id         VARCHAR(36)     NOT NULL,
    sku             VARCHAR(100)    NOT NULL,
    quantity        NUMERIC(18,3)   NOT NULL,
    tenant_id       VARCHAR(64)     NOT NULL,
    last_event_id   VARCHAR(36)     NOT NULL,
    last_event_at   TIMESTAMPTZ,
    version         INT             NOT NULL DEFAULT 0,
    updated_at      TIMESTAMPTZ     NOT NULL,
    CONSTRAINT fk_snapshots_node FOREIGN KEY (node_id) REFERENCES inventory_nodes (id),
    CONSTRAINT uq_snapshots_node_sku_tenant UNIQUE (node_id, sku, tenant_id)
);
CREATE INDEX idx_snapshots_tenant_sku
    ON inventory_snapshots (tenant_id, sku);
CREATE INDEX idx_snapshots_node_updated
    ON inventory_snapshots (node_id, updated_at DESC);
CREATE INDEX idx_snapshots_tenant_updated
    ON inventory_snapshots (tenant_id, updated_at DESC);

-- ---------------------------------------------------------------------------
-- node_staleness — per-node last-event tracking for staleness detection
-- ---------------------------------------------------------------------------
CREATE TABLE node_staleness (
    node_id             VARCHAR(36)     PRIMARY KEY,
    tenant_id           VARCHAR(64)     NOT NULL,
    last_event_at       TIMESTAMPTZ,
    last_event_id       VARCHAR(36),
    staleness_status    VARCHAR(20)     NOT NULL DEFAULT 'FRESH',
    last_checked_at     TIMESTAMPTZ,
    CONSTRAINT fk_node_staleness_node FOREIGN KEY (node_id) REFERENCES inventory_nodes (id),
    CONSTRAINT ck_node_staleness_status
        CHECK (staleness_status IN ('FRESH', 'STALE', 'UNREACHABLE'))
);
CREATE INDEX idx_node_staleness_tenant_status
    ON node_staleness (tenant_id, staleness_status);

-- ---------------------------------------------------------------------------
-- event_dedupe — eventId-based idempotency for Kafka consumers (T8)
-- ---------------------------------------------------------------------------
CREATE TABLE event_dedupe (
    event_id        VARCHAR(36)     PRIMARY KEY,
    tenant_id       VARCHAR(64)     NOT NULL,
    processed_at    TIMESTAMPTZ     NOT NULL,
    source_topic    VARCHAR(200)    NOT NULL
);
CREATE INDEX idx_event_dedupe_tenant_processed
    ON event_dedupe (tenant_id, processed_at);

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

-- ---------------------------------------------------------------------------
-- outbox — schema matches libs/java-messaging OutboxJpaEntity exactly.
-- Required because libs/java-messaging OutboxAutoConfiguration imports
-- OutboxJpaConfig (@EntityScan on OutboxJpaEntity + ProcessedEventJpaEntity),
-- which Hibernate ddl-auto=validate verifies on boot. Used by this service
-- to publish SNAPSHOT_STALE alerts (architecture.md § "messaging/ ← KafkaTemplate
-- alert publisher", § "publishes SNAPSHOT_STALE alerts").
-- Keep field names + types in sync with libs/java-messaging.
-- ---------------------------------------------------------------------------
CREATE TABLE outbox (
    id              BIGSERIAL    PRIMARY KEY,
    aggregate_type  VARCHAR(100) NOT NULL,
    aggregate_id    VARCHAR(255) NOT NULL,
    event_type      VARCHAR(100) NOT NULL,
    payload         TEXT         NOT NULL,
    created_at      TIMESTAMP    NOT NULL,
    published_at    TIMESTAMP,
    status          VARCHAR(20)  NOT NULL,
    CONSTRAINT ck_outbox_status CHECK (status IN ('PENDING', 'PUBLISHED', 'FAILED'))
);
CREATE INDEX idx_outbox_status_created_at
    ON outbox (status, created_at);

-- processed_events — required by libs/java-messaging ProcessedEventJpaEntity
-- (declared via OutboxJpaConfig @EntityScan).
CREATE TABLE processed_events (
    event_id        VARCHAR(100) PRIMARY KEY,
    event_type      VARCHAR(100) NOT NULL,
    processed_at    TIMESTAMP    NOT NULL
);
