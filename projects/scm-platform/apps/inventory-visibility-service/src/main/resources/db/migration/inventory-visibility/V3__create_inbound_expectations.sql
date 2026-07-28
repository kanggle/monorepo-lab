-- ADR-MONO-055 §D4 / TASK-SCM-BE-049 — the scm-internal 3PL inbound-expectation sink.
--
-- A THIRD_PARTY_LOGISTICS-addressed replenishment PO is HONOURED (ADR-MONO-054 §D3):
-- procurement-service publishes scm.procurement.inbound-expected.third-party.v1 on
-- CONFIRMED, and this service records a lightweight expected-inbound row per PO line
-- against the 3PL node (the context that already owns the node + its staleness). It is
-- NOT sent to wms and NOT DLT'd (wms does not operate a 3PL's WMS — ADR-MONO-050 §D4).
--
-- Read-model-shaped: a two-value status (OPEN → SATISFIED) reconciled by a later 3PL
-- observation (POST /nodes/{nodeId}/observed-stock, TASK-SCM-BE-047). No state machine.
--
-- Idempotent on the PO reference: UNIQUE (tenant_id, source_po_number, sku, node_id) so a
-- re-confirmed / replayed PO does not double-record (ADR-MONO-055 §D4).
CREATE TABLE inbound_expectations (
    id                  VARCHAR(36)     PRIMARY KEY,
    tenant_id           VARCHAR(64)     NOT NULL,
    node_id             VARCHAR(36)     NOT NULL,
    sku                 VARCHAR(100)    NOT NULL,
    expected_quantity   NUMERIC(18,3)   NOT NULL,
    source_po_id        VARCHAR(36)     NOT NULL,
    source_po_number    VARCHAR(40)     NOT NULL,
    expected_at         DATE,
    status              VARCHAR(20)     NOT NULL DEFAULT 'OPEN',
    created_at          TIMESTAMPTZ     NOT NULL,
    updated_at          TIMESTAMPTZ     NOT NULL,
    satisfied_at        TIMESTAMPTZ,
    CONSTRAINT fk_inbound_expectations_node FOREIGN KEY (node_id) REFERENCES inventory_nodes (id),
    CONSTRAINT ck_inbound_expectations_status CHECK (status IN ('OPEN', 'SATISFIED')),
    CONSTRAINT uq_inbound_expectations_po_sku_node
        UNIQUE (tenant_id, source_po_number, sku, node_id)
);

-- Reconciliation lookup: OPEN expectations for a (node, sku) pair when an observation lands.
CREATE INDEX idx_inbound_expectations_node_sku_status
    ON inbound_expectations (node_id, sku, status);

-- Operational visibility: open (unmet, aging) expectations per tenant.
CREATE INDEX idx_inbound_expectations_tenant_status
    ON inbound_expectations (tenant_id, status);

COMMENT ON TABLE inbound_expectations IS
    'scm-internal 3PL inbound-expectation sink (ADR-MONO-055 D4 / TASK-SCM-BE-049): stock we expect to arrive at a THIRD_PARTY_LOGISTICS node, reconciled by 3PL observation.';
