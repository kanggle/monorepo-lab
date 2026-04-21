CREATE TABLE shippings (
    shipping_id     VARCHAR(255)    NOT NULL,
    order_id        VARCHAR(255)    NOT NULL,
    user_id         VARCHAR(255)    NOT NULL,
    status          VARCHAR(50)     NOT NULL,
    tracking_number VARCHAR(255),
    carrier         VARCHAR(255),
    created_at      TIMESTAMP       NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP       NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_shippings PRIMARY KEY (shipping_id),
    CONSTRAINT uq_shippings_order_id UNIQUE (order_id)
);

CREATE INDEX idx_shippings_user_id ON shippings (user_id);
CREATE INDEX idx_shippings_status ON shippings (status);
