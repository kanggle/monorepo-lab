-- =============================================================================
-- settlement-service V1 — marketplace seller settlement / commission
-- (ADR-MONO-030 Step 4 facet b, TASK-BE-365)
--
-- 4 tables: seller_commission_rate (per-seller override) · settlement_order_snapshot
-- (+ lines, the OrderPlaced cache) · commission_accrual (append-only ledger) ·
-- processed_event (dedupe). money = BIGINT minor units (KRW), rate = INT bps,
-- ids = VARCHAR (UUID / composite). tenant_id NOT NULL on every settlement row.
-- =============================================================================

-- Per-seller commission-rate override, keyed by (tenant_id, seller_id).
-- id = tenant_id || ':' || seller_id (surrogate). Missing row → platform default.
CREATE TABLE seller_commission_rate (
    id          VARCHAR(512) NOT NULL,
    tenant_id   VARCHAR(255) NOT NULL,
    seller_id   VARCHAR(255) NOT NULL,
    rate_bps    INT          NOT NULL,
    CONSTRAINT pk_seller_commission_rate PRIMARY KEY (id),
    CONSTRAINT uq_seller_commission_rate_tenant_seller UNIQUE (tenant_id, seller_id),
    CONSTRAINT ck_seller_commission_rate_bps CHECK (rate_bps >= 0 AND rate_bps <= 10000)
);

-- Cached OrderPlaced snapshot header. tenant_id from the event envelope is the
-- ONLY authoritative tenant source for settlement (AC-7). Idempotent on order_id.
CREATE TABLE settlement_order_snapshot (
    order_id    VARCHAR(255) NOT NULL,
    tenant_id   VARCHAR(255) NOT NULL,
    CONSTRAINT pk_settlement_order_snapshot PRIMARY KEY (order_id)
);

-- Per-line seller attribution + gross (unitPrice × quantity).
CREATE TABLE settlement_order_snapshot_line (
    id          BIGSERIAL    NOT NULL,
    order_id    VARCHAR(255) NOT NULL,
    line_index  INT          NOT NULL,
    seller_id   VARCHAR(255) NOT NULL,
    gross_minor BIGINT       NOT NULL,
    CONSTRAINT pk_settlement_order_snapshot_line PRIMARY KEY (id),
    CONSTRAINT fk_snapshot_line_order FOREIGN KEY (order_id)
        REFERENCES settlement_order_snapshot (order_id) ON DELETE CASCADE
);

CREATE INDEX idx_snapshot_line_order ON settlement_order_snapshot_line (order_id);

-- Append-only commission ledger. One row per (order line × event). type ∈ {ACCRUAL,
-- REVERSAL}; REVERSAL carries the negation of its accrual. Immutable (F3).
CREATE TABLE commission_accrual (
    accrual_id        VARCHAR(255) NOT NULL,
    tenant_id         VARCHAR(255) NOT NULL,
    order_id          VARCHAR(255) NOT NULL,
    payment_id        VARCHAR(255) NOT NULL,
    seller_id         VARCHAR(255) NOT NULL,
    type              VARCHAR(16)  NOT NULL,
    gross_minor       BIGINT       NOT NULL,
    rate_bps          INT          NOT NULL,
    commission_minor  BIGINT       NOT NULL,
    seller_net_minor  BIGINT       NOT NULL,
    occurred_at       TIMESTAMP    NOT NULL,
    CONSTRAINT pk_commission_accrual PRIMARY KEY (accrual_id),
    CONSTRAINT ck_commission_accrual_type CHECK (type IN ('ACCRUAL', 'REVERSAL')),
    CONSTRAINT ck_commission_accrual_split CHECK (commission_minor + seller_net_minor = gross_minor)
);

CREATE INDEX idx_commission_accrual_tenant_seller ON commission_accrual (tenant_id, seller_id);
CREATE INDEX idx_commission_accrual_order ON commission_accrual (order_id);
CREATE INDEX idx_commission_accrual_order_payment_type ON commission_accrual (order_id, payment_id, type);
CREATE INDEX idx_commission_accrual_occurred_at ON commission_accrual (occurred_at);

-- Dedupe (locally owned — terminal consumer, no libs outbox dependency).
CREATE TABLE processed_event (
    event_id     VARCHAR(255) NOT NULL,
    event_type   VARCHAR(255) NOT NULL,
    processed_at TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_processed_event PRIMARY KEY (event_id)
);

CREATE INDEX idx_processed_event_processed_at ON processed_event (processed_at);
