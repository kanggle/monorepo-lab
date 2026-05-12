-- Partner master data table. Partners are independent aggregates (no parent
-- refs); see specs/services/master-service/domain-model.md §5 Partner and
-- rules/domains/wms.md for the authoritative invariants.
--
-- Invariants enforced at schema level:
--   - partner_code is globally unique (UNIQUE constraint).
--   - partner_type restricted to the enum values declared in PartnerType.java
--     (SUPPLIER / CUSTOMER / BOTH).
--   - status restricted to ACTIVE / INACTIVE (soft deactivation only — no
--     hard delete in v1 per domain-model.md § Common Aggregate Shape).
--   - all audit columns are NOT NULL (created_at/by + updated_at/by always set).
--
-- contact_email / contact_phone are operational B2B contact data per
-- PROJECT.md `data_sensitivity: internal` — not consumer PII. No additional
-- privacy treatment (encryption / masking) required at this layer.

CREATE TABLE partners (
    id              UUID         PRIMARY KEY,
    partner_code    VARCHAR(20)  NOT NULL,
    name            VARCHAR(200) NOT NULL,
    partner_type    VARCHAR(10)  NOT NULL,
    business_number VARCHAR(20),
    contact_name    VARCHAR(100),
    contact_email   VARCHAR(200),
    contact_phone   VARCHAR(30),
    address         VARCHAR(300),
    status          VARCHAR(20)  NOT NULL,
    version         BIGINT       NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ  NOT NULL,
    created_by      VARCHAR(100) NOT NULL,
    updated_at      TIMESTAMPTZ  NOT NULL,
    updated_by      VARCHAR(100) NOT NULL,
    CONSTRAINT uq_partners_partner_code UNIQUE (partner_code),
    CONSTRAINT ck_partners_status CHECK (status IN ('ACTIVE', 'INACTIVE')),
    CONSTRAINT ck_partners_partner_type CHECK (partner_type IN ('SUPPLIER', 'CUSTOMER', 'BOTH'))
);

-- partner_type filter is the most common drill-down for supplier-vs-customer
-- workflows.
CREATE INDEX idx_partners_partner_type ON partners (partner_type);

-- List queries typically filter by status and sort by updated_at desc
-- (per specs/contracts/http/master-service-api.md §5.3 default sort).
CREATE INDEX idx_partners_status_updated_at
    ON partners (status, updated_at DESC);
