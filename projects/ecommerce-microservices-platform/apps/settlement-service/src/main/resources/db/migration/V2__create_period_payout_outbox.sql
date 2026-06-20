-- =============================================================================
-- settlement-service V2 — period close + simulated payout increment
-- (ADR-MONO-030 Step 4 facet b continuation, TASK-BE-415)
--
-- Adds the OPEN→CLOSED period aggregate (settlement_period), its per-seller payout
-- rows (seller_payout, PENDING at close), and introduces the standard ecommerce
-- transactional outbox (settlement-service's first published event,
-- settlement.period.closed.v1). The outbox DDL is byte-identical to
-- order-service / payment-service. money = BIGINT minor units (KRW). tenant_id
-- NOT NULL on every period/payout row (M1). The accrual ledger (V1) is untouched —
-- period close only READS it (F3 immutability preserved across the close).
-- =============================================================================

-- settlement_period — the OPEN→CLOSED aggregate. period_from/period_to are the
-- half-open window edges (period_from inclusive, period_to exclusive). The
-- non-empty-window invariant is enforced both in the domain (open: from < to) and
-- here (CHECK). closed_at/closed_by/seller_count are stamped at close (NULL while
-- OPEN). version supports optimistic-lock parity.
CREATE TABLE settlement_period (
    period_id     VARCHAR(255) NOT NULL,
    tenant_id     VARCHAR(255) NOT NULL,
    period_from   TIMESTAMP    NOT NULL,
    period_to     TIMESTAMP    NOT NULL,
    status        VARCHAR(10)  NOT NULL,
    closed_at     TIMESTAMP,
    closed_by     VARCHAR(255),
    seller_count  INT,
    version       BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT pk_settlement_period PRIMARY KEY (period_id),
    CONSTRAINT ck_settlement_period_status CHECK (status IN ('OPEN', 'CLOSED')),
    CONSTRAINT ck_settlement_period_window CHECK (period_from < period_to)
);

CREATE INDEX idx_settlement_period_tenant_window
    ON settlement_period (tenant_id, period_from, period_to);

-- seller_payout — one row per (period, seller) folded at close from the in-window
-- accruals. status ∈ {PENDING, PAID, FAILED}; PENDING at close, the execution
-- transition (PENDING→PAID|FAILED) is TASK-BE-416. payout_reference is NULL while
-- PENDING, set when PAID. UNIQUE(period_id, seller_id) guarantees one payout per
-- seller in a period (the close runs exactly once via the OPEN→CLOSED guard).
CREATE TABLE seller_payout (
    payout_id          VARCHAR(255) NOT NULL,
    period_id          VARCHAR(255) NOT NULL,
    tenant_id          VARCHAR(255) NOT NULL,
    seller_id          VARCHAR(255) NOT NULL,
    payable_net_minor  BIGINT       NOT NULL,
    commission_minor   BIGINT       NOT NULL,
    accrual_count      INT          NOT NULL,
    status             VARCHAR(16)  NOT NULL,
    payout_reference   VARCHAR(255),
    paid_at            TIMESTAMP,
    version            BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT pk_seller_payout PRIMARY KEY (payout_id),
    CONSTRAINT fk_seller_payout_period FOREIGN KEY (period_id)
        REFERENCES settlement_period (period_id),
    CONSTRAINT uq_seller_payout_period_seller UNIQUE (period_id, seller_id),
    CONSTRAINT ck_seller_payout_status CHECK (status IN ('PENDING', 'PAID', 'FAILED'))
);

CREATE INDEX idx_seller_payout_period ON seller_payout (period_id);
CREATE INDEX idx_seller_payout_tenant_seller ON seller_payout (tenant_id, seller_id);

-- Standard ecommerce transactional outbox (byte-identical to order/payment-service).
-- Introduced this increment for settlement.period.closed.v1 — appended in the same
-- @Transactional boundary as the period close + payout-row inserts.
CREATE TABLE outbox (
    id              BIGSERIAL       PRIMARY KEY,
    aggregate_type  VARCHAR(100)    NOT NULL,
    aggregate_id    VARCHAR(255)    NOT NULL,
    event_type      VARCHAR(100)    NOT NULL,
    payload         TEXT            NOT NULL,
    created_at      TIMESTAMP       NOT NULL DEFAULT NOW(),
    published_at    TIMESTAMP,
    status          VARCHAR(20)     NOT NULL DEFAULT 'PENDING'
);

CREATE INDEX idx_outbox_status_created ON outbox (status, created_at);
