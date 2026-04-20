-- Lot master data table. Lots belong to LOT-tracked SKUs (parent SKU invariant
-- enforced in the domain layer; the sku_id FK here is structural only).
--
-- See specs/services/master-service/domain-model.md §6 Lot and
-- specs/contracts/http/master-service-api.md §6 for the authoritative rules.
--
-- Invariants enforced at schema level:
--   - (sku_id, lot_no) is unique per parent SKU — uq_lots_sku_lotno. Two SKUs
--     may carry the same lot_no; only per-SKU uniqueness is guaranteed.
--   - lot_no trimmed length > 0 (domain factory trims on create; this is
--     defense in depth).
--   - expiry_date >= manufactured_date when both are present — ck_lots_date_pair.
--   - status restricted to the LotStatus enum values.
--   - supplier_partner_id is declared without FK — the Partner aggregate lives
--     in a separate aggregate lifecycle, and v1 performs only soft validation
--     (see specs/contracts/http/master-service-api.md §6.1). When the Partner
--     aggregate ships hard validation (BE-005 follow-up), a cleanup step is
--     expected before promoting this to a FK.
--
-- Partial index idx_lots_expiry_active: supports the daily expiration batch
-- (ACTIVE + expiry_date < today) with minimal index size — rows with NULL
-- expiry_date and non-ACTIVE rows are excluded, so the index stays hot.

CREATE TABLE lots (
    id                  UUID         PRIMARY KEY,
    sku_id              UUID         NOT NULL REFERENCES skus(id),
    lot_no              VARCHAR(40)  NOT NULL CHECK (char_length(trim(lot_no)) > 0),
    manufactured_date   DATE,
    expiry_date         DATE,
    supplier_partner_id UUID,
    status              VARCHAR(16)  NOT NULL CHECK (status IN ('ACTIVE', 'INACTIVE', 'EXPIRED')),
    version             BIGINT       NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ  NOT NULL,
    created_by          VARCHAR(255) NOT NULL,
    updated_at          TIMESTAMPTZ  NOT NULL,
    updated_by          VARCHAR(255) NOT NULL,
    CONSTRAINT uq_lots_sku_lotno UNIQUE (sku_id, lot_no),
    CONSTRAINT ck_lots_date_pair CHECK (
        manufactured_date IS NULL OR expiry_date IS NULL OR expiry_date >= manufactured_date
    )
);

-- Supports per-SKU list queries (§6.3) and existsBySkuIdAndStatus
-- (SKU deactivate reverse guard).
CREATE INDEX idx_lots_sku_status ON lots (sku_id, status);

-- Partial index for the scheduler's daily ACTIVE+expired-today scan.
-- Postgres and H2 2.x both accept the WHERE filter on CREATE INDEX.
CREATE INDEX idx_lots_expiry_active ON lots (expiry_date) WHERE status = 'ACTIVE';
