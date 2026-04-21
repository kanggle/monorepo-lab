CREATE TABLE payments (
    payment_id   VARCHAR(36)  NOT NULL PRIMARY KEY,
    order_id     VARCHAR(36)  NOT NULL UNIQUE,
    user_id      VARCHAR(255) NOT NULL,
    amount       BIGINT       NOT NULL,
    status       VARCHAR(20)  NOT NULL,
    created_at   TIMESTAMP    NOT NULL,
    paid_at      TIMESTAMP,
    refunded_at  TIMESTAMP
);

CREATE INDEX idx_payments_order_id ON payments(order_id);
CREATE INDEX idx_payments_user_id  ON payments(user_id);
