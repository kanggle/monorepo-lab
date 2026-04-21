CREATE TABLE shipping_status_history (
    id              BIGSERIAL       PRIMARY KEY,
    shipping_id     VARCHAR(255)    NOT NULL,
    status          VARCHAR(50)     NOT NULL,
    changed_at      TIMESTAMP       NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_status_history_shipping FOREIGN KEY (shipping_id) REFERENCES shippings (shipping_id)
);

CREATE INDEX idx_status_history_shipping_id ON shipping_status_history (shipping_id);
