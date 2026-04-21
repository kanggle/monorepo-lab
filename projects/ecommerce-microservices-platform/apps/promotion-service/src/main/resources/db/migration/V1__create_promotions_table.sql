CREATE TABLE promotions (
    promotion_id        VARCHAR(36)     NOT NULL,
    name                VARCHAR(255)    NOT NULL,
    description         TEXT,
    discount_type       VARCHAR(20)     NOT NULL,
    discount_value      BIGINT          NOT NULL,
    max_discount_amount BIGINT          NOT NULL DEFAULT 0,
    max_issuance_count  INT             NOT NULL,
    issued_count        INT             NOT NULL DEFAULT 0,
    start_date          TIMESTAMP       NOT NULL,
    end_date            TIMESTAMP       NOT NULL,
    created_at          TIMESTAMP       NOT NULL,
    updated_at          TIMESTAMP       NOT NULL,
    CONSTRAINT pk_promotions PRIMARY KEY (promotion_id)
);

CREATE INDEX idx_promotions_start_end ON promotions (start_date, end_date);
