-- scm-platform procurement-service initial schema (PostgreSQL).
-- Hexagonal aggregate roots: PurchaseOrder, AdvanceShipmentNotice, Supplier.
-- Multi-tenant: every table carries tenant_id; key indexes prefix tenant_id.
-- TASK-SCM-BE-002 § In Scope #4.

-- ---------------------------------------------------------------------------
-- suppliers — v1 internal master (v2 will migrate to supplier-service)
-- ---------------------------------------------------------------------------
CREATE TABLE suppliers (
    id                      VARCHAR(36)    PRIMARY KEY,
    tenant_id               VARCHAR(64)    NOT NULL,
    name                    VARCHAR(200)   NOT NULL,
    status                  VARCHAR(20)    NOT NULL,
    contract_started_at     TIMESTAMPTZ,
    contract_expires_at     TIMESTAMPTZ,
    contact_info            JSONB,
    created_at              TIMESTAMPTZ    NOT NULL,
    updated_at              TIMESTAMPTZ    NOT NULL,
    version                 BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT ck_suppliers_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'CONTRACT_EXPIRED'))
);
CREATE INDEX idx_suppliers_tenant_status
    ON suppliers (tenant_id, status);

-- ---------------------------------------------------------------------------
-- supplier_credentials — column-level AES-GCM encryption (S6).
-- ---------------------------------------------------------------------------
CREATE TABLE supplier_credentials (
    supplier_id             VARCHAR(36)    PRIMARY KEY,
    tenant_id               VARCHAR(64)    NOT NULL,
    credential_type         VARCHAR(40)    NOT NULL,
    encrypted_payload       BYTEA          NOT NULL,
    encryption_key_id       VARCHAR(40)    NOT NULL,
    rotated_at              TIMESTAMPTZ    NOT NULL,
    CONSTRAINT fk_supplier_credentials_supplier
        FOREIGN KEY (supplier_id) REFERENCES suppliers (id)
);

-- ---------------------------------------------------------------------------
-- purchase_orders — aggregate root, state machine
-- ---------------------------------------------------------------------------
CREATE TABLE purchase_orders (
    id                      VARCHAR(36)    PRIMARY KEY,
    tenant_id               VARCHAR(64)    NOT NULL,
    po_number               VARCHAR(40)    NOT NULL,
    supplier_id             VARCHAR(36)    NOT NULL,
    buyer_account_id        VARCHAR(36)    NOT NULL,
    status                  VARCHAR(30)    NOT NULL,
    total_amount            NUMERIC(18,2)  NOT NULL,
    currency                CHAR(3)        NOT NULL,
    submitted_at            TIMESTAMPTZ,
    acknowledged_at         TIMESTAMPTZ,
    confirmed_at            TIMESTAMPTZ,
    canceled_at             TIMESTAMPTZ,
    cancellation_reason     VARCHAR(200),
    created_at              TIMESTAMPTZ    NOT NULL,
    updated_at              TIMESTAMPTZ    NOT NULL,
    version                 BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT uq_purchase_orders_tenant_po_number UNIQUE (tenant_id, po_number),
    CONSTRAINT ck_purchase_orders_status CHECK (status IN (
        'DRAFT', 'SUBMITTED', 'ACKNOWLEDGED', 'CONFIRMED',
        'PARTIALLY_RECEIVED', 'RECEIVED', 'SETTLED', 'CLOSED', 'CANCELED'
    ))
);
CREATE INDEX idx_po_tenant_status_created
    ON purchase_orders (tenant_id, status, created_at DESC);
CREATE INDEX idx_po_tenant_supplier_status
    ON purchase_orders (tenant_id, supplier_id, status);

-- ---------------------------------------------------------------------------
-- purchase_order_lines — child entity of PurchaseOrder
-- ---------------------------------------------------------------------------
CREATE TABLE purchase_order_lines (
    id                      VARCHAR(36)    PRIMARY KEY,
    po_id                   VARCHAR(36)    NOT NULL,
    tenant_id               VARCHAR(64)    NOT NULL,
    line_no                 INT            NOT NULL,
    sku                     VARCHAR(100)   NOT NULL,
    supplier_sku            VARCHAR(100),
    quantity                NUMERIC(18,4)  NOT NULL,
    unit_price              NUMERIC(18,2)  NOT NULL,
    received_quantity       NUMERIC(18,4)  NOT NULL DEFAULT 0,
    CONSTRAINT fk_pol_po FOREIGN KEY (po_id) REFERENCES purchase_orders (id),
    CONSTRAINT uq_pol_po_line UNIQUE (po_id, line_no),
    CONSTRAINT ck_pol_quantity_positive CHECK (quantity > 0),
    CONSTRAINT ck_pol_unit_price_non_negative CHECK (unit_price >= 0)
);
CREATE INDEX idx_pol_tenant_po
    ON purchase_order_lines (tenant_id, po_id);

-- ---------------------------------------------------------------------------
-- po_status_history — append-only audit (S7) for PO state transitions
-- ---------------------------------------------------------------------------
CREATE TABLE po_status_history (
    id                      BIGSERIAL      PRIMARY KEY,
    po_id                   VARCHAR(36)    NOT NULL,
    tenant_id               VARCHAR(64)    NOT NULL,
    from_status             VARCHAR(30)    NOT NULL,
    to_status               VARCHAR(30)    NOT NULL,
    actor_account_id        VARCHAR(36),
    actor_type              VARCHAR(20)    NOT NULL,
    reason                  VARCHAR(200),
    occurred_at             TIMESTAMPTZ    NOT NULL,
    CONSTRAINT ck_psh_actor_type CHECK (actor_type IN ('BUYER', 'OPERATOR', 'SUPPLIER', 'SYSTEM'))
);
CREATE INDEX idx_psh_tenant_po_occurred
    ON po_status_history (tenant_id, po_id, occurred_at DESC);

-- ---------------------------------------------------------------------------
-- advance_shipment_notices — supplier-issued ASN
-- ---------------------------------------------------------------------------
CREATE TABLE advance_shipment_notices (
    id                      VARCHAR(36)    PRIMARY KEY,
    tenant_id               VARCHAR(64)    NOT NULL,
    po_id                   VARCHAR(36)    NOT NULL,
    supplier_asn_ref        VARCHAR(100)   NOT NULL,
    expected_arrival_at     TIMESTAMPTZ    NOT NULL,
    received_at             TIMESTAMPTZ,
    created_at              TIMESTAMPTZ    NOT NULL,
    version                 BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT fk_asn_po FOREIGN KEY (po_id) REFERENCES purchase_orders (id),
    -- S2 idempotency: same supplier_asn_ref under the same tenant is a duplicate.
    CONSTRAINT uq_asn_tenant_supplier_ref UNIQUE (tenant_id, supplier_asn_ref)
);
CREATE INDEX idx_asn_tenant_po_expected
    ON advance_shipment_notices (tenant_id, po_id, expected_arrival_at);

-- ---------------------------------------------------------------------------
-- asn_lines — child entity of AdvanceShipmentNotice
-- ---------------------------------------------------------------------------
CREATE TABLE asn_lines (
    id                      VARCHAR(36)    PRIMARY KEY,
    asn_id                  VARCHAR(36)    NOT NULL,
    tenant_id               VARCHAR(64)    NOT NULL,
    po_line_id              VARCHAR(36)    NOT NULL,
    quantity_shipped        NUMERIC(18,4)  NOT NULL,
    quantity_received       NUMERIC(18,4),
    CONSTRAINT fk_asn_line_asn FOREIGN KEY (asn_id) REFERENCES advance_shipment_notices (id),
    CONSTRAINT fk_asn_line_pol FOREIGN KEY (po_line_id) REFERENCES purchase_order_lines (id),
    CONSTRAINT ck_asn_line_qty_positive CHECK (quantity_shipped > 0)
);
CREATE INDEX idx_asn_lines_asn ON asn_lines (asn_id);
CREATE INDEX idx_asn_lines_tenant_pol ON asn_lines (tenant_id, po_line_id);

-- ---------------------------------------------------------------------------
-- audit_log — application-level audit trail (S7) for actor + before/after
-- ---------------------------------------------------------------------------
CREATE TABLE audit_log (
    id                      BIGSERIAL      PRIMARY KEY,
    tenant_id               VARCHAR(64)    NOT NULL,
    aggregate_type          VARCHAR(40)    NOT NULL,
    aggregate_id            VARCHAR(36)    NOT NULL,
    action                  VARCHAR(40)    NOT NULL,
    actor_account_id        VARCHAR(36),
    actor_type              VARCHAR(20)    NOT NULL,
    before_state            JSONB,
    after_state             JSONB,
    occurred_at             TIMESTAMPTZ    NOT NULL
);
CREATE INDEX idx_audit_aggregate_occurred
    ON audit_log (aggregate_type, aggregate_id, occurred_at DESC);
CREATE INDEX idx_audit_tenant_occurred
    ON audit_log (tenant_id, occurred_at DESC);

-- ---------------------------------------------------------------------------
-- outbox — schema matches libs/java-messaging OutboxJpaEntity exactly.
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

-- ---------------------------------------------------------------------------
-- idempotency_keys — S2 idempotency cache fallback (Redis is the primary).
-- Service-level dedupe table keyed on (clientId, endpoint, idempotencyKey)
-- per rules/traits/transactional.md T1. Used as a tertiary persistent layer
-- when Redis is offline (Failure Scenario D — fail-CLOSED behaviour).
-- ---------------------------------------------------------------------------
CREATE TABLE idempotency_keys (
    idempotency_key         VARCHAR(80)    NOT NULL,
    endpoint                VARCHAR(120)   NOT NULL,
    tenant_id               VARCHAR(64)    NOT NULL,
    payload_hash            VARCHAR(64)    NOT NULL,
    response_status         INT            NOT NULL,
    response_body           TEXT,
    created_at              TIMESTAMPTZ    NOT NULL,
    expires_at              TIMESTAMPTZ    NOT NULL,
    PRIMARY KEY (idempotency_key, endpoint, tenant_id)
);
CREATE INDEX idx_idempotency_expires
    ON idempotency_keys (expires_at);
