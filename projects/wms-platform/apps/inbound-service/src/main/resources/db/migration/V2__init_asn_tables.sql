-- ASN aggregate root + AsnLine child.
-- Authoritative reference: specs/services/inbound-service/domain-model.md §1.
--
-- ASN status enum derives from state-machines/asn-status.md:
--   CREATED, INSPECTING, INSPECTED, IN_PUTAWAY, PUTAWAY_DONE, CLOSED, CANCELLED.
--
-- These tables exist for future tasks (TASK-BE-030+) — no domain code in
-- TASK-BE-029 touches them. They land here so future tasks don't need to
-- bundle migrations with feature work.

CREATE TABLE asn (
    id                    UUID         PRIMARY KEY,
    asn_no                VARCHAR(40)  NOT NULL,
    source                VARCHAR(20)  NOT NULL,
    supplier_partner_id   UUID         NOT NULL,
    warehouse_id          UUID         NOT NULL,
    expected_arrive_date  DATE,
    notes                 VARCHAR(1000),
    status                VARCHAR(20)  NOT NULL,
    version               BIGINT       NOT NULL DEFAULT 0,
    created_at            TIMESTAMPTZ  NOT NULL,
    created_by            VARCHAR(100) NOT NULL,
    updated_at            TIMESTAMPTZ  NOT NULL,
    updated_by            VARCHAR(100) NOT NULL,
    CONSTRAINT uq_asn_no UNIQUE (asn_no),
    CONSTRAINT ck_asn_source
        CHECK (source IN ('MANUAL', 'WEBHOOK_ERP')),
    CONSTRAINT ck_asn_status
        CHECK (status IN (
            'CREATED', 'INSPECTING', 'INSPECTED',
            'IN_PUTAWAY', 'PUTAWAY_DONE', 'CLOSED', 'CANCELLED'
        ))
);

CREATE INDEX idx_asn_warehouse ON asn (warehouse_id);
CREATE INDEX idx_asn_supplier  ON asn (supplier_partner_id);
CREATE INDEX idx_asn_status    ON asn (status);

CREATE TABLE asn_line (
    id            UUID         PRIMARY KEY,
    asn_id        UUID         NOT NULL REFERENCES asn (id) ON DELETE CASCADE,
    line_no       INTEGER      NOT NULL,
    sku_id        UUID         NOT NULL,
    lot_id        UUID,
    expected_qty  INTEGER      NOT NULL,
    CONSTRAINT uq_asn_line_no UNIQUE (asn_id, line_no),
    CONSTRAINT ck_asn_line_no_positive CHECK (line_no >= 1),
    CONSTRAINT ck_asn_line_qty_positive CHECK (expected_qty > 0)
);

CREATE INDEX idx_asn_line_asn ON asn_line (asn_id);
CREATE INDEX idx_asn_line_sku ON asn_line (sku_id);
