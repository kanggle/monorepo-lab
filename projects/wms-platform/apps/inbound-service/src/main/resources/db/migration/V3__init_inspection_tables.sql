-- Inspection aggregate (1:1 with ASN) plus InspectionLine and
-- InspectionDiscrepancy children.
-- Authoritative reference: specs/services/inbound-service/domain-model.md §2.
--
-- These tables exist for future tasks (TASK-BE-031). No domain code in
-- TASK-BE-029 touches them.

CREATE TABLE inspection (
    id            UUID         PRIMARY KEY,
    asn_id        UUID         NOT NULL UNIQUE REFERENCES asn (id) ON DELETE CASCADE,
    inspector_id  VARCHAR(100) NOT NULL,
    completed_at  TIMESTAMPTZ,
    notes         VARCHAR(1000),
    version       BIGINT       NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ  NOT NULL,
    created_by    VARCHAR(100) NOT NULL,
    updated_at    TIMESTAMPTZ  NOT NULL,
    updated_by    VARCHAR(100) NOT NULL
);

CREATE INDEX idx_inspection_asn ON inspection (asn_id);

CREATE TABLE inspection_line (
    id              UUID         PRIMARY KEY,
    inspection_id   UUID         NOT NULL REFERENCES inspection (id) ON DELETE CASCADE,
    asn_line_id     UUID         NOT NULL REFERENCES asn_line (id),
    sku_id          UUID         NOT NULL,
    lot_id          UUID,
    lot_no          VARCHAR(40),
    qty_passed      INTEGER      NOT NULL DEFAULT 0,
    qty_damaged     INTEGER      NOT NULL DEFAULT 0,
    qty_short       INTEGER      NOT NULL DEFAULT 0,
    CONSTRAINT uq_inspection_line_asn_line UNIQUE (inspection_id, asn_line_id),
    CONSTRAINT ck_inspection_line_qty_nonneg
        CHECK (qty_passed >= 0 AND qty_damaged >= 0 AND qty_short >= 0)
);

CREATE INDEX idx_inspection_line_inspection ON inspection_line (inspection_id);
CREATE INDEX idx_inspection_line_asn_line   ON inspection_line (asn_line_id);

CREATE TABLE inspection_discrepancy (
    id                UUID         PRIMARY KEY,
    inspection_id     UUID         NOT NULL REFERENCES inspection (id) ON DELETE CASCADE,
    asn_line_id       UUID         NOT NULL REFERENCES asn_line (id),
    discrepancy_type  VARCHAR(40)  NOT NULL,
    expected_qty      INTEGER      NOT NULL,
    actual_total_qty  INTEGER      NOT NULL,
    variance          INTEGER      NOT NULL,
    acknowledged      BOOLEAN      NOT NULL DEFAULT FALSE,
    acknowledged_by   VARCHAR(100),
    acknowledged_at   TIMESTAMPTZ,
    notes             VARCHAR(500),
    CONSTRAINT ck_inspection_discrepancy_type
        CHECK (discrepancy_type IN ('QUANTITY_MISMATCH', 'LOT_MISMATCH', 'DAMAGE_EXCESS'))
);

CREATE INDEX idx_inspection_discrepancy_inspection
    ON inspection_discrepancy (inspection_id);

CREATE INDEX idx_inspection_discrepancy_unacked
    ON inspection_discrepancy (inspection_id)
    WHERE acknowledged = FALSE;
