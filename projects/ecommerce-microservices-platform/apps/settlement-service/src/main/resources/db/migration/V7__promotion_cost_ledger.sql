-- =============================================================================
-- TASK-BE-592 — the platform bears coupon discounts (owner decision 2026-09-15),
-- recorded in a separate append-only ledger.
--
-- commission_accrual is deliberately untouched: commission and seller_net stay on the
-- pre-discount line gross, so ck_commission_accrual_split, seller balances and the
-- period-close payout fold are exactly what they would be without the coupon.
-- Per order: Σ commission_accrual.gross_minor − Σ promotion_cost.amount_minor = captured.
-- =============================================================================

-- The OrderPlaced snapshot now caches the order's coupon discount. Existing rows (and
-- events without the additive fields) are orders without a coupon → 0 / NULL.
ALTER TABLE settlement_order_snapshot ADD COLUMN discount_minor BIGINT NOT NULL DEFAULT 0;
ALTER TABLE settlement_order_snapshot ADD COLUMN coupon_id VARCHAR(255);

-- Order-level promotion cost. COST = +discount at capture; REVERSAL = negative portion on a
-- refund, linked to its COST row. Immutable (F3) — a correction is a new REVERSAL row.
CREATE TABLE promotion_cost (
    cost_id           VARCHAR(255) NOT NULL,
    tenant_id         VARCHAR(255) NOT NULL,
    order_id          VARCHAR(255) NOT NULL,
    payment_id        VARCHAR(255) NOT NULL,
    coupon_id         VARCHAR(255),
    type              VARCHAR(16)  NOT NULL,
    amount_minor      BIGINT       NOT NULL,
    reverses_cost_id  VARCHAR(255),
    occurred_at       TIMESTAMP    NOT NULL,
    CONSTRAINT pk_promotion_cost PRIMARY KEY (cost_id),
    CONSTRAINT ck_promotion_cost_type CHECK (type IN ('COST', 'REVERSAL')),
    CONSTRAINT ck_promotion_cost_sign CHECK (
        (type = 'COST' AND amount_minor > 0 AND reverses_cost_id IS NULL)
        OR (type = 'REVERSAL' AND amount_minor < 0 AND reverses_cost_id IS NOT NULL))
);

CREATE INDEX idx_promotion_cost_order ON promotion_cost (order_id);
CREATE INDEX idx_promotion_cost_tenant_occurred_at ON promotion_cost (tenant_id, occurred_at);
